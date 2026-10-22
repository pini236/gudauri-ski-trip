import Foundation

/// A run's profile in the descent (the site's PROFILES in site/games/descent, packed by tools/build-ios-data.py as
/// profiles.json): its heights every `step` metres from the top, and the lift at its bottom when there is one.
public struct DescentProfile: Decodable, Sendable {
    public let key: String
    public let color: String
    public let len: Double
    public let top: Double
    public let bot: Double
    public let step: Double
    public let h: [Double]
    public let lift: String

    enum CodingKeys: String, CodingKey { case key, color, len, top, bot, step, h, lift }
    public init(from d: Decoder) throws {
        let c = try d.container(keyedBy: CodingKeys.self)
        key = try c.decode(String.self, forKey: .key); color = try c.decode(String.self, forKey: .color)
        len = try c.decode(Double.self, forKey: .len); top = try c.decode(Double.self, forKey: .top)
        bot = try c.decode(Double.self, forKey: .bot); step = try c.decode(Double.self, forKey: .step)
        h = try c.decode([Double].self, forKey: .h); lift = (try? c.decode(String.self, forKey: .lift)) ?? ""
    }

    /// The five runs, from the packed JSON array.
    public static func parse(_ data: Data) throws -> [DescentProfile] { try JSONDecoder().decode([DescentProfile].self, from: data) }
}

/// The coats of the six characters (the site's FR), the same colours in both games. In the app they have no names
/// (decision 55), only the coat (and in the snowball fight the hat); the name is the colour, for a screen reader.
public enum Coats {
    public static let colors: [UInt32] = [0xD1342B, 0x1F5FC4, 0xF4B942, 0x1B8A4C, 0x7B4FC4, 0xF07A2E]
    public static var count: Int { colors.count }
}

/// The run colours on the signs (the site's RUNCOL).
public func descentRunColor(_ c: String) -> UInt32 {
    switch c { case "green": 0x1B8A4C; case "blue": 0x1F5FC4; case "red": 0xD1342B; default: 0x13233A }
}

/// The site's rng() (mulberry32): the same numbers from the same seed, so a run is laid out as on the site.
public struct SiteRng: Sendable {
    private var seed: Int32
    public init(_ seed: Int32) { self.seed = seed }
    private static func ushr(_ v: Int32, _ n: Int32) -> Int32 { Int32(bitPattern: UInt32(bitPattern: v) >> UInt32(n)) }
    public mutating func next() -> Double {
        seed = seed &+ 0x6D2B_79F5
        var t = (seed ^ Self.ushr(seed, 15)) &* (1 | seed)
        t = (t &+ ((t ^ Self.ushr(t, 7)) &* (61 | t))) ^ t
        return Double(UInt32(bitPattern: t ^ Self.ushr(t, 14))) / 4_294_967_296.0
    }
}

/// Random numbers for what does not need to match the site (the snow's spray, the characters' moods): seeded in tests.
public final class SeededDice: @unchecked Sendable {
    private var s: UInt64
    public init(seed: UInt64 = UInt64.random(in: 1...UInt64.max)) { s = seed }
    /// In [0, 1).
    public func float() -> Float {
        s &+= 0x9E37_79B9_7F4A_7C15
        var z = s
        z = (z ^ (z >> 30)) &* 0xBF58_476D_1CE4_E5B9
        z = (z ^ (z >> 27)) &* 0x94D0_49BB_1331_11EB
        z ^= z >> 31
        return Float(z >> 40) / Float(1 << 24)
    }
}

/// What the descent says, resolved into words by the screen (the app's typed strings).
public enum DescentWord: Sendable, Equatable {
    case popPerfect, popLeftBehind, popOuch, popGullySwallowed, popFellGully, popCleanLanding, popOverSnowcat, popJoined
    case popFlips(n: Int, mult: Int)
    case crashRock, crashSnowcat
    case coachHoldNow, coachReleaseLine, coachAirFlip, coachWind
    case bannerGully, bannerGullyText, bannerWind, bannerWindText, bannerSnowcat, bannerSnowcatText, bannerPowderText, bannerIceText, bannerDog
    case powder, ice
    case liftName(String), finishPop
    case signGully, signSnowcat
    case mission(String)
}

/// The third star of each run (the site's MISSION): whether it was met.
public enum DescentMissions {
    public static let keys = ["Pirveli", "Kikilo 1", "Tatra 2", "Sadzele 3", "Sadzele 1"]
    public static func ok(_ key: String, _ s: DescentGame.Stats) -> Bool {
        switch key {
        case "Pirveli": s.flips >= 3
        case "Kikilo 1": s.coins >= 25
        case "Tatra 2": s.overCat
        case "Sadzele 3": s.falls == 0
        case "Sadzele 1": s.maxFlip >= 2
        default: false
        }
    }
}

