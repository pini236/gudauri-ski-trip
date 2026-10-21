import SwiftUI
import GudiData
import GudiDomain

/// The group page (Q6, Q8, Q9, Q10; rounds 14, 18 and 20), as Android's group/GroupScreen.kt: its head over the view
/// from the village, and four tabs: flights, meetups, high scores, and members (requests and admin actions for admins).
/// After joining as a guest, the gentle offer to keep the place with Apple (A5), once now and once a week before the trip.
struct GroupPage: View {
    let groupId: String
    let tab: GroupTab
    /// A guest just joined: offer to keep the place (A5).
    static var justJoined = false

    @Environment(AppModel.self) private var model
    @Environment(\.ski) private var ski
    @AppStorage("dayNight") private var modeRaw = DayNight.Mode.auto.rawValue
    @AppStorage("group.saveOffers") private var saveOffers = 0
    @State private var shown = Shown(group: nil)
    @State private var watch: GroupWatch?
    @State private var sheet: Sheet?
    @State private var forMember: String?
    @State private var r = GRunner()
    private var api: GroupAPI { GroupService.shared.api }

    enum Sheet: String, Identifiable { case save, same, fill, edit; var id: String { rawValue } }

    var body: some View {
        let c = ski.colors
        let frame = DayNight.at(Int64(Date().timeIntervalSince1970 * 1000), DayNight.Mode(rawValue: modeRaw) ?? .auto)
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                head(frame)
                Spacer().frame(height: 40)
                tabs
                VStack(alignment: .leading, spacing: 12) { content }
                    .padding(16)
            }
            .frame(maxWidth: 560)
            .frame(maxWidth: .infinity)
        }
        .ignoresSafeArea(edges: .top)
        .background(c.snow)
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(.hidden, for: .navigationBar)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button { model.go(.groups(.invite(groupId))) } label: {
                    HStack(spacing: 6) {
                        Image(systemName: "plus").font(.system(size: 15, weight: .bold))
                        Text(S.appGInviteShort).font(ski.type.text(14, .bold))
                    }
                    .foregroundStyle(c.glacier).padding(.horizontal, 4)
                }
                .accessibilityLabel(S.appGInviteTitle)
            }
        }
        .task(id: groupId) {
            let w = api.watch(groupId)
            watch = w
            shown = w.now
            await w.open()
            var qa = AppModel.qaFlag("gsheet").flatMap(Sheet.init(rawValue:))
            for await s in w.states() {
                shown = s
                // the screenshots' sheet, once the group is there
                if let q = qa, s.group != nil { qa = nil; forMember = s.group?.members.first { $0.trip == nil && !$0.me }?.userId; sheet = q }
                offerSave()
            }
        }
        .onDisappear { let w = watch; Task { await w?.close() } }
        .sheet(item: $sheet) { s in
            sheetView(s).environment(\.ski, ski).environment(model).presentationDetents([.medium, .large])
        }
    }

    // A5: a guest who just joined is offered to keep the place, and once more a week before the trip; never again
    private func offerSave() {
        guard let g = shown.group, sheet == nil, api.me()?.registered == false else { return }
        let soon = g.startsOn.map { s in (0...7).contains(s.epochDay - Day.gOf(Date()).epochDay) } ?? false
        if (Self.justJoined && saveOffers == 0) || (saveOffers == 1 && soon) {
            Self.justJoined = false
            saveOffers += 1
            sheet = .save
        }
    }

    // MARK: the head: the view, and the group's card with its countdown

    private func head(_ frame: DayNight.Frame) -> some View {
        let c = ski.colors
        let g = shown.group
        return ZStack(alignment: .bottom) {
            Hero(frame: frame, height: 210)
            HStack(spacing: 0) {
                VStack(alignment: .leading, spacing: 2) {
                    GDisplay(g?.name ?? "…", 40)
                    if let g {
                        GMuted([g.startsOn.flatMap { a in g.endsOn.map { b in dayRange(a...max(a, b)) + ".\(b.year)" } }.map { "\u{2066}\($0)\u{2069}" },
                                S.appGMembers(count: g.members.count), S.appGAdmins(count: g.admins)].compactMap { $0 }.joined(separator: " · "), 13)
                    }
                }
                .padding(.horizontal, 14).padding(.vertical, 10)
                Spacer(minLength: 0)
                if let s = g?.startsOn {
                    let hours = (Double(s.epochDay) * 86400 - Date().timeIntervalSince1970 - Double(TimeZone.current.secondsFromGMT())) / 3600
                    let days = hours <= 0 ? 0 : Int((hours + 23) / 24)
                    VStack(spacing: 0) {
                        Text("\(days)").font(.custom("Karantina-Bold", fixedSize: 44)).foregroundStyle(c.onBoard)
                        Text(c.dark ? S.appPassNights(count: days) : S.appPassDays(count: days)).font(ski.type.text(11, .semibold)).foregroundStyle(c.onBoard)
                    }
                    .frame(width: 84).frame(maxHeight: .infinity).background(c.blue)
                }
            }
            .fixedSize(horizontal: false, vertical: true)
            .background(c.paper)
            .shadow(color: Color(rgb: 0x13233A, opacity: 0.15), radius: 8, y: 3)
            .padding(.horizontal, 16)
            .offset(y: 26)
        }
    }

    private var tabs: some View {
        let c = ski.colors
        let names: [GroupTab: String] = [.flights: S.appGTabFlights, .meetups: S.appGTabMeetups, .scores: S.groupTabScores, .members: S.appGTabMembers]
        // the site's .ac-tabs (round 18): each as wide as its word, in the sign voice at 24, a blue line under the chosen one
        return ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 0) {
                ForEach(GroupTab.allCases, id: \.self) { t in
                    let on = t == tab
                    Button { Fx.shared.tick(); model.replaceTop(.groups(.page(groupId, t))) } label: {
                        Text(names[t]!).font(ski.type.display(24)).foregroundStyle(on ? c.ink : c.muted).lineLimit(1)
                            .padding(.horizontal, 14).frame(minHeight: 46)
                            .overlay(alignment: .bottom) { if on { Rectangle().fill(c.blue).frame(height: 4) } }
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(on ? [.isSelected, .isHeader] : [])
                }
            }
        }
        .overlay(alignment: .bottom) { Rectangle().fill(c.rule).frame(height: 2) }
        .padding(.horizontal, 16)
    }

    @ViewBuilder private var content: some View {
        let c = ski.colors
        if let g = shown.group {
            if shown.gone { GNote(S.appGGone, "icloud.slash") }
            else if shown.offline { GNote(S.appGOffline(time: savedAt(shown.readAt)), "icloud.slash") }
            if shown.waiting { GMuted(S.appGWaiting, 12.5) }
            // a change that waited for signal and the server refused: say why (ד5), until closed
            if let code = shown.refused {
                HStack {
                    Text(S.appGRefused(reason: errorText(code))).font(ski.type.text(13.5, .bold)).foregroundStyle(c.red)
                    Spacer()
                    Button { Task { await watch?.clearRefused() } } label: {
                        Image(systemName: "xmark").foregroundStyle(c.ink).frame(width: 44, height: 44)
                    }
                    .accessibilityLabel(S.commonClose)
                }
            }
            switch tab {
            case .flights:
                FlightsTab(g: g, myTrip: model.trip,
                           onSame: { sheet = .same },
                           onSameAs: { tid in r.run { model.trip = try await api.sameFlight(g.id, tripId: tid); await watch?.refresh() } },
                           onShowMine: { r.run { try await api.showMyTrip(g.id, myName: g.me?.name ?? "", trip: model.trip); await watch?.refresh() } },
                           onFillFor: { id in forMember = id; sheet = .fill })
            case .meetups:
                MeetupsTab(g: g, onOpen: { m in model.go(.meet(station: m.station)) }, onNew: { model.go(.meet()) },
                           onDelete: { m in r.run { try await api.removeMeetup(g.id, meetupId: m.id); await watch?.refresh() } })
            case .scores:
                ScoresTab(g: g)
            case .members:
                MembersTab(g: g, r: r, reload: { await watch?.refresh() }, onInvite: { model.go(.groups(.invite(g.id))) },
                           onEdit: { sheet = .edit }, onLeft: { model.fromHome(nil) },
                           onFill: { m in forMember = m.userId; sheet = .fill })
            }
            GErrorLine(runner: r)
        } else if shown.gone {
            GMuted(S.appGGone, 15)
            GQuiet(text: S.navHome) { model.fromHome(nil) }
        } else if shown.offline {
            GNote(S.appGOfflineNever, "icloud.slash")
        } else {
            GMuted(S.appLoading)
        }
    }

    /// When the phone saved the group: the time today, else the day and the time.
    private func savedAt(_ ms: Int64?) -> String {
        guard let ms else { return "—" }
        let d = Date(timeIntervalSince1970: TimeInterval(ms) / 1000)
        let f = DateFormatter()
        f.dateFormat = Calendar.current.isDateInToday(d) ? "HH:mm" : "d.M HH:mm"
        return f.string(from: d)
    }

    @ViewBuilder private func sheetView(_ s: Sheet) -> some View {
        switch s {
        case .save: SaveOfferSheet()
        case .same, .fill:
            if let g = shown.group { FlightPickSheet(g: g, fill: s == .fill, forMember: forMember, watch: watch) }
        case .edit:
            if let g = shown.group { EditGroupSheet(g: g, watch: watch) }
        }
    }
}

