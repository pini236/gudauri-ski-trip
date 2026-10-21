import Foundation

/// The orbit camera over the mountain (Android's map/Camera.kt, OrbitCamera.State). World: x east, y up (metres),
/// z south (the site's projected y). yaw 0 looks north; pitch is the angle above the horizon. A plain value: the view
/// moves it, the gestures and the framing compute the next one.
public struct Orbit: Sendable, Equatable {
    public var tx: Double, ty: Double, tz: Double
    public var dist: Double
    public var yaw: Double
    public var pitch: Double

    public init(tx: Double, ty: Double, tz: Double, dist: Double, yaw: Double, pitch: Double) {
        self.tx = tx; self.ty = ty; self.tz = tz; self.dist = dist; self.yaw = yaw; self.pitch = pitch
    }

    /// Android's starting view: the whole main side from the south-south-west.
    public static let start = Orbit(tx: 600, ty: 2300, tz: -500, dist: 11000, yaw: 0.35, pitch: 0.62)

    public static let fov = 40.0

    /// Where the eye is, before the lift that keeps it above a ridge.
    public var eye: SIMD3<Double> {
        let cp = cos(pitch)
        return SIMD3(tx + sin(yaw) * cp * dist, ty + sin(pitch) * dist, tz + cos(yaw) * cp * dist)
    }

    /// The eye kept at least 40 m above the snow under it (OrbitCamera.mvp's minEye).
    public func eye(above ground: (Double, Double) -> Double) -> SIMD3<Double> {
        var e = eye
        e.y = max(e.y, ground(e.x, e.z) + 40)
        return e
    }

    /// Near and far planes, as Android's.
    public var near: Double { min(max(dist * 0.02, 5), 150) }
    public var far: Double { dist * 5 + 30000 }

    /// Metres on the ground per screen point at the target.
    public func metresPerPoint(viewHeight h: Double) -> Double { 2 * dist * tan(Self.fov * .pi / 360) / max(h, 1) }

    /// Between two views: the target and the tilt straight, the distance on a log scale, the heading the short way.
    public static func lerp(_ a: Orbit, _ b: Orbit, _ k: Double) -> Orbit {
        let dy = Moves.wrap(b.yaw - a.yaw)
        return Orbit(tx: a.tx + (b.tx - a.tx) * k, ty: a.ty + (b.ty - a.ty) * k, tz: a.tz + (b.tz - a.tz) * k,
                     dist: a.dist * pow(b.dist / a.dist, k), yaw: a.yaw + dy * k, pitch: a.pitch + (b.pitch - a.pitch) * k)
    }

    /// Android's camera move easing (ease in-out cubic).
    public static func ease(_ k: Double) -> Double { k < 0.5 ? 4 * k * k * k : 1 - pow(-2 * k + 2, 3) / 2 }
}

/// A fling: the target slides on and slows (OrbitCamera.fling and step).
public struct Fling: Sendable {
    public var vx = 0.0, vz = 0.0
    public init() {}

    /// Starts from the finger's speed in points a second.
    public init(_ s: Orbit, vxPt: Double, vyPt: Double, viewHeight h: Double) {
        let k = s.metresPerPoint(viewHeight: h)
        let rx = cos(s.yaw), rz = -sin(s.yaw), fx = -sin(s.yaw), fz = -cos(s.yaw)
        let tiltK = 1 / max(sin(s.pitch), 0.35)
        vx = -rx * vxPt * k + fx * vyPt * k * tiltK
        vz = -rz * vxPt * k + fz * vyPt * k * tiltK
    }

    public var moving: Bool { vx != 0 || vz != 0 }

    /// Advances `s` by `dt` seconds; false when it has stopped.
    public mutating func step(_ s: inout Orbit, dt: Double) -> Bool {
        guard moving else { return false }
        s.tx += vx * dt; s.tz += vz * dt
        let damp = exp(-3.2 * dt)
        vx *= damp; vz *= damp
        if vx * vx + vz * vz < 4 { vx = 0; vz = 0 }
        return true
    }
}

/// The camera seen as a lens: screen point to ground and back (Android's Lens). Plain math, so the gestures and the
/// framing are tested here. It ignores the small lift that keeps the eye above a ridge, which is fine for gestures.
public struct Lens: Sendable {
    public let s: Orbit
    public let w: Double, h: Double
    public let eye: SIMD3<Double>
    let f: SIMD3<Double>, r: SIMD3<Double>, u: SIMD3<Double>
    let tanX: Double, tanY: Double

