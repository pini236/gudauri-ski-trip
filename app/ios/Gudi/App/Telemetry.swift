import Foundation
import UIKit
import GudiDomain

/// Usage measurement (decision 32, docs/GROWTH.md, the architect's review of 21.10.2026), the iPhone side of what
/// Android sends with PostHog's library: here without a library, a plain POST to PostHog's EU batch endpoint.
///
/// - The events and properties are only the contract in docs/GROWTH.md ("מדידת שימוש"). No autocapture, no
///   identify(), no person profiles (`$process_person_profile: false` on every event), no address of a screen.
/// - The id is random, kept in UserDefaults (on a phone set to Europe: only in memory, m-5); it changes when the app is reinstalled. Dropping the IP address is the
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

    /// A phone whose clock is set to Europe keeps no stored id: a new one for each run of the app, only in memory,
    /// as the site's PostHog with `persistence: 'memory'` (m-5, decision 37; the privacy line, decision 73).
    private static let europe = Measure.europe(TimeZone.current.identifier)
    private static let memoryId = UUID().uuidString.lowercased()

    private static var id: String {
        if europe { UserDefaults.standard.removeObject(forKey: "telemetry.id"); return memoryId }
        if let v = UserDefaults.standard.string(forKey: "telemetry.id") { return v }
        let v = UUID().uuidString.lowercased()
        UserDefaults.standard.set(v, forKey: "telemetry.id")
        return v
    }

    /// app_open's first_open: the first launch of a fresh install (Android's InstallAnalytics). Read once at launch,
    /// before any event keeps an id, and marked even with the switch off, so turning it on later is not "new".
    let firstOpen: Bool = {
        let d = UserDefaults.standard
        let first = Measure.firstOpen(launchRecorded: d.bool(forKey: "telemetry.launched"),
                                      hadId: d.string(forKey: "telemetry.id") != nil, hadSwitch: d.object(forKey: "telemetry.on") != nil)
        d.set(true, forKey: "telemetry.launched")
        return first
    }()
    private var firstOpenSent = false
    /// What brought the app to the front this time: a link (`link`, `share_link`) or a reminder (`notification`).
    private var openedBy: (source: String, at: Date)?

    /// A link or a notification opened the app; the app_open of the next few seconds carries it (a tap while the
    /// app is already open brings no app_open, and is forgotten).
    func opened(by source: String) { openedBy = (source, .now) }

    /// app_open (docs/GROWTH.md): cold once per process, warm on each return from the background. Sent a moment after
    /// the app becomes active, so a link or a notification that opened it is known.
    func appOpen(cold: Bool) {
        let first = cold && firstOpen && !firstOpenSent
        if cold { firstOpenSent = true }
        let recent = openedBy.flatMap { Date.now.timeIntervalSince($0.at) < 5 ? $0.source : nil }
        // no `store` on iPhone: it has no install referrer, and the contract leaves the install's source to the App
        // Store's own numbers (docs/GROWTH.md, "מקור ההגעה"); `build` already tells the App Store from TestFlight
        let source = recent ?? "direct"
        openedBy = nil
        event("app_open", ["source": source, "cold": cold, "first_open": first])
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
        case .groups(let g):
            switch g {
            case .join, .code, .reclaim: "join"
            case .account: "account"
            case .page, .new, .invite: "group"
            }
        case .about: "about"
        case .trip: "trip"
        }
    }
}