// MARK: - Flights (Q6, Q7)

/// "TLV ✈ TBS", the plane pointing on.
struct GLegLine: View {
    let leg: Leg
    var size: CGFloat = 30
    @Environment(\.ski) private var ski
    var body: some View {
        let c = ski.colors
        HStack(spacing: 6) {
            Text(leg.fromCode ?? (leg.fromCity.isEmpty ? "—" : leg.fromCity)).font(.custom("Karantina-Bold", fixedSize: size)).foregroundStyle(c.ink)
            Image(systemName: "airplane").font(.system(size: size * 0.5)).foregroundStyle(c.glacier).flipsForRightToLeftLayoutDirection(true)
            Text(leg.toCode ?? (leg.toCity.isEmpty ? "—" : leg.toCity)).font(.custom("Karantina-Bold", fixedSize: size)).foregroundStyle(c.ink)
            if !leg.flight.isEmpty {
                Text(leg.flight).font(ski.type.text(12.5)).foregroundStyle(c.muted).environment(\.layoutDirection, .leftToRight).padding(.leading, 6)
            }
        }
    }
}

func gLegWhen(_ l: Leg, ret: Bool) -> String {
    var parts = [ret ? S.appPassRet : S.appPassOut, shortDate(l.date)]
    if let d = l.departs { parts.append(l.arrives.map { S.appGDepArr(dep: d.text, arr: $0.text) } ?? d.text) }
    return parts.joined(separator: " · ")
}

