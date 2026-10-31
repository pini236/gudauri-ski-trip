import SwiftUI
import UIKit
import GudiData
import GudiDomain

/// The meeting point (13.4; design round 3 M1 to M3, round 8 MP1 to MP3, round 20), as Android's meet/MeetScreen.kt
/// and the site's #meet: the map of the lift stations, nothing picked at first ("where do we meet?"); a pin or one of
/// the three fixed spots prints the meeting card with its countdown on Gudauri's clock; cancel by the button, a tap
/// on the empty map, a second tap on the pin or the card's X, with an undo; the day and the time; and the ways to
/// share it. `station` is the route's argument: a station id, or "<station>/<HHMM>/<YYYYMMDD>" from a link.
struct MeetScreen: View {
    let station: String?
    @Environment(AppModel.self) private var app
    @Environment(\.ski) private var ski
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var art: ReliefArt?
    @State private var plan: MeetPlan?
    @State private var lines: MeetLines?
    @State private var view = MapViewport()
    @State private var sid: String?
    @State private var preset: MeetPreset?
    @State private var day: Day?
    @State private var time = Meet.defaultTime
    @State private var undo: (String, MeetPreset?)?
    @State private var toast = 0
    @State private var askTime = false
    @State private var started = false
    @State private var placed = false
    /// The screenshots only: scrolled down to the card ("<station>...+card" as the launch route).
    @State private var showCard = false

    var body: some View {
        let c = ski.colors
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let days = Meet.days(app.trip, nowMs: now)
        let pickedDay = day ?? days[0]
        let st = sid.flatMap { plan?.byId[$0] }
        Screen(title: S.navMeet) {
            GeometryReader { g in
              ScrollViewReader { scroll in
                ScrollView {
                    VStack(spacing: 0) {
                        mapBox(st).frame(height: max(300, g.size.height * 0.56))
                        Rectangle().fill(c.rule).frame(height: 1)
                        VStack(alignment: .leading, spacing: 18) {
                            when(days, pickedDay)
                            if let plan {
                                VStack(alignment: .leading, spacing: 0) {
                                    h2(S.meetOneTap)
                                    PresetPost(plan: plan, picked: preset) { p, s in time = p.time; pick(s.id, p) }
                                }
                            }
                            if let st {
                                MeetCard(station: st, time: time, day: dayLabel(pickedDay, ski.type.lang),
                                         left: Meet.left(at: Meet.at(pickedDay, time), now: now)) { clear("card") }
                                if let lines {
                                    ShareBox(station: st, time: time, day: pickedDay, lines: lines, art: art) {
                                        app.mapLift = st.name
                                        app.go(.map())
                                    }
                                }
                                SaveToGroup(station: st.id, at: Date(timeIntervalSince1970: Double(Meet.at(pickedDay, time)) / 1000))
                            } else {
                                EmptyCard()
                            }
                        }
                        .padding(.horizontal, 16).padding(.top, 16).padding(.bottom, 28)
                        Color.clear.frame(height: 1).id("end")
                    }
                }
                .onChange(of: showCard) { if showCard { scroll.scrollTo("end", anchor: .bottom) } }
              }
            }
        }
        .sheet(isPresented: $askTime) {
            TimeSheet(time: time) { t in time = t; preset = nil; askTime = false }
                .environment(\.ski, ski)
                .presentationDetents([.height(330)])
        }
        .task {
            guard !started else { return }
            started = true
            var arg = station ?? ""
            let qaCard = arg.hasSuffix("+card") || UserDefaults.standard.string(forKey: "qa.card") != nil
            if arg.hasSuffix("+card") { arg.removeLast(5) }
            let link = MeetLink.parse(route: arg)
            sid = link.station
            day = Meet.day(link.day)
            if let t = Meet.time(link.time) { time = t }
            guard let runs = app.runs else { return }
            lines = MeetLines(runs)
            let a = await ReliefArt.load(url: app.terrainURL)
            art = a
            if let a { plan = MeetPlan.build(runs, a.relief) }
            place()
            if qaCard { try? await Task.sleep(nanoseconds: 300_000_000); showCard = true }
        }
        .onChange(of: view.ready) { place() }
        .onChange(of: toast) {
            let n = toast
            Task { try? await Task.sleep(nanoseconds: 4_500_000_000); if toast == n { undo = nil } }
        }
        .onAppear { view.still = reduceMotion }
    }

