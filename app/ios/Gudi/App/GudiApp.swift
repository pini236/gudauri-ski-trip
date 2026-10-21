import SwiftUI
import GudiData
import GudiDomain

/// The app's entry (stage 13.8): the texts and the language (decision 47), the site's data, and the screens.
@main
struct GudiApp: App {
    @State private var model = AppModel()

    init() {
        if let url = Bundle.main.url(forResource: "strings", withExtension: "json", subdirectory: "Data"),
           let data = try? Data(contentsOf: url) {
            try? Strings.shared.load(data)
        }
    }

    var body: some Scene {
        WindowGroup { RootView(model: model).task { await model.pollStatus() } }
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
            let ski = Ski(colors: SkiColors(p: frame.dark ? .night : .day), type: SkiType(lang: lang))
            NavigationStack(path: $model.path) {
                HomeView(frame: frame, mode: mode, trip: model.trip, now: LocalMoment.now(date), status: model.status, weather: model.weather(date),
                         onMode: { modeRaw = mode.next.rawValue }, onLang: { langRaw = $0?.rawValue ?? "" }) { a in
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
                .toolbar(.hidden, for: .navigationBar)
                .navigationDestination(for: Route.self) { r in
                    Group {
                        switch r {
                        case .map(let run): MapScreen(run: run)
                        case .meet(let station): MeetScreen(station: station)
                        case .games: GamesScreen()
                        case .game(let id): GameScreen(id: id)
                        case .group: GroupScreen()
                        case .about: AboutScreen(mode: mode, onMode: { modeRaw = mode.next.rawValue }, onLang: { langRaw = $0?.rawValue ?? "" })
                        case .trip: TripFormScreen()
                        }
                    }
                    .background(ski.colors.snow)
                }
            }
            .tint(ski.colors.ink)
            .id(lang)
            .environment(model)
            .environment(\.ski, ski)
            .environment(\.layoutDirection, lang.rtl ? .rightToLeft : .leftToRight)
            .preferredColorScheme(frame.dark ? .dark : .light)
        }
    }
}
