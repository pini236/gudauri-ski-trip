import SwiftUI
import GudiData
import GudiDomain

#if DAYREC
/// The ski day's screens (round 29, decision 76), only in the build that has the recorder (`DAYREC`, 1.1): recording
/// ("Record"), the end of the day with its timeline, and my days. Android's dayrec/ui. Nothing here sends a reading.
@ViewBuilder func dayRecScreen(_ r: Route) -> some View {
    switch r {
    case .record: RecordScreen()
    case .days: DaysScreen()
    case .day(let start): DayScreen(start: start)
    default: EmptyView()
    }
}

/// Loads the network once and works days out in the background; a day's result is kept next to its journal.
actor DayEngine {
    static let shared = DayEngine()
    private var analyzer: DayRec.Analyzer?

    private func ready() -> DayRec.Analyzer? {
        if let analyzer { return analyzer }
        func data(_ n: String) -> Data? { Bundle.main.url(forResource: n, withExtension: "json", subdirectory: "Data").flatMap { try? Data(contentsOf: $0) } }
        guard let p = data("dayrec-params"), let rl = data("runs-and-lifts"), let t = data("terrain"),
              let params = try? DayRec.Params(p), let terrain = try? Terrain.parse(t),
              let net = try? DayRec.Net(runsAndLifts: rl, terrain: terrain, params: params) else { return nil }
        analyzer = DayRec.Analyzer(net: net, params: params)
        return analyzer
    }

    /// The truth of a day: the saved result if it is of this algorithm, else worked out from the journal (and saved).
    func final(_ e: DayStore.Entry, dir: URL) -> DayRec.Analysis? {
        guard let a = ready() else { return nil }
        let file = dir.appendingPathComponent("\(e.start).json")
        let open = (try? Data(contentsOf: e.url)).flatMap(DayJournal.read)
        if let d = try? Data(contentsOf: file), let r = try? JSONDecoder().decode(DayRec.Analysis.self, from: d),
           r.analysis == a.params.analysis, r.points == open?.points.count { return r }
        guard let day = open else { return nil }
        let r = a.final(day)
        if let d = try? JSONEncoder().encode(r) { try? d.write(to: file, options: .atomic) }
        return r
    }

    /// What the phone is doing now, from the last 20 minutes of the open journal.
    func live(_ e: DayStore.Entry) -> DayRec.Live? {
        guard let a = ready(), var day = (try? Data(contentsOf: e.url)).flatMap(DayJournal.read), let last = day.points.last?.t else { return nil }
        day.points = day.points.filter { $0.t > last - 1_200_000 }
        return a.live(day)
    }
}

private let tbilisi = TimeZone(identifier: "Asia/Tbilisi") ?? .gmt

private func clock(_ ms: Int64) -> String {
    var c = Calendar(identifier: .gregorian); c.timeZone = tbilisi
    let d = Date(timeIntervalSince1970: Double(ms) / 1000)
    return String(format: "%02d:%02d", c.component(.hour, from: d), c.component(.minute, from: d))
}

private func dayName(_ ms: Int64) -> String {
    let f = DateFormatter(); f.timeZone = tbilisi; f.dateStyle = .medium; f.timeStyle = .none
    f.locale = Locale(identifier: Strings.shared.lang.rawValue)
    return f.string(from: Date(timeIntervalSince1970: Double(ms) / 1000))
}

// MARK: recording

private struct RecordScreen: View {
    @Environment(AppModel.self) private var model
    @Environment(\.ski) private var ski
    @State private var oneDescent = false
    @State private var live: DayRec.Live?
    @State private var sofar: DayRec.Analysis?
    @State private var ticks = 0