    /// The first window: on the station of a link, else the whole mountain.
    private func place() {
        guard !placed, let plan, view.ready else { return }
        placed = true
        if let s = sid, plan.byId[s] == nil { sid = nil } // a link to a station not on this map (old data)
        if let s = sid.flatMap({ plan.byId[$0] }) { view.goTo(s.x, s.y, span: 1800, ms: 0) } else { fitAll(0) }
    }

    private func fitAll(_ ms: Int) {
        guard let ss = plan?.stations, !ss.isEmpty else { return }
        view.fit(minX: ss.map(\.x).min()!, minY: ss.map(\.y).min()!, maxX: ss.map(\.x).max()!, maxY: ss.map(\.y).max()!, ms: ms)
    }

    private func pick(_ id: String, _ p: MeetPreset? = nil) {
        sid = id; preset = p
        Fx.shared.tick()
        guard let s = plan?.byId[id] else { return }
        view.goTo(s.x, s.y, span: max(1400, view.span < 1500 ? view.span : 1800), ms: 650)
        let props: [String: Any] = p.map { ["kind": "preset", "preset": $0.rawValue] } ?? ["kind": "station", "station": s.name]
        Telemetry.shared.event("meet_pick", props)
    }

    /// `via`: the button, the empty map, the pin again, or the card's X (meet_clear, docs/GROWTH.md).
    private func clear(_ via: String) {
        guard let was = sid else { return }
        Telemetry.shared.event("meet_clear", ["via": via])
        undo = (was, preset); sid = nil; preset = nil; toast += 1
        Fx.shared.tick(0.3)
    }

    // MARK: - the map (M1, MP1)

