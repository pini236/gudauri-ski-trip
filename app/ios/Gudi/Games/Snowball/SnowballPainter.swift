import SwiftUI
import GudiDomain

/// The snowball fight's field (the site's canvas, Android's SnowballView), drawn far to near from behind your own wall:
/// the sky and the real view from the village (the wide noon picture), the snow between the walls, the characters
/// rising from behind their wall, its flag in the wind, the balls with their trails and shadows, hats in the air, your
/// wall close in front, your hand with the next ball, the aim, and snow on your face. One look, a bright winter
/// afternoon, in both themes. With no fight (the menu) only the sky, the view, the snow and your wall.
@MainActor
final class SnowballPainter {
    private static let ink = Color(rgb: 0x13233A)
    private var seed: UInt64 = 3
    private func rnd() -> CGFloat { seed = seed &* 6364136223846793005 &+ 1442695040888963407; return CGFloat(seed >> 40) / CGFloat(1 << 24) }

    // the projection (the site's P): x to the side, y up, z away, from the eye at camX, camH
    private var W: CGFloat = 0, H: CGFloat = 0, f: CGFloat = 600, y0: CGFloat = 300, camX: CGFloat = 0, camH: CGFloat = 0.98
    private var pX: CGFloat = 0, pY: CGFloat = 0, pS: CGFloat = 0
    private func p(_ x: Float, _ y: Float, _ z: Float) { pS = f / max(0.05, CGFloat(z)); pX = W / 2 + (CGFloat(x) - camX) * pS; pY = y0 - (CGFloat(y) - camH) * pS }

