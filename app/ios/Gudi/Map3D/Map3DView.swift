@preconcurrency import MetalKit
import SwiftUI
import GudiData
import GudiDomain

/// What the map screen asks of the 3D map: the compass, the flight down a run, and moving the camera.
@MainActor
final class Map3DController {
    private weak var engine: Map3DEngine?
    init(_ e: Map3DEngine) { engine = e }

    /// The camera turns back to look north (the site's compass).
    func north() { engine?.north() }
    /// Flies down the chosen run's longest line from its top (6.4.1); any touch on the map stops it.
    func flyDown() { engine?.flyDown() }
    func stopFly() { engine?.stopFly() }
    /// The camera to a point (metres in the site's projection), keeping the heading.
    func lookAt(x: Double, y: Double) { engine?.lookAt(x: x, y: y) }
    /// The whole main side, as when the map opens.
    func frameMain() { engine?.frameMain() }
    /// While flying: metres along the run, the height and the slope (°) where the skier is.
    func flyInfo() -> (d: Double, h: Double, deg: Double)? { engine?.flyInfo }
    var flying: Bool { engine?.flying ?? false }
}

/// The trail map in 3D (13.3, A-30), Android's map/MapView.kt and MapRenderer.kt in Metal: the mountain lit by the
/// real sun or moon of the moment, with its shadows, the sky, the contours, village, roads and water on the snow,
/// every run and lift, the names that are not behind a ridge, the chosen run painted from the top in slope colours with
/// the ground around it, the lift status, and the flight down a run. One finger pans, two fingers pinch, turn and tilt,
/// a double tap zooms in, a tap picks a run or a lift.
///
/// Where Metal is missing (`available` false) the map screen shows the map from above instead and hides the switch,
/// as the site falls back to its top view when three.js does not load.
struct Map3DView: View {
    let model: MapModel
    /// The lift status (S1): closed lifts and runs grey and dashed, chairs on the open lifts; only when fresh.
    let status: LiftStatus
    /// "Only what's open for me": the closed runs fade.
    let forMe: Bool
    /// The moment the mountain is lit as (the app's day and night: now, noon or 22:00 in Gudauri).
    let lightTime: Date
    /// The share of the screen's height the panel covers, so a framed run or lift sits above it.
    let bottom: Double
    @Binding var controller: Map3DController?
    let onRun: (String) -> Void
    let onLift: (Lift) -> Void
    let onFlying: (Bool) -> Void

    @Environment(\.accessibilityReduceMotion) private var still
    @Environment(\.ski) private var ski
    @State private var engine: Map3DEngine?

    /// One renderer for the app: its pipelines are built once; nil when this device has no Metal.
    @MainActor static let renderer: Renderer? = Renderer()
    @MainActor static var available: Bool { renderer != nil }

    var body: some View {
        GeometryReader { g in
            ZStack(alignment: .bottom) {
                if let engine {
                    MountainView(engine: engine).ignoresSafeArea()
                    Labels3D(engine: engine).allowsHitTesting(false).ignoresSafeArea()
                    if engine.flying, let p = model.selected {
                        FlyBar(engine: engine, name: runName(p))
                            .padding(.bottom, g.size.height * bottom + 8)
                    }
                }
            }
        }
        .onAppear(perform: start)
        .onChange(of: model.terrain != nil) { load() }
        .onChange(of: inputsKey) { push() }
        .onChange(of: lightTime) { engine?.setLight(qaTime ?? lightTime) }
        .onChange(of: bottom) { engine?.setCovered(bottom) }
        .onChange(of: model.lift?.id) { if let l = model.lift { engine?.showLift(l) } }
        .onChange(of: still) { engine?.still = still }
    }

    private func start() {
        guard engine == nil, let r = Self.renderer else { return }
        let e = Map3DEngine(renderer: r)
        e.still = still
        e.onRun = onRun
        e.onLift = onLift
        e.onNone = { model.select(nil) }
        e.onFlying = onFlying
        e.setLight(qaTime ?? lightTime)
        e.setCovered(bottom)
        engine = e
        controller = Map3DController(e)
        load()
        push()
    }

    private func load() {
        guard let e = engine, let t = model.terrain else { return }
        e.load(terrain: t, runs: model.runs)
    }

