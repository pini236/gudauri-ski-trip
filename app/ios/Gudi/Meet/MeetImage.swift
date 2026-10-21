import SwiftUI
import UIKit
import GudiData
import GudiDomain

/// The picture for sharing a meeting point (M3), drawn as the site draws it (site/js/app.js, MEET image(); Android's
/// meet/MeetImage.kt): the mountain around the spot, the runs and lifts, the pin, the name on a blue sign and the time
/// in gold, 1080 square. Always in the day's colours, as on the site.
struct MeetImageView: View {
    let station: Station
    let time: String
    let label: String
    let site: String
    let lines: MeetLines
    let art: ReliefArt?
    let type: SkiType
    static let W: CGFloat = 1080

    var body: some View {
        let W = Self.W
        let rtl = type.lang.rtl
        ZStack(alignment: .topLeading) {
            Canvas { ctx, _ in
                let span = 2200.0, k = W / span
                let ox = station.x - span / 2, oy = station.y - span * 0.55
                ctx.fill(Path(CGRect(x: 0, y: 0, width: W, height: W)), with: .color(Color(rgb: 0xEEF2F5)))
                var g = ctx
                g.scaleBy(x: k, y: k); g.translateBy(x: -ox, y: -oy)
                if let art, let img = art.hillDay {
                    var l = g; l.opacity = 0.55
                    l.draw(Image(decorative: img, scale: 1), in: art.box)
                }
                let cols: [String: UInt32] = ["green": 0x1B8A4C, "blue": 0x1F5FC4, "red": 0xD1342B, "black": 0x13233A]
                for (color, path) in lines.runs {
                    g.stroke(path, with: .color(.white), style: StrokeStyle(lineWidth: 11 / k, lineCap: .round, lineJoin: .round))
                    g.stroke(path, with: .color(Color(rgb: cols[color] ?? 0x13233A)), style: StrokeStyle(lineWidth: 6 / k, lineCap: .round, lineJoin: .round))
                }
                g.stroke(lines.lifts, with: .color(.white), style: StrokeStyle(lineWidth: 6 / k, lineCap: .round, lineJoin: .round))
                g.stroke(lines.lifts, with: .color(Color(rgb: 0x3A4556)), style: StrokeStyle(lineWidth: 3 / k, lineCap: .round, lineJoin: .round))

                // the pin
                let px = (station.x - ox) * k, py = (station.y - oy) * k
                ctx.stroke(Path(ellipseIn: CGRect(x: px - 46, y: py - 18, width: 92, height: 36)), with: .color(MeetInk.navy), lineWidth: 6)
                var pin = ctx
                pin.translateBy(x: px, y: py); pin.scaleBy(x: 50.0 / 14, y: 50.0 / 14)
                pin.fill(meetPin, with: .color(MeetInk.gold))
                pin.stroke(meetPin, with: .color(MeetInk.navy), lineWidth: 6 * 14 / 50)
                let dot = Path(ellipseIn: CGRect(x: px - 19, y: py - 119, width: 38, height: 38))
                ctx.fill(dot, with: .color(.white)); ctx.stroke(dot, with: .color(MeetInk.navy), lineWidth: 6)
            }
            // the dark foot
            LinearGradient(stops: [.init(color: MeetInk.navy.opacity(0), location: 0), .init(color: MeetInk.navy.opacity(0.85), location: 0.45),
                                   .init(color: MeetInk.navy.opacity(0.95), location: 1)], startPoint: .top, endPoint: .bottom)
                .frame(width: W, height: 420).offset(y: W - 420)
            // the site's name, top right
            Text(site).font(type.text(34, .bold)).foregroundStyle(.white).lineLimit(1)
                .frame(width: 360, height: 70, alignment: .trailing).padding(.trailing, 30)
                .background(MeetInk.navy).offset(x: W - 420, y: 40)
            // the label, then the name on a blue sign and the time in gold
            VStack(alignment: rtl ? .trailing : .leading, spacing: 50) {
                Text(label).font(type.text(40, .bold)).foregroundStyle(.white).lineLimit(1)
                    .frame(maxWidth: .infinity, alignment: rtl ? .trailing : .leading)
                HStack(spacing: 30) {
                    Text(station.name).font(type.display(150)).foregroundStyle(.white).lineLimit(1).minimumScaleFactor(0.4)
                        .padding(.leading, 70).padding(.trailing, 40).frame(height: 200)
                        .background(Color(rgb: 0x1F5FC4), in: MeetSign())
                    Spacer(minLength: 0)
                    Text(time).font(type.display(150)).foregroundStyle(MeetInk.navy).lineLimit(1).minimumScaleFactor(0.4)
                        .padding(.horizontal, 25).frame(minWidth: 220, maxWidth: 300).frame(height: 200)
                        .background(MeetInk.gold)
                }
                .environment(\.layoutDirection, .leftToRight)
            }
            .frame(width: W - 120)
            .offset(x: 60, y: W - 370)
        }
        .frame(width: W, height: W, alignment: .topLeading)
        .environment(\.layoutDirection, .leftToRight)
    }

    /// The picture, rendered on the phone.
    @MainActor
    static func render(_ v: MeetImageView) -> UIImage? {
        let r = ImageRenderer(content: v)
        r.scale = 1
        r.proposedSize = ProposedViewSize(width: W, height: W)
        return r.uiImage
    }
}

/// The blue sign of the picture: its arrow on the left, as the site's image draws it.
struct MeetSign: Shape {
    func path(in r: CGRect) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: r.minX, y: r.midY)); p.addLine(to: CGPoint(x: r.minX + 50, y: r.minY))
        p.addLine(to: CGPoint(x: r.maxX, y: r.minY)); p.addLine(to: CGPoint(x: r.maxX, y: r.maxY))
        p.addLine(to: CGPoint(x: r.minX + 50, y: r.maxY)); p.closeSubpath()
        return p
    }
}
