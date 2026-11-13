import SwiftUI
import GudiData
import GudiDomain

#if DAYREC
/// Where the day was at a time: the position (metres, as the map's) and the speed (km/h) between the two readings
/// around it. Android's `ReplayAt`.
enum ReplayAt {
    struct Spot: Equatable { let x: Double, y: Double, kmh: Double }

    /// The day, 300 times faster than it was lived: a day of eight hours is a minute and a half.
    static let rate: Double = 300

    /// `t` clamped to the day; nil for a day with no readings. A gap in the recording holds the last reading still.
    static func spot(_ tr: DayTrack, _ t: Int64) -> Spot? {
        let n = tr.t.count
        guard n > 0 else { return nil }
        if t <= tr.t[0] { return Spot(x: tr.x[0], y: tr.y[0], kmh: tr.kmh[0]) }
        if t >= tr.t[n - 1] { return Spot(x: tr.x[n - 1], y: tr.y[n - 1], kmh: tr.kmh[n - 1]) }
        var lo = 0, hi = n - 1
        while hi - lo > 1 {
            let m = (lo + hi) / 2
            if tr.t[m] <= t { lo = m } else { hi = m }
        }
        let dt = tr.t[hi] - tr.t[lo]
        // a hole in the recording: still, not a line across it
        if dt > 90_000 { return Spot(x: tr.x[lo], y: tr.y[lo], kmh: 0) }
        let f = min(max(Double(t - tr.t[lo]) / Double(dt), 0), 1)
        return Spot(x: tr.x[lo] + (tr.x[hi] - tr.x[lo]) * f, y: tr.y[lo] + (tr.y[hi] - tr.y[lo]) * f,
                    kmh: tr.kmh[lo] + (tr.kmh[hi] - tr.kmh[lo]) * f)
    }
}

/// The day again on the 3D map (round 29, "צפייה חוזרת"): a dot goes along your line with the time and the speed, and
/// the camera after it. Android's `ReplayScreen`. The map is the app's own 3D map; nothing leaves the phone.
struct ReplayScreen: View {
    let start: Int64
    /// The example day: its journal lives in the caches.
    var sample = false

    @Environment(AppModel.self) private var app
    @Environment(\.ski) private var ski
    @AppStorage("dayNight") private var dayNightRaw = DayNight.Mode.auto.rawValue

    @State private var map: MapModel?
    @State private var map3d: Map3DController?
    @State private var track: DayTrack?
    /// Milliseconds from the start of the day.
    @State private var at: Double = 0
    @State private var playing = false
    @State private var lookedAt: Double = 0

    private var t0: Int64 { track?.t.first ?? 0 }
    private var t1: Int64 { track?.t.last ?? 0 }
    private var span: Double { max(Double(t1 - t0), 1) }
    private var spot: ReplayAt.Spot? { track.flatMap { ReplayAt.spot($0, t0 + Int64(at)) } }

    private var lightTime: Date {
        let ms = Int64(Date().timeIntervalSince1970 * 1000)
        return Date(timeIntervalSince1970: Double(DayNight.lightTime(ms, DayNight.Mode(rawValue: dayNightRaw) ?? .auto)) / 1000)
    }

    var body: some View {
        let c = ski.colors
        Screen(title: S.appDayReplay) {
            VStack(spacing: 0) {
                if let map, Map3DView.available {
                    Map3DView(model: map, status: app.status, forMe: false, lightTime: lightTime, bottom: 0.3,
                              controller: $map3d, onRun: { _ in }, onLift: { _ in }, onFlying: { _ in })
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .accessibilityHidden(true)
                } else {
                    Text(S.appDayLoading).font(ski.type.text(16)).foregroundStyle(c.muted)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
                controls
            }
        }
        .task { await load() }
        .onChange(of: at) { follow() }
    }

    private var controls: some View {
        let c = ski.colors
        return VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .lastTextBaseline, spacing: 10) {
                Text(DayRec.LiveNumbers.maxKmh(spot?.kmh)).font(ski.type.display(56)).monospacedDigit()
                Text(S.appRecUnit).font(ski.type.text(18))
                Spacer(minLength: 0)
                Text(track == nil ? "" : clock(t0 + Int64(at))).font(ski.type.text(16, .bold)).foregroundStyle(c.muted)
            }
            Slider(value: $at, in: 0...span) { editing in if editing { playing = false } }
                .disabled(track == nil)
                .accessibilityLabel(S.appDayReplay)
            Button {
                if at >= span { at = 0 }
                playing.toggle()
                if playing { tick() }
            } label: {
                Text(playing ? S.appDayrecStop : S.appDayReplayPlay)
                    .font(ski.type.text(16, .bold)).foregroundStyle(c.ink)
                    .frame(maxWidth: .infinity, minHeight: 48)
                    .overlay(Rectangle().stroke(c.ink, lineWidth: 1.5))
            }
            .buttonStyle(.plain)
            .disabled(track == nil)
        }
        .padding(16)
        .frame(maxWidth: .infinity)
        .background(c.paper)
        .overlay(alignment: .top) { Rectangle().fill(c.rule).frame(height: 1) }
    }

    private func load() async {
        // a map model of its own, so the replay never changes what the map screen shows or remembers
        if map == nil, let runs = app.runs { map = MapModel(runs: runs, terrainURL: app.terrainURL) }
        let dir = sample ? SampleDay.folder : DayRecorder.folder
        let entry = sample ? SampleDay.entry() : (try? DayStore(dir: dir))?.days().first { $0.start == start }
        guard let url = entry?.url else { return }
        track = await Task.detached { DayTrack.read(url, max: 1500) }.value
        follow()
    }

    /// The dot on the mountain, and the camera after it (not on every reading).
    private func follow() {
        guard let s = spot else { return }
        map?.marker = (x: s.x, y: s.y)
        let now = Date().timeIntervalSince1970
        if now - lookedAt > 0.6 { lookedAt = now; map3d?.lookAt(x: s.x, y: s.y) }
    }

    /// The day runs on by itself while "play" is on.
    private func tick() {
        guard playing else { return }
        Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(100))
            guard playing else { return }
            at = min(at + 100 * ReplayAt.rate, span)
            if at >= span { playing = false } else { tick() }
        }
    }
}
#endif
