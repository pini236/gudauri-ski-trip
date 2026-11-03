import SwiftUI
import GudiData
import GudiDomain

/// Food and services near the stations (round 31, approved 1.11.2026), as the site and Android show them: small coloured
/// dots beside every station that has something, the "food" key turning them into tags with a count, the lift panel's
/// "near the station", and one line under the meeting card. No hours, no phone numbers, no promise (docs/ARCHITECTURE.md).

/// One colour per kind (site/css/site.css, `--pl-*`): the same by day and by night.
func placeColor(_ kind: String) -> Color {
    switch kind {
    case "food": Color(rgb: 0xE8702A); case "cafe": Color(rgb: 0x8A5A33); case "aid": Color(rgb: 0x1E9E4A); default: Color(rgb: 0x6A4FB5)
    }
}

func placeKindName(_ kind: String) -> String {
    switch kind { case "food": S.placesKindFood; case "cafe": S.placesKindCafe; case "aid": S.placesKindAid; default: S.placesKindWc }
}

/// A place's name, or its kind when the map gives it none.
func placeLabel(_ p: Place) -> String { p.name ?? placeKindName(p.kind) }

private struct PlaceDot: View {
    let kind: String
    var diameter: CGFloat = 10
    var ring = true
    @Environment(\.ski) private var ski
    var body: some View {
        Circle().fill(placeColor(kind)).frame(width: diameter, height: diameter)
            .padding(ring ? 1.5 : 0).background(ring ? ski.colors.paper : Color.clear, in: Circle())
    }
}

/// A row of the list: the dot, the name, the kind when there is a name, and "seasonal".
private struct PlaceRow: View {
    let n: Near
    @Environment(\.ski) private var ski
    var body: some View {
        HStack(spacing: 8) {
            PlaceDot(kind: n.place.kind, ring: false)
            Text("\u{2068}\(placeLabel(n.place))\u{2069}").font(ski.type.text(14)).foregroundStyle(ski.colors.ink)
            if n.place.name != nil { Text(placeKindName(n.place.kind)).font(ski.type.text(12)).foregroundStyle(ski.colors.muted) }
            if n.place.seasonal { Text(S.placesSeasonal).font(ski.type.text(12)).foregroundStyle(ski.colors.muted) }
            Spacer(minLength: 0)
        }
        .frame(minHeight: 30)
        .accessibilityElement(children: .combine)
    }
}

/// What is near one end of a lift: up to three, "all N" for the rest, "no information" for a station without any.
private struct PlacesBlock: View {
    let places: Places
    let station: PlaceStation?
    let head: String
    @State private var all = false
    @Environment(\.ski) private var ski

    var body: some View {
        let near = station.map { places.near($0) } ?? []
        VStack(alignment: .leading, spacing: 0) {
            Text(head).font(ski.type.text(15, .bold)).foregroundStyle(ski.colors.ink).padding(.bottom, 6)
                .accessibilityAddTraits(.isHeader)
            if near.isEmpty {
                Text(S.placesNone).font(ski.type.text(13)).foregroundStyle(ski.colors.muted)
            } else {
                ForEach(Array(near.prefix(3).enumerated()), id: \.offset) { PlaceRow(n: $0.element) }
                if near.count > 3 {
                    Button { all.toggle() } label: {
                        Text(S.placesAllN(n: String(near.count))).font(ski.type.text(14, .bold)).foregroundStyle(ski.colors.glacier)
                            .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                    }
                    .buttonStyle(.plain).accessibilityAddTraits(all ? .isSelected : [])
                    if all { ForEach(Array(near.dropFirst(3).enumerated()), id: \.offset) { PlaceRow(n: $0.element) } }
                }
                Text(S.placesCheck).font(ski.type.text(13)).foregroundStyle(ski.colors.muted).padding(.top, 6)
            }
        }
        .padding(.top, 14)
    }
}

/// The lift panel's "near the station", top then bottom. A lift that is on no station (the unnamed conveyor) gets nothing.
struct LiftPlaces: View {
    let lift: Lift
    @Environment(AppModel.self) private var app

    var body: some View {
        if let places = app.places {
            let top = places.stationOf(liftId: lift.id, bottom: false), bottom = places.stationOf(liftId: lift.id, bottom: true)
            if top != nil || bottom != nil {
                VStack(alignment: .leading, spacing: 0) {
                    PlacesBlock(places: places, station: top, head: S.placesNearTop)
                    PlacesBlock(places: places, station: bottom, head: S.placesNearBottom)
                }
            }
        }
    }
}

/// The meeting card's one line: the names, or just the count at a base with many. Nothing when the station has no information.
struct MeetPlacesLine: View {
    let station: GudiDomain.Station
    @Environment(AppModel.self) private var app
    @Environment(\.ski) private var ski

