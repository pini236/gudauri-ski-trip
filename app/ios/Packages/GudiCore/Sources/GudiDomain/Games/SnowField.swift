import Foundation

/// The kinds of snow (the site's KINDS): how deep a press goes, how much rises around it, how rough, how soft.
public enum SnowKind: CaseIterable, Sendable {
    case powder, crust, wet
    public var depth: Float { switch self { case .powder: 1.0; case .crust: 0.75; case .wet: 0.6 } }
    public var berm: Float { switch self { case .powder: 0.35; case .crust: 0.15; case .wet: 0.55 } }
    public var rough: Float { switch self { case .powder: 0.12; case .crust: 0.5; case .wet: 0.03 } }
    public var soft: Float { switch self { case .powder: 1.0; case .crust: 0.25; case .wet: 0.5 } }
}

/// Fresh snow's surface (the site's fresh snow, 13.6; Android's game/SnowField.kt): a height field of `gw` by `gh`
/// cells, lit by a low sun from the top left. A press pushes the snow down and a little up around the rim (the frozen
/// crust cracks), the snowcat's blade flattens it and leaves corduroy, and fresh snow fills every track back in.
public final class SnowField: @unchecked Sendable {
    public let gw: Int
    public let gh: Int
    public var h: [Float]
    public private(set) var base: [Float]
    /// 0 untouched; a track keeps the snow it was made in (1, or 2 for wet: X-5, the site's fix).
    public var touched: [UInt8]
    public var crack: [Bool]
    public var kind = SnowKind.powder
    private let rnd: GameRandom
    /// Tiny ice crystals that catch the sun.
    private let sparkle: [Float]

    public init(gw: Int, gh: Int, rnd: GameRandom = GameRandom()) {
        self.gw = gw; self.gh = gh; self.rnd = rnd
        sparkle = (0..<(1 << 16)).map { _ in rnd.float() }
        var b = [Float](repeating: 0, count: gw * gh)
        for y in 0..<gh { for x in 0..<gw {
            let fx = Double(x), fy = Double(y)
            let v = 2 + 0.12 * sin(fx * 0.03 + fy * 0.012) + 0.09 * sin(fy * 0.035 - fx * 0.01 + 1.3) + 0.015 * sin(fx * 0.21 + fy * 0.17)
            b[y * gw + x] = Float(v)
        } }
        base = b; h = b
        touched = [UInt8](repeating: 0, count: gw * gh)
        crack = [Bool](repeating: false, count: gw * gh)
    }

    /// All new snow at once.
    public func fresh() {
        h = base
        for i in touched.indices { touched[i] = 0; crack[i] = false }
    }

    /// Fresh snow falling: every track fills back in by `k` of what is left.
    public func fill(_ k: Float) {
        for i in h.indices where h[i] != base[i] {
            h[i] += (base[i] - h[i]) * k
            if abs(h[i] - base[i]) < 0.01 { h[i] = base[i]; touched[i] = 0; crack[i] = false }
        }
    }

    /// An oval stamp at (`cx0`, `cy0`) in cells, `rx` by `ry`, turned by `ang`: the snow inside goes down by `depth` (by
    /// `profile` of the stamp's own coordinates, or a soft bowl), and a little of it rises around the rim. The snow moved.
    @discardableResult
    public func press(_ cx0: Float, _ cy0: Float, _ rx: Float, _ ry: Float, _ ang: Float, _ depth: Float, profile: ((Float, Float) -> Float)? = nil) -> Float {
        let k = kind, ca = cos(ang), sa = sin(ang), r0 = max(rx, ry) * 1.7
        var moved: Float = 0
        let x0 = max(1, Int(floor(cx0 - r0))), x1 = min(gw - 2, Int(ceil(cx0 + r0)))
        let y0 = max(1, Int(floor(cy0 - r0))), y1 = min(gh - 2, Int(ceil(cy0 + r0)))
        if x0 <= x1 && y0 <= y1 {
            for y in y0...y1 { for x in x0...x1 {
                let dx = Float(x) - cx0, dy = Float(y) - cy0
                let u = (dx * ca + dy * sa) / rx, v = (-dx * sa + dy * ca) / ry
                let r = (u * u + v * v).squareRoot(), i = y * gw + x
                if r < 1 {
                    let pr = profile?(u, v) ?? pow(cos(r * .pi / 2), k.soft < 0.5 ? 0.4 : 0.8)
                    let jitter = 1 + (rnd.float() - 0.5) * k.rough
                    let target = base[i] - depth * k.depth * pr * jitter
                    if h[i] > target { moved += h[i] - target; h[i] = target; touched[i] = k == .wet ? 2 : 1 }
                    if k == .crust && r > 0.82 && rnd.float() < 0.35 { crack[i] = true }
                } else if r < 1.6 {
                    let add = k.berm * depth * 0.25 * sin((r - 1) / 0.6 * .pi)
                    if add > 0 && h[i] < base[i] + 0.6 { h[i] += add * (0.6 + rnd.float() * 0.4) * 0.25 }
                }
            } }
        }
        // cracks run out from the stamp in the frozen crust
        if k == .crust && moved > 0.5 {
            for _ in 0..<3 {
                var a = rnd.float() * 2 * .pi, x = cx0 + cos(a) * rx, y = cy0 + sin(a) * ry
                for _ in 0..<14 {
                    a += (rnd.float() - 0.5) * 0.8; x += cos(a); y += sin(a)
                    let xi = Int(x), yi = Int(y)
                    if (1..<gw).contains(xi) && (1..<gh).contains(yi) { crack[yi * gw + xi] = true }
                }
            }
        }
        return moved
    }

