import Foundation

/// The rung names and goals of the snowball fight, resolved into words by the screen.
public enum SnowballWord: Sendable, Equatable {
    case arenaHotelYard, arenaLift, arenaDeepSnow, arenaRidge, arenaSummit
    case goalWinUntouched, goalHat, goalAccuracy, goalTwoHats
    case popMiss, popOverYou, popHatOff, popHead, popHit, popOut, popGotHit, won
}

/// One rung of the ladder (the site's RUNGS, Android's Snowball.kt): more characters behind the far wall, shorter
/// peeks, stronger wind and better aim.
public struct Rung: Sendable {
    public let arena: SnowballWord, n: Int, hp: Int
    public let windA: Float, windB: Float, err: Float, upA: Float, upB: Float, waitA: Float, waitB: Float
    public let goal: SnowballWord
    /// The strongest wind on this rung (the ladder's "wind up to").
    public var windMax: Int { Int(max(abs(windA), abs(windB)).rounded()) }
    /// Whether the third star's goal was met.
    public func ok(_ s: SnowballFight.Stats) -> Bool {
        switch goal {
        case .goalHat: s.hats >= 1
        case .goalAccuracy: s.thrown > 0 && Float(s.hits) / Float(s.thrown) >= 0.5
        case .goalTwoHats: s.hats >= 2
        default: s.taken == 0
        }
    }

    public static let all: [Rung] = [
        Rung(arena: .arenaHotelYard, n: 1, hp: 3, windA: 0, windB: 1, err: 0.9, upA: 1.8, upB: 2.4, waitA: 1.6, waitB: 2.6, goal: .goalWinUntouched),
        Rung(arena: .arenaLift, n: 1, hp: 3, windA: -2, windB: 2, err: 0.6, upA: 1.4, upB: 1.9, waitA: 1.3, waitB: 2.2, goal: .goalHat),
        Rung(arena: .arenaDeepSnow, n: 2, hp: 2, windA: -2, windB: 2, err: 0.55, upA: 1.3, upB: 1.8, waitA: 1.6, waitB: 2.8, goal: .goalAccuracy),
        // the two top rungs aim a little wider (X-5, the site's fix): nobody is a perfect shot
        Rung(arena: .arenaRidge, n: 2, hp: 2, windA: -4, windB: 4, err: 0.5, upA: 1.1, upB: 1.6, waitA: 1.3, waitB: 2.4, goal: .goalTwoHats),
        Rung(arena: .arenaSummit, n: 3, hp: 2, windA: -5, windB: 5, err: 0.45, upA: 1.0, upB: 1.4, waitA: 1.5, waitB: 2.8, goal: .goalWinUntouched),
    ]

    /// Who stands behind the far wall on rung `i` when you wear coat `me` (the site's foesOf).
    public static func foes(_ i: Int, me: Int) -> [Int] {
        let o = (0..<Coats.count).filter { $0 != me }
        return (0..<all[i].n).map { j in o[(i + j * 2) % o.count] }
    }
}

/// What the fight tells the screen (all optional).
public protocol SnowballOut: AnyObject {
    func hp()
    func pop(_ w: SnowballWord)
    func noise(_ d: Double, _ v: Float, _ f: Double, _ type: SnowballFight.Noise)
    func tone(_ f: Double, _ d: Double, _ v: Float, _ e: Double)
    func buzz(_ strong: Bool)
    func finished(_ r: SnowballFight.Result)
}
public extension SnowballOut {
    func hp() {}
    func pop(_ w: SnowballWord) {}
    func noise(_ d: Double, _ v: Float, _ f: Double, _ type: SnowballFight.Noise) {}
    func tone(_ f: Double, _ d: Double, _ v: Float, _ e: Double) {}
    func buzz(_ strong: Bool) {}
    func finished(_ r: SnowballFight.Result) {}
}

