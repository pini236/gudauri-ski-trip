import SwiftUI
import UIKit
import GudiDomain

/// The descent's mountain from the side (the site's canvas, Android's DescentView): the sky and the real view from the
/// village drifting by, the run's snow with its gullies, ice and powder, your tracks, pines, markers every 25 m, the
/// kickers with their pop line, signs ahead of each surprise, the lift and the finish, rocks, khachapuri, the snowcat,
/// the others waiting and riding in your tracks, the ghost, the dog, you, the avalanche behind, the wind and the fog on
/// the ridge, and the run's profile across the top. One look, a bright winter day, in both themes.
@MainActor
final class DescentPainter {
    private var zoom: CGFloat = 0
    private var shakeSeed: UInt64 = 9
    /// Where the run's profile goes: below the crew's squares, in points from the top.
    var mapTop: CGFloat = 140

    // the camera (the site's draw): metres to points
    private var W: CGFloat = 0, H: CGFloat = 0, sc: CGFloat = 1, camX: CGFloat = 0, camY: CGFloat = 0
    private func sx(_ x: Float) -> CGFloat { (CGFloat(x) - camX) * sc }
    private func sy(_ y: Float) -> CGFloat { H * 0.5 - (CGFloat(y) - camY) * sc }
    private func pt(_ x: Float, _ y: Float) -> CGPoint { CGPoint(x: sx(x), y: sy(y)) }
    private func hash(_ i: Int) -> CGFloat { let v = sin(Double(i) * 127.1) * 43758.5; return CGFloat(v - v.rounded(.down)) }
    private func rnd() -> CGFloat { shakeSeed = shakeSeed &* 6364136223846793005 &+ 1442695040888963407; return CGFloat(shakeSeed >> 40) / CGFloat(1 << 24) }

    func reset() { zoom = 0 }

    private static let ink = Color(rgb: 0x13233A)
    private static let trackOff: [CGFloat] = [0.16, 0.4]

