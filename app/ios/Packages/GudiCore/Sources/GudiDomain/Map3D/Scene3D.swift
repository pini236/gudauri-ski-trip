import Foundation
import GudiData

/// A point on the mountain in world space: x east, y up, z south (metres).
public typealias P3 = SIMD3<Double>

/// Interleaved terrain vertices for the GPU (Android's TerrainMesh): position(3) normal(3) colour(3) slope(1).
public struct TerrainMesh: Sendable {
    public static let stride = 10
    public let vertices: [Float]
    public let indices: [UInt32]

    public static func build(_ t: Terrain) -> TerrainMesh {
        let nx = t.nx, ny = t.ny
        var v = [Float](repeating: 0, count: nx * ny * stride)
        for r in 0..<ny {
            for c in 0..<nx {
                let i = r * nx + c
                let h = t.at(c, r)
                let gx = (t.at(min(c + 1, nx - 1), r) - t.at(max(c - 1, 0), r)) / (2 * t.sx)
                let gz = (t.at(c, min(r + 1, ny - 1)) - t.at(c, max(r - 1, 0))) / (2 * t.sy)
                let len = (gx * gx + 1 + gz * gz).squareRoot()
                let slope = atan(hypot(gx, gz)) * 180 / .pi
                // the site's snow texture: snow, steep faces shed it to rock, steep low valley walls are scrub
                let rock = min(max((slope - 36) / 18, 0), 1) * 0.75
                let low = min(max((1750 - h) / 350, 0), 1) * min(max((slope - 18) / 14, 0), 1)
                var cr = 246.0, cg = 249.0, cb = 252.0
                cr = cr * (1 - rock) + 150 * rock; cg = cg * (1 - rock) + 153 * rock; cb = cb * (1 - rock) + 160 * rock
                cr = cr * (1 - low) + 150 * low; cg = cg * (1 - low) + 160 * low; cb = cb * (1 - low) + 158 * low
                let cool = min(max((3000 - h) / 1600, 0), 1) * 6
                let o = i * stride
                v[o] = Float(t.x0 + Double(c) * t.sx); v[o + 1] = Float(h); v[o + 2] = Float(t.y0 + Double(r) * t.sy)
                v[o + 3] = Float(-gx / len); v[o + 4] = Float(1 / len); v[o + 5] = Float(-gz / len)
                v[o + 6] = Float((cr - cool) / 255); v[o + 7] = Float((cg - cool * 0.5) / 255); v[o + 8] = Float(cb / 255)
                v[o + 9] = Float(slope)
            }
        }
        var idx = [UInt32]()
        idx.reserveCapacity((nx - 1) * (ny - 1) * 6)
        for r in 0..<(ny - 1) {
            for c in 0..<(nx - 1) {
                let a = UInt32(r * nx + c), b = a + 1, e = a + UInt32(nx), f = e + 1
                idx += [a, e, b, b, e, f]
            }
        }
        return TerrainMesh(vertices: v, indices: idx)
    }
}

/// Screen-space ribbons for lines on the mountain (Android's Ribbon): per vertex position(3) prev(3) next(3) side(1)
/// rgba(4) progress(1); two vertices a point, two triangles a segment. The shader widens them in screen points.
public struct Ribbon: Sendable {
    public static let stride = 15
    public let vertices: [Float]
    public let indices: [UInt32]

    public static func build(_ lines: [[P3]], colour: (Int, Int) -> SIMD4<Double>, progress: (Int, Int) -> Double = { _, _ in 0 }) -> Ribbon {
        var v: [Float] = [], idx: [UInt32] = []
        var base: UInt32 = 0
        for (li, l) in lines.enumerated() {
            let n = l.count
            guard n >= 2 else { continue }
            for i in 0..<n {
                let p = l[i], pr = l[max(0, i - 1)], nx = l[min(n - 1, i + 1)]
                let col = colour(li, i), pg = Float(progress(li, i))
                for side: Float in [-1, 1] {
                    v += [Float(p.x), Float(p.y), Float(p.z), Float(pr.x), Float(pr.y), Float(pr.z), Float(nx.x), Float(nx.y), Float(nx.z),
                          side, Float(col.x), Float(col.y), Float(col.z), Float(col.w), pg]
                }
                if i < n - 1 { let a = base + UInt32(i * 2); idx += [a, a + 1, a + 2, a + 2, a + 1, a + 3] }
            }
            base += UInt32(n * 2)
        }
        return Ribbon(vertices: v, indices: idx)
    }
}

