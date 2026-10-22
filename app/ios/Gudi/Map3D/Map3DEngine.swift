@preconcurrency import MetalKit
import SwiftUI
import simd
import GudiData
import GudiDomain

/// A label on the mountain (Android's MapLabel): where, what, how it looks, and what a tap on it opens.
struct Label3D {
    enum Kind { case peak, place, lift, run }
    let p: P3
    let text: String
    let kind: Kind
    let priority: Int
    /// A run's label: its key (hidden with the run by the filters, and alone while a run is chosen).
    var run: String? = nil
    /// A lift's label: its index in the data.
    var lift: Int? = nil
    var color: UInt32 = 0x13233A
}

/// The 3D map's state and its frame loop (Android's MapView and MapRenderer together, minus the drawing): the camera
/// and its moves, the scene prepared off the main thread, the chosen run, the flight down, and when to draw. Draws
/// only when something moves: the view is paused otherwise.
@MainActor
@Observable
final class Map3DEngine: NSObject {
    /// Bumped on every frame drawn, so the labels and the flight's bar follow the camera.
    private(set) var tick = 0
    private(set) var flying = false
    private(set) var ready = false

    let renderer: Renderer
    @ObservationIgnored weak var view: MTKView?
    @ObservationIgnored private(set) var scene: Scene3D?
    @ObservationIgnored private(set) var labels: [Label3D] = []
    @ObservationIgnored var cam = Orbit.start
    @ObservationIgnored private var fling = Fling()
    @ObservationIgnored private var anim: (from: Orbit, to: Orbit, t: Double, dur: Double)?
    @ObservationIgnored private var fly: FlyDown?
    @ObservationIgnored private var flyKey = ""
    @ObservationIgnored private var flyT0 = 0.0
    /// The first view is framed once the mountain and the view's size are known (unless a touch or a run came first).
    @ObservationIgnored private var opened = false
    @ObservationIgnored private var touched = false
    @ObservationIgnored private var lastTime: CFTimeInterval = 0
    @ObservationIgnored private var started = false

    // what to draw
    @ObservationIgnored private var selected: String?
    @ObservationIgnored private var hidden: Set<String> = []
    @ObservationIgnored private var hideLifts = false
    @ObservationIgnored private var status = StatusPaint()
    @ObservationIgnored private var marker: P3?
    @ObservationIgnored var still = false
    @ObservationIgnored private var light = Sky.at(Int64(Date().timeIntervalSince1970 * 1000))
    @ObservationIgnored private var lightMs: Int64 = 0
    @ObservationIgnored private var shadowMs: Int64?
    @ObservationIgnored private var reveal = 1.0
    @ObservationIgnored private var dim = 0.0
    @ObservationIgnored private var dimTarget = 0.0
    @ObservationIgnored private var hasSelection = false
    /// The line the camera frames (the chosen run, a lift), and the free part of the screen above the panel.
    @ObservationIgnored private var framed: [P3]?
    @ObservationIgnored private var freeBottom = Framing.Box().bottom
    /// The share of the screen the panel covers, and the top of the screen the map's bar, switch and compass take
    /// (deeper than Android's 0.16: the iPhone's status bar, title and controls), kept clear of framed lines and names.
    @ObservationIgnored private(set) var covered = 0.0
    static let freeTop = 0.38
    @ObservationIgnored private var chairsTask: Task<Void, Never>?

    /// The view's size in points, and the last frame's matrix and eye (for the labels and the taps).
    @ObservationIgnored var size = CGSize(width: 390, height: 700)
    @ObservationIgnored private(set) var mvp = matrix_identity_double4x4
    @ObservationIgnored private(set) var eye = SIMD3<Double>(0, 0, 0)
    /// The labels the overlay placed in the last frame, for a tap on a name (a run's or a lift's).
    @ObservationIgnored var placed: [(rect: CGRect, run: String?, lift: Int?)] = []

