import SwiftUI
import GudiData
import GudiDomain

/// The map's panel (13.3), as Android's map/RunSheet.kt and the site's map panel on the phone, after the approved run
/// view (round 3, T1 to T4; round 20): a chosen run's sign with its number, the steps to the run before and after (also
/// a swipe), share, the three numbers, the profile that moves a dot on the mountain, "what's ahead", and the videos.
/// iPhone style: it lives in a sheet over the map (MapScreen), at a small height that shows the head, or taller.

private func colorName(_ c: String) -> String {
    switch c { case "green": S.commonColorGreen; case "blue": S.commonColorBlue; case "red": S.commonColorRed; default: S.commonColorBlack }
}

private func rateName(_ c: String) -> String {
    switch c { case "green": S.mapDifficultyBeginner; case "blue": S.mapDifficultyEasy; case "red": S.mapDifficultyIntermediate; default: S.mapDifficultyHard }
}

private func rate(_ c: String) -> Int { switch c { case "green": 1; case "blue": 2; case "red": 3; default: 4 } }

private func osmGrade(_ g: String) -> String? {
    switch g {
    case "novice": S.runOsmGradeNovice; case "easy": S.runOsmGradeEasy; case "intermediate": S.runOsmGradeIntermediate
    case "advanced": S.runOsmGradeAdvanced; case "expert": S.runOsmGradeExpert; default: nil
    }
}

/// A heading inside the panel (the site's h3).
struct H3: View {
    let text: String
    @Environment(\.ski) private var ski
    init(_ text: String) { self.text = text }
    var body: some View {
        Text(text).font(ski.type.display(26)).foregroundStyle(ski.colors.ink)
            .padding(.top, 18).padding(.bottom, 6).accessibilityAddTraits(.isHeader)
    }
}

struct Hint: View {
    let text: String
    @Environment(\.ski) private var ski
    init(_ text: String) { self.text = text }
    var body: some View { Text(text).font(ski.type.text(13)).foregroundStyle(ski.colors.muted).padding(.top, 6).fixedSize(horizontal: false, vertical: true) }
}

/// A row of the details (the site's dl.kv): the label, and its value.
struct Kv<V: View>: View {
    let label: String
    @ViewBuilder let value: V
    @Environment(\.ski) private var ski
    var body: some View {
        HStack(alignment: .top, spacing: 8) {
            Text(label).font(ski.type.text(13)).foregroundStyle(ski.colors.muted).frame(width: 118, alignment: .leading)
            value.frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.vertical, 4)
        .accessibilityElement(children: .combine)
    }
}

extension Kv where V == KvText {
    init(_ label: String, _ value: String) { self.label = label; self.value = KvText(text: value) }
}

struct KvText: View {
    let text: String
    @Environment(\.ski) private var ski
    var body: some View { Text(text).font(ski.type.text(15)).foregroundStyle(ski.colors.ink).fixedSize(horizontal: false, vertical: true) }
}

/// A run or lift to go to (the site's .tag): the run's colour at its start, or the lift's arrow.
struct Tag: View {
    let text: String
    let color: Color?
    let action: () -> Void
    @Environment(\.ski) private var ski
    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                if let color { Rectangle().fill(color).frame(width: 10, height: 10) } else { Text("⇡").font(ski.type.text(15, .bold)) }
                Text(text).font(ski.type.text(14, .bold)).environment(\.layoutDirection, .leftToRight)
            }
            .foregroundStyle(ski.colors.ink)
            .padding(.horizontal, 10).frame(minHeight: 44)
            .background(ski.colors.snow).overlay(Rectangle().stroke(ski.colors.rule, lineWidth: 1.5))
        }
        .buttonStyle(.plain)
    }
}

/// Four squares, filled to the run's difficulty (the site's pips).
private struct Pips: View {
    let color: String
    @Environment(\.ski) private var ski
    var body: some View {
        let col = ski.colors.run(color)
        HStack(spacing: 3) {
            ForEach(1...4, id: \.self) { i in
                Rectangle().fill(i <= rate(color) ? col : .clear).frame(width: 10, height: 10).overlay(Rectangle().stroke(col, lineWidth: 1.5))
            }
        }
        .padding(.trailing, 8)
    }
}