    func draw(_ ctx: inout GraphicsContext, _ size: CGSize, game g: DescentGame?, preview: Course?, pano: Image?, panoAspect: CGFloat, type: SkiType, still: Bool) {
        W = size.width; H = size.height
        let c = g?.c ?? preview
        let px: Float = g?.x ?? 40
        let py: Float = g?.y ?? (c?.ground(40) ?? 0)
        let ps = CGFloat(g?.s ?? 0)
        let hNow = c.map { CGFloat(max(0, py - $0.ground(px))) } ?? 0
        let want = min(W, H * 0.75) / (14 + ps * 0.6 + min(hNow, 12))
        zoom = zoom > 0 ? zoom + (want - zoom) * 0.06 : want
        sc = zoom * (1 - CGFloat(g?.zoomKick ?? 0))
        camX = CGFloat(px) - W * 0.27 / sc; camY = CGFloat(py) + min(hNow, 12) * 0.35 + H * 0.1 / sc
        let t = g?.t ?? 0
        let fog = c.map { CGFloat(min(max(($0.realH(px) - 2950) / 400, 0), 0.55)) } ?? 0
        let ink = Self.ink

        // sky and the real view from the village, drifting slowly
        ctx.fill(Path(CGRect(origin: .zero, size: size)), with: .linearGradient(Gradient(stops: [.init(color: Color(rgb: 0x4F90D2), location: 0), .init(color: Color(rgb: 0xD2E4F3), location: 0.6)]),
                                                                          startPoint: .zero, endPoint: CGPoint(x: 0, y: H)))
        if let pano, panoAspect > 0 {
            let ph = max(H * 0.7, W * 0.42 / panoAspect), pw = ph * panoAspect, top = H * 0.78 - ph
            let scroll = CGFloat(px) * 0.6; var k = Int((scroll / pw).rounded(.down))
            while CGFloat(k) * pw - scroll < W { // mirrored tiles hide the seam
                let x = CGFloat(k) * pw - scroll
                var l = ctx
                if k % 2 != 0 { l.translateBy(x: x + pw, y: 0); l.scaleBy(x: -1, y: 1); l.draw(pano, in: CGRect(x: 0, y: top, width: pw, height: ph)) }
                else { l.draw(pano, in: CGRect(x: x, y: top, width: pw, height: ph)) }
                k += 1
            }
            ctx.fill(Path(CGRect(x: 0, y: top + ph * 0.72, width: W, height: ph * 0.28)),
                     with: .linearGradient(Gradient(colors: [Color(rgb: 0xE2ECF6, opacity: 0), Color(rgb: 0xE2ECF6)]), startPoint: CGPoint(x: 0, y: top + ph * 0.72), endPoint: CGPoint(x: 0, y: top + ph)))
            ctx.fill(Path(CGRect(x: 0, y: top + ph - 1, width: W, height: H)), with: .color(Color(rgb: 0xE2ECF6)))
        }
        if fog > 0 { ctx.fill(Path(CGRect(origin: .zero, size: size)), with: .color(Color(rgb: 0xECF2F8, opacity: min(1, fog * 1.2)))) }
        guard let c else { return }

        var cv = ctx
        if let g, g.shake > 0 { cv.translateBy(x: (rnd() - 0.5) * CGFloat(g.shake), y: (rnd() - 0.5) * CGFloat(g.shake)) }
        let x0 = Float(camX - 3), x1 = Float(camX + W / sc + 3)
        // the surface, with the gully walls exactly at their edges
        var xs: [Float] = []
        var xx = x0; while xx <= x1 { xs.append(xx); xx += 1 }
        for gp in c.gaps where gp.x1 > x0 && gp.x0 < x1 { xs += [gp.x0, gp.x0 + 0.001, gp.x1 - 0.001, gp.x1] }
        xs.sort()
        func gy(_ x: Float) -> Float { c.inGap(x) != nil ? c.baseGround(x) - 7 : c.baseGround(x) }
        var surf = Path(); surf.move(to: CGPoint(x: sx(x0), y: H + 10))
        for x in xs { surf.addLine(to: pt(x, gy(x))) }
        surf.addLine(to: CGPoint(x: sx(x1), y: H + 10)); surf.closeSubpath()
        cv.fill(surf, with: .linearGradient(Gradient(colors: [.white, Color(rgb: 0xC9D8E7)]), startPoint: CGPoint(x: 0, y: H * 0.3), endPoint: CGPoint(x: 0, y: H)))
        // gully: a dark cut with icy water at the bottom
        for gp in c.gaps where !(gp.x1 < x0 || gp.x0 > x1) {
            let a = gp.x0, b = gp.x1
            var p = Path(); p.move(to: pt(a, c.baseGround(a))); p.addLine(to: pt(a, c.baseGround(a) - 7)); p.addLine(to: pt(b, c.baseGround(b) - 7)); p.addLine(to: pt(b, c.baseGround(b))); p.closeSubpath()
            cv.fill(p, with: .color(Color(rgb: 0x6F88A3)))
            let wy = sy(c.baseGround(a) - 5.8)
            cv.fill(Path(CGRect(x: sx(a), y: wy, width: CGFloat(b - a) * sc, height: 1.2 * sc)), with: .color(Color(rgb: 0x3E6E9C)))
            for q in 0..<4 {
                let qx = a + (Float(q * 2) + t * 1.5).truncatingRemainder(dividingBy: b - a)
                var l = Path(); l.move(to: CGPoint(x: sx(qx), y: wy + 2)); l.addLine(to: CGPoint(x: sx(qx + 0.8), y: wy + 2))
                cv.stroke(l, with: .color(.white.opacity(0.6)), lineWidth: 1.5)
            }
            cv.fill(Path(CGRect(x: sx(a) - 2, y: sy(c.baseGround(a)), width: 4, height: sc * 1.2)), with: .color(.white))
            cv.fill(Path(CGRect(x: sx(b) - 2, y: sy(c.baseGround(b)), width: 4, height: sc * 1.2)), with: .color(.white))
        }
        // ice sheen and powder pillows on the surface
        for z in c.zones where !(z.x1 < x0 || z.x0 > x1 || z.kind == Course.wind) {
            if z.kind == Course.ice {
                let lw = max(3, sc * 0.35)
                var p = Path(); var x = z.x0; var first = true
                while x <= z.x1 { let q = CGPoint(x: sx(x), y: sy(c.ground(x)) + lw / 2); if first { p.move(to: q) } else { p.addLine(to: q) }; first = false; x += 1 }
                cv.stroke(p, with: .color(Color(rgb: 0x78C8F0, opacity: 0.8)), lineWidth: lw)
                x = z.x0 + 2
                while x < z.x1 {
                    let X = sx(x), Y = sy(c.ground(x)) + sc * 0.2
                    var l = Path(); l.move(to: CGPoint(x: X, y: Y)); l.addLine(to: CGPoint(x: X + sc * 1.2, y: Y - sc * 0.12))
                    cv.stroke(l, with: .color(.white.opacity(0.9)), lineWidth: 1.5); x += 5
                }
            } else {
                var x = z.x0
                while x <= z.x1 {
                    let r = sc * (0.55 + 0.25 * hash(Int((x * 3).rounded()))); let X = sx(x), Y = sy(c.ground(x))
                    let e = Path(ellipseIn: CGRect(x: X - r, y: Y - r, width: 2 * r, height: 2 * r))
                    cv.fill(e, with: .color(.white))
                    let arc = arcadeArc(CGPoint(x: X, y: Y), r, r) // the top half
                    cv.stroke(arc, with: .color(Color(rgb: 0x8FA7C0, opacity: 0.5)), lineWidth: 1)
                    x += 1.6
                }
            }
        }
        do {
            var p = Path(); var first = true
            for x in xs { if c.inGap(x) != nil { first = true; continue }; let q = pt(x, gy(x)); if first { p.move(to: q) } else { p.addLine(to: q) }; first = false }
            cv.stroke(p, with: .color(Color(rgb: 0x8FA7C0)), lineWidth: 2)
        }
        // corduroy just under the surface, and your ski tracks carved into it
        for k in 1..<4 {
            var p = Path(); var first = true; var x = x0
            while x <= x1 {
                if c.inGap(x) != nil { first = true; x += 3; continue }
                let q = CGPoint(x: sx(x), y: sy(c.baseGround(x)) + CGFloat(k * 7)); if first { p.move(to: q) } else { p.addLine(to: q) }; first = false; x += 3
            }
            cv.stroke(p, with: .color(Color(rgb: 0x8FA7C0, opacity: 0.3)), lineWidth: 1)
        }
        if let g {
            let lw = max(1, sc * 0.09)
            for tr in g.tracks {
                let a = max(tr[0], x0), b = min(tr[1], x1); if b <= a { continue }
                for off in Self.trackOff {
                    var p = Path(); var x = a; p.move(to: CGPoint(x: sx(x), y: sy(c.ground(x)) + off * sc))
                    while x <= b { p.addLine(to: CGPoint(x: sx(x), y: sy(c.ground(x)) + off * sc)); x += 0.8 }
                    p.addLine(to: CGPoint(x: sx(b), y: sy(c.ground(b)) + off * sc))
                    cv.stroke(p, with: .color(Color(rgb: 0x5F7DA0, opacity: 0.55)), lineWidth: lw)
                }
            }
        }
        // pines in front of the piste
        treeRow(&cv, c, 8, 8, 4.5, 7.5, Color(rgb: 0x2F5A4A), 1.15)
        treeRow(&cv, c, 12, 13, 6, 10, Color(rgb: 0x1F4436), 1.3)
        // piste markers every 25 m in the run's colour, kicker flags, warning signs
        let rc = Color(rgb: descentRunColor(c.color))
        var mk = (x0 / 25).rounded(.up) * 25
        while mk < x1 {
            if c.inGap(mk) == nil {
                let X = sx(mk), Y = sy(c.ground(mk))
                cv.fill(Path(CGRect(x: X - 2, y: Y - sc * 1.6, width: 4, height: sc * 1.6)), with: .color(rc))
                cv.fill(Path(CGRect(x: X - 2, y: Y - sc * 1.6, width: 4, height: sc * 0.35)), with: .color(.white))
            }
            mk += 25
        }
        for k in c.kickers where k.x >= x0 && k.x <= x1 {
            let X = sx(k.x), Y = sy(c.ground(k.x))
            var l = Path(); l.move(to: CGPoint(x: sx(k.x - 3.2), y: sy(c.ground(k.x - 3.2)) + 1)); l.addLine(to: CGPoint(x: X, y: Y + 1))
            cv.stroke(l, with: .color(Color(rgb: 0xF4B942, opacity: 0.9)), lineWidth: max(3, sc * 0.3)) // the pop zone
            var f = Path(); f.move(to: CGPoint(x: X, y: Y - sc * 2.4)); f.addLine(to: CGPoint(x: X + sc, y: Y - sc * 2.05)); f.addLine(to: CGPoint(x: X, y: Y - sc * 1.7)); f.closeSubpath()
            cv.fill(f, with: .color(Color(rgb: k.gap ? 0xD1342B : 0xF08A3C)))
            var pole = Path(); pole.move(to: CGPoint(x: X, y: Y)); pole.addLine(to: CGPoint(x: X, y: Y - sc * 2.4))
            cv.stroke(pole, with: .color(ink), lineWidth: 1.5)
        }
        for s in c.signs { sign(&cv, c, s.x, DescentText.word(s.text), start: false, type) }
        sign(&cv, c, 6, S.gameDescentSignStart(alt: c.top.formatted(.number.grouping(.automatic).locale(Locale(identifier: "en_US")))), start: true, type)
        // the lift and the finish arch
        let lx = c.len - 3
        if lx > x0 - 20 && lx < x1 + 20 {
            let X = sx(lx), Y = sy(c.ground(lx))
            cv.fill(Path(CGRect(x: X - 3, y: Y - sc * 9, width: 6, height: sc * 9)), with: .color(ink))
            cv.fill(Path(CGRect(x: X - sc * 2.2, y: Y - sc * 9, width: sc * 4.4, height: sc * 0.8)), with: .color(ink))
            var cable = Path(); cable.move(to: CGPoint(x: X, y: Y - sc * 8.6)); cable.addLine(to: CGPoint(x: X + W, y: Y - sc * 8.6 - W * 0.35))
            cv.stroke(cable, with: .color(ink), lineWidth: 2)
            let liftText = c.lift.isEmpty ? S.gameDescentFinishSign : S.gameDescentLiftName(lift: c.lift)
            cv.draw(Text(liftText).font(type.display(max(8, (sc * 1.6).rounded()) / type.displayScale)).foregroundColor(Color(rgb: 0xF4B942)), at: CGPoint(x: X, y: Y - sc * 10), anchor: .bottom)
            let fx = c.len - 8, FX = sx(fx), FY = sy(c.ground(fx))
            cv.fill(Path(CGRect(x: FX - sc * 3, y: FY - sc * 4.2, width: sc * 6, height: sc * 0.9)), with: .color(Color(rgb: 0xD1342B)))
            cv.fill(Path(CGRect(x: FX - sc * 3, y: FY - sc * 4.2, width: 3, height: sc * 4.2)), with: .color(ink))
            cv.fill(Path(CGRect(x: FX + sc * 3 - 3, y: FY - sc * 4.2, width: 3, height: sc * 4.2)), with: .color(ink))
            cv.draw(Text(S.gameDescentFinishSign).font(type.text(max(8, (sc * 0.8).rounded()))).foregroundColor(.white), at: CGPoint(x: FX, y: FY - sc * 3.75), anchor: .center)
        }

        // the world in metres, y up
        var w = cv
        w.translateBy(x: 0, y: H * 0.5); w.scaleBy(x: sc, y: -sc); w.translateBy(x: -camX, y: -camY)
        for r in c.rocks where !(r.x < x0 - 3 || r.x > x1 + 3) {
            var l = w; l.translateBy(x: CGFloat(r.x), y: CGFloat(c.ground(r.x))); l.rotate(by: .radians(Double(c.slope(r.x))))
            let h = CGFloat(r.h)
            l.fill(poly([(-0.9, 0), (-0.6, h * 0.8), (-0.1, h), (0.5, h * 0.85), (0.9, 0)]), with: .color(Color(rgb: 0x5B6675)))
            l.fill(poly([(-0.62, h * 0.78), (-0.1, h + 0.08), (0.52, h * 0.86), (0.2, h * 0.7), (-0.3, h * 0.72)]), with: .color(.white))
        }
        // khachapuri: a boat of bread with an egg
        for k in c.coins where !(k.got || k.x < x0 - 2 || k.x > x1 + 2) {
            let y = CGFloat(c.baseGround(k.x) + k.dy + sin(t * 4 + k.x) * 0.12), x = CGFloat(k.x)
            w.fill(Path(ellipseIn: CGRect(x: x - 0.62, y: y - 0.3, width: 1.24, height: 0.6)), with: .color(Color(rgb: 0xC9822E)))
            w.fill(Path(ellipseIn: CGRect(x: x - 0.42, y: y + 0.04 - 0.18, width: 0.84, height: 0.36)), with: .color(Color(rgb: 0xF7E3A1)))
            w.fill(circle(x, y + 0.06, 0.11), with: .color(Color(rgb: 0xF4A11D)))
        }
        // the snowcat grooming uphill
        for k in c.cats where !(k.x < x0 - 6 || k.x > x1 + 6) {
            var l = w; l.translateBy(x: CGFloat(k.x), y: CGFloat(c.ground(k.x))); l.rotate(by: .radians(Double(c.slope(k.x))))
            l.fill(Path(roundedRect: CGRect(x: -2.2, y: 0, width: 4.4, height: 0.8), cornerRadius: 0.35), with: .color(Color(rgb: 0x2A2F38)))
            var wx: CGFloat = -1.8; while wx <= 1.81 { l.fill(circle(wx, 0.4, 0.22), with: .color(.white)); wx += 0.9 }
            l.fill(Path(CGRect(x: -1.9, y: 0.8, width: 3.4, height: 0.8)), with: .color(Color(rgb: 0xD1342B)))
            l.fill(Path(CGRect(x: -0.3, y: 1.6, width: 1.7, height: 0.9)), with: .color(Color(rgb: 0xD1342B)))
            l.fill(Path(CGRect(x: 0, y: 1.75, width: 1.2, height: 0.6)), with: .color(Color(rgb: 0xBFE3F5)))
            l.fill(poly([(-2.3, 0.1), (-3, 0.1), (-3.1, 1.3), (-2.3, 1.1)]), with: .color(Color(rgb: 0x8F99A6))) // blade
            l.fill(Path(CGRect(x: 0.4, y: 2.5, width: 0.4, height: 0.22)), with: .color(Color(rgb: sin(t * 8) > 0 ? 0xF4B942 : 0x8A6A1F))) // beacon
        }
        // the others waiting, facing you and waving
        for sp in c.spots where !(sp.lost || sp.got || sp.x < x0 - 5 || sp.x > x1 + 5) {
            let a = c.slope(sp.x)
            skier(&w, sp.x, c.ground(sp.x), a, a * 0.4, Arcade.coat(sp.coat), wave: t + Float(sp.coat))
        }
        if let g {
            // the best run, as a ghost
            let gi = g.ghostAt(t)
            if gi >= 0, let gh = g.ghost {
                w.drawLayer { l in
                    l.opacity = 0.35
                    self.skier(&l, gh.r[gi + 1], gh.r[gi + 2], gh.r[gi + 3], gh.r[gi + 3] * 0.5, Arcade.coat(gh.who))
                }
            }
            // the crew in your tracks, SPACING metres apart; they jump and flip where you did
            for (k, rd) in g.crew.enumerated() {
                let p = g.histAt(g.x - DescentGame.spacing * Float(k + 1))
                var x = p.x; var y = p.air ? p.y : c.ground(p.x); let ang = p.air ? p.ang : c.slope(p.x)
                if let sp = c.spots.first(where: { $0.coat == rd.coat }), sp.joinT > 0 { let f = sp.joinT / 0.7; x = x * (1 - f) + sp.x * f; y = y * (1 - f) + c.ground(sp.x) * f }
                if p.air { shadow(&w, c, x, y - c.ground(x)) }
                skier(&w, x, y, ang, p.air ? ang : ang * 0.4, Arcade.coat(rd.coat), crouch: p.crouch * 0.8, scarf: t + Float(k))
            }
            // the dog
            if g.dogOn {
                let dx = g.x - DescentGame.spacing * Float(g.crew.count + 1) + sin(g.dogT * 1.3) * 1.5, dy = c.ground(dx) + abs(sin(g.dogT * 12)) * 0.35
                var l = w; l.translateBy(x: CGFloat(dx), y: CGFloat(dy)); l.rotate(by: .radians(Double(c.slope(dx))))
                let fur = Color(rgb: 0xC99A5B)
                l.fill(Path(ellipseIn: CGRect(x: -0.6, y: 0.27, width: 1.2, height: 0.56)), with: .color(fur))
                l.fill(Path(ellipseIn: CGRect(x: 0.37, y: 0.62, width: 0.5, height: 0.4)), with: .color(fur))
                var ear = l; ear.translateBy(x: 0.62, y: 1); ear.rotate(by: .radians(-0.4)); ear.translateBy(x: -0.62, y: -1)
                ear.fill(Path(ellipseIn: CGRect(x: 0.54, y: 0.87, width: 0.16, height: 0.26)), with: .color(Color(rgb: 0x8C6434)))
                let ph = CGFloat(g.dogT * 14)
                var legs = Path()
                legs.move(to: CGPoint(x: -0.4, y: 0.4)); legs.addLine(to: CGPoint(x: -0.4 + sin(ph) * 0.2, y: 0))
                legs.move(to: CGPoint(x: 0.35, y: 0.4)); legs.addLine(to: CGPoint(x: 0.35 - sin(ph) * 0.2, y: 0))
                legs.move(to: CGPoint(x: -0.6, y: 0.6)); legs.addLine(to: CGPoint(x: -0.9, y: 0.85 + sin(ph) * 0.1))
                l.stroke(legs, with: .color(fur), style: StrokeStyle(lineWidth: 0.13, lineCap: .round))
                l.fill(circle(0.72, 0.86, 0.04), with: .color(ink))
            }
            // you
            let a = c.slope(g.x); if g.air { shadow(&w, c, g.x, g.y - c.ground(g.x)) }
            let onSnow = !g.air && g.crashT <= 0
            skier(&w, g.x, g.y, onSnow ? a : g.ang, onSnow ? a * 0.4 : g.ang, Arcade.coat(g.me), crouch: g.crouch, scarf: t, squash: g.squash)
            for p in g.bits {
                let col = Color(rgb: p.col, opacity: Double(min(max(p.life, 0), 1)))
                if p.conf { w.fill(Path(CGRect(x: CGFloat(p.x) - 0.12, y: CGFloat(p.y) - 0.08, width: 0.24, height: 0.16)), with: .color(col)) }
                else { w.fill(circle(CGFloat(p.x), CGFloat(p.y), CGFloat(p.r)), with: .color(col)) }
            }
        }
        // the ghost's label, in points
        if let g, let gh = g.ghost { let gi = g.ghostAt(t); if gi >= 0 { label(&cv, sx(gh.r[gi + 1]), sy(gh.r[gi + 2]), S.gameDescentGhostLabel, .white, 0.35, type) } }
        // the avalanche: a wall of powder behind you
        if let g, g.avalancheOn {
            let X = sx(g.avaX)
            if X > -200 {
                var p = Path(); p.move(to: CGPoint(x: -10, y: H)); var yy = H
                while yy > H * 0.08 { p.addLine(to: CGPoint(x: X + (sin(yy * 0.05 + CGFloat(t) * 6) * 16 + sin(yy * 0.013 + CGFloat(t) * 2) * 24), y: yy)); yy -= 18 }
                p.addLine(to: CGPoint(x: -10, y: H * 0.08)); p.closeSubpath()
                cv.fill(p, with: .linearGradient(Gradient(stops: [.init(color: .white, location: 0), .init(color: Color(rgb: 0xF0F6FC, opacity: 0.95), location: 0.8), .init(color: Color(rgb: 0xF0F6FC, opacity: 0), location: 1)]),
                                                 startPoint: CGPoint(x: X - 260, y: 0), endPoint: CGPoint(x: X + 40, y: 0)))
            }
        }
        guard let g else { return }
        // ridge wind: streaks blowing up the run, against you
        if c.zoneAt(g.x)?.kind == Course.wind && !still {
            var p = Path()
            for i in 0..<22 {
                let y = (CGFloat(i) * 53).truncatingRemainder(dividingBy: H)
                let x = (CGFloat(i) * 211 + CGFloat(g.windT) * 1100).truncatingRemainder(dividingBy: W + 200) - 100
                p.move(to: CGPoint(x: x, y: y)); p.addLine(to: CGPoint(x: x - 60, y: y + 4))
            }
            ctx.stroke(p, with: .color(.white.opacity(0.75)), lineWidth: 2)
        }
        if fog > 0 {
            ctx.fill(Path(CGRect(origin: .zero, size: size)), with: .linearGradient(Gradient(colors: [Color(rgb: 0xECF2F8, opacity: 0), Color(rgb: 0xECF2F8, opacity: min(1, fog * 1.4))]),
                                                                              startPoint: CGPoint(x: W * 0.55, y: 0), endPoint: CGPoint(x: W, y: 0)))
        }
        // speed lines when fast
        if g.s > 18 && !still {
            var p = Path()
            for i in 0..<8 {
                let y = (CGFloat(i) * 97 + CGFloat(t) * 900).truncatingRemainder(dividingBy: H), x = (CGFloat(i) * 151 + CGFloat(t) * 1400).truncatingRemainder(dividingBy: W)
                p.move(to: CGPoint(x: W - x, y: y)); p.addLine(to: CGPoint(x: W - x - (40 + CGFloat(g.s - 18) * 6), y: y + 8))
            }
            ctx.stroke(p, with: .color(.white.opacity(0.55)), lineWidth: 2)
        }
        if g.state != .end { miniMap(&ctx, g) }
    }

