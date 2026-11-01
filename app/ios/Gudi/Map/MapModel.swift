import SwiftUI
import GudiData
import GudiDomain

/// The map screen's state (Android's MapScreen state and MapView's selection): the elevation model (read once, off
/// the main thread), the chosen run or lift, the list, the filters, the view (3D or from above), and the profile's dot.
@MainActor
@Observable
final class MapModel {
    let runs: RunsAndLifts
    var terrain: Terrain?
    /// The shaded relief, contours, village, roads and water under the top view (UI/Relief2D, shared with the meeting point).
    var art: ReliefArt?
    var videos: [Video] = []
    /// The named runs in the sign's order (colour, then name).
    let order: [String]

    var selected: Piste? { didSet { if oldValue?.key != selected?.key { selectionChanged() } } }
    var lift: Lift?
    var list = false
    /// Filters off: "green", "blue", "red", "black", "unnamed", "lifts".
    var hidden: Set<String> = []
    /// "3d" or "2d", kept on the phone as the site keeps it in the browser.
    var mode: String { didSet { UserDefaults.standard.set(mode, forKey: "mapView") } }
    var facts: RunFacts?
    /// The profile's dot on the map, in metres.
    var marker: (x: Double, y: Double)?
    private var compareCache: [String: Double] = [:]

    init(runs: RunsAndLifts, terrainURL: URL?) {
        self.runs = runs
        order = RunFacts.order(runs.pistes)
        mode = UserDefaults.standard.string(forKey: "mapView") ?? "3d"
        // the emulator run's switch (a launch argument for the screenshots)
        if let qa = UserDefaults.standard.string(forKey: "qa.view") { mode = qa }
        list = UserDefaults.standard.string(forKey: "qa.list") != nil
        if let url = Bundle.main.url(forResource: "videos-seed", withExtension: "json", subdirectory: "Data"),
           let d = try? Data(contentsOf: url) { videos = Video.parse(d) }
        if let terrainURL {
            Task.detached(priority: .userInitiated) {
                let t = (try? Data(contentsOf: terrainURL)).flatMap { try? Terrain.parse($0) }
                await MainActor.run { [weak self] in self?.terrain = t; self?.selectionChanged() }
            }
            Task { [weak self] in
                let a = await ReliefArt.load(url: terrainURL)
                self?.art = a
            }
        }
    }

    /// The keys the filters hide.
    var hiddenKeys: Set<String> {
        Set(runs.pistes.filter { hidden.contains($0.color) || (!$0.named && hidden.contains("unnamed")) }.map(\.key))
    }
    var hideLifts: Bool { hidden.contains("lifts") }

    func piste(_ key: String) -> Piste? { runs.pistes.first { $0.key == key } }

    /// Chooses a run; `via` is how (docs/GROWTH.md run_open: "map", "list", "swipe", "link"), as Android's
    /// MapView.select: run_open goes when a run is chosen, not when the map draws it again.
    func select(_ key: String?, via: String? = nil) {
        selected = key.flatMap(piste)
        if let p = selected {
            lift = nil; list = false
            if let via { Telemetry.shared.event("run_open", ["run": p.key, "color": p.color, "via": via]) }
        }
    }

    /// Opens a lift's panel; lift_open with its name as in the data (Android's MapScreen, from any way in).
    func showLift(_ l: Lift) {
        selected = nil; lift = l; list = false
        Telemetry.shared.event("lift_open", l.name.isEmpty ? [:] : ["lift": l.name])
    }

    func toggle(_ filter: String) {
        if hidden.contains(filter) { hidden.remove(filter) } else { hidden.insert(filter) }
        Telemetry.shared.event("map_filter", ["filter": filter, "on": !hidden.contains(filter)])
    }

    func compare(_ key: String) -> (steep: String, long: String)? {
        guard let terrain else { return nil }
        return RunFacts.compare(terrain, runs.pistes, key: key, cache: &compareCache)
    }

    private func selectionChanged() {
        marker = nil
        guard let p = selected, let t = terrain else { facts = nil; return }
        let key = p.key
        Task.detached(priority: .userInitiated) {
            let f = RunFacts.of(t, p)
            await MainActor.run { [weak self] in if self?.selected?.key == key { self?.facts = f } }
        }
    }
}

/// Numbers and lengths as the site writes them, in the app's language.
enum Fmt {
    static func n(_ v: Int) -> String {
        let f = NumberFormatter(); f.numberStyle = .decimal; f.locale = Locale(identifier: Strings.shared.lang.rawValue)
        return f.string(from: NSNumber(value: v)) ?? String(v)
    }
    static func metres(_ v: Int) -> String { S.commonUnitM(n: n(v)) }
    /// Metres, or kilometres with two decimals from 1,000 m (the site's fmtLen).
    static func length(_ m: Int) -> String {
        m >= 1000 ? S.commonUnitKm(n: String(format: "%.2f", Double(m) / 1000)) : metres(m)
    }
    static func round(_ x: Double) -> Int { Int((x + 0.5).rounded(.down)) }
}

/// A run's name as the site shows it (dispName).
func runName(_ p: Piste) -> String { !p.named ? S.mapUnnamedSegment : p.key == "Firni ?" ? "Firni (1/2?)" : p.key }

/// The run's link on the site (#map/run/<key>), which opens the same run in the app or on the site.
func runLink(_ key: String) -> URL {
    let k = key.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? key
    return URL(string: "\(Site.base.absoluteString)/?utm_source=app&utm_medium=share#map/run/\(k)")!
}