    @ViewBuilder private func mapBox(_ st: Station?) -> some View {
        let c = ski.colors
        ZStack {
            c.snow
            if let plan, let lines {
                MeetMap(view: view, plan: plan, lines: lines, art: art, selected: sid,
                        onPin: { id in if id == sid { clear("pin") } else { pick(id) } }, onEmpty: { clear("map") })
                if let st { Callout(view: view, station: st) }
            } else {
                ProgressView().tint(c.muted)
            }
            // positions on the map are physical (left and top), whatever the reading direction, as on Android
            VStack(alignment: .leading, spacing: 8) {
                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 8) {
                        mapButton(S.meetWholeMountain) { fitAll(700) }
                        VStack(spacing: 0) {
                            zoomButton("plus", S.meetZoomIn) { view.zoomAt(0.6, mx: st?.x, my: st?.y, ms: 250) }
                            Rectangle().fill(c.rule).frame(width: 44, height: 1.5)
                            zoomButton("minus", S.meetZoomOut) { view.zoomAt(1 / 0.6, ms: 250) }
                        }
                        .background(c.paper).overlay(Rectangle().stroke(c.rule, lineWidth: 1.5))
                        .accessibilityElement(children: .contain).accessibilityLabel(S.meetZoom)
                    }
                    Spacer()
                    if st != nil {
                        Button { clear("button") } label: {
                            HStack(spacing: 8) {
                                LineIcon(d: LineIcon.x, size: 14, color: c.ink)
                                Text(S.meetClear).font(ski.type.text(14, .bold)).foregroundStyle(c.ink)
                                    .environment(\.layoutDirection, ski.type.lang.rtl ? .rightToLeft : .leftToRight)
                            }
                            .padding(.horizontal, 14).frame(minHeight: 44)
                            .background(c.paper).overlay(Rectangle().stroke(c.rule, lineWidth: 1.5))
                        }
                        .buttonStyle(.plain)
                    }
                }
                Spacer()
                bottomBar(st)
            }
            .padding(12)
            .environment(\.layoutDirection, .leftToRight)
        }
        .clipped()
    }

    @ViewBuilder private func bottomBar(_ st: Station?) -> some View {
        let dir: LayoutDirection = ski.type.lang.rtl ? .rightToLeft : .leftToRight
        if let u = undo {
            HStack {
                Text(S.meetCleared).font(ski.type.text(14, .bold)).foregroundStyle(.white)
                Spacer()
                Button {
                    undo = nil
                    Telemetry.shared.event("meet_undo")
                    pick(u.0, u.1)
                    if let p = u.1 { time = p.time }
                } label: {
                    Text(S.meetUndo).font(ski.type.text(15, .bold)).foregroundStyle(MeetInk.navy)
                        .padding(.horizontal, 18).frame(minHeight: 44).background(MeetInk.gold)
                }
                .buttonStyle(.plain)
            }
            .padding(.leading, 14).padding(.trailing, 8).padding(.vertical, 8)
            .background(MeetInk.navy)
            .environment(\.layoutDirection, dir)
            .transition(.move(edge: .bottom).combined(with: .opacity))
        } else if st == nil {
            HStack(spacing: 10) {
                AskDot()
                Text(S.meetHintPick(n: String(plan?.stations.count ?? 0))).font(ski.type.text(14, .bold)).foregroundStyle(.white)
                    .fixedSize(horizontal: false, vertical: true)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 14).padding(.vertical, 10)
            .background(MeetInk.navy)
            .environment(\.layoutDirection, dir)
        }
    }

    private func mapButton(_ text: String, _ action: @escaping () -> Void) -> some View {
        let c = ski.colors
        return Button(action: action) {
            Text(text).font(ski.type.text(14, .semibold)).foregroundStyle(c.ink)
                .padding(.horizontal, 14).frame(minHeight: 44)
                .background(c.paper).overlay(Rectangle().stroke(c.rule, lineWidth: 1.5))
        }
        .buttonStyle(.plain)
    }

    private func zoomButton(_ symbol: String, _ label: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol).font(.system(size: 18, weight: .semibold)).foregroundStyle(ski.colors.ink).frame(width: 44, height: 44)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }

    // MARK: - when (M2)

    private func h2(_ t: String) -> some View {
        Text(t).font(ski.type.display(26)).foregroundStyle(ski.colors.ink).padding(.bottom, 8).accessibilityAddTraits(.isHeader)
    }

    @ViewBuilder private func when(_ days: [Day], _ picked: Day) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            h2(S.meetWhen)
            ForEach(Array(stride(from: 0, to: days.count, by: 4)), id: \.self) { i in
                HStack(spacing: 6) {
                    ForEach(i..<min(i + 4, days.count), id: \.self) { j in
                        let d = days[j]
                        MeetChip(text: dayLabel(d, ski.type.lang), on: d == picked, big: false) { day = d; preset = nil }
                    }
                    ForEach(0..<(4 - min(4, days.count - i)), id: \.self) { _ in Color.clear.frame(maxWidth: .infinity, maxHeight: 1) }
                }
                .padding(.bottom, 6)
            }
            // round 20: "other time" is a clock block at the end of the times row; a time picked there shows in it
            HStack(spacing: 6) {
                ForEach(Meet.times, id: \.self) { t in
                    MeetChip(text: t.text, on: t == time, big: true) { time = t; preset = nil }
                }
                if Meet.times.contains(time) {
                    Button { askTime = true } label: {
                        LineIcon(d: LineIcon.clockOther, size: 22, color: ski.colors.ink)
                            .frame(maxWidth: .infinity, minHeight: 48)
                            .background(ski.colors.paper)
                            .overlay(Rectangle().inset(by: 0.75).stroke(ski.colors.ink, style: StrokeStyle(lineWidth: 1.5, dash: [4, 3])))
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(S.meetOtherTime)
                } else {
                    MeetChip(text: time.text, on: true, big: true) { askTime = true }
                        .accessibilityLabel("\(S.meetOtherTime) \(time.text)")
                }
            }
            .padding(.top, 2)
            .environment(\.layoutDirection, .leftToRight)
        }
    }
}

