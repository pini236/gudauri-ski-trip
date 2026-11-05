import SwiftUI
import GudiData
import GudiDomain

/// The map from above's window over the mountain (Android's MeetView as the overview uses it): `k` pixels a metre, and
/// the metre at the top left corner. Moves are animated by SwiftUI (the values are plain state).
struct MapCamera: Equatable {
    var k: Double = 0.1
    var x: Double = 0, y: Double = 0
    var size: CGSize = .zero

    func sx(_ mx: Double) -> CGFloat { (mx - x) * k }
    func sy(_ my: Double) -> CGFloat { (my - y) * k }
    func mx(_ sx: CGFloat) -> Double { x + sx / k }
    func my(_ sy: CGFloat) -> Double { y + sy / k }

    /// Frames a box (metres) with a margin, in the part of the screen above `bottom` (a share of the height the panel takes).
    mutating func frame(_ b: CGRect, pad: Double, minSpan: Double = 0, bottom: Double = 0) {
        guard size.width > 0 else { return }
        let w = max(b.width * pad, minSpan), h = max(b.height * pad, minSpan)
        let free = size.height * (1 - bottom)
        k = min(size.width / w, free / h)
        x = b.midX - size.width / k / 2
        y = b.midY - free / k / 2
    }

    /// Zooms by `f` (more than 1 is in) around a screen point.
    mutating func zoom(_ f: Double, at p: CGPoint) {
        let ax = mx(p.x), ay = my(p.y)
        k = min(max(k * f, 0.02), 4)
        x = ax - p.x / k; y = ay - p.y / k
    }

    var span: Double { size.width / k }
}

/// The chosen run painted from its top in slope colours (T1): every line from its top, segment by segment, with how
/// far along the whole run each segment starts (0 to 1), for the reveal.
struct OverviewPaint {
    struct Seg { let a: CGPoint, b: CGPoint, color: Color, at: Double }
    let key: String
    let segs: [Seg]

    static func of(_ t: Terrain, _ p: Piste) -> OverviewPaint {
        let lines = p.lines.map { RunFacts.sample(t, RunFacts.topDown(t, $0), step: 10) }
        let total = max(lines.reduce(0) { $0 + ($1.last?.d ?? 0) }, 1)
        var segs: [Seg] = [], before = 0.0
        for l in lines {
            for i in 0..<max(l.count - 1, 0) {
                segs.append(Seg(a: CGPoint(x: l[i].x, y: l[i].y), b: CGPoint(x: l[i + 1].x, y: l[i + 1].y),
                                color: Color(hex: SlopeColors.hex(forDegrees: l[i].a)), at: (before + l[i].d) / total))
            }
            before += l.last?.d ?? 0
        }
        return OverviewPaint(key: p.key, segs: segs)
    }
}

/// The map from above (PARITY A-30), the site's top view (site/js/app.js draw and layoutLabels) and Android's
/// map/Overview.kt: every run (dashed for a ski way or a section without a name) and lift with its stations; the names
/// that fit; the chosen run painted from the top in slope colours; the profile's dot; and the lift status: closed lifts
/// and runs grey and dashed, and chairs moving on the open lifts. The relief under it comes from the meeting point's
/// relief layer (UI/Relief2D) when present.
struct OverviewMap: View {
    let model: MapModel
    @Binding var cam: MapCamera
    let status: LiftStatus
    let forMe: Bool
    var relief: AnyView? = nil
    let onKobi: () -> Void
    @Environment(\.ski) private var ski
    @Environment(\.accessibilityReduceMotion) private var still
    @State private var paint: OverviewPaint?
    @State private var reveal = 1.0
    @State private var pinch: Double?
    @State private var drag: CGSize?
    /// The open ends of partial runs (round 21), found once the elevation model is here.
    @State private var fades: [String: [OpenEnds.Fade]] = [:]