    @ObservationIgnored var onRun: ((String) -> Void)?
    @ObservationIgnored var onLift: ((Lift) -> Void)?
    @ObservationIgnored var onNone: (() -> Void)?
    @ObservationIgnored var onFlying: ((Bool) -> Void)?

    init(renderer: Renderer) {
        self.renderer = renderer
        super.init()
    }

    // MARK: inputs

    /// The mountain: built once, off the main thread (the mesh, the lines, the shadows and the drape).
    func load(terrain t: Terrain, runs: RunsAndLifts, relief r: Relief) {
        guard scene == nil, !started else { return }
        started = true
        let dir = light.dir
        Task {
            let made = await Task.detached(priority: .userInitiated) { () -> (Scene3D, TerrainMesh, [Float], Relief, DrapePicture?) in
                let s = Scene3D(terrain: t, runs: runs)
                let m = TerrainMesh.build(t)
                let sh = Sun.shadows(t, toward: dir)
                let d = r
                return (s, m, sh, d, DrapePicture.paint(d))
            }.value
            let (s, mesh, shadow, drape, pic) = made
            renderer.upload(s, mesh: mesh, shadow: shadow)
            shadowMs = lightMs
            if let pic {
                renderer.setDrape(pic.rgba, w: pic.w, h: pic.h,
                                  box: SIMD4(Float(drape.x0), Float(drape.y0), Float(1 / (drape.x1 - drape.x0)), Float(1 / (drape.y1 - drape.y0))))
            }
            scene = s
            labels = Self.buildLabels(s, drape)
            ready = true
            if let k = selected { selected = nil; select(k) }
            requestDraw()
        }
    }

    func setInputs(selected key: String?, hidden h: Set<String>, hideLifts hl: Bool, status st: StatusPaint, marker mk: (x: Double, y: Double)?) {
        var changed = false
        if h != hidden { hidden = h; changed = true }
        if hl != hideLifts { hideLifts = hl; changed = true }
        if st != status { status = st; changed = true }
        let m = mk.flatMap { p in scene.map { P3(p.x, $0.elev(p.x, p.y), p.y) } }
        if m != marker { marker = m; changed = true }
        if key != selected { select(key) }
        if changed { requestDraw(); scheduleChairs() }
    }

    /// The light at this moment in Gudauri (the app's day and night: now, noon or 22:00); the shadows follow it.
    func setLight(_ date: Date) {
        let ms = Int64(date.timeIntervalSince1970 * 1000)
        guard abs(ms - lightMs) > 30_000 || lightMs == 0 else { return }
        lightMs = ms
        light = Sky.at(ms)
        requestDraw()
        // the shadows again when the sun has moved (every few minutes), off the main thread
        guard let s = scene, shadowMs.map({ abs(ms - $0) > 4 * 60_000 }) ?? true else { return }
        shadowMs = ms
        let t = s.terrain, dir = light.dir
        Task {
            let sh = await Task.detached(priority: .utility) { Sun.shadows(t, toward: dir) }.value
            renderer.setShadow(sh); requestDraw()
        }
    }

    /// The panel covers the screen below `bottom` (a share of the height): the line in view moves above it.
    func setCovered(_ covered: Double) {
        self.covered = covered
        let b = min(max(1 - covered, 0.5), Framing.Box().bottom)
        guard abs(b - freeBottom) >= 0.02 else { return }
        freeBottom = b
        if let path = framed { frame(path, seconds: 0.6) }
    }

    // MARK: selection and camera moves

