import SwiftUI
import GudiDomain

/// The account shown at the top of about (the site's #abMe, P4 and P5 of round 12; Android's home/Soon.kt Account).
/// The group part fills it in; until then the page shows a guest pass whose "sign in" leads to the group.
struct AboutAccount {
    /// The name, or nil before any sign-in.
    var name: String?
    /// Signed in with Google or Apple (not a guest).
    var registered: Bool
    var onSignIn: () -> Void
    var onManage: () -> Void
    var onSignOut: () -> Void
}

/// About and settings (13.7), as the site's #about (decision 21, round 11) and Android's AboutScreen: the account's
/// ski pass, the settings (sound, vibration, the display, the language, measuring, resetting the best scores), who
/// built it, and the credits the data, the recordings and the fonts require (OpenStreetMap's ODbL and the tear's CC BY
/// ask for a visible credit; the fonts' licence texts are on the licences page), the privacy policy and "unofficial".
struct AboutScreen: View {
    let mode: DayNight.Mode
    let onMode: () -> Void
    let onLang: (Lang?) -> Void
    /// The account (the group part connects it); nil: a guest pass with "sign in".
    var account: AboutAccount? = nil
    /// Resets the games' best scores (the games provide it); by default the `best.` keys in UserDefaults.
    var onResetBests: (() -> Void)? = nil

    @Environment(\.ski) private var ski
    @Environment(AppModel.self) private var app
    @Environment(\.openURL) private var openURL
    @AppStorage("sound") private var sound = true
    @AppStorage("haptics") private var haptics = true
    @State private var analytics = true
    @State private var langs = false
    @State private var armed = false
    @State private var done = false
    @State private var licenses = false

