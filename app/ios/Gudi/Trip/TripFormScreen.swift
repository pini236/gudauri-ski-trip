import SwiftUI
import GudiDomain

/// "Your trip" (H2, decision 27; Android's trip/TripForm.kt): the outbound flight (only its date is required) and the
/// return, the full ski days worked out from them, save and delete. No sign-up: it stays on this phone (`AppModel.trip`).
///
/// Nothing is typed but the flight numbers (Pini, 2.10.2026): dates from the calendar, times from the wheel, airports
/// from a list; the destination starts as Tbilisi, and the way back is the way out reversed.
///
/// Round 20: with no trip yet ("when do you fly?" on the empty pass), the screen opens on the one calendar of two taps;
/// saving it fills the pass and goes back home. Closing the calendar leaves this full form.
struct TripFormScreen: View {
    /// The trip to edit; nil: the user's own (`AppModel.trip`). A group admin filling a member's flight passes theirs.
    var initial: Trip?? = nil
    var title: String? = nil
    var intro: String? = nil
    /// False for a member's flight an admin fixes: only the member deletes their own.
    var canDelete = true
    /// Where the trip goes; nil: the user's own (`AppModel.trip`).
    var onSave: ((Trip) -> Void)? = nil
    var onDelete: (() -> Void)? = nil

    @Environment(AppModel.self) private var app
    @Environment(\.ski) private var ski
    @Environment(\.dismiss) private var dismiss

    @State private var loaded = false
    @State private var hadTrip = false
    @State private var oDate: Day?
    @State private var oFlight = ""
    @State private var oFrom = ""
    @State private var oTo = ""
    @State private var oDep: ClockTime?
    @State private var oArr: ClockTime?
    @State private var rDate: Day?
    @State private var rFlight = ""
    @State private var rDep: ClockTime?
    @State private var rArr: ClockTime?
    /// The ski days set by hand ("change"), or follow the flights.
    @State private var manual = false
    @State private var sFrom: Day?
    @State private var sTo: Day?
    @State private var pick: Pick?
    @State private var calendar = false
    @State private var saved = false
    @State private var tried = false
    @State private var armed = false

    /// Which picker is open.
    enum Pick: String, Identifiable {
        case oDate, rDate, sFrom, sTo, oDep, oArr, rDep, rArr, oFrom, oTo
        var id: String { rawValue }
    }

    private var flights: Trip? {
        guard let d = oDate else { return nil }
        let f = { (s: String) in s.trimmingCharacters(in: .whitespaces) }
        return Trip(Leg(d, flight: f(oFlight), from: f(oFrom), to: f(oTo), departs: oDep, arrives: oArr),
                    rDate.map { Leg($0, flight: f(rFlight), from: f(oTo), to: f(oFrom), departs: rDep, arrives: rArr) })
    }
    private var orderBad: Bool { if let o = oDate, let r = rDate { return r < o }; return false }
    private var skiOk: Bool { !manual || (sFrom != nil && sTo != nil && sFrom! <= sTo!) }
    // both airports from the list or a three-letter code (K-5, the site's trip.bad_code)
    private var fromOk: Bool { Airports.ok(oFrom) }
    private var toOk: Bool { Airports.ok(oTo) }
    private var trip: Trip? {
        guard var t = flights, !orderBad, skiOk, fromOk, toOk else { return nil }
        t.ski = manual ? sFrom!...sTo! : nil
        return t
    }

