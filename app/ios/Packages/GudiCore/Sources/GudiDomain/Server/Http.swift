import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

/// One HTTP exchange with the server (Android's server/Http.kt). The real one is [URLSessionTransport]; the tests pass
/// a fake, so they never reach the live server.
public protocol Transport: Sendable {
    /// Throws [Offline] when there is no answer at all (no signal, a timeout); any HTTP status is an answer.
    func send(_ req: HTTPRequest) async throws -> HTTPResponse
}

public struct HTTPRequest: Sendable, Equatable {
    public let method: String
    public let url: String
    public let headers: [String: String]
    public let body: String?
    public init(_ method: String, _ url: String, _ headers: [String: String], _ body: String? = nil) {
        self.method = method; self.url = url; self.headers = headers; self.body = body
    }
}

public struct HTTPResponse: Sendable, Equatable {
    public let status: Int
    public let body: String
    public init(_ status: Int, _ body: String) { self.status = status; self.body = body }
    public var ok: Bool { (200...299).contains(status) }
}

/// No answer from the server: the caller keeps what it has on the phone and tries again later.
public struct Offline: Error, Sendable {
    public let reason: String
    public init(_ reason: String = "") { self.reason = reason }
}

/// A refusal from the server: a short code (server/CONTRACT.md, "שגיאות") that the app turns into its own words.
public struct ServerError: Error, Sendable, Equatable {
    public let code: String
    public let status: Int
    public init(_ code: String, _ status: Int) { self.code = code; self.status = status }
}

/// The system's HTTP client: no library for a few small JSON requests (as Android's UrlTransport).
public struct URLSessionTransport: Transport {
    public init() {}

    private static let session: URLSession = {
        let c = URLSessionConfiguration.ephemeral
        c.timeoutIntervalForRequest = 20
        c.timeoutIntervalForResource = 30
        c.requestCachePolicy = .reloadIgnoringLocalCacheData
        c.urlCache = nil
        return URLSession(configuration: c)
    }()

    public func send(_ req: HTTPRequest) async throws -> HTTPResponse {
        guard let url = URL(string: req.url) else { throw Offline("bad url") }
        var r = URLRequest(url: url)
        r.httpMethod = req.method
        for (k, v) in req.headers { r.setValue(v, forHTTPHeaderField: k) }
        if let b = req.body { r.httpBody = Data(b.utf8) }
        do {
            let (data, resp) = try await Self.session.data(for: r)
            let status = (resp as? HTTPURLResponse)?.statusCode ?? 0
            return HTTPResponse(status, String(decoding: data, as: UTF8.self))
        } catch {
            throw Offline(String(describing: error))
        }
    }
}