    private func circle(_ x: CGFloat, _ y: CGFloat, _ r: CGFloat) -> Path { Path(ellipseIn: CGRect(x: x - r, y: y - r, width: 2 * r, height: 2 * r)) }
    private func poly(_ pts: [(CGFloat, CGFloat)]) -> Path {
        var p = Path(); p.move(to: CGPoint(x: pts[0].0, y: pts[0].1)); for q in pts.dropFirst() { p.addLine(to: CGPoint(x: q.0, y: q.1)) }; p.closeSubpath(); return p
    }

    private func treeRow(_ cv: inout GraphicsContext, _ c: Course, _ gap: CGFloat, _ off: Int, _ hmin: CGFloat, _ hmax: CGFloat, _ col: Color, _ par: CGFloat) {
        let cxw = camX * par
        var i = Int(((cxw - 40) / gap).rounded(.down))
        while CGFloat(i) < (cxw + W / sc + 40) / gap {
            if hash(i + off) >= 0.45 {
                let wx = CGFloat(i) * gap + hash(i * 3 + off) * gap * 0.8, gx = camX + (wx - cxw)
                if gx >= 0 && gx <= CGFloat(c.len) { tree(&cv, sx(Float(gx)), sy(c.baseGround(Float(gx))) + CGFloat(off) * sc, (hmin + (hmax - hmin) * hash(i * 7 + off)) * sc, col) }
            }
            i += 1
        }
    }
    private func tree(_ cv: inout GraphicsContext, _ X: CGFloat, _ Y: CGFloat, _ h: CGFloat, _ col: Color) {
        var body = Path(), snow = Path()
        for k in 0..<3 {
            let w = h * (0.42 - CGFloat(k) * 0.1), y = Y - h * (0.28 + CGFloat(k) * 0.24)
            body.move(to: CGPoint(x: X, y: y - h * 0.34)); body.addLine(to: CGPoint(x: X + w, y: y + h * 0.06)); body.addLine(to: CGPoint(x: X - w, y: y + h * 0.06)); body.closeSubpath()
            snow.move(to: CGPoint(x: X, y: y - h * 0.34)); snow.addLine(to: CGPoint(x: X + w * 0.45, y: y - h * 0.12)); snow.addLine(to: CGPoint(x: X - w * 0.45, y: y - h * 0.12)); snow.closeSubpath()
        }
        body.addRect(CGRect(x: X - h * 0.04, y: Y - h * 0.28, width: h * 0.08, height: h * 0.28))
        cv.fill(body, with: .color(col)); cv.fill(snow, with: .color(.white.opacity(0.85)))
    }

