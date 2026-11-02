import SwiftUI
import GudiDomain

/// Typing the code (Q4): six letters in six boxes, for whoever installed from the store and lost the link on the way.
/// One hidden field under the boxes takes the typing; letters only, without I and O (the server's alphabet).
struct CodeScreen: View {
    @Environment(AppModel.self) private var model
    @Environment(\.ski) private var ski
    @State private var code = ""
    @FocusState private var focused: Bool

    var body: some View {
        let c = ski.colors
        Screen(title: S.appGCodeTitle) {
            GPage {
                GMuted(S.appGCodeWhy, 14)
                Spacer().frame(height: 10)
                Text(S.appGSixLetters).font(ski.type.text(13, .semibold)).foregroundStyle(c.muted)
                ZStack {
                    TextField("", text: $code)
                        .focused($focused)
                        .textInputAutocapitalization(.characters).autocorrectionDisabled().keyboardType(.asciiCapable)
                        .foregroundStyle(.clear).tint(.clear)
                        .accessibilityLabel(S.appGCodeLabel)
                        .onChange(of: code) { _, v in
                            if let l = InviteCode.fromLink(v) { go(l); return }
                            let k = InviteCode.clean(v); if k != v { code = k }
                        }
                    // the boxes read left to right in every language (Latin letters)
                    HStack(spacing: 6) {
                        ForEach(0..<6, id: \.self) { i in
                            let ch = i < code.count ? String(Array(code)[i]) : ""
                            Text(ch).font(.custom("Karantina-Bold", fixedSize: 44)).foregroundStyle(c.ink)
                                .frame(maxWidth: .infinity, minHeight: 64)
                                .background(c.paper)
                                .overlay(Rectangle().inset(by: 1).stroke(!ch.isEmpty || i == code.count ? c.ink : c.rule, lineWidth: 2))
                        }
                    }
                    .environment(\.layoutDirection, .leftToRight)
                    .contentShape(Rectangle())
                    .onTapGesture { focused = true }
                    .accessibilityHidden(true)
                }
                GPrimary(text: S.appGContinue, enabled: InviteCode.isCode(code)) { go(code) }
                GNote(S.appGCodeLetters, "key")
            }
        }
        .onAppear { focused = true }
    }

    private func go(_ c: String) { model.replaceTop(.groups(.join(c))) }
}

/// The invitation (Q3, round 20): which group, your name in it, and in. A guest gets an anonymous identity first, without
/// being asked (docs/USERS.md: "invited without signing up"). Also the way to "I'm already in the group" (Q5), and the
/// answers that are not "joined": waiting for an admin, a code that does not work, too many tries.
struct InvitedScreen: View {
    let code: String
    @Environment(AppModel.self) private var model
    @Environment(\.ski) private var ski
    @AppStorage("dayNight") private var modeRaw = DayNight.Mode.auto.rawValue
    @State private var preview: InvitePreview?
    @State private var result: JoinStatus?
    @State private var name = ""
    @State private var load = GRunner()
    @State private var r = GRunner()
    @State private var stop: (() -> Void)?
    private var api: GroupAPI { GroupService.shared.api }