    public init(_ s: Orbit, w: Double, h: Double, fov: Double = Orbit.fov) {
        self.s = s; self.w = w; self.h = h
        eye = s.eye
        let f = Lens.norm(SIMD3(s.tx, s.ty, s.tz) - eye)
        let r = Lens.norm(SIMD3(-f.z, 0, f.x)) // forward × up
        self.f = f; self.r = r
        u = SIMD3(r.y * f.z - r.z * f.y, r.z * f.x - r.x * f.z, r.x * f.y - r.y * f.x)
        tanY = tan(fov / 2 * .pi / 180); tanX = tanY * w / h
    }

    static func norm(_ v: SIMD3<Double>) -> SIMD3<Double> {
        let l = max((v * v).sum().squareRoot(), 1e-9)
        return v / l
    }

    /// Unit direction of the ray through a screen point (y down).
    public func ray(_ px: Double, _ py: Double) -> SIMD3<Double> {
        let nx = (2 * px / w - 1) * tanX, ny = (1 - 2 * py / h) * tanY
        return Lens.norm(f + r * nx + u * ny)
    }

    /// Where the ray through a screen point meets the level plane at height `y`; nil above the horizon or too far.
    public func ground(_ px: Double, _ py: Double, y: Double? = nil, maxDist: Double? = nil) -> SIMD3<Double>? {
        let d = ray(px, py)
        if d.y > -1e-3 { return nil }
        let t = ((y ?? s.ty) - eye.y) / d.y
        if t <= 0 || t > (maxDist ?? s.dist * 8) { return nil }
        return eye + d * t
    }

    /// The snow under a screen point: march the ray over the terrain and refine where it meets it; nil on the sky.
    public func hit(_ px: Double, _ py: Double, _ terrain: (Double, Double) -> Double, maxDist: Double? = nil) -> SIMD3<Double>? {
        let d = ray(px, py), far = maxDist ?? s.dist * 6
        var prev = 0.0, t = 0.0, step = max(s.dist * 0.01, 8)
        func under(_ t: Double) -> Bool { eye.y + d.y * t <= terrain(eye.x + d.x * t, eye.z + d.z * t) }
        while t < far {
            t += step; step *= 1.04
            if under(t) {
                var lo = prev, hi = t
                for _ in 0..<24 { let m = (lo + hi) / 2; if under(m) { hi = m } else { lo = m } }
                return eye + d * hi
            }
            prev = t
        }
        return nil
    }

    /// The screen point of a world point, or nil when it is behind the camera.
    public func project(_ x: Double, _ y: Double, _ z: Double) -> (x: Double, y: Double)? {
        let v = SIMD3(x, y, z) - eye
        let zf = (v * f).sum()
        if zf <= 1 { return nil }
        let xr = (v * r).sum() / (zf * tanX), yu = (v * u).sum() / (zf * tanY)
        return ((xr + 1) / 2 * w, (1 - yu) / 2 * h)
    }
}

/// Camera moves for the gestures, as on phone maps: the ground under the fingers stays under the fingers (Moves.kt).
public enum Moves {
    public static let minDist = 250.0
    public static let maxDist = 32000.0
    /// Lowest tilt: almost level, so the low winter sun and the moon can come into view over the ridges.
    public static let minPitch = 0.035
    public static let maxPitch = 1.5

    /// Moves the camera sideways until the snow point `a` is under (px, py) again; a few rounds settle the height too.
    static func hold(_ s0: Orbit, _ w: Double, _ h: Double, _ a: SIMD3<Double>, _ px: Double, _ py: Double, _ ground: (Double, Double) -> Double) -> Orbit {
        var st = s0; st.ty = ground(s0.tx, s0.tz)
        for _ in 0..<5 {
            guard let b = Lens(st, w: w, h: h).ground(px, py, y: a.y, maxDist: st.dist * 40) else { return st }
            st.tx += a.x - b.x; st.tz += a.z - b.z; st.ty = ground(st.tx, st.tz)
        }
        return st
    }

