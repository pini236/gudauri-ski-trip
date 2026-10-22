import Foundation

/// What the descent tells the screen: words to show, sounds, taps, the end (all optional).
public protocol DescentOut: AnyObject {
    func pop(_ w: DescentWord)
    func banner(_ big: DescentWord?, _ text: DescentWord, dog: Bool)
    func coach(_ id: String, _ w: DescentWord, force: Bool)
    /// A tone of `f` Hz for `d` seconds at `v`, a sine, triangle or square (`wave`), sliding to `f * end`.
    func tone(_ f: Double, _ d: Double, _ v: Float, wave: Int, end: Double)
    func buzz(_ kind: Int)
    func combo(_ n: Int)
    func finished(_ ok: Bool)
}
public extension DescentOut {
    func pop(_ w: DescentWord) {}
    func banner(_ big: DescentWord?, _ text: DescentWord, dog: Bool) {}
    func coach(_ id: String, _ w: DescentWord, force: Bool) {}
    func tone(_ f: Double, _ d: Double, _ v: Float, wave: Int, end: Double) {}
    func buzz(_ kind: Int) {}
    func combo(_ n: Int) {}
    func finished(_ ok: Bool) {}
}

/// The crew's descent (13.6), the site's game (site/games/descent, version 5), as Android's DescentGame.kt: one button,
/// as a skier jumps (hold to crouch and load, let go to pop away from the snow; in the air hold to flip), on a run's
/// real profile, picking up the others on the way, over rocks, gullies and the snowcat, ahead of the avalanche, against
/// the ghost of the best run. Units are metres and seconds, y up. The others have no names (decision 55), only coats.
public final class DescentGame {
    public final class Stats { public var flips = 0, coins = 0, falls = 0, maxFlip = 0, pops = 0; public var overCat = false; public init() {} }
    /// The best run's positions every tenth of a second: t, x, y, angle.
    public struct Ghost: Sendable {
        public let t: Float, who: Int, r: [Float]
        public init(t: Float, who: Int, r: [Float]) { self.t = t; self.who = who; self.r = r }
        public func save() -> String { "\(t);\(who);" + r.map { "\($0)" }.joined(separator: ",") }
        public static func load(_ s: String?) -> Ghost? {
            guard let s else { return nil }
            let p = s.split(separator: ";", omittingEmptySubsequences: false)
            guard p.count == 3, let t = Float(p[0]), let who = Int(p[1]) else { return nil }
            if p[2].isEmpty { return Ghost(t: t, who: who, r: []) }
            let r = p[2].split(separator: ",").compactMap { Float($0) }
            return Ghost(t: t, who: who, r: r)
        }
    }
    public enum State { case count, run, fin, end }
    public struct Bit { public var x, y, vx, vy, life: Float; public let r: Float; public let col: UInt32; public let conf: Bool }
    public struct Pos { public let x, y, ang: Float; public let air: Bool; public let crouch: Float }
    public struct Rider { public let coat: Int; public let from: Float }

    public static let g: Float = 9.81, vmax: Float = 30, spacing: Float = 4.2
    public static let sine = 0, tri = 1, square = 2
    public static let tick = 0, click = 1, crash = 2
    public static let white: UInt32 = 0xFFFFFF, gold: UInt32 = 0xF4B942

    public let c: Course
    public let me: Int
    public let avalancheOn: Bool
    public let ghost: Ghost?
    private weak var out: DescentOut?
    private let dice: SeededDice
    private let still: Bool

    public private(set) var state = State.count
    public private(set) var t: Float = 0
    public private(set) var countT: Float = 3
    public let stats = Stats()
    public private(set) var score = 0
    public private(set) var combo = 1
    public private(set) var flips = 0
    public private(set) var crew: [Rider] = []
    public private(set) var bits: [Bit] = []
    public private(set) var tracks: [[Float]] = [[3, 3]]
    private var hist: [Pos] = []
    private var rec: [Float] = []
    public private(set) var shake: Float = 0
    public private(set) var slow: Float = 1
    public var zoomKick: Float = 0
    public private(set) var avaX: Float = -110
    private var avaV: Float = 8
    public private(set) var dogOn = false
    private var dogDone = false
    public private(set) var dogT: Float = 0
    public private(set) var windT: Float = 0
    private var finT: Float = 0

