import SwiftUI
import UIKit
import AVFoundation
import QuartzCore
import GudiDomain

// What the descent and the snowball fight share on the phone: the frame clock, the made-up sounds, the taps, the
// records, the sound key, and the menus' pieces (the site's games pages: .menu, h2, .how, .go). Kept here, in this
// part's own folder, until the games' common pieces (Games/Common) exist; then these can move or give way to them.

/// The games' own look: a bright winter day, the same in both themes (the site's game pages).
enum Arcade {
    static let ink = Color(rgb: 0x13233A)
    static let muted = Color(rgb: 0x4B5A6F)
    static let rule = Color(rgb: 0xCBD5DF)
    static let accent = Color(rgb: 0xF4B942)
    static let warn = Color(rgb: 0xF08A3C)

    /// The settings' switches (default on), as Android's FxPrefs.
    static var sound: Bool { UserDefaults.standard.object(forKey: "sound") as? Bool ?? true }
    static var haptics: Bool { UserDefaults.standard.object(forKey: "haptics") as? Bool ?? true }
    /// The phone's "reduce motion": the camera does not shake, nothing pulses.
    @MainActor static var still: Bool { UIAccessibility.isReduceMotionEnabled }

    /// A coat's colour, for a screen reader (no names in the games, decision 55).
    static func coatName(_ i: Int) -> String {
        switch i { case 0: S.commonColorRed; case 1: S.commonColorBlue; case 2: S.appColorYellow; case 3: S.commonColorGreen; case 4: S.appColorPurple; default: S.appColorOrange }
    }
    static func coat(_ i: Int) -> Color { Color(rgb: Coats.colors[min(max(i, 0), Coats.count - 1)]) }
}

/// The taps of the games, under the settings' switch (Android's Haptics: tick, click, thud).
@MainActor
enum ArcadeHaptics {
    static func tick(_ s: CGFloat = 0.5) { if Arcade.haptics { Fx.shared.tick(s) } }
    static func click(_ s: CGFloat = 0.6) { if Arcade.haptics { Fx.shared.click(s) } }
    static func thud(_ s: CGFloat = 0.9) { if Arcade.haptics { UIImpactFeedbackGenerator(style: .heavy).impactOccurred(intensity: s) } }
}

/// A frame clock (CADisplayLink) that steps a game on the main thread, outside SwiftUI's updates.
@MainActor
final class ArcadeTicker: NSObject {
    private var link: CADisplayLink?
    private var last: CFTimeInterval = 0
    var onTick: ((Float) -> Void)?

    func start() {
        guard link == nil else { return }
        let l = CADisplayLink(target: self, selector: #selector(tick(_:)))
        l.preferredFrameRateRange = CAFrameRateRange(minimum: 30, maximum: 60, preferred: 60)
        l.add(to: .main, forMode: .common)
        link = l; last = 0
    }
    func stop() { link?.invalidate(); link = nil }

    @objc private func tick(_ l: CADisplayLink) {
        let now = l.timestamp
        let dt = last == 0 ? 1.0 / 60 : min(0.05, now - last)
        last = now
        onTick?(Float(dt))
    }
}

/// Each game's records on this phone (Android's Bests): the record that goes to the group's table, and the stars of
/// each level (the snowball fight's rungs, the descent's runs), which open the next one.
enum ArcadeBests {
    static func get(_ game: String) -> Int { UserDefaults.standard.integer(forKey: "bests.\(game)") }
    /// The end of a game: keeps a higher score; true when it is a new record.
    @discardableResult
    static func ended(_ game: String, _ score: Int) -> Bool {
        let s = max(0, score)
        guard s > get(game) else { return false }
        UserDefaults.standard.set(s, forKey: "bests.\(game)")
        return true
    }
    static func level(_ game: String, _ n: Int) -> Int { UserDefaults.standard.integer(forKey: "bests_levels_\(game).\(n)") }
    @discardableResult
    static func setLevel(_ game: String, _ n: Int, _ stars: Int) -> Bool {
        guard stars > level(game, n) else { return false }
        UserDefaults.standard.set(stars, forKey: "bests_levels_\(game).\(n)")
        return true
    }
}

/// The games' little sounds made on the phone, as the site makes them with Web Audio (no files), Android's Synth:
/// grains of filtered noise, tones that slide, and a hiss under the skis. One engine, one source mixing every voice.
/// Silent with the settings' sound off.
final class ArcadeSynth: @unchecked Sendable {
    static let shared = ArcadeSynth()
    static let rate = 44_100.0
    enum Filter { case low, high, band }
    enum Wave { case sine, tri, square }