    func draw(_ ctx: inout GraphicsContext, _ size: CGSize, fight g: SnowballFight?, pano: Image?, panoAspect: CGFloat, still: Bool) {
        W = size.width; H = size.height
        if let g { f = CGFloat(g.f); y0 = CGFloat(g.y0); camX = CGFloat(g.camX); camH = CGFloat(g.camH) }
        else { f = min(W * 1.75, H * 1.15); y0 = H * 0.42; camX = 0; camH = 0.98 }
        let G = SnowballFight.g, ZF = SnowballFight.zf, ZO = SnowballFight.zo, ZW = SnowballFight.zw, FLIGHT = SnowballFight.flight
        var c = ctx
        if let g, g.shake > 0 { c.translateBy(x: (rnd() * 2 - 1) * CGFloat(g.shake), y: (rnd() * 2 - 1) * CGFloat(g.shake)) }
        let full = Path(CGRect(x: -20, y: -20, width: W + 40, height: H + 40))
        c.fill(full, with: .linearGradient(Gradient(colors: [Color(rgb: 0x4F8FCF), Color(rgb: 0xD6E6F4)]), startPoint: .zero, endPoint: CGPoint(x: 0, y: y0 + 80)))
        // the real view from the village, far away on the horizon: once, always wider than the screen, no seams
        p(0, 0, 400); let hz = pY
        if let pano, panoAspect > 0 {
            var ph = min(H * 0.5, W * 0.95), pw = ph * panoAspect
            if pw < W * 1.25 { pw = W * 1.25; ph = pw / panoAspect }
            let px = max(W - pw, min(0, W / 2 - pw / 2 - camX * 20))
            c.draw(pano, in: CGRect(x: px, y: hz - ph * 0.9, width: pw, height: ph))
        }
        // the snowfield between the walls
        c.fill(Path(CGRect(x: -20, y: hz - 1, width: W + 40, height: H - hz + 1)),
               with: .linearGradient(Gradient(colors: [Color(rgb: 0xE9F1F8), .white]), startPoint: CGPoint(x: 0, y: hz), endPoint: CGPoint(x: 0, y: H)))
        do {
            var drifts = Path(); var z: Float = 3
            while z < ZF { p(0, 0, z); drifts.move(to: CGPoint(x: 0, y: pY)); drifts.addLine(to: CGPoint(x: W, y: pY)); z += 2.5 } // for depth
            c.stroke(drifts, with: .color(Color(rgb: 0x9FB5CB, opacity: 0.25)), lineWidth: 1)
        }
        if let g {
            // ball shadows on the snow, so you can read how far a ball is
            for b in g.balls where b.z >= 1 { p(b.x, 0, b.z); c.fill(Path(ellipseIn: CGRect(x: pX - 0.14 * pS, y: pY - 0.05 * pS, width: 0.28 * pS, height: 0.1 * pS)), with: .color(Self.ink.opacity(0.18))) }
            // the characters, then their wall in front of them; nothing shows below the snow
            p(0, 0, ZF)
            var behind = c; behind.clip(to: Path(CGRect(x: -20, y: -20, width: W + 40, height: pY + 20)))
            for o in g.foes { foe(&behind, g, o) }
            farWall(&c, g)
            for o in g.hats { p(o.x, o.y, o.z); var l = c; l.translateBy(x: pX, y: pY); l.rotate(by: .radians(Double(o.r))); Self.hat(&l, 0, 0, Arcade.coat(o.coat), pS) }
            // snow bits, then the balls and their trails
            for q in g.bits where q.z >= 0.32 {
                p(q.x, q.y, q.z); let r = max(1, CGFloat(q.r) * pS)
                c.fill(Path(ellipseIn: CGRect(x: pX - r, y: pY - r, width: 2 * r, height: 2 * r)), with: .color(.white.opacity(Double(min(1, q.life * 2)))))
            }
            for b in g.balls where b.z >= 0.32 {
                let tr = b.trail
                if tr.count > 1 {
                    for i in 1..<tr.count {
                        p(tr[i - 1].0, tr[i - 1].1, tr[i - 1].2); let a = CGPoint(x: pX, y: pY); let lw = 0.12 * pS
                        p(tr[i].0, tr[i].1, tr[i].2)
                        var l = Path(); l.move(to: a); l.addLine(to: CGPoint(x: pX, y: pY))
                        c.stroke(l, with: .color(.white.opacity(0.55)), style: StrokeStyle(lineWidth: lw, lineCap: .round))
                    }
                }
                if b.z > ZW || b.mine { p(b.x, b.y, b.z); ball(&c, pX, pY, 0.11 * pS) }
            }
        } else { farWall(&c, nil) }
        // your wall, close in front of you
        myWall(&c)
        if let g {
            // incoming balls that cleared your wall fly past your face
            for b in g.balls where !b.mine && b.z <= ZW && b.z > 0.3 { p(b.x, b.y, b.z); ball(&c, pX, pY, 0.11 * pS) }
            // your hand with the next ball, while you are up
            if camH > 1.25 {
                let k = min(1, (camH - 1.25) / 0.45), hx = W - 56, hy = H - 130 * k + (g.reload > 0 ? 60 : 0)
                var sleeve = Path(); sleeve.move(to: CGPoint(x: hx - 22, y: hy + 14)); sleeve.addLine(to: CGPoint(x: hx + 16, y: hy + 18)); sleeve.addLine(to: CGPoint(x: W + 30, y: H + 40)); sleeve.addLine(to: CGPoint(x: W - 90, y: H + 40)); sleeve.closeSubpath()
                c.fill(sleeve, with: .color(Arcade.coat(g.me)))
                c.fill(Path(ellipseIn: CGRect(x: hx - 22, y: hy - 10, width: 40, height: 40)), with: .color(Self.ink))
                if g.reload <= 0 { ball(&c, hx - 2, hy - 6, 20) }
            }
            // the aim: a ring where the ball lands without wind, where the wind will push it, and the arc on the way
            if let a = g.aim, g.state == .play {
                let tx = g.ringX(a), ty = g.ringY(a), up = camH > 1.5 && g.reload <= 0
                let alpha: Double = up ? 1 : 0.35
                p(tx, ty, ZO); let rx = pX, ry = pY, s = pS
                var ring = Path(ellipseIn: CGRect(x: rx - 0.3 * s, y: ry - 0.3 * s, width: 0.6 * s, height: 0.6 * s))
                ring.move(to: CGPoint(x: rx - 0.42 * s, y: ry)); ring.addLine(to: CGPoint(x: rx + 0.42 * s, y: ry))
                ring.move(to: CGPoint(x: rx, y: ry - 0.42 * s)); ring.addLine(to: CGPoint(x: rx, y: ry + 0.42 * s))
                c.stroke(ring, with: .color(Color(rgb: 0xD1342B, opacity: alpha)), lineWidth: 3)
                if abs(g.wind) > 0.2 {
                    p(tx + 0.5 * g.wind * 0.5 * FLIGHT * FLIGHT, ty, ZO)
                    c.stroke(Path(ellipseIn: CGRect(x: pX - 0.3 * s, y: ry - 0.3 * s, width: 0.6 * s, height: 0.6 * s)), with: .color(Self.ink.opacity(0.5 * alpha)),
                             style: StrokeStyle(lineWidth: 1, dash: [4, 4]))
                }
                let x0: Float = 0.32, yy = Float(camH) - 0.3, z0 = SnowballFight.handZ
                for k in 1..<12 {
                    let tt = Float(k) / 12 * FLIGHT
                    let x = x0 + (tx - x0) * tt / FLIGHT, zz = z0 + (ZO - z0) * tt / FLIGHT
                    let y = yy + ((ty - yy + 0.5 * G * FLIGHT * FLIGHT) / FLIGHT) * tt - 0.5 * G * tt * tt
                    if zz < ZW + 0.2 { continue }
                    p(x, y, zz); let r = max(1.5, 0.03 * pS)
                    c.fill(Path(ellipseIn: CGRect(x: pX - r, y: pY - r, width: 2 * r, height: 2 * r)), with: .color(Self.ink.opacity(0.5 * alpha)))
                }
            }
        }
        guard let g else { return }
        // snow on your face: white blobs that slide down and fade
        for s in g.splats {
            let a = Double(min(1, s.life / 0.6)), slide = CGFloat(s.max - s.life), yy = CGFloat(s.y) + slide * 22, r = CGFloat(s.r), x = CGFloat(s.x), seed = CGFloat(s.seed)
            var blob = Path(ellipseIn: CGRect(x: x - r * 0.62, y: yy - r * 0.62, width: r * 1.24, height: r * 1.24))
            for i in 0..<7 {
                let ang = seed + CGFloat(i) * 0.9, dd = r * (0.45 + 0.25 * sin(seed * 3 + CGFloat(i))), rr = r * (0.22 + 0.12 * cos(CGFloat(i) + seed))
                blob.addEllipse(in: CGRect(x: x + cos(ang) * dd - rr, y: yy + sin(ang) * dd - rr, width: 2 * rr, height: 2 * rr))
            }
            blob.addRoundedRect(in: CGRect(x: x - r * 0.12, y: yy, width: r * 0.24, height: slide * 36 + r * 0.4), cornerSize: CGSize(width: r * 0.12, height: r * 0.12))
            ctx.fill(blob, with: .color(.white.opacity(a * 0.92)))
            ctx.fill(Path(ellipseIn: CGRect(x: x + r * 0.15 - r * 0.3, y: yy + r * 0.18 - r * 0.3, width: r * 0.6, height: r * 0.6)), with: .color(Color(rgb: 0xC9D8E7, opacity: a * 0.92 * 0.7)))
        }
        // wind streaks
        if abs(g.wind) > 2.5 && !still && g.state == .play {
            var p = Path(); let span = W + 120
            for i in 0..<9 {
                let y = (CGFloat(i) * 67).truncatingRemainder(dividingBy: y0 + 40) + 20
                let x = ((CGFloat(i) * 173 + CGFloat(g.t * g.wind) * 70).truncatingRemainder(dividingBy: span) + span).truncatingRemainder(dividingBy: span) - 60
                p.move(to: CGPoint(x: x, y: y)); p.addLine(to: CGPoint(x: x + (g.wind > 0 ? 36 : -36), y: y))
            }
            ctx.stroke(p, with: .color(.white.opacity(0.65)), lineWidth: 2)
        }
    }