/// A run of the descent laid out for the game (the site's buildRun, Android's Course): the real profile from its highest
/// point to its lowest after it, exaggerated 1.5 times, with kickers where a gentler stretch meets a steeper one,
/// gullies on the steeper kickers, rocks, a snowcat, deep powder, ice, wind on the ridge above 2,900 m, khachapuri over
/// every kicker, the five others waiting along the run, and a dog near the bottom. `index` is the run's place among the
/// five (the seed), `me` the coat you wear (the others are the rest). The layout is in doubles, as on the site.
public final class Course: @unchecked Sendable {
    public final class Kicker { public let x: Float; public var gap = false; init(_ x: Float) { self.x = x } }
    public final class Gap { public let x0: Float, x1: Float; public var warned = false; init(_ a: Float, _ b: Float) { x0 = a; x1 = b } }
    public final class Rock { public let x: Float, h: Float; public var hit = false; init(_ x: Float, _ h: Float) { self.x = x; self.h = h } }
    public final class Cat { public var x: Float; public let v: Float; public var over = false, hit = false, warned = false; init(_ x: Float, _ v: Float) { self.x = x; self.v = v } }
    public final class Zone { public let kind: Int, x0: Float, x1: Float; public var warned = false; init(_ k: Int, _ a: Float, _ b: Float) { kind = k; x0 = a; x1 = b } }
    public final class Coin { public let x: Float, dy: Float; public var got = false; init(_ x: Float, _ dy: Float) { self.x = x; self.dy = dy } }
    public final class Spot { public let coat: Int, x: Float; public var got = false, lost = false, joinT: Float = 0; init(_ c: Int, _ x: Float) { coat = c; self.x = x } }
    public struct Sign { public let x: Float; public let text: DescentWord }

    public static let exag: Double = 1.5
    public static let powder = 0, ice = 1, wind = 2

    public let key: String, color: String, lift: String
    /// The real heights every 5 m, top to bottom.
    public let h: [Float]
    public let len: Float, top: Int, bot: Int
    public private(set) var kickers: [Kicker] = [], gaps: [Gap] = [], rocks: [Rock] = [], cats: [Cat] = []
    public private(set) var zones: [Zone] = [], coins: [Coin] = [], spots: [Spot] = [], signs: [Sign] = []
    public let dogX: Float
    private var terrain: [Float] = []