    /// A filter from the Web Audio cookbook (the same curves as BiquadFilterNode's).
    struct Biquad {
        let type: Filter; let q: Double
        var b0 = 0.0, b1 = 0.0, b2 = 0.0, a1 = 0.0, a2 = 0.0, x1 = 0.0, x2 = 0.0, y1 = 0.0, y2 = 0.0
        init(_ type: Filter, _ freq: Double, _ q: Double) { self.type = type; self.q = q; set(freq) }
        mutating func set(_ freq: Double) {
            let w = 2 * Double.pi * min(max(freq, 10), ArcadeSynth.rate * 0.45) / ArcadeSynth.rate
            let alpha = sin(w) / (2 * max(q, 0.0001)), c = cos(w), a0 = 1 + alpha
            switch type {
            case .low: b0 = (1 - c) / 2 / a0; b1 = (1 - c) / a0; b2 = b0
            case .high: b0 = (1 + c) / 2 / a0; b1 = -(1 + c) / a0; b2 = b0
            case .band: b0 = alpha / a0; b1 = 0; b2 = -alpha / a0
            }
            a1 = -2 * c / a0; a2 = (1 - alpha) / a0
        }
        mutating func next(_ x: Double) -> Double {
            let y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = x; y2 = y1; y1 = y
            return y
        }
    }

    /// Skis on the snow: band-passed noise at `freq` and `gain`, changed any time, until `stop()`.
    final class Hiss: @unchecked Sendable {
        var gain: Float = 0
        var freq = 1800.0
        fileprivate var stopped = false
        fileprivate var band = Biquad(.band, 1800, 0.7)
        fileprivate var g: Float = 0
        func stop() { stopped = true }
    }

    private struct Voice { let s: [Float]; let vol: Float; var at: Int }
    private let lock = NSLock()
    private var voices: [Voice] = []
    private var hisses: [Hiss] = []
    private var engine: AVAudioEngine?

    private func ensure() {
        guard engine == nil else { return }
        try? AVAudioSession.sharedInstance().setCategory(.ambient)
        let e = AVAudioEngine()
        guard let fmt = AVAudioFormat(standardFormatWithSampleRate: Self.rate, channels: 1) else { return }
        let node = AVAudioSourceNode(format: fmt) { [weak self] _, _, frames, abl -> OSStatus in
            self?.render(Int(frames), abl)
            return noErr
        }
        e.attach(node)
        e.connect(node, to: e.mainMixerNode, format: fmt)
        try? e.start()
        engine = e
    }

    private func render(_ n: Int, _ abl: UnsafeMutablePointer<AudioBufferList>) {
        let list = UnsafeMutableAudioBufferListPointer(abl)
        guard let raw = list.first?.mData else { return }
        let out = raw.assumingMemoryBound(to: Float.self)
        for i in 0..<n { out[i] = 0 }
        lock.lock()
        var keep: [Voice] = []
        for var v in voices {
            var i = 0
            while i < n && v.at < v.s.count { out[i] += v.s[v.at] * v.vol; v.at += 1; i += 1 }
            if v.at < v.s.count { keep.append(v) }
        }
        voices = keep
        hisses.removeAll { $0.stopped }
        for h in hisses {
            h.band.set(h.freq)
            for i in 0..<n {
                h.g += (h.gain - h.g) * 0.002
                out[i] += Float(h.band.next(Double.random(in: -1...1))) * h.g
            }
        }
        lock.unlock()
        for i in 0..<n { out[i] = tanh(out[i]) } // soft at the top
    }

    private func play(_ s: [Float], _ vol: Float) {
        guard Arcade.sound else { return }
        ensure()
        lock.lock(); if voices.count < 96 { voices.append(Voice(s: s, vol: vol, at: 0)) }; lock.unlock()
    }

    /// A tone sliding from `f0` to `f1` Hz over `dur` seconds, its loudness falling to a thousandth (Web Audio's ramp).
    func tone(_ f0: Double, _ f1: Double, _ dur: Double, _ vol: Float, _ wave: Wave = .sine) {
        guard Arcade.sound, dur > 0 else { return }
        let n = Int(Self.rate * dur); var ph = 0.0
        let s = (0..<n).map { i -> Float in
            let k = Double(i) / Double(n)
            ph += f0 * pow(f1 / f0, k) / Self.rate
            let p = ph.truncatingRemainder(dividingBy: 1)
            let w: Double = switch wave { case .tri: 4 * abs(p - 0.5) - 1; case .square: p < 0.5 ? 1 : -1; case .sine: sin(2 * .pi * ph) }
            return Float(w * pow(0.001, k))
        }
        play(s, vol)
    }

