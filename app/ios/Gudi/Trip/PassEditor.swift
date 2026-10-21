import SwiftUI
import GudiDomain

/*
 * Round 20 (decision 65; Android's trip/PassForm.kt): the pass is the form. The empty pass has TLV and TBS written on it
 * and asks "when do you fly?": one calendar, two taps (out and back), the ski days shaded between them, and the pass
 * fills in. The flight number and the times wait on it as "+" fields; each field opens only its own sheet. Only the
 * outbound date is required; the full form stays behind "edit" (deleting, ski days by hand).
 */

/// Which field of the pass is being filled: the dates (the calendar), an airport, a flight number or a time.
enum PassEdit: Hashable, Identifiable {
    case dates
    case place(from: Bool)
    case flight(ret: Bool)
    case time(ret: Bool, departs: Bool)
    var id: Self { self }
}

/// The sheet of one field of the pass, for home to present (`.sheet(item: $edit) { PassEditor(edit: $0) }`): it reads
/// the trip from the app and saves it back. With no trip only the dates make sense (the calendar).
struct PassEditor: View {
    let edit: PassEdit
    @Environment(AppModel.self) private var app
    @Environment(\.ski) private var ski

    var body: some View {
        let trip = app.trip
        let tlv = airportPlace("TLV")
        let tbs = airportPlace("TBS")
        Group {
            switch edit {
            case .dates:
                CalendarSheet(trip: trip) { o, r in
                    app.trip = trip?.withDates(o, r) ?? PassEdits.newTrip(out: o, ret: r, tlv: tlv, tbs: tbs)
                }
                .presentationDetents([.large])
            case .place(let from):
                if let trip {
                    let o = trip.out
                    PlaceSheet(title: from ? S.appTripPlaceFrom : S.appTripPlaceTo, current: from ? o.from : o.to,
                               first: from ? ["TLV"] : ["TBS", "KUT", "BUS"]) { p in
                        app.trip = from ? trip.withPlaces(from: p, to: o.to) : trip.withPlaces(from: o.from, to: p)
                    }
                    .presentationDetents([.large])
                }
            case .flight(let ret):
                if let trip {
                    FlightSheet(current: (ret ? trip.ret : trip.out)?.flight ?? "") { f in
                        app.trip = trip.withLeg(ret: ret) { $0.flight = f }
                    }
                    .presentationDetents([.height(240)])
                }
            case .time(let ret, let departs):
                if let trip, let leg = ret ? trip.ret : trip.out {
                    TimeSheet(title: (ret ? S.appPassRet : S.appPassOut) + " · " + (departs ? S.ticketDeparts : S.ticketArrives),
                              initial: departs ? leg.departs : (leg.arrives ?? leg.departs)) { t in
                        app.trip = trip.withLeg(ret: ret) { if departs { $0.departs = t } else { $0.arrives = t } }
                    }
                    .presentationDetents([.height(400)])
                }
            }
        }
        .environment(\.ski, ski)
        // a sheet is a new window: the app's direction goes with it
        .environment(\.layoutDirection, ski.type.lang.rtl ? .rightToLeft : .leftToRight)
        .presentationBackground(ski.colors.snow)
    }
}

/// One calendar, two taps (round 20): the way out, then the way back, both in ink; the full ski days between them
/// shaded, as the pass will count them. Days before today cannot be picked. Only the way out is required.
struct CalendarSheet: View {
    let trip: Trip?
    let onSave: (Day, Day?) -> Void
    @Environment(\.ski) private var ski
    @Environment(\.dismiss) private var dismiss
    @State private var out: Day?
    @State private var ret: Day?
    /// The month shown, as its first day.
    @State private var month = Day(2027, 1, 1)!
    @State private var ready = false

    /// The ski days' light blue in the calendar (the canvas's trip-2, Android's SKI_SHADE); at night a deep blue.
    private var shade: Color { ski.colors.dark ? Color(rgb: ski.colors.p.blue, opacity: 0.32) : Color(rgb: 0xDCE8F6) }