    var body: some View {
        let fresh = status.fresh
        let openLifts = (!fresh || still || model.hideLifts) ? [] : model.runs.lifts.filter { status.isOpen($0.name.isEmpty ? nil : $0.name) == true }
        TimelineView(.animation(paused: openLifts.isEmpty && reveal >= 1)) { tl in
            let t = tl.date.timeIntervalSinceReferenceDate
            Canvas { c, size in
                draw(&c, size: size, t: t, fresh: fresh, openLifts: openLifts)
            }
        }
        .background(ski.colors.snow)
        .overlay { relief.allowsHitTesting(false) }
        .contentShape(Rectangle())
        .gesture(SpatialTapGesture().onEnded { v in tap(v.location) })
        .simultaneousGesture(DragGesture(minimumDistance: 4).onChanged { v in
            let prev = drag ?? .zero
            cam.x -= (v.translation.width - prev.width) / cam.k; cam.y -= (v.translation.height - prev.height) / cam.k
            drag = v.translation
        }.onEnded { _ in drag = nil })
        .simultaneousGesture(MagnifyGesture().onChanged { v in
            let prev = pinch ?? 1
            cam.zoom(v.magnification / prev, at: v.startLocation)
            pinch = v.magnification
        }.onEnded { _ in pinch = nil })
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(S.mapMapAria)
        .task(id: model.terrain != nil) {
            guard let t = model.terrain else { fades = [:]; return }
            var out: [String: [OpenEnds.Fade]] = [:]
            for p in model.runs.pistes where !p.openEnds.isEmpty { out[p.key] = OpenEnds.fades(p) { t.elev($0, $1) } }
            fades = out
        }
        .task(id: "\(model.selected?.key ?? "")|\(model.terrain != nil)") {
            guard let p = model.selected, let t = model.terrain else { paint = nil; return }
            let made = await Task.detached { OverviewPaint.of(t, p) }.value
            paint = made
            // painted from its top down in 1.3 s, as on the site; at once with reduced motion
            if still { reveal = 1; return }
            reveal = 0
            let start = Date()
            while reveal < 1, !Task.isCancelled {
                try? await Task.sleep(for: .milliseconds(16))
                reveal = min(1, Date().timeIntervalSince(start) / 1.3)
            }
        }
    }

    // MARK: drawing