    /// A warning sign on a post, the flag pointing back up the run (the start in the run's colour).
    private func sign(_ cv: inout GraphicsContext, _ c: Course, _ x: Float, _ s: String, start: Bool, _ type: SkiType) {
        let x0 = Float(camX - 3), x1 = Float(camX + W / sc + 3)
        if x < x0 - 10 || x > x1 + 10 { return }
        let X = sx(x), Y = sy(c.ground(x))
        let text = cv.resolve(Text(s).font(type.text(max(11, (sc * 0.75).rounded()))).foregroundColor(start ? .white : Self.ink))
        let tw = text.measure(in: CGSize(width: 1000, height: 200)).width
        let w = tw + sc * 1.2, hh = sc * 1.1, top = Y - sc * 3.2
        cv.fill(Path(CGRect(x: X - 2, y: top, width: 4, height: sc * 3.2)), with: .color(Self.ink))
        var p = Path(); p.move(to: CGPoint(x: X - w / 2 - hh * 0.4, y: top - hh / 2)); p.addLine(to: CGPoint(x: X - w / 2, y: top - hh)); p.addLine(to: CGPoint(x: X + w / 2, y: top - hh))
        p.addLine(to: CGPoint(x: X + w / 2, y: top)); p.addLine(to: CGPoint(x: X - w / 2, y: top)); p.closeSubpath()
        cv.fill(p, with: .color(start ? Color(rgb: descentRunColor(c.color)) : Color(rgb: 0xF08A3C)))
        cv.draw(text, at: CGPoint(x: X, y: top - hh / 2), anchor: .center)
    }