    var body: some View {
        if let places = app.places,
           let near = station.ends.compactMap({ places.stationOf(liftId: $0.liftId, bottom: !$0.top) }).map({ places.near($0) }).first(where: { !$0.isEmpty }) {
            Text(near.count > 3 ? S.placesMeetCount(n: String(near.count)) : S.placesMeetNames(names: near.map { placeLabel($0.place) }.joined(separator: ", ")))
                .font(ski.type.text(13)).foregroundStyle(ski.colors.muted).padding(.horizontal, 4)
        }
    }
}

/// The map's "food" key: off on every visit; on, the dots grow into tags. Like the weather key, beside it.
struct PlacesKey: View {
    let on: Bool
    let action: () -> Void
    @Environment(\.ski) private var ski
    var body: some View {
        let fg = on ? ski.colors.paper : ski.colors.ink
        Button(action: action) {
            HStack(spacing: 7) {
                Image(systemName: "fork.knife").font(.system(size: 15, weight: .semibold))
                Text(S.placesChip).font(ski.type.text(14, .bold))
            }
            .foregroundStyle(fg).padding(.horizontal, 14).frame(minHeight: 44)
            .background(on ? ski.colors.ink : ski.colors.paper).overlay(Rectangle().stroke(ski.colors.ink, lineWidth: 1.5))
        }
        .buttonStyle(.plain).accessibilityLabel(S.placesChipAria).accessibilityAddTraits(on ? .isSelected : [])
    }
}

/// The dots over the top view (the site's PL.layout): small coloured dots beside every station that has something; the key
/// turns them into tags with a count, and a tag that would sit on another falls back to the dots. A café on the slope gets
/// a dashed ring. Not for touch: the map under it still moves.
struct PlacesLayer: View {
    let places: Places
    let tags: Bool
    let size: CGSize
    /// The site's metres (x east, y south) to a point of this view, or nil when it is off the view.
    let at: (Double, Double) -> CGPoint?
    @Environment(\.ski) private var ski

    private struct Spot: Identifiable { let id: String; let p: CGPoint; let kinds: [String]; let count: Int; let tag: Bool }

    private var spots: [Spot] {
        var taken: [CGRect] = []
        var out: [Spot] = []
        for (st, kinds) in places.withPlaces() {
            guard let q = at(Geo.x(st.lon), Geo.y(st.lat)) else { continue }
            let origin = CGPoint(x: q.x + 9, y: q.y - 9)
            var tag = tags
            if tags {
                let box = CGRect(x: origin.x, y: origin.y - 24, width: CGFloat(kinds.count) * 15 + 30, height: 24)
                if taken.contains(where: { $0.intersects(box) }) { tag = false } else { taken.append(box) }
            }
            out.append(Spot(id: st.id, p: origin, kinds: kinds, count: places.near(st).count, tag: tag))
        }
        return out
    }

    var body: some View {
        ZStack(alignment: .topLeading) {
            ForEach(spots) { s in
                HStack(spacing: s.tag ? 5 : 3) {
                    ForEach(s.kinds, id: \.self) { PlaceDot(kind: $0, ring: !s.tag) }
                    if s.tag { Text(String(s.count)).font(ski.type.text(12, .bold)).foregroundStyle(ski.colors.ink) }
                }
                .padding(.horizontal, s.tag ? 7 : 0).padding(.vertical, s.tag ? 3 : 0)
                .background(s.tag ? ski.colors.paper : Color.clear).overlay { if s.tag { Rectangle().stroke(ski.colors.ink, lineWidth: 1.5) } }
                .fixedSize().position(x: s.p.x + (s.tag ? 24 : 12), y: s.p.y - (s.tag ? 0 : 5))
            }
            ForEach(places.onSlope(), id: \.id) { p in
                if let q = at(Geo.x(p.lon), Geo.y(p.lat)) {
                    Circle().fill(ski.colors.paper).frame(width: 12, height: 12)
                        .overlay(Circle().inset(by: 1).stroke(placeColor(p.kind), style: StrokeStyle(lineWidth: 2, dash: [4, 3])))
                        .position(q)
                    if tags {
                        Text(p.name ?? "").font(ski.type.text(12)).foregroundStyle(ski.colors.ink).padding(.horizontal, 6).padding(.vertical, 2)
                            .background(ski.colors.paper).overlay(Rectangle().stroke(ski.colors.ink, lineWidth: 1.5)).fixedSize()
                            .position(x: q.x + 10 + 30, y: q.y - 9)
                    }
                }
            }
        }
        .frame(width: size.width, height: size.height)
        .environment(\.layoutDirection, .leftToRight)
        .accessibilityHidden(true).allowsHitTesting(false)
    }
}