    var body: some View {
        let c = ski.colors
        let frame = DayNight.at(Int64(Date().timeIntervalSince1970 * 1000), DayNight.Mode(rawValue: modeRaw) ?? .auto)
        ScrollView {
            ZStack(alignment: .top) {
                Hero(frame: frame, height: 260)
                VStack(alignment: .leading, spacing: 12) {
                    Spacer().frame(height: 150)
                    GSnowCard(edge: c.blue, seed: 51) {
                        if let p = preview {
                            if p.status != .ok {
                                let limited = p.status == .rate_limited
                                GDisplay(limited ? S.appGTooManyTries : S.appGBadCode, 30)
                                GMuted(limited ? S.appGTooManyTriesSub : S.appGBadCodeSub).padding(.top, 6)
                                Spacer().frame(height: 10)
                                GPrimary(text: S.appGRetype) { model.replaceTop(.groups(.code)) }
                            } else {
                                JoinHead(p: p)
                            }
                        } else if load.error != nil {
                            GErrorLine(runner: load)
                            GQuiet(text: S.appGRetype) { model.replaceTop(.groups(.code)) }
                        } else {
                            GMuted(S.appLoading)
                        }
                    }
                    if let p = preview, p.status == .ok {
                        if result == .pending {
                            GPendingCard { r.run { if let g = p.groupId { try await api.cancelRequest(g) }; model.path.removeLast() } }
                        } else {
                            GField(label: S.appGYourName, text: $name, hint: S.appGYourNameHint, max: 40)
                            GPrimary(text: p.requiresApproval ? S.appGAskToJoin : S.appGEnter, icon: "person.2.fill",
                                     enabled: !name.trimmingCharacters(in: .whitespaces).isEmpty && !r.busy) {
                                r.run {
                                    let j = try await api.join(code, myName: name)
                                    result = j.status
                                    if j.status == .joined { Telemetry.shared.event("group_join", ["via": InviteCode.via(code)]) }
                                    if j.status == .pending, let g = p.groupId { watchApproval(g) }
                                    if (j.status == .joined || j.status == .already_member), let g = j.groupId { joined(g) }
                                }
                            }
                            GErrorLine(runner: r)
                            // the preview was fine, but joining was refused: the code changed meanwhile, or too many tries
                            switch result {
                            case .group_full?: Text(S.appGFull).font(ski.type.text(15, .bold)).foregroundStyle(c.red)
                            case .invalid_code?:
                                Text(S.appGBadCode).font(ski.type.text(15, .bold)).foregroundStyle(c.red)
                                GMuted(S.appGBadCodeSub, 13)
                            case .rate_limited?:
                                Text(S.appGTooManyTries).font(ski.type.text(15, .bold)).foregroundStyle(c.red)
                                GMuted(S.appGTooManyTriesSub, 13)
                            default: EmptyView()
                            }
                            // "I'm already in the group" needs the names to pick from; without them, joining asks the admin
                            if !p.members.isEmpty {
                                GSnowCard(edge: c.ink, seed: 52) {
                                    GDisplay(S.appGAlreadyIn, 30).padding(.bottom, 10)
                                    GButton(text: S.joinReclaimPick, look: .ghost) { model.go(.groups(.reclaim(code))) }
                                }
                            }
                        }
                    }
                    if preview != nil { GQuiet(text: S.navHome) { model.fromHome(nil) } }
                }
                .padding(.horizontal, 16).padding(.bottom, 24)
                .frame(maxWidth: 560)
            }
            .frame(maxWidth: .infinity)
        }
        .ignoresSafeArea(edges: .top)
        .background(c.snow)
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(.hidden, for: .navigationBar)
        .task {
            name = api.me()?.name ?? ""
            load.run {
                if api.me() == nil { _ = try await api.signInGuest(); Telemetry.shared.event("sign_in", ["method": "guest"]) }
                let p = try await api.preview(code)
                preview = p
                if p.alreadyMember, let g = p.groupId { joined(g) }
            }
        }
        .onDisappear { stop?() }
    }

    private func joined(_ g: String) {
        Fx.shared.click()
        model.fromHome(.groups(.page(g, .flights)))
        GroupPage.justJoined = api.me()?.registered != true
    }

    // waiting for an admin: the approval opens the group, live (A-19)
    private func watchApproval(_ g: String) {
        stop = api.watchMine { approved in if approved.contains(g) { Telemetry.shared.event("group_join", ["via": InviteCode.via(code)]); joined(g) } }
    }
}

/// The top of the invitation (round 20, .r20-jh): "joining", the group's name big, then its dates by a calendar and its
/// members as initials with how many (no names to who is not in yet, when an admin must approve).
struct JoinHead: View {
    let p: InvitePreview
    @Environment(\.ski) private var ski
    var body: some View {
        let c = ski.colors
        GMuted(S.appGInvitedTo)
        GDisplay(p.name, 44)
        HStack(spacing: 18) {
            if let a = p.startsOn, let b = p.endsOn {
                HStack(spacing: 6) {
                    Image(systemName: "calendar").font(.system(size: 17)).foregroundStyle(c.ink).accessibilityHidden(true)
                    Text(dayRange(a...max(a, b))).font(ski.type.display(22)).foregroundStyle(c.ink).environment(\.layoutDirection, .leftToRight)
                }
            }
            if !p.members.isEmpty {
                HStack(spacing: -6) {
                    ForEach(Array(p.members.prefix(4).enumerated()), id: \.offset) { _, m in
                        Text(String(m.1.prefix(1))).font(ski.type.text(13, .bold)).foregroundStyle(.white)
                            .frame(width: 24, height: 24).background(c.glacier, in: Circle())
                            .padding(2).background(c.paper, in: Circle())
                    }
                    Text("\(p.members.count)").font(ski.type.display(22)).foregroundStyle(c.ink).padding(.leading, 12)
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(S.appGMembers(count: p.members.count))
            }
        }
        .padding(.top, 6)
    }
}

/// "I'm already in the group" (Q5): pick your name; an admin approves, and you are back with what you had. A name that
/// belongs to a registered account signs in instead.
struct ReclaimScreen: View {
    let code: String
    @Environment(AppModel.self) private var model
    @Environment(\.ski) private var ski
    @State private var preview: InvitePreview?
    @State private var pick: String?
    @State private var status: JoinStatus?
    @State private var load = GRunner()
    @State private var r = GRunner()
    @State private var stop: (() -> Void)?
    private var api: GroupAPI { GroupService.shared.api }