/// The ground around a chosen run in slope colours: its corner and size in metres, and RGBA texels from the corner.
public struct SlopeLayer: Sendable {
    public let x0: Double, z0: Double, width: Double, depth: Double
    public let w: Int, h: Int
    public let rgba: [UInt8]
}

/// The mountain's lines in 3D, prepared off the main thread (Android's MapScene): the runs draped over the snow, the
/// lifts' cables, a chosen run from the top down with its slopes, and the ground layer around it (X-3).
public struct Scene3D: Sendable {
    public let terrain: Terrain
    public let runs: RunsAndLifts
    /// Each run's lines, densified every 18 m and lifted 4 m above the snow.
    public let draped: [String: [[P3]]]
    /// Each lift's cable, in the order of `runs.lifts`.
    public let cables: [[P3]]

    /// X-3: the ground's slope over 20 m each way, in texels of 10 m, out to 150 m from the run (decision 10).
    public static let groundD = 20.0
    public static let texel = 10.0
    public static let around = 150.0

    public init(terrain t: Terrain, runs: RunsAndLifts) {
        terrain = t; self.runs = runs
        var d: [String: [[P3]]] = [:]
        for p in runs.pistes { d[p.key] = p.lines.map { Scene3D.drape(t, $0, lift: 4) } }
        draped = d
        cables = runs.lifts.map { Scene3D.cable(t, $0) }
    }

    public func elev(_ x: Double, _ z: Double) -> Double { terrain.elev(x, z) }

    /// Kobi's side starts north of Kobi Pass (the site's KOBI_LAT): metres, y south.
    public static let kobiZ = Geo.y(lat: 42.5115)

    /// The main side's runs and lifts (all south of Kobi Pass), every few points: what the map opens on.
    public func mainArea() -> [P3] {
        func south(_ l: [P3]) -> Bool { !l.isEmpty && l.reduce(0) { $0 + $1.z } / Double(l.count) >= Scene3D.kobiZ }
        var out: [P3] = []
        for p in runs.pistes {
            guard let ls = draped[p.key], south(ls.flatMap { $0 }) else { continue }
            for l in ls { for (i, q) in l.enumerated() where i % 4 == 0 || i == l.count - 1 { out.append(q) } }
        }
        for c in cables where south(c) { out.append(c[0]); out.append(c[c.count - 1]) }
        return out
    }

    /// A line on the snow, a point every 18 m, `lift` metres above it.
    public static func drape(_ t: Terrain, _ xy: [(x: Double, y: Double)], lift: Double) -> [P3] {
        var out: [P3] = []
        for i in 0..<xy.count {
            let a = xy[i]
            if i > 0 {
                let b = xy[i - 1]
                let steps = Int((hypot(a.x - b.x, a.y - b.y) / 18).rounded(.up))
                if steps > 1 {
                    for k in 1..<steps {
                        let f = Double(k) / Double(steps)
                        let x = b.x + (a.x - b.x) * f, y = b.y + (a.y - b.y) * f
                        out.append(P3(x, t.elev(x, y) + lift, y))
                    }
                }
            }
            out.append(P3(a.x, t.elev(a.x, a.y) + lift, a.y))
        }
        return out
    }

    /// A lift: the straight chord between the stations, kept above the snow (the site's cable()).
    static func cable(_ t: Terrain, _ l: Lift) -> [P3] {
        var d = drape(t, l.line, lift: 0)
        let n = d.count
        guard n >= 2 else { return d }
        let e0 = d[0].y + 10, e1 = d[n - 1].y + 10
        var tot = 0.0
        for i in 1..<n { tot += hypot(d[i].x - d[i - 1].x, d[i].z - d[i - 1].z) }
        var acc = 0.0
        for i in 0..<n {
            if i > 0 { acc += hypot(d[i].x - d[i - 1].x, d[i].z - d[i - 1].z) }
            let f = tot > 0 ? acc / tot : 0
            d[i].y = max(e0 + (e1 - e0) * f, d[i].y + 9)
        }
        return d
    }