    /// One finger (or the middle of two) moved from (x0, y0) to (x1, y1): the snow under it moves with it.
    public static func pan(_ s: Orbit, w: Double, h: Double, from x0: Double, _ y0: Double, to x1: Double, _ y1: Double, ground: (Double, Double) -> Double) -> Orbit {
        if let a = Lens(s, w: w, h: h).hit(x0, y0, ground), hypot(a.x - s.tx, a.z - s.tz) < s.dist * 3 {
            return hold(s, w, h, a, x1, y1, ground)
        }
        // on the sky, or the snow too far away to hold: move at the speed of the ground at the centre
        let k = 2 * s.dist * tan(20 * .pi / 180) / h
        let tiltK = 1 / max(sin(s.pitch), 0.35)
        let dx = x1 - x0, dy = y1 - y0
        let rx = cos(s.yaw), rz = -sin(s.yaw), fx = -sin(s.yaw), fz = -cos(s.yaw)
        var n = s
        n.tx = s.tx - rx * dx * k + fx * dy * k * tiltK
        n.tz = s.tz - rz * dx * k + fz * dy * k * tiltK
        n.ty = ground(n.tx, n.tz)
        return n
    }

    /// Zoom by `factor` (more than 1 is closer) towards (fx, fy): the snow there stays where it is on the screen.
    public static func zoom(_ s: Orbit, w: Double, h: Double, at fx: Double, _ fy: Double, factor: Double, ground: (Double, Double) -> Double) -> Orbit {
        var next = s; next.dist = min(max(s.dist / factor, minDist), maxDist)
        guard let a = Lens(s, w: w, h: h).hit(fx, fy, ground) else { next.ty = ground(next.tx, next.tz); return next }
        return hold(next, w, h, a, fx, fy, ground)
    }

    /// Turn the map by `angle` radians on the screen (positive clockwise, as the fingers turn, y down) around (fx, fy).
    public static func rotate(_ s: Orbit, w: Double, h: Double, at fx: Double, _ fy: Double, angle: Double, ground: (Double, Double) -> Double) -> Orbit {
        let g = Lens(s, w: w, h: h).hit(fx, fy, ground) ?? SIMD3(s.tx, s.ty, s.tz)
        let ox = s.tx - g.x, oz = s.tz - g.z
        let c = cos(angle), sn = sin(angle)
        var t = s
        t.tx = g.x + ox * c + oz * sn; t.tz = g.z - ox * sn + oz * c; t.yaw = s.yaw + angle
        return hold(t, w, h, g, fx, fy, ground)
    }

    /// Two fingers up or down together: tilt towards the horizon (up) or down to the map from above (down).
    public static func tilt(_ s: Orbit, h: Double, dy: Double) -> Orbit {
        var n = s; n.pitch = min(max(s.pitch + dy / h * 1.6, minPitch), maxPitch); return n
    }

    /// An angle difference wrapped to -π...π.
    public static func wrap(_ a: Double) -> Double {
        var d = a
        while d > .pi { d -= 2 * .pi }
        while d < -.pi { d += 2 * .pi }
        return d
    }

    /// True when two fingers moved by (dx0, dy0) and (dx1, dy1) mostly up or down together: the tilt gesture.
    public static func isTilt(_ dx0: Double, _ dy0: Double, _ dx1: Double, _ dy1: Double, spreadChange: Double, slop: Double) -> Bool {
        dy0 * dy1 > 0 && abs(dy0) > abs(dx0) * 1.5 && abs(dy1) > abs(dx1) * 1.5 && abs(spreadChange) < slop * 1.5
    }
}

/// Where the camera lands on a chosen run or lift: the whole line on the screen, in the free box above the panel,
/// looking uphill so the run's top is at the top (Android's Framing).
public enum Framing {
    /// Free screen area, as shares: the bars above, the panel below.
    public struct Box: Sendable {
        public var left = 0.08, right = 0.92, top = 0.16, bottom = 0.78
        public init(bottom: Double = 0.78) { self.bottom = bottom }
    }

    /// `path` runs from the top to the bottom.
    public static func fit(_ path: [SIMD3<Double>], w: Double, h: Double, ground: (Double, Double) -> Double, pitch: Double = 0.8, box: Box = Box()) -> Orbit {
        let n = path.count
        guard n >= 2 else {
            let p = path.first ?? SIMD3(0, 0, 0)
            return Orbit(tx: p.x, ty: ground(p.x, p.z), tz: p.z, dist: 3000, yaw: 0, pitch: pitch)
        }
        let xs = path.map(\.x), zs = path.map(\.z)
        let cx = (xs.min()! + xs.max()!) / 2, cz = (zs.min()! + zs.max()!) / 2
        // look uphill: from the bottom towards the top
        let yaw = atan2(-(path[0].x - path[n - 1].x), -(path[0].z - path[n - 1].z))
        var s = Orbit(tx: cx, ty: ground(cx, cz), tz: cz, dist: 3000, yaw: yaw, pitch: pitch)
        let stride = max(1, n / 120)
        for _ in 0..<3 {
            var lo = Moves.minDist, hi = Moves.maxDist
            for _ in 0..<28 {
                let mid = (lo * hi).squareRoot()
                var t = s; t.dist = mid
                if fits(t, path, stride, w, h, box) { hi = mid } else { lo = mid }
            }
            s.dist = hi
            guard let b = bounds(s, path, stride: stride, w: w, h: h) else { return s }
            let bx = (b.minX + b.maxX) / 2, by = (b.minY + b.maxY) / 2
            let mx = (box.left + box.right) / 2 * w, my = (box.top + box.bottom) / 2 * h
            s = Moves.pan(s, w: w, h: h, from: bx, by, to: mx, my, ground: ground)
        }
        return s
    }