    func select(_ key: String?) {
        if flying && key != flyKey { stopFly() }
        selected = key
        guard let s = scene else { return }
        guard let key else {
            framed = nil; hasSelection = false
            renderer.select(casing: nil, paint: nil, ground: nil)
            dimTarget = 0; if still { dim = 0 }
            requestDraw(); return
        }
        Task {
            let made = await Task.detached(priority: .userInitiated) { () -> (Ribbon, Ribbon, SlopeLayer, [P3])? in
                guard let p = s.paint(key) else { return nil }
                let casing = Ribbon.build(p.lines, colour: { _, _ in SIMD4(1, 1, 1, 1) }, progress: { li, i in p.progress[li][i] })
                let paint = Ribbon.build(p.lines, colour: { li, i in p.colours[li][i] }, progress: { li, i in p.progress[li][i] })
                return (casing, paint, s.slopeLayer(p.lines), p.lines.flatMap { $0 })
            }.value
            guard let made, selected == key else { return }
            renderer.select(casing: made.0, paint: made.1, ground: made.2)
            hasSelection = true
            reveal = still ? 1 : 0; dimTarget = 1; if still { dim = 1 }
            // land on the whole run, above the panel, looking uphill so its top is at the top
            frame(made.3, seconds: 1.1)
        }
    }

    /// A lift's whole line, as on a chosen run (the meeting point's "see it on the run map", a tap on a lift).
    func showLift(_ l: Lift) {
        guard let s = scene, let i = s.runs.lifts.firstIndex(where: { $0.id == l.id }), s.cables[i].count >= 2 else { return }
        if selected != nil { select(nil) }
        frame(s.cables[i], seconds: 1.1)
    }

    private func frame(_ path: [P3], seconds: Double) {
        framed = path
        guard let s = scene else { return }
        let w = size.width, h = size.height, box = Framing.Box(top: Self.freeTop, bottom: freeBottom)
        Task {
            let to = await Task.detached(priority: .userInitiated) { Framing.fit(path, w: w, h: h, ground: s.elev, box: box) }.value
            if framed == path { animate(to, seconds: seconds) }
        }
    }

    func animate(_ to: Orbit, seconds: Double = 1.1) {
        fling = Fling()
        if still { anim = nil; cam = to; requestDraw(); return }
        anim = (cam, to, 0, seconds)
        requestDraw()
    }

    /// The compass (the site's): the camera turns back to look north, around the same spot.
    func north() { var to = cam; to.yaw = 0; animate(to, seconds: 0.6) }

    /// The whole main side, as when the map opens.
    func frameMain() { fitMain(seconds: 1.1) }

    /// The free band for the whole area: below the title and the view switch, above the bottom keys.
    static let areaTop = 0.32

    /// All the runs south of Kobi Pass (the site's fitMain), centred in the free band, at the first view's heading and tilt.
    private func fitMain(seconds: Double) {
        framed = nil
        guard let s = scene else { return }
        let w = size.width, h = size.height, box = Framing.Box(top: Self.areaTop, bottom: freeBottom)
        Task {
            let to = await Task.detached(priority: .userInitiated) { Framing.fitArea(s.mainArea(), w: w, h: h, ground: s.elev, box: box) }.value
            guard framed == nil, selected == nil, !flying, seconds > 0 || !touched else { return }
            if seconds == 0 { anim = nil; cam = to; requestDraw() } else { animate(to, seconds: seconds) }
        }
    }

    /// The camera to a point (metres), keeping the heading, not farther than 2.6 km.
    func lookAt(x: Double, y: Double) {
        guard let s = scene else { return }
        var to = cam
        to.tx = x; to.tz = y; to.ty = s.elev(x, y); to.dist = min(cam.dist, 2600)
        animate(to, seconds: 0.6)
    }

    func flyDown() {
        guard let s = scene, let key = selected, let path = s.longest(key), path.count >= 2 else { return }
        if flying { flyEnded(done: false) }
        fly = FlyDown(path); flyKey = key; flyT0 = CACurrentMediaTime(); anim = nil; fling = Fling()
        flying = true; onFlying?(true)
        Telemetry.shared.event("run_fly_start", ["run": key])
        requestDraw()
    }

    /// Stopped by the person (the bar's key, another run, the panel closed, a touch on the map).
    func stopFly() {
        guard fly != nil || flying else { return }
        fly = nil
        flyEnded(done: false)
        requestDraw()
    }