    private func draw(_ c: inout GraphicsContext, size: CGSize, t: TimeInterval, fresh: Bool, openLifts: [Lift]) {
        guard cam.k > 0 else { return }
        if let art = model.art { drawRelief(in: c, art, k: cam.k, x: cam.x, y: cam.y, colors: ski.colors, far: cam.k < 0.12) }
        let u = 1 / cam.k // metres a point
        var m = c
        m.translateBy(x: -cam.x * cam.k, y: -cam.y * cam.k)
        m.scaleBy(x: cam.k, y: cam.k)
        func path(_ l: [(x: Double, y: Double)]) -> Path {
            var p = Path()
            for (i, q) in l.enumerated() { if i == 0 { p.move(to: CGPoint(x: q.x, y: q.y)) } else { p.addLine(to: CGPoint(x: q.x, y: q.y)) } }
            return p
        }
        func stroke(_ w: Double, dash: [Double]? = nil) -> StrokeStyle {
            StrokeStyle(lineWidth: w * u, lineCap: .round, lineJoin: .round, dash: (dash ?? []).map { $0 * u })
        }
        let sel = model.selected?.key
        let hidden = model.hiddenKeys
        let hasLbl = model.runs.pistes.contains { !$0.labelWindows.isEmpty }
        // the runs: the casing, then the colour
        for q in model.runs.pistes where !hidden.contains(q.key) {
            let shut = fresh && status.runOpen(q) == false
            let alpha = sel != nil && q.key != sel ? 0.28 : (shut && forMe ? 0.15 : 1)
            let col = shut ? ski.colors.dash : ski.colors.run(q.color)
            // a section without a name is a thin solid line where the data carries name windows (round 21)
            let dash: [Double]? = shut ? [6, 5] : !q.named ? (hasLbl ? nil : [5, 4]) : q.kind == "ski-way" ? [8, 5] : nil
            let w = q.named ? (q.kind == "ski-way" ? 2.6 : 3.4) : 2
            let fq = fades[q.key] ?? []
            for (li, l) in q.lines.enumerated() {
                let p = path(l)
                func line(_ g: inout GraphicsContext) {
                    g.stroke(p, with: .color(ski.colors.casing.opacity(alpha)), style: stroke(q.named ? 6.5 : 4.5))
                    g.stroke(p, with: .color(col.opacity(alpha)), style: stroke(w, dash: dash))
                }
                if let f = fq.first(where: { $0.line == li }) {
                    // the open end: the last 130 m fade to nothing, with nothing drawn across the hole
                    m.drawLayer { g in
                        line(&g)
                        g.blendMode = .destinationIn
                        g.fill(Path(CGRect(x: -1e5, y: -1e5, width: 2e5, height: 2e5)),
                               with: .linearGradient(Gradient(colors: [Color.black.opacity(0), .black]),
                                                     startPoint: CGPoint(x: f.a.x, y: f.a.y), endPoint: CGPoint(x: f.b.x, y: f.b.y)))
                    }
                } else { line(&m) }
            }
        }
        // the chosen run, from the top down in slope colours over a white casing
        if let paint, paint.key == sel {
            for s in paint.segs where s.at <= reveal {
                var p = Path(); p.move(to: s.a); p.addLine(to: s.b)
                m.stroke(p, with: .color(.white), style: stroke(10))
            }
            for s in paint.segs where s.at <= reveal {
                var p = Path(); p.move(to: s.a); p.addLine(to: s.b)
                m.stroke(p, with: .color(s.color), style: stroke(6))
            }
        }
        // small arrows down the chosen run, thinned by zoom (round 21)
        if let paint, paint.key == sel, hasLbl, reveal >= 1 {
            let every = RunArrows.step(metresPerPx: u)
            for (i, a) in RunArrows.along(paint.segs.map { (a: (x: Double($0.a.x), y: Double($0.a.y)), b: (x: Double($0.b.x), y: Double($0.b.y))) }).enumerated() where i % every == 0 {
                var arrow = Path()
                arrow.move(to: CGPoint(x: 5 * u, y: 0)); arrow.addLine(to: CGPoint(x: -4 * u, y: -4 * u)); arrow.addLine(to: CGPoint(x: -4 * u, y: 4 * u)); arrow.closeSubpath()
                let t = CGAffineTransform(rotationAngle: a.angle).concatenating(CGAffineTransform(translationX: a.x, y: a.y))
                m.fill(arrow.applying(t), with: .color(.white))
                m.stroke(arrow.applying(t), with: .color(ski.colors.ink), lineWidth: 1.2 * u)
            }
        }
        // the lifts: casing, the line (a gondola thicker; out of use dashed 2 3; closed grey, dashed 4 4), stations
        if !model.hideLifts {
            for l in model.runs.lifts {
                let line = l.line
                guard line.count >= 2 else { continue }
                let closed = fresh && status.isOpen(l.name.isEmpty ? nil : l.name) == false
                let p = path(line)
                let col = closed ? ski.colors.dash : ski.colors.lift
                m.stroke(p, with: .color(ski.colors.casing), style: stroke(4.5))
                m.stroke(p, with: .color(col), style: stroke(l.kind == "gondola" ? 2.4 : 1.6, dash: closed ? [4, 4] : l.status == "inactive" ? [2, 3] : nil))
                for e in [line[0], line[line.count - 1]] {
                    let r = 3.2 * u
                    let dot = Path(ellipseIn: CGRect(x: e.x - r, y: e.y - r, width: 2 * r, height: 2 * r))
                    m.fill(dot, with: .color(col)); m.stroke(dot, with: .color(ski.colors.casing), lineWidth: 1.5 * u)
                }
            }
        }
        // the chairs: three on each open lift, a lap in at least 8 s (the site's len/60)
        for l in openLifts {
            let line = l.line
            var len = 0.0
            for i in 1..<line.count { len += hypot(line[i].x - line[i - 1].x, line[i].y - line[i - 1].y) }
            let dur = max(8, (l.len > 0 ? l.len : len) / 60)
            for k in 0..<3 {
                let f = (t / dur + Double(k) / 3).truncatingRemainder(dividingBy: 1)
                let q = along(line, len, f), r = 3.6 * u
                let dot = Path(ellipseIn: CGRect(x: q.x - r, y: q.y - r, width: 2 * r, height: 2 * r))
                m.fill(dot, with: .color(Color(rgb: 0xF4B942))); m.stroke(dot, with: .color(ski.colors.ink), lineWidth: u)
            }
        }
        // the peaks' marks
        for pk in model.terrain?.peaks ?? [] where !pk.name.hasPrefix("Kobi Pass") {
            var tri = Path()
            tri.move(to: CGPoint(x: pk.x, y: pk.y - 8 * u)); tri.addLine(to: CGPoint(x: pk.x + 5.5 * u, y: pk.y + 1.5 * u))
            tri.addLine(to: CGPoint(x: pk.x - 5.5 * u, y: pk.y + 1.5 * u)); tri.closeSubpath()
            m.fill(tri, with: .color(ski.colors.peak)); m.stroke(tri, with: .color(ski.colors.casing), lineWidth: 1.2 * u)
        }
        if let mk = model.marker {
            let r = 7 * u
            let dot = Path(ellipseIn: CGRect(x: mk.x - r, y: mk.y - r, width: 2 * r, height: 2 * r))
            m.fill(dot, with: .color(ski.colors.ink)); m.stroke(dot, with: .color(.white), lineWidth: 3 * u)
        }
        drawNames(&c, size: size)
    }