    /// The line's box on the screen, or nil if part of it is behind the camera.
    public static func bounds(_ s: Orbit, _ path: [SIMD3<Double>], stride: Int, w: Double, h: Double) -> (minX: Double, maxX: Double, minY: Double, maxY: Double)? {
        let lens = Lens(s, w: w, h: h)
        var b = (minX: Double.infinity, maxX: -Double.infinity, minY: Double.infinity, maxY: -Double.infinity)
        var i = 0
        let n = path.count
        while i < n {
            guard let p = lens.project(path[i].x, path[i].y, path[i].z) else { return nil }
            b.minX = min(b.minX, p.x); b.maxX = max(b.maxX, p.x); b.minY = min(b.minY, p.y); b.maxY = max(b.maxY, p.y)
            i = i == n - 1 ? n : min(i + stride, n - 1)
        }
        return b
    }

    static func fits(_ s: Orbit, _ path: [SIMD3<Double>], _ stride: Int, _ w: Double, _ h: Double, _ box: Box) -> Bool {
        guard let b = bounds(s, path, stride: stride, w: w, h: h) else { return false }
        return b.minX >= box.left * w && b.maxX <= box.right * w && b.minY >= box.top * h && b.maxY <= box.bottom * h
    }
}

/// The camera flies down a run from the top: behind and above the line, looking ahead (Android's FlyDown).
public struct FlyDown: Sendable {
    let path: [SIMD3<Double>]
    let cum: [Double]
    public private(set) var s = 0.0
    let speed: Double
    var yaw = Double.nan
    /// Metres along, the run's length, the height and the slope (°) where the skier is.
    public private(set) var info: (along: Double, length: Double, height: Double, deg: Double)

    public init(_ path: [SIMD3<Double>]) {
        self.path = path
        var c = [0.0]
        for i in 1..<max(path.count, 1) { c.append(c[i - 1] + hypot(path[i].x - path[i - 1].x, path[i].z - path[i - 1].z)) }
        cum = c
        speed = min(max((c.last ?? 0) / 26, 40), 140) // the whole run in about 26 seconds
        info = (0, c.last ?? 0, path.first?.y ?? 0, 0)
    }

    public var length: Double { cum.last ?? 0 }
    public var progress: Double { length > 0 ? s / length : 0 }
    public var position: SIMD3<Double> { at(s) }

    func at(_ d: Double) -> SIMD3<Double> {
        let n = path.count
        guard n >= 2 else { return path.first ?? SIMD3(0, 0, 0) }
        var i = 0
        while i < n - 2 && cum[i + 1] < d { i += 1 }
        let seg = max(cum[i + 1] - cum[i], 0.01)
        let f = min(max((d - cum[i]) / seg, 0), 1)
        return path[i] + (path[i + 1] - path[i]) * f
    }

    /// One step of `dt` seconds; the camera to use, or nil at the bottom.
    public mutating func step(_ dt: Double) -> Orbit? {
        guard path.count >= 2 else { return nil }
        s += speed * dt
        if s >= length { return nil }
        let p = at(s), a = at(max(s - 60, 0)), b = at(min(s + 120, length))
        let target = atan2(-(b.x - a.x), -(b.z - a.z))
        if yaw.isNaN { yaw = target }
        yaw += Moves.wrap(target - yaw) * (1 - exp(-2.5 * dt))
        // the slope here, over 30 m of the run
        let a2 = at(max(s - 15, 0)), b2 = at(min(s + 15, length))
        let run = max(hypot(b2.x - a2.x, b2.z - a2.z), 1)
        info = (s, length, p.y - 4, atan(max(a2.y - b2.y, 0) / run) * 180 / .pi)
        return Orbit(tx: p.x, ty: p.y, tz: p.z, dist: 520, yaw: yaw, pitch: 0.48)
    }
}