/// The day on a chip, as the site writes it: "ו׳ 2.10" in Hebrew, "Fri 2 Oct" elsewhere.
func dayLabel(_ d: Day, _ lang: Lang) -> String {
    let f = DateFormatter()
    f.timeZone = TimeZone(identifier: "UTC")
    f.locale = Locale(identifier: lang.rawValue)
    let date = Date(timeIntervalSince1970: Double(d.epochDay) * 86_400 + 43_200)
    if lang == .he { f.dateFormat = "EEEEE"; return f.string(from: date) + " \(d.day).\(d.month)" }
    f.dateFormat = "EEE d MMM"
    return f.string(from: date)
}

/// "התחנה התחתונה של Goodaura ו-New Goodaura" (the site's where); `isolate` keeps Latin names whole in a sentence.
func stationWhere(_ s: Station, isolate: Bool = true) -> String {
    func iso(_ n: String) -> String { isolate ? "\u{2068}\(n)\u{2069}" : n }
    let join = S.meetLiftNamesJoin
    var parts: [String] = []
    if !s.bottomOf.isEmpty { parts.append(S.meetWhereBottomStation(lifts: s.bottomOf.map(iso).joined(separator: join))) }
    if !s.topOf.isEmpty { parts.append(S.meetWhereTopStation(lifts: s.topOf.map(iso).joined(separator: join))) }
    return parts.joined(separator: ", ")
}

/// A day or a time to pick (the site's .mchip): 48 high, an ink border; the picked one gold, lifted, with a hard shadow.
struct MeetChip: View {
    let text: String
    let on: Bool
    let big: Bool
    let action: () -> Void
    @Environment(\.ski) private var ski

    var body: some View {
        let c = ski.colors
        Button(action: action) {
            Text(text)
                .font(big ? ski.type.display(21) : ski.type.text(13.5, .bold))
                .foregroundStyle(on ? MeetInk.navy : c.ink)
                .lineLimit(1).minimumScaleFactor(0.7)
                .frame(maxWidth: .infinity, minHeight: 48)
                .background(on ? MeetInk.gold : c.paper)
                .overlay(Rectangle().stroke(c.ink, lineWidth: 1.5))
                .background(Rectangle().fill(c.ink).offset(y: on ? 4 : 0))
                .offset(y: on ? -3 : 0)
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(on ? .isSelected : [])
    }
}

/// The gold dot that asks where to meet (meet-hint .ask).
private struct AskDot: View {
    var body: some View {
        TimelineView(.animation) { t in
            let f = t.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 1.6) / 1.6
            ZStack {
                Circle().fill(MeetInk.gold.opacity(0.3 * (1 - f))).frame(width: 10 + 12 * f, height: 10 + 12 * f)
                Circle().fill(MeetInk.gold).frame(width: 10, height: 10)
            }
            .frame(width: 22, height: 22)
        }
        .accessibilityHidden(true)
    }
}

/// The dark label over the picked pin (meet-callout): its name only (round 20).
private struct Callout: View {
    let view: MapViewport
    let station: Station
    @Environment(\.ski) private var ski

