import SwiftUI
import GudiDomain

/// The lift status on home without a trip (round 20, decision 65; the site's #seasonBoard and Android's SeasonBoard):
/// one dot per lift on the board. Without a fresh report it is switched off: greyed, the dots dashed and empty, "0/N"
/// and "no report". With one, the same widget live: a green rule on top, the open lifts' dots filled, "open/N" and when
/// it was updated.
struct SeasonBoard: View {
    let status: LiftStatus?
    let action: () -> Void
    @Environment(\.ski) private var ski

    var body: some View {
        let c = ski.colors
        let live = status?.fresh ?? false
        let names = status?.names ?? []
        let open = live ? status!.open : 0
        let foot = live ? agoText(status!) : S.statusNoReport
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                LiftIcon().stroke(c.ink, lineWidth: 1.8).frame(width: 22, height: 22)
                Text(S.statusHeadingLiftStatus).font(ski.type.text(15, .bold)).foregroundStyle(c.ink)
            }
            if !names.isEmpty {
                // the dots run left to right in every language, as the site's .sb-dots
                FlowDots(names: names, isOn: { live && status!.isOpen($0) == true }, on: c.green, off: c.muted)
                    .environment(\.layoutDirection, .leftToRight)
                HStack(alignment: .lastTextBaseline, spacing: 10) {
                    Text("\(open)/\(names.count)").font(ski.type.display(30)).foregroundStyle(c.ink)
                        .environment(\.layoutDirection, .leftToRight)
                    Text(foot).font(ski.type.text(13)).foregroundStyle(c.muted)
                }
            }
        }
        .padding(.horizontal, 14).padding(.top, 18).padding(.bottom, 14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(c.paper)
        .overlay(alignment: .top) { Rectangle().fill(live ? c.green : c.dash).frame(height: 6) }
        .shadow(color: Color(rgb: 0x13233A, opacity: 0.1), radius: 6, y: 2)
        .opacity(live ? 1 : 0.62)
        .contentShape(Rectangle())
        .onTapGesture { if live { action() } }
        .accessibilityElement(children: .combine)
        .accessibilityLabel(S.statusHeadingLiftStatus + ": " + (live ? S.statusBarSummary(open: String(open), total: String(names.count), ago: foot) : foot))
    }

    private func agoText(_ s: LiftStatus) -> String {
        let m = s.minutesAgo
        // the same words as Android's status/StatusUi.kt agoText
        if m < 1 { return S.statusAgoNow }
        if m < 60 { return S.statusAgoMinutes(n: String(m)) }
        return S.statusAgoHours(n: String(Int((Double(m) / 60).rounded())))
    }
}

private struct FlowDots: View {
    let names: [String]
    let isOn: (String) -> Bool
    let on: Color
    let off: Color
    var body: some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 16, maximum: 16), spacing: 6)], alignment: .leading, spacing: 6) {
            ForEach(names, id: \.self) { n in
                if isOn(n) { Circle().fill(on).frame(width: 16, height: 16) }
                else { Circle().inset(by: 1).stroke(off, style: StrokeStyle(lineWidth: 2, dash: [5, 4])).frame(width: 16, height: 16) }
            }
        }
    }
}

/// A lift's cabin on its cable, the icon of the lift status (as Android's Icons.lift).
struct LiftIcon: Shape {
    func path(in r: CGRect) -> Path {
        let s = r.width / 24
        var p = Path()
        p.move(to: CGPoint(x: 2 * s, y: 5 * s)); p.addLine(to: CGPoint(x: 22 * s, y: 3 * s))
        p.move(to: CGPoint(x: 12 * s, y: 4 * s)); p.addLine(to: CGPoint(x: 12 * s, y: 9 * s))
        p.addRoundedRect(in: CGRect(x: 6 * s, y: 9 * s, width: 12 * s, height: 11 * s), cornerSize: CGSize(width: 2 * s, height: 2 * s))
        p.move(to: CGPoint(x: 6 * s, y: 14 * s)); p.addLine(to: CGPoint(x: 18 * s, y: 14 * s))
        return p
    }
}