struct Stat: View {
    let label: String, value: String
    @Environment(\.ski) private var ski
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(label).font(ski.type.text(12)).foregroundStyle(ski.colors.muted)
            Text(value).font(ski.type.display(28)).foregroundStyle(ski.colors.ink).environment(\.layoutDirection, .leftToRight)
        }
        .accessibilityElement(children: .combine)
    }
}

/// The head of a run's panel: the sign (T4), its number, the numbers, the steps to the run before and after, share,
/// and the fly down.
struct RunHead: View {
    let p: Piste
    let model: MapModel
    let flying: Bool
    let canFly: Bool
    let onFly: () -> Void
    let onStop: () -> Void
    @Environment(\.ski) private var ski
    @Environment(\.layoutDirection) private var dir
    @Environment(\.accessibilityReduceMotion) private var still

    var body: some View {
        let order = model.order
        let i = order.firstIndex(of: p.key)
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 10) {
                Text(runName(p)).font(ski.type.display(32)).foregroundStyle(.white).lineLimit(1).minimumScaleFactor(0.6)
                    .environment(\.layoutDirection, .leftToRight)
                    .padding(.leading, 14).padding(.trailing, 26).padding(.top, 6).padding(.bottom, 4)
                    .background(ski.colors.run(p.color), in: SignShape(arrow: 16, rtl: dir == .rightToLeft))
                    .accessibilityAddTraits(.isHeader)
                if let ref = p.refs.first {
                    // the text face: the display face draws 7 like a Hebrew letter
                    Text(ref).font(ski.type.text(20, .bold)).foregroundStyle(Color(rgb: 0x13233A))
                        .padding(.horizontal, 8).padding(.vertical, 2).background(Color.white)
                        .overlay(Rectangle().stroke(ski.colors.ink, lineWidth: 2))
                        .accessibilityLabel(S.runRefTitle + " " + ref)
                }
            }
            Text(colorName(p.color) + (p.named ? S.runColorOfficialSuffix : S.runColorOsmSuffix) + " · " + rateName(p.color))
                .font(ski.type.text(13)).foregroundStyle(ski.colors.muted).padding(.top, 6)
            // length, drop, steepest stretch (T4's three numbers)
            HStack(spacing: 18) {
                Stat(label: S.commonLengthLabel, value: Fmt.length(Int(p.len)))
                if let f = model.facts {
                    Stat(label: S.runStatDropLabel, value: Fmt.metres(f.drop))
                    Stat(label: S.runStatSteepLabel, value: Fmt.n(Fmt.round(f.maxG * 100)) + "%")
                }
            }
            .padding(.top, 8)
            // before, share, after (the site's run-nav); a run outside the list only shares
            HStack(spacing: 6) {
                if let i {
                    let prev = order[(i - 1 + order.count) % order.count], next = order[(i + 1) % order.count]
                    nav("\(dir == .rightToLeft ? "→" : "←") \(prev)", S.runNavPrevAria(run: prev)) { model.select(prev, via: "swipe") }
                    share
                    nav("\(next) \(dir == .rightToLeft ? "←" : "→")", S.runNavNextAria(run: next)) { model.select(next, via: "swipe") }
                } else { share }
            }
            .padding(.top, 10)
            .accessibilityElement(children: .contain).accessibilityLabel(S.runNavGroupAria)
            // with reduced motion there is no fly down, as on the site and Android (A-12)
            if canFly, model.facts != nil, flying || !still {
                Button(action: flying ? onStop : onFly) {
                    Text(flying ? S.runFlyStop : S.runFlyButton).font(ski.type.text(15, .bold))
                        .foregroundStyle(flying ? .white : ski.colors.snow)
                        .frame(maxWidth: .infinity, minHeight: 44).background(flying ? ski.colors.red : ski.colors.ink)
                }
                .buttonStyle(.plain).padding(.top, 8)
            }
        }
        // a swipe across the head goes to the next or the previous run (left is the next one, as on the site)
        .contentShape(Rectangle())
        .gesture(DragGesture(minimumDistance: 30).onEnded { g in
            guard let i, abs(g.translation.width) > 70, abs(g.translation.width) > abs(g.translation.height) else { return }
            let step = g.translation.width < 0 ? 1 : -1
            model.select(order[(i + step + order.count) % order.count], via: "swipe")
            Fx.shared.tick()
        })
    }

    private func nav(_ text: String, _ label: String, _ go: @escaping () -> Void) -> some View {
        Button(action: go) {
            Text(text).font(ski.type.text(14, .bold)).foregroundStyle(ski.colors.ink).lineLimit(1).minimumScaleFactor(0.7)
                .environment(\.layoutDirection, .leftToRight)
                .frame(maxWidth: .infinity, minHeight: 44).padding(.horizontal, 6)
                .overlay(Rectangle().stroke(ski.colors.rule, lineWidth: 1.5))
        }
        .buttonStyle(.plain).accessibilityLabel(label)
    }

    private var share: some View {
        ShareLink(item: runLink(p.key), subject: Text(S.runShareTitle(run: p.key)), message: Text(S.runShareTitle(run: p.key))) {
            Text(S.runShareButton).font(ski.type.text(14, .bold)).foregroundStyle(ski.colors.snow)
                .frame(maxWidth: .infinity, minHeight: 44).background(ski.colors.glacier)
        }
        .buttonStyle(.plain)
        // the system's share sheet opens (Android's run_share with "native")
        .simultaneousGesture(TapGesture().onEnded { Telemetry.shared.event("run_share", ["run": p.key, "method": "native"]) })
    }
}

