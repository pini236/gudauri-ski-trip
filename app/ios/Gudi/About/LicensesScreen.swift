import SwiftUI

/// One font and what it is under (A-24, round 18; Android's home/Licenses.kt): its full text is packed in the app
/// (Gudi/About/Licenses, the same files as app/ios/licenses). The iPhone app has no third-party library yet; when one
/// comes (PostHog, Sentry, supabase-swift), its licence joins this list.
struct OpenLicense: Identifiable, Hashable {
    let id: String
    let name: String
    let license: String
    let file: String
}

extension OpenLicense {
    static let ofl = "SIL Open Font License 1.1"
    /// The rows, in the board's order: the fonts.
    static let all: [OpenLicense] = [
        OpenLicense(id: "karantina", name: "Karantina", license: ofl, file: "OFL-Karantina"),
        OpenLicense(id: "plex-hebrew", name: "IBM Plex Sans Hebrew", license: ofl, file: "OFL-IBMPlexSansHebrew"),
        OpenLicense(id: "plex", name: "IBM Plex Sans", license: ofl, file: "OFL-IBMPlexSans"),
        OpenLicense(id: "oswald", name: "Oswald", license: ofl, file: "OFL-Oswald"),
        OpenLicense(id: "noto-georgian", name: "Noto Sans Georgian", license: ofl, file: "OFL-NotoSansGeorgian"),
    ]

    /// The full text, as it was written (the files are resources of the app's bundle).
    var text: String {
        let url = Bundle.main.url(forResource: file, withExtension: "txt")
            ?? Bundle.main.url(forResource: file, withExtension: "txt", subdirectory: "Licenses")
        return url.flatMap { try? String(contentsOf: $0, encoding: .utf8) } ?? ""
    }
}

/// The licences page: a row for each, and a tap opens its full text.
struct LicensesScreen: View {
    @Environment(\.ski) private var ski
    @State private var open: OpenLicense?

    var body: some View {
        let c = ski.colors
        Screen(title: S.appLicensesTitle) {
            ScrollView {
                VStack(spacing: 0) {
                    ForEach(OpenLicense.all) { l in
                        Button { open = l } label: {
                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(l.name).font(ski.type.text(15.5, .bold)).foregroundStyle(c.ink)
                                    Text(l.license).font(ski.type.text(13)).foregroundStyle(c.muted)
                                }
                                Spacer()
                                Image(systemName: "chevron.forward").font(.system(size: 15, weight: .semibold)).foregroundStyle(c.glacier).accessibilityHidden(true)
                            }
                            .padding(.vertical, 6).frame(minHeight: 56).contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        AboutRule()
                    }
                }
                .padding(.horizontal, 16).padding(.bottom, 24)
            }
        }
        .navigationDestination(item: $open) { l in LicenseScreen(license: l).background(c.snow) }
    }
}

/// One licence's full text, as it was written: in English, left to right.
struct LicenseScreen: View {
    let license: OpenLicense
    @Environment(\.ski) private var ski
    var body: some View {
        let c = ski.colors
        Screen(title: license.name) {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text(license.license).font(ski.type.text(13)).foregroundStyle(c.muted)
                    Text(license.text).font(.custom("IBM Plex Sans", fixedSize: 13)).lineSpacing(4).foregroundStyle(c.ink)
                        .multilineTextAlignment(.leading).textSelection(.enabled)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 16).padding(.bottom, 24)
                .environment(\.layoutDirection, .leftToRight)
            }
        }
    }
}
