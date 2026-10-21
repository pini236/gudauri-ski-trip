import UIKit
import GudiDomain

/// Fresh snow (13.6), the site's game on the phone (Android's FreshSnowView.kt): touch the snow with a finger, a boot,
/// skis, a ball or the snowcat, each kind of snow with its own sound; or smash the frozen lake by the village, plate by
/// plate. Several fingers at once. The sizes are the site's, in points; it draws only while something moves.
final class FreshSnowView: UIView {
    enum Tool: CaseIterable { case hand, boot, ski, ball, cat }

    /// The number on screen: tracks in the snow, or the lake broken, in percent.
    var onStat: ((Int) -> Void)?
    var onTouched: (() -> Void)?
    /// A new longest chain of plates on the lake (n, and whether it is shown).
    var onChain: ((Int, Bool) -> Void)?

    private let synth: Synth
    private let still: Bool

    var lake = false {
        didSet { if lake && cells.isEmpty && bounds.width > 0 { initLake() }; report(); kick() }
    }
    var tool = Tool.hand
    /// The new kind applies to new touches only; the tracks already made stay (X-5: it used to wipe the field).
    var kind = FreshSnowKind.powder { didSet { field?.kind = kind; dirty = true; kick() } }
    /// The chain's words ("Chain ×3"), in the app's language and font.
    var chainText: (Int) -> String = { "×\($0)" }
    var display = UIFont.boldSystemFont(ofSize: 52)
    var bestChain = 0

    init(synth: Synth, still: Bool) {
        self.synth = synth; self.still = still
        super.init(frame: .zero)
        isMultipleTouchEnabled = true
        isOpaque = true
        backgroundColor = UIColor(red: 0.91, green: 0.94, blue: 0.97, alpha: 1)
        contentMode = .redraw
    }
    required init?(coder: NSCoder) { fatalError() }

    // ---------- the frame clock: runs only while something moves ----------
    private var link: CADisplayLink?
    private var lastFrame: CFTimeInterval = 0

