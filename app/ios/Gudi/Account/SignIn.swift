import Foundation
import AuthenticationServices
import CryptoKit
import UIKit
import GudiDomain

/// The ways in on iPhone (decision 27; docs/APPLE.md, 3): Sign in with Apple, and Google next to it (App Store
/// guideline 4.8 asks for Apple wherever Google is offered). Each returns an ID token for the server (Supabase signs in
/// with it, `grant_type=id_token`); the raw nonce goes with it, the provider gets only its SHA-256.
@MainActor
enum SignIn {
    /// Google's iOS OAuth client (Google Cloud, type iOS, bundle io.github.pini236.skiapp). Pini creates it and adds it to
    /// Supabase's Google provider (its "Client IDs" list). Not a secret. While empty, the Google button is hidden.
    static let googleIOSClientID = ""

    static var googleReady: Bool { !googleIOSClientID.isEmpty }

    struct Token { let idToken: String; let nonce: String; let name: String? }

    static func nonce() -> String {
        var b = [UInt8](repeating: 0, count: 24)
        _ = SecRandomCopyBytes(kSecRandomDefault, b.count, &b)
        return b.map { String(format: "%02x", $0) }.joined()
    }

    static func sha256(_ s: String) -> String { SHA256.hash(data: Data(s.utf8)).map { String(format: "%02x", $0) }.joined() }

    // MARK: Apple

    /// The system's Apple sheet. Needs the "Sign in with Apple" capability (entitlement) and the Apple provider in
    /// Supabase, with the bundle id as its client id.
    static func apple() async throws -> Token {
        let raw = nonce()
        let req = ASAuthorizationAppleIDProvider().createRequest()
        req.requestedScopes = [.fullName]
        req.nonce = sha256(raw)
        let delegate = AppleDelegate()
        let c = ASAuthorizationController(authorizationRequests: [req])
        c.delegate = delegate
        c.presentationContextProvider = delegate
        let cred: ASAuthorizationAppleIDCredential = try await withCheckedThrowingContinuation { k in
            delegate.done = { k.resume(with: $0) }
            c.performRequests()
        }
        withExtendedLifetime((delegate, c)) {}
        guard let t = cred.identityToken, let id = String(data: t, encoding: .utf8) else { throw ApiError("apple_failed") }
        let name = [cred.fullName?.givenName, cred.fullName?.familyName].compactMap { $0 }.joined(separator: " ")
        return Token(idToken: id, nonce: raw, name: name.isEmpty ? nil : name)
    }

    private final class AppleDelegate: NSObject, ASAuthorizationControllerDelegate, ASAuthorizationControllerPresentationContextProviding {
        var done: ((Result<ASAuthorizationAppleIDCredential, Error>) -> Void)?

        func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization a: ASAuthorization) {
            if let c = a.credential as? ASAuthorizationAppleIDCredential { done?(.success(c)) } else { done?(.failure(ApiError("apple_failed"))) }
            done = nil
        }

        func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
            let cancelled = (error as? ASAuthorizationError)?.code == .canceled
            done?(.failure(ApiError(cancelled ? "cancelled" : "apple_failed")))
            done = nil
        }

        func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor { keyWindow() }
    }

    // MARK: Google

    /// Google's own page in the system's sheet (ASWebAuthenticationSession), the code flow with PKCE as for an iOS
    /// client (no secret), then Google's token endpoint for the ID token.
    static func google() async throws -> Token {
        guard googleReady else { throw ApiError("google_not_ready") }
        let raw = nonce()
        let verifier = nonce() + nonce()
        let challenge = Data(SHA256.hash(data: Data(verifier.utf8))).base64EncodedString()
            .replacingOccurrences(of: "+", with: "-").replacingOccurrences(of: "/", with: "_").replacingOccurrences(of: "=", with: "")
        let scheme = googleIOSClientID.split(separator: ".").reversed().joined(separator: ".")
        let redirect = "\(scheme):/oauth2redirect"
        var c = URLComponents(string: "https://accounts.google.com/o/oauth2/v2/auth")!
        c.queryItems = [.init(name: "client_id", value: googleIOSClientID), .init(name: "redirect_uri", value: redirect),
                        .init(name: "response_type", value: "code"), .init(name: "scope", value: "openid email profile"),
                        .init(name: "code_challenge", value: challenge), .init(name: "code_challenge_method", value: "S256"),
                        .init(name: "nonce", value: sha256(raw)), .init(name: "prompt", value: "select_account")]
        let anchor = WebAnchor()
        let back: URL = try await withCheckedThrowingContinuation { k in
            let s = ASWebAuthenticationSession(url: c.url!, callbackURLScheme: scheme) { url, err in
                if let url { k.resume(returning: url) }
                else { k.resume(throwing: ApiError((err as? ASWebAuthenticationSessionError)?.code == .canceledLogin ? "cancelled" : "google_failed")) }
            }
            s.presentationContextProvider = anchor
            s.prefersEphemeralWebBrowserSession = false
            webSession = s
            s.start()
        }
        webSession = nil
        withExtendedLifetime(anchor) {}
        guard let code = URLComponents(url: back, resolvingAgainstBaseURL: false)?.queryItems?.first(where: { $0.name == "code" })?.value else {
            throw ApiError("google_failed")
        }
        var r = URLRequest(url: URL(string: "https://oauth2.googleapis.com/token")!)
        r.httpMethod = "POST"
        r.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        let form = ["code": code, "client_id": googleIOSClientID, "redirect_uri": redirect, "grant_type": "authorization_code", "code_verifier": verifier]
        r.httpBody = Data(form.map { "\($0.key)=\(ServerClient.enc($0.value))" }.joined(separator: "&").utf8)
        let data: Data
        do { (data, _) = try await URLSession.shared.data(for: r) } catch { throw ApiError("offline") }
        guard let id = JSON.parse(data)?.str("id_token") else { throw ApiError("google_failed") }
        return Token(idToken: id, nonce: raw, name: nil)
    }

    /// The sheet must stay alive while it shows.
    private static var webSession: ASWebAuthenticationSession?

    private final class WebAnchor: NSObject, ASWebAuthenticationPresentationContextProviding {
        func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor { keyWindow() }
    }

    nonisolated static func keyWindow() -> UIWindow {
        MainActor.assumeIsolated {
            UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.flatMap(\.windows).first { $0.isKeyWindow }
                ?? UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first.map { UIWindow(windowScene: $0) } ?? UIWindow()
        }
    }

    /// Sign in with a provider through the group door; with the pretend server, no sheet at all.
    /// A phone with no trip of its own gets back the trip this account keeps on the server (the site's pullTrip).
    static func run(_ provider: String, model: AppModel) async throws {
        let api = GroupService.shared.api
        if GroupService.shared.fake { _ = try await api.signIn(provider: provider, idToken: "qa", nonce: nil); return }
        let t = provider == "apple" ? try await apple() : try await google()
        _ = try await api.signIn(provider: provider, idToken: t.idToken, nonce: t.nonce)
        if api.me()?.name == nil, let n = t.name { try? await api.rename(n) }
        if model.trip == nil, let back = try? await api.myTripOnServer() { model.trip = back }
    }
}