/// The rest of a run's panel: the details, the profile and "what's ahead" (T2, T3), and the videos. Round 20 (decisions
/// 64, 65): no connections, notes or source and certainty here; the source stays in the data.
struct RunBody: View {
    let p: Piste
    let model: MapModel
    var flyAt: (() -> Double?)? = nil
    var weather: WeatherNow? = nil
    @Environment(\.ski) private var ski

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if let f = model.facts {
                Kv(S.runStatAltitudeLabel, S.runStatAltitudeValue(top: Fmt.n(f.top), bot: Fmt.n(f.bot)))
                Kv(S.runStatDropLabel, Fmt.metres(f.drop) +
                   (p.lines.count == 1 && p.len > 0 ? " · " + S.runStatAvgGradient(pct: Fmt.n(Fmt.round(Double(f.drop) * 100 / p.len))) : ""))
                Kv(label: S.runStatSteepLabel) {
                    VStack(alignment: .leading, spacing: 2) {
                        KvText(text: Fmt.n(Fmt.round(f.maxG * 100)) + "%")
                        Text(S.runStatSteepHint).font(ski.type.text(13)).foregroundStyle(ski.colors.muted)
                    }
                }
            }
            Kv(label: S.runDifficultyLabel) { HStack(spacing: 0) { Pips(color: p.color); KvText(text: rateName(p.color)) } }
            if !p.osmDiff.isEmpty { Kv(S.runOsmGradeLabel, p.osmDiff.map { osmGrade($0) ?? S.runOsmGradeNone }.joined(separator: " / ")) }
            if !p.refs.isEmpty { Kv(S.runRefLabel, p.refs.joined(separator: ", ")) }
            if !p.groom.isEmpty { Kv(S.runGroomingLabel, p.groom.contains("classic") ? S.runGroomedValue : p.groom.joined(separator: ", ")) }
            // the forecast at the run's top and bottom (round 19), after the details and before the profile
            if let f = model.facts, p.named, let weather { RunConditions(top: f.top, bottom: f.bot, w: weather, run: p.key) }
            if let f = model.facts, f.points.count >= 4 { RunProfile(p: p, f: f, model: model, flyAt: flyAt) }
            if p.named { Videos(p: p, list: Video.of(model.videos, key: p.key)) }
        }
    }
}