    /// The snowcat: a wide blade flattens everything in front of it, and the tiller leaves corduroy behind.
    @discardableResult
    public func groom(_ cx0: Float, _ cy0: Float, _ ang: Float) -> Float {
        let half: Float = 24, ca = cos(ang), sa = sin(ang), r0 = half + 2
        var m: Float = 0
        let x0 = max(1, Int(floor(cx0 - r0))), x1 = min(gw - 2, Int(ceil(cx0 + r0)))
        let y0 = max(1, Int(floor(cy0 - r0))), y1 = min(gh - 2, Int(ceil(cy0 + r0)))
        guard x0 <= x1 && y0 <= y1 else { return 0 }
        for y in y0...y1 { for x in x0...x1 {
            let dx = Float(x) - cx0, dy = Float(y) - cy0
            let along = dx * ca + dy * sa, cross = -dx * sa + dy * ca
            if abs(along) < 2.2 && abs(cross) < half {
                let i = y * gw + x
                let edge = min(1, (half - abs(cross)) / 3)
                let target = base[i] - 0.1 + 0.035 * sin(cross * 1.25)
                let nh = h[i] + (target - h[i]) * edge
                m += abs(h[i] - nh); h[i] = nh; touched[i] = 0; crack[i] = false
            }
        } }
        return m
    }

    /// The light on the snow into `px` (0xAARRGGBB, a pixel a cell): slopes facing the sun bright, the far walls of a
    /// hole in blue shadow.
    public func render(_ px: inout [UInt32]) {
        let wet = kind == .wet
        for y in 0..<gh { for x in 0..<gw {
            let i = y * gw + x
            let hx = h[i + (x < gw - 1 ? 1 : 0)] - h[i - (x > 0 ? 1 : 0)]
            let hy = h[i + (y < gh - 1 ? gw : 0)] - h[i - (y > 0 ? gw : 0)]
            let l = min(1.12, max(0.35, 0.82 + (hx * 0.9 + hy * 1.2) * 1.6))
            let depth = max(0, base[i] - h[i])
            var r = 226 * l + 20, g = 236 * l + 14, b = 248 * l + 8
            r -= depth * 22; g -= depth * 12
            // a track keeps the snow it was made in; the untouched snow is the kind chosen now
            if touched[i] != 0 ? touched[i] == 2 : wet { r -= 6; g -= 3 }
            if crack[i] { r -= 40; g -= 30; b -= 18 }
            if l > 0.95 && sparkle[i & 65535] > 0.985 && touched[i] == 0 { r = 255; g = 255; b = 255 }
            px[i] = 0xFF00_0000 | (SnowField.byte(r) << 16) | (SnowField.byte(g) << 8) | SnowField.byte(b)
        } }
    }

    private static func byte(_ v: Float) -> UInt32 { UInt32(min(255, max(0, Int(v)))) }

    /// How much of the snow has tracks, in percent (every seventh cell, as the site counts).
    public func tracks() -> Int {
        var c = 0, i = 0
        while i < touched.count { if touched[i] != 0 { c += 1 }; i += 7 }
        return Int((Float(c) * 7 / Float(touched.count) * 100).rounded())
    }

    /// A boot's sole: a heel and a toe, with a tread (the stamp's own coordinates).
    public static func boot(_ u: Float, _ v: Float) -> Float {
        let toe = hypotf(u * 1.05, (v + 0.38) / 0.62), heel = hypotf(u * 1.25, (v - 0.55) / 0.45)
        return min(toe, heel) < 1 ? (sin(v * 22) > 0.2 ? 1 : 0.9) : 0
    }
}