/// The snowball fight (13.6), the site's game (design/games/snowball, version 2), as Android's Snowball.kt: seen from
/// behind your own wall, in metres, x to the side, y up and z away; your wall at z 1.4, theirs at 10, the characters
/// at 10.6. A finger down stands you up to aim (a ring shows where the ball lands without wind), letting go throws, and
/// with no finger you are down behind the wall. The screen (`w`, `h`, focal `f`, horizon `y0` in points, `dp` points to
/// the site's CSS pixel) is set by the view.
public final class SnowballFight {
    public enum Noise: Sendable { case low, high, band }
    public final class Stats { public var thrown = 0, hits = 0, hats = 0, taken = 0; public init() {} }
    public struct Result: Sendable { public let won: Bool; public let goals: [Bool]; public let stars: Int; public let hits: Int, hats: Int, thrown: Int, taken: Int }
    public enum State { case play, done, end }

    public static let hide = 0, peek = 1, aimPhase = 2, after = 3, outPhase = 4
    public static let g: Float = 9.81, zw: Float = 1.4, zf: Float = 10, zo: Float = 10.6, wall: Float = 1, fwall: Float = 1.05
    public static let handZ: Float = 0.5, flight: Float = 0.85
    /// The ring sits this far above the finger (CSS pixels), so the finger never hides it.
    public static let off: Float = 70

    public final class Foe {
        public let coat: Int; public var x: Float; public let home: Float; public var hp: Int; public var timer: Float
        public var up: Float = 0, want: Float = 0, phase = SnowballFight.hide, hat = true, hit: Float = 0, wob: Float = 0, throwT: Float = 0, goal = Float.nan
        init(_ c: Int, _ x: Float, _ hp: Int, _ t: Float) { coat = c; self.x = x; home = x; self.hp = hp; timer = t }
    }
    public final class Ball {
        public var x, y, z, vx, vy: Float; public let vz: Float; public let mine: Bool; public var dead = false
        /// The last 8 places, oldest first.
        public private(set) var trail: [(Float, Float, Float)] = []
        public init(_ x: Float, _ y: Float, _ z: Float, _ vx: Float, _ vy: Float, _ vz: Float, _ mine: Bool) { self.x = x; self.y = y; self.z = z; self.vx = vx; self.vy = vy; self.vz = vz; self.mine = mine }
        func push() { if trail.count == 8 { trail.removeFirst() }; trail.append((x, y, z)) }
    }
    public struct Bit { public var x, y, z, vx, vy: Float; public let vz: Float; public var life: Float; public let r: Float }
    public struct Hat { public var x, y, z, vx, vy, vz, r, vr: Float; public let coat: Int }
    /// Snow on your face, on the screen (points): it slides down and fades.
    public struct Splat { public let x, y, r: Float; public var life: Float; public let max, seed: Float }
    public struct Aim { public var x, y: Float }

    public let rungIndex: Int, me: Int
    public let rung: Rung
    private weak var outEar: SnowballOut?
    private let dice: SeededDice
    private let still: Bool

    public private(set) var w: Float = 390, h: Float = 844, dp: Float = 1
    public private(set) var f: Float = 600, y0: Float = 300
    public func place(_ width: Float, _ height: Float, _ density: Float) { w = width; h = height; dp = density; f = min(w * 1.75, h * 1.15); y0 = h * 0.42 }

    public private(set) var state = State.play
    public private(set) var t: Float = 0
    public let stats = Stats()
    public private(set) var foes: [Foe] = []
    public var balls: [Ball] = []
    public private(set) var bits: [Bit] = [], hats: [Hat] = [], splats: [Splat] = []
    /// The far wall's top every 25 cm from x -6 to 6: it wears down where it is hit.
    public private(set) var farWall: [Float] = (0..<49).map { fwall + 0.06 * sin(Float($0) * 1.7) }
    public var wind: Float = 0
    public var aim: Aim?
    public private(set) var reload: Float = 0
    public private(set) var myHp = 3
    public var camH: Float = 0.98
    public private(set) var camX: Float = 0
    public private(set) var shake: Float = 0
    public private(set) var won = false
    private var endT: Float = 0
    private var wonPop: Float = -1

