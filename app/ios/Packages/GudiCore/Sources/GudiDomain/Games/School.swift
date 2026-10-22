import Foundation

/// Words from a game to the screen: a key of i18n/strings.json and its named arguments (a `Say` among them is said too).
public struct Say: Equatable, Sendable {
    public indirect enum Arg: Equatable, Sendable {
        case text(String)
        case say(Say)
    }
    public let key: String
    public let args: [String: Arg]
    public init(_ key: String, _ args: [String: Arg] = [:]) { self.key = key; self.args = args }

    /// The words, in the app's language now.
    public func text(_ s: Strings = .shared) -> String {
        var a: [String: String] = [:]
        for (k, v) in args {
            switch v {
            case .text(let t): a[k] = t
            case .say(let inner): a[k] = inner.text(s)
            }
        }
        return s.text(key, a)
    }
}

/// The ski school's seven lessons, each with its own control.
public enum SchoolMode: Sendable { case wedge, skis, steer, rhythm, lean }

/// The little drawing on a lesson's card (the site's D).
public enum SchoolFig: Sendable { case wedge, outer, shape, rhythm, look, carve, final }

/// One lesson (the site's LS): its trail colour, control and drill, the slope in degrees, how long it is and how fast you
/// start (m, m/s). Gates are x, y (m down the slope) and width. Its words are game.school.lesson<n>_*.
public struct Lesson: Sendable {
    public let n: Int
    public let mode: SchoolMode
    public let color: UInt32
    public let slope: Float
    public let len: Float
    public let start: Float
    public let fig: SchoolFig
    public var gates: [(x: Float, y: Float, w: Float)] = []
    public var demo: Float = 0
    public var traps: [Float]? = nil
    public var trapLimit = 0
    public var beat: Float = 0
    public var traffic = false
    public var fade = false
    public var wedgeBtn = false
    public var limit = 0

    public var title: String { "game.school.lesson\(n)_title" }
    public var rule: String { "game.school.lesson\(n)_rule" }
    public var ctrl: String { "game.school.lesson\(n)_controls" }
    public var drill: String { "game.school.lesson\(n)_drill" }
    public var goals: [String] { (1...3).map { "game.school.lesson\(n)_goal\($0)" } }
    public var tip: String { "game.school.tip_\(n)" }
}

private let green: UInt32 = 0x1B8A4C, blue: UInt32 = 0x1F5FC4, red: UInt32 = 0xD1342B

/// The site's seven lessons (site/games/school; Android's game/School.kt), in its order.
public let LESSONS: [Lesson] = {
    var l: [Lesson] = []
    l.append(Lesson(n: 1, mode: .wedge, color: green, slope: 9, len: 125, start: 4, fig: .wedge))
    var l2 = Lesson(n: 2, mode: .skis, color: green, slope: 10, len: 175, start: 3, fig: .outer)
    l2.gates = [(-5, 28, 6), (5, 52, 6), (-5, 76, 6), (5, 100, 6), (-5, 124, 6), (5, 148, 6)]
    l.append(l2)
    var l3 = Lesson(n: 3, mode: .steer, color: blue, slope: 15, len: 285, start: 3, fig: .shape)
    l3.demo = 3.5; l3.traps = [70, 130, 190, 250]; l3.trapLimit = 22
    l.append(l3)
    var l4 = Lesson(n: 4, mode: .rhythm, color: blue, slope: 14, len: 240, start: 5, fig: .rhythm)
    l4.beat = 1.15
    l.append(l4)
    var l5 = Lesson(n: 5, mode: .steer, color: red, slope: 15, len: 260, start: 4, fig: .look)
    l5.gates = [(0, 35, 6), (-6, 70, 6), (5, 105, 6), (-4, 140, 6), (6, 175, 6), (-5, 210, 6), (3, 245, 6)]
    l5.traffic = true; l5.fade = true
    l.append(l5)
    l.append(Lesson(n: 6, mode: .lean, color: red, slope: 16, len: 285, start: 6, fig: .carve))
    var l7 = Lesson(n: 7, mode: .steer, color: blue, slope: 16, len: 340, start: 4, fig: .final)
    l7.gates = [(-5, 40, 7), (5, 85, 7), (-6, 130, 7), (4, 175, 7), (-5, 220, 7), (5, 265, 7), (0, 310, 8)]
    l7.wedgeBtn = true; l7.traffic = true; l7.limit = 40
    l.append(l7)
    return l
}()

