import SwiftUI
import GudiDomain

/// A new group (Q1): its name, its own dates (its countdown, and how long its invite works), your name in it, and
/// whether your trip shows there ("show my flight", round 18). You are its admin; a second admin is recommended.
struct NewGroupScreen: View {
    @Environment(AppModel.self) private var model
    @Environment(\.ski) private var ski
    @State private var name = ""
    @State private var from: Day?
    @State private var to: Day?
    @State private var me = ""
    @State private var showTrip = true
    @State private var r = GRunner()
    private var api: GroupAPI { GroupService.shared.api }

    var body: some View {
        let trip = model.trip
        Screen(title: S.appGNewTitle) {
            GPage {
                GField(label: S.appGGroupName, text: $name, hint: S.appGGroupNameHint, max: 60)
                GroupDates(from: $from, to: $to)
                GNote(S.appGDatesNote, "clock")
                GField(label: S.appGYourName, text: $me, hint: S.appGYourNameHint, max: 40)
                if let trip {
                    GToggle(label: S.groupShowMyTrip, sub: [trip.out.flight.isEmpty ? nil : trip.out.flight,
                                                            shortDate(trip.out.date) + (trip.out.departs.map { " · \($0.text)" } ?? "")]
                        .compactMap { $0 }.joined(separator: ", "), on: showTrip) { showTrip = $0 }
                }
                let ok = !name.trimmingCharacters(in: .whitespaces).isEmpty && !me.trimmingCharacters(in: .whitespaces).isEmpty
                GPrimary(text: S.appGCreateIt, icon: "person.2.fill", enabled: ok && !r.busy) {
                    r.run {
                        let id = try await api.createGroup(name: name, myName: me, startsOn: from, endsOn: to, myTrip: showTrip ? trip : nil)
                        Fx.shared.click()
                        model.replaceTop(.groups(.page(id, .flights)))
                    }
                }
                GErrorLine(runner: r)
                Spacer().frame(height: 8)
                GNote(S.appGAdminNote, "key")
            }
        }
        .onAppear {
            if me.isEmpty { me = api.me()?.name ?? "" }
            if from == nil { from = trip?.out.date; to = trip?.ret?.date }
        }
    }

    private var trip: Trip? { model.trip }
}

/// The group's dates (Q1, Q10): from the calendar, the end never before the start; both may stay empty.
struct GroupDates: View {
    @Binding var from: Day?
    @Binding var to: Day?
    @Environment(\.ski) private var ski
    @State private var pick: String?

    var body: some View {
        HStack(spacing: 10) {
            field(S.appGFromDate, from) { pick = "from" }
            field(S.appGToDate, to) { pick = "to" }
        }
        .sheet(item: Binding(get: { pick.map(Pick.init) }, set: { pick = $0?.id })) { p in
            DateSheet(title: p.id == "from" ? S.appGFromDate : S.appGToDate,
                      value: p.id == "from" ? from : to,
                      min: p.id == "from" ? Day.gOf(Date()) : (from ?? Day.gOf(Date()))) { d in
                if p.id == "from" { from = d; if let t = to, let d, t < d { to = nil } } else { to = d }
                pick = nil
            }
            .environment(\.ski, ski)
            .presentationDetents([.medium, .large])
        }
    }

    private struct Pick: Identifiable { let id: String }

    private func field(_ label: String, _ d: Day?, _ action: @escaping () -> Void) -> some View {
        let c = ski.colors
        return VStack(alignment: .leading, spacing: 5) {
            Text(label).font(ski.type.text(13, .semibold)).foregroundStyle(c.muted)
            Button(action: action) {
                HStack(spacing: 8) {
                    Image(systemName: "calendar").foregroundStyle(c.muted)
                    Text(d.map(gDayText) ?? S.appTripPickDate).font(ski.type.text(15)).foregroundStyle(d == nil ? c.muted : c.ink).lineLimit(1).minimumScaleFactor(0.7)
                    Spacer(minLength: 0)
                }
                .padding(.horizontal, 12).frame(height: 48)
                .background(c.paper)
                .overlay(Rectangle().inset(by: 0.75).stroke(c.rule, lineWidth: 1.5))
            }
            .buttonStyle(.plain)
            .accessibilityLabel(label)
        }
        .frame(maxWidth: .infinity)
    }
}

/// A date from the system's calendar, with its title, a clear button, and today as the first day.
struct DateSheet: View {
    let title: String
    let value: Day?
    let min: Day
    let done: (Day?) -> Void
    @State private var date = Date()
    @Environment(\.ski) private var ski

    var body: some View {
        GSheet {
            GDisplay(title, 30)
            DatePicker("", selection: $date, in: min.gDate..., displayedComponents: .date)
                .datePickerStyle(.graphical).labelsHidden()
                .environment(\.locale, Locale(identifier: ski.type.lang.rawValue))
                .tint(ski.colors.blue)
            HStack(spacing: 10) {
                GButton(text: S.appTripClear, look: .ghost) { done(nil) }
                GPrimary(text: S.appSave) { done(Day.gOf(date)) }
            }
        }
        .onAppear { date = (value ?? min).gDate }
    }
}

/// Inviting friends (Q2): the invite card with its six letters and the link, WhatsApp and share, and for admins the
/// invite's settings: approve each joiner by hand, how long it works, cancel it and make a new one.
struct InviteScreen: View {
    let groupId: String
    @Environment(\.ski) private var ski
    @Environment(\.openURL) private var openURL
    @State private var group: TripGroup?
    @State private var load = GRunner()
    @State private var r = GRunner()
    @State private var copied = false
    private var api: GroupAPI { GroupService.shared.api }

