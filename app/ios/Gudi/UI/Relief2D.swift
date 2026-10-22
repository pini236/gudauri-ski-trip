import SwiftUI
import UIKit
import GudiDomain

/// The relief under a flat map, ready to draw (Android's meet/Relief2D.kt): GudiDomain's `Relief` turned into paths in
/// the site's metres, and the shaded picture by day and by night (the site's night map filter: grayscale, then
/// brightness .42, then contrast 1.35). Heavy to build (~1 MB of lines and a 1231×1299 picture): build it once, off the
/// main thread (`ReliefArt.load(url:)`), and keep it.
///
/// Stable API: `ReliefArt.load(url:)`, `relief`, `box`, the paths, and `drawRelief(in:_:view:colors:far:)`.
final class ReliefArt: @unchecked Sendable {
    let relief: Relief
    /// The box the picture covers, in metres.
    let box: CGRect
    let c50: Path, c100: Path, c250: Path, c500: Path
    let village: Path, rivers: Path, water: Path
    /// Road classes 0 (main) to 4 (tracks).
    let roads: [Path]
    let hillDay: CGImage?
    let hillNight: CGImage?

    init(_ r: Relief) {
        relief = r
        box = CGRect(x: r.x0, y: r.y0, width: r.x1 - r.x0, height: r.y1 - r.y0)
        func path(_ lines: [[Double]], closed: Bool) -> Path {
            var p = Path()
            for l in lines where l.count >= 4 {
                p.move(to: CGPoint(x: l[0], y: l[1]))
                var i = 2
                while i + 1 < l.count { p.addLine(to: CGPoint(x: l[i], y: l[i + 1])); i += 2 }
                if closed { p.closeSubpath() }
            }
            return p
        }
        c50 = path(r.c50, closed: false); c100 = path(r.c100, closed: false); c250 = path(r.c250, closed: false); c500 = path(r.c500, closed: false)
        village = path(r.village, closed: true); rivers = path(r.rivers, closed: false); water = path(r.water, closed: true)
        roads = r.roads.map { path($0, closed: false) }
        let day = r.hillJPEG.flatMap { UIImage(data: $0)?.cgImage }
        hillDay = day
        hillNight = day.flatMap(Self.night)
    }

    /// Parses terrain.json and builds the art, off the main thread.
    static func load(url: URL?) async -> ReliefArt? {
        guard let url else { return nil }
        return await Task.detached(priority: .userInitiated) {
            guard let data = try? Data(contentsOf: url), let r = try? Relief.parse(data) else { return nil }
            return ReliefArt(r)
        }.value
    }

    /// The site's night map: grayscale (Android's ColorMatrix.setSaturation(0) weights), brightness .42, contrast 1.35.
    static func night(_ img: CGImage) -> CGImage? {
        let w = img.width, h = img.height
        var px = [UInt8](repeating: 0, count: w * h * 4)
        let cs = CGColorSpaceCreateDeviceRGB()
        let made: CGImage? = px.withUnsafeMutableBytes { buf -> CGImage? in
            guard let ctx = CGContext(data: buf.baseAddress, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4, space: cs,
                                      bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return nil }
            ctx.draw(img, in: CGRect(x: 0, y: 0, width: w, height: h))
            let p = buf.bindMemory(to: UInt8.self)
            let k = 0.42 * 1.35, off = (1 - 1.35) * 127.5
            for i in stride(from: 0, to: w * h * 4, by: 4) {
                let g = 0.213 * Double(p[i]) + 0.715 * Double(p[i + 1]) + 0.072 * Double(p[i + 2])
                let v = UInt8(max(0, min(255, g * k + off)))
                p[i] = v; p[i + 1] = v; p[i + 2] = v
            }
            return ctx.makeImage()
        }
        return made
    }
}

/// Draws the relief into a map's canvas, in the viewport's window: the picture, the contours (every 50 m only when not
/// `far`, as the site hides the thinnest when far out), the village, rivers, lakes and roads (no tracks when `far`).
/// The widths are points on the screen and the alphas Android's (meet/MeetMap.kt, map/Overview.kt drawRelief).
@MainActor
func drawRelief(in ctx: GraphicsContext, _ art: ReliefArt, view: MapViewport, colors: SkiColors, far: Bool) {
    drawRelief(in: ctx, art, k: view.k, x: view.x, y: view.y, colors: colors, far: far)
}

/// The same drawing for any camera: `k` points per metre, (x, y) the metres at the top-leading corner (the trail map's).
func drawRelief(in ctx: GraphicsContext, _ art: ReliefArt, k: Double, x: Double, y: Double, colors: SkiColors, far: Bool) {
    let p = colors.p
    var g = ctx
    g.scaleBy(x: k, y: k)
    g.translateBy(x: -x, y: -y)
    func stroke(_ path: Path, _ rgb: UInt32, _ a: Double, _ px: Double) {
        g.stroke(path, with: .color(Color(rgb: rgb, opacity: a)), style: StrokeStyle(lineWidth: px / k, lineCap: .round, lineJoin: .round))
    }
    if colors.dark, let img = art.hillNight {
        var l = g; l.opacity = 0.95
        l.draw(Image(decorative: img, scale: 1), in: art.box)
    } else if !colors.dark, let img = art.hillDay {
        var l = g; l.blendMode = .multiply; l.opacity = 0.62
        l.draw(Image(decorative: img, scale: 1), in: art.box)
    }
    if !far { stroke(art.c50, p.contour, 0.55, 0.45) }
    stroke(art.c100, p.contour, 0.7, 0.6)
    stroke(art.c250, p.contourI, 0.8, 1)
    g.fill(art.village, with: .color(Color(rgb: p.village, opacity: 0.75)))
    stroke(art.rivers, p.water, 0.9, 1.4)
    g.fill(art.water, with: .color(Color(rgb: p.water)))
    stroke(art.water, p.waterEdge, 1, 0.8)
    let top = far ? 3 : 4
    for cls in stride(from: top, through: 0, by: -1) {
        stroke(art.roads[cls], p.casing, 0.85, cls == 0 ? 5 : cls >= 3 ? 2.4 : 3.4)
    }
    for cls in stride(from: top, through: 0, by: -1) {
        stroke(art.roads[cls], cls == 0 ? p.roadMain : p.road, 1, cls == 0 ? 3 : cls <= 2 ? 1.8 : 1.1)
    }
}
