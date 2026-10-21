import Foundation

/// The site's projection (site/js/app.js, Android's data/Geo.kt): metres, x east, y south, around lat0/lon0.
public enum Geo {
    public static let lat0 = 42.51
    public static let lon0 = 44.495
    static let kx = 111_320 * cos(lat0 * .pi / 180)
    static let ky = 111_320.0

    public static func x(_ lon: Double) -> Double { (lon - lon0) * kx }
    public static func y(_ lat: Double) -> Double { -(lat - lat0) * ky }
}

public struct Peak: Sendable {
    public let name: String
    public let ele: Int
    public let x: Double, y: Double
}

/// The elevation grid from site/data/terrain.json (Android's data/Terrain.kt): nx*ny int16 metres, little-endian, row
/// 0 at y0 (north); with the peaks, the hillshade image (`hill.src`, a data URL), the contour lines (SVG path text) and
/// the surroundings (roads, water, the village, rivers and places) kept as they are for the map's own drawing.
public struct Terrain {
    public let nx: Int, ny: Int
    public let x0: Double, y0: Double, x1: Double, y1: Double
    public let h: [Int16]
    public let peaks: [Peak]
    /// The raw JSON object, for the layers each drawing reads in its own way (contours, env, hill).
    public let raw: [String: Any]

    public var sx: Double { (x1 - x0) / Double(nx - 1) }
    public var sy: Double { (y1 - y0) / Double(ny - 1) }

    public func at(_ c: Int, _ r: Int) -> Double { Double(h[r * nx + c]) }

    /// Bilinear elevation in metres, clamped to the grid's edge (the site's elev()).
    public func elev(_ x: Double, _ y: Double) -> Double {
        let c = min(max((x - x0) / sx, 0), Double(nx) - 1.0001)
        let r = min(max((y - y0) / sy, 0), Double(ny) - 1.0001)
        let c0 = Int(c), r0 = Int(r), fc = c - Double(c0), fr = r - Double(r0)
        let i = r0 * nx + c0
        let a = Double(h[i]) * (1 - fc) + Double(h[i + 1]) * fc
        let b = Double(h[i + nx]) * (1 - fc) + Double(h[i + nx + 1]) * fc
        return a * (1 - fr) + b * fr
    }

    public static func parse(_ data: Data) throws -> Terrain {
        guard let t = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let d = t["dem"] as? [String: Any], let b64 = d["b64"] as? String,
              let bytes = Data(base64Encoded: b64) else { throw CocoaError(.fileReadCorruptFile) }
        let h: [Int16] = bytes.withUnsafeBytes { raw in
            (0..<bytes.count / 2).map { Int16(littleEndian: raw.loadUnaligned(fromByteOffset: $0 * 2, as: Int16.self)) }
        }
        let peaks = (t["peaks"] as? [[String: Any]] ?? []).map {
            Peak(name: $0["n"] as? String ?? "", ele: ($0["ele"] as? NSNumber)?.intValue ?? 0,
                 x: ($0["x"] as? NSNumber)?.doubleValue ?? 0, y: ($0["y"] as? NSNumber)?.doubleValue ?? 0)
        }
        func n(_ k: String) -> Double { (d[k] as? NSNumber)?.doubleValue ?? 0 }
        return Terrain(nx: Int(n("nx")), ny: Int(n("ny")), x0: n("x0"), y0: n("y0"), x1: n("x1"), y1: n("y1"),
                       h: h, peaks: peaks, raw: t)
    }
}

extension Terrain: @unchecked Sendable {}