    /// The point a fraction `f` along a line `len` metres long (a chair on its cable).
    private func along(_ l: [(x: Double, y: Double)], _ len: Double, _ f: Double) -> CGPoint {
        var want = f * len
        for i in 1..<l.count {
            let seg = hypot(l[i].x - l[i - 1].x, l[i].y - l[i - 1].y)
            if want <= seg || i == l.count - 1 {
                let t = seg > 0 ? min(max(want / seg, 0), 1) : 0
                return CGPoint(x: l[i - 1].x + (l[i].x - l[i - 1].x) * t, y: l[i - 1].y + (l[i].y - l[i - 1].y) * t)
            }
            want -= seg
        }
        return CGPoint(x: l[0].x, y: l[0].y)
    }

    // MARK: names

    private struct Name { let x, y: Double; let text: String; let size: CGFloat; let color: Color; let dy: CGFloat; var run: String? = nil; var kobi = false; var lift = false
        /// Round 21: the name goes beside the line where a window has room (nil: at a point, as before).
        var win: [LabelWindow]? = nil; var segs: [[LabelFit.P]] = [] }

    private var names: [Name] {
        var out: [Name] = []
        if let pass = model.runs.pistes.first(where: { $0.key == "Kobi" })?.lines.first?.first {
            out.append(Name(x: pass.x, y: pass.y, text: S.mapKobiSideLabel, size: 13, color: ski.colors.glacier, dy: -6, kobi: true))
        }
        for pk in model.terrain?.peaks ?? [] where !pk.name.hasPrefix("Kobi Pass") {
            out.append(Name(x: pk.x, y: pk.y, text: S.appPeakLabel(name: "\u{2068}\(pk.name)\u{2069}", ele: Fmt.n(pk.ele)), size: 12, color: ski.colors.peak, dy: -11))
        }
        let hasLbl = model.runs.pistes.contains { !$0.labelWindows.isEmpty }
        for p in model.runs.pistes where p.named {
            if hasLbl {
                // the name beside the line, curving with it, only where a window has room (never on another run)
                let segs = p.segs.map { $0.g.map { (x: Geo.x($0[1]), y: Geo.y($0[0])) } }
                let col = ski.colors.run(p.color)
                if !p.labelWindows.isEmpty { out.append(Name(x: 0, y: 0, text: runName(p), size: 13, color: col, dy: 0, run: p.key, win: p.labelWindows, segs: segs)) }
                // a stretch two runs share: one name for both when they are of one family ("Tatra 1 · 2")
                let by = Dictionary(grouping: p.sharedWindows) { $0.other ?? "" }
                for (o, ws) in by.sorted(by: { $0.key < $1.key }) where !o.isEmpty {
                    let other = model.runs.pistes.first { $0.key == o }.map { runName($0) } ?? o
                    out.append(Name(x: 0, y: 0, text: LabelFit.sharedName(runName(p), other), size: 13, color: col, dy: 0, run: p.key, win: ws, segs: segs))
                }
                continue
            }
            // halfway along its longest line (the site's middle point)
            guard let l = p.lines.max(by: { $0.count < $1.count }) else { continue }
            let q = l[l.count / 2]
            out.append(Name(x: q.x, y: q.y, text: runName(p), size: 13, color: ski.colors.run(p.color), dy: -6, run: p.key))
        }
        for l in model.runs.lifts where !l.name.isEmpty {
            let line = l.line; let q = line[line.count / 2]
            out.append(Name(x: q.x, y: q.y, text: "⇡ " + l.name, size: 11.5, color: ski.colors.lift, dy: -6, lift: true))
        }
        return out
    }


