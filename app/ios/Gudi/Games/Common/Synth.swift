import AVFoundation
import Foundation

/// The games' little sounds made on the phone, as the site makes them with Web Audio and Android with fx/Synth.kt (no
/// files): grains of filtered noise (the snow, the ice), tones that slide (a ping, a squeak), the crunch of packed snow
/// and a swish. Everything goes into one mixer (one source node of AVAudioEngine), so many grains a second cost little.
/// Off with the settings' sound switch. One per game screen: `start()` when it shows, `stop()` when it goes.
final class Synth: @unchecked Sendable {
    enum Filter { case low, high, band }
    static let rate = 44_100.0

    private let engine = AVAudioEngine()
    private var node: AVAudioSourceNode?
    private let mixer = Mixer()

    private var on: Bool { FxPrefs.sound }

    func start() {
        guard node == nil else { return }
        try? AVAudioSession.sharedInstance().setCategory(.ambient)
        let mixer = self.mixer
        let format = AVAudioFormat(standardFormatWithSampleRate: Synth.rate, channels: 1)!
        let n = AVAudioSourceNode(format: format) { _, _, frames, abl -> OSStatus in
            let buffers = UnsafeMutableAudioBufferListPointer(abl)
            guard let out = buffers.first?.mData?.assumingMemoryBound(to: Float.self) else { return noErr }
            mixer.render(out, Int(frames))
            for b in buffers.dropFirst() { b.mData?.copyMemory(from: out, byteCount: Int(b.mDataByteSize)) }
            return noErr
        }
        engine.attach(n)
        engine.connect(n, to: engine.mainMixerNode, format: format)
        node = n
        try? engine.start()
    }

    func stop() {
        engine.stop()
        if let n = node { engine.detach(n) }
        node = nil
        mixer.clear()
    }

    /// The site's grain(): noise of `dur` seconds through a `type` filter at `freq` Hz (Q `q`), falling away squared,
    /// with a short rise; at `at` seconds from now.
    func grain(_ at: Double, _ dur: Double, _ vol: Float, _ type: Filter, _ freq: Double, _ q: Double) {
        guard on else { return }
        let n = max(64, Int(Synth.rate * dur))
        var f = Biquad(type, freq, q)
        var s = [Float](repeating: 0, count: n)
        for i in 0..<n {
            let k = 1 - Double(i) / Double(n)
            let e = k * k * (Double(i) < Double(n) * 0.05 ? Double(i) / (Double(n) * 0.05) : 1)
            s[i] = Float(f.next((Double.random(in: 0..<1) * 2 - 1) * e))
        }
        mixer.play(s, vol, Int(at * Synth.rate))
    }

    /// A tone sliding from `f0` to `f1` Hz over `dur` seconds (exponential, as Web Audio's ramp), its loudness falling the
    /// same way from `vol`; a sine, a triangle, a square, or a sawtooth through a band at `band` Hz (wet snow's squeak).
    func tone(_ at: Double, _ f0: Double, _ f1: Double, _ dur: Double, _ vol: Float, saw: Bool = false, band: Double = 0, q: Double = 1,
              tri: Bool = false, square: Bool = false) {
        guard on else { return }
        let n = Int(Synth.rate * dur)
        var f: Biquad? = band > 0 ? Biquad(.band, band, q) : nil
        var ph = 0.0
        var s = [Float](repeating: 0, count: max(1, n))
        for i in 0..<n {
            let k = Double(i) / Double(n)
            ph += f0 * pow(f1 / f0, k) / Synth.rate
            let w: Double
            if saw { w = 2 * (ph - (ph + 0.5).rounded(.down)) }
            else if tri { w = 4 * abs(ph.truncatingRemainder(dividingBy: 1) - 0.5) - 1 }
            else if square { w = ph.truncatingRemainder(dividingBy: 1) < 0.5 ? 1 : -1 }
            else { w = sin(2 * .pi * ph) }
            let v = w * pow(0.001, k)
            s[i] = Float(f?.next(v) ?? v)
        }
        mixer.play(s, vol, Int(at * Synth.rate))
    }

    /// A merge: `lv` the new ball's level (0 a flake .. 11 the king).
    func crunch(_ lv: Int, volume: Float = 0.25) {
        guard on else { return }
        let n = Int(Synth.rate * (0.09 + Double(lv) * 0.01))
        var b = Biquad(.band, 700 + Double(lv) * 260, 1.4)
        var s = [Float](repeating: 0, count: n)
        for i in 0..<n {
            let k = 1 - Double(i) / Double(n)
            let x = (Double.random(in: 0..<1) * 2 - 1) * k * k * (Double.random(in: 0..<1) < 0.3 ? 1 : 0.35)
            s[i] = Float(b.next(x) * 3)
        }
        mixer.play(s, volume * 1.6, 0)
        if lv >= 4 { let f = 330 * pow(2, Double(lv - 4) / 6); tone(0, f, f, 0.35, volume * 0.5) }
    }