/// Which members share one flight card (X-4, the same on the site and Android): the direction, the date and the flight
/// number, normalised ("IZ 897" and "iz-897" are one flight); only without a number, the two airports.
struct GFlightKey: Hashable, Comparable {
    let ret: Bool
    let date: Day
    let flight: String, from: String, to: String

    static func of(_ l: Leg, ret: Bool) -> GFlightKey {
        let n = l.flight.uppercased().filter { !$0.isWhitespace && $0 != "-" }
        if !n.isEmpty { return GFlightKey(ret: ret, date: l.date, flight: n, from: "", to: "") }
        return GFlightKey(ret: ret, date: l.date, flight: "", from: (l.fromCode ?? l.from.trimmingCharacters(in: .whitespaces)).uppercased(),
                         to: (l.toCode ?? l.to.trimmingCharacters(in: .whitespaces)).uppercased())
    }

    static func < (a: GFlightKey, b: GFlightKey) -> Bool { a.ret != b.ret ? !a.ret : a.date < b.date }
}

struct FlightsTab: View {
    let g: TripGroup
    let myTrip: Trip?
    let onSame: () -> Void
    let onSameAs: (String) -> Void
    let onShowMine: () -> Void
    let onFillFor: (String) -> Void
    @Environment(\.ski) private var ski

