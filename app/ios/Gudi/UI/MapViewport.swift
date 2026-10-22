import SwiftUI

/// What a flat map shows: a window on the mountain in the site's metres (`x`, `y` at the top left, `span` metres across
/// the view's width), and the moves between windows (the site's goTo, fitAll and zoomAt in site/js/app.js; Android's
/// meet/MeetMap.kt MeetView). Shared by the meeting point and the trail map's top view.
///
/// Stable API: `w`/`h` (set by the view from its size, in points), `ready`, `still`, `k`, `sx`/`sy` (metres to
/// points), `mx`/`my` (points to metres), `goTo`, `fit(minX:minY:maxX:maxY:…)`, `frame`, `zoomAt`, `pan`, `stop`.
@MainActor
@Observable
final class MapViewport {
    /// Reduced motion: the map jumps to a place instead of gliding there, as on the site.
    var still = false
    private(set) var x: Double = 0
    private(set) var y: Double = 0
    private(set) var span: Double = 1
    /// The view's size in points.
    private(set) var w: Double = 1
    private(set) var h: Double = 1
    /// The first window can be set once the map knows its size.
    private(set) var ready = false
    /// The closest and the farthest the map goes, metres across.
    var minSpan: Double = 300
    var maxSpan: Double = 9000
    @ObservationIgnored private var move: Task<Void, Never>?

    /// Points per metre.
    var k: Double { w / span }
    func sx(_ mx: Double) -> Double { (mx - x) * k }
    func sy(_ my: Double) -> Double { (my - y) * k }
    func mx(_ px: Double) -> Double { x + px / k }
    func my(_ py: Double) -> Double { y + py / k }

    /// The view's size; the first call makes the viewport `ready`.
    func resize(_ size: CGSize) {
        guard size.width > 0, size.height > 0 else { return }
        w = size.width; h = size.height
        if !ready { ready = true }
    }

    func stop() { move?.cancel(); move = nil }

    private func to(_ nx: Double, _ ny: Double, _ ns: Double) { x = nx; y = ny; span = ns }

    nonisolated static func inOut(_ t: Double) -> Double { t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2 }
    nonisolated static func out(_ t: Double) -> Double { 1 - pow(1 - t, 3) }

    /// A spot a little below the middle, so a pin above it shows whole (the site's goTo); `s` metres across.
    func goTo(_ px: Double, _ py: Double, span s: Double, ms: Int) {
        animate(px - s / 2, py - s * h / w * 0.55, s, ms, Self.inOut)
    }

    /// The box of these points, whole, with the site's margin (fitAll; 1.15 times, a little lower).
    func fit(minX a: Double, minY b: Double, maxX c: Double, maxY d: Double, ms: Int) {
        let s = max(c - a, (d - b) * w / h) * 1.15
        goTo((a + c) / 2, (b + d) / 2 + (d - b) * 0.05, span: s, ms: ms)
    }

    /// A box of the map, whole, in the top `free` share of the view (above a panel), `pad` times its size and at least
    /// `least` metres across (the run map's fit, 1.12, and its focusOn, 1.5 and 900 m).
    func frame(minX a: Double, minY b: Double, maxX c: Double, maxY d: Double, pad: Double, least: Double, free: Double, ms: Int) {
        let f = min(max(free, 0.3), 1)
        let s = min(max(max(max(c - a, (d - b) * w / (h * f)), least) * pad, minSpan), maxSpan)
        animate((a + c) / 2 - s / 2, (b + d) / 2 - s * h / w * f / 2, s, ms, Self.inOut)
    }

    /// Zoom by `f` (under 1 closer) around a point of the map in metres (the middle when nil).
    func zoomAt(_ f: Double, mx px: Double? = nil, my py: Double? = nil, ms: Int) {
        let ns = min(max(span * f, minSpan), maxSpan)
        let cx = px ?? (x + span / 2), cy = py ?? (y + span * h / w / 2)
        let fx = (cx - x) / span, fy = (cy - y) / (span * h / w)
        animate(cx - fx * ns, cy - fy * ns * h / w, ns, ms, Self.out)
    }

    /// Moves the map with a finger, in points.
    func pan(_ dx: Double, _ dy: Double) { stop(); x -= dx / k; y -= dy / k }

    private func animate(_ tx: Double, _ ty: Double, _ ts: Double, _ ms: Int, _ ease: @escaping @Sendable (Double) -> Double) {
        stop()
        guard ms > 0, ready, !still else { to(tx, ty, ts); return }
        let fx = x, fy = y, fs = span
        let t0 = Date()
        move = Task { @MainActor [weak self] in
            while !Task.isCancelled {
                let t = min(1, Date().timeIntervalSince(t0) * 1000 / Double(ms))
                let e = ease(t)
                self?.to(fx + (tx - fx) * e, fy + (ty - fy) * e, fs + (ts - fs) * e)
                if t >= 1 { break }
                try? await Task.sleep(nanoseconds: 16_000_000)
            }
        }
    }
}

/// The map's gestures, iPhone style: one finger drags, two pinch around their middle (a drag is not a tap).
struct MapGestures: ViewModifier {
    let view: MapViewport
    @State private var lastDrag: CGSize = .zero
    @State private var lastScale: CGFloat = 1

    func body(content: Content) -> some View {
        content
            .gesture(
                DragGesture(minimumDistance: 6)
                    .onChanged { g in
                        view.pan(g.translation.width - lastDrag.width, g.translation.height - lastDrag.height)
                        lastDrag = g.translation
                    }
                    .onEnded { _ in lastDrag = .zero }
                    .simultaneously(with: MagnifyGesture()
                        .onChanged { g in
                            let f = Double(lastScale / g.magnification)
                            lastScale = g.magnification
                            view.zoomAt(f, mx: view.mx(g.startLocation.x), my: view.my(g.startLocation.y), ms: 0)
                        }
                        .onEnded { _ in lastScale = 1 })
            )
    }
}

extension View {
    func mapGestures(_ view: MapViewport) -> some View { modifier(MapGestures(view: view)) }
}