    var body: some View {
        let c = ski.colors
        Screen(title: S.appRecSign) {
            if let rec = model.dayRecorder {
                ScrollView {
                    VStack(spacing: 18) {
                        content(rec)
                    }
                    .padding(16).frame(maxWidth: 520)
                }
                .sheet(isPresented: .constant(rec.state == .asking)) {
                    PermissionSheet { rec.ask() }.environment(\.ski, ski).presentationDetents([.medium])
                }
            }
        }
        .foregroundStyle(c.ink)
        .task(id: model.dayRecorder?.recording) {
            // the live view every few seconds, the day so far every two minutes (Android's DayRec.live and sofar)
            while !Task.isCancelled, let rec = model.dayRecorder, rec.recording {
                if let e = DayStore.latest() {
                    live = await DayEngine.shared.live(e)
                    if ticks % 24 == 0 { sofar = await DayEngine.shared.final(e, dir: DayRecorder.folder) }
                }
                ticks += 1
                try? await Task.sleep(for: .seconds(5))
            }
        }
    }

    @ViewBuilder private func content(_ rec: DayRecorder) -> some View {
        let c = ski.colors
        switch rec.state {
        case .recording(let since, _):
            Text(S.appRecNow(time: DayRec.LiveNumbers.elapsed(ms: Int64(Date().timeIntervalSince(since) * 1000)))).font(ski.type.text(14)).foregroundStyle(c.muted)
            Text(DayRec.LiveNumbers.kmh(rec.last?.spd)).font(ski.type.display(120)).monospacedDigit()
            Text(S.appRecUnit).font(ski.type.text(14)).foregroundStyle(c.muted)
            Text(whatNow()).font(ski.type.text(16, .bold)).multilineTextAlignment(.center)
            if weak(rec) { Text(S.appRecGpsWeak).font(ski.type.text(14)).foregroundStyle(c.red) }
            HStack(spacing: 10) {
                Num(value: DayRec.LiveNumbers.maxKmh(live?.maxKmh ?? sofar?.stats?.maxKmh), label: S.appRecMax)
                Num(value: String(sofar?.stats?.descents ?? 0), label: S.appRecRuns)
                Num(value: DayRec.LiveNumbers.km(sofar?.stats?.skiDistM), label: S.appRecDist)
                Num(value: DayRec.LiveNumbers.metres(sofar?.stats?.dropM), label: S.appRecDrop)
            }
            Text(S.appRecPermPocket).font(ski.type.text(13)).foregroundStyle(c.muted)
            Button { rec.stop(); if let e = DayStore.latest() { model.go(.day(e.start)) } } label: {
                Text(S.appRecStop).font(ski.type.display(26)).frame(maxWidth: .infinity, minHeight: 56)
            }
            .buttonStyle(.borderedProminent).tint(c.ink)
        case .denied:
            Text(S.appRecErrPerm).font(ski.type.text(16))
            Button(S.appRecSettings) { if let u = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(u) } }
        case .approximate:
            Text(S.appRecErrPrecise).font(ski.type.text(16))
            Button(S.appRecSettings) { if let u = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(u) } }
        case .interrupted:
            Text(S.appRecCutTitle).font(ski.type.display(32))
            Text(S.appRecCutText).font(ski.type.text(16))
            Button(S.appRecCutGo) { rec.resume() }.buttonStyle(.borderedProminent).tint(c.ink)
            Button(S.appRecCutClose) { rec.dismissInterrupted() }
        case .idle, .asking, .full:
            Text(S.appRecSoon).font(ski.type.text(14)).foregroundStyle(c.muted)
            Picker("", selection: $oneDescent) {
                Text(S.appRecAll).tag(false)
                Text(S.appRecOne).tag(true)
            }
            .pickerStyle(.segmented)
            Button { rec.start(oneDescent: oneDescent) } label: {
                Text(S.appRecSign).font(ski.type.display(30)).frame(maxWidth: .infinity, minHeight: 64)
            }
            .buttonStyle(.borderedProminent).tint(c.red)
            Text(S.appRecPermLocal).font(ski.type.text(13)).foregroundStyle(c.muted)
            Button(S.appDaysTitle) { model.go(.days) }.font(ski.type.text(16, .bold))
        }
    }

    private func whatNow() -> String {
        guard let ch = live?.events.last else { return "" }
        switch ch.act {
        case "lift": return S.appRecOnLift(lift: ch.lift ?? "")
        case "ski":
            if let runs = ch.runs, !runs.isEmpty { return S.appRecOnRun(run: runs.joined(separator: " · ")) + " (\(S.appRecProbably))" }
            return ch.runs == nil ? S.appRecOff : S.appRecProbably
        case "walk": return S.appRecWalking
        case "stand": return S.appRecStanding
        default: return ""
        }
    }

    private func weak(_ rec: DayRecorder) -> Bool {
        guard case .recording(let since, _) = rec.state else { return false }
        return DayRec.LiveNumbers.weakGps(now: DayRecorder.ms(Date()), lastFix: rec.last?.t ?? 0, startedAt: DayRecorder.ms(since))
    }
}

