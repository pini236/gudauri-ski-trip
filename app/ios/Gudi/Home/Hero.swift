import SwiftUI
import UIKit
import GudiDomain

/// The real view from the village (site/img/pano, design/round3/panorama.py), decoded once and kept.
@MainActor
enum Pano {
    private static var cache: [String: UIImage] = [:]

    static func image(_ name: String) -> UIImage? {
        if let i = cache[name] { return i }
        guard let url = Bundle.main.url(forResource: name, withExtension: "webp", subdirectory: "Data/pano"),
              let data = try? Data(contentsOf: url), let img = UIImage(data: data) else { return nil }
        if cache.count > 4 { cache.removeAll() }
        cache[name] = img
        return img
    }

    /// Positions in the images, as fractions (design/round3/pano.json): the village of New Gudauri.
    static func village(wide: Bool) -> CGPoint { CGPoint(x: wide ? 0.4742 : 0.4525, y: 0.6926) }

    /// Window lights around the village, in points from it (the site's 16 lights).
    static let lights: [(Int, Int)] = [(-44, 6), (-35, 2), (-28, 9), (-19, 4), (-12, 11), (-5, 1), (3, 7), (9, 13), (16, 3), (24, 10),
                                       (31, 5), (39, 12), (-23, 15), (0, 16), (20, 17), (46, 8)]

    /// Stars at fixed places, so the sky looks the same every night (percent of the sky).
    static let stars: [(Int, Int)] = [(4, 8), (11, 19), (18, 5), (25, 14), (33, 7), (40, 21), (47, 4), (55, 16), (61, 9), (68, 24), (74, 6), (81, 15),
                                      (88, 10), (94, 22), (7, 30), (29, 28), (51, 31), (72, 33), (90, 29), (15, 40), (38, 38), (63, 41), (84, 37), (97, 4)]
}

/// The sky and the mountains at the top of the home page, as the site and Android (6.3, N1): the sky's colours, the
/// glow of the low sun, stars and the moon at night, the view from the village cross-fading through the day, and the
/// village's windows lit after dark. Everything here is physical (left is west), so it does not mirror in Hebrew.
struct Hero: View {
    let frame: DayNight.Frame
    let height: CGFloat
    @Environment(\.ski) private var ski

    var body: some View {
        GeometryReader { g in
            let wide = g.size.width > 560
            let iw: CGFloat = wide ? 1440 : 390
            let s = max(g.size.width / iw, g.size.height / 400)
            let w = iw * s, h = 400 * s
            let left = (g.size.width - w) / 2, top = min(0, g.size.height * 0.9 - 0.75 * h)
            ZStack(alignment: .topLeading) {
                LinearGradient(stops: [.init(color: Color(rgb: frame.skyTop), location: 0),
                                       .init(color: Color(rgb: frame.skyBottom), location: 0.75)],
                               startPoint: .top, endPoint: .bottom)
                Canvas { ctx, size in
                    if frame.glow > 0.01 {
                        let c = CGPoint(x: size.width * (frame.glowMorning ? 0.96 : 0.04), y: size.height * 0.4)
                        let r: CGFloat = 280
                        let glow = Color(rgb: frame.glowColor)
                        var g2 = ctx
                        g2.opacity = frame.glow
                        g2.translateBy(x: c.x, y: c.y); g2.scaleBy(x: 1, y: 420.0 / 560.0); g2.translateBy(x: -c.x, y: -c.y)
                        g2.fill(Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: 2 * r, height: 2 * r)),
                                with: .radialGradient(Gradient(colors: [glow, glow.opacity(0)]), center: c, startRadius: 0, endRadius: r))
                    }
                    if frame.stars > 0.01 {
                        for (i, xy) in Pano.stars.enumerated() {
                            let r: CGFloat = i % 3 == 2 ? 1.5 : 1
                            let p = CGPoint(x: size.width * CGFloat(xy.0) / 100, y: size.height * CGFloat(xy.1) / 100)
                            ctx.fill(Path(ellipseIn: CGRect(x: p.x - r, y: p.y - r, width: 2 * r, height: 2 * r)), with: .color(.white.opacity(frame.stars)))
                        }
                    }
                    if frame.moon > 0.01 {
                        // a thick crescent, as the site's moon: a circle with a narrower ellipse cut out of it
                        let s = 36.0 / 34.0, o = CGPoint(x: size.width * 0.30, y: size.height * 0.19)
                        var m = Path()
                        m.move(to: CGPoint(x: 17, y: 3))
                        m.addArc(center: CGPoint(x: 17, y: 17), radius: 14, startAngle: .degrees(-90), endAngle: .degrees(90), clockwise: false)
                        // back up along the right half of a narrow ellipse (rx 7.5, ry 14), as two curves
                        let k = 0.5523
                        m.addCurve(to: CGPoint(x: 24.5, y: 17), control1: CGPoint(x: 17 + 7.5 * k, y: 31), control2: CGPoint(x: 24.5, y: 17 + 14 * k))
                        m.addCurve(to: CGPoint(x: 17, y: 3), control1: CGPoint(x: 24.5, y: 17 - 14 * k), control2: CGPoint(x: 17 + 7.5 * k, y: 3))
                        m.closeSubpath()
                        let t = CGAffineTransform(translationX: o.x, y: o.y).scaledBy(x: s, y: s)
                        ctx.fill(m.applying(t), with: .color(Color(rgb: 0xF4F7FB).opacity(frame.moon)))
                    }
                }
                // the mountains: a under b, b at opacity mix
                ZStack {
                    let prefix = wide ? "pano-wide-" : "pano-"
                    if let a = Pano.image(prefix + frame.imgA) { Image(uiImage: a).resizable() }
                    if frame.imgA != frame.imgB, frame.mix > 0.01, let b = Pano.image(prefix + frame.imgB) {
                        Image(uiImage: b).resizable().opacity(frame.mix)
                    }
                    if frame.lights > 0.01 {
                        Canvas { ctx, size in
                            let v = Pano.village(wide: wide)
                            let base = CGPoint(x: size.width * v.x, y: size.height * v.y)
                            for (dx, dy) in Pano.lights {
                                let p = CGPoint(x: base.x + CGFloat(dx), y: base.y + CGFloat(dy))
                                ctx.fill(Path(ellipseIn: CGRect(x: p.x - 2, y: p.y - 2, width: 6, height: 6)),
                                         with: .color(Color(rgb: 0xFFC46E).opacity(frame.lights * 0.35)))
                                ctx.fill(Path(CGRect(x: p.x, y: p.y, width: 2, height: 2)), with: .color(Color(rgb: 0xFFD890).opacity(frame.lights)))
                            }
                        }
                    }
                }
                .frame(width: w, height: h)
                .offset(x: left, y: top)
                .animation(.easeInOut(duration: 0.9), value: frame.imgA + frame.imgB)
                // into the page
                VStack(spacing: 0) {
                    Spacer()
                    LinearGradient(colors: [ski.colors.snow.opacity(0), ski.colors.snow], startPoint: .top, endPoint: .bottom).frame(height: 70)
                }
            }
        }
        .frame(height: height)
        .clipped()
        .environment(\.layoutDirection, .leftToRight)
        .accessibilityHidden(true)
    }
}