    var body: some View {
        if view.ready {
            let x = view.sx(station.x), y = view.sy(station.y)
            let left = min(max(x - 100, 8), max(8, view.w - 208))
            let top = max(6, y - 110)
            let tip = min(max(x - left - 8, 12), 184)
            ZStack(alignment: .topLeading) {
                Text(station.name).font(ski.type.display(30)).foregroundStyle(.white).lineLimit(1)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 12).padding(.top, 8).padding(.bottom, 9)
                    .background(MeetInk.navy)
                    .shadow(color: .black.opacity(0.3), radius: 8, y: 3)
                    .overlay(alignment: .bottomLeading) {
                        Rectangle().fill(MeetInk.navy).frame(width: 16, height: 16).rotationEffect(.degrees(45))
                            .offset(x: tip, y: 8)
                    }
            }
            .frame(width: 200)
            .position(x: left + 100, y: top + 26)
            .environment(\.layoutDirection, .leftToRight)
            .allowsHitTesting(false)
            .accessibilityHidden(true)
        }
    }
}

/// The three spots as signs on a post (mp-sign): gold, blue and ink, each a little shorter.
private struct PresetPost: View {
    let plan: MeetPlan
    let picked: MeetPreset?
    let onPick: (MeetPreset, Station) -> Void
    @Environment(\.ski) private var ski
    @Environment(\.layoutDirection) private var dir

    var body: some View {
        let c = ski.colors
        let spec: [(MeetPreset, String, CGFloat)] = [(.morning, S.meetPresetMorningLift, 0.92), (.noon, S.meetPresetNoon, 0.84), (.end, S.meetPresetEndOfDay, 0.76)]
        GeometryReader { g in
            VStack(alignment: .leading, spacing: 8) {
                ForEach(spec, id: \.0) { item in
                    let (p, label, width) = item
                    if let s = plan.preset(p) {
                        let on = picked == p
                        let (bg, fg): (Color, Color) = p == .morning ? (MeetInk.gold, MeetInk.navy) : p == .noon ? (c.blue, c.onBoard) : (c.ink, c.paper)
                        Button { onPick(p, s) } label: {
                            HStack {
                                Text(label).font(ski.type.text(14, .bold)).foregroundStyle(fg).lineLimit(1)
                                Spacer(minLength: 6)
                                Text("\(s.name) \(p.time.text)").font(ski.type.display(23)).foregroundStyle(fg).lineLimit(1)
                                    .minimumScaleFactor(0.7).environment(\.layoutDirection, .leftToRight)
                            }
                            .padding(.leading, 16).padding(.trailing, 30)
                            .frame(width: (g.size.width - 14) * width, alignment: .leading)
                            .frame(minHeight: 48)
                            .background(bg, in: SignShape(arrow: 18, rtl: dir == .rightToLeft))
                            .overlay(alignment: .bottom) { if on { Rectangle().fill(.white.opacity(0.55)).frame(height: 5) } }
                            .clipShape(SignShape(arrow: 18, rtl: dir == .rightToLeft))
                            .contentShape(SignShape(arrow: 18, rtl: dir == .rightToLeft))
                        }
                        .buttonStyle(.plain)
                        .offset(x: on ? (dir == .rightToLeft ? 10 : -10) : 0)
                        .accessibilityAddTraits(on ? .isSelected : [])
                    }
                }
            }
            .padding(.leading, 14)
            .background(alignment: .topLeading) { Rectangle().fill(c.ink).frame(width: 7).padding(.top, -8).padding(.bottom, -12) }
        }
        .frame(height: 3 * 48 + 2 * 8)
    }
}

/// MP2: the card waits, a dashed outline with an empty pin.
private struct EmptyCard: View {
    @Environment(\.ski) private var ski
    var body: some View {
        let c = ski.colors
        HStack(spacing: 14) {
            LineIcon(d: LineIcon.pin, size: 34, color: c.muted)
            VStack(alignment: .leading, spacing: 4) {
                Text(S.meetEmptyTitle).font(ski.type.display(30)).foregroundStyle(c.ink)
                Text(S.meetEmptyBody).font(ski.type.text(13)).foregroundStyle(c.muted).fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 16).padding(.vertical, 18)
        .overlay(Rectangle().stroke(c.dash, style: StrokeStyle(lineWidth: 2, dash: [6, 4])))
        .rotationEffect(.degrees(-1.5))
        .padding(.vertical, 6)
    }
}

/// M3: the meeting card, a boarding pass of the mountain with its stub (mcard), tilted, with the X to cancel (MP3).
private struct MeetCard: View {
    let station: Station
    let time: ClockTime
    let day: String
    let left: Meet.Left
    let onCancel: () -> Void
    @Environment(\.ski) private var ski
    @Environment(\.layoutDirection) private var dir

