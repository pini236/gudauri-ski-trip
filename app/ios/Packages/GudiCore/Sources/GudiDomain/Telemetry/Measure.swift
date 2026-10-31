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
}
