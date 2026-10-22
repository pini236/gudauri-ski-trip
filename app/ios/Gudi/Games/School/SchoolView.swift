import UIKit
import GudiDomain

/// The ski school's slope (the site's canvas; Android's SchoolView.kt): a bright training slope from behind the skier,
/// forward up, with the lesson's marks (stop lines, the green band, speed traps, gates), the tracks (two thin lines when
/// clean, a wide smear when skidding, a V in the wedge), the other skiers and the skier. The same in both themes. A
/// finger on it is the lesson's control; the screen around it holds the words and the buttons.
final class SchoolView: UIView {
    var run: SchoolRun? {
        didSet { lastFrame = 0; if let r = run { place(r); start() } else { stop() }; setNeedsDisplay() }
    }
    /// Each frame, after the slope moved.
    var onFrame: ((SchoolRun) -> Void)?
    var font = UIFont.boldSystemFont(ofSize: 13)
    /// The canvas's words: the stop line, the band, the trap and the tilt.
    var words: (Say) -> String = { _ in "" }
    private let still: Bool

    init(still: Bool) {
        self.still = still
        super.init(frame: .zero)
        isOpaque = true
        isMultipleTouchEnabled = true
        backgroundColor = UIColor(game: 0xFBFDFF)
        contentMode = .redraw
    }
    required init?(coder: NSCoder) { fatalError() }

    /// The run's screen: this view's size, and a metre of slope by the site's PX (16 to 28 points).
    func place(_ r: SchoolRun) {
        r.w = Float(bounds.width); r.h = Float(bounds.height); r.px = Float(min(28, max(16, bounds.width / 19)))
    }

    override func layoutSubviews() { super.layoutSubviews(); if let r = run { place(r) } }

