import SwiftUI
import GudiData
import GudiDomain

/// A lift's panel (the site's showLift, Android's LiftHead and LiftBody): type, length, ride time, seats, people an
/// hour, stations and rise, year, and the runs from its top and to its bottom.
struct LiftHead: View {
    let l: Lift
    @Environment(\.ski) private var ski
    var body: some View {
        Text("⇡ " + (l.name.isEmpty ? S.liftUnnamed : l.name)).font(ski.type.display(32)).foregroundStyle(ski.colors.ink)
            .accessibilityAddTraits(.isHeader)
    }
}

struct LiftBody: View {
    let l: Lift
    let model: MapModel
    @Environment(\.ski) private var ski
    @Environment(\.openURL) private var openURL

    private var kind: String {
        switch l.kind {
        case "chair_lift": S.liftKindChairLift; case "gondola": S.liftKindGondola; case "platter": S.liftKindPlatter
        case "magic_carpet": S.liftKindMagicCarpet; case "drag_lift": S.liftKindDragLift; case "t_bar": S.liftKindTBar
        default: l.kind
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Kv(S.liftTypeLabel, kind + (l.status == "inactive" ? S.liftInactiveSuffix : ""))
            Kv(S.commonLengthLabel, Fmt.length(Int(l.len)))
            if let d = l.dur { Kv(S.liftRideTimeLabel, S.liftRideTimeValue(n: d)) }
            if let o = l.occ { Kv(S.liftSeatsLabel, o + (l.bubble == "yes" ? S.liftBubbleSuffix : "")) }
            if let c = l.cap { Kv(S.liftCapacityLabel, S.liftCapacityValue(n: c)) }
            let line = l.line
            if line.count >= 2, let t = model.terrain {
                let ea = Fmt.round(t.elev(line[0].x, line[0].y)), eb = Fmt.round(t.elev(line[line.count - 1].x, line[line.count - 1].y))
                Kv(S.liftStationsLabel, S.liftStationsValue(bot: Fmt.n(min(ea, eb)), top: Fmt.n(max(ea, eb))))
                Kv(S.liftRiseLabel, S.liftRiseModelValue(n: Fmt.n(abs(eb - ea))) + (l.rise.map { S.liftRiseOsmSuffix(n: $0) } ?? ""))
            }
            if let y = l.year { Kv(S.liftBuiltLabel, y) }
            H3(S.liftRunsFromTopHeading)
            runs(model.runs.pistes.filter { !l.name.isEmpty && $0.fromLifts.contains(l.name) })
            H3(S.liftRunsToBottomHeading)
            runs(model.runs.pistes.filter { !l.name.isEmpty && $0.toLifts.contains(l.name) })
            if !l.id.isEmpty {
                Button { if let u = URL(string: "https://www.openstreetmap.org/way/\(l.id)") { openURL(u) } } label: {
                    Text("OSM: \(l.id)").font(ski.type.text(13)).underline().foregroundStyle(ski.colors.glacier).frame(minHeight: 44)
                }
                .buttonStyle(.plain).environment(\.layoutDirection, .leftToRight).padding(.top, 6)
            }
        }
    }

    @ViewBuilder private func runs(_ list: [Piste]) -> some View {
        if list.isEmpty { KvText(text: "—") }
        else {
            Flow(spacing: 6) { ForEach(list) { r in Tag(text: runName(r), color: ski.colors.run(r.color)) { model.select(r.key) } } }
        }
    }
}

/// The map's filters (the site's): the four colours, the unnamed sections and the lifts.
let mapFilters = ["green", "blue", "red", "black", "unnamed", "lifts"]

/// All the runs (the site's overview): the filters, the list by colour, the partly found ones, and where it all comes from.
struct RunList: View {
    let model: MapModel
    @Environment(\.ski) private var ski

    private func label(_ k: String) -> String {
        switch k { case "unnamed": S.mapUnnamed; case "lifts": S.mapLifts; case "green": S.commonColorGreen; case "blue": S.commonColorBlue; case "red": S.commonColorRed; default: S.commonColorBlack }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(S.mapFilter).font(ski.type.text(13)).foregroundStyle(ski.colors.muted).padding(.top, 4).padding(.bottom, 6)
            Flow(spacing: 6) {
                ForEach(mapFilters, id: \.self) { k in
                    let on = !model.hidden.contains(k)
                    let sw = k == "unnamed" ? ski.colors.muted : k == "lifts" ? ski.colors.lift : ski.colors.run(k)
                    Button { model.toggle(k); Fx.shared.tick() } label: {
                        HStack(spacing: 6) {
                            Rectangle().fill(on ? sw : .clear).frame(width: 12, height: 12).overlay(Rectangle().stroke(sw, lineWidth: 1.5))
                            Text(label(k)).font(ski.type.text(14, .bold)).foregroundStyle(on ? ski.colors.ink : ski.colors.muted)
                        }
                        .padding(.horizontal, 10).frame(minHeight: 44)
                        .background(on ? ski.colors.paper : ski.colors.snow)
                        .overlay(Rectangle().stroke(on ? ski.colors.ink : ski.colors.rule, lineWidth: 1.5))
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(on ? .isSelected : [])
                }
            }
            Text(S.mapOverviewLead).font(ski.type.text(14)).foregroundStyle(ski.colors.ink).padding(.top, 12).fixedSize(horizontal: false, vertical: true)
            H3(S.mapOverviewOnMapCount(n: String(model.order.count)))
            ForEach(model.order, id: \.self) { k in
                if let p = model.piste(k) {
                    Button { model.select(k) } label: {
                        HStack(spacing: 10) {
                            Rectangle().fill(ski.colors.run(p.color)).frame(width: 14, height: 14)
                            Text(runName(p)).font(ski.type.text(16, .bold)).foregroundStyle(ski.colors.ink)
                            Spacer()
                            Text(Fmt.length(Int(p.len))).font(ski.type.text(13)).foregroundStyle(ski.colors.muted)
                        }
                        .frame(minHeight: 48).contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
            let partial = model.runs.pistes.filter { $0.research?.partial != nil }
            if !partial.isEmpty {
                H3(S.mapOverviewPartialCount(n: String(partial.count)))
                Flow(spacing: 6) { ForEach(partial) { p in Tag(text: p.key, color: ski.colors.run(p.color)) { model.select(p.key) } } }
                Hint(S.mapOverviewPartialHint)
            }
        }
    }
}

/// A row that wraps (the site's flex-wrap), for tags and filters.
struct Flow: Layout {
    var spacing: CGFloat = 6
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let w = proposal.width ?? .infinity
        var x: CGFloat = 0, y: CGFloat = 0, row: CGFloat = 0, widest: CGFloat = 0
        for s in subviews {
            let z = s.sizeThatFits(.unspecified)
            if x > 0 && x + z.width > w { y += row + spacing; x = 0; row = 0 }
            x += z.width + spacing; row = max(row, z.height); widest = max(widest, x - spacing)
        }
        return CGSize(width: min(widest, w), height: y + row)
    }
    func placeSubviews(in b: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x: CGFloat = 0, y: CGFloat = 0, row: CGFloat = 0
        for s in subviews {
            let z = s.sizeThatFits(.unspecified)
            if x > 0 && x + z.width > b.width { y += row + spacing; x = 0; row = 0 }
            s.place(at: CGPoint(x: b.minX + x, y: b.minY + y), proposal: ProposedViewSize(z))
            x += z.width + spacing; row = max(row, z.height)
        }
    }
}