    /// The countdown on the stub, its number big in the middle (the site's slots()).
    private var slots: (String, String, String) {
        func split(_ text: String, _ n: String) -> (String, String, String) {
            guard let r = text.range(of: n) else { return ("", text, "") }
            return (String(text[..<r.lowerBound]).trimmingCharacters(in: .whitespaces), n, String(text[r.upperBound...]).trimmingCharacters(in: .whitespaces))
        }
        switch left {
        case .passed: return ("", S.meetCountdownPassed, "")
        case .minutes(let n): return split(S.meetCountdownMinutes(count: n), String(n))
        case .hours(let hm): let (a, _, b) = split(S.meetCountdownHours(hm: "\u{1}"), "\u{1}"); return (a, hm, b)
        case .days(let n): return split(S.meetCountdownDays(count: n), String(n))
        }
    }

    var body: some View {
        let c = ski.colors
        let (c1, c2, c3) = slots
        let rtl = dir == .rightToLeft
        ZStack(alignment: .topTrailing) {
            VStack(spacing: 0) {
                HStack {
                    Text(S.meetCard).font(ski.type.display(24)).foregroundStyle(.white)
                    Spacer()
                    // clear of the X that sits on the card's corner
                    Text(S.meetCardSub).font(ski.type.text(12)).foregroundStyle(MeetInk.fog).padding(.trailing, 30)
                }
                .padding(.horizontal, 16).padding(.vertical, 10).background(MeetInk.navy)
                // round 20: only the station and the time, the day over the time
                HStack(alignment: .bottom) {
                    Text(station.name).font(ski.type.display(34)).foregroundStyle(c.onBoard).lineLimit(1).minimumScaleFactor(0.6)
                        .environment(\.layoutDirection, .leftToRight)
                        .padding(.leading, 14).padding(.trailing, 28).padding(.vertical, 5)
                        .background(c.blue, in: SignShape(arrow: 18, rtl: rtl))
                    Spacer(minLength: 12)
                    VStack(alignment: .trailing, spacing: 0) {
                        Text(day).font(ski.type.text(12)).foregroundStyle(c.muted)
                        Text(time.text).font(ski.type.display(44)).foregroundStyle(c.ink)
                    }
                }
                .padding(.horizontal, 16).padding(.top, 12).padding(.bottom, 14)
                .overlay(Rectangle().stroke(c.rule, lineWidth: 1))
                VStack(spacing: 0) {
                    if !c1.isEmpty { Text(c1).font(ski.type.text(13, .bold)).foregroundStyle(MeetInk.navy) }
                    Text(c2).font(ski.type.display(64)).foregroundStyle(MeetInk.navy).lineLimit(1).minimumScaleFactor(0.5)
                    if !c3.isEmpty { Text(c3).font(ski.type.text(13, .bold)).foregroundStyle(MeetInk.navy) }
                }
                .frame(maxWidth: .infinity).frame(height: 112).background(MeetInk.gold)
            }
            .background(c.paper)
            .overlay(alignment: .bottom) {
                // the punched notches where the stub tears off, in the page's colour
                HStack { Circle().fill(c.snow).frame(width: 22, height: 22).offset(x: -11); Spacer(); Circle().fill(c.snow).frame(width: 22, height: 22).offset(x: 11) }
                    .offset(y: -112 + 11)
            }
            .compositingGroup()
            .shadow(color: .black.opacity(0.18), radius: 10, y: 5)
            .rotationEffect(.degrees(-2))
            Button(action: onCancel) {
                LineIcon(d: LineIcon.x, size: 14, color: c.ink)
                    .frame(width: 44, height: 44)
                    .background(Circle().fill(c.paper))
                    .overlay(Circle().stroke(c.ink, lineWidth: 2))
                    .shadow(color: .black.opacity(0.2), radius: 3, y: 1)
            }
            .buttonStyle(.plain)
            .offset(x: rtl ? -6 : 6, y: -8)
            .accessibilityLabel(S.meetCancel)
        }
        .padding(.top, 6).padding(.bottom, 4)
        .accessibilityElement(children: .contain)
    }
}

/// M3's ways to share: WhatsApp, the picture, the link, and the run map (round 20: four icon buttons, WhatsApp filled;
/// their words stay for a screen reader). Saving in a group comes with the group part.
private struct ShareBox: View {
    let station: Station
    let time: ClockTime
    let day: Day
    let lines: MeetLines
    let art: ReliefArt?
    let onMap: () -> Void
    @Environment(\.ski) private var ski
    @Environment(\.openURL) private var openURL
    @State private var copied = false
    @State private var sharing: [Any]?