    private func kick() {
        setNeedsDisplay()
        if link == nil {
            let l = CADisplayLink(target: self, selector: #selector(tick))
            l.add(to: .main, forMode: .common)
            link = l
        }
    }
    @objc private func tick() { setNeedsDisplay() }
    private func idle() { link?.invalidate(); link = nil; lastFrame = 0 }

    override func willMove(toWindow w: UIWindow?) { if w == nil { idle() } }

    // ---------- the snow ----------
    private let cell: CGFloat = 3
    private var field: SnowField?
    private var px: [UInt32] = []
    private var snowImage: CGImage?
    private var dirty = true
    private final class Finger { var lx: CGFloat = 0, ly: CGFloat = 0, has = false, acc: CGFloat = 99 }
    private var fingers: [ObjectIdentifier: Finger] = [:]
    private var stepSide: CGFloat = 1
    private var lastGrain: CFTimeInterval = 0
    private var catX: CGFloat = 0, catY: CGFloat = 0, catAng: CGFloat = 0, catT: CGFloat = 0
    private var falling: CGFloat = 0
    private struct Flake { var x, y: CGFloat; let v, r: CGFloat; var s: CGFloat }
    private var flakes: [Flake] = []
    private var areaT: CGFloat = 0
    private var lastStat = -1
    private var laidOut = CGSize.zero

    override func layoutSubviews() {
        super.layoutSubviews()
        guard bounds.size != laidOut, bounds.width > 0, bounds.height > 0 else { return }
        laidOut = bounds.size
        let gw = Int(ceil(bounds.width / cell)), gh = Int(ceil(bounds.height / cell))
        let f = SnowField(gw: gw, gh: gh); f.kind = kind
        field = f
        px = [UInt32](repeating: 0, count: gw * gh); dirty = true
        if lake || !cells.isEmpty { initLake() }
        report(); kick()
    }

    /// New snow: it falls and slowly fills every track back in (the site's freshSnow).
    func newSnow() {
        if still { field?.fresh(); dirty = true; report(); kick(); return }
        falling = 2.4
        for _ in 0..<90 {
            flakes.append(Flake(x: .random(in: 0..<max(1, bounds.width)), y: -.random(in: 0..<max(1, bounds.height)), v: 40 + .random(in: 0..<60),
                                r: 1 + .random(in: 0..<2.5), s: .random(in: 0..<6)))
        }
        kick()
    }

    private func stroke(_ f: Finger, _ x: CGFloat, _ y: CGFloat) {
        let gx = x / cell, gy = y / cell
        if !f.has { f.has = true; f.lx = gx; f.ly = gy; stamp(gx, gy, 0, true); return }
        let dx = gx - f.lx, dy = gy - f.ly, dist = hypot(dx, dy)
        if dist < 0.5 { return }
        let ang = atan2(dy, dx)
        if tool == .boot {
            f.acc += dist
            if f.acc > 30 {
                f.acc = 0; stepSide = -stepSide
                stamp(gx - sin(ang) * 6 * stepSide, gy + cos(ang) * 6 * stepSide, ang + .pi / 2, true)
            }
        } else {
            let steps = Int(ceil(dist / 1.2))
            for s in 1...max(1, steps) { stamp(f.lx + dx * CGFloat(s) / CGFloat(steps), f.ly + dy * CGFloat(s) / CGFloat(steps), ang, s == steps) }
        }
        f.lx = gx; f.ly = gy
    }

    private func stamp(_ gx: CGFloat, _ gy: CGFloat, _ ang: CGFloat, _ sound: Bool) {
        guard let f = field else { return }
        let x = Float(gx), y = Float(gy), a = Float(ang)
        var m: Float = 0
        switch tool {
        case .hand: m = f.press(x, y, 6, 6, 0, 1.3)
        case .ball: m = f.press(x, y, 13, 13, 0, 1.7)
        case .boot: m = f.press(x, y, 8, 15, a, 1.5, profile: SnowField.boot); grains(10, 1)
        case .cat:
            m = f.groom(x, y, a); catX = gx; catY = gy; catAng = ang; catT = 1.2; dirty = true
            if sound { catSound(m) }
            return
        case .ski: for s: Float in [-1, 1] { m += f.press(x - sin(a) * 7 * s, y + cos(a) * 7 * s, 2.2, 2.2, 0, 1) }
        }
        dirty = true
        if sound && tool != .boot { grains(min(4, 1 + CGFloat(m) * 0.6), 0.5) }
    }

    /// Grains of filtered noise, different for every kind of snow (the site's grains()), and a buzz on the hard ones.
    private func grains(_ n0: CGFloat, _ vol: Float) {
        let now = CACurrentMediaTime(); if now - lastGrain < 0.028 { return }; lastGrain = now
        let n = Int((n0 + 0.5).rounded(.down))
        var t = 0.0
        for _ in 0..<n {
            switch kind {
            case .powder:
                synth.grain(t, 0.05 + .random(in: 0..<0.05), 0.25 * vol, .low, 900 + .random(in: 0..<900), 0.7)
                synth.grain(t, 0.03, 0.08 * vol, .band, 2600 + .random(in: 0..<1500), 1.2)
            case .crust:
                synth.grain(t, 0.012 + .random(in: 0..<0.015), 0.55 * vol, .high, 2500 + .random(in: 0..<2500), 0.8)
                if Double.random(in: 0..<1) < 0.4 { synth.grain(t, 0.06, 0.3 * vol, .low, 260, 1) }
            case .wet:
                synth.grain(t, 0.04, 0.2 * vol, .low, 600, 1)
                if Double.random(in: 0..<1) < 0.5 { let f0 = 700 + Double.random(in: 0..<500); synth.tone(t, f0, f0 * 1.4, 0.07, 0.05 * vol, saw: true, band: f0 * 1.6, q: 6) }
            }
            t += 0.012 + .random(in: 0..<0.018)
        }
        if kind == .crust { GameHaptics.tick(0.35) } else if kind == .wet { GameHaptics.tick(0.2) }
    }

    private func catSound(_ m: Float) {
        let now = CACurrentMediaTime(); if now - lastGrain < 0.06 { return }; lastGrain = now
        synth.grain(0, 0.09, 0.22, .low, 160 + .random(in: 0..<60), 1.2)
        synth.grain(0.02, 0.06, 0.08 + min(0.15, m * 0.01), .band, 700 + .random(in: 0..<400), 0.8)
    }

    private func drawCat(_ c: CGContext) {
        guard catT > 0 else { return }
        c.saveGState(); c.translateBy(x: catX * cell, y: catY * cell); c.rotate(by: catAng)
        let a = min(1, catT * 2)
        func r(_ col: UInt32, _ al: CGFloat, _ x: CGFloat, _ y: CGFloat, _ w: CGFloat, _ h: CGFloat) {
            c.setFillColor(UIColor(game: col, alpha: al * a).cgColor); c.fill(CGRect(x: x, y: y, width: w, height: h))
        }
        r(0x13233A, 0.18, -46, -34, 78, 72)
        r(0x2A3B55, 1, -44, -36, 62, 14); r(0x2A3B55, 1, -44, 22, 62, 14) // tracks
        r(0xD1342B, 1, -40, -24, 58, 48) // body
        r(0x9FC4E6, 1, 2, -18, 14, 36) // cabin glass
        r(0xF4B942, 1, 20, -74, 8, 148) // the blade in front
        r(0x4B5A6F, 1, -62, -72, 14, 144) // the tiller behind
        c.restoreGState()
    }

    // ---------- the frozen lake: ice in plates, cracks that spread, and plates that give way into the water ----------
    private let g2: CGFloat = 2
    private let sc: CGFloat = 2 // the ice's pixels a point
    private var lw = 0, lh = 0
    private var cid: [Int] = []
    private final class Plate {
        let x: CGFloat, y: CGFloat; var hp: CGFloat
        var broken = false; var nb = Set<Int>()
        var x0 = Int.max, y0 = Int.max, x1 = 0, y1 = 0
        init(_ x: CGFloat, _ y: CGFloat, _ hp: CGFloat) { self.x = x; self.y = y; self.hp = hp }
    }
    private var cells: [Plate] = []
    private var ice: CGContext?
    private final class Shard { let img: CGImage; let x, y, w, h, rot: CGFloat; var t: CGFloat = 0
        init(_ img: CGImage, _ x: CGFloat, _ y: CGFloat, _ w: CGFloat, _ h: CGFloat, _ rot: CGFloat) { self.img = img; self.x = x; self.y = y; self.w = w; self.h = h; self.rot = rot } }
    private var shards: [Shard] = []
    private struct Ripple { let x, y: CGFloat; var r: CGFloat = 4; var a: CGFloat = 0.6 }
    private var ripples: [Ripple] = []
    private struct Chip { var x, y, vx, vy, life: CGFloat; let r: CGFloat; let water: Bool }
    private var chips: [Chip] = []
    private struct Pending { let at: CFTimeInterval; let plate: Int; let depth: Int }
    private var pending: [Pending] = []
    private var broken = 0
    private var chain = 0, chainT: CGFloat = 0
    private var bigT: CGFloat = 0, bigS = ""
    private var refreezeT: CGFloat = 0
    private var lakeLast: Finger?

    /// The plates: a Voronoi pattern over the lake, each with its strength, its neighbours and its box.
    private func initLake() {
        let w = bounds.width, h = bounds.height
        guard w > 0, h > 0 else { return }
        lw = Int(ceil(w / g2)); lh = Int(ceil(h / g2))
        let n = max(60, Int((w * h / 2600).rounded()))
        let cols = max(1, Int((sqrt(CGFloat(n) * w / h)).rounded())), rows = Int(ceil(CGFloat(n) / CGFloat(cols)))
        var sx = [CGFloat](repeating: 0, count: rows * cols), sy = sx
        for r in 0..<rows { for c in 0..<cols {
            let k = r * cols + c
            sx[k] = (CGFloat(c) + 0.2 + .random(in: 0..<0.6)) / CGFloat(cols) * CGFloat(lw)
            sy[k] = (CGFloat(r) + 0.2 + .random(in: 0..<0.6)) / CGFloat(rows) * CGFloat(lh)
        } }
        cells = (0..<(rows * cols)).map { Plate(sx[$0] * g2, sy[$0] * g2, 1.4 + .random(in: 0..<1.3)) }
        cid = [Int](repeating: 0, count: lw * lh)
        for y in 0..<lh { for x in 0..<lw {
            var b = 0, bd = CGFloat.greatestFiniteMagnitude
            let r0 = Int(CGFloat(y) / CGFloat(lh) * CGFloat(rows)), c0 = Int(CGFloat(x) / CGFloat(lw) * CGFloat(cols))
            for r in max(0, r0 - 2)...min(rows - 1, r0 + 2) { for c in max(0, c0 - 2)...min(cols - 1, c0 + 2) {
                let k = r * cols + c, dx = sx[k] - CGFloat(x), dy = sy[k] - CGFloat(y), dd = dx * dx + dy * dy
                if dd < bd { bd = dd; b = k }
            } }
            cid[y * lw + x] = b
            let p = cells[b]
            p.x0 = min(p.x0, x); p.y0 = min(p.y0, y); p.x1 = max(p.x1, x); p.y1 = max(p.y1, y)
        } }
        for y in 0..<(lh - 1) { for x in 0..<(lw - 1) {
            let a = cid[y * lw + x], r = cid[y * lw + x + 1], dn = cid[(y + 1) * lw + x]
            if a != r { cells[a].nb.insert(r); cells[r].nb.insert(a) }
            if a != dn { cells[a].nb.insert(dn); cells[dn].nb.insert(a) }
        } }
        let pw = Int(ceil(w * sc)), ph = Int(ceil(h * sc))
        ice = CGContext(data: nil, width: pw, height: ph, bitsPerComponent: 8, bytesPerRow: pw * 4, space: CGColorSpaceCreateDeviceRGB(),
                        bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)
        // points, from the top left
        ice?.translateBy(x: 0, y: CGFloat(ph)); ice?.scaleBy(x: sc, y: -sc)
        paintIce(w, h)
        broken = 0; shards.removeAll(); ripples.removeAll(); chips.removeAll(); pending.removeAll()
    }

    private func paintIce(_ w: CGFloat, _ h: CGFloat) {
        guard let c = ice else { return }
        let space = CGColorSpaceCreateDeviceRGB()
        let g = CGGradient(colorsSpace: space, colors: [UIColor(game: 0xB9D9EE).cgColor, UIColor(game: 0x8DBBD9).cgColor, UIColor(game: 0xA9CDE6).cgColor] as CFArray, locations: [0, 0.5, 1])!
        c.drawLinearGradient(g, start: .zero, end: CGPoint(x: w, y: h), options: [])
        // dark streaks where the ice froze clear, frost blooms, and trapped air bubbles
        c.saveGState()
        c.setLineCap(.round); c.setLineJoin(.round)
        for _ in 0..<14 {
            let al = 0.08 + CGFloat.random(in: 0..<0.1), lw = 20 + CGFloat.random(in: 0..<60)
            let path = CGMutablePath()
            var x = CGFloat.random(in: 0..<w), y = CGFloat.random(in: 0..<h)
            path.move(to: CGPoint(x: x, y: y))
            for _ in 0..<5 { x += (.random(in: 0..<1) - 0.5) * w * 0.5; y += (.random(in: 0..<1) - 0.5) * h * 0.3; path.addLine(to: CGPoint(x: x, y: y)) }
            // soft edges: the same streak wide and faint, then narrower
            for (k, f) in [(1.6, 0.35), (1.0, 0.6), (0.5, 0.8)] as [(CGFloat, CGFloat)] {
                c.addPath(path); c.setLineWidth(lw * k)
                c.setStrokeColor(UIColor(red: 40 / 255, green: 90 / 255, blue: 130 / 255, alpha: al * f).cgColor); c.strokePath()
            }
        }
        c.restoreGState()
        let bloom = CGGradient(colorsSpace: space, colors: [UIColor(white: 1, alpha: 0.35).cgColor, UIColor(white: 1, alpha: 0).cgColor] as CFArray, locations: [0, 1])!
        for _ in 0..<70 {
            let x = CGFloat.random(in: 0..<w), y = CGFloat.random(in: 0..<h), r = 10 + CGFloat.random(in: 0..<50)
            c.drawRadialGradient(bloom, startCenter: CGPoint(x: x, y: y), startRadius: 0, endCenter: CGPoint(x: x, y: y), endRadius: r, options: [])
        }
        c.setLineWidth(0.6)
        for _ in 0..<260 {
            let x = CGFloat.random(in: 0..<w), y = CGFloat.random(in: 0..<h), r = 0.8 + CGFloat.random(in: 0..<3)
            let rect = CGRect(x: x - r, y: y - r, width: 2 * r, height: 2 * r)
            c.setFillColor(UIColor(white: 1, alpha: 0.55).cgColor); c.fillEllipse(in: rect)
            c.setStrokeColor(UIColor(red: 30 / 255, green: 70 / 255, blue: 100 / 255, alpha: 0.25).cgColor); c.strokeEllipse(in: rect)
        }
        // a dusting of snow at the edges of the lake
        let dust = CGGradient(colorsSpace: space, colors: [UIColor(white: 1, alpha: 0.75).cgColor, UIColor(white: 1, alpha: 0).cgColor,
                                                           UIColor(white: 1, alpha: 0).cgColor, UIColor(white: 1, alpha: 0.7).cgColor] as CFArray, locations: [0, 0.12, 0.85, 1])!
        c.drawLinearGradient(dust, start: .zero, end: CGPoint(x: 0, y: h), options: [])
    }

    private func crackLine(_ x0: CGFloat, _ y0: CGFloat, _ a0: CGFloat, _ len: CGFloat, _ w: CGFloat) {
        guard let c = ice else { return }
        let path = CGMutablePath(), shade = CGMutablePath()
        var x = x0, y = y0, a = a0, l: CGFloat = 0
        path.move(to: CGPoint(x: x, y: y)); shade.move(to: CGPoint(x: x + 0.8, y: y + 0.8))
        while l < len {
            let st = 4 + CGFloat.random(in: 0..<9)
            a += (.random(in: 0..<1) - 0.5) * 0.7; x += cos(a) * st; y += sin(a) * st; l += st
            path.addLine(to: CGPoint(x: x, y: y)); shade.addLine(to: CGPoint(x: x + 0.8, y: y + 0.8))
            if Double.random(in: 0..<1) < 0.08 && len - l > 20 { crackLine(x, y, a + (Bool.random() ? -1 : 1) * (0.5 + .random(in: 0..<0.6)), (len - l) * 0.5, w * 0.7) }
        }
        c.saveGState(); c.setBlendMode(.sourceAtop); c.setLineCap(.round); c.setLineJoin(.round)
        c.addPath(shade); c.setStrokeColor(UIColor(red: 20 / 255, green: 55 / 255, blue: 85 / 255, alpha: 0.45).cgColor); c.setLineWidth(w + 1.2); c.strokePath()
        c.addPath(path); c.setStrokeColor(UIColor(white: 1, alpha: 0.92).cgColor); c.setLineWidth(w); c.strokePath()
        c.restoreGState()
    }

    /// A hit: cracks run out from the point, every plate nearby weakens, and a plate with nothing left gives way.
    private func hit(_ px: CGFloat, _ py: CGFloat, _ force: CGFloat) {
        guard let c = ice else { return }
        let n = Int((4 + force * 5).rounded())
        for i in 0..<n { crackLine(px, py, CGFloat(i) / CGFloat(n) * 2 * .pi + .random(in: 0..<0.5), (30 + .random(in: 0..<90)) * force, 1.1 * force + 0.3) }
        c.saveGState(); c.setBlendMode(.sourceAtop); c.setStrokeColor(UIColor(white: 1, alpha: 0.75).cgColor); c.setLineWidth(1)
        var r: CGFloat = 12
        while r < 40 * force {
            let a0 = CGFloat.random(in: 0..<(2 * .pi))
            c.addArc(center: CGPoint(x: px, y: py), radius: r, startAngle: a0, endAngle: a0 + 0.6 + .random(in: 0..<1.2), clockwise: false); c.strokePath()
            r += 10 + .random(in: 0..<8)
        }
        let glow = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: [UIColor(white: 1, alpha: 0.8).cgColor, UIColor(white: 1, alpha: 0).cgColor] as CFArray, locations: [0, 1])!
        c.drawRadialGradient(glow, startCenter: CGPoint(x: px, y: py), startRadius: 0, endCenter: CGPoint(x: px, y: py), endRadius: max(1, 14 * force), options: [])
        c.restoreGState()
        let reach = 70 * force
        let gi = min(lh - 1, max(0, Int(py / g2))) * lw + min(lw - 1, max(0, Int(px / g2)))
        let home = gi < cid.count ? cid[gi] : -1
        var gave = 0
        let now = CACurrentMediaTime()
        for (k, p) in cells.enumerated() where !p.broken {
            let dd = hypot(p.x - px, p.y - py)
            if dd > reach && k != home { continue }
            p.hp -= force * (k == home ? 1 : max(0, 1 - dd / reach)) * 1.05
            if p.hp <= 0 { gave += 1; pending.append(Pending(at: now + Double(dd * 2.2) / 1000, plate: k, depth: 0)) }
        }
        crackSound(force)
        for _ in 0..<Int(8 * force) { chips.append(Chip(x: px, y: py, vx: (.random(in: 0..<1) - 0.5) * 160, vy: -60 - .random(in: 0..<120), life: 0.5, r: 1 + .random(in: 0..<2), water: false)) }
        if gave > 0 { GameHaptics.thud(0.7) } else { GameHaptics.tick(0.5) }
        kick()
    }