    public init(_ p: DescentProfile, index: Int, me: Int) {
        // ride from the highest point of the line to its lowest point after it
        var i0 = 0; for i in p.h.indices where p.h[i] > p.h[i0] { i0 = i }
        var i1 = i0; for i in i0..<p.h.count where p.h[i] < p.h[i1] { i1 = i }
        let hh = Array(p.h[i0...i1]); h = hh.map { Float($0) }
        let n = hh.count; let rl = Double(n - 1) * 5.0
        len = Float(rl); top = Int((hh[0] + 0.5).rounded(.down)); bot = Int((hh[n - 1] + 0.5).rounded(.down))
        key = p.key; color = p.color; lift = p.lift; dogX = Float(rl * 0.72)
        var rnd = SiteRng(Int32(index * 7919 + 17)); func r() -> Double { rnd.next() }
        let hard = p.color == "green" ? 0 : p.color == "blue" ? 1 : p.color == "red" ? 2 : 3
        let base = (0..<n).map { (hh[$0] - hh[0]) * Course.exag }
        func steep(_ i: Int) -> Double { (base[max(0, i - 4)] - base[min(n - 1, i + 4)]) / 40 }
        // kickers: where a gentler stretch meets a steeper one (a natural lip), or every ~170 m
        var ks: [Kicker] = []; var lastK = -999
        if n - 30 > 24 {
            for i in 24..<(n - 30) {
                let s1 = (base[i - 6] - base[i]) / 30, s2 = (base[i] - base[i + 6]) / 30
                if (s2 - s1 > 0.08 && i * 5 - lastK > 90) || i * 5 - lastK > 170 { ks.append(Kicker(Float(i * 5))); lastK = i * 5 }
            }
        }
        // gullies to jump: on the steeper kickers in the middle of the run (none on the green); a stable sort, as Kotlin's
        let want = [0, 1, 2, 2][hard]
        let cand = ks.enumerated().filter { Double($0.element.x) > rl * 0.25 && Double($0.element.x) < rl * 0.85 }
            .map { (i: $0.offset, k: $0.element, s: steep(Int($0.element.x / 5) - 6)) }
            .sorted { $0.s != $1.s ? $0.s > $1.s : $0.i < $1.i }
        var gs: [Gap] = []
        for c in cand {
            if gs.count >= want { break }
            if gs.contains(where: { abs($0.x0 - c.k.x) < 250 }) { continue }
            c.k.gap = true; gs.append(Gap(c.k.x + 0.5, c.k.x + 7.5))
        }
        kickers = ks; gaps = gs
        terrain = (0..<n).map { i in
            let x = Double(i) * 5.0; var y = base[i] + sin(x * 0.09) * 0.35 + sin(x * 0.031 + 1.3) * 0.6
            for k in ks { let d = x - Double(k.x); let hg = k.gap ? 3.0 : 2.2; if d > -15 && d <= 0 { y += hg * pow((d + 15) / 15, 2) } }
            return Float(y)
        }
        func busy(_ x: Double, _ rr: Double) -> Bool {
            x < 60 || x > rl - 60 || ks.contains { x > Double($0.x) - rr - 15 && x < Double($0.x) + rr + 22 }
                || gs.contains { x > Double($0.x0) - rr - 15 && x < Double($0.x1) + rr + 15 }
        }
        // rocks to hop over
        var rockX: [Double] = []
        do {
            var x = 110 + r() * 80
            while x < rl - 80 {
                if !busy(x, 10) { rocks.append(Rock(Float(x), Float(0.7 + r() * 0.35))); rockX.append(x) }
                x += Double([260, 190, 150, 120][hard]) * (0.7 + r() * 0.6)
            }
        }
        // one snowcat grooming its way up the run, from about the middle
        do { var x = rl * (0.42 + r() * 0.12); var k = 0; while k < 40 && busy(x, 20) { x += 9; k += 1 }; cats.append(Cat(Float(x), 1.6)) }
        // deep powder slows you, ice is fast, wind on the ridge (only above 2,900 m real) pushes you back
        var zoneX: [(Double, Double)] = []
        do {
            var zx = 150 + r() * 100
            while zx < rl - 120 {
                let kind = r() < 0.5 ? Course.powder : Course.ice
                let w = kind == Course.powder ? 22 + r() * 10 : 30 + r() * 15
                if !busy(zx, w) && !rockX.contains(where: { $0 > zx - 8 && $0 < zx + w + 8 }) { zones.append(Zone(kind, Float(zx), Float(zx + w))); zoneX.append((zx, zx + w)) }
                zx += 230 + r() * 200
            }
            var nw = 0; var x = 60.0
            while x < rl - 60 && nw < 3 {
                if realH(Float(x)) > 2900 && !busy(x, 45) { zones.append(Zone(Course.wind, Float(x), Float(x + 45))); zoneX.append((x, x + 45)); nw += 1; x += 240 }
                x += 5
            }
        }
        // khachapuri: an arc over every kicker (the reward for jumping) and short lines on the snow
        for k in ks {
            let span: Float = k.gap ? 20 : 16
            for j in 0..<5 { let f = (Float(j) + 0.5) / 5; coins.append(Coin(k.x + 3 + span * f, 1.2 + 4.2 * sin(Float.pi * f))) }
        }
        do {
            var x = 90.0
            while x < rl - 60 {
                if !(busy(x, 14) || rockX.contains(where: { abs($0 - x) < 18 })) { for j in 0..<4 { coins.append(Coin(Float(x + Double(j) * 2.6), 0.9)) } }
                x += 110 + r() * 70
            }
        }
        // the five others wait along the run, not on top of anything else
        let others = (0..<Coats.count).filter { $0 != me }
        spots = others.enumerated().map { k, c in
            var x = rl * (0.1 + 0.16 * Double(k)) + 12; var j = 0
            while j < 30 && (busy(x, 6) || rockX.contains(where: { abs($0 - x) < 12 }) || zoneX.contains(where: { x > $0.0 - 6 && x < $0.1 + 6 })) { x += 7; j += 1 }
            return Spot(c, Float(x))
        }
        // signs a little ahead of each surprise
        for g in gs { signs.append(Sign(x: g.x0 - 45, text: .signGully)) }
        for z in zones where z.kind != Course.wind { signs.append(Sign(x: z.x0 - 30, text: z.kind == Course.powder ? .powder : .ice)) }
        signs.append(Sign(x: cats[0].x - 60, text: .signSnowcat))
    }

    public func realH(_ x: Float) -> Float {
        let i = min(max(x / 5, 0), Float(h.count) - 1.001); let i0 = Int(i)
        return h[i0] + (h[i0 + 1] - h[i0]) * (i - Float(i0))
    }
    public func inGap(_ x: Float) -> Gap? { gaps.first { x > $0.x0 && x < $0.x1 } }
    public func baseGround(_ x: Float) -> Float {
        let i = min(max(x / 5, 0), Float(terrain.count) - 2); let i0 = min(Int(i.rounded(.down)), terrain.count - 2); let f = i - Float(i0)
        return terrain[i0] * (1 - f) + terrain[i0 + 1] * f
    }
    public func ground(_ x: Float) -> Float { baseGround(x) - (inGap(x) != nil ? 7 : 0) }
    /// The snow's angle, negative going down (a gully's walls don't count).
    public func slope(_ x: Float) -> Float { atan2(baseGround(x + 1) - baseGround(x - 1), 2) }
    public func zoneAt(_ x: Float) -> Zone? { zones.first { x >= $0.x0 && x <= $0.x1 } }
}