/// The (i) of round 20: a small ring with an i, 44 to touch, that shows or hides a note.
struct InfoDot: View {
    let label: String
    @Binding var open: Bool
    @Environment(\.ski) private var ski
    var body: some View {
        Button { open.toggle() } label: {
            Text("i").font(ski.type.text(12, .bold)).foregroundStyle(open ? ski.colors.ink : ski.colors.muted)
                .frame(width: 22, height: 22).overlay(Circle().stroke(open ? ski.colors.ink : ski.colors.muted, lineWidth: 1.5))
                .frame(width: 44, height: 44)
        }
        .buttonStyle(.plain).accessibilityLabel(label).accessibilityValue(open ? "+" : "")
    }
}

/// The elevation profile (T2): drag along it and the dot moves on the mountain; then "what's ahead" (T3).
private struct RunProfile: View {
    let p: Piste
    let f: RunFacts
    let model: MapModel
    let flyAt: (() -> Double?)?
    @State private var at = 0
    @State private var info = false
    @Environment(\.ski) private var ski

    /// The run whose profile was dragged: run_profile_scrub goes once each time a run is opened (docs/GROWTH.md).
    @State private var scrubbed: String?

    private func go(_ i: Int) {
        if scrubbed != p.key { scrubbed = p.key; Telemetry.shared.event("run_profile_scrub", ["run": p.key]) }
        guard i != at else { return }
        at = i
        model.marker = (f.points[i].x, f.points[i].y)
        Fx.shared.tick()
    }

    private func nearest(_ d: Double) -> Int {
        var best = 0, bd = Double.infinity
        for (i, q) in f.points.enumerated() where abs(q.d - d) < bd { bd = abs(q.d - d); best = i }
        return best
    }