    var body: some View {
        let dayText = dayLabel(day, ski.type.lang)
        let link = Meet.sharedLink(station.id, time, day)
        let alt = station.h.map { S.meetShareAltSuffix(n: Self.thousands($0)) } ?? ""
        let message = S.meetShareMessage(place: station.name, day: dayText, time: time.text, where: stationWhere(station, isolate: false), alt: alt, link: link)
        HStack(spacing: 10) {
            icon(LineIcon.whatsapp, S.meetSendWa, filled: true) {
                var cs = URLComponents(string: "https://wa.me/")!
                cs.queryItems = [URLQueryItem(name: "text", value: message)]
                if let u = cs.url { openURL(u) { ok in if ok { Task { @MainActor in Telemetry.shared.event("meet_share", ["method": "whatsapp"]) } } } }
            }
            icon(LineIcon.image, S.meetShareImage) {
                let v = MeetImageView(station: station, time: time.text, label: S.meetShareImageCardLabel(day: dayText), site: S.commonSiteName,
                                      lines: lines, art: art, type: ski.type)
                if let img = MeetImageView.render(v) { sharing = [img, message]; Telemetry.shared.event("meet_share", ["method": "image"]) }
            }
            icon(copied ? LineIcon.check : LineIcon.link, copied ? S.commonLinkCopied : S.meetCopyLink) {
                UIPasteboard.general.string = link
                Telemetry.shared.event("meet_share", ["method": "copy"])
                Fx.shared.tick()
                copied = true
                Task { try? await Task.sleep(nanoseconds: 2_200_000_000); copied = false }
            }
            icon(LineIcon.map, S.meetOnMap, action: onMap)
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 4)
        .sheet(isPresented: Binding(get: { sharing != nil }, set: { if !$0 { sharing = nil } })) {
            ActivitySheet(items: sharing ?? []).presentationDetents([.medium, .large]).ignoresSafeArea()
        }
    }

    static func thousands(_ n: Int) -> String {
        let f = NumberFormatter(); f.locale = Locale(identifier: "en_US"); f.numberStyle = .decimal
        return f.string(from: NSNumber(value: n)) ?? String(n)
    }