    /// run_fly_end, as on the site and Android: `completed` when it reached the bottom.
    private func flyEnded(done: Bool) {
        guard flying else { return }
        flying = false
        Telemetry.shared.event("run_fly_end", ["run": flyKey, "completed": done,
                                               "seconds": Int((CACurrentMediaTime() - flyT0 + 0.5).rounded(.down))])
        onFlying?(false)
    }

    /// Metres along, the height and the slope where the skier is; nil when not flying.
    var flyInfo: (d: Double, h: Double, deg: Double)? { fly.map { ($0.info.along, $0.info.height, $0.info.deg) } }

    // MARK: gestures (from the view)

    var ground: (Double, Double) -> Double {
        let s = scene
        return { x, z in s?.elev(x, z) ?? 2300 }
    }

    func touchDown() {
        touched = true
        if flying { stopFly() }
        anim = nil; fling = Fling()
    }

    func move(_ f: (Orbit) -> Orbit) { cam = f(cam); requestDraw() }

    func startFling(vx: Double, vy: Double) {
        fling = Fling(cam, vxPt: vx, vyPt: vy, viewHeight: size.height)
        requestDraw()
    }

    /// A tap: a label first, then the run or lift whose line passes closest, within 28 points; nothing closes the panel.
    func pick(_ pt: CGPoint) {
        guard let s = scene else { return }
        if let l = placed.first(where: { $0.rect.insetBy(dx: -4, dy: -4).contains(pt) }) {
            if let r = l.run { onRun?(r); return }
            if let i = l.lift { onLift?(s.runs.lifts[i]); return }
        }
        var bestD = 28.0, best: String?, lift = -1
        for p in s.runs.pistes where !hidden.contains(p.key) {
            for l in s.draped[p.key] ?? [] {
                for i in Swift.stride(from: 0, to: l.count, by: 2) {
                    guard let q = screen(l[i]) else { continue }
                    let d = hypot(q.x - pt.x, q.y - pt.y)
                    if d < bestD { bestD = d; best = p.key }
                }
            }
        }
        if !hideLifts {
            for (li, l) in s.cables.enumerated() where !s.runs.lifts[li].id.isEmpty && l.count >= 2 {
                for i in 0..<(l.count - 1) {
                    for k in 0...8 {
                        let f = Double(k) / 8
                        guard let q = screen(l[i] + (l[i + 1] - l[i]) * f) else { continue }
                        let d = hypot(q.x - pt.x, q.y - pt.y)
                        if d < bestD { bestD = d; lift = li; best = nil }
                    }
                }
            }
        }
        if lift >= 0 { onLift?(s.runs.lifts[lift]); return }
        if let best, best != selected { onRun?(best) } else if best == nil, selected != nil { onNone?() }
    }

    /// A world point on the screen (points), or nil behind the camera.
    func screen(_ p: P3) -> CGPoint? {
        let c = mvp * SIMD4(p.x, p.y, p.z, 1)
        guard c.w > 0 else { return nil }
        return CGPoint(x: (c.x / c.w * 0.5 + 0.5) * size.width, y: (1 - (c.y / c.w * 0.5 + 0.5)) * size.height)
    }

    /// Not behind a ridge, as the site's labels: steps along the line from the eye, hidden where the mountain rises
    /// more than 8 m above it.
    func visible(_ p: P3) -> Bool {
        guard let s = scene else { return true }
        let d = p - eye
        for i in 4..<40 {
            let t = Double(i) / 40
            if s.elev(eye.x + d.x * t, eye.z + d.z * t) > eye.y + d.y * t + 8 { return false }
        }
        return true
    }

    /// The snow's height there (metres), or 0 before the mountain is read.
    func ground(_ x: Double, _ y: Double) -> Double { scene?.elev(x, y) ?? 0 }

    var chosen: String? { selected }
    var hiddenRuns: Set<String> { hidden }
    var liftsOff: Bool { hideLifts }