    var body: some View {
        let c = ski.colors
        var legs: [GFlightKey: [(Leg, Member)]] = [:]
        for m in g.members {
            guard let t = m.trip else { continue }
            legs[GFlightKey.of(t.out, ret: false), default: []].append((t.out, m))
            if let r = t.ret { legs[GFlightKey.of(r, ret: true), default: []].append((r, m)) }
        }
        func dep(_ k: GFlightKey) -> Int { legs[k]?.first?.0.departs?.minutes ?? 9999 }
        let keys = legs.keys.sorted { a, b in
            if a.ret != b.ret || a.date != b.date { return a < b }
            return dep(a) < dep(b)
        }
        return VStack(alignment: .leading, spacing: 12) {
            ForEach(keys, id: \.self) { key in
                let rows = legs[key]!
                let leg = rows[0].0
                HStack(spacing: 0) {
                    VStack(alignment: .leading, spacing: 4) {
                        GLegLine(leg: leg)
                        GMuted(gLegWhen(leg, ret: key.ret), 12.5)
                        Flow(spacing: 6) {
                            ForEach(rows.indices, id: \.self) { k in
                                let m = rows[k].1
                                HStack(spacing: 4) {
                                    Text(m.name + (m.me ? " " + S.appGMeSuffix : "")).font(ski.type.text(13, .bold)).foregroundStyle(c.ink)
                                    if m.enteredByAdmin != nil { Image(systemName: "pencil").font(.system(size: 11)).foregroundStyle(c.muted) }
                                }
                                .padding(.horizontal, 10).frame(minHeight: 30)
                                .background(c.bpPaper2)
                                .overlay { if m.me { Rectangle().inset(by: 1).stroke(c.blue, lineWidth: 2) } }
                            }
                        }
                        .padding(.top, 4)
                        if let m = rows.map { $0.1 }.first(where: { $0.enteredByAdmin != nil }) {
                            GMuted(S.appGEnteredBy(admin: "\u{2068}\(m.enteredByAdmin!)\u{2069}", member: "\u{2068}\(m.name)\u{2069}"), 12).padding(.top, 2)
                        }
                        // as on the site: on every outbound flight I am not on (the trip is copied whole, so not on a return)
                        if !key.ret, !rows.contains(where: { $0.1.me }), let tid = rows[0].1.tripId {
                            GButton(text: S.groupSameFlight, look: .ghost, small: true, full: false) { onSameAs(tid) }.padding(.top, 4)
                        }
                    }
                    .padding(.horizontal, 14).padding(.top, 10).padding(.bottom, 12)
                    Spacer(minLength: 0)
                    VStack(spacing: 0) {
                        Text("\(rows.count)").font(.custom("Karantina-Bold", fixedSize: 34)).foregroundStyle(c.ink)
                        Text(S.appGOnFlight).font(ski.type.text(11, .semibold)).foregroundStyle(c.muted).multilineTextAlignment(.center)
                    }
                    .frame(width: 64).frame(maxHeight: .infinity)
                    .background(c.bpPaper2)
                    .overlay(alignment: .leading) {
                        // the perforation on the seam with the pass, at its start side (PARITY A-39)
                        GSeam().stroke(c.dash, style: StrokeStyle(lineWidth: 2, dash: [6, 4])).frame(width: 2)
                    }
                }
                .fixedSize(horizontal: false, vertical: true)
                .background(c.bpPaper)
                .shadow(color: Color(rgb: 0x13233A, opacity: 0.15), radius: 5, y: 2)
            }
            ForEach(g.members.filter { $0.trip == nil }) { m in
                HStack(spacing: 12) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(m.name + (m.me ? " " + S.appGMeSuffix : "")).font(ski.type.text(15, .bold)).foregroundStyle(c.ink)
                        GMuted(S.groupNoFlight, 12.5)
                    }
                    Spacer(minLength: 0)
                    if m.me && myTrip != nil {
                        GButton(text: S.appGShowMine, look: .ink, small: true, full: false, action: onShowMine)
                    } else if m.me && g.members.contains(where: { $0.trip != nil }) {
                        GPrimary(text: S.appGSameFlight, full: false, action: onSame)
                    } else if !m.me && g.admin {
                        GButton(text: S.appGFillFor, look: .ghost, small: true, full: false) { onFillFor(m.userId) }
                    }
                }
                .padding(.horizontal, 14).padding(.vertical, 12)
                .overlay(Rectangle().inset(by: 1).stroke(c.dash, style: StrokeStyle(lineWidth: 2, dash: [6, 4])))
            }
            GNote(S.appGFlightsPrivate, "lock")
        }
    }
}

/// "I'm on the same flight" (Q7), or an admin filling in a member's flight from those in the group.
struct FlightPickSheet: View {
    let g: TripGroup
    let fill: Bool
    let forMember: String?
    let watch: GroupWatch?
    @Environment(AppModel.self) private var model
    @Environment(\.ski) private var ski
    @Environment(\.dismiss) private var dismiss
    @State private var picked: String?
    @State private var r = GRunner()
    private var api: GroupAPI { GroupService.shared.api }