/// What the screen hears from a lesson.
public protocol SchoolOut: AnyObject {
    func coach(_ say: Say, tone: Int)
    func pop(_ say: Say)
    func checks(_ items: [SchoolRun.Check])
    func ding(_ f: Double, _ d: Double, _ v: Float, square: Bool)
    func buzz(_ strength: Float)
    func finished(_ r: SchoolRun.Result)
}

public extension SchoolOut {
    func coach(_ say: Say, tone: Int) {}
    func pop(_ say: Say) {}
    func checks(_ items: [SchoolRun.Check]) {}
    func ding(_ f: Double, _ d: Double, _ v: Float, square: Bool) {}
    func buzz(_ strength: Float) {}
    func finished(_ r: SchoolRun.Result) {}
}

private func rint(_ f: Float) -> Int { Int((f + 0.5).rounded(.down)) }
private func fixed1(_ f: Float) -> String { String(format: "%.1f", Double(f)) }

/// One lesson on the slope (the site's ski school, 13.6; Android's SchoolRun): the skier's physics, the control of the
/// lesson and the drill the coach watches. x runs across the slope (right positive) and y down it, in metres; the
/// screen sees the slope from behind the skier, forward up, `w` by `h` points with `px` points a metre. The words and
/// the sounds go to `out`.
public final class SchoolRun: @unchecked Sendable {
    /// A line of the drill's checklist: open, done (ok) or missed (no).
    public struct Check: Equatable, Sendable { public let say: Say; public let mark: Int }
    /// The end: which of the three goals were met, the stars, and the line under them.
    public final class Result: @unchecked Sendable {
        public let goals: [Bool]; public let stars: Int; public let line: Say
        init(_ g: [Bool], _ s: Int, _ l: Say) { goals = g; stars = s; line = l }
    }

    public final class Skier { public var x: Float = 0, y: Float = -2, h: Float = 0, v: Float = 0, wedge: Float = 0, skid: Float = 0, edge: Float = 0, crashT: Float = 0, jerk: Float = 0 }
    public final class Gate { public let x: Float, y: Float, w: Float; public var done: Bool?; init(_ x: Float, _ y: Float, _ w: Float) { self.x = x; self.y = y; self.w = w } }
    public final class Person {
        public var x: Float, y: Float; public let v: Float; public var ph: Float; public let amp: Float; public let color: UInt32; public var hit = false
        init(_ x: Float, _ y: Float, _ v: Float, _ ph: Float, _ amp: Float, _ color: UInt32) { self.x = x; self.y = y; self.v = v; self.ph = ph; self.amp = amp; self.color = color }
    }
    public struct Track: Sendable { public let x: Float, y: Float, h: Float, sk: Float, wd: Float }
    public final class Finger { public var x: Float, y: Float; public init(_ x: Float, _ y: Float) { self.x = x; self.y = y } }
    private final class Arc { let side: Float, h0: Float; var dur: Float = 0; var clean = true; init(_ s: Float, _ h: Float) { side = s; h0 = h } }

    public static let g: Float = 9.81
    public static let kmhPerMs: Float = 3.6
    /// The wedge lesson's three stop lines, and the green band between the second and the third (m down the slope).
    public static let stopLines: [Float] = [35, 72, 112]
    public static let band: [Float] = [80, 102]
    public static let plain = 0, good = 1, bad = 2
    public static let open = 0, ok = 1, no = 2

    public let lv: Lesson
    private let out: SchoolOut
    public var w: Float = 1080, h: Float = 2200, px: Float = 54
    public func sx(_ x: Float) -> Float { w / 2 + (x - camX) * px }
    public func sy(_ y: Float) -> Float { h * 0.7 - (y - camY) * px }

