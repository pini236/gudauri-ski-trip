import SwiftUI
import GudiDomain

/// What the home board shows before the season opens (round 39, "the lift goes up"; Android's season/SeasonUi.kt): the state,
/// the date, and how far from the announcement to the date we are.
struct OpeningView {
    let kind: Season.Kind
    let days: Int
    let open: Day
    let progress: Double
    let postponed: Bool

    /// From the state and the season; nil when there is nothing to count (a fresh report, no date, or the date has passed).
    static func of(_ season: Season?, _ s: Season.State, today: Day) -> OpeningView? {
        guard let season, s.kind == .countdown || s.kind == .openingDay else { return nil }
        return OpeningView(kind: s.kind, days: s.days ?? 0, open: season.open, progress: season.progress(s, today: today), postponed: s.postponed)
    }
}

/// The ski-pass row on home (round 40): days and total when there is a trip with ski days and a price for them, else the day ticket.
struct SkipassEntry {
    let days: Int?
    let total: Int?
    let daily: Int
}

/// "20 Dec", "20 дек.", "20 დეკ.": the date as the app's language writes it.
func shortDateText(_ d: Day, _ lang: Lang) -> String {
    var cal = Calendar(identifier: .gregorian)
    cal.timeZone = TimeZone(identifier: "UTC")!
    let date = cal.date(from: DateComponents(year: d.year, month: d.month, day: d.day, hour: 12))!
    let f = DateFormatter()
    f.locale = Locale(identifier: lang.rawValue)
    f.calendar = cal
    f.timeZone = cal.timeZone
    f.setLocalizedDateFormatFromTemplate("d MMM")
    return f.string(from: date)
}

/// The board before the opening: how many days, and a lift line with a cabin that has gone part of the way up. On the day,
/// "opening day, waiting for the lifts' report". It is planned, and it can move; with a fresh lift report the board is the
/// lift board instead.
struct OpeningBoard: View {
    let v: OpeningView
    let skip: SkipassEntry?
    let onSkipass: () -> Void
    @Environment(\.ski) private var ski

    var body: some View {
        let c = ski.colors
        let lang = ski.type.lang
        let date = shortDateText(v.open, lang)
        let today = v.kind == .openingDay
        let tomorrow = v.kind == .countdown && v.days == 1
        let head = today ? S.appSeasonToday : tomorrow ? S.appSeasonTomorrow : v.postponed ? S.appSeasonMoved(date: date) : S.statusMountainAsleep
        let side = today ? S.appSeasonTodaySub : tomorrow ? S.appSeasonTomorrowSub(date: date) : v.postponed ? S.appSeasonMovedSub : S.appSeasonPlanned(date: date)
        let label = today ? head + ". " + side : S.appSeasonDaysA11y(count: v.days, date: date)
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .firstTextBaseline) {
                Text(head).font(ski.type.text(15, .bold)).foregroundStyle(c.ink).frame(maxWidth: .infinity, alignment: .leading)
                Text(side).font(ski.type.text(13)).foregroundStyle(c.muted)
            }
            if !today {
                HStack(alignment: .lastTextBaseline, spacing: 10) {
                    Text(String(v.days)).font(ski.type.display(84)).foregroundStyle(c.ink).environment(\.layoutDirection, .leftToRight)
                    Text(S.appSeasonDaysCaption(count: v.days)).font(ski.type.text(15)).foregroundStyle(c.muted)
                }
            }
            LiftLine(t: v.progress).frame(height: 120)
            Text(S.appSeasonSource).font(ski.type.text(12)).foregroundStyle(c.muted)
            if let skip {
                Rectangle().fill(c.rule).frame(height: 1)
                SkipassRow(entry: skip, action: onSkipass)
            }
        }
        .padding(.horizontal, 14).padding(.top, 18).padding(.bottom, 14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(c.paper)
        .overlay(alignment: .top) { Rectangle().fill(c.dash).frame(height: 6) }
        .shadow(color: Color(rgb: 0x13233A, opacity: 0.1), radius: 6, y: 2)
        // the countdown reads as one thing; the ski-pass row stays its own button inside it
        .accessibilityElement(children: .contain)
        .accessibilityLabel(label)
    }
}

/// A ridge, a lift line up it, and a cabin `t` of the way (0 to 1). Mirrored in right-to-left languages, so the climb always
/// goes toward the peak the way the text reads.
private struct LiftLine: View {
    let t: Double
    @Environment(\.ski) private var ski
    @Environment(\.layoutDirection) private var dir
    @Environment(\.accessibilityReduceMotion) private var still

