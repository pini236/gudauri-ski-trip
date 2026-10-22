import UIKit
import GudiDomain

/// The fonts of the words the games draw themselves (in UIKit views), by the language as the app's SkiType: the sign
/// voice (Karantina, Oswald, Noto Sans Georgian) and the text.
enum GameFonts {
    static func display(_ lang: Lang, _ size: CGFloat) -> UIFont {
        let name = switch lang { case .he, .en: "Karantina-Bold"; case .ru: "Oswald-Regular"; case .ka: "NotoSansGeorgian-Regular" }
        let s = size * (lang == .ru ? 0.85 : lang == .ka ? 0.9 : 1)
        return UIFont(name: name, size: s) ?? .systemFont(ofSize: s, weight: .bold)
    }

    static func text(_ lang: Lang, _ size: CGFloat, bold: Bool = false) -> UIFont {
        let name = switch lang {
        case .he: bold ? "IBMPlexSansHebrew-Bold" : "IBMPlexSansHebrew-Regular"
        case .en, .ru: "IBM Plex Sans"
        case .ka: "Noto Sans Georgian"
        }
        let base = UIFont(name: name, size: size) ?? .systemFont(ofSize: size)
        guard bold, lang != .he, let d = base.fontDescriptor.withSymbolicTraits(.traitBold) else { return base }
        return UIFont(descriptor: d, size: size)
    }
}