    private func drawNames(_ c: inout GraphicsContext, size: CGSize) {
        let sel = model.selected?.key, hidden = model.hiddenKeys
        var placed: [CGRect] = []
        // the chosen run's first, then in the site's order; one that would cover another already placed is left out
        let order = names.sorted { a, b in (a.run == sel && sel != nil) && !(b.run == sel) }
        for n in order {
            if let r = n.run, hidden.contains(r) { continue }
            if n.lift && model.hideLifts { continue }
            if let ws = n.win { drawAlong(&c, n, ws, size: size, placed: &placed, dim: sel != nil && n.run != sel); continue }
            let sx = cam.sx(n.x), sy = cam.sy(n.y) + n.dy
            if sx < -200 || sy < -50 || sx > size.width + 200 || sy > size.height + 50 { continue }
            let txt = c.resolve(Text(n.text).font(ski.type.text(n.size, .bold)).foregroundColor(n.color))
            let z = txt.measure(in: CGSize(width: 400, height: 50))
            let rect = CGRect(x: sx - z.width / 2 - 2, y: sy - z.height - 2, width: z.width + 4, height: z.height + 4)
            if placed.contains(where: { $0.intersects(rect) }) { continue }
            placed.append(rect)
            let dim = sel != nil && n.run != nil && n.run != sel
            var cc = c
            cc.opacity = dim ? 0.28 : 1
            // the halo: the name in the casing colour, nudged around, under it
            let halo = c.resolve(Text(n.text).font(ski.type.text(n.size, .bold)).foregroundColor(ski.colors.casing))
            for (dx, dy) in [(-1.2, 0.0), (1.2, 0), (0, -1.2), (0, 1.2), (-0.9, -0.9), (0.9, 0.9), (-0.9, 0.9), (0.9, -0.9)] {
                cc.draw(halo, at: CGPoint(x: sx + dx, y: sy + dy), anchor: .bottom)
            }
            cc.draw(txt, at: CGPoint(x: sx, y: sy), anchor: .bottom)
        }
    }

