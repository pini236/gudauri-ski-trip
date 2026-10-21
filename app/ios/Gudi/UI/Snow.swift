import SwiftUI

/// Snow on the signs and cards, drawn as the site's js/snow.js and Android's home/Snow.kt draw it (round 18): a cap to
/// the element's real width, resting on its top edge from end to end and over its top strip, stopping where a sign's
/// arrow begins. `.drift` (the game signs), `.low` (the narrow lift signs) and `.pile` (the white cards with a coloured
/// top strip: piles taller toward the middle, no outline, a soft shadow). The colours are the --sn* tokens, moonlit at
/// night. The seed keeps each sign's own drift, the same numbers as on the site.
enum SnowKind { case drift, low, pile }

struct SnowCap {
    let body: Path, edge: Path?, drips: Path
    let pile: Bool
    /// How far the cap reaches above the element's top edge, and its whole height, in points.
    let top: CGFloat, height: CGFloat

    /// The cap for an element `w` points wide: `border` its top strip (6 on the cards), `arrow` the width of a sign's
    /// arrow, on the start side at the left of the drawing (`arrowAtStart`) or at its right.
    init(seed: Int, width w: CGFloat, kind: SnowKind = .drift, border: CGFloat = 0, arrow: CGFloat = 0, arrowAtStart: Bool = false) {
        let pile = kind == .pile
        let t: CGFloat = pile ? 34 : 16
        let b = t + border
        var r = (Double(seed + 1) * 9301).truncatingRemainder(dividingBy: 233280)
        func rnd(_ a: CGFloat, _ z: CGFloat) -> CGFloat {
            r = (r * 9301 + 49297).truncatingRemainder(dividingBy: 233280)
            return CGFloat(Double(a) + Double(z - a) * r / 233280)
        }
        var x0: CGFloat = -3, x1 = w + 3
        let bt = b > t
        if arrow > 0 { if arrowAtStart { x0 = arrow - 5 } else { x1 = w - arrow + 5 } }
        // the ends roll over the edge of the sign, down past its top border
        let end = b + (bt ? 1 : 4)
        var up = [CGPoint(x: x0, y: end), CGPoint(x: x0 + 3, y: t - 2)]
        var pk = true
        if pile {
            // piles, taller toward the middle, with low dips between them
            var x = x0 + rnd(12, 18)
            while x < x1 - 20 {
                let mid = 1 - abs((x - x0) / (x1 - x0) - 0.5) * 1.1
                up.append(pk ? CGPoint(x: x, y: t - 8 - mid * rnd(10, 20)) : CGPoint(x: x, y: t - rnd(1, 6)))
                x += pk ? rnd(30, 48) : rnd(22, 34); pk.toggle()
            }
        } else {
            // drifts of different sizes with flatter stretches between them
            let hi: CGFloat = kind == .low ? 10 : 14
            var x = x0 + rnd(14, 24)
            while x < x1 - 18 {
                if pk { up.append(CGPoint(x: x, y: t - (rnd(0, 1) < 0.45 ? rnd(hi * 0.6, hi) : rnd(2.5, hi * 0.45)))) }
                else { up.append(CGPoint(x: x, y: t - rnd(1, 2.5))) }
                x += pk ? rnd(18, 34) : rnd(16, 30); pk.toggle()
            }
        }
        up.append(CGPoint(x: x1 - 3, y: t - 2)); up.append(CGPoint(x: x1, y: end))
        // the underside: a wavy lip a few points over the face, always below the top border
        let lip: (CGFloat, CGFloat) = pile ? (4, 8) : bt ? (2, 4.5) : (3, 6.5)
        var lo: [CGPoint] = []
        var xl = x1 - 5
        while xl > x0 + 8 { lo.append(CGPoint(x: xl, y: b + rnd(lip.0, lip.1))); xl -= rnd(22, 40) }
        lo.append(CGPoint(x: x0 + 3, y: b + (bt ? 2 : 5)))
        var body = SnowCap.curve(up + lo + [up[0]]); body.closeSubpath()
        var drips = Path()
        let n = Int((rnd(1, 3.4) + 0.5).rounded(.down))
        var at: [CGFloat] = []
        for _ in 0..<n {
            let dx = rnd(x0 + 26, x1 - 26), dl = rnd(5, 9.5) * (pile ? 1.25 : 1), dw = rnd(2.4, 3.6) * (pile ? 1.35 : 1), y0 = b + 2
            // on a pile, no two drips side by side
            if pile && at.contains(where: { abs($0 - dx) < 24 }) { continue }
            at.append(dx)
            drips.move(to: CGPoint(x: dx - dw, y: y0))
            drips.addCurve(to: CGPoint(x: dx, y: y0 + dl), control1: CGPoint(x: dx - dw, y: y0 + dl * 0.7), control2: CGPoint(x: dx - dw * 0.4, y: y0 + dl))
            drips.addCurve(to: CGPoint(x: dx + dw, y: y0), control1: CGPoint(x: dx + dw * 0.4, y: y0 + dl), control2: CGPoint(x: dx + dw, y: y0 + dl * 0.7))
            drips.closeSubpath()
        }
        self.body = body; self.drips = drips; self.pile = pile
        edge = pile ? nil : SnowCap.curve(up)
        top = t; height = b + (pile ? 24 : 16)
    }

