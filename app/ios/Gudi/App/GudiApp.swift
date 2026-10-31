import SwiftUI
import GudiData
import GudiDomain

/// The app's entry (stage 13.8): the texts and the language (decision 47), the site's data, and the screens.
@main
struct GudiApp: App {
    @State private var model = AppModel()
    @Environment(\.scenePhase) private var phase
    /// The first app_open of this process is a cold one (docs/GROWTH.md).
    @State private var opened = false

    init() {
        if let url = Bundle.main.url(forResource: "strings", withExtension: "json", subdirectory: "Data"),
           let data = try? Data(contentsOf: url) {
            try? Strings.shared.load(data)
        }
    }

    var body: some Scene {
        WindowGroup {
            RootView(model: model)
                .task {
                    Reminders.attach(model)
                    // a new record goes to the group's table (via the write queue when there is no signal)
                    Bests.onRecord = { _, _ in Task { await GroupService.shared.sendBests() } }
                    await GroupService.shared.sendBests()
                    await model.pollStatus()
                }
                .onOpenURL { url in
                    // app_open's source: a link the app or the site shared (utm_medium=share), or any other link
                    Telemetry.shared.opened(by: Measure.source(of: url))
                    if MeetLink.parse(url: url.absoluteString)?.station != nil { Telemetry.shared.event("meet_link_open") }
                    // an invitation (/j/<token>, /join/<code>, #join/<code>) opens the group's door; anything else the map or meeting point
                    if let r = GroupService.route(for: url) { model.path = [r] } else { model.open(url) }
                }
        }
            .onChange(of: phase) { _, p in
                switch p {
                case .active:
                    Task { await GroupService.shared.flush() }
                    let cold = !opened
                    // a moment later, so the link or the reminder that opened the app has arrived (onOpenURL, the
                    // notification's delegate) and app_open carries it
                    Task { @MainActor in
                        try? await Task.sleep(for: .milliseconds(600))
                        Telemetry.shared.appOpen(cold: cold)
                    }
                    if cold { Telemetry.shared.screen("home") }
                    opened = true
                case .background: Telemetry.shared.flush()
                default: break
                }
            }
    }
}

/// Home, and the screens it leads to in a navigation stack (back with the system's edge swipe).
struct RootView: View {
    @Bindable var model: AppModel
    @AppStorage("dayNight") private var modeRaw = DayNight.Mode.auto.rawValue
    /// A language chosen in the app (empty: the phone's, decision 47).
    @AppStorage("lang") private var langRaw = ""
    @Environment(\.openURL) private var openURL

    var body: some View {
        let mode = DayNight.Mode(rawValue: modeRaw) ?? .auto
        // the sky changes by the minute; the whole app goes dark with it (the site's data-theme)
        TimelineView(.periodic(from: .now, by: 60)) { t in
            let date = model.clock(t.date)
            let now = Int64(date.timeIntervalSince1970 * 1000)
            let frame = DayNight.at(now, mode)
            let lang = Lang(rawValue: langRaw) ?? Lang.auto(Locale.preferredLanguages)
            let _ = { Strings.shared.lang = lang }()
            let _ = Telemetry.shared.set(lang: lang.rawValue, langSource: langRaw.isEmpty ? "auto" : "manual", theme: mode.rawValue)
            let ski = Ski(colors: SkiColors(p: frame.dark ? .night : .day), type: SkiType(lang: lang))
            NavigationStack(path: $model.path) {
                HomeView(frame: frame, mode: mode, trip: model.trip, now: LocalMoment.now(date), status: model.status, weather: model.weather(date),
                         onMode: { modeRaw = mode.next.rawValue; Telemetry.shared.event("theme_set", ["mode": mode.next.rawValue]) },
                         onLang: { setLang($0, lang) }) { a in
                    switch a {
                    case .map: model.go(.map())
                    case .meet: model.go(.meet())
                    case .games: model.go(.games)
                    case .group: model.go(.group)
                    case .about: model.go(.about)
                    case .trip: model.go(.trip)
                    case .status: model.statusSheet = true; model.go(.map())
                    case .privacy: openURL(Site.privacy(lang))
                    }
                }
                // home is the sky, the boarding pass and the sign post, drawn at fixed sizes
                .fixedType()
                .toolbar(.hidden, for: .navigationBar)
                .navigationDestination(for: Route.self) { r in
                    Group {
                        switch r {
                        case .map(let run): MapScreen(run: run)
                        // the meeting point: a grid of day and hour blocks, signs and the meeting card, all at fixed sizes
                        case .meet(let station): MeetScreen(station: station).fixedType()
                        // the games page (signs on a post) and the games themselves: art and play fields at fixed sizes
                        case .games: GamesScreen().fixedType()
                        case .game(let id): GameScreen(id: id).fixedType()
                        case .group: GroupScreen()
                        case .about: AboutScreen(mode: mode, onMode: { modeRaw = mode.next.rawValue; Telemetry.shared.event("theme_set", ["mode": mode.next.rawValue]) },
                                                 onLang: { setLang($0, lang) }, onResetBests: { Bests.reset() })
                        case .trip: TripFormScreen()
                        case .groups(let g): GroupFlow(route: g)
                        }
                    }
                    .background(ski.colors.snow)
                }
            }
            .onChange(of: model.path) { old, new in
                if new.count > old.count, let r = new.last {
                    if case .game(let g) = r { Telemetry.shared.screen("game", ["game": g]) } else { Telemetry.shared.screen(r.screenName) }
                } else if new.isEmpty { Telemetry.shared.screen("home") }
            }
            .tint(ski.colors.ink)
            .id(lang)
            .environment(model)
            .environment(\.ski, ski)
            .environment(\.layoutDirection, lang.rtl ? .rightToLeft : .leftToRight)
            .preferredColorScheme(frame.dark ? .dark : .light)
            // body text follows the phone's text size up to the largest before the accessibility sizes, where the
            // layouts still hold; the screenshots can pin one (qa flag text=xxxl)
            .dynamicTypeSize(qaTextSize.map { $0...$0 } ?? (.xSmall ... .xxxLarge))
        }
    }

    private var qaTextSize: DynamicTypeSize? {
        switch UserDefaults.standard.string(forKey: "qa.text") ?? "" {
        case "xxxl": .xxxLarge
        case "ax3": .accessibility3
        // the screenshots run (it has a qaRoute) draws at the default size ("Large") unless a flag says otherwise, so the
        // shots do not depend on the simulator's own text size setting
        default: UserDefaults.standard.string(forKey: "qaRoute") != nil ? .large : nil
        }
    }

    private func setLang(_ l: Lang?, _ previous: Lang) {
        langRaw = l?.rawValue ?? ""
        let now = l ?? Lang.auto(Locale.preferredLanguages)
        Telemetry.shared.event("lang_set", ["lang": now.rawValue, "previous": previous.rawValue])
    }
}