    var body: some View {
        let c = ski.colors
        Screen(title: S.appGAlreadyIn) {
            GPage {
                if let p = preview {
                    GMuted(S.appGReclaimIntro(group: p.name), 14)
                    VStack(spacing: 8) {
                        ForEach(p.members.indices, id: \.self) { i in
                            let m = p.members[i]
                            let sel = pick == m.0
                            Button { pick = m.0; Fx.shared.tick() } label: {
                                HStack(spacing: 12) {
                                    ZStack {
                                        Circle().stroke(sel ? c.blue : c.muted, lineWidth: 2).frame(width: 20, height: 20)
                                        if sel { Circle().fill(c.blue).frame(width: 10, height: 10) }
                                    }
                                    Text(m.1).font(ski.type.text(15.5, .bold)).foregroundStyle(c.ink)
                                    Spacer()
                                }
                                .padding(.horizontal, 12).frame(minHeight: 52)
                                .background(sel ? c.grid : c.paper)
                                .overlay(Rectangle().inset(by: sel ? 1 : 0.5).stroke(sel ? c.blue : c.rule, lineWidth: sel ? 2 : 1))
                            }
                            .buttonStyle(.plain)
                            .accessibilityAddTraits(sel ? .isSelected : [])
                        }
                    }
                    switch status {
                    case .pending?:
                        GPendingCard { r.run { if let g = p.groupId { try await api.cancelRequest(g) }; model.path.removeLast() } }
                    case .sign_in_instead?:
                        Text(S.appGSignInInstead).font(ski.type.text(15, .bold)).foregroundStyle(c.ink)
                        GAppleButton(text: S.acctApple) { r.run { try await SignIn.run("apple", model: model); if let g = p.groupId { done(g) } } }
                        if SignIn.googleReady {
                            GGoogleButton(text: S.appAGoogleContinue) { r.run { try await SignIn.run("google", model: model); if let g = p.groupId { done(g) } } }
                        }
                    default:
                        GPrimary(text: S.appGSendRequest, enabled: pick != nil) {
                            guard let m = pick else { return }
                            r.run {
                                let j = try await api.reclaim(code, memberId: m)
                                status = j.status
                                if j.status == .already_member, let g = j.groupId { done(g) }
                                if j.status == .pending, let g = p.groupId {
                                    stop = api.watchMine { approved in if approved.contains(g) { Telemetry.shared.event("group_join", ["via": InviteCode.via(code)]); done(g) } }
                                }
                            }
                        }
                        // every answer its own words (ד5), the site's (join.st_*)
                        let words: String? = switch status {
                        case .no_such_member?: S.joinStNoSuchMember
                        case .invalid_code?: S.joinStInvalidCode
                        case .rate_limited?: S.joinStRateLimited
                        case .group_full?: S.joinStGroupFull
                        default: nil
                        }
                        if let words { Text(words).font(ski.type.text(15, .bold)).foregroundStyle(c.red) }
                    }
                    GErrorLine(runner: r)
                } else if load.busy || load.error == nil {
                    GMuted(S.appLoading)
                } else {
                    GErrorLine(runner: load)
                }
            }
        }
        .task {
            load.run {
                if api.me() == nil { _ = try await api.signInGuest(); Telemetry.shared.event("sign_in", ["method": "guest"]) }
                preview = try await api.preview(code)
            }
        }
        .onDisappear { stop?() }
    }

    private func done(_ g: String) { model.fromHome(.groups(.page(g, .flights))) }
}
