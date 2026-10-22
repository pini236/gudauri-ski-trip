import Foundation
import GudiData

/// Where the sun is over Gudauri, and the shadows it casts on the mountain (Android's map/Sun.kt).
extension Sun {
    static let r = Double.pi / 180

    /// Azimuth from north, clockwise, and altitude above the horizon, both in radians (low precision, a fraction of a degree).
    public static func position(_ epochMs: Int64, lat: Double = 42.475, lon: Double = 44.48) -> (az: Double, alt: Double) {
        let d = Double(epochMs) / 86_400_000 + 2440587.5 - 2451545.0
        let g = (357.529 + 0.98560028 * d) * r
        let q = 280.459 + 0.98564736 * d
        let l = (q + 1.915 * sin(g) + 0.020 * sin(2 * g)) * r
        let e = (23.439 - 0.00000036 * d) * r
        let ra = atan2(cos(e) * sin(l), cos(l))
        let dec = asin(sin(e) * sin(l))
        let gmst = (18.697374558 + 24.06570982441908 * d) * 15.0
        let h = (gmst + lon) * r - ra
        let la = lat * r
        let alt = asin(sin(la) * sin(dec) + cos(la) * cos(dec) * cos(h))
        let az = atan2(-sin(h), tan(dec) * cos(la) - sin(la) * cos(h))
        return ((az + 2 * .pi).truncatingRemainder(dividingBy: 2 * .pi), alt)
    }

    /// Unit vector towards the sun in world space (x east, y up, z south).
    public static func direction(az: Double, alt: Double) -> SIMD3<Double> {
        SIMD3(sin(az) * cos(alt), sin(alt), -cos(az) * cos(alt))
    }

    /// Soft shadow per grid point: march towards the light over the elevation model and see whether the mountain
    /// rises above the ray. 0 in the sun, 1 in the shade.
    public static func shadows(_ t: Terrain, toward dir: SIMD3<Double>) -> [Float] {
        var out = [Float](repeating: 0, count: t.nx * t.ny)
        let horiz = max((dir.x * dir.x + dir.z * dir.z).squareRoot(), 1e-4)
        let ux = dir.x / horiz, uz = dir.z / horiz
        let rise = dir.y / horiz // metres up per metre along the ground
        let step = 35.0, maxD = 9000.0
        for row in 0..<t.ny {
            for c in 0..<t.nx {
                let x = t.x0 + Double(c) * t.sx, z = t.y0 + Double(row) * t.sy
                let h0 = t.at(c, row) + 3
                var s = 0.0, dd = step
                while dd < maxD {
                    let px = x + ux * dd, pz = z + uz * dd
                    if px < t.x0 || px > t.x1 || pz < t.y0 || pz > t.y1 { break }
                    let over = t.elev(px, pz) - (h0 + rise * dd)
                    if over > 0 { s = max(s, min(over / 30, 1)); if s >= 1 { break } }
                    if h0 + rise * dd > 3400 { break }
                    dd += step * (1 + dd / 3000)
                }
                out[row * t.nx + c] = Float(s)
            }
        }
        return out
    }
}

/// The sky and the light over the mountain at a moment in Gudauri, as on the site (N1, N2) and Android's map/Sky.kt:
/// the same sky colours through the day, warm light when the sun is low, moonlight at night; the sun and the moon
/// where they really are. RGB 0...1.
public enum Sky {
    public struct Light: Sendable {
        /// Towards the light that casts the shadows: the sun by day, the moon (or a dim sky light) at night.
        public let dir: SIMD3<Double>
        public let color: SIMD3<Double>
        public let ambTop: SIMD3<Double>, ambGround: SIMD3<Double>
        public let skyTop: SIMD3<Double>, skyBottom: SIMD3<Double>
        public let sunDir: SIMD3<Double>, moonDir: SIMD3<Double>
        public let sunUp: Bool, moonUp: Bool
        public let glow: Double, glowColor: SIMD3<Double>
        public let dark: Bool
        public let hour: Double
    }

    struct Key { let h: Double, top: UInt32, bottom: UInt32, glow: Double, glowC: UInt32 }

    /// The site's keyframes (keys() in site/js/app.js), by Gudauri hour.
    static func keys(_ rise: Double, _ set: Double, _ noon: Double) -> [Key] {
        func night(_ h: Double) -> Key { Key(h: h, top: 0x050A15, bottom: 0x1A2645, glow: 0, glowC: 0xFFFFFF) }
        return [
            night(0), night(rise - 1.2),
            Key(h: rise - 0.3, top: 0x2C3B66, bottom: 0xE8A987, glow: 0.7, glowC: 0xFFB38A),
            Key(h: rise + 1.2, top: 0x6FA6DC, bottom: 0xDCEAF4, glow: 0.35, glowC: 0xFFF2D6),
            Key(h: noon, top: 0x4F90D2, bottom: 0xD2E4F3, glow: 0.2, glowC: 0xFFFFFF),
            Key(h: set - 1.8, top: 0x6F9CCB, bottom: 0xF1DDC2, glow: 0.6, glowC: 0xFFD29A),
            Key(h: set - 0.35, top: 0x3A4677, bottom: 0xF09A6A, glow: 1, glowC: 0xFF9A6A),
            Key(h: set + 0.45, top: 0x1B2448, bottom: 0x6E5D86, glow: 0.35, glowC: 0xC98AA0),
            night(set + 1.3), night(24),
        ]
    }

