import Foundation
import UIKit

/// Usage measurement (decision 32, docs/GROWTH.md, the architect's review of 21.10.2026), the iPhone side of what
/// Android sends with PostHog's library: here without a library, a plain POST to PostHog's EU batch endpoint.
///
/// - The events and properties are only the contract in docs/GROWTH.md ("מדידת שימוש"). No autocapture, no
///   identify(), no person profiles (`$process_person_profile: false` on every event), no address of a screen.
/// - The id is random, kept in UserDefaults; it changes when the app is reinstalled. Dropping the IP address is the
///   project's setting ("Discard client IP data").
/// - `build` goes inside every event: `debug` (a Debug build), `test` (TestFlight: the receipt is the sandbox one),
///   `store` (the App Store).
/// - Nothing is sent from the simulator, from the screenshot runs or from tests.
/// - The switch (settings) is kept on the phone; off means nothing is queued or sent, not even the change itself.
/// - Small and polite: a queue of at most 100 events in memory, sent every 30 s and when the app goes to the
///   background, a 10 s timeout, and one try per batch (a failed batch is dropped, never retried in a loop).
/// - Crash reports (Sentry) come later, with their own project (gudi-ios), off unless the switch is on.
@MainActor
final class Telemetry {
    static let shared = Telemetry()

    /// The site's project key, public by design: it can only send events (site/js/telemetry.js).
    private static let key = "phc_njMEqbLpeiRxvqRxpop9m8pP4xwb6PHnqjuvaTxu9Ch3"
    private static let endpoint = URL(string: "https://eu.i.posthog.com/batch/")!

    private var queue: [[String: Any]] = []
    private var common: [String: Any] = [:]
    private var timer: Timer?
    private let session: URLSession = {
        let c = URLSessionConfiguration.ephemeral
        c.timeoutIntervalForRequest = 10
        c.urlCache = nil
        c.httpCookieStorage = nil
        return URLSession(configuration: c)
    }()

    static var enabled: Bool {
        get { UserDefaults.standard.object(forKey: "telemetry.on") as? Bool ?? true }
        set { UserDefaults.standard.set(newValue, forKey: "telemetry.on") }
    }

    /// The simulator, a screenshot run (it passes -qaRoute) or a test: measure nothing.
    private static let silent: Bool = {
        #if targetEnvironment(simulator)
        return true
        #else
        let args = ProcessInfo.processInfo.arguments
        return args.contains("-qaRoute") || ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] != nil
        #endif
    }()

    private static let build: String = {
        #if DEBUG
        return "debug"
        #else
        return Bundle.main.appStoreReceiptURL?.lastPathComponent == "sandboxReceipt" ? "test" : "store"
        #endif
    }()

    private static var id: String {
        if let v = UserDefaults.standard.string(forKey: "telemetry.id") { return v }
        let v = UUID().uuidString.lowercased()
        UserDefaults.standard.set(v, forKey: "telemetry.id")
        return v
    }

    private var on: Bool { !Self.silent && Self.enabled }

    /// The properties every event carries; called again when the language or the theme changes.
    func set(lang: String, langSource: String, theme: String) {
        let v = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "0"
        common = ["platform": "ios", "app_version": v, "build": Self.build, "lang": lang, "lang_source": langSource,
                  "theme": theme, "device_class": UIDevice.current.userInterfaceIdiom == .pad ? "tablet" : "phone"]
    }

    func event(_ name: String, _ props: [String: Any] = [:]) {
        guard on else { return }
        var p = common.merging(props) { _, new in new }
        p["distinct_id"] = Self.id
        p["$process_person_profile"] = false
        p["$lib"] = "gudi-ios"
        queue.append(["event": name, "properties": p, "timestamp": ISO8601DateFormatter().string(from: Date())])
        if queue.count > 100 { queue.removeFirst(queue.count - 100) }
        if timer == nil {
            timer = Timer.scheduledTimer(withTimeInterval: 30, repeats: true) { _ in Task { @MainActor in Telemetry.shared.flush() } }
        }
    }

    func screen(_ name: String, _ extra: [String: Any] = [:]) { event("screen_view", extra.merging(["screen": name]) { a, _ in a }) }

    /// The user's switch. Off drops what is queued and sends nothing more; on sends the change.
    func setEnabled(_ value: Bool) {
        Self.enabled = value
        if value { event("settings_change", ["setting": "analytics", "on": true]) } else { queue.removeAll() }
    }

    func flush() {
        guard on, !queue.isEmpty else { return }
        let batch = queue
        queue.removeAll()
        guard let body = try? JSONSerialization.data(withJSONObject: ["api_key": Self.key, "batch": batch]) else { return }
        var req = URLRequest(url: Self.endpoint)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = body
        session.dataTask(with: req).resume()
    }
}

extension Route {
    /// The contract's screen name (docs/GROWTH.md, screen_view).
    var screenName: String {
        switch self {
        case .map: "map"
        case .meet: "meet"
        case .games: "games"
        case .game: "game"
        case .group: "group"
        case .about: "about"
        case .trip: "trip"
        }
    }
}