    // ---------- the frame clock, while a lesson runs ----------
    private var link: CADisplayLink?
    private var lastFrame: CFTimeInterval = 0
    private func start() {
        guard link == nil else { return }
        let l = CADisplayLink(target: self, selector: #selector(tick)); l.add(to: .main, forMode: .common); link = l
    }
    private func stop() { link?.invalidate(); link = nil }
    override func willMove(toWindow w: UIWindow?) { if w == nil { stop() } else if run != nil { start() } }

    @objc private func tick() {
        guard let r = run else { return }
        let now = CACurrentMediaTime()
        let dt = lastFrame == 0 ? 0.016 : min(0.033, now - lastFrame)
        lastFrame = now
        if r.w != Float(bounds.width) { place(r) }
        r.update(Float(dt))
        onFrame?(r)
        setNeedsDisplay()
    }

    // ---------- touch: a finger is the lesson's control; in the rhythm lesson every touch is a tap ----------
    private var first: UITouch?
    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let r = run else { return }
        for t in touches {
            if first == nil { first = t; let p = t.location(in: self); r.finger = SchoolRun.Finger(Float(p.x), Float(p.y)) }
            r.tap()
        }
    }
    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let r = run, let f = first, touches.contains(f) else { return }
        let p = f.location(in: self)
        if let finger = r.finger { finger.x = Float(p.x); finger.y = Float(p.y) }
    }
    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) { up(touches) }
    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) { up(touches) }
    private func up(_ touches: Set<UITouch>) {
        if let f = first, touches.contains(f) { first = nil; run?.finger = nil }
    }

    // ---------- drawing ----------
    private func col(_ c: UInt32, _ a: CGFloat = 1) -> CGColor { UIColor(game: c, alpha: a).cgColor }
    private func rgba(_ r: CGFloat, _ g: CGFloat, _ b: CGFloat, _ a: CGFloat) -> CGColor { UIColor(red: r / 255, green: g / 255, blue: b / 255, alpha: a).cgColor }

    override func draw(_ rect: CGRect) {
        guard let c = UIGraphicsGetCurrentContext() else { return }
        c.setFillColor(col(0xFBFDFF)); c.fill(bounds)
        guard let r = run else { return }
        draw(c, r)
    }

    private func draw(_ c: CGContext, _ r: SchoolRun) {
        let w = CGFloat(r.w), h = CGFloat(r.h), px = CGFloat(r.px), lv = r.lv, s = r.s
        func X(_ x: Float) -> CGFloat { CGFloat(r.sx(x)) }
        func Y(_ y: Float) -> CGFloat { CGFloat(r.sy(y)) }
        let edge: Float = 15
        let yTop = r.camY + Float(h * 0.7 / px) + 2, yBot = r.camY - Float(h * 0.3 / px) - 2
        let left = X(-edge), right = X(edge)
        c.setFillColor(col(0xEAF1F7)); c.fill(CGRect(x: 0, y: 0, width: left, height: h)); c.fill(CGRect(x: right, y: 0, width: w - right, height: h))
        // the snow's texture
        c.setFillColor(rgba(159, 181, 203, 0.16))
        var y = (yBot / 6).rounded(.down) * 6
        while y < yTop {
            for k in 0..<4 {
                let x = (y * 7.3 + Float(k) * 11).truncatingRemainder(dividingBy: 28) - 14
                let cx = X(x), cy = Y(y + (Float(k) * 2.1).truncatingRemainder(dividingBy: 6))
                c.fillEllipse(in: CGRect(x: cx - px * 1.2, y: cy - px * 0.25, width: px * 2.4, height: px * 0.5))
            }
            y += 6
        }
        y = (yBot / 9).rounded(.down) * 9
        while y < yTop {
            for side: Float in [-1, 1] { tree(c, px, X(side * (edge + 2 + (y * 3.7).truncatingRemainder(dividingBy: 5))), Y(y), h) }
            y += 9
        }
        // the drill's marks
        if lv.mode == .wedge {
            for (i, sy0) in SchoolRun.stopLines.enumerated() {
                let yy = Y(sy0)
                let cc: UInt32 = i >= r.stops.count ? 0xF08A3C : r.stops[i] == nil ? 0xD1342B : 0x1B8A4C
                c.setFillColor(col(cc)); c.fill(CGRect(x: left, y: yy - 3, width: right - left, height: 6))
                c.setFillColor(rgba(27, 138, 76, 0.12)); c.fill(CGRect(x: left, y: yy, width: right - left, height: px * 1.5))
                label(c, words(Say("game.school.canvas_line", ["n": .text(String(i + 1))])), left + 40, yy - 8, col(cc))
            }
            c.setFillColor(rgba(27, 138, 76, 0.13))
            c.fill(CGRect(x: left, y: Y(SchoolRun.band[1]), width: right - left, height: Y(SchoolRun.band[0]) - Y(SchoolRun.band[1])))
            label(c, words(Say("game.school.canvas_band")), right - 90, Y(SchoolRun.band[0]) - 8, col(0x1B8A4C))
        }
        if let traps = lv.traps {
            for (i, ty) in traps.enumerated() {
                let yy = Y(ty)
                let cc: UInt32 = i >= r.traps.count ? 0x1F5FC4 : r.traps[i] ? 0x1B8A4C : 0xD1342B
                c.setFillColor(col(cc, 0.18)); c.fill(CGRect(x: left, y: yy - px * 1.2, width: right - left, height: px * 1.2))
                c.setFillColor(col(cc)); c.fill(CGRect(x: left, y: yy - 2, width: right - left, height: 4))
                label(c, words(Say("game.school.canvas_trap", ["limit": .text(String(lv.trapLimit))])), X(0), yy - px * 1.3, col(cc))
            }
        }
        // tracks: two thin lines when clean, a wide smear when skidding, a V in the wedge
        c.setLineCap(.butt)
        let tr = r.tracks
        if tr.count > 1 {
            for side: Float in [-1, 1] {
                for i in 1..<tr.count {
                    let p = tr[i - 1], q = tr[i]
                    if q.y < yBot - 2 || p.y > yTop + 2 { continue }
                    let op = (0.17 + p.wd * 0.25) * side, oq = (0.17 + q.wd * 0.25) * side
                    if q.sk > 0.25 { c.setStrokeColor(rgba(159, 181, 203, 0.45)); c.setLineWidth(px * CGFloat(0.15 + q.sk * 0.5)) }
                    else { c.setStrokeColor(rgba(70, 95, 125, 0.55)); c.setLineWidth(1.6) }
                    c.move(to: CGPoint(x: X(p.x + cos(p.h) * op), y: Y(p.y - sin(p.h) * op)))
                    c.addLine(to: CGPoint(x: X(q.x + cos(q.h) * oq), y: Y(q.y - sin(q.h) * oq)))
                    c.strokePath()
                }
            }
        }
        // gates: in the look-ahead lesson they fade as you come close
        for g in r.gates {
            var al: CGFloat = 1
            if lv.fade && g.done == nil { al = CGFloat(min(1, max(0, (g.y - s.y - 7) / 6))) }
            let a: CGFloat = g.done != nil ? 0.6 : al
            let cc: UInt32 = g.done == false ? 0x9FB5CB : g.x < 0 ? 0xD1342B : 0x1F5FC4
            c.setFillColor(col(cc, a))
            for k: Float in [-1, 1] {
                let xx = X(g.x + k * g.w / 2), yy = Y(g.y)
                c.fill(CGRect(x: xx - 3, y: yy - px * 1.4, width: 6, height: px * 1.4))
                let fx = xx - (k < 0 ? 0 : px * 0.8)
                c.fill(CGRect(x: fx, y: yy - px * 1.4, width: px * 0.8, height: px * 0.5))
            }
            if g.done == true {
                text(c, "✓", X(g.x), Y(g.y) - px * 1.5, UIColor(game: 0x1B8A4C, alpha: a), UIFont.boldSystemFont(ofSize: 14))
            }
        }
        // the finish line
        let fy = Y(lv.len)
        var fx = -edge
        while fx < edge {
            c.setFillColor(Int(fx + edge) % 2 == 1 ? col(0x13233A) : col(0xFFFFFF))
            c.fill(CGRect(x: X(fx), y: fy, width: px, height: px * 0.6)); fx += 1
        }
        for p in r.people { person(c, px, X(p.x), Y(p.y), p.color, CGFloat(cos(p.ph) * 0.6), p.hit) }
        if let f = r.finger, r.running, lv.mode == .steer {
            c.setStrokeColor(rgba(19, 35, 58, 0.18)); c.setLineWidth(2); c.setLineDash(phase: 0, lengths: [4, 6])
            c.move(to: CGPoint(x: X(s.x), y: Y(s.y))); c.addLine(to: CGPoint(x: CGFloat(f.x), y: CGFloat(f.y))); c.strokePath()
            c.setLineDash(phase: 0, lengths: [])
            c.setFillColor(rgba(244, 185, 66, 0.5)); c.fillEllipse(in: CGRect(x: CGFloat(f.x) - 14, y: CGFloat(f.y) - 14, width: 28, height: 28))
        }
        // the fall-line compass around the skier: red straight down, green across
        if lv.traps != nil {
            let xx = X(s.x), yy = Y(s.y), rr = px * 2.2
            c.setLineWidth(6)
            for k in -8...8 {
                let ang = CGFloat(k) / 8 * 1.9, ah = abs(ang)
                c.setStrokeColor(ah < 0.35 ? rgba(209, 52, 43, 0.35) : ah < 1 ? rgba(244, 185, 66, 0.35) : rgba(27, 138, 76, 0.35))
                c.addArc(center: CGPoint(x: xx, y: yy), radius: rr, startAngle: -.pi / 2 + ang - 0.12, endAngle: -.pi / 2 + ang + 0.12, clockwise: false)
                c.strokePath()
            }
            c.setStrokeColor(col(0x13233A)); c.setLineWidth(3)
            c.move(to: CGPoint(x: xx, y: yy)); c.addLine(to: CGPoint(x: xx + CGFloat(sin(s.h)) * rr, y: yy - CGFloat(cos(s.h)) * rr)); c.strokePath()
        }
        if lv.mode == .rhythm && r.pulse > 0 {
            c.setStrokeColor(rgba(244, 185, 66, CGFloat(r.pulse))); c.setLineWidth(6)
            let rad = px * CGFloat(1.6 + (1 - r.pulse) * 1.6)
            c.strokeEllipse(in: CGRect(x: X(s.x) - rad, y: Y(s.y) - rad, width: 2 * rad, height: 2 * rad))
        }
        skier(c, r)
        if lv.mode == .lean { leanGauge(c, r) }
    }

    private func text(_ c: CGContext, _ s: String, _ x: CGFloat, _ y: CGFloat, _ color: UIColor, _ f: UIFont) {
        let a = NSAttributedString(string: s, attributes: [.font: f, .foregroundColor: color])
        let sz = a.size()
        a.draw(at: CGPoint(x: x - sz.width / 2, y: y - sz.height * 0.8))
    }

    private func label(_ c: CGContext, _ s: String, _ x: CGFloat, _ y: CGFloat, _ color: CGColor) {
        let a = NSAttributedString(string: s, attributes: [.font: font, .foregroundColor: UIColor(cgColor: color)])
        let sz = a.size()
        let w = sz.width + 12
        c.setFillColor(UIColor(white: 1, alpha: 0.9).cgColor); c.fill(CGRect(x: x - w / 2, y: y - 15, width: w, height: 19))
        a.draw(at: CGPoint(x: x - sz.width / 2, y: y - 5.5 - sz.height / 2))
    }

    private func leanGauge(_ c: CGContext, _ r: SchoolRun) {
        let x = CGFloat(r.w) / 2, y = CGFloat(r.h) - 70, rr = min(CGFloat(r.w) * 0.36, 150)
        c.setLineWidth(12); c.setStrokeColor(col(0xEEF2F5))
        c.addArc(center: CGPoint(x: x, y: y), radius: rr, startAngle: .pi * 1.15, endAngle: .pi * 1.85, clockwise: false); c.strokePath()
        let ang = CGFloat.pi * 1.5 + CGFloat(r.s.edge) * .pi * 0.35
        c.setStrokeColor(r.s.skid > 0.25 ? col(0xD1342B) : col(0x1B8A4C))
        c.addArc(center: CGPoint(x: x, y: y), radius: rr, startAngle: min(.pi * 1.5, ang), endAngle: max(.pi * 1.5, ang), clockwise: false); c.strokePath()
        c.setFillColor(col(0x13233A))
        c.fillEllipse(in: CGRect(x: x + cos(ang) * rr - 9, y: y + sin(ang) * rr - 9, width: 18, height: 18))
        label(c, words(Say("game.school.canvas_tilt")), x, y - rr * 0.35, col(0x13233A))
    }

    private func tree(_ c: CGContext, _ px: CGFloat, _ x: CGFloat, _ y: CGFloat, _ h: CGFloat) {
        if y < -40 || y > h + 40 { return }
        c.setFillColor(rgba(19, 35, 58, 0.12)); c.fillEllipse(in: CGRect(x: x + 6 - px * 1.1, y: y + 4 - px * 0.5, width: px * 2.2, height: px))
        c.setFillColor(col(0x2F6B4F)); c.fillEllipse(in: CGRect(x: x - px * 1.1, y: y - px * 1.1, width: px * 2.2, height: px * 2.2))
        c.setFillColor(col(0xFFFFFF)); c.fillEllipse(in: CGRect(x: x - px * 0.75, y: y - px * 0.75, width: px * 0.9, height: px * 0.9))
    }

    private func person(_ c: CGContext, _ px: CGFloat, _ x: CGFloat, _ y: CGFloat, _ color: UInt32, _ h: CGFloat, _ hit: Bool) {
        c.saveGState(); c.translateBy(x: x, y: y); c.rotate(by: h)
        c.setFillColor(col(0x13233A))
        c.fill(CGRect(x: -px * 0.28, y: -px * 0.8, width: px * 0.12, height: px * 1.6)); c.fill(CGRect(x: px * 0.16, y: -px * 0.8, width: px * 0.12, height: px * 1.6))
        c.setFillColor(hit ? col(0x9FB5CB) : col(color)); c.fillEllipse(in: CGRect(x: -px * 0.38, y: -px * 0.38, width: px * 0.76, height: px * 0.76))
        c.setFillColor(col(0xF2D2BE)); c.fillEllipse(in: CGRect(x: -px * 0.18, y: -px * 0.28, width: px * 0.36, height: px * 0.36))
        c.restoreGState()
    }

    private func skier(_ c: CGContext, _ r: SchoolRun) {
        let s = r.s, px = CGFloat(r.px)
        c.saveGState(); c.translateBy(x: CGFloat(r.sx(s.x)), y: CGFloat(r.sy(s.y))); c.rotate(by: CGFloat(s.h))
        let wd = CGFloat(s.wedge), len = px * 1.7, sp = px * (0.2 + wd * 0.08), ang = wd * 0.32
        let lean = r.lv.mode == .lean ? CGFloat(s.edge) * px * 0.35 : 0
        c.setFillColor(rgba(19, 35, 58, 0.15)); c.fillEllipse(in: CGRect(x: 4 - px * 0.6, y: 4 - px, width: px * 1.2, height: px * 2))
        // in a wedge the tips (forward, up) come together and the tails spread
        for k: CGFloat in [-1, 1] {
            c.saveGState(); c.translateBy(x: k * sp, y: 0); c.rotate(by: -k * ang)
            let pressed = r.lv.mode == .skis && ((k < 0 && r.skiL) || (k > 0 && r.skiR))
            c.setFillColor(pressed ? col(0xD1342B) : col(0x13233A)); c.fill(CGRect(x: -px * 0.09, y: -len * 0.5, width: px * 0.18, height: len))
            c.setFillColor(col(0xF4B942)); c.fill(CGRect(x: -px * 0.09, y: -len * 0.5, width: px * 0.18, height: px * 0.12))
            c.restoreGState()
        }
        if s.skid > 0.3 && !still {
            c.setFillColor(UIColor(white: 1, alpha: 0.95).cgColor)
            for _ in 0..<4 {
                let x = (CGFloat.random(in: 0..<1) - 0.5) * px * 2, y = (CGFloat.random(in: 0..<1) - 0.2) * px
                c.fillEllipse(in: CGRect(x: x - px * 0.12, y: y - px * 0.12, width: px * 0.24, height: px * 0.24))
            }
        }
        c.setFillColor(col(0xF07A2E)); c.fillEllipse(in: CGRect(x: lean - px * 0.5, y: -px * 0.36, width: px, height: px * 0.72))
        c.setFillColor(col(0x13233A)); c.fillEllipse(in: CGRect(x: lean * 1.3 - px * 0.22, y: -px * 0.08 - px * 0.22, width: px * 0.44, height: px * 0.44))
        c.restoreGState()
    }
}