    private func breakPlate(_ k: Int, _ depth: Int) {
        let p = cells[k]
        guard !p.broken, let src = ice, let data = src.data else { return }
        p.broken = true; broken += 1
        let pw = src.width, ph = src.height, row = src.bytesPerRow
        let bx = Int(CGFloat(p.x0) * g2 * sc), by = Int(CGFloat(p.y0) * g2 * sc)
        let bw = min(pw - bx, Int(ceil(CGFloat(p.x1 - p.x0 + 1) * g2 * sc))), bh = min(ph - by, Int(ceil(CGFloat(p.y1 - p.y0 + 1) * g2 * sc)))
        if bw > 0 && bh > 0 {
            // the plate's pixels leave the ice and become a piece that sinks
            let all = data.assumingMemoryBound(to: UInt32.self)
            var mine = [UInt32](repeating: 0, count: bw * bh)
            let space = CGColorSpaceCreateDeviceRGB()
            let per = g2 * sc
            for y in 0..<bh { for x in 0..<bw {
                let gx = min(lw - 1, Int(CGFloat(bx + x) / per)), gy = min(lh - 1, Int(CGFloat(by + y) / per))
                if cid[gy * lw + gx] == k { let i = (by + y) * (row / 4) + bx + x; mine[y * bw + x] = all[i]; all[i] = 0 }
            } }
            let img: CGImage? = still ? nil : mine.withUnsafeMutableBytes { buf in
                guard let sctx = CGContext(data: buf.baseAddress, width: bw, height: bh, bitsPerComponent: 8, bytesPerRow: bw * 4, space: space,
                                           bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return nil }
                // the white broken edge of the plate
                sctx.setBlendMode(.sourceAtop); sctx.setStrokeColor(UIColor(white: 1, alpha: 0.9).cgColor); sctx.setLineWidth(3 * sc)
                sctx.stroke(CGRect(x: 1.5 * sc, y: 1.5 * sc, width: CGFloat(bw) - 3 * sc, height: CGFloat(bh) - 3 * sc))
                return sctx.makeImage()
            }
            if let img { shards.append(Shard(img, CGFloat(bx) / sc, CGFloat(by) / sc, CGFloat(bw) / sc, CGFloat(bh) / sc, (.random(in: 0..<1) - 0.5) * 0.35)) }
        }
        ripples.append(Ripple(x: p.x, y: p.y))
        let w = CGFloat(p.x1 - p.x0 + 1) * g2, h = CGFloat(p.y1 - p.y0 + 1) * g2
        for _ in 0..<10 {
            chips.append(Chip(x: p.x + (.random(in: 0..<1) - 0.5) * w * 0.5, y: p.y + (.random(in: 0..<1) - 0.5) * h * 0.5, vx: (.random(in: 0..<1) - 0.5) * 120,
                              vy: -40 - .random(in: 0..<100), life: 0.6, r: 1.5 + .random(in: 0..<2.5), water: Bool.random()))
        }
        chain = chainT > 0 ? chain + 1 : 1; chainT = 0.5
        if chain > bestChain { bestChain = chain }
        if chain >= 3 { bigS = chainText(chain); bigT = 1 }
        onChain?(chain, chain >= 3)
        breakSound()
        report()
        // weakened neighbours follow: the satisfying collapse
        let now = CACurrentMediaTime()
        for nb in p.nb where !cells[nb].broken && cells[nb].hp < 0.55 { pending.append(Pending(at: now + 0.07 + .random(in: 0..<0.09), plate: nb, depth: depth + 1)) }
    }