    public let s = Skier()
    public let gates: [Gate]
    public let people: [Person]
    public private(set) var tracks: [Track] = []
    public var finger: Finger?
    public private(set) var t: Float = 0
    public private(set) var camY: Float
    public private(set) var camX: Float = 0
    public private(set) var running = true
    public private(set) var result: Result?

    // the controls: the wedge slider, the two skis and the wedge button held, the rhythm's turn
    public private(set) var wedgeCtl: Float
    public private(set) var skiL = false
    public private(set) var skiR = false
    public var wedgeBtn = false
    private var turnDir: Float = 1
    private var target: Float = 0

    // the drill's tally (the site's st)
    public private(set) var time: Float = 0
    public private(set) var maxV: Float = 0
    public private(set) var passed = 0
    public private(set) var crashes = 0
    private var under: Float = 0
    public private(set) var stops: [Float?] = []
    private var stopIdx = 0
    private var bandIn: Float = 0
    private var bandT: Float = 0
    public private(set) var wrong = 0
    public private(set) var traps: [Bool] = []
    private var trapIdx = 0
    public private(set) var taps = 0
    public private(set) var goodN = 0
    public private(set) var perfect = 0
    public private(set) var streak = 0
    public private(set) var best = 0
    private var beatN = 0
    public private(set) var pulse: Float = 0
    public private(set) var arcs = 0
    public private(set) var skidT: Float = 0
    private var arc: Arc?
    private let beat0: Float = 0.6
    private var lastTap: Float = 0
    private var finishIn: Float = 0

    /// The speed now, km/h.
    public var kmh: Float { s.v * SchoolRun.kmhPerMs }
    /// Over the final test's limit.
    public var over: Bool { lv.limit > 0 && kmh > Float(lv.limit) }

    private var coachSaid: (Say, Int)?
    private func coach(_ say: Say, _ tone: Int = SchoolRun.plain) {
        if let c = coachSaid, c.0 == say, c.1 == tone { return }
        coachSaid = (say, tone); out.coach(say, tone: tone)
    }
    private func say(_ k: String, _ a: [String: Say.Arg] = [:]) -> Say { Say("game.school." + k, a) }
    private func n(_ i: Int) -> Say.Arg { .text(String(i)) }

    public init(_ lv: Lesson, out: SchoolOut) {
        self.lv = lv; self.out = out
        s.v = lv.start
        camY = s.y
        wedgeCtl = lv.mode == .wedge ? 0.15 : 0
        gates = lv.gates.map { Gate($0.x, $0.y, $0.w) }
        let colors: [UInt32] = [0x1B8A4C, 0x7B4FC4, 0xF4B942, 0x1F5FC4]
        var ps: [Person] = []
        if lv.traffic {
            for k in 0..<6 {
                let sideX: Float = k % 2 == 1 ? 1 : -1
                let x: Float = sideX * (2 + Float((k * 37) % 6))
                let y: Float = 45 + Float(k) * (lv.len - 70) / 6
                let v: Float = 2 + Float(k % 3) * 0.8
                ps.append(Person(x, y, v, Float(k) * 1.3, 2.5 + Float(k % 3), colors[k % 4]))
            }
        }
        people = ps
        coach(say(lv.mode == .steer && lv.demo > 0 ? "coach_watch_demo" : "coach_start"))
        checks()
    }

    // ---------- input ----------
    /// A ski button held or let go (lesson 2): the coach checks the side before each gate.
    public func ski(right: Bool, down: Bool) {
        if right { skiR = down } else { skiL = down }
        guard down, running, lv.mode == .skis, let g = nextGate() else { return }
        let turnsLeft = right
        let need = g.x < s.x - 1 ? -1 : g.x > s.x + 1 ? 1 : 0
        if need != 0 && (need < 0) != turnsLeft {
            wrong += 1
            coach(say(turnsLeft ? "coach_wrong_left" : "coach_wrong_right"), SchoolRun.bad)
            out.buzz(0.7); out.ding(200, 0.2, 0.15, square: true); checks()
        } else if need != 0 { coach(say(turnsLeft ? "coach_correct_left" : "coach_correct_right"), SchoolRun.good) }
    }