    public init(rung: Int, me: Int, out: SnowballOut?, dice: SeededDice = SeededDice(), still: Bool = false) {
        rungIndex = rung; self.me = me; self.rung = Rung.all[rung]; outEar = out; self.dice = dice; self.still = still
        let xs: [Float] = self.rung.n == 1 ? [0] : self.rung.n == 2 ? [-1.5, 1.5] : [-2, 0, 2]
        foes = Rung.foes(rung, me: me).enumerated().map { i, c in Foe(c, xs[i], self.rung.hp, r(1.2, 2.4) + Float(i) * 0.9) }
        wind = (r(self.rung.windA, self.rung.windB) * 2).rounded() / 2
    }

    private var out: SnowballOut? { outEar }
    private func r(_ a: Float, _ b: Float) -> Float { a + dice.float() * (b - a) }
    public func base(_ o: Foe) -> Float { Self.fwall - 1.95 + o.up * 0.95 } // feet: hidden below the wall top when crouched
    public func farWallTop(_ x: Float) -> Float { farWall[min(max(Int(((x + 6) / 0.25).rounded()), 0), farWall.count - 1)] }

    // ---------- aiming: a finger anywhere, the ring above it; letting go throws when you are up ----------
    public func ringX(_ a: Aim) -> Float { (a.x - w / 2) / (f / Self.zo) }
    public func ringY(_ a: Aim) -> Float { 1.75 - ((a.y - Self.off * dp) - y0) / (f / Self.zo) }
    public func down(_ x: Float, _ y: Float) { if state == .play { aim = Aim(x: x, y: y) } }
    public func move(_ x: Float, _ y: Float) { if aim != nil { aim = Aim(x: x, y: y) } }
    public func cancel() { aim = nil }
    public func up() {
        guard let a = aim else { return }; aim = nil
        guard state == .play else { return }
        if reload > 0 || camH < 1.5 { return } // not up yet: you just duck back down
        throwAt(ringX(a), ringY(a))
    }
    public func throwAt(_ tx: Float, _ ty: Float) {
        let x0: Float = 0.32, yy = camH - 0.3, z0 = Self.handZ, tt = Self.flight, G = Self.g
        balls.append(Ball(x0, yy, z0, (tx - x0) / tt, (ty - yy + 0.5 * G * tt * tt) / tt, (Self.zo - z0) / tt, true))
        reload = 0.45; stats.thrown += 1
        out?.noise(0.1, 0.18, 2600, .high); out?.tone(200, 0.08, 0.12, 1.6)
    }

    // ---------- the characters behind the far wall ----------
    private func foeStep(_ o: Foe, _ dt: Float) {
        o.hit = max(0, o.hit - dt); o.wob *= exp(-6 * dt); o.throwT = max(0, o.throwT - dt)
        if o.hp <= 0 { o.want = 0; o.up += (0 - o.up) * min(1, dt * 10); return }
        o.timer -= dt
        if o.phase == Self.hide && o.timer <= 0 { // up somewhere along the wall (sometimes just a peek)
            if dice.float() < 0.25 { o.phase = Self.peek; o.timer = r(0.35, 0.6); o.want = 0.45 }
            else { o.phase = Self.aimPhase; o.timer = r(rung.upA, rung.upB) * 0.6; o.want = 1 }
        } else if o.phase == Self.peek && o.timer <= 0 { o.phase = Self.hide; o.want = 0; o.timer = r(0.5, 1.1) }
        else if o.phase == Self.aimPhase && o.timer <= 0 { if state == .play { throwFoe(o) }; o.phase = Self.after; o.timer = r(rung.upA, rung.upB) * 0.4; o.throwT = 0.25 }
        else if o.phase == Self.after && o.timer <= 0 { o.phase = Self.hide; o.want = 0; o.timer = r(rung.waitA, rung.waitB); if dice.float() < 0.5 { o.goal = o.home + r(-0.8, 0.8) } }
        if o.phase == Self.hide && !o.goal.isNaN { o.x += (o.goal - o.x) * min(1, dt * 2.5) }
        o.up += (o.want - o.up) * min(1, dt * 9)
    }
    private func throwFoe(_ o: Foe) {
        let x0 = o.x - 0.3, yy: Float = 1.5, z0 = Self.zo - 0.4, tt = r(0.85, 1.1), G = Self.g
        // at your head when standing, with an error that shrinks up the ladder; better players lean into the wind
        // (k < 1 leaves a little error, X-5)
        let tx = r(-1, 1) * rung.err, ty = 1.55 + r(-1, 1) * rung.err * 0.6, tz: Float = 0.3, k = 1 - rung.err * 0.5
        balls.append(Ball(x0, yy, z0, (tx - x0) / tt - 0.25 * wind * k * tt, (ty - yy + 0.5 * G * tt * tt) / tt, (tz - z0) / tt, false))
        out?.noise(0.08, 0.1, 2000, .high)
    }

