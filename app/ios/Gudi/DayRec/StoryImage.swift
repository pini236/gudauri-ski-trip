import SwiftUI
import UIKit
import GudiData
import GudiDomain

#if DAYREC
/// The day's line, thinned for drawing: projected metres (as the map's runs), and the speed of each point in km/h.
/// Android's `Track` (`dayrec/ui/DayView.kt`).
struct DayTrack: Sendable {
    var x: [Double] = [], y: [Double] = [], kmh: [Double] = [], t: [Int64] = []

    var isEmpty: Bool { x.count < 2 }

    /// Reads a day's journal and keeps about `max` points of it.
    static func read(_ url: URL, max: Int = 1800) -> DayTrack {
        guard let day = (try? Data(contentsOf: url)).flatMap(DayJournal.read) else { return DayTrack() }
        let step = Swift.max(day.points.count / max, 1)
        var out = DayTrack()
        for (i, p) in day.points.enumerated() where i % step == 0 {
            out.x.append(Geo.x(p.lon)); out.y.append(Geo.y(p.lat))
            out.kmh.append((p.spd ?? 0) * 3.6); out.t.append(p.t)
        }
        return out
    }
}

/// The picture of the day for sharing (round 29): the head over the numbers, the day's line in the colours of the
/// speed, and up to three insights. Android's `StoryImage`.
/// No name, no fall, no place name, and nothing the engine keeps for the skier's eyes alone (a fall, an off-piste line,
/// tomorrow, the end of the trip, where the time went): `share_exclude`, and `kept` here.
struct StoryImageView: View {
    struct Card: Identifiable { let id: Int; let title: String?; let line: String? }

    /// The kinds that never go in the picture.
    static let kept: Set<String> = ["fall", "offpiste", "tomorrow", "trip", "time_split"]
    static let W: CGFloat = 1080
    static let H: CGFloat = 1350

    let title: String?
    let sub: String?
    let cards: [Card]
    let track: DayTrack?
    let site: String
    let type: SkiType

    var body: some View {
        let rtl: Bool = type.lang.rtl
        VStack(alignment: rtl ? .trailing : .leading, spacing: 0) {
            headText
            trackBox.padding(.top, 20)
            ForEach(cards) { card in cardText(card, rtl: rtl) }
            Spacer(minLength: 0)
            Text(site).font(type.text(34)).foregroundStyle(Color(rgb: 0x8DA2BD))
                .frame(maxWidth: .infinity, alignment: .center).padding(.bottom, 20)
        }
        .multilineTextAlignment(rtl ? .trailing : .leading)
        .padding(.horizontal, 70).padding(.top, 80)
        .frame(width: Self.W, height: Self.H, alignment: .topLeading)
        .background(Color(rgb: 0x13233A))
        .environment(\.layoutDirection, rtl ? .rightToLeft : .leftToRight)
    }

    /// The head: the kind of day over its numbers.
    @ViewBuilder private var headText: some View {
        if let title {
            Text(title).font(type.display(110)).foregroundStyle(.white).lineLimit(2).minimumScaleFactor(0.5)
        }
        if let sub {
            Text(sub).font(type.text(38)).foregroundStyle(Color(rgb: 0xC9D6E6)).lineLimit(2).padding(.top, 6)
        }
    }

    /// One insight: its title in gold, its line in white.
    @ViewBuilder private func cardText(_ card: Card, rtl: Bool) -> some View {
        VStack(alignment: rtl ? .trailing : .leading, spacing: 4) {
            if let t = card.title {
                Text(t).font(type.text(40, .bold)).foregroundStyle(Color(rgb: 0xF4B942)).lineLimit(1)
            }
            if let l = card.line {
                Text(l).font(type.text(40)).foregroundStyle(.white).lineLimit(3)
            }
        }
        .frame(maxWidth: .infinity, alignment: rtl ? .trailing : .leading)
        .padding(.top, 24)
    }

    /// The day's line, in a box of its own; a hole in the recording is not a line.
    private var trackBox: some View {
        Canvas { ctx, size in
            ctx.fill(Path(CGRect(origin: .zero, size: size)), with: .color(Color(rgb: 0x1B3050)))
            guard let track, !track.isEmpty else { return }
            let pad = 40.0
            let x0 = track.x.min() ?? 0, x1 = track.x.max() ?? 1, y0 = track.y.min() ?? 0, y1 = track.y.max() ?? 1
            let k = min((size.width - 2 * pad) / max(x1 - x0, 1), (size.height - 2 * pad) / max(y1 - y0, 1))
            let ox = (size.width - (x1 - x0) * k) / 2, oy = (size.height - (y1 - y0) * k) / 2
            for i in 0..<(track.x.count - 1) {
                if track.t[i + 1] - track.t[i] > 90_000 { continue }
                var p = Path()
                p.move(to: CGPoint(x: ox + (track.x[i] - x0) * k, y: oy + (track.y[i] - y0) * k))
                p.addLine(to: CGPoint(x: ox + (track.x[i + 1] - x0) * k, y: oy + (track.y[i + 1] - y0) * k))
                ctx.stroke(p, with: .color(Color(rgb: DayRec.SpeedScale.rgb(max(track.kmh[i], track.kmh[i + 1])))),
                           style: StrokeStyle(lineWidth: 7, lineCap: .round))
            }
        }
        .frame(width: Self.W - 140, height: 470)
        .environment(\.layoutDirection, .leftToRight)
    }

    /// The picture, rendered on the phone.
    @MainActor
    static func render(_ v: StoryImageView) -> UIImage? {
        let r = ImageRenderer(content: v)
        r.scale = 1
        r.proposedSize = ProposedViewSize(width: W, height: H)
        return r.uiImage
    }
}
#endif
