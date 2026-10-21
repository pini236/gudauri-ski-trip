import SwiftUI
import GudiData
import GudiDomain

/// The app's entry (stage 13.8): the texts and the language (decision 47), the site's data, and the home page.
@main
struct GudiApp: App {
    init() {
        if let url = Bundle.main.url(forResource: "strings", withExtension: "json", subdirectory: "Data"),
           let data = try? Data(contentsOf: url) {
            try? Strings.shared.load(data)
        }
    }

    var body: some Scene {
        WindowGroup { RootView() }
    }
}

struct RootView: View {
    /// A made-up trip for the screenshots (invented flight numbers, as Android's QA).
    static let sample = Trip(Leg(Day(2027, 1, 10)!, flight: "XX 101", from: "TLV", to: "TBS", departs: ClockTime(16, 0)!, arrives: ClockTime(20, 35)!),
                             Leg(Day(2027, 1, 15)!, flight: "XX 102", from: "TBS", to: "TLV", departs: ClockTime(1, 35)!, arrives: ClockTime(2, 15)!))

    @AppStorage("dayNight") private var modeRaw = DayNight.Mode.auto.rawValue
    /// A language chosen in the app (empty: the phone's, decision 47).
    @AppStorage("lang") private var langRaw = ""
    /// The user's own trip, as JSON in Android's format (decision 27: only on the phone).
    @AppStorage("trip") private var tripData = Data()
    /// For the screenshots on GitHub only (a launch argument): a made-up trip, never the crew's flight.
    @AppStorage("qaTrip") private var qaTrip = false
    private let runs: RunsAndLifts? = {
        guard let url = Bundle.main.url(forResource: "runs-and-lifts", withExtension: "json", subdirectory: "Data"),
              let data = try? Data(contentsOf: url) else { return nil }
        return try? RunsAndLifts.decode(data)
    }()

    var body: some View {
        let mode = DayNight.Mode(rawValue: modeRaw) ?? .auto
        // the sky changes by the minute; the whole app goes dark with it (the site's data-theme)
        TimelineView(.periodic(from: .now, by: 60)) { t in
            let now = Int64(t.date.timeIntervalSince1970 * 1000)
            let frame = DayNight.at(now, mode)
            let lang = Lang(rawValue: langRaw) ?? Lang.auto(Locale.preferredLanguages)
            let _ = { Strings.shared.lang = lang }()
            let names = runs.map { LiftStatus.names($0.mainLifts) } ?? []
            let trip = qaTrip ? Self.sample : Trip.from(json: tripData)
            HomeView(frame: frame, mode: mode, trip: trip, now: LocalMoment.now(t.date), status: LiftStatus(names: names, report: nil, now: now),
                     onMode: { modeRaw = mode.next.rawValue }, onLang: { langRaw = $0?.rawValue ?? "" }, go: { _ in })
                .id(lang)
                .environment(\.ski, Ski(colors: SkiColors(p: frame.dark ? .night : .day), type: SkiType(lang: lang)))
                .environment(\.layoutDirection, lang.rtl ? .rightToLeft : .leftToRight)
                .preferredColorScheme(frame.dark ? .dark : .light)
        }
    }
}