    /// The sound of lake ice: sharp crackles, a singing ping that falls, a deep thump, and water when a plate goes.
    private func crackSound(_ force: CGFloat) {
        let f = Float(force)
        for i in 0..<Int(3 + force * 4) { synth.grain(Double(i) * 0.008 + .random(in: 0..<0.01), 0.008 + .random(in: 0..<0.01), 0.5 * f, .high, 3500 + .random(in: 0..<3500), 0.7) }
        synth.grain(0, 0.08, 0.35 * f, .low, 110, 1.4)
        synth.tone(0, 2400 + .random(in: 0..<800), 380, 0.32, 0.12 * f, band: 1200, q: 0.8)
    }

    private func breakSound() {
        let t = 0.01
        for i in 0..<10 { synth.grain(t + Double(i) * 0.018 + .random(in: 0..<0.02), 0.01 + .random(in: 0..<0.015), 0.45, .high, 2500 + .random(in: 0..<4000), 0.6) }
        synth.grain(t + 0.05, 0.35, 0.28, .low, 500 + .random(in: 0..<200), 0.8)
        synth.tone(t + 0.1, 260, 90, 0.24, 0.12)
    }

    /// The lake frozen again.
    func refreeze() {
        initLake(); refreezeT = still ? 0 : 1
        synth.grain(0, 0.6, 0.12, .high, 6000, 0.5)
        report(); kick()
    }