    var body: some View {
        let c = ski.colors
        let lang = ski.type.lang
        Screen(title: title ?? S.appHomeTrip) {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    // round 20: no opening line for my own trip; a member's flight an admin fills still says whose
                    if let intro { Text(intro).font(ski.type.text(13.5)).foregroundStyle(c.muted) }
                    TripSection(title: S.appPassOut, note: S.appTripOutNote)
                    pair {
                        PickField(label: S.appTripDate, value: dayText(oDate, lang), hint: S.appTripPickDate, icon: "calendar",
                                  error: oDate == nil && tried ? S.appTripErrDate : nil) { pick = .oDate }
                    } _: {
                        FlightField(label: S.appTripFlightNo, text: $oFlight)
                    }
                    pair {
                        PickField(label: S.ticketFrom, value: oFrom.isEmpty ? nil : oFrom, hint: S.appTripPickPlace, icon: "airplane.departure",
                                  error: tried && !fromOk ? S.tripBadCode : nil) { pick = .oFrom }
                    } _: {
                        PickField(label: S.ticketTo, value: oTo.isEmpty ? nil : oTo, hint: S.appTripPickPlace, icon: "airplane.arrival",
                                  error: tried && !toOk ? S.tripBadCode : nil) { pick = .oTo }
                    }
                    pair {
                        PickField(label: S.ticketDeparts, value: oDep?.text, hint: S.appTripPickTime, icon: "clock", onClear: { oDep = nil }) { pick = .oDep }
                    } _: {
                        PickField(label: S.ticketArrives, value: oArr?.text, hint: S.appTripPickTime, icon: "clock", onClear: { oArr = nil }) { pick = .oArr }
                    }

                    TripSection(title: S.appPassRet, note: S.appTripRetNote)
                    let back = [Leg.codeOrPlace(oTo), Leg.codeOrPlace(oFrom)]
                    if back.allSatisfy({ !$0.isEmpty }) {
                        Text(S.appTripRetRoute(from: back[0], to: back[1])).font(ski.type.text(13)).foregroundStyle(c.muted)
                    }
                    pair {
                        PickField(label: S.appTripDate, value: dayText(rDate, lang), hint: S.appTripPickDate, icon: "calendar",
                                  error: orderBad ? S.appTripErrOrder : nil, onClear: { rDate = nil }) { pick = .rDate }
                    } _: {
                        FlightField(label: S.appTripFlightNo, text: $rFlight)
                    }
                    pair {
                        PickField(label: S.ticketDeparts, value: rDep?.text, hint: S.appTripPickTime, icon: "clock", onClear: { rDep = nil }) { pick = .rDep }
                    } _: {
                        PickField(label: S.ticketArrives, value: rArr?.text, hint: S.appTripPickTime, icon: "clock", onClear: { rArr = nil }) { pick = .rArr }
                    }

                    skiDays
                    if manual {
                        pair {
                            PickField(label: S.appTripSkiFirst, value: dayText(sFrom, lang), hint: S.appTripPickDate, icon: "calendar",
                                      error: sFrom == nil && tried ? S.appTripErrDate : nil) { pick = .sFrom }
                        } _: {
                            PickField(label: S.appTripSkiLast, value: dayText(sTo, lang), hint: S.appTripPickDate, icon: "calendar",
                                      error: sTo == nil && tried ? S.appTripErrDate
                                          : (sFrom != nil && sTo != nil && sTo! < sFrom!) ? S.appTripErrSkiOrder : nil) { pick = .sTo }
                        }
                    }

                    VStack(spacing: 6) {
                        TripButton(title: S.appSave, icon: "checkmark") { save() }
                        if hadTrip && canDelete {
                            TripQuietButton(title: armed ? S.tripDeleteConfirm : S.appTripDelete, danger: armed) {
                                if armed { delete() } else { armed = true; Fx.shared.tick(0.5) }
                            }
                        }
                    }
                    .padding(.top, 8)
                }
                .padding(.horizontal, 16).padding(.bottom, 24)
                .frame(maxWidth: 520)
                .frame(maxWidth: .infinity)
            }
            .scrollDismissesKeyboard(.interactively)
        }
        .sheet(item: $pick) { p in
            // a sheet is a new window: the app's look and direction go with it
            sheet(p).environment(\.ski, ski).environment(\.layoutDirection, lang.rtl ? .rightToLeft : .leftToRight)
                .presentationBackground(c.snow)
        }
        .sheet(isPresented: $calendar, onDismiss: { if saved { dismiss() } }) {
            CalendarSheet(trip: app.trip) { o, r in
                // the pass fills in; back home once the calendar is down
                app.trip = app.trip?.withDates(o, r) ?? PassEdits.newTrip(out: o, ret: r, tlv: airportPlace("TLV"), tbs: airportPlace("TBS"))
                saved = true
            }
            .environment(\.ski, ski)
            .environment(\.layoutDirection, lang.rtl ? .rightToLeft : .leftToRight)
            .presentationDetents([.large])
            .presentationBackground(c.snow)
        }
        .task(id: armed) {
            // the second tap must come within four seconds, as Android
            guard armed else { return }
            try? await Task.sleep(for: .seconds(4))
            armed = false
        }
        .onAppear(perform: load)
    }

    // MARK: - Pieces

    private func pair<A: View, B: View>(@ViewBuilder _ a: () -> A, @ViewBuilder _ b: () -> B) -> some View {
        HStack(alignment: .top, spacing: 10) {
            a().frame(maxWidth: .infinity)
            b().frame(maxWidth: .infinity)
        }
    }

    /// The ski days: worked out from the flights as on the site (Trip.flightSkiDays), or set by hand with "change".
    private var skiDays: some View {
        let c = ski.colors
        let t = trip ?? flights
        let days = t?.skiDays()
        return HStack(spacing: 0) {
            VStack(alignment: .leading, spacing: 2) {
                Text(manual ? S.tripSkiManual : S.appTripSkiCalc).font(ski.type.text(12, .semibold)).foregroundStyle(c.muted)
                Text(days.map { S.appTripSkiValue(count: t!.skiDayCount(), range: "\u{2066}" + dayRange($0) + "\u{2069}") } ?? S.appTripSkiNone)
                    .font(ski.type.display(30)).foregroundStyle(c.ink)
            }
            .padding(.vertical, 6)
            Spacer(minLength: 8)
            Button {
                if manual { manual = false } else {
                    // start from what the flights give, so changing a day is one pick
                    if let d = flights?.flightSkiDays() { if sFrom == nil { sFrom = d.lowerBound }; if sTo == nil { sTo = d.upperBound } }
                    manual = true
                }
            } label: {
                Text(manual ? S.appTripSkiAuto : S.appTripSkiChange).font(ski.type.text(13.5, .bold)).foregroundStyle(c.glacier)
                    .padding(.horizontal, 8).frame(minHeight: 44).contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
        .padding(.leading, 14).padding(.trailing, 8).padding(.top, 12).padding(.bottom, 6)
        .background(c.paper)
        .overlay(alignment: .top) { Rectangle().fill(c.blue).frame(height: 6) }
    }

    // each picker says which field it fills: "Outbound · Departs"
    @ViewBuilder
    private func sheet(_ p: Pick) -> some View {
        let out = S.appPassOut, ret = S.appPassRet
        let today = Day.today
        switch p {
        // the outbound from today on; the return and the ski days within the trip
        case .oDate:
            DateSheet(title: out + " · " + S.appTripDate, initial: oDate, open: today, min: today, max: nil) { d in
                oDate = d
                if let r = rDate, r < d { rDate = nil }
            }.presentationDetents([.large])
        case .rDate:
            DateSheet(title: ret + " · " + S.appTripDate, initial: rDate, open: oDate, min: oDate ?? today, max: nil) { rDate = $0 }
                .presentationDetents([.large])
        case .sFrom:
            DateSheet(title: S.appTripSkiFirst, initial: sFrom, open: oDate, min: oDate, max: rDate) { sFrom = $0 }
                .presentationDetents([.large])
        case .sTo:
            DateSheet(title: S.appTripSkiLast, initial: sTo, open: sFrom ?? oDate, min: sFrom ?? oDate, max: rDate) { sTo = $0 }
                .presentationDetents([.large])
        case .oDep: TimeSheet(title: out + " · " + S.ticketDeparts, initial: oDep) { oDep = $0 }.presentationDetents([.height(400)])
        case .oArr: TimeSheet(title: out + " · " + S.ticketArrives, initial: oArr ?? oDep) { oArr = $0 }.presentationDetents([.height(400)])
        case .rDep: TimeSheet(title: ret + " · " + S.ticketDeparts, initial: rDep) { rDep = $0 }.presentationDetents([.height(400)])
        case .rArr: TimeSheet(title: ret + " · " + S.ticketArrives, initial: rArr ?? rDep) { rArr = $0 }.presentationDetents([.height(400)])
        case .oFrom: PlaceSheet(title: S.appTripPlaceFrom, current: oFrom, first: ["TLV"]) { oFrom = $0 }.presentationDetents([.large])
        case .oTo: PlaceSheet(title: S.appTripPlaceTo, current: oTo, first: ["TBS", "KUT", "BUS"]) { oTo = $0 }.presentationDetents([.large])
        }
    }

    // MARK: - State

    private func load() {
        guard !loaded else { return }
        loaded = true
        let t: Trip? = initial ?? app.trip
        hadTrip = t != nil
        let o = t?.out, r = t?.ret
        oDate = o?.date; oFlight = o?.flight ?? ""
        // as on the site, a new trip starts from Tel Aviv to Tbilisi
        oFrom = t == nil ? airportPlace("TLV") : (o?.from ?? "")
        oTo = t == nil ? airportPlace("TBS") : (o?.to ?? "")
        oDep = o?.departs; oArr = o?.arrives
        rDate = r?.date; rFlight = r?.flight ?? ""; rDep = r?.departs; rArr = r?.arrives
        manual = t?.ski != nil; sFrom = t?.ski?.lowerBound; sTo = t?.ski?.upperBound
        // round 20: "when do you fly?" opens the one calendar (the screenshots of the full form ask for "trip:form")
        let qa = UserDefaults.standard.string(forKey: "qaRoute")
        if t == nil, onSave == nil, qa != "trip:form" { calendar = true }
        // the screenshots of the calendar over a trip (its two flights in ink, the ski days shaded)
        if qa == "trip:calendar", onSave == nil { calendar = true }
    }

    private func save() {
        tried = true
        guard let trip else { Fx.shared.tick(0.8); return }
        finish(trip)
    }

    private func finish(_ t: Trip) {
        Fx.shared.click(0.6)
        if let onSave { onSave(t) } else { app.trip = t }
        dismiss()
    }

    private func delete() {
        if let onDelete { onDelete() } else { app.trip = nil }
        dismiss()
    }
}

extension Leg {
    /// "TBS" for "TBS · Tbilisi", or the place as written.
    static func codeOrPlace(_ s: String) -> String {
        Leg(Day(2000, 1, 1)!, from: s).fromCode ?? s.trimmingCharacters(in: .whitespaces)
    }
}