    /// Metres along a line (in 3D), from its first point.
    public static func metres(_ l: [P3]) -> [Double] {
        var c = [Double](repeating: 0, count: l.count)
        for i in 1..<max(l.count, 1) { let v = l[i] - l[i - 1]; c[i] = c[i - 1] + (v * v).sum().squareRoot() }
        return c
    }

    /// Metres along a line on the ground (x and z only).
    public static func flat(_ l: [P3]) -> [Double] {
        var c = [Double](repeating: 0, count: l.count)
        for i in 1..<max(l.count, 1) { c[i] = c[i - 1] + hypot(l[i].x - l[i - 1].x, l[i].z - l[i - 1].z) }
        return c
    }

    /// A run's lines from the top down, the highest first.
    public func topDown(_ key: String) -> [[P3]] {
        (draped[key] ?? []).filter { $0.count >= 2 }.map { l in l[0].y < l[l.count - 1].y ? l.reversed() : l }
            .sorted { $0[0].y > $1[0].y }
    }

    /// The run's longest line from its top (the one its profile shows, and the fly down follows; A-13).
    public func longest(_ key: String) -> [P3]? {
        topDown(key).max { (Scene3D.flat($0).last ?? 0) < (Scene3D.flat($1).last ?? 0) }
    }

    /// Slope in degrees along a line at each point, over about 40 m (20 m each way).
    public static func lineSlopes(_ l: [P3]) -> [Double] {
        let n = l.count
        let cum = flat(l)
        return (0..<n).map { i in
            var a = i, b = i
            while a > 0 && cum[i] - cum[a] < 20 { a -= 1 }
            while b < n - 1 && cum[b] - cum[i] < 20 { b += 1 }
            let run = max(cum[b] - cum[a], 1)
            return atan(abs(l[a].y - l[b].y) / run) * 180 / .pi
        }
    }

    /// The slope of the ground at a point (X-3): the height's central difference 20 m each way along both axes, as
    /// the site's GudRelief.slopeCanvas.
    public static func groundSlope(_ t: Terrain, _ x: Double, _ z: Double) -> Double {
        let d = groundD
        let gx = (t.elev(x + d, z) - t.elev(x - d, z)) / (2 * d)
        let gz = (t.elev(x, z + d) - t.elev(x, z - d)) / (2 * d)
        return atan(hypot(gx, gz)) * 180 / .pi
    }

    /// The ground around a chosen run, coloured by slope (T1, X-3): texels of 10 m over the run's box and 150 m around
    /// it. Alpha is how much each texel belongs to the run's surroundings: whole near the line, gone by 150 m.
    public func slopeLayer(_ lines: [[P3]]) -> SlopeLayer {
        let t = terrain
        var minX = Double.infinity, maxX = -Double.infinity, minZ = Double.infinity, maxZ = -Double.infinity
        for l in lines { for p in l { minX = min(minX, p.x); maxX = max(maxX, p.x); minZ = min(minZ, p.z); maxZ = max(maxZ, p.z) } }
        let A = Self.around, T = Self.texel
        let x0 = max(t.x0, minX - A), z0 = max(t.y0, minZ - A)
        let x1 = min(t.x1, maxX + A), z1 = min(t.y1, maxZ + A)
        let w = max(2, Int(((x1 - x0) / T).rounded())), h = max(2, Int(((z1 - z0) / T).rounded()))
        var out = [UInt8](repeating: 0, count: w * h * 4)
        // the segments, for the distance to the line
        var segs: [(ax: Double, az: Double, dx: Double, dz: Double, len2: Double)] = []
        for l in lines where l.count >= 2 {
            for k in 0..<(l.count - 1) {
                let dx = l[k + 1].x - l[k].x, dz = l[k + 1].z - l[k].z
                segs.append((l[k].x, l[k].z, dx, dz, dx * dx + dz * dz))
            }
        }
        let rgb = SlopeColors.hex.map { h -> (UInt8, UInt8, UInt8) in
            let s = Array(h.dropFirst())
            func c(_ i: Int) -> UInt8 { UInt8(String(s[i..<i + 2]), radix: 16)! }
            return (c(0), c(2), c(4))
        }
        for j in 0..<h {
            let z = z0 + (Double(j) + 0.5) * T
            for i in 0..<w {
                let x = x0 + (Double(i) + 0.5) * T
                var best = Double.infinity
                for s in segs {
                    let f = s.len2 > 0 ? min(max(((x - s.ax) * s.dx + (z - s.az) * s.dz) / s.len2, 0), 1) : 0
                    let ex = s.ax + s.dx * f - x, ez = s.az + s.dz * f - z
                    best = min(best, ex * ex + ez * ez)
                }
                let a = min(max((A - best.squareRoot()) / 60, 0), 1)
                let deg = Self.groundSlope(t, x, z)
                let c = rgb[SlopeColors.limits.firstIndex { deg < $0 } ?? SlopeColors.limits.count]
                let o = (j * w + i) * 4
                out[o] = c.0; out[o + 1] = c.1; out[o + 2] = c.2; out[o + 3] = UInt8((a * 255).rounded())
            }
        }
        return SlopeLayer(x0: x0, z0: z0, width: Double(w) * T, depth: Double(h) * T, w: w, h: h, rgba: out)
    }