    private func icon(_ d: [String], _ label: String, filled: Bool = false, action: @escaping () -> Void) -> some View {
        let c = ski.colors
        return Button(action: action) {
            LineIcon(d: d, size: 22, color: filled ? c.paper : c.ink)
                .frame(width: 48, height: 48)
                .background(filled ? c.ink : c.paper)
                .overlay(Rectangle().stroke(c.ink, lineWidth: 1.5))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}

/// The system's share sheet.
struct ActivitySheet: UIViewControllerRepresentable {
    let items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController { UIActivityViewController(activityItems: items, applicationActivities: nil) }
    func updateUIViewController(_ c: UIActivityViewController, context: Context) {}
}

/// "Other time": a clock in a sheet (any minute, as Android).
private struct TimeSheet: View {
    @State var time: ClockTime
    let onPick: (ClockTime) -> Void
    @Environment(\.ski) private var ski
    @Environment(\.dismiss) private var dismiss
    @State private var date = Date()

    var body: some View {
        let c = ski.colors
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(S.meetOtherTime).font(ski.type.display(30)).foregroundStyle(c.ink)
                Spacer()
                Button { dismiss() } label: {
                    Image(systemName: "xmark").font(.system(size: 18, weight: .semibold)).foregroundStyle(c.ink).frame(width: 44, height: 44)
                }
                .accessibilityLabel(S.commonClose)
            }
            DatePicker("", selection: $date, displayedComponents: .hourAndMinute)
                .datePickerStyle(.wheel).labelsHidden()
                .environment(\.locale, Locale(identifier: "en_GB"))
                .environment(\.timeZone, TimeZone(identifier: "UTC")!)
                .frame(maxWidth: .infinity)
            Button {
                var cal = Calendar(identifier: .gregorian); cal.timeZone = TimeZone(identifier: "UTC")!
                let h = cal.component(.hour, from: date), m = cal.component(.minute, from: date)
                onPick(ClockTime(h, m) ?? time)
            } label: {
                Text(S.appPickOk).font(ski.type.text(16, .bold)).foregroundStyle(c.paper)
                    .frame(maxWidth: .infinity, minHeight: 48).background(c.ink)
            }
            .buttonStyle(.plain)
        }
        .padding(16)
        .background(c.snow)
        .onAppear {
            date = Date(timeIntervalSince1970: Double(time.minutes * 60))
        }
    }
}


/// "Save in the group" (round 14, Android's SaveButton): only for someone in a group; to the only group, or a choice
/// among mine. Offline it waits in the write queue on the phone, and the reminders follow from the group's meetups.
private struct SaveToGroup: View {
    let station: String
    let at: Date
    @Environment(\.ski) private var ski
    @State private var groups: [GroupSummary] = []
    @State private var choose = false
    @State private var saved = false
    @State private var error: String?

    var body: some View {
        if !groups.isEmpty {
            VStack(spacing: 6) {
                Button { tap() } label: {
                    Text(saved ? S.meetSavedGroup : S.meetSaveGroup).font(ski.type.text(15, .bold)).foregroundStyle(ski.colors.snow)
                        .frame(maxWidth: .infinity, minHeight: 48).background(ski.colors.ink)
                }
                .buttonStyle(.plain)
                if choose {
                    Text(S.meetPickGroup).font(ski.type.text(13)).foregroundStyle(ski.colors.muted).frame(maxWidth: .infinity, alignment: .leading)
                    ForEach(groups) { g in
                        Button { save(g.id) } label: {
                            Text(g.name).font(ski.type.text(14, .bold)).foregroundStyle(ski.colors.ink)
                                .frame(maxWidth: .infinity, minHeight: 44).overlay(Rectangle().stroke(ski.colors.ink, lineWidth: 1.5))
                        }
                        .buttonStyle(.plain)
                    }
                }
                if let error { Text(error).font(ski.type.text(13)).foregroundStyle(ski.colors.red) }
            }
            .padding(.top, 8)
        } else {
            Color.clear.frame(height: 0).task { groups = await GroupService.shared.myGroups() }
        }
    }

    private func tap() {
        if groups.count == 1 { save(groups[0].id) } else { choose.toggle() }
    }

    private func save(_ id: String) {
        Task {
            do {
                try await GroupService.shared.addMeetup(groupId: id, station: station, at: at)
                Fx.shared.tick(); saved = true; choose = false; error = nil
                try? await Task.sleep(nanoseconds: 2_500_000_000); saved = false
            } catch let e as ApiError { self.error = errorText(e.code) } catch { self.error = errorText("general") }
        }
    }
}