    private func label(_ cv: inout GraphicsContext, _ X: CGFloat, _ Y: CGFloat, _ s: String, _ bg: Color, _ alpha: Double, _ type: SkiType) {
        let text = cv.resolve(Text(s).font(type.text(max(8, 0.8 * sc))).foregroundColor(Self.ink))
        let w = text.measure(in: CGSize(width: 1000, height: 200)).width + 0.5 * sc
        var l = cv; l.opacity = alpha
        l.fill(Path(CGRect(x: X - w / 2, y: Y - 4.3 * sc, width: w, height: 1.05 * sc)), with: .color(bg))
        l.draw(text, at: CGPoint(x: X, y: Y - 3.775 * sc), anchor: .center)
    }

    private func shadow(_ w: inout GraphicsContext, _ c: Course, _ x: Float, _ hgt: Float) {
        if c.inGap(x) != nil { return }
        let s = CGFloat(max(0.2, 1 - hgt / 10))
        var l = w; l.translateBy(x: CGFloat(x), y: CGFloat(c.ground(x) + 0.02)); l.rotate(by: .radians(Double(c.slope(x)))); l.scaleBy(x: 1, y: 0.28)
        l.fill(circle(0, 0, 1.3 * s), with: .color(Color(rgb: 0x283C5A, opacity: Double(0.25 * s))))
    }