    /// Everything the mountain shows, as one value to watch.
    private var inputsKey: String {
        let m = model.marker.map { "\(Int($0.x)),\(Int($0.y))" } ?? "-"
        return "\(model.selected?.key ?? "-")|\(model.hiddenKeys.sorted())|\(model.hideLifts)|\(m)|\(status.fresh)|\(status.report?.updated ?? 0)|\(forMe)|\(still)"
    }

    private func push() {
        guard let e = engine else { return }
        var p = StatusPaint(forMe: forMe)
        if status.fresh {
            p.closedRuns = Set(model.runs.pistes.filter { status.runOpen($0) == false }.map(\.key))
            for l in model.runs.lifts where !l.name.isEmpty {
                switch status.isOpen(l.name) {
                case false?: p.closedLifts.insert(l.name)
                case true?: if !still { p.openLifts.insert(l.name) }
                case nil: break
                }
            }
        }
        e.setInputs(selected: model.selected?.key, hidden: model.hiddenKeys, hideLifts: model.hideLifts, status: p, marker: model.marker)
    }

    /// The screenshots' pinned hour in Gudauri (`qa.time`, "17:10"), today.
    private var qaTime: Date? {
        guard let s = UserDefaults.standard.string(forKey: "qa.time") else { return nil }
        let hm = s.split(separator: ":").compactMap { Double($0) }
        guard hm.count == 2 else { return nil }
        let hour = 3_600_000.0
        let local = Date().timeIntervalSince1970 * 1000 + 4 * hour
        let dayStart = (local / (24 * hour)).rounded(.down) * 24 * hour - 4 * hour
        return Date(timeIntervalSince1970: (dayStart + (hm[0] + hm[1] / 60) * hour) / 1000)
    }
}

/// The Metal view in SwiftUI.
private struct MountainView: UIViewRepresentable {
    let engine: Map3DEngine

    func makeUIView(context: Context) -> MapMTKView {
        let v = MapMTKView(frame: .zero, device: engine.renderer.device)
        v.engine = engine
        v.colorPixelFormat = .bgra8Unorm
        v.depthStencilPixelFormat = .depth32Float
        v.sampleCount = engine.renderer.samples
        v.preferredFramesPerSecond = 60
        v.isPaused = true
        v.enableSetNeedsDisplay = true
        v.isMultipleTouchEnabled = true
        v.delegate = engine
        v.isAccessibilityElement = true
        v.accessibilityLabel = S.mapMapAria
        engine.view = v
        return v
    }

    func updateUIView(_ v: MapMTKView, context: Context) {}
}

/// The map's gestures, as Android's MapView: one finger pans and flings; two fingers pan, pinch to zoom towards
/// them, and turn the map once the turn passes a threshold (so a pinch does not turn it by accident); two fingers up or
/// down together tilt instead. A double tap zooms in on the spot, a two-finger tap zooms out. A tap picks.
final class MapMTKView: MTKView {
    weak var engine: Map3DEngine?
    private static let turnThreshold = 0.2 // about 11° of finger turn before the map turns
    private static let slop: CGFloat = 8
    private var active: [UITouch] = []
    private var last = CGPoint.zero, down = CGPoint.zero
    private var downT: TimeInterval = 0
    private var moved = false, multi = false
    private enum Two { case undecided, transform, tilt }
    private var two = Two.undecided
    private var start0 = CGPoint.zero, start1 = CGPoint.zero, startGap: CGFloat = 0
    private var lastMid = CGPoint.zero, lastGap: CGFloat = 0, lastAngle: CGFloat = 0
    private var turned = 0.0, turning = false, twoDownT: TimeInterval = 0
    private var lastTapT: TimeInterval = 0, lastTap = CGPoint.zero
    private var pendingPick: Task<Void, Never>?
    private var track: [(p: CGPoint, t: TimeInterval)] = []