    var body: some View {
        let c = ski.colors
        Screen(title: S.commonAboutSettings) {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    accountPass
                    // the settings (the site's .ab-set); round 20: no line under them
                    AboutH2(text: S.aboutSettings)
                    VStack(spacing: 0) {
                        AboutToggle(title: S.aboutSound, sub: nil, on: $sound)
                            .onChange(of: sound) { _, v in Telemetry.shared.event("settings_change", ["setting": "sound", "on": v]) }
                        // every iPhone can vibrate (Android hides the row on a phone that cannot)
                        AboutToggle(title: S.aboutHaptics, sub: nil, on: $haptics)
                            .onChange(of: haptics) { _, v in Telemetry.shared.event("settings_change", ["setting": "haptics", "on": v]) }
                        AboutRow(title: S.aboutDisplay, sub: nil, value: modeName(mode), action: onMode)
                        AboutRow(title: S.aboutLanguage + (ski.type.lang == .en ? "" : " · \u{2068}Language\u{2069}"), sub: nil,
                                 value: ski.type.lang.name) { langs = true }
                        // the site's switch (round 11, AN1/AN2): off stops usage statistics and crash reports at once
                        AboutToggle(title: S.aboutAnalytics, sub: analytics ? S.aboutAnalyticsOn : S.aboutAnalyticsOff, on: $analytics)
                            .onChange(of: analytics) { _, v in Telemetry.shared.setEnabled(v) }
                        // reset the best scores: twice, as on the site
                        AboutRow(title: S.aboutReset, sub: done ? S.aboutResetDone : armed ? S.aboutResetConfirm : nil, value: nil, danger: armed) { reset() }
                    }
                    // who built it (the site's .ab-who)
                    AboutH2(text: S.aboutWho)
                    AboutPass(top: S.aboutPassTop, end: S.aboutBuilder) {
                        BuilderPhoto()
                        VStack(alignment: .leading, spacing: 6) {
                            Text(S.aboutBuilderName).font(ski.type.display(34)).foregroundStyle(c.ink)
                            Text(S.aboutBio).font(ski.type.text(13.5)).foregroundStyle(c.muted).fixedSize(horizontal: false, vertical: true)
                            AboutLink(text: "github.com/pini236") { openURL(URL(string: "https://github.com/pini236")!) }
                                .environment(\.layoutDirection, .leftToRight)
                        }
                    }
                    // the credits (the site's .ab-credits)
                    AboutH2(text: S.aboutCredits)
                    AboutCredit(label: S.aboutCreditOsmLabel, text: "© " + S.aboutCreditLicensed(link: S.aboutCreditOsmLink, license: "ODbL"),
                           url: "https://www.openstreetmap.org/copyright")
                    AboutCredit(label: S.aboutCreditTerrainLabel, text: S.aboutCreditTerrain)
                    // the weather's source, as its licence asks (round 19, m-6 section 6)
                    AboutCredit(label: S.aboutCreditWeatherLabel, text: S.aboutCreditLicensed(link: "Open-Meteo.com", license: "CC BY 4.0"), url: "https://open-meteo.com/")
                    AboutCredit(label: S.aboutCreditTearLabel, text: S.aboutCreditLicensed(link: S.aboutCreditTearLink, license: "CC BY 4.0"),
                           url: "https://freesound.org/people/everythingsounds/sounds/198233/")
                    AboutCredit(label: S.aboutCreditCardsLabel, text: S.aboutCreditLicensed(link: "Kenney", license: "CC0"), url: "https://kenney.nl/assets/casino-audio")
                    AboutCredit(label: S.aboutCreditFontsLabel, text: S.appAboutFonts)
                    AboutCredit(label: S.aboutCreditVideosLabel, text: S.appAboutCreditVideos)
                    // the full texts the fonts' licences ask to show (A-24, round 18)
                    Button { licenses = true } label: {
                        HStack {
                            Text(S.appLicensesRow).font(ski.type.display(24)).foregroundStyle(c.ink)
                            Spacer()
                            Image(systemName: "chevron.forward").font(.system(size: 15, weight: .semibold)).foregroundStyle(c.glacier).accessibilityHidden(true)
                        }
                        .frame(minHeight: 56).contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .overlay(alignment: .top) { AboutRule() }
                    .overlay(alignment: .bottom) { AboutRule() }
                    AboutCredit(label: nil, text: S.aboutCreditMta)
                    HStack(spacing: 0) {
                        AboutLink(text: S.appAboutPrivacy) { openURL(Site.privacy(ski.type.lang)) }
                        Text(" · " + S.aboutUnofficial).font(ski.type.text(13)).foregroundStyle(c.muted)
                    }
                    Text(S.appAboutVersion(v: Self.version)).font(ski.type.text(12)).foregroundStyle(c.muted)
                }
                .padding(.horizontal, 16).padding(.bottom, 24)
                .frame(maxWidth: 560, alignment: .leading)
                .frame(maxWidth: .infinity)
            }
        }
        .sheet(isPresented: $langs) {
            LangSheet { l in langs = false; onLang(l) }
                .environment(\.ski, ski)
                .presentationDetents([.medium, .large])
        }
        .navigationDestination(isPresented: $licenses) { LicensesScreen().background(c.snow) }
        .task(id: armed) {
            // the second tap must come within four seconds, as Android
            guard armed else { return }
            try? await Task.sleep(for: .seconds(4))
            armed = false
        }
        .onAppear {
            analytics = Telemetry.enabled
            // the screenshots open the licences page through the about page ("about:licenses")
            if UserDefaults.standard.string(forKey: "qaRoute") == "about:licenses" {
                Task { try? await Task.sleep(for: .seconds(1.2)); licenses = true }
            }
        }
    }

    /// The account's ski pass (the site's #abMe): the name or "guest", what an account gives, and the way in or out.
    @ViewBuilder private var accountPass: some View {
        let c = ski.colors
        let a = account ?? AboutAccount(name: nil, registered: false, onSignIn: { app.go(.group) }, onManage: { app.go(.group) }, onSignOut: {})
        let me = a.name
        AboutH2(text: me != nil ? S.acctTitle : S.acctSigninTitle)
        AboutPass(top: me != nil ? S.acctPassTopMe : S.acctPassTopGuest,
                    end: me == nil ? S.acctNoAccount : a.registered ? S.acctGoogleName : S.acctSignedGuest) {
            AboutAvatar(name: me, size: 52)
            VStack(alignment: .leading, spacing: 6) {
                Text(me ?? S.ticketPaxGuest).font(ski.type.display(34)).foregroundStyle(c.ink).lineLimit(1)
                Text(me == nil ? S.appGRestWorks : a.registered ? S.acctSyncNote : S.appAGuestNote)
                    .font(ski.type.text(13.5)).foregroundStyle(c.muted).fixedSize(horizontal: false, vertical: true)
                HStack(spacing: 16) {
                    if me != nil { AboutLink(text: S.acctManage, action: a.onManage) }
                    if me == nil { AboutLink(text: S.acctSigninTitle, action: a.onSignIn) }
                    else if a.registered { AboutLink(text: S.acctSignOutShort, color: c.ink, action: a.onSignOut) }
                }
            }
        }
    }

    private func reset() {
        if armed {
            armed = false; done = true
            if let onResetBests { onResetBests() } else { Self.clearBests() }
            Telemetry.shared.event("best_reset")
            Fx.shared.click(0.8)
        } else {
            armed = true; done = false
            Fx.shared.tick(0.5)
        }
    }

    /// The games keep their best scores under `best.` (the games' convention); resetting removes them all.
    static func clearBests() {
        let d = UserDefaults.standard
        for k in d.dictionaryRepresentation().keys where k.hasPrefix("best.") { d.removeObject(forKey: k) }
    }

    private func modeName(_ m: DayNight.Mode) -> String {
        switch m { case .auto: S.daynightModeAuto; case .day: S.daynightModeDay; case .night: S.daynightModeNight }
    }

    /// "0.1 (123)": the version and the build (the build number never resets, R-8).
    static var version: String {
        let i = Bundle.main.infoDictionary
        let v = i?["CFBundleShortVersionString"] as? String ?? "?"
        let b = i?["CFBundleVersion"] as? String ?? "?"
        return "\(v) (\(b))"
    }
}