    /// A skier (the site's skier()): skis along the snow, the body upright over the boots and leaning into the fall
    /// line; in the air everything turns together. A coat in `col`, a helmet and goggles; `wave` waves, `scarf` flutters.
    private func skier(_ w: inout GraphicsContext, _ x: Float, _ y: Float, _ skiAng: Float, _ bodyAng: Float, _ col: Color,
                       crouch cr: Float = 0, wave: Float? = nil, scarf: Float? = nil, squash: Float = 0) {
        let crouch = CGFloat(cr), h = 1 - 0.32 * crouch, k: CGFloat = 1.6
        var base = w; base.translateBy(x: CGFloat(x), y: CGFloat(y))
        if squash != 0 { base.rotate(by: .radians(Double(skiAng))); base.scaleBy(x: 1 - CGFloat(squash) * 0.18, y: 1 + CGFloat(squash) * 0.3); base.rotate(by: .radians(-Double(skiAng))) }
        let ink = Self.ink
        do {
            var s = base; s.rotate(by: .radians(Double(skiAng))); s.scaleBy(x: k, y: k)
            var p = Path(); p.move(to: CGPoint(x: -0.95, y: 0.04)); p.addLine(to: CGPoint(x: 1, y: 0.04)); p.addQuadCurve(to: CGPoint(x: 1.28, y: 0.2), control: CGPoint(x: 1.22, y: 0.04))
            s.stroke(p, with: .color(ink), style: StrokeStyle(lineWidth: 0.13, lineCap: .round))
        }
        var b = base; b.rotate(by: .radians(Double(bodyAng))); b.scaleBy(x: k, y: k)
        let kneeX = 0.18 + 0.25 * crouch, hipY = 0.85 * h
        var legs = Path(); legs.move(to: CGPoint(x: 0, y: 0.1)); legs.addLine(to: CGPoint(x: kneeX, y: 0.45 * h)); legs.addLine(to: CGPoint(x: -0.05, y: hipY))
        b.stroke(legs, with: .color(Color(rgb: 0x2A3B55)), style: StrokeStyle(lineWidth: 0.2, lineCap: .round, lineJoin: .round))
        b.fill(Path(CGRect(x: -0.14, y: 0.04, width: 0.3, height: 0.14)), with: .color(Color(rgb: 0x1A2A40))) // boots
        let torso = 0.35 * crouch // tuck folds the torso forward
        var u = b; u.translateBy(x: -0.05, y: hipY); u.rotate(by: .radians(-Double(torso)))
        if let scarf {
            var p = Path(); p.move(to: CGPoint(x: 0.05, y: 0.72))
            for s in 1...4 { let sf = CGFloat(s); p.addLine(to: CGPoint(x: 0.05 - sf * 0.22, y: 0.72 + sin(CGFloat(scarf) * 14 + sf * 1.3) * 0.07 * sf - sf * 0.02)) }
            u.stroke(p, with: .color(col), style: StrokeStyle(lineWidth: 0.12, lineCap: .round, lineJoin: .round))
        }
        var coat = Path(); coat.move(to: CGPoint(x: -0.2, y: -0.05)); coat.addQuadCurve(to: CGPoint(x: 0.1, y: 0.78), control: CGPoint(x: -0.26, y: 0.7))
        coat.addQuadCurve(to: CGPoint(x: 0.3, y: -0.05), control: CGPoint(x: 0.4, y: 0.66)); coat.closeSubpath()
        u.fill(coat, with: .color(col))
        u.fill(Path(CGRect(x: -0.2, y: 0.25, width: 0.5, height: 0.07)), with: .color(.white.opacity(0.35))) // jacket stripe
        if let wave {
            let wv = sin(CGFloat(wave) * 9) * 0.5
            var p = Path(); p.move(to: CGPoint(x: 0.08, y: 0.6)); p.addLine(to: CGPoint(x: 0.35, y: 0.95 + wv * 0.2)); p.addLine(to: CGPoint(x: 0.5 + wv * 0.15, y: 1.25))
            u.stroke(p, with: .color(col), style: StrokeStyle(lineWidth: 0.17, lineCap: .round, lineJoin: .round))
        } else {
            var arm = Path(); arm.move(to: CGPoint(x: 0.08, y: 0.58)); arm.addLine(to: CGPoint(x: 0.5, y: 0.3 - 0.1 * crouch))
            u.stroke(arm, with: .color(col), style: StrokeStyle(lineWidth: 0.17, lineCap: .round))
            var pole = Path(); pole.move(to: CGPoint(x: 0.5, y: 0.3 - 0.1 * crouch)); pole.addLine(to: CGPoint(x: -0.25 - 0.3 * crouch, y: -hipY + 0.08))
            u.stroke(pole, with: .color(Color(rgb: 0x3A4556)), lineWidth: 0.05) // arm and pole
        }
        u.fill(circle(0.12, 1, 0.25), with: .color(Color(rgb: 0xF4F7FB))); u.stroke(circle(0.12, 1, 0.25), with: .color(ink), lineWidth: 0.06) // helmet
        u.fill(Path(CGRect(x: 0.16, y: 0.93, width: 0.25, height: 0.1)), with: .color(ink)); u.fill(Path(CGRect(x: 0.18, y: 0.95, width: 0.2, height: 0.05)), with: .color(col)) // goggles
    }