    var body: some View {
        let c = ski.colors
        var seen: [Trip] = []
        let trips = g.members.filter { m in
            guard let t = m.trip, m.tripId != nil, !seen.contains(t) else { return false }
            seen.append(t); return true
        }
        return GSheet {
            GDisplay(fill ? S.appGFillFor : S.appGSameFlight, 36)
            GMuted(fill ? S.appGFillSub : S.appGSameSub, 14).padding(.top, 6).padding(.bottom, 14)
            VStack(spacing: 10) {
                ForEach(trips) { m in
                    let t = m.trip!, sel = picked == m.tripId
                    let on = g.members.filter { $0.trip == t }.count
                    Button { picked = m.tripId; Fx.shared.tick() } label: {
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                GLegLine(leg: t.out, size: 28)
                                GMuted(gLegWhen(t.out, ret: false) + " · " + S.appGFromGroup(count: on), 12.5)
                            }
                            Spacer()
                            if sel { Image(systemName: "checkmark").foregroundStyle(c.glacier) }
                        }
                        .padding(.horizontal, 14).padding(.vertical, 8).frame(minHeight: 64)
                        .background(c.bpPaper2)
                        .overlay(Rectangle().inset(by: sel ? 1 : 0.5).stroke(sel ? c.blue : c.rule, lineWidth: sel ? 2 : 1))
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(sel ? .isSelected : [])
                }
            }
            VStack(spacing: 6) {
                GPrimary(text: S.appSave, icon: "checkmark", enabled: picked != nil && !r.busy) {
                    guard let tid = picked else { return }
                    r.run {
                        if fill {
                            guard let who = forMember, let t = g.members.first(where: { $0.tripId == tid })?.trip else { return }
                            try await api.setMemberTrip(g.id, userId: who, trip: t)
                        } else {
                            model.trip = try await api.sameFlight(g.id, tripId: tid)
                        }
                        await watch?.refresh()
                        dismiss()
                    }
                }
                // another flight: my own trip form (filling a new flight for a member waits for the trip part)
                if !fill { GQuiet(text: S.appGOtherFlight) { dismiss(); model.go(.trip) } }
                GErrorLine(runner: r)
            }
            .padding(.top, 14)
        }
    }
}

/// The group's name and dates, for its admins (Q10, round 14).
struct EditGroupSheet: View {
    let g: TripGroup
    let watch: GroupWatch?
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var from: Day?
    @State private var to: Day?
    @State private var r = GRunner()

    var body: some View {
        GSheet {
            GDisplay(S.appGNameDates, 32)
            VStack(alignment: .leading, spacing: 12) {
                GField(label: S.appGGroupName, text: $name, max: 60)
                GroupDates(from: $from, to: $to)
                GPrimary(text: S.appSave, icon: "checkmark", enabled: !name.trimmingCharacters(in: .whitespaces).isEmpty && !r.busy) {
                    r.run { try await GroupService.shared.api.updateGroup(g.id, name: name, startsOn: from, endsOn: to); await watch?.refresh(); dismiss() }
                }
                GErrorLine(runner: r)
            }
            .padding(.top, 12)
        }
        .onAppear { name = g.name; from = g.startsOn; to = g.endsOn }
    }
}

/// A5: you joined without signing up, and that is enough; one tap keeps your place.
struct SaveOfferSheet: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var r = GRunner()
    var body: some View {
        GSheet {
            GDisplay(S.appAWelcome, 36)
            GMuted(S.appAWelcomeSub, 14).padding(.top, 6).padding(.bottom, 16)
            VStack(spacing: 10) {
                GAppleButton(text: S.acctApple) { r.run { try await SignIn.run("apple", model: model); dismiss() } }
                if SignIn.googleReady || GroupService.shared.fake {
                    GGoogleButton(text: S.appASaveGoogle) { r.run { try await SignIn.run("google", model: model); dismiss() } }
                }
                GErrorLine(runner: r)
                GQuiet(text: S.appANotNow) { dismiss() }
            }
            GMuted(S.appAAskOnceMore, 12.5).padding(.top, 12)
        }
    }
}

// MARK: - Meetups (Q8)

/// Every meetup of the group, by day, who set it; a new one is picked on the meeting-point map, and a tap opens its card
/// there. The next one says how long until it. The X deletes for the whole group, with a second tap (as on the site).
struct MeetupsTab: View {
    let g: TripGroup
    let onOpen: (GroupMeetup) -> Void
    let onNew: () -> Void
    let onDelete: (GroupMeetup) -> Void
    @Environment(AppModel.self) private var model
    @Environment(\.ski) private var ski
    @State private var armed: String?

    private static let gudauri = TimeZone(secondsFromGMT: 4 * 3600)!