    // you
    public var x: Float = 3, y: Float = 0, s: Float = 0, vx: Float = 0, vy: Float = 0, air = false, ang: Float = 0
    public var airRot: Float = 0, airT: Float = 0, crashT: Float = 0, crouch: Float = 0, inv: Float = 0, squash: Float = 0
    public var loading = false, loadT: Float = 0, flipArm = false, holding = false, tucking = false

    public init(_ c: Course, me: Int, out: DescentOut?, avalancheOn: Bool = true, ghost: Ghost? = nil, dice: SeededDice = SeededDice(), still: Bool = false) {
        self.c = c; self.me = me; self.out = out; self.avalancheOn = avalancheOn; self.ghost = ghost; self.dice = dice; self.still = still
        y = c.ground(3); ang = c.slope(3)
    }

    public var coinsTotal: Int { c.coins.count }
    public var got: Int { crew.count }
    private func rf() -> Float { dice.float() }

    // ---------- the one button ----------
    public func press() {
        guard state == .run else { return }
        holding = true
        if air { flipArm = true; return } // a fresh press in the air starts a flip
        loading = crashT <= 0; loadT = 0
    }
    /// Letting go is the jump: the longer the crouch (up to a third of a second), the higher, and highest right at the lip.
    public func release() {
        let was = holding; holding = false; flipArm = false
        guard was, loading else { return }
        loading = false
        guard state == .run, !air, crashT <= 0 else { return }
        let lip = c.kickers.first { $0.x - x > -0.6 && $0.x - x < 3.2 }; let k = min(1, loadT / 0.32)
        var pop: Float = 5.4 + 3.8 * k
        if lip != nil { pop *= 1.35; out?.pop(.popPerfect); stats.pops += 1; score += 40 * combo; out?.tone(520, 0.12, 0.3, wave: Self.tri, end: 1.6) }
        let a = c.slope(x)
        takeOff(s * cos(a) + pop * -sin(a) * 0.55, s * sin(a) + pop * cos(a)); squash = 1
        if !still { zoomKick = 0.06 + 0.08 * k }
        let n = lip != nil ? 28 : 10 + Int((10 * k).rounded())
        for _ in 0..<n {
            let d: Float = rf() < 0.5 ? -1 : 1
            bits.append(Bit(x: x + d * 0.4, y: y + 0.1, vx: d * (2 + rf() * 4) + vx * 0.3, vy: 1.5 + rf() * 3.5, life: 0.45 + rf() * 0.4, r: 0.14 + rf() * 0.2, col: Self.white, conf: false))
        }
        out?.tone(lip != nil ? 330 : 200 + 60 * Double(k), 0.09, 0.22, wave: Self.square, end: 1.9); out?.buzz(Self.tick)
    }
    private func takeOff(_ tvx: Float, _ tvy: Float) {
        air = true; vx = tvx; vy = tvy; airRot = 0; airT = 0; loading = false; flipArm = false
        if !tracks.isEmpty { tracks[tracks.count - 1][1] = x }
    }

    private func spray(_ px: Float, _ py: Float, _ n: Int, _ spd: Float, _ col: UInt32 = white, up: Float = 3) {
        for _ in 0..<n { bits.append(Bit(x: px, y: py, vx: -spd * (0.3 + rf() * 0.6), vy: 1 + rf() * up, life: 0.5 + rf() * 0.5, r: 0.12 + rf() * 0.2, col: col, conf: false)) }
    }
    private func burst(_ px: Float, _ py: Float, _ n: Int) {
        for i in 0..<n {
            let a = rf() * Float.pi; let v = 4 + rf() * 7
            bits.append(Bit(x: px, y: py, vx: cos(a) * v, vy: sin(a) * v + 2, life: 1 + rf() * 0.8, r: 0.18, col: Coats.colors[i % Coats.count], conf: true))
        }
    }
    private func loseFriend() -> Bool {
        guard let one = crew.popLast() else { return false }
        c.spots.first { $0.coat == one.coat }?.lost = true; return true
    }
    private func setCombo(_ n: Int) { combo = min(6, n); out?.combo(combo) }