    /// A chosen run painted from its top (T1): its lines top-down, the slope colour of every point, and how far down
    /// the whole run each point is (0...1), so the paint flows from the top across all its pieces.
    public func paint(_ key: String) -> (lines: [[P3]], colours: [[SIMD4<Double>]], progress: [[Double]])? {
        let lines = topDown(key)
        guard !lines.isEmpty else { return nil }
        let lens = lines.map { Scene3D.flat($0).last ?? 0 }
        let total = max(lens.reduce(0, +), 1)
        var before = 0.0
        var prog: [[Double]] = []
        for (li, l) in lines.enumerated() {
            let c = Scene3D.flat(l)
            prog.append(c.map { (before + $0) / total })
            before += lens[li]
        }
        let cols = lines.map { l in Scene3D.lineSlopes(l).map { d -> SIMD4<Double> in let c = SlopeColors.rgb(forDegrees: d); return SIMD4(c.0, c.1, c.2, 1) } }
        return (lines, cols, prog)
    }

    /// The colours of the runs on the 3D map (Android's MapRenderer.runRgb).
    public static func runRgb(_ color: String) -> SIMD4<Double> {
        switch color {
        case "green": SIMD4(0.106, 0.541, 0.298, 1)
        case "blue": SIMD4(0.122, 0.373, 0.769, 1)
        case "red": SIMD4(0.820, 0.204, 0.169, 1)
        default: SIMD4(0.075, 0.137, 0.227, 1)
        }
    }

    /// Each run's line width against a named run's, as on the site: a ski way 0.7, a section without a name 0.65.
    public static func width(_ p: Piste) -> Double { !p.named ? 0.65 : p.kind == "ski-way" ? 0.7 : 1 }

    /// The point `d` metres along a cable (3D metres), three metres above it, as the site's chairs.
    public static func along(_ l: [P3], _ cum: [Double], _ d: Double) -> P3 {
        guard l.count >= 2 else { return l.first ?? P3(0, 0, 0) }
        var i = 1
        while i < cum.count - 1 && cum[i] < d { i += 1 }
        let t = cum[i] > cum[i - 1] ? (d - cum[i - 1]) / (cum[i] - cum[i - 1]) : 0
        var p = l[i - 1] + (l[i] - l[i - 1]) * min(max(t, 0), 1)
        p.y += 3
        return p
    }

    /// The point halfway along a lift's cable on the ground (where its name goes, A-35).
    public static func middle(_ l: [P3]) -> P3? {
        guard l.count >= 2 else { return nil }
        let c = flat(l), half = (c.last ?? 0) / 2
        var k = 1
        while k < l.count - 1 && c[k] < half { k += 1 }
        let seg = max(c[k] - c[k - 1], 0.01)
        let f = min(max((half - c[k - 1]) / seg, 0), 1)
        return l[k - 1] + (l[k] - l[k - 1]) * f
    }
}