    var body: some View {
        let c = ski.colors
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = Self.gudauri
        let sorted = g.meetups.sorted { $0.at < $1.at }
        let days = Dictionary(grouping: sorted) { m -> Day in let d = cal.dateComponents([.year, .month, .day], from: m.at); return Day(d.year!, d.month!, d.day!)! }
        let now = Date()
        let next = sorted.first { $0.at > now }
        return VStack(alignment: .leading, spacing: 12) {
            if sorted.isEmpty { GMuted(S.appGNoMeetups, 14) }
            ForEach(days.keys.sorted(), id: \.self) { day in
                Text(shortDate(day)).font(ski.type.text(13, .semibold)).foregroundStyle(c.muted)
                ForEach(days[day]!) { m in
                    let t = cal.dateComponents([.hour, .minute], from: m.at)
                    HStack(spacing: 0) {
                        Button { onOpen(m) } label: {
                            HStack(spacing: 0) {
                                VStack(spacing: 0) {
                                    GMuted(shortDate(day), 12)
                                    Text(String(format: "%02d:%02d", t.hour!, t.minute!)).font(.custom("Karantina-Bold", fixedSize: 36)).foregroundStyle(c.ink)
                                }
                                .frame(width: 76).environment(\.layoutDirection, .leftToRight)
                                VStack(alignment: .leading, spacing: 3) {
                                    HStack(spacing: 8) {
                                        Rectangle().fill(spot(m) ?? c.rule).frame(width: 12, height: 12)
                                        Text(stationName(m.station)).font(ski.type.text(15.5, .bold)).foregroundStyle(c.ink).multilineTextAlignment(.leading)
                                    }
                                    let left = m == next ? S.appGIn(time: inWords(m.at.timeIntervalSince(now))) : nil
                                    let line = [m.byName.map { S.appGSetBy(name: $0) }, left].compactMap { $0 }.joined(separator: " · ")
                                    if !line.isEmpty { GMuted(line, 12.5) }
                                    if let n = m.note { GMuted(n, 12.5) }
                                }
                                .padding(.horizontal, 14).padding(.vertical, 10)
                                Spacer(minLength: 0)
                            }
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        Button {
                            if armed == m.id { armed = nil; onDelete(m) } else { armed = m.id; Fx.shared.tick() }
                        } label: {
                            Group {
                                if armed == m.id {
                                    Text(S.tripDeleteConfirm).font(ski.type.text(12, .bold)).foregroundStyle(c.red).frame(maxWidth: 90).padding(.horizontal, 4)
                                } else {
                                    Image(systemName: "xmark").font(.system(size: 15)).foregroundStyle(c.muted).frame(width: 44)
                                }
                            }
                            .frame(minHeight: 44)
                        }
                        .accessibilityLabel(S.appGMeetupDelete)
                    }
                    .background(c.bpPaper)
                    .overlay { if m == next { Rectangle().inset(by: 1).stroke(c.blue, lineWidth: 2) } }
                    .shadow(color: Color(rgb: 0x13233A, opacity: 0.12), radius: 5, y: 2)
                }
            }
            GButton(text: S.appGNewMeetup, look: .ink, icon: "mappin", action: onNew)
            GNote(S.appGMeetupsOffline, "iphone")
        }
        .task(id: armed) { if armed != nil { try? await Task.sleep(for: .seconds(4)); armed = nil } }
    }

    /// The station's name as the meeting point gives it: the lift and which end ("158744075b": bottom of Goodaura).
    private func stationName(_ id: String) -> String {
        let top = id.hasSuffix("t")
        guard let lift = model.runs?.lifts.first(where: { $0.id == String(id.dropLast()) }) else { return id }
        let n = "\u{2068}\(lift.name)\u{2069}"
        return top ? S.meetWhereTopStation(lifts: n) : S.meetWhereBottomStation(lifts: n)
    }

    /// The colour of the meeting point's post, when the meetup is one of its three spots (M2).
    private func spot(_ m: GroupMeetup) -> Color? {
        guard let lift = model.runs?.lifts.first(where: { $0.id == String(m.station.dropLast()) }) else { return nil }
        let top = m.station.hasSuffix("t")
        if lift.name == "Goodaura" { return top ? ski.colors.blue : gGold }
        if lift.name == "New Goodaura" && !top { return ski.colors.ink }
        return nil
    }

    /// "שעה ו-12 דקות", "1 hour, 12 minutes": how long until a meetup, in the app's language.
    private func inWords(_ seconds: TimeInterval) -> String {
        let m = max(1, Int(seconds / 60))
        let f = DateComponentsFormatter()
        f.unitsStyle = .full
        var cal = Calendar.current; cal.locale = Locale(identifier: ski.type.lang.rawValue)
        f.calendar = cal
        if m < 60 { f.allowedUnits = [.minute]; return f.string(from: TimeInterval(m * 60)) ?? "" }
        if m < 24 * 60 { f.allowedUnits = [.hour, .minute]; return f.string(from: TimeInterval(m * 60)) ?? "" }
        f.allowedUnits = [.day]
        return f.string(from: TimeInterval(((m + 24 * 60 - 1) / (24 * 60)) * 86400)) ?? ""
    }
}

// MARK: - Scores (Q9)

struct ScoresTab: View {
    let g: TripGroup
    @Environment(\.ski) private var ski
    @State private var game = GroupService.games[0]
    @State private var rows: [GroupScore]?
    @State private var r = GRunner()