    // MARK: the frame loop

    func requestDraw() {
        guard let v = view else { return }
        if v.isPaused { v.setNeedsDisplay() }
    }

    private func run(_ on: Bool) {
        guard let v = view, v.isPaused == on else { return }
        v.isPaused = !on
        v.enableSetNeedsDisplay = !on
    }

    /// The chairs at about ten frames a second, as the site's, when nothing else moves.
    private func scheduleChairs() {
        chairsTask?.cancel()
        guard !still, !hideLifts, !status.openLifts.isEmpty else { return }
        chairsTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(for: .milliseconds(100))
                guard let self, !Task.isCancelled else { return }
                if self.view?.isPaused == true { self.requestDraw() }
            }
        }
    }

    func render(_ v: MTKView) {
        let now = CACurrentMediaTime()
        let dt = lastTime == 0 ? 0.016 : min(max(now - lastTime, 0), 0.1)
        lastTime = now
        var moving = false
        if var f = fly {
            if let next = f.step(dt) { cam = next; fly = f; moving = true } else { fly = nil; flyEnded(done: true) }
        }
        if let a = anim {
            let t = a.t + dt
            let k = min(max(t / a.dur, 0), 1)
            cam = Orbit.lerp(a.from, a.to, Orbit.ease(k))
            anim = k >= 1 ? nil : (a.from, a.to, t, a.dur)
            moving = moving || anim != nil
        }
        if fling.step(&cam, dt: dt) { moving = true }
        if let s = scene {
            let t = s.terrain
            cam.tx = min(max(cam.tx, t.x0), t.x1); cam.tz = min(max(cam.tz, t.y0), t.y1)
            cam.ty = t.elev(cam.tx, cam.tz)
        }
        if hasSelection && reveal < 1 { reveal = min(1, reveal + dt / 1.2); moving = true }
        if dim != dimTarget {
            dim += (dimTarget - dim) * (1 - exp(-6 * dt))
            if abs(dim - dimTarget) < 0.01 { dim = dimTarget }
            moving = true
        }

        let w = max(Double(v.bounds.width), 1), h = max(Double(v.bounds.height), 1)
        size = CGSize(width: w, height: h)
        if !opened, scene != nil, w > 50, h > 50 {
            opened = true
            if selected == nil && framed == nil && !touched { fitMain(seconds: 0) }
        }
        let e = scene.map { s in cam.eye(above: s.elev) } ?? cam.eye
        eye = e
        mvp = Self.perspective(fovy: Orbit.fov * .pi / 180, aspect: w / h, near: cam.near, far: cam.far)
            * Self.lookAt(eye: e, center: SIMD3(cam.tx, cam.ty, cam.tz), up: SIMD3(0, 1, 0))
        let l = light
        var f = FrameU()
        f.mvp = Self.float(mvp)
        f.inv = Self.float(mvp.inverse)
        f.eye = SIMD4(Float(e.x), Float(e.y), Float(e.z), 1)
        func v4(_ a: SIMD3<Double>, _ w: Double = 0) -> SIMD4<Float> { SIMD4(Float(a.x), Float(a.y), Float(a.z), Float(w)) }
        f.sun = v4(l.dir); f.sunCol = v4(l.color); f.ambTop = v4(l.ambTop); f.ambGround = v4(l.ambGround)
        f.fog = SIMD4(Float(cam.dist * 1.2 + 6000), Float(cam.dist * 4 + 30000), 0, 0)
        f.fogCol = v4(l.skyBottom); f.skyTop = v4(l.skyTop); f.skyBottom = v4(l.skyBottom)
        f.sunDir = v4(l.sunDir, l.sunUp ? 1 : 0); f.moonDir = v4(l.moonDir, l.moonUp ? 1 : 0)
        f.glowCol = v4(l.glowColor, l.glow)
        f.misc = SIMD4(l.dark ? 1 : 0, Float(dim), 0, 0)
        let scale = Float(v.contentScaleFactor)
        f.res = SIMD4(Float(v.drawableSize.width), Float(v.drawableSize.height), scale, 0)
        var inp = Renderer.Inputs(frame: f, hidden: hidden, hideLifts: hideLifts, status: status)
        inp.dim = Float(dim); inp.reveal = Float(reveal)
        inp.metresPerPoint = Float(cam.metresPerPoint(viewHeight: h))
        inp.dot = fly?.position ?? marker
        inp.pulse = fly != nil ? Float(0.5 + 0.5 * sin(now * 4)) : 0.5
        inp.chairsAt = still ? nil : now.truncatingRemainder(dividingBy: 3600)
        renderer.draw(v, inp)
        tick &+= 1
        run(moving || fly != nil)
    }

    // MARK: matrices

    static func perspective(fovy: Double, aspect: Double, near: Double, far: Double) -> simd_double4x4 {
        let y = 1 / tan(fovy / 2), x = y / aspect, z = far / (near - far)
        return simd_double4x4(columns: (SIMD4(x, 0, 0, 0), SIMD4(0, y, 0, 0), SIMD4(0, 0, z, -1), SIMD4(0, 0, z * near, 0)))
    }

    static func lookAt(eye: SIMD3<Double>, center: SIMD3<Double>, up: SIMD3<Double>) -> simd_double4x4 {
        let f = simd_normalize(center - eye), s = simd_normalize(simd_cross(f, up)), u = simd_cross(s, f)
        return simd_double4x4(columns: (SIMD4(s.x, u.x, -f.x, 0), SIMD4(s.y, u.y, -f.y, 0), SIMD4(s.z, u.z, -f.z, 0),
                                        SIMD4(-simd_dot(s, eye), -simd_dot(u, eye), simd_dot(f, eye), 1)))
    }

    static func float(_ m: simd_double4x4) -> simd_float4x4 {
        simd_float4x4(columns: (SIMD4<Float>(m.columns.0), SIMD4<Float>(m.columns.1), SIMD4<Float>(m.columns.2), SIMD4<Float>(m.columns.3)))
    }

    // MARK: labels

    /// Peaks, the named lifts at mid-cable, Gudauri and Kobi, and the named runs 45% down their longest line.
    static func buildLabels(_ s: Scene3D, _ d: Relief) -> [Label3D] {
        func iso(_ n: String) -> String { "\u{2068}\(n)\u{2069}" }
        var out: [Label3D] = []
        for p in s.terrain.peaks {
            out.append(Label3D(p: P3(p.x, s.elev(p.x, p.y) + 30, p.y), text: S.appPeakLabel(name: iso(p.name), ele: Fmt.n(p.ele)), kind: .peak, priority: 100))
        }
        for (i, l) in s.runs.lifts.enumerated() where !l.name.trimmingCharacters(in: .whitespaces).isEmpty {
            guard let m = Scene3D.middle(s.cables[i]) else { continue }
            out.append(Label3D(p: m + P3(0, 12, 0), text: S.appLiftLabel(name: iso(l.name)), kind: .lift, priority: 45, lift: i))
        }
        for pl in d.places where pl.name == "Gudauri" || pl.name == "Kobi" {
            out.append(Label3D(p: P3(pl.x, s.elev(pl.x, pl.y) + 10, pl.y), text: pl.name == "Gudauri" ? S.mapPlaceGudauri : iso(pl.name), kind: .place, priority: 60))
        }
        for p in s.runs.pistes where p.named {
            guard let line = (s.draped[p.key] ?? []).max(by: { $0.count < $1.count }), !line.isEmpty else { continue }
            let k = min(Int(Double(line.count) * 0.45), line.count - 1)
            let col: UInt32 = switch p.color { case "green": Palette.day.green; case "blue": Palette.day.blue; case "red": Palette.day.red; default: Palette.day.black }
            out.append(Label3D(p: line[k] + P3(0, 8, 0), text: iso(p.key == "Firni ?" ? "Firni (1/2?)" : p.key), kind: .run, priority: 40, run: p.key, color: col))
        }
        return out.sorted { $0.priority > $1.priority }
    }
}