    /// The site's grain(): noise of `dur` seconds through a filter at `freq` Hz, falling away squared.
    func grain(_ dur: Double, _ vol: Float, _ type: Filter, _ freq: Double, q: Double = 1) {
        guard Arcade.sound else { return }
        let n = max(64, Int(Self.rate * dur)); var f = Biquad(type, freq, q)
        let s = (0..<n).map { i -> Float in
            let e = pow(1 - Double(i) / Double(n), 2) * (Double(i) < Double(n) * 0.05 ? Double(i) / (Double(n) * 0.05) : 1)
            return Float(f.next(Double.random(in: -1...1) * e))
        }
        play(s, vol)
    }

    func hiss() -> Hiss {
        let h = Hiss()
        ensure()
        lock.lock(); hisses.append(h); lock.unlock()
        return h
    }
}

/// The sound on and off inside a game (the site's .snd, A-41): a dark square of 44 with the speaker. It is the
/// settings' sound, so it holds in every game and in the settings too.
struct ArcadeSoundKey: View {
    let label: String
    @AppStorage("sound") private var on = true
    var body: some View {
        Button { on.toggle() } label: {
            Image(systemName: on ? "speaker.wave.2.fill" : "speaker.slash.fill")
                .font(.system(size: 17, weight: .semibold)).foregroundStyle(.white)
                .frame(width: 44, height: 44).background(Arcade.ink.opacity(0.7))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
        .accessibilityValue(on ? "🔊" : "🔇")
    }
}

/// A small dark square of 44 that leaves the run or the fight for the menu (iPhone has no system back key in a game).
struct ArcadeExitKey: View {
    let label: String
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Image(systemName: "xmark").font(.system(size: 16, weight: .bold)).foregroundStyle(.white)
                .frame(width: 44, height: 44).background(Arcade.ink.opacity(0.7))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}

// ---------- the menus' pieces ----------

/// The site's .menu: the field shows through a gradient that darkens toward the bottom; the content scrolls.
struct ArcadeShade<Content: View>: View {
    let top: UInt32
    @ViewBuilder let content: Content
    var body: some View {
        ZStack {
            LinearGradient(stops: [.init(color: Color(rgb: top, opacity: 0.25), location: 0),
                                   .init(color: Color(rgb: 0x0D1522, opacity: 0.75), location: 0.58),
                                   .init(color: Color(rgb: 0x0D1522, opacity: 0.75), location: 1)],
                           startPoint: .top, endPoint: .bottom)
                .ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 14) { content }
                    .padding(.horizontal, 16).padding(.top, 12).padding(.bottom, 20)
                    .frame(maxWidth: 560, alignment: .leading)
                    .frame(maxWidth: .infinity)
            }
        }
    }
}

/// A back link at the top of a game's menu (Android's BackLink).
struct ArcadeBackLink: View {
    let text: String
    let action: () -> Void
    @Environment(\.ski) private var ski
    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Image(systemName: "chevron.backward").font(.system(size: 15, weight: .bold))
                Text(text).font(ski.type.text(15, .bold))
            }
            .foregroundStyle(.white).frame(minHeight: 44)
        }
        .buttonStyle(.plain)
    }
}

struct ArcadeH2: View {
    let text: String
    @Environment(\.ski) private var ski
    var body: some View { Text(text).font(ski.type.display(28)).foregroundStyle(.white) }
}

struct ArcadeTitle: View {
    let text: String
    @Environment(\.ski) private var ski
    var body: some View {
        Text(text).font(ski.type.display(58)).foregroundStyle(.white).shadow(color: Arcade.ink, radius: 0, x: 0, y: 3)
            .lineLimit(2).minimumScaleFactor(0.6)
    }
}

/// One step of "how to play" (the site's .how): a gold rule on top, a title and a line.
struct ArcadeHow: View {
    let title: String
    let text: String
    @Environment(\.ski) private var ski
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title).font(ski.type.display(22)).foregroundStyle(.white)
            Text(text).font(ski.type.text(12.5)).foregroundStyle(.white).fixedSize(horizontal: false, vertical: true)
        }
        .padding(.horizontal, 8).padding(.top, 11).padding(.bottom, 8)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(Color.white.opacity(0.14))
        .overlay(alignment: .top) { Arcade.accent.frame(height: 3) }
    }
}