    private func name(_ k: String) -> String {
        switch k { case "descent": S.gamesDescentName; case "school": S.gamesSchoolName; case "fresh": S.gamesFreshName
        case "snowball": S.gamesSnowballName; default: S.gamesMergeName }
    }

    var body: some View {
        let c = ski.colors
        VStack(alignment: .leading, spacing: 12) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(GroupService.games, id: \.self) { k in
                        let on = k == game
                        Button { game = k } label: {
                            Text(name(k)).font(ski.type.text(14, .bold)).foregroundStyle(on ? c.onBoard : c.ink)
                                .padding(.horizontal, 14).frame(minHeight: 44)
                                .background(on ? c.blue : c.paper)
                                .overlay { if !on { Rectangle().inset(by: 0.5).stroke(c.rule, lineWidth: 1) } }
                        }
                        .buttonStyle(.plain)
                        .accessibilityAddTraits(on ? .isSelected : [])
                    }
                }
            }
            if let list = rows {
                if list.isEmpty { GMuted(S.appGNoScores, 14) }
                else {
                    VStack(spacing: 0) {
                        ForEach(Array(list.enumerated()), id: \.offset) { i, s in
                            HStack(spacing: 12) {
                                Text("\(i + 1)").font(.custom("Karantina-Bold", fixedSize: 30)).foregroundStyle(i == 0 ? gGold : c.ink).frame(width: 28)
                                Text(s.name + (s.me ? " " + S.appGMeSuffix : "")).font(ski.type.text(15, .bold)).foregroundStyle(c.ink)
                                Spacer()
                                Text(s.best.formatted()).font(.custom("Karantina-Bold", fixedSize: 30)).foregroundStyle(c.ink).environment(\.layoutDirection, .leftToRight)
                            }
                            .padding(.horizontal, 12).frame(minHeight: 54)
                            .background(i == 0 ? gGold.opacity(0.2) : c.paper)
                            Rectangle().fill(c.rule).frame(height: 1)
                        }
                    }
                    .shadow(color: Color(rgb: 0x13233A, opacity: 0.12), radius: 5, y: 2)
                }
            } else if r.error != nil { GErrorLine(runner: r) } else { GMuted(S.appLoading) }
            GNote(S.appGScoresNote, "trophy")
        }
        .task(id: game) { rows = nil; r.run { rows = try await GroupService.shared.api.leaderboard(g.id, game: game) } }
    }
}

// MARK: - Members (Q10)

/// Requests waiting for an admin, the members with their roles and the admin's menu for each, and the group's actions.
/// Leaving, removing and deleting ask twice.
struct MembersTab: View {
    let g: TripGroup
    let r: GRunner
    let reload: () async -> Void
    let onInvite: () -> Void
    let onEdit: () -> Void
    let onLeft: () -> Void
    let onFill: (Member) -> Void
    @Environment(\.ski) private var ski
    @State private var armed: String?
    @State private var removing: Member?
    private var api: GroupAPI { GroupService.shared.api }