    /// Lesson 4: every tap starts a turn the other way, graded against the beat.
    public func tap() {
        guard running, lv.mode == .rhythm else { return }
        taps += 1; lastTap = t
        let b = lv.beat
        let ph = ((t - beat0).truncatingRemainder(dividingBy: b) + b).truncatingRemainder(dividingBy: b)
        let off = min(ph, b - ph) * (ph < b / 2 ? 1 : -1)
        turnDir = -turnDir; target = turnDir * 0.95
        let a = abs(off)
        if a < 0.12 { perfect += 1; goodN += 1; streak += 1; coach(say("beat_perfect"), SchoolRun.good); out.ding(880, 0.12, 0.15, square: false) }
        else if a < 0.25 { goodN += 1; streak += 1; coach(say("beat_good"), SchoolRun.good); out.ding(660, 0.1, 0.12, square: false) }
        else { streak = 0; coach(say(off > 0 ? "beat_late" : "beat_early"), SchoolRun.bad); out.ding(220, 0.12, 0.1, square: true) }
        best = max(best, streak); checks()
    }

    private func nextGate() -> Gate? { gates.first { $0.done == nil } }

    // ---------- the slope ----------
    public func update(_ dt: Float) {
        t += dt
        let a = lv.slope * .pi / 180
        var want: Float? = s.h
        var rateMax: Float = 2.4, wedge: Float = 0
        switch lv.mode {
        case .wedge:
            want = 0
            if let f = finger { let top = h * 0.3, bot = h * 0.82; wedgeCtl = min(1, max(0, (f.y - top) / (bot - top))) }
            wedge = wedgeCtl
        case .skis:
            wedge = 0.25; rateMax = 1.3
            want = skiR && !skiL ? -0.75 : skiL && !skiR ? 0.75 : s.h * 0.6
        case .steer:
            if lv.demo > 0 && t < lv.demo { want = 0 }
            else if let f = finger { let dx = f.x - sx(s.x), fwd = sy(s.y) - f.y; want = atan2(dx, max(-px * 1.5, fwd)) }
            wedge = lv.wedgeBtn && wedgeBtn ? 1 : 0
        case .rhythm:
            want = target; rateMax = 1.6
        case .lean:
            let e: Float = finger.map { min(1, max(-1, ($0.x - w / 2) / (w * 0.32))) } ?? s.edge * 0.98
            // how fast the skis really change edge (not the finger): smoothed, so touches that come in bursts do not
            // count as snaps (X-5, the site's fix)
            let e0 = s.edge
            s.edge += min(3 * dt, max(-3 * dt, e - s.edge))
            s.jerk += (abs(s.edge - e0) / dt - s.jerk) * min(1, dt * 10)
            want = nil
        }
        want = want.map { min(1.9, max(-1.9, $0)) }
        s.wedge += (wedge - s.wedge) * min(1, dt * 8)
        var rate: Float
        if lv.mode == .lean {
            // the ski's shape turns you: more edge, a tighter turn; snapping from edge to edge breaks the grip
            rate = s.v * s.edge * 0.11
            let sk = max(0, s.jerk - 2.2) / 0.8 // a full edge change in under about 0.9 s is a snap
            s.skid += (min(1, sk) - s.skid) * min(1, dt * 8)
            if s.h > 1.9 && rate > 0 || s.h < -1.9 && rate < 0 { rate = 0 }
        } else {
            let d = want! - s.h
            rate = min(rateMax, max(-rateMax, d * 3.2))
            let carveRate = s.v / 9 + 0.25
            let skid: Float = lv.mode == .rhythm ? 0 : max(0, abs(rate) - carveRate) // the rhythm lesson is about timing, not edging
            s.skid += (min(1, skid / 1.2) - s.skid) * min(1, dt * 10)
        }
        s.h += rate * dt
        let G = SchoolRun.g
        let pull: Float = G * sin(a) * cos(s.h) - 0.05 * G * cos(a)
        let drag: Float = 0.0035 * s.v * s.v
        let brake: Float = s.wedge * (2.6 + s.v * 0.35)
        let scrub: Float = s.skid * s.v * 0.9 + abs(rate) * s.v * 0.06
        var acc: Float = pull - drag - brake - scrub
        if s.crashT > 0 { s.crashT -= dt; acc = -6 }
        // in the turning and edging lessons you never stall: a push with the poles, so a skier who turned up the hill can
        // turn back (X-5: the edging lesson stalled)
        s.v = max((lv.mode == .skis || lv.mode == .lean) && running ? 1.2 : 0, s.v + acc * dt)
        if !running { s.v = max(0, s.v - 6 * dt) }
        s.x += sin(s.h) * s.v * dt; s.y += cos(s.h) * s.v * dt; s.x = min(14, max(-14, s.x))
        camY += (s.y - camY) * min(1, dt * 6); camX += (s.x * 0.55 - camX) * min(1, dt * 3)
        if s.v > 0.3 && (tracks.last.map { hypotf($0.x - s.x, $0.y - s.y) > 0.25 } ?? true) {
            tracks.append(Track(x: s.x, y: s.y, h: s.h, sk: s.skid, wd: s.wedge))
        }
        if tracks.count > 2500 { tracks.removeFirst(500) }
        if !running { return }
        time += dt
        let kmh = kmh
        // the top speed counts once you steer: not in the demo, nor the moment after it (X-5)
        if !(lv.demo > 0 && t < lv.demo + 1.5) { maxV = max(maxV, kmh) }
        if lv.limit > 0 && kmh <= Float(lv.limit) { under += dt }
        drills(dt, kmh)
        for g in gates where g.done == nil && s.y >= g.y {
            let ok = abs(s.x - g.x) <= g.w / 2; g.done = ok
            if ok { passed += 1; out.ding(660 + Double(passed) * 30, 0.3, 0.18, square: false); out.pop(say("pop_gate")) }
            else { out.pop(say("pop_gate_missed")); out.buzz(0.5) }
            checks()
        }
        for p in people {
            p.ph += dt * 0.6; p.x += cos(p.ph) * p.amp * dt * 0.8; p.y += p.v * dt
            if !p.hit && hypotf(p.x - s.x, p.y - s.y) < 1.3 && s.crashT <= 0 {
                p.hit = true; crashes += 1; s.crashT = 0.8; s.v *= 0.3
                out.pop(say("pop_crash")); coach(say("coach_priority"), SchoolRun.bad); out.buzz(1); checks()
            } else if !p.hit && p.y > s.y && p.y - s.y < 9 && abs(p.x - s.x) < 3 { coach(say("coach_skier_ahead")) }
        }
        if finishIn > 0 { finishIn -= dt; if finishIn <= 0 { finish() } }
        if running && s.y >= lv.len { finish() }
    }