    // ---------- a step ----------
    public func update(_ dt: Float) {
        if state == .end { return }
        t += dt
        reload = max(0, reload - dt)
        let want: Float = aim != nil && state == .play ? 1.75 : 0.98 // ducked, you still see their heads over your wall
        camH += (want - camH) * min(1, dt * 11)
        camX += ((aim.map { ($0.x - w / 2) / w * 0.6 } ?? 0) - camX) * min(1, dt * 4) // lean a little toward the aim
        for o in foes { foeStep(o, dt) }
        let G = Self.g
        for b in balls where !b.dead {
            let pz = b.z
            b.vx += wind * 0.5 * dt; b.vy -= G * dt; b.x += b.vx * dt; b.y += b.vy * dt; b.z += b.vz * dt; b.push()
            if b.mine {
                if pz < Self.zf && b.z >= Self.zf && b.y < farWallTop(b.x) && b.y > 0 { b.dead = true; wallHit(b.x, b.y); continue }
                if pz < Self.zo && b.z >= Self.zo {
                    for o in foes where o.hp > 0 {
                        let bs = base(o), head = bs + 1.62, wt = farWallTop(o.x)
                        if abs(b.x - o.x) < 0.25 && abs(b.y - head) < 0.25 && head > wt { b.dead = true; hitFoe(o, b, true); break }
                        if abs(b.x - o.x) < 0.33 && b.y < bs + 1.32 && b.y > max(wt, bs + 0.4) { b.dead = true; hitFoe(o, b, false); break }
                    }
                }
                if !b.dead && b.y < 0 { b.dead = true; splatAt(b.x, 0, b.z, 8); out?.noise(0.1, 0.15, 600, .low); if b.z > Self.zo - 2 { out?.pop(.popMiss) } }
                if b.z > Self.zo + 6 { b.dead = true }
            } else {
                if pz > Self.zw && b.z <= Self.zw && b.y < Self.wall { b.dead = true; splatAt(b.x, b.y, Self.zw, 16); out?.noise(0.15, 0.35, 900, .low); shake = still ? 0 : 4; continue }
                if pz > 0.3 && b.z <= 0.3 {
                    b.dead = true
                    let exposed = camH > 1.3, dx = abs(b.x - camX), dy = b.y - camH
                    if exposed && dx < 0.42 && dy > -0.95 && dy < 0.3 { meHit(b) }
                    else if dx < 1.2 { out?.noise(0.2, 0.25, 3000, .band); out?.pop(.popOverYou) }
                }
                if b.y < 0 { b.dead = true; splatAt(b.x, 0, b.z, 8) }
            }
        }
        balls.removeAll { $0.dead }
        for i in bits.indices { bits[i].vy -= G * 0.7 * dt; bits[i].x += bits[i].vx * dt; bits[i].y += bits[i].vy * dt; bits[i].z += bits[i].vz * dt; bits[i].life -= dt }
        bits.removeAll { $0.life <= 0 }
        for i in hats.indices {
            hats[i].vy -= G * dt; hats[i].x += hats[i].vx * dt; hats[i].y += hats[i].vy * dt; hats[i].z += hats[i].vz * dt; hats[i].r += hats[i].vr * dt
            if hats[i].y < 0 { hats[i].y = 0; hats[i].vx = 0; hats[i].vy = 0; hats[i].vz = 0; hats[i].vr = 0 }
        }
        for i in splats.indices { splats[i].life -= dt }
        splats.removeAll { $0.life <= 0 }
        shake = max(0, shake - dt * 30)
        if wonPop >= 0 && t >= wonPop { wonPop = -1; out?.pop(.won) }
        if state == .done { endT += dt; if endT > 1.6 { finish() } }
    }

