import SwiftUI
import GudiData
import GudiDomain

/// The meeting point's fixed colours, the same by day and by night (the site's and Android's NAVY, GOLD, FOG).
enum MeetInk {
    static let navy = Color(rgb: 0x13233A)
    static let gold = Color(rgb: 0xF4B942)
    static let fog = Color(rgb: 0xA3B3C8)
}

/// The runs and lifts of the main side as paths in metres, built once (the site's mm-run and mm-lift).
struct MeetLines: @unchecked Sendable {
    /// Named runs by colour (green, blue, red, black), their line segments only (no areas).
    let runs: [(color: String, path: Path)]
    let lifts: Path

    init(_ data: RunsAndLifts) {
        var by: [String: Path] = [:]
        for r in data.mainPistes where r.named {
            var p = by[r.color] ?? Path()
            for s in r.segs where !s.area && s.g.count >= 2 { Self.add(&p, Geo.line(s.g)) }
            by[r.color] = p
        }
        runs = ["green", "blue", "red", "black", "none"].compactMap { c in by[c].map { (c, $0) } }
        var l = Path()
        for lift in data.mainLifts where !lift.name.isEmpty { Self.add(&l, Geo.line(lift.g)) }
        lifts = l
    }

    static func add(_ p: inout Path, _ f: [Double]) {
        guard f.count >= 4 else { return }
        p.move(to: CGPoint(x: f[0], y: f[1]))
        var i = 2
        while i + 1 < f.count { p.addLine(to: CGPoint(x: f[i], y: f[i + 1])); i += 2 }
    }
}

/// The pin of the site (mm-pin): a drop with a dot, 28 units tall, its tip at (0, 0).
let meetPin: Path = SVGPath.parse("M0 0C0 0 -14 -17 -14 -28A14 14 0 0 1 14 -28C14 -17 0 0 0 0z")

/// The map of the meeting point (M1, MP1): the mountain from above, the runs and lifts, and a pin on every station.
/// A tap near a pin reports it (44 points around the whole pin, as on the site), a tap elsewhere reports the empty
/// map; the screen decides (a second tap on the picked pin clears it). Dragging moves the map, two fingers zoom.
struct MeetMap: View {
    let view: MapViewport
    let plan: MeetPlan
    let lines: MeetLines
    let art: ReliefArt?
    let selected: String?
    let onPin: (String) -> Void
    let onEmpty: () -> Void
    @Environment(\.ski) private var ski

    var body: some View {
        let c = ski.colors
        let p = c.p
        // the window this draws (read here, so SwiftUI draws again when it moves)
        let _ = (view.x, view.y, view.span, view.w, view.h, view.ready)
        ZStack {
            // the mountain, the runs and the lifts: drawn again only when the window moves
            Canvas { ctx, size in
                ctx.fill(Path(CGRect(origin: .zero, size: size)), with: .color(c.snow))
                guard view.ready else { return }
                if let art { drawRelief(in: ctx, art, view: view, colors: c, far: false) }
                let k = view.k
                var g = ctx
                g.scaleBy(x: k, y: k); g.translateBy(x: -view.x, y: -view.y)
                for (color, path) in lines.runs {
                    g.stroke(path, with: .color(Color(rgb: Self.run(p, color), opacity: 0.8)), style: StrokeStyle(lineWidth: 3 / k, lineCap: .round, lineJoin: .round))
                }
                g.stroke(lines.lifts, with: .color(Color(rgb: p.lift)), style: StrokeStyle(lineWidth: 1.6 / k, lineCap: .round, lineJoin: .round))
            }
            .drawingGroup()
            // the pins, at a fixed size on the screen, the picked one last (on top), bigger and with its pulsing ring
            TimelineView(.animation(paused: selected == nil)) { t in
                Canvas { ctx, _ in
                    guard view.ready else { return }
                    let f = t.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 1.6) / 1.6
                    for s in plan.stations where s.id != selected { pin(ctx, s, on: false, pulse: 0) }
                    if let sel = selected, let s = plan.byId[sel] { pin(ctx, s, on: true, pulse: f) }
                }
            }
            .allowsHitTesting(false)
        }
        .onGeometryChange(for: CGSize.self) { $0.size } action: { view.resize($0) }
        .contentShape(Rectangle())
        .onTapGesture(coordinateSpace: .local) { at in tap(at) }
        .mapGestures(view)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(S.meetMapAria)
    }

    static func run(_ p: Palette, _ color: String) -> UInt32 {
        switch color { case "green": p.green; case "blue": p.blue; case "red": p.red; default: p.black }
    }

    private func pin(_ ctx: GraphicsContext, _ s: Station, on: Bool, pulse f: Double) {
        let c = ski.colors
        var g = ctx
        g.translateBy(x: view.sx(s.x), y: view.sy(s.y))
        let sc = on ? 1.2 : 0.8
        g.scaleBy(x: sc, y: sc)
        if on {
            var r = g
            r.scaleBy(x: 1 + f * 0.8, y: 1 + f * 0.8)
            r.stroke(Path(ellipseIn: CGRect(x: -11, y: -5, width: 22, height: 10)), with: .color(MeetInk.gold.opacity(1 - f)), lineWidth: 2.5)
        }
        g.fill(meetPin, with: .color(on ? MeetInk.gold : c.paper))
        g.stroke(meetPin, with: .color(c.ink), lineWidth: 2.2)
        let dot = Path(ellipseIn: CGRect(x: -5.5, y: -33.5, width: 11, height: 11))
        g.fill(dot, with: .color(c.paper))
        g.stroke(dot, with: .color(c.ink), lineWidth: 1.5)
    }

    private func tap(_ at: CGPoint) {
        // distance to the whole pin (its tip and its head), in points
        func dpin(_ s: Station) -> Double {
            let tx = view.sx(s.x), ty = view.sy(s.y)
            let hy = ty - 26 * (s.id == selected ? 1.2 : 0.8)
            return min(hypot(tx - at.x, ty - at.y), hypot(tx - at.x, hy - at.y))
        }
        guard let best = plan.stations.min(by: { dpin($0) < dpin($1) }) else { return }
        if dpin(best) <= 44 { onPin(best.id) } else { onEmpty() }
    }
}