    var body: some View {
        let pts = f.points
        let hmax = pts.map(\.h).max()!, hmin = pts.map(\.h).min()!, dmax = max(f.length, 1)
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 0) { H3(S.runProfileHeading); InfoDot(label: S.runProfileInfoAria, open: $info) }
            if info { Hint(S.runProfileHint); Hint(S.runElevationAccuracyHint).padding(.bottom, 6) }
            // the profile reads from the top of the run, at the left, in every language (as on the site)
            GeometryReader { g in
                let w = g.size.width, h = g.size.height - 18, pad = 8.0
                let x = { (d: Double) in pad + d / dmax * (w - 2 * pad) }
                let y = { (v: Double) in pad + (hmax - v) / max(hmax - hmin, 1) * (h - 30) }
                let base = h - 16
                ZStack(alignment: .topLeading) {
                    Canvas { c, _ in
                        if f.steepG > 0 {
                            c.fill(Path(CGRect(x: x(f.steepD), y: 0, width: x(pts[f.steepJ].d) - x(f.steepD), height: base)), with: .color(Color(rgb: 0xDC3B33, opacity: 0.2)))
                        }
                        var line = Path(), fill = Path()
                        fill.move(to: CGPoint(x: pad, y: base))
                        for (i, q) in pts.enumerated() {
                            let pt = CGPoint(x: x(q.d), y: y(q.h))
                            if i == 0 { line.move(to: pt) } else { line.addLine(to: pt) }
                            fill.addLine(to: pt)
                        }
                        fill.addLine(to: CGPoint(x: w - pad, y: base)); fill.closeSubpath()
                        c.fill(fill, with: .color(ski.colors.ink.opacity(0.13)))
                        c.stroke(line, with: .color(ski.colors.ink), style: StrokeStyle(lineWidth: 2, lineCap: .round))
                        // the slope band under it, in the slope colours
                        var i = 0
                        while i < pts.count - 1 {
                            let col = SlopeColors.hex(forDegrees: pts[i].a)
                            var j = i + 1
                            while j < pts.count - 1 && SlopeColors.hex(forDegrees: pts[j].a) == col { j += 1 }
                            c.fill(Path(CGRect(x: x(pts[i].d), y: h - 14, width: x(pts[j].d) - x(pts[i].d) + 0.6, height: 7)), with: .color(Color(hex: col)))
                            i = j
                        }
                        let q = pts[at]
                        c.fill(Path(CGRect(x: x(q.d) - 0.75, y: 0, width: 1.5, height: h - 8)), with: .color(ski.colors.glacier))
                        c.fill(Path(ellipseIn: CGRect(x: x(q.d) - 7, y: y(q.h) - 7, width: 14, height: 14)), with: .color(ski.colors.paper))
                        c.fill(Path(ellipseIn: CGRect(x: x(q.d) - 5, y: y(q.h) - 5, width: 10, height: 10)), with: .color(ski.colors.glacier))
                    }
                    VStack {
                        Text(Fmt.n(Fmt.round(hmax))); Spacer(); Text(Fmt.n(Fmt.round(hmin)))
                    }
                    .font(ski.type.text(11)).foregroundStyle(ski.colors.muted).frame(maxWidth: .infinity, alignment: .trailing)
                }
                .contentShape(Rectangle())
                // sideways only: a swipe up or down on the profile scrolls the panel
                .gesture(DragGesture(minimumDistance: 0).onChanged { v in
                    guard abs(v.translation.width) >= abs(v.translation.height) || v.translation == .zero else { return }
                    go(nearest(min(max((v.location.x - pad) / (w - 2 * pad), 0), 1) * dmax))
                })
            }
            .frame(height: 132)
            .environment(\.layoutDirection, .leftToRight)
            .accessibilityElement()
            .accessibilityLabel(S.runProfileRangeAria)
            .accessibilityValue("\(S.runProfileFromStart) \(Fmt.metres(Fmt.round(pts[at].d))), \(S.runProfileAltitudeLabel) \(Fmt.metres(Fmt.round(pts[at].h))), \(Fmt.round(pts[at].a))°")
            .accessibilityAdjustableAction { d in
                // up and down by about 100 m, as the site's range input (A-15)
                let step = max(1, pts.count / 20)
                go(d == .increment ? min(at + step, pts.count - 1) : max(at - step, 0))
            }
            let q = pts[at]
            HStack {
                Stat(label: S.runProfileFromStart, value: Fmt.metres(Fmt.round(q.d)))
                Spacer()
                Stat(label: S.runProfileAltitudeLabel, value: Fmt.metres(Fmt.round(q.h)))
                Spacer()
                Stat(label: S.runProfileSlopeHere, value: "\(Fmt.round(q.a))°")
            }
            .padding(.top, 4)
            // the slope key (the site's .slope-key)
            HStack(spacing: 10) {
                let labels = [S.mapSlopeUpto15, "15°–25°", "25°–30°", S.mapSlopeOver30]
                ForEach(0..<4, id: \.self) { k in
                    HStack(spacing: 4) {
                        Rectangle().fill(Color(hex: SlopeColors.hex[k])).frame(width: 10, height: 10)
                        Text(labels[k]).font(ski.type.text(12)).foregroundStyle(ski.colors.ink)
                    }
                }
            }
            .padding(.top, 8)
            // what's ahead (T3, round 20): three big numbers, the start, the steep part and the end
            H3(S.runAheadHeading)
            HStack(spacing: 0) {
                step(S.runStepStart, Fmt.n(Fmt.round(pts.first!.h)), unit: true, color: nil, first: true)
                if f.steepG > 0 { step(S.runStepSteep, "\(RunFacts.deg(f.maxG))°", unit: false, color: Color(rgb: 0xDC3B33), first: false) }
                step(S.runStepEnd, Fmt.n(Fmt.round(pts.last!.h)), unit: true, color: nil, first: false)
            }
            .overlay(alignment: .top) { Rectangle().fill(ski.colors.rule).frame(height: 1) }
            .overlay(alignment: .bottom) { Rectangle().fill(ski.colors.rule).frame(height: 1) }
            .padding(.top, 2).padding(.bottom, 10)
        }
        .onChange(of: p.key) { at = 0 }
        .task(id: flyAt == nil ? 0 : 1) {
            // flying down: the profile's dot goes with the camera, as on the site (A-13)
            guard let flyAt else { return }
            while !Task.isCancelled {
                if let d = flyAt() { let i = nearest(d); if i != at { at = i } }
                try? await Task.sleep(for: .milliseconds(100))
            }
        }
    }

    private func step(_ label: String, _ value: String, unit: Bool, color: Color?, first: Bool) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label).font(ski.type.text(12)).foregroundStyle(ski.colors.muted).lineLimit(1)
            (Text(value).font(ski.type.display(28)) + Text(unit ? "\u{2009}" + S.commonUnitM(n: "").trimmingCharacters(in: .whitespaces) : "").font(ski.type.display(16)))
                .foregroundStyle(color ?? ski.colors.ink).lineLimit(1).environment(\.layoutDirection, .leftToRight)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.leading, first ? 0 : 6).padding(.trailing, 6).padding(.vertical, 8)
        .overlay(alignment: .leading) { if !first { Rectangle().fill(ski.colors.rule).frame(width: 1) } }
        .accessibilityElement(children: .combine)
    }
}