    private func ball(_ c: inout GraphicsContext, _ x: CGFloat, _ y: CGFloat, _ r: CGFloat) {
        let rr = max(1.5, r), e = Path(ellipseIn: CGRect(x: x - rr, y: y - rr, width: 2 * rr, height: 2 * rr))
        c.fill(e, with: .color(.white)); c.stroke(e, with: .color(Color(rgb: 0x9FB5CB)), lineWidth: max(1, rr * 0.12))
    }

    private func farWall(_ c: inout GraphicsContext, _ g: SnowballFight?) {
        let ZF = SnowballFight.zf
        var path = Path(); p(-6, 0, ZF); path.move(to: CGPoint(x: pX, y: pY))
        for i in 0..<49 { p(-6 + Float(i) * 0.25, g?.farWall[i] ?? (SnowballFight.fwall + 0.06 * sin(Float(i) * 1.7)), ZF); path.addLine(to: CGPoint(x: pX, y: pY)) }
        p(6, 0, ZF); path.addLine(to: CGPoint(x: pX, y: pY)); path.closeSubpath()
        c.fill(path, with: .color(Color(rgb: 0xF7FAFD))); c.stroke(path, with: .color(Color(rgb: 0xA9BED3)), lineWidth: 1.5)
        // a little flag that shows the wind
        let wind = g?.wind ?? 0, t = CGFloat(g?.t ?? 0)
        p(5.2, g?.farWallTop(5.2) ?? SnowballFight.fwall, ZF); let fx = pX, fy = pY, s = pS
        var pole = Path(); pole.move(to: CGPoint(x: fx, y: fy)); pole.addLine(to: CGPoint(x: fx, y: fy - 1.3 * s))
        c.stroke(pole, with: .color(Color(rgb: 0x4B5A6F)), lineWidth: 2)
        let fl = CGFloat(min(max(wind / 4, -1), 1)), sg: CGFloat = fl == 0 ? 1 : (fl > 0 ? 1 : -1)
        var flag = Path(); flag.move(to: CGPoint(x: fx, y: fy - 1.3 * s)); flag.addLine(to: CGPoint(x: fx + (0.2 + 0.5 * abs(fl)) * s * sg, y: fy - (1.2 + 0.04 * sin(t * 12)) * s)); flag.addLine(to: CGPoint(x: fx, y: fy - 1.05 * s)); flag.closeSubpath()
        c.fill(flag, with: .color(Color(rgb: 0xD1342B)))
    }