    /// The run's real profile across the top, with what is coming.
    private func miniMap(_ cv: inout GraphicsContext, _ g: DescentGame) {
        let c = g.c, top = mapTop, h: CGFloat = 26, left: CGFloat = 12, w = W - 24, n = c.h.count
        func px(_ x: Float) -> CGFloat { left + w * CGFloat(x / c.len) }
        func py(_ v: Float) -> CGFloat { top + h * (1 - CGFloat(v - Float(c.bot)) / CGFloat(max(1, c.top - c.bot))) }
        var p = Path(); p.move(to: CGPoint(x: left, y: top + h)); var i = 0
        while i < n { p.addLine(to: CGPoint(x: px(Float(i) * 5), y: py(c.h[i]))); i += 2 }
        p.addLine(to: CGPoint(x: left + w, y: top + h)); p.closeSubpath()
        cv.fill(p, with: .color(Self.ink.opacity(0.38)))
        var done = cv; done.clip(to: Path(CGRect(x: left, y: top - 4, width: max(0, px(g.x) - left), height: h + 8)))
        done.fill(p, with: .color(Color(rgb: 0xF4B942, opacity: 0.85)))
        func mark(_ x: Float, _ col: Color, _ s: CGFloat) { cv.fill(Path(CGRect(x: px(x) - s / 2, y: py(c.realH(x)) - s - 2, width: s, height: s)), with: .color(col)) }
        for gp in c.gaps { mark(gp.x0, Color(rgb: 0xD1342B), 5) }
        for k in c.cats { mark(k.x, Color(rgb: 0xD1342B), 6) }
        for sp in c.spots where !sp.got && !sp.lost { mark(sp.x, Arcade.coat(sp.coat), 6) }
        if g.avalancheOn && g.avaX > 0 { cv.fill(Path(CGRect(x: px(g.avaX) - 2, y: top - 3, width: 4, height: h + 6)), with: .color(.white)) }
        let me = circle(px(g.x), py(c.realH(g.x)) - 1, 5)
        cv.fill(me, with: .color(Arcade.coat(g.me))); cv.stroke(me, with: .color(.white), lineWidth: 2)
    }
}