    // ---------- what each drill watches and says ----------
    private func drills(_ dt: Float, _ kmh: Float) {
        switch lv.mode {
        case .wedge:
            let line: Float? = stopIdx < SchoolRun.stopLines.count ? SchoolRun.stopLines[stopIdx] : nil
            if let line, finishIn <= 0 {
                let d = line - s.y
                if s.v < 0.12 && d >= 0 && d < 6 && t > 1 {
                    stops.append(d); stopIdx += 1
                    out.pop(say(d < 1.5 ? "pop_stop_perfect" : "pop_stopped")); out.ding(d < 1.5 ? 880 : 660, 0.3, 0.18, square: false)
                    coach(say("coach_narrow_continue"), SchoolRun.good); checks()
                    if stopIdx >= SchoolRun.stopLines.count { finishIn = 0.9 }
                } else if s.y > line {
                    stops.append(nil); stopIdx += 1
                    out.pop(say("pop_line_passed")); out.buzz(0.7); checks()
                    if stopIdx >= SchoolRun.stopLines.count { finishIn = 0.9 }
                } else if d < 14 && s.v > 2 { coach(say("coach_line_close")) }
            }
            if s.y > SchoolRun.band[0] && s.y < SchoolRun.band[1] {
                bandT += dt
                if kmh >= 6 && kmh <= 10 { bandIn += dt; coach(say("coach_in_band", ["kmh": n(rint(kmh))]), SchoolRun.good) }
                else { coach(say(kmh > 10 ? "coach_too_fast" : "coach_too_slow"), SchoolRun.bad) }
            } else if !(line != nil && line! - s.y < 14 && s.v > 2) {
                let c = wedgeCtl
                coach(say(c < 0.2 ? "coach_skis_straight" : c < 0.6 ? "coach_narrow_pizza" : "coach_wide_pizza"), c >= 0.6 ? SchoolRun.good : SchoolRun.plain)
            }
        case .skis:
            if let g = nextGate(), !skiL, !skiR { coach(say(g.x < s.x ? "coach_next_gate_left" : "coach_next_gate_right")) }
        case .steer:
            guard let trapsAt = lv.traps else { return }
            if lv.demo > 0 && t < lv.demo { coach(say("coach_demo_speed", ["kmh": n(rint(kmh))])); return }
            if lv.demo > 0 && t < lv.demo + 0.1 && t - dt < lv.demo { out.pop(say("pop_your_turn")) }
            if trapIdx < trapsAt.count, s.y >= trapsAt[trapIdx] {
                let ok = kmh <= Float(lv.trapLimit); traps.append(ok); trapIdx += 1
                out.pop(ok ? say("pop_trap_ok") : say("pop_trap_caught", ["kmh": n(rint(kmh))]))
                if ok { out.ding(880, 0.3, 0.18, square: false) } else { out.buzz(0.9) }
                checks()
            }
            let ah = abs(s.h)
            coach(say(ah < 0.35 ? "coach_straight" : ah < 1 ? "coach_diagonal" : "coach_across"),
                  ah < 0.35 ? SchoolRun.bad : ah > 1 ? SchoolRun.good : SchoolRun.plain)
        case .rhythm:
            let b = lv.beat
            let nb = Int(floor((t - beat0) / b))
            if nb > beatN {
                beatN = nb; out.ding(1200, 0.05, 0.12, square: true); pulse = 1
                if t - lastTap > b * 1.8 && taps > 0 { streak = 0; coach(say("coach_missed_beat"), SchoolRun.bad) }
            }
            pulse = max(0, pulse - dt * 3)
            if taps == 0 { coach(say("coach_listen")) }
        case .lean:
            if s.skid > 0.25 { skidT += dt; coach(say("coach_skid"), SchoolRun.bad) }
            else if abs(s.edge) > 0.3 { coach(say("coach_on_edge"), SchoolRun.good) }
            else { coach(say("coach_flat")) }
            // a clean arc: from one side to the other, long enough, without skidding
            let side: Float = abs(s.edge) > 0.3 ? (s.edge > 0 ? 1 : -1) : 0
            if side != 0 {
                if arc == nil || arc!.side != side {
                    if let c = arc, c.clean, c.dur > 0.8, abs(s.h - c.h0) > 0.6 {
                        arcs += 1; out.pop(say("pop_clean_arc", ["n": n(arcs)])); out.ding(700 + Double(arcs) * 40, 0.3, 0.18, square: false); checks()
                    }
                    arc = Arc(side, s.h)
                }
                arc!.dur += dt; if s.skid > 0.25 { arc!.clean = false }
            }
        }
    }