/// A run's videos (the site's vidBlock): the thumbnail opens YouTube; and a search for more.
private struct Videos: View {
    let p: Piste
    let list: [Video]
    @Environment(\.ski) private var ski
    @Environment(\.openURL) private var openURL

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            H3(S.runVideosHeading)
            if list.isEmpty { Hint(S.runVideosEmpty) }
            ForEach(list) { v in
                Button {
                    // video_play only with a YouTube id (the contract's `video`), as Android
                    if let id = v.youtubeId { Telemetry.shared.event("video_play", ["run": p.key, "video": id]) }
                    if let u = URL(string: v.url) { openURL(u) }
                } label: {
                    VStack(alignment: .leading, spacing: 2) {
                        if let id = v.youtubeId {
                            ZStack {
                                Rectangle().fill(Color(rgb: 0x13233A))
                                AsyncImage(url: URL(string: "https://i.ytimg.com/vi/\(id)/hqdefault.jpg")) { $0.resizable().scaledToFill() } placeholder: { Color.clear }
                                Image(systemName: "play.fill").font(.title2).foregroundStyle(.white)
                                    .frame(width: 56, height: 40).background(Color(rgb: 0xDC3B33, opacity: 0.9))
                            }
                            .aspectRatio(16 / 9, contentMode: .fit).clipped()
                            .accessibilityLabel(v.title.isEmpty ? S.runVideoPlayAria : S.runVideoPlayAriaTitled(title: v.title))
                        }
                        Text(v.title.isEmpty ? (URL(string: v.url)?.host ?? v.url) : v.title)
                            .font(ski.type.text(14, .bold)).underline().foregroundStyle(ski.colors.glacier).multilineTextAlignment(.leading)
                        let sub = [v.channel, v.length].filter { !$0.isEmpty }.joined(separator: " · ")
                        if !sub.isEmpty { Text(sub).font(ski.type.text(13)).foregroundStyle(ski.colors.muted) }
                    }
                    .padding(.vertical, 6)
                }
                .buttonStyle(.plain)
            }
            let name = p.key == "Firni ?" ? "Firni" : p.key
            Button {
                let q = ("Gudauri " + name + " ski").addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
                if let u = URL(string: "https://www.youtube.com/results?search_query=" + q) { openURL(u) }
            } label: {
                Text(S.runVideosSearchYoutube(resort: "Gudauri", name: name)).font(ski.type.text(13)).underline()
                    .foregroundStyle(ski.colors.glacier).frame(minHeight: 44, alignment: .leading)
            }
            .buttonStyle(.plain).padding(.top, 8)
        }
    }
}