extension Map3DEngine: @preconcurrency MTKViewDelegate {
    func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) { requestDraw() }
    func draw(in view: MTKView) { render(view) }
}

/// What lies on the snow, painted once into a picture the terrain shader lays over the mountain (Android's Drape.kt):
/// the contours every 100 m (stronger every 500 m), the village, rivers, lakes and roads. Premultiplied RGBA, row 0 at
/// the north edge of the model.
struct DrapePicture: Sendable {
    let rgba: [UInt8]
    let w: Int, h: Int

    /// The site's 2048, about 7 m a pixel on this mountain.
    static let width = 2048

    static func paint(_ d: Relief) -> DrapePicture? {
        let mw = d.x1 - d.x0, mh = d.y1 - d.y0
        let tw = width, th = Int((Double(width) * mh / mw).rounded())
        var px = [UInt8](repeating: 0, count: tw * th * 4)
        let ok: Bool = px.withUnsafeMutableBytes { buf in
            guard let c = CGContext(data: buf.baseAddress, width: tw, height: th, bitsPerComponent: 8, bytesPerRow: tw * 4,
                                    space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return false }
            let s = Double(tw) / mw
            // metres to pixels, row 0 (the top of the picture) at the north edge
            c.translateBy(x: 0, y: CGFloat(th)); c.scaleBy(x: s, y: -s); c.translateBy(x: -d.x0, y: -d.y0)
            c.setLineJoin(.round); c.setLineCap(.round)
            func path(_ lines: [[Double]], closed: Bool) -> CGPath {
                let p = CGMutablePath()
                for l in lines where l.count >= 4 {
                    p.move(to: CGPoint(x: l[0], y: l[1]))
                    var i = 2
                    while i + 1 < l.count { p.addLine(to: CGPoint(x: l[i], y: l[i + 1])); i += 2 }
                    if closed { p.closeSubpath() }
                }
                return p
            }
            func rgba(_ r: Int, _ g: Int, _ b: Int, _ a: Int = 255) -> CGColor {
                CGColor(srgbRed: CGFloat(r) / 255, green: CGFloat(g) / 255, blue: CGFloat(b) / 255, alpha: CGFloat(a) / 255)
            }
            func stroke(_ p: CGPath, _ col: CGColor, _ metres: Double) { c.addPath(p); c.setStrokeColor(col); c.setLineWidth(metres); c.strokePath() }
            func fill(_ p: CGPath, _ col: CGColor) { c.addPath(p); c.setFillColor(col); c.fillPath() }
            stroke(path(d.c100, closed: false), rgba(70, 90, 115, 36), 3.2)
            stroke(path(d.c500, closed: false), rgba(70, 90, 115, 77), 5.2)
            fill(path(d.village, closed: true), rgba(214, 203, 190, 191))
            stroke(path(d.rivers, closed: false), rgba(120, 160, 200, 179), max(1.2 / s, 6))
            let w = Palette.day.water
            let water = path(d.water, closed: true)
            fill(water, rgba(Int((w >> 16) & 255), Int((w >> 8) & 255), Int(w & 255)))
            stroke(water, rgba(0x6F, 0x9C, 0xC4), 1 / s)
            // the roads, small ones first so the main road is on top
            let rw = [14.0, 10, 7, 6, 4]
            for k in stride(from: 4, through: 0, by: -1) {
                stroke(path(d.roads[k], closed: false), k == 0 ? rgba(0x8A, 0x7F, 0x76) : rgba(140, 128, 118, 217), max(1 / s, rw[k]))
            }
            return true
        }
        return ok ? DrapePicture(rgba: px, w: tw, h: th) : nil
    }
}
