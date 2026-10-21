import Foundation
import GudiData

/// The site's projection (site/js/app.js, terrain.json's `proj`): metres, x east, y south, around lat0/lon0. The same
/// numbers as Android's data/Geo.kt.
public enum Geo {
    public static let lat0 = 42.51
    public static let lon0 = 44.495
    static let kx = 111_320.0 * cos(lat0 * .pi / 180)
    static let ky = 111_320.0

    public static func x(lon: Double) -> Double { (lon - lon0) * kx }
    public static func y(lat: Double) -> Double { -(lat - lat0) * ky }
    /// A line of the data ([lat, lon] points) as a flat [x, y, x, y, ...] in metres.
    public static func line(_ g: [[Double]]) -> [Double] {
        var out: [Double] = []
        out.reserveCapacity(g.count * 2)
        for p in g where p.count >= 2 { out.append(x(lon: p[1])); out.append(y(lat: p[0])) }
        return out
    }
}

/// The top view of the mountain from site/data/terrain.json, as the site draws it under the meeting point and the
/// overview map (site/js/relief.js, svgRelief; Android's meet/Relief2D.kt): the elevation grid, the shaded relief
/// picture, the contours, the village, rivers, lakes, roads and named places. Pure data, in the site's metres (x east,
/// y south); every line is a flat [x, y, x, y, ...] array. Parsed once, off the main thread (it is ~1 MB of JSON).
///
/// Stable API (shared by the meeting point and the trail map's top view):
/// `Relief.parse(_:)`, `elev(_:_:)`, the box `x0...y1`, `hillJPEG`, `c50` `c100` `c250` `c500`, `village`, `rivers`,
/// `water`, `roads[0...4]`, `places`, `peaks`.
public struct Relief: Sendable {
    public struct Place: Sendable, Equatable {
        public let name: String
        /// The Georgian name, when the data has one.
        public let ka: String?
        public let x: Double, y: Double
    }
    public struct Peak: Sendable, Equatable {
        public let name: String
        public let ele: Int
        public let x: Double, y: Double
        public let pass: Bool
    }

    // the elevation grid (dem): nx*ny metres, row 0 at y0 (north)
    public let nx: Int, ny: Int
    public let x0: Double, y0: Double, x1: Double, y1: Double
    let h: [Int16]

    /// The shaded relief picture (a JPEG), drawn over the box x0...y1; nil if it does not decode.
    public let hillJPEG: Data?
    /// Contours by weight: every 50 m (not on 100 or 250), every 100 m (not on 250), every 250 m; and the index lines
    /// every 500 m (also inside c250), for a map that wants them stronger.
    public let c50: [[Double]], c100: [[Double]], c250: [[Double]], c500: [[Double]]
    /// Closed shapes.
    public let village: [[Double]]
    public let rivers: [[Double]]
    /// Lakes, closed shapes.
    public let water: [[Double]]
    /// Road classes 0 (main) to 4 (tracks).
    public let roads: [[[Double]]]
    public let places: [Place]
    public let peaks: [Peak]

    /// Bilinear elevation in metres, clamped to the grid's edge (the site's elev(), Android's Terrain.elev).
    public func elev(_ x: Double, _ y: Double) -> Double {
        let sx = (x1 - x0) / Double(nx - 1), sy = (y1 - y0) / Double(ny - 1)
        let c = min(max((x - x0) / sx, 0), Double(nx) - 1.0001)
        let r = min(max((y - y0) / sy, 0), Double(ny) - 1.0001)
        let c0 = Int(c), r0 = Int(r)
        let fc = c - Double(c0), fr = r - Double(r0)
        let i = r0 * nx + c0
        let a = Double(h[i]) * (1 - fc) + Double(h[i + 1]) * fc
        let b = Double(h[i + nx]) * (1 - fc) + Double(h[i + nx + 1]) * fc
        return a * (1 - fr) + b * fr
    }

    public enum ParseError: Error { case bad(String) }