    private func report() {
        let v = lake ? (cells.isEmpty ? 0 : Int((CGFloat(broken) * 100 / CGFloat(cells.count)).rounded())) : (field?.tracks() ?? 0)
        if v != lastStat { lastStat = v; onStat?(v) }
    }

    // ---------- touch ----------
    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        onTouched?()
        for t in touches {
            let p = t.location(in: self)
            if lake {
                if lakeLast == nil && refreezeT <= 0 { let f = Finger(); f.lx = p.x; f.ly = p.y; f.acc = 0; lakeLast = f; hit(p.x, p.y, 1) }
            } else {
                let f = Finger(); fingers[ObjectIdentifier(t)] = f; stroke(f, p.x, p.y)
            }
        }
        kick()
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        if lake {
            guard let l = lakeLast, refreezeT <= 0, let t = touches.first else { return }
            let p = t.location(in: self), dd = hypot(p.x - l.lx, p.y - l.ly)
            l.acc += dd
            // dragging scratches a hairline crack, and every so often presses harder
            if dd > 3, let c = ice {
                c.saveGState(); c.setBlendMode(.sourceAtop); c.setStrokeColor(UIColor(white: 1, alpha: 0.8).cgColor); c.setLineWidth(1)
                c.move(to: CGPoint(x: l.lx, y: l.ly)); c.addLine(to: CGPoint(x: p.x + (.random(in: 0..<1) - 0.5) * 3, y: p.y + (.random(in: 0..<1) - 0.5) * 3))
                c.strokePath(); c.restoreGState()
                synth.grain(0, 0.01, 0.2, .high, 4000 + .random(in: 0..<2000), 0.7)
                kick()
            }
            if l.acc > 45 { l.acc = 0; hit(p.x, p.y, 0.45) }
            l.lx = p.x; l.ly = p.y
        } else {
            for t in touches {
                guard let f = fingers[ObjectIdentifier(t)] else { continue }
                for ct in event?.coalescedTouches(for: t) ?? [t] { let p = ct.location(in: self); stroke(f, p.x, p.y) }
            }
            kick()
        }
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) { end(touches, event) }
    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) { end(touches, event) }
    private func end(_ touches: Set<UITouch>, _ event: UIEvent?) {
        for t in touches { fingers[ObjectIdentifier(t)] = nil }
        if event?.allTouches?.allSatisfy({ $0.phase == .ended || $0.phase == .cancelled }) ?? true { fingers.removeAll(); lakeLast = nil }
    }

    // ---------- drawing ----------
    override func draw(_ rect: CGRect) {
        guard let c = UIGraphicsGetCurrentContext() else { return }
        let now = CACurrentMediaTime()
        let dt = lastFrame == 0 ? 0.016 : CGFloat(min(0.05, now - lastFrame))
        lastFrame = now
        let more = lake ? drawLake(c, dt, now) : drawSnow(c, dt)
        if !more { idle() }
    }

    private func drawSnow(_ c: CGContext, _ dt: CGFloat) -> Bool {
        guard let f = field else { return false }
        if falling > 0 {
            falling -= dt
            f.fill(1 - pow(0.25, Float(dt))); dirty = true
            if falling <= 0 { f.fresh() }
        }
        if catT > 0 { catT -= dt }
        if dirty || snowImage == nil {
            f.render(&px)
            snowImage = px.withUnsafeMutableBytes { buf -> CGImage? in
                // 0xAARRGGBB words, little-endian: BGRA bytes
                let ctx = CGContext(data: buf.baseAddress, width: f.gw, height: f.gh, bitsPerComponent: 8, bytesPerRow: f.gw * 4, space: CGColorSpaceCreateDeviceRGB(),
                                    bitmapInfo: CGImageAlphaInfo.noneSkipFirst.rawValue | CGBitmapInfo.byteOrder32Little.rawValue)
                return ctx?.makeImage()
            }
            dirty = false
        }
        if let img = snowImage {
            c.interpolationQuality = .high
            UIImage(cgImage: img).draw(in: CGRect(x: 0, y: 0, width: CGFloat(f.gw) * cell, height: CGFloat(f.gh) * cell))
        }
        drawCat(c)
        if !flakes.isEmpty {
            c.setFillColor(UIColor(white: 1, alpha: 0.9).cgColor)
            for i in flakes.indices {
                flakes[i].y += flakes[i].v * dt; flakes[i].s += dt; flakes[i].x += sin(flakes[i].s) * 0.5
                let k = flakes[i]; c.fillEllipse(in: CGRect(x: k.x - k.r, y: k.y - k.r, width: 2 * k.r, height: 2 * k.r))
            }
            flakes.removeAll { $0.y > bounds.height + 10 }
        }
        areaT += dt
        if areaT > 0.4 { areaT = 0; report() }
        return falling > 0 || catT > 0 || !flakes.isEmpty || !fingers.isEmpty
    }

    private func drawLake(_ c: CGContext, _ dt: CGFloat, _ now: CFTimeInterval) -> Bool {
        let w = bounds.width, h = bounds.height
        // plates waiting their turn to give way
        if !pending.isEmpty {
            let due = pending.filter { $0.at <= now }
            pending.removeAll { $0.at <= now }
            for d in due { breakPlate(d.plate, d.depth) }
        }
        chainT = max(0, chainT - dt)
        // dark water under the ice, with slow light ripples
        let water = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: [UIColor(game: 0x16405C).cgColor, UIColor(game: 0x0A2236).cgColor] as CFArray, locations: [0, 1])!
        c.drawLinearGradient(water, start: .zero, end: CGPoint(x: 0, y: h), options: [])
        c.setLineWidth(1.5)
        for i in ripples.indices { ripples[i].r += dt * 40; ripples[i].a -= dt * 0.35 }
        ripples.removeAll { $0.a <= 0 }
        for r in ripples {
            c.setStrokeColor(UIColor(red: 160 / 255, green: 210 / 255, blue: 240 / 255, alpha: r.a).cgColor)
            c.strokeEllipse(in: CGRect(x: r.x - r.r, y: r.y - r.r, width: 2 * r.r, height: 2 * r.r))
        }
        if let img = ice?.makeImage() {
            var a: CGFloat = 1
            if refreezeT > 0 { refreezeT -= dt; a = min(1, max(0, 1 - refreezeT)) }
            UIImage(cgImage: img).draw(in: CGRect(x: 0, y: 0, width: w, height: h), blendMode: .normal, alpha: a)
        }
        // plates sinking: they tip, darken and slide under
        for s in shards { s.t += dt }
        shards.removeAll { $0.t >= 1.1 }
        for s in shards {
            let k = min(1, s.t / 1.1)
            c.saveGState()
            c.translateBy(x: s.x + s.w / 2, y: s.y + s.h / 2 + k * 10); c.rotate(by: s.rot * k); c.scaleBy(x: 1 - 0.15 * k, y: 1 - 0.6 * k)
            let r = CGRect(x: -s.w / 2, y: -s.h / 2, width: s.w, height: s.h)
            c.setAlpha(1 - k * 0.85)
            c.beginTransparencyLayer(auxiliaryInfo: nil)
            UIImage(cgImage: s.img).draw(in: r)
            c.setBlendMode(.sourceAtop); c.setFillColor(UIColor(red: 10 / 255, green: 34 / 255, blue: 54 / 255, alpha: k * 0.8).cgColor); c.fill(r)
            c.endTransparencyLayer()
            c.restoreGState()
        }
        for i in chips.indices { chips[i].vy += 400 * dt; chips[i].x += chips[i].vx * dt; chips[i].y += chips[i].vy * dt; chips[i].life -= dt }
        chips.removeAll { $0.life <= 0 }
        for k in chips {
            let a = min(1, k.life * 2)
            c.setFillColor(k.water ? UIColor(red: 160 / 255, green: 210 / 255, blue: 240 / 255, alpha: 0.9 * a).cgColor : UIColor(white: 1, alpha: 0.95 * a).cgColor)
            c.fillEllipse(in: CGRect(x: k.x - k.r, y: k.y - k.r, width: 2 * k.r, height: 2 * k.r))
        }
        if bigT > 0 {
            bigT -= dt
            let a = min(1, max(0, bigT * 2))
            let y = h * 0.3 - (1 - bigT) * 30
            let s = NSAttributedString(string: bigS, attributes: [.font: display, .foregroundColor: UIColor(white: 1, alpha: a),
                                                                   .strokeColor: UIColor(red: 10 / 255, green: 34 / 255, blue: 54 / 255, alpha: a * 0.6), .strokeWidth: -4])
            let sz = s.size()
            s.draw(at: CGPoint(x: (w - sz.width) / 2, y: y - sz.height * 0.8))
        }
        return !pending.isEmpty || !ripples.isEmpty || !shards.isEmpty || !chips.isEmpty || bigT > 0 || refreezeT > 0
    }
}

extension UIColor {
    convenience init(game v: UInt32, alpha: CGFloat = 1) {
        self.init(red: CGFloat((v >> 16) & 255) / 255, green: CGFloat((v >> 8) & 255) / 255, blue: CGFloat(v & 255) / 255, alpha: alpha)
    }
}