// MARK: - Pieces

/// A heading in the sign voice, 30 (the site's .ab h2).
struct AboutH2: View {
    let text: String
    @Environment(\.ski) private var ski
    var body: some View {
        Text(text).font(ski.type.display(30)).foregroundStyle(ski.colors.ink)
            .padding(.top, 14).accessibilityAddTraits(.isHeader)
    }
}

struct AboutRule: View {
    @Environment(\.ski) private var ski
    var body: some View { Rectangle().fill(ski.colors.rule).frame(height: 1) }
}

/// A link in the page (the glacier blue, bold), 44 high.
struct AboutLink: View {
    let text: String
    var color: Color? = nil
    let action: () -> Void
    @Environment(\.ski) private var ski
    var body: some View {
        Button(action: action) {
            Text(text).font(ski.type.text(14, .bold)).foregroundStyle(color ?? ski.colors.glacier)
                .frame(minHeight: 44).contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

/// A switch row (the site's .ab-row with a switch): its name and line, and the switch at the end.
struct AboutToggle: View {
    let title: String
    let sub: String?
    @Binding var on: Bool
    @Environment(\.ski) private var ski
    var body: some View {
        let c = ski.colors
        VStack(spacing: 0) {
            Toggle(isOn: $on) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(ski.type.text(15.5, .bold)).foregroundStyle(c.ink)
                    if let sub { Text(sub).font(ski.type.text(12.5)).foregroundStyle(c.muted) }
                }
            }
            .tint(c.green)
            .frame(minHeight: 60)
            .onChange(of: on) { Fx.shared.tick(0.4) }
            AboutRule()
        }
    }
}

/// A settings row that is not a switch (the site's .ab-row button): its name and line, and the value at the end.
struct AboutRow: View {
    let title: String
    let sub: String?
    let value: String?
    var danger = false
    let action: () -> Void
    @Environment(\.ski) private var ski
    var body: some View {
        let c = ski.colors
        VStack(spacing: 0) {
            Button(action: action) {
                HStack(spacing: 14) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(title).font(ski.type.text(15.5, .bold)).foregroundStyle(danger ? c.red : c.ink)
                        if let sub { Text(sub).font(ski.type.text(12.5)).foregroundStyle(danger ? c.red : c.muted) }
                    }
                    Spacer(minLength: 0)
                    if let value { Text(value).font(ski.type.text(14, .bold)).foregroundStyle(c.glacier) }
                }
                .frame(minHeight: 60).contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            AboutRule()
        }
    }
}