    /// The drill's checklist.
    public func checks() {
        let O = SchoolRun.open, K = SchoolRun.ok, N = SchoolRun.no
        var items: [Check]
        if lv.mode == .wedge {
            items = SchoolRun.stopLines.indices.map { i in
                Check(say: say("check_stop_line", ["n": n(i + 1)]), mark: i >= stops.count ? O : stops[i] == nil ? N : K)
            }
            let bs = bandT > 0 ? say("check_band_pct", ["pct": n(rint(bandIn / max(0.01, bandT) * 100))]) : say("check_band")
            items.insert(Check(say: bs, mark: bandT > 0 && s.y > SchoolRun.band[1] ? (bandIn / bandT >= 0.7 ? K : N) : O), at: 2)
        } else if lv.mode == .skis {
            let all = gates.allSatisfy { $0.done != nil }
            items = [Check(say: say("check_gates", ["n": n(passed), "total": n(gates.count)]), mark: !all ? O : passed == gates.count ? K : N),
                     Check(say: say("check_wrong", ["n": n(wrong)]), mark: wrong > 1 ? N : O)]
        } else if let tr = lv.traps {
            items = tr.indices.map { i in Check(say: say("check_trap", ["n": n(i + 1)]), mark: i >= traps.count ? O : traps[i] ? K : N) }
        } else if lv.mode == .rhythm {
            items = [Check(say: say("check_on_beat", ["n": n(goodN)]), mark: goodN >= 10 ? K : O),
                     Check(say: say("check_perfect", ["n": n(perfect)]), mark: perfect >= 8 ? K : O),
                     Check(say: say("check_streak", ["n": n(streak), "best": n(best)]), mark: best >= 8 ? K : O)]
        } else if lv.mode == .lean {
            items = [Check(say: say("check_arcs", ["n": n(arcs)]), mark: arcs >= 8 ? K : O),
                     Check(say: say("check_skid", ["sec": .text(fixed1(skidT))]), mark: skidT >= 1.5 ? N : O)]
        } else {
            items = [Check(say: say("check_gates", ["n": n(passed), "total": n(gates.count)]), mark: O),
                     Check(say: say("check_crashes", ["n": n(crashes)]), mark: crashes > 0 ? N : O)]
        }
        out.checks(items)
    }