    /// A smooth line through the points (Catmull-Rom as cubic curves), as the site's curve().
    static func curve(_ p: [CGPoint]) -> Path {
        var path = Path()
        path.move(to: p[0])
        for k in 0..<(p.count - 1) {
            let a = p[max(k - 1, 0)], b = p[k], c = p[k + 1], e = p[min(k + 2, p.count - 1)]
            path.addCurve(to: c, control1: CGPoint(x: b.x + (c.x - a.x) / 6, y: b.y + (c.y - a.y) / 6),
                          control2: CGPoint(x: c.x - (e.x - b.x) / 6, y: c.y - (e.y - b.y) / 6))
        }
        return path
    }
}

/// The cap drawn on a view's top edge: the shadow it casts (soft under a pile), the drips, the snow and its outline
/// (light, by day only; none on a pile).
struct SnowCapView: View {
    let seed: Int
    var kind: SnowKind = .drift
    var border: CGFloat = 0
    var arrow: CGFloat = 0
    @Environment(\.ski) private var ski
    @Environment(\.layoutDirection) private var dir

    var body: some View {
        GeometryReader { g in
            // drawn in left-to-right coordinates (the canvas is never mirrored): in a right-to-left language the sign's
            // arrow, at the end of the reading direction, is at the drawing's left
            let cap = SnowCap(seed: seed, width: g.size.width, kind: kind, border: border, arrow: arrow, arrowAtStart: dir == .rightToLeft)
            let p = ski.colors.p
            Canvas { c, _ in
                c.translateBy(x: 0, y: -cap.top)
                var s = c
                s.translateBy(x: 0, y: cap.pile ? 2.2 : 1.6)
                if cap.pile { s.addFilter(.blur(radius: 1.2)) }
                let shadow = ski.colors.dark ? Color(.sRGB, red: 0, green: 0, blue: 0, opacity: 0.45) : Color(rgb: 0x0D1522, opacity: 0.16)
                s.fill(cap.body, with: .color(shadow)); s.fill(cap.drips, with: .color(shadow))
                c.fill(cap.drips, with: .color(Color(rgb: p.sn3)))
                c.fill(cap.body, with: .linearGradient(Gradient(stops: [.init(color: Color(rgb: p.sn1), location: 0),
                                                                        .init(color: Color(rgb: p.sn2), location: cap.pile ? 0.7 : 0.55),
                                                                        .init(color: Color(rgb: p.sn3), location: 1)]),
                                                       startPoint: .zero, endPoint: CGPoint(x: 0, y: cap.height)))
                if let e = cap.edge, let col = p.snEdge { c.stroke(e, with: .color(Color(rgb: col)), lineWidth: 1) }
            }
            .frame(width: g.size.width, height: cap.height)
            .environment(\.layoutDirection, .leftToRight)
            .offset(y: -cap.top)
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

extension View {
    /// Snow on this view's top edge (the site's data-snow).
    func snow(seed: Int, kind: SnowKind = .drift, border: CGFloat = 0, arrow: CGFloat = 0) -> some View {
        overlay(alignment: .top) { SnowCapView(seed: seed, kind: kind, border: border, arrow: arrow) }
    }
}