    private func crashNow(_ msg: DescentWord?) {
        crashT = 1; inv = 2; air = false; y = c.ground(x); s *= 0.4; shake = still ? 0 : 14
        out?.buzz(Self.crash); out?.tone(70, 0.35, 0.7, wave: Self.sine, end: 0.5); spray(x, y + 0.5, 40, 6)
        stats.falls += 1; setCombo(1)
        out?.pop(loseFriend() ? .popLeftBehind : msg ?? .popOuch)
    }
    private func fellIn(_ gx0: Float, _ gx1: Float) {
        if !tracks.isEmpty { tracks[tracks.count - 1][1] = min(tracks[tracks.count - 1][1], gx0) }
        x = gx1 + 1.5; y = c.ground(x); air = false; s = 4; crashT = 1.1; inv = 2.2; ang = c.slope(x); shake = still ? 0 : 18
        out?.buzz(Self.crash); out?.tone(55, 0.5, 0.8, wave: Self.sine, end: 0.5)
        spray(x, y + 0.3, 50, 5, Self.white, up: 5); stats.falls += 1; setCombo(1); tracks.append([x, x])
        out?.pop(loseFriend() ? .popGullySwallowed : .popFellGully)
    }
    private func land() {
        let a = c.slope(x); let wall = c.ground(x) - y
        let g = c.inGap(x)
        if g != nil || wall > 1.4 {
            let q = g ?? c.gaps.first { x >= $0.x1 - 0.5 && x < $0.x1 + 4 }
            if let q { fellIn(q.x0, q.x1) } else { fellIn(x - 5, x) }
            return
        }
        let rel = atan2(sin(ang - a), cos(ang - a))
        let n = Int(((abs(airRot) + 0.9) / (2 * Float.pi)).rounded(.down))
        air = false; y = c.ground(x); tracks.append([x, x])
        if abs(rel) > 0.75 { crashNow(nil); return }
        let along = vx * cos(a) + vy * sin(a)
        s = min(Self.vmax, max(4, along)); ang = a; squash = -min(1, airT * 0.9); if !still && airT > 0.8 { shake = max(shake, 3 + airT * 2) }
        let clean = abs(rel) < 0.14
        if n > 0 {
            flips += n; stats.flips += n; stats.maxFlip = max(stats.maxFlip, n)
            let mult = (1 + crew.count) * combo; score += n * 100 * mult; s = min(Self.vmax + 3, s + 3 * Float(n)); slow = still ? 1 : 0.35; out?.buzz(Self.click)
            out?.pop(.popFlips(n: n, mult: mult))
            out?.tone(140, 0.2, 0.5, wave: Self.sine, end: 0.5); spray(x, y, 26, s * 0.6); setCombo(combo + 1)
        } else {
            spray(x, y, 12, s * 0.4); out?.tone(100, 0.12, 0.25, wave: Self.sine, end: 0.5)
            if airT > 0.5 { score += 20 * combo; if clean { s += 1.5; score += 15; out?.pop(.popCleanLanding); setCombo(combo + 1) } }
        }
        if holding { loading = true; loadT = 0; flipArm = false }
    }

    // ---------- a step ----------
    public func step(_ dtIn: Float) {
        var dt = min(0.05, dtIn)
        if state == .count {
            let before = countT.rounded(.up); countT -= dt
            if countT <= -0.4 { state = .run; s = 7; out?.tone(660, 0.25, 0.3, wave: Self.tri, end: 1) }
            else if countT.rounded(.up) != before { out?.tone(440, 0.12, 0.25, wave: Self.tri, end: 1) }
        }
        if state == .run || state == .fin { dt *= slow; slow = min(1, slow + dt * 1.8); update(dt) }
    }