    public static func rgb(_ c: UInt32) -> SIMD3<Double> {
        SIMD3(Double((c >> 16) & 255) / 255, Double((c >> 8) & 255) / 255, Double(c & 255) / 255)
    }
    static func mix(_ a: SIMD3<Double>, _ b: SIMD3<Double>, _ t: Double) -> SIMD3<Double> { a + (b - a) * t }

    public static func at(_ epochMs: Int64) -> Light {
        let (rise, set, noon) = Sun.times(epochMs)
        let local = Double(epochMs) / 3_600_000 + 4
        let h = local - (local / 24).rounded(.down) * 24
        let k = keys(rise, set, noon)
        var i = 0
        while i < k.count - 2 && k[i + 1].h < h { i += 1 }
        let a = k[i], b = k[i + 1]
        let span = b.h - a.h == 0 ? 1 : b.h - a.h
        let f = min(max((h - a.h) / span, 0), 1)
        let dark = h < rise - 0.3 || h > set + 0.3

        let sp = Sun.position(epochMs)
        let sunDir = Sun.direction(az: sp.az, alt: sp.alt)
        let mp = moon(epochMs)
        let moonDir = Sun.direction(az: mp.az, alt: mp.alt)
        let sunUp = sp.alt > 0
        let moonUp = mp.alt > 4 * Sun.r

        let altDeg = sp.alt / Sun.r
        let dir: SIMD3<Double>, color: SIMD3<Double>, top: SIMD3<Double>, ground: SIMD3<Double>
        if sunUp {
            let low = min(max(1 - altDeg / 12, 0), 1) // 1 at the horizon, 0 above 12°
            let warm = mix(rgb(0xFFF6EA), rgb(0xFF9F7A), low)
            dir = sunDir
            color = warm * (0.5 + 0.3 * min(altDeg / 20, 1))
            let hemi = 0.55 - 0.15 * low
            top = rgb(low > 0.5 ? 0xB9B0D0 : 0xDDE8F5) * hemi
            ground = rgb(0x7D879A) * hemi
        } else {
            // moonlight, from the real moon when it is up; otherwise a faint light from the south-east, as on the site
            dir = moonUp ? moonDir : Sun.direction(az: 150 * Sun.r, alt: 38 * Sun.r)
            color = rgb(0x9FB4E0) * (moonUp ? 0.3 : 0.18)
            let hemi = dark ? 0.42 : 0.5
            top = rgb(0x4A5A86) * hemi
            ground = rgb(0x1C2438) * hemi
        }
        return Light(dir: dir, color: color, ambTop: top, ambGround: ground,
                     skyTop: mix(rgb(a.top), rgb(b.top), f), skyBottom: mix(rgb(a.bottom), rgb(b.bottom), f),
                     sunDir: sunDir, moonDir: moonDir, sunUp: sunUp, moonUp: moonUp,
                     glow: a.glow + (b.glow - a.glow) * f, glowColor: rgb(f < 0.5 ? a.glowC : b.glowC), dark: dark, hour: h)
    }

    /// The moon's azimuth (from north, clockwise) and altitude in radians: low precision, to a degree or so.
    public static func moon(_ epochMs: Int64) -> (az: Double, alt: Double) {
        let r = Sun.r
        let d = Double(epochMs) / 86_400_000 + 2440587.5 - 2451545.0
        let e = 23.4397 * r
        let l0 = (218.316 + 13.176396 * d) * r
        let m = (134.963 + 13.064993 * d) * r
        let f = (93.272 + 13.229350 * d) * r
        let l = l0 + 6.289 * r * sin(m)
        let b = 5.128 * r * sin(f)
        let dec = asin(sin(b) * cos(e) + cos(b) * sin(e) * sin(l))
        let ra = atan2(sin(l) * cos(e) - tan(b) * sin(e), cos(l))
        let st = (280.16 + 360.9856235 * d) * r + 44.495 * r
        let hh = st - ra
        let phi = 42.51 * r
        let alt = asin(sin(phi) * sin(dec) + cos(phi) * cos(dec) * cos(hh))
        let az = atan2(-sin(hh), tan(dec) * cos(phi) - sin(phi) * cos(hh))
        return ((az + 2 * .pi).truncatingRemainder(dividingBy: 2 * .pi), alt)
    }
}