    private var w: Double { Double(bounds.width) }
    private var h: Double { Double(bounds.height) }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let e = engine else { return }
        for t in touches where !active.contains(t) { active.append(t) }
        if active.count == 1, let t = active.first {
            e.touchDown()
            let p = t.location(in: self)
            last = p; down = p; downT = t.timestamp; moved = false; multi = false
            track = [(p, t.timestamp)]
        } else if active.count >= 2 {
            multi = true; moved = true; two = .undecided; turned = 0; turning = false; twoDownT = event?.timestamp ?? 0
            twoFrom(active[0].location(in: self), active[1].location(in: self))
        }
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let e = engine, let first = active.first else { return }
        if active.count >= 2 { twoFingers(e, active[0].location(in: self), active[1].location(in: self)); return }
        let p = first.location(in: self)
        track.append((p, first.timestamp)); if track.count > 8 { track.removeFirst() }
        if !moved && hypot(p.x - down.x, p.y - down.y) > Self.slop { moved = true; last = down } // the ground catches up with the finger
        if moved {
            let a = last, g = e.ground, w = w, h = h
            e.move { Moves.pan($0, w: w, h: h, from: a.x, a.y, to: p.x, p.y, ground: g) }
        }
        last = p
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) { end(touches, event, cancelled: false) }
    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) { end(touches, event, cancelled: true) }

    private func end(_ touches: Set<UITouch>, _ event: UIEvent?, cancelled: Bool) {
        guard let e = engine else { active.removeAll { touches.contains($0) }; return }
        let before = active.count
        active.removeAll { touches.contains($0) }
        let now = event?.timestamp ?? 0
        if before >= 2 && !cancelled && two == .undecided && now - twoDownT < 0.3 {
            // a two-finger tap: zoom out around the middle of the fingers
            let m = lastMid, g = e.ground
            e.animate(Moves.zoom(e.cam, w: w, h: h, at: m.x, m.y, factor: 0.5, ground: g), seconds: 0.35)
            two = .transform
        }
        if active.count >= 2 { twoFrom(active[0].location(in: self), active[1].location(in: self)); return }
        if let t = active.first { last = t.location(in: self); return }
        guard !cancelled else { return }
        if !moved && !multi && now - downT < 0.3 { tap(down, now, e) }
        else if !multi, let a = track.first, let b = track.last, b.t - a.t > 0.01 {
            let vx = (b.p.x - a.p.x) / (b.t - a.t), vy = (b.p.y - a.p.y) / (b.t - a.t)
            if hypot(vx, vy) > 300, now - b.t < 0.08 { e.startFling(vx: vx, vy: vy) }
        }
        e.requestDraw()
    }

    private func twoFrom(_ a: CGPoint, _ b: CGPoint) {
        start0 = a; start1 = b
        startGap = hypot(b.x - a.x, b.y - a.y)
        lastMid = CGPoint(x: (a.x + b.x) / 2, y: (a.y + b.y) / 2)
        lastGap = startGap; lastAngle = atan2(b.y - a.y, b.x - a.x)
    }

    private func twoFingers(_ e: Map3DEngine, _ a: CGPoint, _ b: CGPoint) {
        let mid = CGPoint(x: (a.x + b.x) / 2, y: (a.y + b.y) / 2)
        let gap = hypot(b.x - a.x, b.y - a.y), angle = atan2(b.y - a.y, b.x - a.x)
        if two == .undecided {
            let d0 = hypot(a.x - start0.x, a.y - start0.y), d1 = hypot(b.x - start1.x, b.y - start1.y)
            if d0 < Self.slop && d1 < Self.slop { return }
            two = Moves.isTilt(a.x - start0.x, a.y - start0.y, b.x - start1.x, b.y - start1.y, spreadChange: gap - startGap, slop: Self.slop) ? .tilt : .transform
        }
        let w = w, h = h, g = e.ground
        if two == .tilt {
            let dy = mid.y - lastMid.y
            e.move { Moves.tilt($0, h: h, dy: dy) }
        } else {
            let p = lastMid
            let factor = lastGap > 1 ? gap / lastGap : 1
            let da = Moves.wrap(angle - lastAngle)
            turned += da
            if !turning && abs(turned) > Self.turnThreshold { turning = true }
            let turn = turning
            e.move { s in
                var st = Moves.pan(s, w: w, h: h, from: p.x, p.y, to: mid.x, mid.y, ground: g)
                st = Moves.zoom(st, w: w, h: h, at: mid.x, mid.y, factor: factor, ground: g)
                return turn ? Moves.rotate(st, w: w, h: h, at: mid.x, mid.y, angle: da, ground: g) : st
            }
        }
        lastMid = mid; lastGap = gap; lastAngle = angle
    }

    /// A tap picks; it waits for a possible second tap, which zooms in on the spot instead.
    private func tap(_ p: CGPoint, _ t: TimeInterval, _ e: Map3DEngine) {
        if t - lastTapT < 0.3 && hypot(p.x - lastTap.x, p.y - lastTap.y) < 48 {
            pendingPick?.cancel(); lastTapT = 0
            e.animate(Moves.zoom(e.cam, w: w, h: h, at: p.x, p.y, factor: 2.2, ground: e.ground), seconds: 0.35)
            return
        }
        lastTapT = t; lastTap = p
        pendingPick?.cancel()
        pendingPick = Task { @MainActor [weak e] in
            try? await Task.sleep(for: .milliseconds(300))
            guard !Task.isCancelled else { return }
            e?.pick(p)
        }
    }
}

