import Foundation

/// The rules of the usage measurement that are logic, not sending (docs/GROWTH.md, "מדידת שימוש"); the app's
/// Telemetry.swift sends, these decide. Each is the site's or Android's rule, word for word.
public enum Measure {
    /// A phone whose clock is set to Europe keeps no stored id (the architect's m-5, decision 37; the privacy line
    /// for Europe, decision 73): the site's `europe()` in site/js/telemetry.js, with the same zones.
    public static func europe(_ timeZone: String) -> Bool {
        if timeZone.hasPrefix("Europe/") { return true }
        if ["Atlantic/Azores", "Atlantic/Canary", "Atlantic/Faroe", "Atlantic/Madeira", "Atlantic/Reykjavik"].contains(timeZone) { return true }
        return timeZone == "Asia/Nicosia" || timeZone == "Asia/Famagusta"
    }

    /// app_open's `source` for a link that opened the app: shared from the app or the site (utm_medium=share) is
    /// `share_link`, any other link `link` (Android's OpenSource.ofQuery).
    public static func source(of url: URL) -> String {
        utm(URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems ?? [])["utm_medium"] == "share" ? "share_link" : "link"
    }

    /// The utm_* of a link (Android's OpenSource.utm): only the four keys, non-blank, at most 100 characters.
    public static func utm(_ items: [URLQueryItem]) -> [String: String] {
        let keys: Set<String> = ["utm_source", "utm_medium", "utm_campaign", "utm_content"]
        var out: [String: String] = [:]
        for i in items where keys.contains(i.name) {
            guard let v = i.value, !v.trimmingCharacters(in: .whitespaces).isEmpty else { continue }
            out[i.name] = String(v.prefix(100))
        }
        return out
    }

    /// app_open's `first_open` (Android's InstallAnalytics.qualifiesFirstOpen): true only on the first recorded
    /// launch of a fresh install. On iPhone a fresh install is one with nothing the app ever kept: no launch mark,
    /// no measurement id and no measurement switch, so an update from a version without the mark is not "new".
    public static func firstOpen(launchRecorded: Bool, hadId: Bool, hadSwitch: Bool) -> Bool {
        !launchRecorded && !hadId && !hadSwitch
    }

    /// `install_channel` (docs/GROWTH.md): a closed list, never a raw value. `environment` is StoreKit 2's
    /// `AppTransaction.environment` (preferred), `receiptName` the last component of the receipt URL (the fallback).
    /// TestFlight is the sandbox in both; the App Store is production.
    public static func installChannel(simulator: Bool, environment: String?, receiptName: String?) -> String {
        if simulator { return "simulator" }
        switch environment?.lowercased() {
        case "production": return "appstore"
        case "sandbox": return "testflight"
        default: break
        }
        switch receiptName {
        case "sandboxReceipt": return "testflight"
        case "receipt": return "appstore"
        default: return "unknown"
        }
    }

    /// The two properties every event carries besides the fixed ones: `install_channel` always, `is_internal` only
    /// when the user marked the phone (never `false`).
    public static func installProps(channel: String, internal isInternal: Bool) -> [String: Any] {
        var p: [String: Any] = ["install_channel": channel]
        if isInternal { p["is_internal"] = true }
        return p
    }
}