    var body: some View {
        let c = ski.colors
        let lang = ski.type.lang
        let today = Day.today
        // the ski days as the pass will count them: the trip's own times, with the new dates
        let skiDays = out.flatMap { o in (trip ?? Trip(Leg(o))).withDates(o, ret).skiDays() }
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 0) {
                Text(monthTitle(lang)).font(ski.type.display(34)).foregroundStyle(c.ink).lineLimit(1).minimumScaleFactor(0.6)
                Spacer()
                arrow("chevron.backward", S.tripMonthPrev, enabled: month > Day(today.year, today.month, 1)!) { month = shift(-1) }
                arrow("chevron.forward", S.tripMonthNext, enabled: true) { month = shift(1) }
            }
            .padding(.trailing, -10)
            let cal = calendar(lang)
            let symbols = cal.veryShortStandaloneWeekdaySymbols
            HStack(spacing: 2) {
                ForEach(0..<7, id: \.self) { i in
                    Text(symbols[(cal.firstWeekday - 1 + i) % 7]).font(ski.type.text(12)).foregroundStyle(c.muted).frame(maxWidth: .infinity)
                }
            }
            .padding(.top, 10).padding(.bottom, 4)
            ForEach(Array(weeks(cal).enumerated()), id: \.offset) { _, week in
                HStack(spacing: 2) {
                    ForEach(0..<7, id: \.self) { i in
                        if let d = week[i] { cell(d, today: today, ski: skiDays, lang: lang) } else { Color.clear.frame(maxWidth: .infinity).frame(height: 44) }
                    }
                }
                .padding(.vertical, 2)
            }
            // the key: the two flights, and how many ski days
            HStack(spacing: 16) {
                key(c.ink, S.tripLegendFlights)
                let n = skiDays.map { $0.upperBound.epochDay - $0.lowerBound.epochDay + 1 } ?? 0
                if n > 0 { key(shade, S.tripLegendSki(count: n)) }
            }
            .padding(.top, 10)
            TripButton(title: S.appSave, enabled: out != nil) {
                guard let out else { return }
                Fx.shared.click(0.6)
                onSave(out, ret)
                dismiss()
            }
            .padding(.top, 16)
            Spacer(minLength: 0)
        }
        .padding(16)
        .background(c.snow)
        .onAppear {
            guard !ready else { return }
            ready = true
            out = trip?.out.date; ret = trip?.ret?.date
            let m = out ?? today
            month = Day(m.year, m.month, 1)!
        }
    }

    @ViewBuilder
    private func cell(_ d: Day, today: Day, ski skiDays: ClosedRange<Day>?, lang: Lang) -> some View {
        let c = ski.colors
        let past = d < today
        let flight = d == out || d == ret
        let shaded = !flight && (skiDays?.contains(d) ?? false)
        Button {
            let t = PassEdits.tap(out: out, ret: ret, day: d)
            out = t.out; ret = t.ret
            Fx.shared.tick(0.4)
        } label: {
            Text(String(d.day)).font(ski.type.text(15, .bold))
                .foregroundStyle(flight ? c.paper : shaded ? (c.dark ? c.ink : c.blue) : c.ink)
                .frame(maxWidth: .infinity).frame(height: 44)
                .background(flight ? c.ink : shaded ? shade : c.paper)
                .opacity(past ? 0.35 : 1)
        }
        .buttonStyle(.plain)
        .disabled(past)
        .accessibilityLabel(longDay(d, lang))
        .accessibilityAddTraits(flight ? .isSelected : [])
    }

    private func key(_ color: Color, _ text: String) -> some View {
        HStack(spacing: 6) {
            Rectangle().fill(color).frame(width: 12, height: 12).overlay(Rectangle().stroke(ski.colors.rule, lineWidth: 1))
            Text(text).font(ski.type.text(12.5)).foregroundStyle(ski.colors.muted)
        }
    }

    private func arrow(_ icon: String, _ label: String, enabled: Bool, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon).font(.system(size: 20, weight: .semibold)).foregroundStyle(ski.colors.ink)
                .frame(width: 44, height: 44).contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!enabled).opacity(enabled ? 1 : 0.3)
        .accessibilityLabel(label)
    }

    private func calendar(_ lang: Lang) -> Calendar {
        var cal = Day.calendar
        cal.locale = lang.locale
        return cal
    }

    private func shift(_ n: Int) -> Day {
        var y = month.year, m = month.month + n
        if m > 12 { m -= 12; y += 1 } else if m < 1 { m += 12; y -= 1 }
        return Day(y, m, 1)!
    }

    /// The month's days in weeks, from the locale's first day of the week, with blanks before the first.
    private func weeks(_ cal: Calendar) -> [[Day?]] {
        let weekday = cal.component(.weekday, from: month.date) // 1 = Sunday
        let lead = (weekday - cal.firstWeekday + 7) % 7
        let next = shift(1)
        let count = next.epochDay - month.epochDay
        let cells: [Day?] = Array(repeating: nil, count: lead) + (0..<count).map { month.plus(days: $0) }
        return stride(from: 0, to: cells.count, by: 7).map { i in
            (0..<7).map { j in i + j < cells.count ? cells[i + j] : nil }
        }
    }

    private func monthTitle(_ lang: Lang) -> String {
        let f = DateFormatter()
        f.calendar = Day.calendar
        f.locale = lang.locale
        f.dateFormat = "LLLL yyyy"
        let s = f.string(from: month.date)
        return s.prefix(1).uppercased() + s.dropFirst()
    }

    private func longDay(_ d: Day, _ lang: Lang) -> String {
        let f = DateFormatter()
        f.calendar = Day.calendar
        f.locale = lang.locale
        f.dateFormat = "EEEE d MMMM"
        return f.string(from: d.date)
    }
}