    /// The lesson is over: the three goals, the stars and the line under them.
    public func finish() {
        guard running else { return }
        running = false
        let g: [Bool], line: Say
        if lv.mode == .wedge {
            let ok = stops.compactMap { $0 }
            let avg: Float = ok.isEmpty ? 99 : ok.reduce(0, +) / Float(ok.count)
            let bnd: Float = bandT > 0 ? bandIn / bandT : 0
            g = [ok.count == 3, bnd >= 0.7, ok.count == 3 && avg <= 1.5]
            line = say("res_wedge", ["stops": n(ok.count), "avg": avg < 90 ? .say(say("res_meters", ["m": .text(fixed1(avg))])) : .text("-"), "pct": n(rint(bnd * 100))])
        } else if lv.mode == .skis {
            g = [passed >= 4, passed == 6, passed == 6 && wrong <= 1]
            line = say("res_skis", ["n": n(passed), "wrong": n(wrong)])
        } else if lv.traps != nil {
            let c = traps.filter { $0 }.count
            g = [c >= 3, c == 4, c == 4 && maxV <= 35]
            line = say("res_traps", ["n": n(c), "max": n(rint(maxV))])
        } else if lv.mode == .rhythm {
            g = [goodN >= 10, perfect >= 8, best >= 8]
            line = say("res_rhythm", ["good": n(goodN), "perfect": n(perfect), "best": n(best)])
        } else if lv.mode == .lean {
            g = [arcs >= 4, arcs >= 8, arcs >= 8 && skidT < 1.5]
            line = say("res_lean", ["arcs": n(arcs), "sec": .text(fixed1(skidT))])
        } else if lv.fade {
            g = [passed >= 5, passed >= 6 && crashes == 0, passed == 7 && crashes == 0]
            line = say("res_look", ["n": n(passed), "crashes": n(crashes)])
        } else {
            let u: Float = time > 0 ? under / time : 0, all = passed == gates.count && crashes == 0
            g = [passed >= 5, all, all && u >= 0.9]
            line = say("res_final", ["n": n(passed), "total": n(gates.count), "crashes": n(crashes), "pct": n(rint(u * 100))])
        }
        let r = Result(g, g.filter { $0 }.count, line)
        result = r
        out.finished(r)
    }
}