    /// A name along the line (round 21): the baseline from `LabelFit`, each letter set on it and turned with it.
    private func drawAlong(_ c: inout GraphicsContext, _ n: Name, _ ws: [LabelWindow], size: CGSize, placed: inout [CGRect], dim: Bool) {
        let u = 1 / cam.k, font = ski.type.text(n.size, .bold)
        let glyphs = n.text.map { String($0) }
        let widths = glyphs.map { c.resolve(Text($0).font(font)).measure(in: CGSize(width: 100, height: 60)).width }
        let total = widths.reduce(0, +)
        guard let f = LabelFit.fit(ws, segs: n.segs, wM: Double(total) * u * 1.08, hM: n.size * u, off: 6 * u) else { return }
        let pts = f.pts.map { CGPoint(x: cam.sx($0.x), y: cam.sy($0.y)) }
        guard let first = pts.first else { return }
        let xs = pts.map(\.x), ys = pts.map(\.y)
        let rect = CGRect(x: xs.min()! - 2, y: ys.min()! - n.size - 2, width: xs.max()! - xs.min()! + 4, height: ys.max()! - ys.min()! + n.size + 6)
        if first.x < -300 || first.y < -100 || first.x > size.width + 300 || first.y > size.height + 100 { return }
        if placed.contains(where: { $0.intersects(rect) }) { return }
        placed.append(rect)
        var segLen: [CGFloat] = [], pathLen: CGFloat = 0
        for i in 1..<max(pts.count, 1) { let l = hypot(pts[i].x - pts[i - 1].x, pts[i].y - pts[i - 1].y); segLen.append(l); pathLen += l }
        func at(_ s: CGFloat) -> (CGPoint, Double) {
            var rest = max(0, min(s, pathLen))
            for i in 0..<segLen.count {
                if rest <= segLen[i] || i == segLen.count - 1 {
                    let t = segLen[i] > 0 ? rest / segLen[i] : 0
                    let a = pts[i], b = pts[i + 1]
                    return (CGPoint(x: a.x + (b.x - a.x) * t, y: a.y + (b.y - a.y) * t), atan2(b.y - a.y, b.x - a.x))
                }
                rest -= segLen[i]
            }
            return (pts[0], 0)
        }
        var g = c
        g.opacity = dim ? 0.28 : 1
        var s = (pathLen - total) / 2
        for (i, ch) in glyphs.enumerated() {
            let (pt, ang) = at(s + widths[i] / 2)
            s += widths[i]
            if ch == " " { continue }
            var gg = g
            gg.translateBy(x: pt.x, y: pt.y); gg.rotate(by: .radians(ang))
            let halo = c.resolve(Text(ch).font(font).foregroundColor(ski.colors.casing))
            for (dx, dy) in [(-1.2, 0.0), (1.2, 0), (0, -1.2), (0, 1.2)] { gg.draw(halo, at: CGPoint(x: dx, y: dy), anchor: .bottom) }
            gg.draw(c.resolve(Text(ch).font(font).foregroundColor(n.color)), at: .zero, anchor: .bottom)
        }
    }

    // MARK: taps

    private func tap(_ at: CGPoint) {
        let mx = cam.mx(at.x), my = cam.my(at.y), tol = 1 / cam.k
        // the Kobi signpost first, then a run's line, then a lift's, as the site's hit areas
        if let pass = model.runs.pistes.first(where: { $0.key == "Kobi" })?.lines.first?.first,
           hypot(cam.sx(pass.x) - at.x, cam.sy(pass.y) - 8 - at.y) < 30 { onKobi(); return }
        let hidden = model.hiddenKeys
        var best: (Piste, Double)?
        for p in model.runs.pistes where !hidden.contains(p.key) {
            for l in p.lines { let d = dist(l, mx, my); if d <= 12 * tol, d < (best?.1 ?? .infinity) { best = (p, d) } }
        }
        if let best { model.select(best.0.key, via: "map"); Fx.shared.tick(); return }
        if !model.hideLifts {
            var bl: (Lift, Double)?
            for l in model.runs.lifts { let d = dist(l.line, mx, my); if d <= 10 * tol, d < (bl?.1 ?? .infinity) { bl = (l, d) } }
            if let bl { model.showLift(bl.0); Fx.shared.tick(); return }
        }
    }

    /// The distance from a point to a line, in metres.
    private func dist(_ l: [(x: Double, y: Double)], _ x: Double, _ y: Double) -> Double {
        guard l.count > 1 else { return l.first.map { hypot($0.x - x, $0.y - y) } ?? .infinity }
        var best = Double.infinity
        for i in 0..<(l.count - 1) {
            let a = l[i], dx = l[i + 1].x - a.x, dy = l[i + 1].y - a.y, len2 = dx * dx + dy * dy
            let f = len2 > 0 ? min(max(((x - a.x) * dx + (y - a.y) * dy) / len2, 0), 1) : 0
            best = min(best, hypot(a.x + dx * f - x, a.y + dy * f - y))
        }
        return best
    }
}