    private func update(_ dt: Float) {
        t += dt
        tucking = state == .run && holding && (air ? flipArm : crashT <= 0); if loading { loadT += dt }
        let a = c.slope(x); let z = c.zoneAt(x)
        inv = max(0, inv - dt)
        let G = Self.g
        if state == .fin { // glide to a stop at the station and celebrate
            finT += dt; s = max(0, s - 9 * dt); x += s * cos(a) * dt
            if air { vy -= G * dt; y += vy * dt; x += vx * dt * 0.3; if y <= c.ground(x) { air = false } }
            if !air { y = c.ground(x) }; ang += (a - ang) * min(1, dt * 10)
            if rf() < dt * 6 { burst(x + 2 + rf() * 6, c.ground(x) + 6, 8) }
            if finT > 1.6 { finish(true); return }
        } else if crashT > 0 {
            crashT -= dt; s = max(0, s - 6 * dt); x += s * cos(a) * dt; y = c.ground(x); ang += dt * 9; if crashT <= 0 { ang = a; s = max(s, 4) }
        } else if !air {
            crouch += ((tucking ? 1 : 0) - crouch) * min(1, dt * 8)
            var drag: Float = tucking ? 0.0022 : 0.0034; var mu: Float = 0.035; var push: Float = 0
            if z?.kind == Course.powder { drag *= 4.5; mu = 0.09 }
            if z?.kind == Course.ice { drag *= 0.8; mu = 0.008 }
            if z?.kind == Course.wind { push = tucking ? 0.8 : 4.2 }
            s += (-G * sin(a) - drag * s * s - mu * G * cos(a) - push) * dt
            s = min(Self.vmax, max(s, 3.2)) // skating on the flats; a racer's top speed
            let nx = x + s * cos(a) * dt; let ty = y + s * sin(a) * dt
            // the snow bends away faster than gravity can hold you: airborne off the lip
            let bend = (a - c.slope(x + 2.5)) / 2.5
            if s > 7 && bend * s * s > G * cos(a) * 1.05 { takeOff(s * cos(a), s * sin(a)); x = nx; y = ty }
            else {
                x = nx; y = c.ground(nx); if !tracks.isEmpty { tracks[tracks.count - 1][1] = x }
                if let gp = c.inGap(x) { fellIn(gp.x0, gp.x1) }
            }
            ang += (a - ang) * min(1, dt * 14)
            let sp = z?.kind == Course.powder ? 4 : 1
            if rf() < s * dt * 2.5 * Float(sp) { spray(x - 0.6, y + 0.1, sp + (s > 14 ? 1 : 0), s * 0.5, Self.white, up: sp > 1 ? 5 : 3) }
        } else {
            airT += dt
            let v = hypot(vx, vy); let d = 1 - 0.0034 * v * dt; vx *= d; vy *= d // the same air drag as on the snow
            if z?.kind == Course.wind { vx = max(2, vx - 3 * dt) }
            let gk: Float = abs(vy) < 2.2 ? 0.7 : vy < 0 ? 1.12 : 1 // a floaty top and a firmer fall
            vy -= G * gk * dt; x += vx * dt; y += vy * dt
            let spin: Float = tucking ? 7.6 : 0; ang += spin * dt; airRot += spin * dt
            if !tucking { // let go and the skier settles toward the snow below (and finishes a nearly complete flip)
                let tgt = c.slope(x); let rel = atan2(sin(ang - tgt), cos(ang - tgt))
                let tau = 2 * Float.pi; let m = airRot.truncatingRemainder(dividingBy: tau)
                let fwd: Float = airRot > 0.5 && rel > 0 ? min(tau - (m == 0 ? tau : m), 9) : 0
                let sg: Float = rel > 0 ? 1 : rel < 0 ? -1 : 0
                let st = fwd > 0 && fwd < 1.6 ? min(fwd, 4 * dt) : -sg * min(abs(rel), 2.6 * dt)
                ang += st; if st > 0 { airRot += st }
            }
            crouch += ((tucking ? 1 : 0) - crouch) * min(1, dt * 10)
            if y <= c.ground(x) { land() }
        }
        if squash != 0 { squash *= pow(Float(0.0008), dt); if abs(squash) < 0.02 { squash = 0 } }
        let hgt = y - c.ground(x)
        if state == .run {
            // rocks and the snowcat: jump them or fall
            for r in c.rocks where !r.hit && abs(x - r.x) < 0.8 && hgt < r.h && inv <= 0 && crashT <= 0 { r.hit = true; crashNow(.crashRock) }
            for k in c.cats {
                if abs(x - k.x) > 3 { k.x -= k.v * dt * (c.inGap(k.x - 2.5) != nil ? 0 : 1) }
                if !k.warned && k.x - x < 70 && k.x > x { k.warned = true; out?.banner(.bannerSnowcat, .bannerSnowcatText, dog: false) }
                if abs(x - k.x) < 2 {
                    if hgt < 2.1 && !k.over && inv <= 0 && crashT <= 0 && !k.hit { k.hit = true; crashNow(.crashSnowcat) }
                    else if hgt >= 2.1 && !k.over {
                        k.over = true; stats.overCat = true; score += 300 * combo; out?.pop(.popOverSnowcat)
                        out?.tone(520, 0.2, 0.35, wave: Self.tri, end: 1.5); setCombo(combo + 1)
                    }
                }
            }
            if !air && crashT <= 0 && c.kickers.contains(where: { $0.x - x > 4 && $0.x - x < 14 }) { out?.coach("load", .coachHoldNow, force: false) }
            if !air && crashT <= 0 && holding && c.kickers.contains(where: { $0.x - x > 0 && $0.x - x < 3.2 }) { out?.coach("jump", .coachReleaseLine, force: false) }
            if air && airT > 0.25 && !holding { out?.coach("flip", .coachAirFlip, force: false) }
            for g in c.gaps where !g.warned && g.x0 - x < 60 && g.x0 > x { g.warned = true; out?.banner(.bannerGully, .bannerGullyText, dog: false) }
            for zz in c.zones where !zz.warned && zz.x0 - x < 35 && zz.x1 > x {
                zz.warned = true
                switch zz.kind {
                case Course.wind: out?.banner(.bannerWind, .bannerWindText, dog: false); out?.coach("wind", .coachWind, force: true)
                case Course.powder: out?.banner(.powder, .bannerPowderText, dog: false)
                default: out?.banner(.ice, .bannerIceText, dog: false)
                }
            }
            // khachapuri
            for k in c.coins where !k.got {
                let cy = c.baseGround(k.x) + k.dy
                if abs(x - k.x) < 1.3 && abs(y + 0.9 - cy) < 1.5 {
                    k.got = true; stats.coins += 1; score += 10 * combo; out?.tone(990 + Double(stats.coins % 5 * 80), 0.07, 0.12, wave: Self.tri, end: 1.2)
                    bits.append(Bit(x: k.x, y: cy, vx: 0, vy: 3, life: 0.5, r: 0.25, col: Self.gold, conf: false))
                }
            }
            // the others: ski through one to pick them up
            for sp in c.spots where !sp.got && !sp.lost {
                if abs(x - sp.x) < 4 && hgt < 12 && crashT <= 0 {
                    sp.got = true; sp.joinT = 0.7; crew.append(Rider(coat: sp.coat, from: sp.x)); score += 150
                    out?.pop(.popJoined); setCombo(combo + 1); out?.buzz(Self.click); out?.tone(330, 0.1, 0.2, wave: Self.sine, end: 0.5)
                }
            }
            // a Gudauri dog joins for a while near the bottom
            if !dogOn && !dogDone && x > c.dogX { dogOn = true; dogT = 0; out?.banner(nil, .bannerDog, dog: true) }
            if x >= c.len - 8 {
                state = .fin; finT = 0; holding = false; loading = false; flipArm = false; tucking = false
                out?.pop(c.lift.isEmpty ? .finishPop : .liftName(c.lift))
                out?.tone(520, 0.3, 0.4, wave: Self.tri, end: 2); burst(x + 3, y + 3, 60)
            }
        }
        if dogOn { dogT += dt; if dogT > 14 || state == .fin { dogOn = false; dogDone = true } }
        // the crew ride in your exact tracks, a fixed distance apart
        if let lastH = hist.last { if x - lastH.x > 0.15 || air != lastH.air { hist.append(Pos(x: x, y: y, ang: ang, air: air, crouch: crouch)) } }
        else { hist.append(Pos(x: x, y: y, ang: ang, air: air, crouch: crouch)) }
        if hist.count > 4000 { hist.removeFirst(1000) }
        for sp in c.spots { sp.joinT = max(0, sp.joinT - dt) }
        if rec.isEmpty || t - rec[rec.count - 4] >= 0.1 { rec += [r2(t), r1(x), r1(y), r2(ang)] }
        // the avalanche: fast on steep snow, slow on the flats; it never falls hopelessly far behind
        if avalancheOn && state == .run {
            let asl = max(0, -sin(c.slope(max(0, avaX))))
            var tv = min(24, 5 + t * 0.05 + asl * 55); if x - avaX > 140 { tv = max(tv, s + 3) }
            avaV += (tv - avaV) * min(1, dt * 0.8); if t > 2 { avaX += avaV * dt }
            if avaX >= x - 1 { finish(false); return }
        }
        for i in bits.indices {
            bits[i].vy -= G * (bits[i].conf ? 0.25 : 0.35) * dt; bits[i].x += bits[i].vx * dt; bits[i].y += bits[i].vy * dt; bits[i].life -= dt
        }
        bits.removeAll { $0.life <= 0 }
        if bits.count > 500 { bits.removeFirst(bits.count - 500) }
        shake = max(0, shake - dt * 40); windT += dt
        zoomKick = max(0, zoomKick - 0.006)
    }
    private func r1(_ v: Float) -> Float { (v * 10).rounded() / 10 }
    private func r2(_ v: Float) -> Float { (v * 100).rounded() / 100 }