    private func wallTop(_ x: Float) -> Float { SnowballFight.wall + 0.02 * sin(x * 3.1) + 0.012 * sin(x * 7.3) }
    private func myWall(_ c: inout GraphicsContext) {
        let ZW = SnowballFight.zw
        var path = Path(); path.move(to: CGPoint(x: -20, y: H + 20)); p(-4, wallTop(-4), ZW); path.addLine(to: CGPoint(x: -20, y: pY))
        var x: Float = -4; while x <= 4.0001 { p(x, wallTop(x), ZW); path.addLine(to: CGPoint(x: pX, y: pY)); x += 0.1 }
        path.addLine(to: CGPoint(x: W + 20, y: pY)); path.addLine(to: CGPoint(x: W + 20, y: H + 20)); path.closeSubpath()
        p(0, SnowballFight.wall, ZW)
        c.fill(path, with: .linearGradient(Gradient(colors: [.white, Color(rgb: 0xC9D8E7)]), startPoint: CGPoint(x: 0, y: pY), endPoint: CGPoint(x: 0, y: H)))
        c.stroke(path, with: .color(Color(rgb: 0xB8CADB)), lineWidth: 2)
        // packed-snow texture
        var tex = Path()
        for i in 0..<26 {
            p(-3.6 + (Float(i) * 1.37).truncatingRemainder(dividingBy: 7.2), 0.2 + (Float(i) * 0.53).truncatingRemainder(dividingBy: SnowballFight.wall - 0.35), ZW)
            tex.addEllipse(in: CGRect(x: pX - 0.12 * pS, y: pY - 0.05 * pS, width: 0.24 * pS, height: 0.1 * pS))
        }
        c.fill(tex, with: .color(Color(rgb: 0x9FB5CB, opacity: 0.18)))
    }