    var body: some View {
        let c = ski.colors
        Screen(title: S.appGInviteTitle) {
            GPage {
                if let g = group {
                    let inv = g.invite
                    Spacer().frame(height: 10)
                    if let inv {
                        InviteCard(groupName: g.name, invite: inv) {
                            UIPasteboard.general.string = InviteCode.shared(inv.token)
                            Fx.shared.tick(); copied = true
                        }
                    } else {
                        GMuted(S.groupNoInvite, 14)
                    }
                    let text = inv.map { S.appGInviteMessage(group: g.name, link: InviteCode.shared($0.token), code: $0.code) }
                    HStack(spacing: 10) {
                        GPrimary(text: S.appGWhatsapp, icon: "paperplane.fill", enabled: text != nil) {
                            if let t = text, let u = URL(string: "https://wa.me/?text=" + (t.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? "")) { openURL(u) }
                        }
                        if let text {
                            ShareLink(item: text) {
                                HStack(spacing: 8) {
                                    Image(systemName: "square.and.arrow.up").font(.system(size: 17, weight: .semibold))
                                    Text(S.appGShare).font(ski.type.text(16.5, .bold))
                                }
                                .foregroundStyle(c.ink).frame(maxWidth: .infinity, minHeight: 52)
                                .overlay(Rectangle().inset(by: 1).stroke(c.ink, lineWidth: 2))
                            }
                        }
                    }
                    if g.admin {
                        GDisplay(S.appGInviteSettings, 28).padding(.top, 8)
                        GToggle(label: S.groupApproval, sub: inv?.requiresApproval == true ? S.appGManualOn : S.appGManualOff,
                                on: inv?.requiresApproval == true, enabled: !r.busy) { on in
                            r.run { _ = try await api.newInvite(groupId, requiresApproval: on); group = try await api.group(groupId) }
                        }
                        VStack(alignment: .leading, spacing: 2) {
                            Text(S.appGValid).font(ski.type.text(15.5, .bold)).foregroundStyle(c.ink)
                            GMuted(g.endsOn.map { S.appGValidUntil(date: "\($0.day).\($0.month).\($0.year)") } ?? S.appGValid90, 13)
                        }
                        .padding(.vertical, 8)
                        Rectangle().fill(c.rule).frame(height: 1)
                        GButton(text: S.appGReplaceLink, look: .danger, icon: "arrow.triangle.2.circlepath") {
                            r.run { _ = try await api.newInvite(groupId, requiresApproval: inv?.requiresApproval == true); group = try await api.group(groupId) }
                        }
                        GMuted(S.appGReplaceNote, 12.5)
                        // the invite stops working, with no new one (as on the site); "new code and link" makes the next
                        if let inv { GQuiet(text: S.groupRevoke, danger: true) { r.run { try await api.revokeInvite(inv.id); group = try await api.group(groupId) } } }
                        GErrorLine(runner: r)
                    }
                } else if load.error != nil {
                    GErrorLine(runner: load)
                } else {
                    GMuted(S.appLoading)
                }
            }
        }
        .task { load.run { group = try await api.group(groupId) } }
        .alert(S.commonLinkCopied, isPresented: $copied) { Button("OK") {} }
    }
}

/// The invite card (Q2): paper, tilted a little, with fresh snow; the group, the six letters and the link.
struct InviteCard: View {
    let groupName: String
    let invite: Invite
    let onCopy: () -> Void
    @Environment(\.ski) private var ski

    var body: some View {
        let c = ski.colors
        GSnowCard(edge: .clear, seed: 41) {
            HStack {
                Text(S.appGInviteStrip).font(ski.type.text(12, .semibold))
                Spacer()
                Text(groupName).font(ski.type.text(12, .semibold)).lineLimit(1)
            }
            .foregroundStyle(Color(rgb: c.p.onAccent))
            .padding(.horizontal, 16).padding(.vertical, 7)
            .background(c.accent)
            .padding(.horizontal, -16).padding(.top, -16)
            GMuted(S.appGInviteHow, 13).padding(.top, 14).padding(.bottom, 12)
            HStack(spacing: 6) {
                ForEach(Array(invite.code.enumerated()), id: \.offset) { _, ch in
                    Text(String(ch)).font(.custom("Karantina-Bold", fixedSize: 46)).foregroundStyle(c.ink)
                        .frame(maxWidth: .infinity, minHeight: 64)
                        .background(c.bpPaper2)
                        .overlay(alignment: .bottom) { Rectangle().fill(c.accent).frame(height: 3) }
                }
            }
            .environment(\.layoutDirection, .leftToRight)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(S.appGCodeAria(code: invite.code))
            HStack(spacing: 8) {
                Image(systemName: "link").foregroundStyle(c.muted)
                Text(InviteCode.link(invite.token).replacingOccurrences(of: "https://", with: ""))
                    .font(ski.type.text(13.5)).foregroundStyle(c.ink).lineLimit(1).truncationMode(.middle)
                Spacer(minLength: 0)
                Button(action: onCopy) {
                    Image(systemName: "doc.on.doc").font(.system(size: 18)).foregroundStyle(c.glacier).frame(width: 44, height: 44)
                }
                .accessibilityLabel(S.appGCopyLink)
            }
            .environment(\.layoutDirection, .leftToRight)
            .padding(.leading, 12)
            .overlay(Rectangle().inset(by: 0.75).stroke(c.dash, lineWidth: 1.5))
            .padding(.top, 12)
        }
        .rotationEffect(.degrees(-1.2))
    }
}