/// The big gold button with its ink shadow (the site's .go).
struct ArcadeGo: View {
    let text: String
    let action: () -> Void
    @Environment(\.ski) private var ski
    var body: some View {
        Button(action: action) {
            Text(text).font(ski.type.display(34)).foregroundStyle(Arcade.ink)
                .frame(maxWidth: .infinity, minHeight: 60)
                .background(Arcade.accent)
                .background(Arcade.ink.offset(y: 5))
        }
        .buttonStyle(.plain)
        .padding(.bottom, 5)
    }
}

struct ArcadeOutline: View {
    let text: String
    let action: () -> Void
    @Environment(\.ski) private var ski
    var body: some View {
        Button(action: action) {
            Text(text).font(ski.type.text(15, .bold)).foregroundStyle(.white).multilineTextAlignment(.center)
                .padding(.horizontal, 8).frame(maxWidth: .infinity, minHeight: 48)
                .overlay(Rectangle().stroke(Color.white, lineWidth: 2))
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

/// A result's number with its label (the end card).
struct ArcadeStat: View {
    let label: String
    let value: String
    @Environment(\.ski) private var ski
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(label).font(ski.type.text(11.5)).foregroundStyle(Arcade.muted).lineLimit(1).minimumScaleFactor(0.7)
            Text(value).font(ski.type.display(28)).foregroundStyle(Arcade.ink).lineLimit(1).minimumScaleFactor(0.6)
                .environment(\.layoutDirection, .leftToRight)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// The three goals under a result, each with its star.
struct ArcadeGoals: View {
    let lines: [String]
    let ok: [Bool]
    @Environment(\.ski) private var ski
    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            ForEach(lines.indices, id: \.self) { k in
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Text(ok[k] ? "★" : "☆").font(.system(size: 13)).foregroundStyle(ok[k] ? Arcade.accent : Arcade.rule)
                    Text(lines[k]).font(ski.type.text(13, ok[k] ? .bold : .regular)).foregroundStyle(ok[k] ? Arcade.ink : Arcade.muted)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.top, 8)
        .overlay(alignment: .top) { Arcade.rule.frame(height: 1) }
    }
}

/// The big word (the site's .pop): white with an ink shadow, it jumps in, stays a moment and floats away.
struct ArcadePop: View {
    let text: String?
    let n: Int
    let at: CGFloat
    let maxSize: CGFloat
    @Environment(\.ski) private var ski
    @State private var start = Date.distantPast

    var body: some View {
        GeometryReader { geo in
            TimelineView(.animation(paused: Date().timeIntervalSince(start) > 1.2)) { tl in
                let k = CGFloat(tl.date.timeIntervalSince(start) / 1.1)
                if let text, k < 1 {
                    let still = Arcade.still
                    let alpha = still ? 1 : k < 0.2 ? k / 0.2 : k < 0.75 ? 1 : 1 - (k - 0.75) / 0.25
                    let sc = still ? 1 : k < 0.2 ? 0.6 + (k / 0.2) * 0.48 : k < 0.3 ? 1.08 - (k - 0.2) / 0.1 * 0.08 : 1
                    let dy = still ? 0 : k < 0.2 ? 20 * (1 - k / 0.2) : k < 0.75 ? 0 : -30 * (k - 0.75) / 0.25
                    Text(text).font(ski.type.display(min(maxSize, max(34, geo.size.width * 0.11))))
                        .foregroundStyle(.white).shadow(color: Arcade.ink, radius: 0, x: 0, y: 6)
                        .lineLimit(1).fixedSize()
                        .scaleEffect(sc).opacity(alpha)
                        .position(x: geo.size.width / 2, y: geo.size.height * at + dy)
                }
            }
        }
        .allowsHitTesting(false)
        .onChange(of: n) { start = Date() }
    }
}

/// The top half of an ellipse around `c` (from the left, over the top, to the right), as points: no doubt about which
/// way an arc turns in a flipped space. `close` makes it a filled half.
func arcadeArc(_ c: CGPoint, _ rx: CGFloat, _ ry: CGFloat, close: Bool = false, steps: Int = 16) -> Path {
    var p = Path()
    for i in 0...steps {
        let a = CGFloat.pi + CGFloat.pi * CGFloat(i) / CGFloat(steps)
        let q = CGPoint(x: c.x + rx * cos(a), y: c.y + ry * sin(a))
        if i == 0 { p.move(to: q) } else { p.addLine(to: q) }
    }
    if close { p.closeSubpath() }
    return p
}