/// A ski pass card (the site's .ab-pass): a blue strip with two words, and its body, tilted a little.
struct AboutPass<Content: View>: View {
    let top: String
    let end: String
    @ViewBuilder let content: Content
    @Environment(\.ski) private var ski
    var body: some View {
        let c = ski.colors
        VStack(spacing: 0) {
            HStack {
                // onBoard: white by day, dark on the bright night blue (PARITY A-38)
                Text(top.uppercased()).font(ski.type.text(12, .semibold)).kerning(0.6).foregroundStyle(c.onBoard)
                Spacer()
                Text(end.uppercased()).font(ski.type.text(12, .semibold)).kerning(0.6).foregroundStyle(c.onBoard)
            }
            .lineLimit(1)
            .padding(.horizontal, 14).padding(.vertical, 8)
            .background(c.blue)
            HStack(alignment: .top, spacing: 14) { content }
                .padding(14)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .background(c.paper)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .shadow(color: .black.opacity(c.dark ? 0.4 : 0.14), radius: 10, y: 3)
        .rotationEffect(.degrees(-1.5))
        .frame(maxWidth: 420)
        .padding(.vertical, 6)
    }
}

/// A round mark with the name's first letter, or "?" (Android's group AboutAvatar).
struct AboutAvatar: View {
    let name: String?
    var size: CGFloat = 52
    @Environment(\.ski) private var ski
    var body: some View {
        let c = ski.colors
        Circle().fill(name != nil ? c.blue : c.dark ? c.rule : Color(rgb: c.p.grid))
            .overlay(Text(name.flatMap { $0.first.map(String.init) } ?? "?").font(ski.type.display(size * 0.6))
                .foregroundStyle(name != nil ? c.onBoard : c.muted))
            .frame(width: size, height: size)
            .accessibilityHidden(true)
    }
}

/// The builder's picture, his GitHub picture as on the site, packed in the app (no request to github.com, R-3).
/// Pixel art: scaled without smoothing.
struct BuilderPhoto: View {
    @Environment(\.ski) private var ski
    var body: some View {
        let c = ski.colors
        Group {
            if let ui = UIImage(named: "builder") {
                Image(uiImage: ui).resizable().interpolation(.none).scaledToFill()
            } else {
                Color(rgb: c.p.grid)
            }
        }
        .frame(width: 82, height: 82)
        .clipShape(RoundedRectangle(cornerRadius: 6))
        .padding(3)
        .background(c.paper, in: RoundedRectangle(cornerRadius: 8))
        .background(Color(rgb: c.p.grid), in: RoundedRectangle(cornerRadius: 8).inset(by: -1))
        .accessibilityHidden(true)
    }
}

/// A credit: what, in bold, and whose; with a url the words are the link (OpenStreetMap's licence asks for it).
struct AboutCredit: View {
    let label: String?
    let text: String
    var url: String? = nil
    @Environment(\.ski) private var ski
    @Environment(\.openURL) private var openURL
    var body: some View {
        let c = ski.colors
        let t = (label.map { Text($0 + " ").font(ski.type.text(13, .bold)).foregroundColor(c.ink) } ?? Text(""))
            + Text(text).font(ski.type.text(13)).foregroundColor(c.muted).underline(url != nil)
        if let url, let u = URL(string: url) {
            Button { openURL(u) } label: { t.multilineTextAlignment(.leading).frame(minHeight: 44, alignment: .leading).contentShape(Rectangle()) }
                .buttonStyle(.plain)
        } else {
            t.fixedSize(horizontal: false, vertical: true)
        }
    }
}