    private func splatAt(_ x: Float, _ y: Float, _ z: Float, _ n: Int) {
        for _ in 0..<n { let a = r(0, Float.pi), v = r(0.6, 2.6); bits.append(Bit(x: x, y: y + 0.05, z: z, vx: cos(a) * v, vy: sin(a) * v, vz: r(-0.6, 0.6), life: r(0.35, 0.8), r: r(0.04, 0.09))) }
    }
    private func wallHit(_ x: Float, _ y: Float) {
        splatAt(x, y, Self.zf, 12); out?.noise(0.12, 0.22, 800, .low)
        let i = Int(((x + 6) / 0.25).rounded())
        for k in -2...2 { let j = i + k; if farWall.indices.contains(j) { farWall[j] = max(0.55, farWall[j] - 0.09 * (1 - Float(abs(k)) / 3)) } }
    }
    private func hitFoe(_ o: Foe, _ b: Ball, _ head: Bool) {
        guard state == .play else { return } // after the end, nothing changes the result (X-5)
        o.hp -= 1; o.hit = 0.5; o.wob = 1; stats.hits += 1
        splatAt(b.x, b.y, Self.zo, 22); out?.buzz(head); out?.noise(0.2, 0.4, 1200, .low); out?.tone(head ? 520 : 380, 0.16, 0.3, 1.5)
        if head && o.hat {
            o.hat = false; stats.hats += 1
            hats.append(Hat(x: o.x, y: base(o) + 1.8, z: Self.zo, vx: b.vx * 0.15, vy: 3.5, vz: 2, r: 0, vr: r(-9, 9), coat: o.coat))
            out?.pop(.popHatOff)
        } else { out?.pop(head ? .popHead : .popHit) }
        if o.hp <= 0 { out?.pop(.popOut); o.phase = Self.outPhase }
        out?.hp()
        if foes.allSatisfy({ $0.hp <= 0 }) { state = .done; won = true; endT = 0; wonPop = t + 0.7 }
    }
    private func meHit(_ b: Ball) {
        guard state == .play else { return }
        myHp -= 1; stats.taken += 1; shake = still ? 0 : 16
        out?.buzz(true); out?.noise(0.25, 0.5, 1400, .low); out?.tone(140, 0.25, 0.35, 0.6)
        // snow stuck on the screen, sliding down
        for _ in 0..<9 { splats.append(Splat(x: w / 2 + (b.x - camX) * w * 0.5 + r(-w * 0.25, w * 0.25), y: r(h * 0.2, h * 0.65), r: r(28, 70) * dp, life: r(1.6, 2.4), max: 2.4, seed: dice.float() * 9)) }
        out?.pop(.popGotHit); out?.hp()
        if myHp <= 0 { state = .done; won = false; endT = 0 }
    }

    /// The end of the fight: the three stars (to win, without being hit, the rung's goal). Once.
    public func finish() {
        if state == .end { return }
        state = .end; aim = nil
        let g = [won, won && stats.taken == 0, won && rung.ok(stats)]
        out?.finished(Result(won: won, goals: g, stars: g.filter { $0 }.count, hits: stats.hits, hats: stats.hats, thrown: stats.thrown, taken: stats.taken))
    }
    /// For the screenshots: every character out, as if hit.
    public func knockOut() { for o in foes { o.hp = 0 }; out?.hp(); state = .done; won = true; endT = 0; wonPop = t + 0.7 }
}