    var body: some View {
        let c = ski.colors
        VStack(alignment: .leading, spacing: 12) {
            if g.admin && !g.requests.isEmpty {
                Text(S.appGRequests).font(ski.type.text(13, .semibold)).foregroundStyle(c.muted)
                ForEach(g.requests) { q in
                    GSnowCard(edge: gGold, seed: abs(q.id.hashValue % 97)) {
                        Text(q.reclaim ? S.appGRequestReclaim(name: "\u{2068}\(q.name)\u{2069}") : S.appGRequestJoin(name: "\u{2068}\(q.name)\u{2069}")).font(ski.type.text(15.5, .bold)).foregroundStyle(c.ink)
                        GMuted(q.reclaim ? S.appGRequestReclaimSub : S.appGRequestJoinSub, 12.5)
                        HStack(spacing: 8) {
                            GPrimary(text: S.groupApprove, icon: "checkmark") { r.run { try await api.decide(q.id, approve: true); await reload() } }
                            GButton(text: S.appGReject, look: .ghost, icon: "xmark", small: true) { r.run { try await api.decide(q.id, approve: false); await reload() } }
                        }
                        .padding(.top, 10)
                    }
                }
            }
            VStack(spacing: 0) {
                ForEach(g.members) { m in
                    HStack(spacing: 12) {
                        GAvatar(name: m.name, strong: m.role == .admin)
                        VStack(alignment: .leading, spacing: 1) {
                            Text(m.name + (m.me ? " " + S.appGMeSuffix : "")).font(ski.type.text(15, .bold)).foregroundStyle(c.ink)
                            GMuted(m.role == .admin ? S.appGRoleAdmin : S.appGRoleMember, 12.5)
                        }
                        Spacer(minLength: 0)
                        if g.admin && !m.me {
                            Menu {
                                if m.role == .member {
                                    Button(S.appGMakeAdmin) { r.run { try await api.setRole(g.id, userId: m.userId, role: .admin); await reload() } }
                                } else {
                                    Button(S.appGUnmakeAdmin) { r.run { try await api.setRole(g.id, userId: m.userId, role: .member); await reload() } }
                                }
                                // unless the member entered the flight themselves (A-20: a flight an admin entered can be fixed)
                                if m.trip == nil || m.enteredByAdmin != nil, g.members.contains(where: { $0.trip != nil }) {
                                    Button(S.groupFillTrip) { onFill(m) }
                                }
                                Button(S.appGRemove, role: .destructive) { removing = m }
                            } label: {
                                Image(systemName: "ellipsis").font(.system(size: 18, weight: .semibold)).foregroundStyle(c.muted).frame(width: 44, height: 44)
                            }
                            .accessibilityLabel(S.appGActionsOn(name: m.name))
                        }
                    }
                    .frame(minHeight: 56)
                    .overlay(alignment: .bottom) { Rectangle().fill(c.rule).frame(height: 1) }
                }
            }
            if g.admin { GMuted(S.appGMenuNote, 13) }
            // no admin left (the last one deleted the account and only guests remained): a registered member takes it on
            if !g.members.contains(where: { $0.role == .admin }) && api.me()?.registered == true {
                GPrimary(text: S.groupClaimAdmin, icon: "checkmark") { r.run { try await api.claimAdmin(g.id); await reload() } }
            }
            VStack(spacing: 4) {
                if g.admin { GQuiet(text: S.appGNameDates, action: onEdit) }
                GQuiet(text: S.appGInviteSettingsLink, action: onInvite)
                GQuiet(text: armed == "leave" ? S.groupLeaveConfirm : S.appGLeave, danger: armed == "leave") {
                    if armed == "leave" { r.run { try await api.leave(g.id); onLeft() } } else { armed = "leave"; Fx.shared.tick() }
                }
                if g.admin {
                    GButton(text: armed == "delete" ? S.groupDeleteConfirm : S.appGDelete, look: .danger, icon: "trash") {
                        if armed == "delete" { r.run { try await api.deleteGroup(g.id); onLeft() } } else { armed = "delete"; Fx.shared.tick() }
                    }
                }
            }
        }
        .task(id: armed) { if armed != nil { try? await Task.sleep(for: .seconds(4)); armed = nil } }
        // out of the group in two taps, as on the site: the system asks once more
        .confirmationDialog(removing.map { S.groupRemoveConfirm(name: $0.name) } ?? "", isPresented: Binding(get: { removing != nil }, set: { if !$0 { removing = nil } }),
                            titleVisibility: .visible) {
            if let m = removing {
                Button(S.appGRemove, role: .destructive) { r.run { try await api.removeMember(g.id, userId: m.userId); await reload() } }
            }
        }
    }
}

/// A vertical line down the middle of its frame (the pass's perforation).
struct GSeam: Shape {
    func path(in r: CGRect) -> Path { var p = Path(); p.move(to: CGPoint(x: r.midX, y: r.minY)); p.addLine(to: CGPoint(x: r.midX, y: r.maxY)); return p }
}