    /// The rider's own path at distance `px` along the run (where the crew ride).
    public func histAt(_ px: Float) -> Pos {
        guard let h0 = hist.first, px > h0.x else { return Pos(x: px, y: c.ground(px), ang: c.slope(px), air: false, crouch: 0) }
        var lo = 0, hi = hist.count - 1
        while hi - lo > 1 { let m = (lo + hi) / 2; if hist[m].x < px { lo = m } else { hi = m } }
        return hist[lo]
    }
    /// Where the ghost is at `tt`: index into its record (t, x, y, angle by fours), or -1.
    public func ghostAt(_ tt: Float) -> Int {
        guard let g = ghost, !g.r.isEmpty else { return -1 }
        var i = 0; while i + 4 < g.r.count && g.r[i + 4] < tt { i += 4 }
        return i
    }

    // ---------- the end ----------
    public private(set) var ok = false
    public private(set) var goals = [false, false, false]
    public private(set) var stars = 0
    /// The run as a ghost for next time (when it is a new best).
    public func record() -> Ghost { Ghost(t: t, who: me, r: rec) }
    public func finish(_ reached: Bool) {
        if state == .end { return }
        state = .end; holding = false; loading = false; flipArm = false; tucking = false; ok = reached
        score += reached ? got * 200 + max(0, Int((600 - t * 3).rounded())) : 0
        goals = [reached, reached && got == 5, reached && DescentMissions.ok(c.key, stats)]; stars = goals.filter { $0 }.count
        out?.finished(reached)
    }
    /// For the screenshots: straight to the bottom of the run, as if skied.
    public func toBottom() { if state != .run { state = .run; s = 7 }; x = c.len - 9; y = c.ground(x); air = false; crashT = 0 }
}