    var body: some View {
        if still { canvas(sway: 0) }
        else {
            TimelineView(.animation(minimumInterval: 1.0 / 20)) { tl in
                // -3 to 3 degrees and back, every three seconds
                let phase = tl.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 3) / 3
                canvas(sway: 3 * sin(phase * 2 * .pi))
            }
        }
    }

    private func canvas(sway: Double) -> some View {
        let c = ski.colors
        let rtl = dir == .rightToLeft
        return Canvas { g, size in
            // the drawing is made in a 340 x 150 box
            let sx = size.width / 340, sy = size.height / 150
            func pt(_ x: Double, _ y: Double) -> CGPoint { CGPoint(x: (rtl ? 340 - x : x) * sx, y: y * sy) }
            let ridge = c.dark ? Color(rgb: 0x33507A) : Color(rgb: 0x9FB4CC)
            let snow = c.dark ? Color(rgb: 0xDCE6F5) : Color.white
            var m = Path()
            m.move(to: pt(0, 150)); m.addLine(to: pt(0, 118)); m.addLine(to: pt(60, 96)); m.addLine(to: pt(110, 108)); m.addLine(to: pt(170, 56))
            m.addLine(to: pt(215, 78)); m.addLine(to: pt(270, 22)); m.addLine(to: pt(340, 70)); m.addLine(to: pt(340, 150)); m.closeSubpath()
            g.fill(m, with: .color(ridge))
            var cap = Path()
            cap.move(to: pt(270, 22)); cap.addLine(to: pt(252, 40)); cap.addLine(to: pt(270, 36)); cap.addLine(to: pt(284, 42)); cap.closeSubpath()
            g.fill(cap, with: .color(snow))
            let p0 = pt(24, 128), p1 = pt(294, 24)
            var line = Path(); line.move(to: p0); line.addLine(to: p1)
            g.stroke(line, with: .color(c.ink), lineWidth: 1.5)
            let cx = p0.x + (p1.x - p0.x) * t, cy = p0.y + (p1.y - p0.y) * t
            var done = Path(); done.move(to: p0); done.addLine(to: CGPoint(x: cx, y: cy))
            g.stroke(done, with: .color(c.accent), style: StrokeStyle(lineWidth: 3.5, lineCap: .round))
            for p in [p0, p1] { g.fill(Path(ellipseIn: CGRect(x: p.x - 5, y: p.y - 5, width: 10, height: 10)), with: .color(c.ink)) }
            // the cabin hangs from the line and sways a little
            var cg = g
            cg.translateBy(x: cx, y: cy)
            cg.rotate(by: .degrees(sway))
            var hang = Path(); hang.move(to: .zero); hang.addLine(to: CGPoint(x: 0, y: 12))
            cg.stroke(hang, with: .color(c.ink), lineWidth: 1.5)
            let box = CGRect(x: -10, y: 12, width: 20, height: 15)
            cg.fill(Path(box), with: .color(Color(rgb: 0xFFC62B)))
            cg.stroke(Path(box), with: .color(c.ink), lineWidth: 1.5)
        }
        .accessibilityHidden(true)
    }
}

/// The ski-pass row: what the trip's ski days cost on one pass, or the day ticket's price; a tap opens the ski-pass screen.
struct SkipassRow: View {
    let entry: SkipassEntry
    let action: () -> Void
    @Environment(\.ski) private var ski

    var body: some View {
        let c = ski.colors
        let gel = { (n: Int) in S.appSkipassPrice(n: String(n)) }
        let tripCost = entry.days != nil && entry.total != nil
        let daily = gel(entry.daily)
        Button(action: action) {
            HStack(spacing: 10) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(tripCost ? S.appSkipassPassDays(count: entry.days!) : S.appSkipassTitle).font(ski.type.text(15, .bold)).foregroundStyle(c.ink)
                    if tripCost { Text(S.appSkipassDaily(price: daily)).font(ski.type.text(13)).foregroundStyle(c.muted) }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Text(tripCost ? gel(entry.total!) : S.appSkipassPerDay(price: daily)).font(ski.type.text(20, .bold)).foregroundStyle(c.ink)
                    .environment(\.layoutDirection, .leftToRight)
                Image(systemName: ski.type.lang.rtl ? "chevron.left" : "chevron.right").font(.system(size: 14, weight: .semibold)).foregroundStyle(c.glacier)
            }
            .frame(minHeight: 52)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(.isButton)
    }
}