    /// A slide.
    func swish() {
        guard on else { return }
        let n = Int(Synth.rate * 0.07)
        var prev = 0.0
        var s = [Float](repeating: 0, count: n)
        for i in 0..<n {
            let x = (Double.random(in: 0..<1) * 2 - 1) * sin(.pi * Double(i) / Double(n))
            s[i] = Float(x - prev); prev = x // high-pass: only the hiss
        }
        mixer.play(s, 0.3, 0)
    }

    /// The site's ding(): a triangle (or a square) at `f` Hz for `dur` seconds, falling from `vol` to a thousandth.
    func ding(_ f: Double, _ dur: Double = 0.3, _ vol: Float = 0.18, square: Bool = false) {
        guard on else { return }
        let n = Int(Synth.rate * dur)
        var s = [Float](repeating: 0, count: max(1, n))
        for i in 0..<n {
            let ph = (f * Double(i) / Synth.rate).truncatingRemainder(dividingBy: 1)
            let w = square ? (ph < 0.5 ? 1.0 : -1.0) : 4 * abs(ph - 0.5) - 1
            s[i] = Float(w * pow(0.001 / Double(vol), Double(i) / Double(n)))
        }
        mixer.play(s, vol, 0)
    }

    /// Skis on the snow (the ski school's hiss): noise through a band at `freq` Hz, at `gain`, both changed any time, on
    /// until `stop()`. Silent while the settings' sound is off.
    func hiss() -> Hiss { let h = Hiss(); mixer.add(h); return h }

    final class Hiss: @unchecked Sendable {
        private let lock = NSLock()
        private var _gain: Float = 0, _freq = 1800.0, _stopped = false
        var gain: Float { get { lock.withLock { _gain } } set { lock.withLock { _gain = newValue } } }
        var freq: Double { get { lock.withLock { _freq } } set { lock.withLock { _freq = newValue } } }
        var stopped: Bool { lock.withLock { _stopped } }
        func stop() { lock.withLock { _stopped = true } }
        fileprivate var band = Biquad(.band, 1800, 0.7)
        fileprivate var g: Float = 0
    }

    /// A filter from the Web Audio cookbook (the same curves as BiquadFilterNode's lowpass, highpass and bandpass).
    fileprivate struct Biquad {
        private let type: Filter, q: Double
        private var b0 = 0.0, b1 = 0.0, b2 = 0.0, a1 = 0.0, a2 = 0.0
        private var x1 = 0.0, x2 = 0.0, y1 = 0.0, y2 = 0.0
        init(_ type: Filter, _ freq: Double, _ q: Double) { self.type = type; self.q = q; set(freq) }
        /// A new frequency, keeping what is already ringing.
        mutating func set(_ freq: Double) {
            let w = 2 * .pi * min(Synth.rate * 0.45, max(10, freq)) / Synth.rate
            let alpha = sin(w) / (2 * max(q, 0.0001))
            let c = cos(w), a0 = 1 + alpha
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

    /// Every voice and hiss, mixed on the audio thread, soft at the top.
    private final class Mixer: @unchecked Sendable {
        private final class Voice { let s: [Float]; let vol: Float; var wait: Int; var at = 0; init(_ s: [Float], _ v: Float, _ w: Int) { self.s = s; vol = v; wait = w } }
        private let lock = NSLock()
        private var voices: [Voice] = []
        private var hisses: [Hiss] = []

        func play(_ s: [Float], _ vol: Float, _ wait: Int) {
            lock.withLock {
                if voices.count > 96 { return } // a storm of grains: the rest would not be heard anyway
                voices.append(Voice(s, vol, max(0, wait)))
            }
        }
        func add(_ h: Hiss) { lock.withLock { hisses.append(h) } }
        func clear() { lock.withLock { voices.removeAll(); hisses.removeAll() } }

        func render(_ out: UnsafeMutablePointer<Float>, _ n: Int) {
            for i in 0..<n { out[i] = 0 }
            let audible = FxPrefs.sound
            lock.lock()
            voices.removeAll { v in
                var i = 0
                if v.wait > 0 { let w = min(v.wait, n); v.wait -= w; i = w }
                while i < n && v.at < v.s.count { out[i] += v.s[v.at] * v.vol; v.at += 1; i += 1 }
                return v.at >= v.s.count
            }
            hisses.removeAll { h in
                let stopped = h.stopped
                let to: Float = stopped || !audible ? 0 : h.gain
                if stopped && h.g < 1e-4 { return true }
                h.band.set(h.freq)
                // a smooth change of loudness, about a twentieth of a second
                for i in 0..<n { h.g += (to - h.g) * 0.0005; out[i] += Float(h.band.next(Double.random(in: -1..<1))) * h.g }
                return false
            }
            lock.unlock()
            for i in 0..<n { out[i] = Float(tanh(Double(out[i]))) * 0.92 }
        }
    }
}
