import SwiftUI
import UIKit
import CoreText
import GudiDomain

/// The site's tokens as SwiftUI colours, by day or by night (GudiDomain's `Palette`, tested against site/css/site.css).
struct SkiColors {
    let p: Palette
    var dark: Bool { p.dark }
    var snow: Color { Color(rgb: p.snow) }
    var paper: Color { Color(rgb: p.paper) }
    var ink: Color { Color(rgb: p.ink) }
    var muted: Color { Color(rgb: p.muted) }
    var rule: Color { Color(rgb: p.rule) }
    var glacier: Color { Color(rgb: p.glacier) }
    var green: Color { Color(rgb: p.green) }
    var blue: Color { Color(rgb: p.blue) }
    var red: Color { Color(rgb: p.red) }
    var onBoard: Color { Color(rgb: p.onBoard) }
    var accent: Color { Color(rgb: p.accent) }
    var dash: Color { Color(rgb: p.dash) }
    var black: Color { Color(rgb: p.black) }
    var lift: Color { Color(rgb: p.lift) }
    var casing: Color { Color(rgb: p.casing) }
    var peak: Color { Color(rgb: p.peak) }
    var grid: Color { Color(rgb: p.grid) }
    var water: Color { Color(rgb: p.water) }
    var waterEdge: Color { Color(rgb: p.waterEdge) }
    var road: Color { Color(rgb: p.road) }
    var roadMain: Color { Color(rgb: p.roadMain) }
    var contour: Color { Color(rgb: p.contour) }
    var contourI: Color { Color(rgb: p.contourI) }
    var village: Color { Color(rgb: p.village) }
    /// A run's colour by its name in the data (green, blue, red, black); black for anything else.
    func run(_ c: String) -> Color {
        switch c { case "green": green; case "blue": blue; case "red": red; default: black }
    }
    // the boarding pass's own print (--bp-*)
    var bpPaper: Color { Color(rgb: p.bpPaper) }
    var bpPaper2: Color { Color(rgb: p.bpPaper2) }
    var bpInk: Color { Color(rgb: p.bpInk) }
    var bpMuted: Color { Color(rgb: p.bpMuted) }
    var bpStrip: Color { Color(rgb: p.bpStrip) }
    var bpOnStrip: Color { Color(rgb: p.bpOnStrip) }
    var bpAccent: Color { Color(rgb: p.bpAccent) }
}

/// Fonts per language, as approved in the canvas (FT1, decision 36) and as Android's i18n/Lang.kt: Hebrew and English
/// in Karantina for display; Russian Oswald 600; Georgian Noto Sans Georgian. Text in IBM Plex Sans Hebrew (Hebrew),
/// IBM Plex Sans (English, Russian) and Noto Sans Georgian.
struct SkiType {
    let lang: Lang

    /// Oswald and the narrow Georgian set bigger than Karantina at the same size.
    var displayScale: CGFloat { lang == .ru ? 0.85 : lang == .ka ? 0.9 : 1 }

    func display(_ size: CGFloat) -> Font {
        let s = size * displayScale
        switch lang {
        case .he, .en: return .custom("Karantina-Bold", fixedSize: s)
        case .ru: return variable("Oswald-Regular", s, weight: 600)
        // narrow (width 62.5%) and 800 for headings, as Android
        case .ka: return variable("NotoSansGeorgian-Regular", s, weight: 800, width: 62.5)
        }
    }

    /// A variable font at an exact weight (and width), through its axes ('wght', 'wdth'), as Android's FontVariation.
    private func variable(_ name: String, _ size: CGFloat, weight: CGFloat, width: CGFloat? = nil) -> Font {
        var axes: [NSNumber: CGFloat] = [0x7767_6874: weight] // 'wght'
        if let width { axes[0x7764_7468] = width } // 'wdth'
        let d = UIFontDescriptor(fontAttributes: [.name: name, UIFontDescriptor.AttributeName(rawValue: kCTFontVariationAttribute as String): axes])
        return Font(UIFont(descriptor: d, size: size))
    }

    /// Body text follows the phone's text size (Dynamic Type) from the size the canvas drew at the default ("Large"),
    /// so nothing changes at the default. The app caps it at the largest size before the accessibility sizes, and the
    /// parts drawn as art (home, the boarding pass, the games, the maps) keep the default size (`fixedType()`).
    /// The display face (signs, big numbers) stays fixed.
    func text(_ size: CGFloat, _ weight: Font.Weight = .regular) -> Font {
        switch lang {
        case .he: return .custom(weight == .regular ? "IBMPlexSansHebrew-Regular" : "IBMPlexSansHebrew-Bold", size: size, relativeTo: .body)
        case .en, .ru: return .custom("IBM Plex Sans", size: size, relativeTo: .body).weight(weight)
        case .ka: return .custom("Noto Sans Georgian", size: size, relativeTo: .body).weight(weight)
        }
    }
}

extension View {
    /// A part drawn as art at fixed sizes (the boarding pass, the sign post, the games, the maps and their labels):
    /// its text stays at the default size whatever the phone's text size, as the canvas drew it.
    func fixedType() -> some View { dynamicTypeSize(.large) }
}

struct Ski {
    let colors: SkiColors
    let type: SkiType
}

private struct SkiKey: EnvironmentKey {
    static let defaultValue = Ski(colors: SkiColors(p: .day), type: SkiType(lang: .he))
}

extension EnvironmentValues {
    var ski: Ski {
        get { self[SkiKey.self] }
        set { self[SkiKey.self] = newValue }
    }
}

extension Color {
    /// 0xRRGGBB
    init(rgb v: UInt32, opacity: Double = 1) {
        self.init(.sRGB, red: Double((v >> 16) & 255) / 255, green: Double((v >> 8) & 255) / 255,
                  blue: Double(v & 255) / 255, opacity: opacity)
    }
    /// #RRGGBB
    init(hex: String) { self.init(rgb: UInt32(hex.dropFirst(), radix: 16) ?? 0) }
}
