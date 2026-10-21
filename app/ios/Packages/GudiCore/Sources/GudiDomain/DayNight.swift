import Foundation

/// Sunrise, sunset and solar noon in Gudauri, as Android's `map/Sky.sunTimes` and the site (hours in Gudauri time).
public enum Sun {
    static let lat = 42.51, lon = 44.495, tz = 4.0 // UTC+4, no daylight saving
    static let rad = Double.pi / 180

    /// [rise, set, noon], Gudauri hours, for the local day of `epochMs`.
    public static func times(_ epochMs: Int64) -> (rise: Double, set: Double, noon: Double) {
        let localDays = ((Double(epochMs) / 3_600_000 + tz) / 24).rounded(.down)
        let day = Day(epochDay: Int(localDays))
        let n = Double(day.epochDay - Day(day.year, 1, 1)!.epochDay + 1)
        let dec = -23.44 * cos(2 * Double.pi / 365 * (n + 10)) * rad
        let b = 2 * Double.pi / 364 * (n - 81)
        let eot = 9.87 * sin(2 * b) - 7.53 * cos(b) - 1.5 * sin(b)
        let half = acos(-tan(lat * rad) * tan(dec)) / rad / 15
        let noon = 12 - (lon - tz * 15) / 15 - eot / 60
        return (noon - half, noon + half, noon)
    }
}

/// Day and night on the home page, as on the site (6.3, N1, site/js/app.js DN) and Android (`home/DayNight.kt`):
/// three modes, auto follows the clock in Gudauri and that day's sunrise and sunset. The real view from the village in
/// seven moments of the day, cross-faded, with the sky colours, stars and village lights in between. Plain math,
/// tested; the screen only draws what `at` returns.
public enum DayNight {
    public enum Mode: String, CaseIterable, Sendable {
        case auto, day, night
        public var next: Mode { Mode.allCases[(Mode.allCases.firstIndex(of: self)! + 1) % Mode.allCases.count] }
    }

    struct Key { let h: Double, img: String, top: UInt32, bottom: UInt32, glow: Double, glowC: UInt32, star: Double, moon: Double, win: Double }

    static func night(_ h: Double) -> Key { Key(h: h, img: "night", top: 0x050A15, bottom: 0x1A2645, glow: 0, glowC: 0xFFFFFF, star: 1, moon: 1, win: 1) }

    /// The site's keyframes (keys() in site/js/app.js), by Gudauri hour.
    static func keys(_ rise: Double, _ set: Double, _ noon: Double) -> [Key] { [
        night(0), night(rise - 1.2),
        Key(h: rise - 0.3, img: "dawn", top: 0x2C3B66, bottom: 0xE8A987, glow: 0.7, glowC: 0xFFB38A, star: 0.2, moon: 0.3, win: 0.8),
        Key(h: rise + 1.2, img: "morning", top: 0x6FA6DC, bottom: 0xDCEAF4, glow: 0.35, glowC: 0xFFF2D6, star: 0, moon: 0, win: 0),
        Key(h: noon, img: "noon", top: 0x4F90D2, bottom: 0xD2E4F3, glow: 0.2, glowC: 0xFFFFFF, star: 0, moon: 0, win: 0),
        Key(h: set - 1.8, img: "gold", top: 0x6F9CCB, bottom: 0xF1DDC2, glow: 0.6, glowC: 0xFFD29A, star: 0, moon: 0, win: 0),
        Key(h: set - 0.35, img: "sunset", top: 0x3A4677, bottom: 0xF09A6A, glow: 1, glowC: 0xFF9A6A, star: 0.1, moon: 0.2, win: 0.6),
        Key(h: set + 0.45, img: "dusk", top: 0x1B2448, bottom: 0x6E5D86, glow: 0.35, glowC: 0xC98AA0, star: 0.6, moon: 0.8, win: 1),
        night(set + 1.3), night(24),
    ] }

    public struct Frame: Sendable {
        /// The two images to show: `imgA` under `imgB`, which is drawn at opacity `mix`.
        public let imgA: String, imgB: String, mix: Double
        public let skyTop: UInt32, skyBottom: UInt32
        public let glow: Double, glowColor: UInt32, glowMorning: Bool
        public let stars: Double, moon: Double, lights: Double
        /// The whole app goes dark (the night palette), as the site's data-theme.
        public let dark: Bool
        /// The clock in Gudauri, "13:35".
        public let clock: String
        /// sunrise, day, golden_hour, sunset, twilight, night (the site's daynight.phase_*).
        public let phase: String
    }

    static func lerp(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }
    static func mixRgb(_ a: UInt32, _ b: UInt32, _ t: Double) -> UInt32 {
        func ch(_ s: UInt32) -> UInt32 { UInt32(lerp(Double((a >> s) & 255), Double((b >> s) & 255), t) + 0.5) << s }
        return ch(16) | ch(8) | ch(0)
    }

    /// The moment the mountain is lit as: now in auto, else that day's solar noon (day) or 22:00 (night) in Gudauri.
    public static func lightTime(_ epochMs: Int64, _ mode: Mode) -> Int64 {
        if mode == .auto { return epochMs }
        let h = mode == .day ? Sun.times(epochMs).noon : 22
        let hourMs: Int64 = 3_600_000
        let local = epochMs + 4 * hourMs
        let dayStart = (local >= 0 ? local / (24 * hourMs) : (local - 24 * hourMs + 1) / (24 * hourMs)) * 24 * hourMs - 4 * hourMs
        return dayStart + Int64(h * Double(hourMs))
    }

    public static func at(_ epochMs: Int64, _ mode: Mode) -> Frame {
        let local = Double(epochMs) / 3_600_000 + 4
        let now = local - (local / 24).rounded(.down) * 24
        let (rise, set, noon) = Sun.times(epochMs)
        let h: Double = switch mode { case .day: noon; case .night: 22; case .auto: now }
        let k = keys(rise, set, noon)
        var i = 0
        while i < k.count - 2 && k[i + 1].h < h { i += 1 }
        let a = k[i], b = k[i + 1]
        let span = b.h - a.h == 0 ? 1 : b.h - a.h
        let f = min(max((h - a.h) / span, 0), 1)
        let dark = mode == .night || (mode == .auto && (h < rise - 0.3 || h > set + 0.3))
        let hh = Int(now.rounded(.down)), mm = Int(((now - Double(hh)) * 60).rounded(.down))
        let phase = dark ? (h > set && h < set + 1.3 ? "twilight" : "night")
            : h < rise + 0.8 ? "sunrise" : h < set - 2 ? "day" : h < set - 0.6 ? "golden_hour" : "sunset"
        return Frame(imgA: a.img, imgB: b.img, mix: a.img == b.img ? 0 : f,
                     skyTop: mixRgb(a.top, b.top, f), skyBottom: mixRgb(a.bottom, b.bottom, f),
                     glow: lerp(a.glow, b.glow, f), glowColor: f < 0.5 ? a.glowC : b.glowC, glowMorning: h < noon,
                     stars: lerp(a.star, b.star, f), moon: lerp(a.moon, b.moon, f), lights: lerp(a.win, b.win, f),
                     dark: dark, clock: pad2(hh) + ":" + pad2(mm), phase: phase)
    }
}