    /// A character facing you, rising from behind the far wall (the site's foe()).
    private func foe(_ c: inout GraphicsContext, _ g: SnowballFight, _ o: SnowballFight.Foe) {
        if o.hp <= 0 && o.up < 0.05 { return }
        p(o.x, g.base(o), SnowballFight.zo)
        Self.character(&c, pX, pY, pS, Arcade.coat(o.coat), CGFloat(sin(g.t * 30) * o.wob * 0.12), hit: o.hit > 0,
                       arm: o.throwT > 0 ? -1.1 : o.phase == SnowballFight.aimPhase ? 1.2 : 0.3, holding: o.phase == SnowballFight.aimPhase, out: o.hp <= 0, hat: o.hat)
    }

    /// A woolly hat in `col` with a white band and a pompom, `s` points to the metre (the site's hat()).
    static func hat(_ c: inout GraphicsContext, _ x: CGFloat, _ y: CGFloat, _ col: Color, _ s: CGFloat) {
        c.fill(arcadeArc(CGPoint(x: x, y: y), 0.2 * s, 0.16 * s, close: true), with: .color(col))
        c.fill(Path(CGRect(x: x - 0.21 * s, y: y - 0.02 * s, width: 0.42 * s, height: 0.07 * s)), with: .color(.white))
        c.fill(Path(ellipseIn: CGRect(x: x - 0.06 * s, y: y - 0.25 * s, width: 0.12 * s, height: 0.12 * s)), with: .color(.white))
    }

    /// A character facing you, feet at (`x`, `y`), `s` points to the metre: legs, a coat in `col` (white for a moment
    /// when hit), the throwing arm at `arm` (with a ball while `holding`), a face, and the hat. The menu's coats too.
    static func character(_ ctx: inout GraphicsContext, _ x: CGFloat, _ y: CGFloat, _ s: CGFloat, _ col: Color, _ wob: CGFloat,
                          hit: Bool = false, arm: CGFloat = 0.3, holding: Bool = false, out: Bool = false, hat show: Bool = true) {
        var c = ctx; c.translateBy(x: x, y: y); c.rotate(by: .radians(Double(wob)))
        let legs = Color(rgb: 0x2A3B55)
        c.fill(Path(CGRect(x: -0.2 * s, y: -0.55 * s, width: 0.15 * s, height: 0.55 * s)), with: .color(legs))
        c.fill(Path(CGRect(x: 0.05 * s, y: -0.55 * s, width: 0.15 * s, height: 0.55 * s)), with: .color(legs))
        c.fill(Path(CGRect(x: -0.28 * s, y: -1.3 * s, width: 0.56 * s, height: 0.8 * s)), with: .color(hit ? .white : col))
        var a = Path(); a.move(to: CGPoint(x: -0.25 * s, y: -1.15 * s)); a.addLine(to: CGPoint(x: -0.25 * s - cos(arm) * 0.1 * s, y: -1.15 * s - sin(arm) * 0.45 * s))
        c.stroke(a, with: .color(col), style: StrokeStyle(lineWidth: 0.13 * s, lineCap: .round))
        if holding {
            let r = max(1.5, 0.11 * s), e = Path(ellipseIn: CGRect(x: -0.27 * s - r, y: -1.65 * s - r, width: 2 * r, height: 2 * r))
            c.fill(e, with: .color(.white)); c.stroke(e, with: .color(Color(rgb: 0x9FB5CB)), lineWidth: max(1, r * 0.12))
        }
        let hy = -1.62 * s
        c.fill(Path(ellipseIn: CGRect(x: -0.22 * s, y: hy - 0.22 * s, width: 0.44 * s, height: 0.44 * s)), with: .color(Color(rgb: 0xF2D2BE)))
        c.fill(Path(CGRect(x: -0.09 * s, y: hy - 0.03 * s, width: 0.05 * s, height: 0.05 * s)), with: .color(ink))
        c.fill(Path(CGRect(x: 0.05 * s, y: hy - 0.03 * s, width: 0.05 * s, height: 0.05 * s)), with: .color(ink))
        if out { var m = Path(); m.move(to: CGPoint(x: -0.1 * s, y: hy + 0.1 * s)); m.addLine(to: CGPoint(x: 0.1 * s, y: hy + 0.1 * s)); c.stroke(m, with: .color(ink), lineWidth: 0.03 * s) }
        if show { hat(&c, 0, hy - 0.12 * s, col, s) }
    }
}