private struct Num: View {
    let value: String, label: String
    @Environment(\.ski) private var ski
    var body: some View {
        VStack(spacing: 2) {
            Text(value).font(ski.type.display(34)).monospacedDigit().minimumScaleFactor(0.6).lineLimit(1)
            Text(label).font(ski.type.text(11)).foregroundStyle(ski.colors.muted).multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 10)
        .background(ski.colors.paper)
        .overlay(Rectangle().stroke(ski.colors.ink.opacity(0.15)))
    }
}

/// Before the system's question: what is recorded and where it stays (Android's explaining sheet).
private struct PermissionSheet: View {
    let go: () -> Void
    @Environment(\.ski) private var ski
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text(S.appRecPermTitle).font(ski.type.display(34))
            Text(S.appRecPermOk).font(ski.type.text(16))
            Text(S.appRecPermPocket).font(ski.type.text(16))
            Text(S.appRecPermLocal).font(ski.type.text(16, .bold))
            Spacer()
            Button(action: go) { Text(S.appRecPermGo).font(ski.type.display(26)).frame(maxWidth: .infinity, minHeight: 52) }
                .buttonStyle(.borderedProminent).tint(ski.colors.ink)
        }
        .padding(24)
        .background(ski.colors.snow)
    }
}

extension DayStore {
    /// The newest day's entry on the phone, whatever its state.
    @MainActor static func latest() -> Entry? { (try? DayStore(dir: DayRecorder.folder))?.days().last }
}

// MARK: my days

private struct DaysScreen: View {
    @Environment(AppModel.self) private var model
    @Environment(\.ski) private var ski
    @State private var days: [DayStore.Entry] = []
    @State private var stats: [Int64: DayRec.Stats] = [:]
    /// The day (or 0: everything) waiting for its second tap.
    @State private var sure: Int64?

    var body: some View {
        let c = ski.colors
        Screen(title: S.appDaysTitle) {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if days.isEmpty { Text(S.appDaysEmpty).font(ski.type.text(16)).foregroundStyle(c.muted) }
                    ForEach(days.reversed(), id: \.start) { e in
                        HStack {
                            Button { model.go(.day(e.start)) } label: {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(dayName(e.start)).font(ski.type.text(17, .bold))
                                    Text(stats[e.start].map { S.appDaysItem(n: String($0.descents), max: DayRec.LiveNumbers.maxKmh($0.maxKmh)) } ?? "…")
                                        .font(ski.type.text(14)).foregroundStyle(c.muted)
                                }
                                .frame(maxWidth: .infinity, alignment: .leading)
                            }
                            Button(sure == e.start ? S.appDaysDeleteSure : S.appDaysDelete) {
                                if sure == e.start { DayStore.deleteDay(e); sure = nil; load() } else { sure = e.start }
                            }
                            .font(ski.type.text(14, .bold)).foregroundStyle(c.red)
                        }
                        .padding(14).background(c.paper)
                    }
                    if !days.isEmpty {
                        Button(sure == 0 ? S.appDaysDeleteAllSure : S.appDaysDeleteAll) {
                            if sure == 0 { DayStore.deleteAll(); sure = nil; load() } else { sure = 0 }
                        }
                        .font(ski.type.text(14, .bold)).foregroundStyle(c.red).padding(.top, 8)
                    }
                }
                .padding(16).frame(maxWidth: 520).foregroundStyle(c.ink)
            }
        }
        .onAppear(perform: load)
    }

    private func load() {
        days = (try? DayStore(dir: DayRecorder.folder))?.days() ?? []
        Task {
            for e in days {
                if let r = await DayEngine.shared.final(e, dir: DayRecorder.folder), let s = r.stats { stats[e.start] = s }
            }
        }
    }
}

