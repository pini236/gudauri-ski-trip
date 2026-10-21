/// The app's colours are the site's tokens (site/css/site.css, :root and :root[data-theme="dark"]), as hex RGB.
/// A colour changed on the site fails `TokensTests` until it is carried over, as on Android (`ui/Tokens.kt`).
public struct Palette: Sendable {
    public let snow, paper, ink, muted, rule, glacier, grid, lift, casing, green, blue, red, black, onBoard, accent, onAccent, dash, sky1, sky2, water, road, contour, peak, waterEdge, roadMain, village, contourI, lsDark, sn1, sn2, sn3, bpPaper, bpPaper2, bpInk, bpMuted, bpStrip, bpOnStrip, bpAccent: UInt32
    /// The snow's outline: light by day, none at night (transparent on the site).
    public let snEdge: UInt32?
    public let dark: Bool

    /// Each colour with the name of its CSS token, for the test.
    public var byToken: [String: UInt32] { [
        "snow": snow,
        "paper": paper,
        "ink": ink,
        "muted": muted,
        "rule": rule,
        "glacier": glacier,
        "grid": grid,
        "lift": lift,
        "casing": casing,
        "p-green": green,
        "p-blue": blue,
        "p-red": red,
        "p-black": black,
        "on-board": onBoard,
        "accent": accent,
        "on-accent": onAccent,
        "dash": dash,
        "sky1": sky1,
        "sky2": sky2,
        "water": water,
        "road": road,
        "contour": contour,
        "peak": peak,
        "water-edge": waterEdge,
        "road-main": roadMain,
        "vill": village,
        "contour-i": contourI,
        "ls-dark": lsDark,
        "sn1": sn1,
        "sn2": sn2,
        "sn3": sn3,
        "bp-paper": bpPaper,
        "bp-paper2": bpPaper2,
        "bp-ink": bpInk,
        "bp-muted": bpMuted,
        "bp-strip": bpStrip,
        "bp-on-strip": bpOnStrip,
        "bp-acc": bpAccent,
    ] }

    public static let day = Palette(snow: 0xEEF2F5, paper: 0xFFFFFF, ink: 0x13233A, muted: 0x4B5A6F, rule: 0xCBD5DF, glacier: 0x1F5FC4, grid: 0xDCE4EC, lift: 0x3A4556, casing: 0xFFFFFF, green: 0x1B8A4C, blue: 0x1F5FC4, red: 0xD1342B, black: 0x13233A, onBoard: 0xFFFFFF, accent: 0x1F5FC4, onAccent: 0xFFFFFF, dash: 0x8E9CAD, sky1: 0xDCE8F1, sky2: 0xEEF2F5, water: 0x8DB6D8, road: 0xA49A91, contour: 0x8193A8, peak: 0x3D4A5C, waterEdge: 0x5F8FBB, roadMain: 0x8A7C70, village: 0xE3D8CB, contourI: 0x62758C, lsDark: 0x13233A, sn1: 0xFFFFFF, sn2: 0xF1F5F9, sn3: 0xD3DFEB, bpPaper: 0xFFFFFF, bpPaper2: 0xF4F7FA, bpInk: 0x13233A, bpMuted: 0x4B5A6F, bpStrip: 0x1F5FC4, bpOnStrip: 0xFFFFFF, bpAccent: 0x1F5FC4, snEdge: 0xC3D0DD, dark: false)
    public static let night = Palette(snow: 0x0D1522, paper: 0x16223A, ink: 0xEAF0F7, muted: 0xA3B3C8, rule: 0x2A3B55, glacier: 0x8DB9FF, grid: 0x18263A, lift: 0xC9D3E0, casing: 0x0D1522, green: 0x3CC47C, blue: 0x5B9BFF, red: 0xFF6A5F, black: 0xEAF0F7, onBoard: 0x0D1522, accent: 0xF4B942, onAccent: 0x0D1522, dash: 0x51627D, sky1: 0x0B1320, sky2: 0x16223A, water: 0x2C5A82, road: 0x6D6760, contour: 0x5A6D85, peak: 0xC9D3E0, waterEdge: 0x4D82B3, roadMain: 0x8F857B, village: 0x3A352F, contourI: 0x7A90AA, lsDark: 0x34496E, sn1: 0xE8EEF6, sn2: 0xCAD6E5, sn3: 0x9EB1C9, bpPaper: 0x16223A, bpPaper2: 0x1B2944, bpInk: 0xFFD98A, bpMuted: 0xC9B98F, bpStrip: 0xF4B942, bpOnStrip: 0x0D1522, bpAccent: 0xFFD98A, snEdge: nil, dark: true)
}