/// The names on the mountain, over the Metal view (Android's LabelOverlay): the peaks, Gudauri and Kobi, the lifts at
/// mid-cable, and the runs in their colours up close (or only the chosen one). Each on a small sign with a post, never
/// over another, and hidden behind a ridge. The 3D map is always snowy, so its labels keep the day palette (the site's
/// .r3-labels).
private struct Labels3D: View {
    let engine: Map3DEngine
    @Environment(\.ski) private var ski

    var body: some View {
        let _ = engine.tick
        Canvas { c, size in draw(&c, size) }
    }

    private func draw(_ c: inout GraphicsContext, _ size: CGSize) {
        var placed: [(rect: CGRect, run: String?, lift: Int?)] = []
        guard engine.ready else { engine.placed = []; return }
        let chosen = engine.chosen, hidden = engine.hiddenRuns, far = engine.cam.dist > 9000
        let day = SkiColors(p: .day)
        let pad: CGFloat = 5
        let ordered = engine.labels.filter { chosen != nil && $0.run == chosen } + engine.labels.filter { chosen == nil || $0.run != chosen }
        for l in ordered {
            if l.kind == .lift && engine.liftsOff { continue }
            if let r = l.run, hidden.contains(r) || (chosen != nil && r != chosen) || (chosen == nil && far) { continue }
            guard let s = engine.screen(l.p), s.x > -50, s.x < size.width + 50, s.y > -20, s.y < size.height + 20 else { continue }
            guard engine.visible(l.p) else { continue }
            let font: Font, color: Color
            switch l.kind {
            case .peak: font = ski.type.display(19); color = day.ink
            case .place: font = ski.type.display(15); color = day.muted
            case .lift: font = ski.type.text(12); color = .white
            case .run: font = ski.type.text(13, .bold); color = Color(rgb: l.color)
            }
            let txt = c.resolve(Text(l.text).font(font).foregroundColor(color))
            let z = txt.measure(in: CGSize(width: 400, height: 60))
            let r = CGRect(x: s.x - z.width / 2 - pad, y: s.y - z.height - pad * 2, width: z.width + pad * 2, height: z.height + pad * 2)
            if placed.contains(where: { $0.rect.intersects(r) }) { continue }
            placed.append((r, l.run, l.lift))
            let dark = l.kind == .lift
            c.fill(Path(r), with: .color(dark ? Color(rgb: 0x13233A, opacity: 0.92) : Color.white.opacity(0.92)))
            c.fill(Path(CGRect(x: s.x - 1.5, y: s.y, width: 3, height: pad * 1.6)), with: .color(dark ? .white : day.ink))
            c.draw(txt, at: CGPoint(x: r.minX + pad, y: r.minY + pad), anchor: .topLeading)
        }
        engine.placed = placed
    }
}

/// While flying down: where the skier is, as on the site (6.4.1): the run, distance, height and slope.
private struct FlyBar: View {
    let engine: Map3DEngine
    let name: String
    @Environment(\.ski) private var ski

    var body: some View {
        let _ = engine.tick
        HStack(spacing: 0) {
            Text(name).font(ski.type.text(15, .bold))
            if let f = engine.flyInfo {
                Text("  ·  " + S.runFlyHudReadout(dist: Fmt.n(Int(f.d)), alt: Fmt.n(Int(f.h)), deg: String(Fmt.round(f.deg)))).font(ski.type.text(15))
            }
        }
        .foregroundStyle(.white)
        .padding(.horizontal, 12).padding(.vertical, 8)
        .background(Color(rgb: 0x13233A, opacity: 0.9))
        .accessibilityElement(children: .combine)
    }
}
