import SwiftUI
import GudiDomain

/// The language, from the home page (decision 47, as Android's home/LangSheet.kt): a small tag in the head with the
/// language's mark (עב, EN, RU, KA) opens the list.
struct LangTag: View {
    let action: () -> Void
    @Environment(\.ski) private var ski
    var body: some View {
        let c = ski.colors
        Button(action: action) {
            Text(ski.type.lang.code).font(ski.type.text(13, .bold)).foregroundStyle(c.ink)
                .padding(.horizontal, 6).padding(.vertical, 2)
                .overlay(Rectangle().stroke(c.ink, lineWidth: 1.5))
                .frame(minWidth: 44, minHeight: 44)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(S.appLangButton(name: ski.type.lang.name))
    }
}

/// The list (the site's .lang-sheet): each language in its own script and title font, so anyone finds theirs even in a
/// language they do not read, and "from the phone" goes back to the phone's language.
struct LangSheet: View {
    /// A language, or nil for the phone's.
    let onPick: (Lang?) -> Void
    @Environment(\.ski) private var ski
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let c = ski.colors
        let current = ski.type.lang
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text(S.aboutLanguage + (current == .en ? "" : " · \u{2068}Language\u{2069}"))
                    .font(ski.type.display(32)).foregroundStyle(c.ink).accessibilityAddTraits(.isHeader)
                Spacer()
                Button { dismiss() } label: {
                    Image(systemName: "xmark").font(.system(size: 18, weight: .semibold)).foregroundStyle(c.ink).frame(width: 44, height: 44)
                }
                .accessibilityLabel(S.commonClose)
            }
            Text(S.aboutLanguageHint).font(ski.type.text(13)).foregroundStyle(c.muted).padding(.top, 2).padding(.bottom, 14)
            VStack(spacing: 8) {
                ForEach(Lang.allCases, id: \.self) { l in
                    let on = l == current
                    Button { onPick(l) } label: {
                        HStack {
                            Text(l.name).font(SkiType(lang: l).display(l == .he || l == .en ? 28 : 26)).foregroundStyle(c.ink)
                            Spacer()
                            Rectangle().stroke(c.ink, lineWidth: 2).frame(width: 22, height: 22)
                                .overlay { if on { Rectangle().fill(c.ink).frame(width: 12, height: 12) } }
                        }
                        .padding(.horizontal, 14).frame(minHeight: 56)
                        .background(c.paper)
                        .overlay(Rectangle().stroke(on ? c.ink : c.rule, lineWidth: 2))
                        // each row in its own direction, whatever the app's language is now
                        .environment(\.layoutDirection, l.rtl ? .rightToLeft : .leftToRight)
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(on ? .isSelected : [])
                }
            }
            Button { onPick(nil) } label: {
                Text(S.appLangPhone).font(ski.type.text(14)).underline().foregroundStyle(c.glacier).padding(.vertical, 12)
            }
            .buttonStyle(.plain).padding(.top, 14)
            Spacer(minLength: 0)
        }
        .padding(16)
        .background(c.snow)
    }
}