    public static func parse(_ data: Data) throws -> Relief {
        guard let t = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let d = t["dem"] as? [String: Any] else { throw ParseError.bad("no dem") }
        func num(_ o: [String: Any], _ k: String) throws -> Double {
            guard let v = o[k] as? NSNumber else { throw ParseError.bad(k) }
            return v.doubleValue
        }
        guard let b64 = d["b64"] as? String, let raw = Data(base64Encoded: b64) else { throw ParseError.bad("b64") }
        let h = int16s(raw)
        let nx = Int(try num(d, "nx")), ny = Int(try num(d, "ny"))
        guard nx > 1, ny > 1, h.count >= nx * ny else { throw ParseError.bad("grid") }

        let hill: Data? = (t["hill"] as? [String: Any])?["src"].flatMap { $0 as? String }.flatMap { src in
            guard let i = src.range(of: "base64,") else { return nil }
            return Data(base64Encoded: String(src[i.upperBound...]))
        }

        // contours: [level, n, x, y, dx, dy, ...] as little-endian int16 (relief.js)
        var c50: [[Double]] = [], c100: [[Double]] = [], c250: [[Double]] = [], c500: [[Double]] = []
        if let cs = t["contours"] as? String, let craw = Data(base64Encoded: cs) {
            let s = int16s(craw)
            var i = 0
            while i + 3 < s.count {
                let lev = Int(s[i]), n = Int(s[i + 1])
                var x = Double(s[i + 2]), y = Double(s[i + 3]); i += 4
                var line: [Double] = [x, y]
                line.reserveCapacity(n * 2)
                for _ in 1..<max(n, 1) where i + 1 < s.count {
                    x += Double(s[i]); y += Double(s[i + 1]); line.append(x); line.append(y); i += 2
                }
                if lev % 250 == 0 { c250.append(line) } else if lev % 100 == 0 { c100.append(line) } else { c50.append(line) }
                if lev % 500 == 0 { c500.append(line) }
            }
        }

        let env = t["env"] as? [String: Any] ?? [:]
        func flat(_ a: Any?) -> [Double] { (a as? [Any])?.compactMap { ($0 as? NSNumber)?.doubleValue } ?? [] }
        let village: [[Double]] = ((env["village"] as? [Any]) ?? []).map(flat)
        let rivers: [[Double]] = ((env["rivers"] as? [Any]) ?? []).map(flat)
        let water: [[Double]] = ((env["water"] as? [Any]) ?? []).map { flat(($0 as? [String: Any])?["g"]) }
        var roads: [[[Double]]] = Array(repeating: [], count: 5)
        for r in env["roads"] as? [Any] ?? [] {
            guard let r = r as? [Any], r.count >= 2, let cls = (r[0] as? NSNumber)?.intValue else { continue }
            roads[min(max(cls, 0), 4)].append(flat(r[1]))
        }
        let places: [Place] = (env["places"] as? [Any] ?? []).compactMap {
            guard let o = $0 as? [String: Any], let n = o["n"] as? String, let x = o["x"] as? NSNumber, let y = o["y"] as? NSNumber else { return nil }
            return Place(name: n, ka: o["ka"] as? String, x: x.doubleValue, y: y.doubleValue)
        }
        let peaks: [Peak] = (t["peaks"] as? [Any] ?? []).compactMap {
            guard let o = $0 as? [String: Any], let n = o["n"] as? String, let e = o["ele"] as? NSNumber,
                  let x = o["x"] as? NSNumber, let y = o["y"] as? NSNumber else { return nil }
            return Peak(name: n, ele: e.intValue, x: x.doubleValue, y: y.doubleValue, pass: (o["pass"] as? Bool) ?? false)
        }
        return Relief(nx: nx, ny: ny, x0: try num(d, "x0"), y0: try num(d, "y0"), x1: try num(d, "x1"), y1: try num(d, "y1"), h: h,
                      hillJPEG: hill, c50: c50, c100: c100, c250: c250, c500: c500, village: village, rivers: rivers,
                      water: water, roads: roads, places: places, peaks: peaks)
    }

    static func int16s(_ d: Data) -> [Int16] {
        var out = [Int16](repeating: 0, count: d.count / 2)
        d.withUnsafeBytes { b in
            for i in 0..<out.count { out[i] = Int16(bitPattern: UInt16(b[i * 2]) | UInt16(b[i * 2 + 1]) << 8) }
        }
        return out
    }
}