extension DayStore {
    @MainActor static func deleteDay(_ e: Entry) { (try? DayStore(dir: DayRecorder.folder))?.delete(e) }
    @MainActor static func deleteAll() { (try? DayStore(dir: DayRecorder.folder))?.deleteAll() }
}

// MARK: the end of the day

private struct DayScreen: View {
    let start: Int64
    @Environment(\.ski) private var ski
    @State private var day: DayRec.DayModel.Day?
    @State private var failed = false

    var body: some View {
        let c = ski.colors
        Screen(title: S.appDayTitle) {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    if let day {
                        let s = day.summary
                        HStack(spacing: 10) {
                            Num(value: String(s.descents), label: S.appDaySumDescents)
                            Num(value: DayRec.LiveNumbers.metres(s.dropM), label: S.appDaySumDrop)
                            Num(value: DayRec.LiveNumbers.maxKmh(s.maxKmh), label: S.appDaySumMax)
                        }
                        if let at = s.maxAt { Text(S.appDayTopAt(time: clock(at))).font(ski.type.text(14)).foregroundStyle(c.muted) }
                        if day.chapters.isEmpty { Text(S.appDayEmpty).font(ski.type.text(16)) }
                        ForEach(Array(day.chapters.enumerated()), id: \.offset) { _, ch in
                            Text(ch.afternoon ? S.appDayAfternoon : S.appDayMorning).font(ski.type.display(28)).padding(.top, 8)
                            ForEach(Array(ch.items.enumerated()), id: \.offset) { _, it in row(it) }
                        }
                        Text(S.appDayNote).font(ski.type.text(12)).foregroundStyle(c.muted).padding(.top, 8)
                    } else if failed {
                        Text(S.appDayEmpty).font(ski.type.text(16))
                    } else {
                        ProgressView(S.appDayLoading)
                    }
                }
                .padding(16).frame(maxWidth: 520).foregroundStyle(c.ink)
            }
        }
        .task {
            let entry = (try? DayStore(dir: DayRecorder.folder))?.days().first { $0.start == start }
            guard let entry, let r = await DayEngine.shared.final(entry, dir: DayRecorder.folder) else { failed = true; return }
            day = DayRec.DayModel.build(r, zone: tbilisi)
        }
    }

    @ViewBuilder private func row(_ it: DayRec.DayModel.Item) -> some View {
        let c = ski.colors
        switch it {
        case .descent(let d):
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text(clock(d.t0)).font(ski.type.text(13)).foregroundStyle(c.muted)
                    Text(d.runs.isEmpty ? S.appRecOff : d.runs.joined(separator: " · ")).font(ski.type.text(17, .bold))
                }
                HStack(spacing: 14) {
                    Text("\(DayRec.LiveNumbers.maxKmh(d.maxKmh)) \(S.appRecUnit)")
                    Text("\(DayRec.LiveNumbers.metres(d.dropM)) m")
                    Text("\(Int(d.movingS / 60)):\(String(format: "%02d", Int(d.movingS) % 60))")
                }
                .font(ski.type.text(14))
                if d.offRuns { Text(S.appDayOff).font(ski.type.text(12)).foregroundStyle(c.muted) }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(12).background(c.paper)
            .overlay(alignment: .leading) { Rectangle().fill(Color(rgb: DayRec.SpeedScale.rgb(d.maxKmh))).frame(width: 5) }
        case .queue(_, let m, _): Text(S.appDayQueue(min: String(m))).font(ski.type.text(13)).foregroundStyle(c.muted)
        case .rest(_, let m, let long): Text(long ? S.appDayLunch(min: String(m)) : S.appDayRest(min: String(m))).font(ski.type.text(13)).foregroundStyle(c.muted)
        case .walk(_, let dist): Text(S.appDayWalk(m: DayRec.LiveNumbers.metres(dist))).font(ski.type.text(13)).foregroundStyle(c.muted)
        }
    }
}
#else
/// Plain (1.0) build: no ski day screens at all.
@ViewBuilder func dayRecScreen(_ r: Route) -> some View { EmptyView() }
#endif
