import SwiftUI
import GudiDomain

/// The group sign (Android's GroupHub): my group if I am in one, "my groups" in the account if in more (K-4), otherwise
/// the way in (A1). Watched live while it shows (A-19): an admin who lets me in opens the group.
struct GroupScreen: View {
    @Environment(AppModel.self) private var model
    @State private var groups: [GroupSummary]?
    @State private var changes = 0
    @State private var stop: (() -> Void)?
    private var api: GroupAPI { GroupService.shared.api }

    var body: some View {
        Group {
            if let g = groups, g.isEmpty {
                GroupEntryScreen(changes: changes)
            } else {
                Screen(title: S.appSignGroup) { ProgressView() }
            }
        }
        .task(id: changes) {
            // what waits for signal goes out (the lead also calls this on launch)
            if changes == 0 { await GroupService.shared.flush() }
            let g = api.me() == nil ? [] : ((try? await api.myGroups()) ?? [])
            groups = g
            if g.count == 1 { model.replaceTop(.groups(.page(g[0].id, .flights))) }
            else if g.count > 1 { model.replaceTop(.groups(.account)) }
        }
        .onAppear { stop = api.watchMine { _ in changes += 1 } }
        .onDisappear { stop?(); stop = nil }
    }
}

/// The group sign as a guest (A1, round 20): join with a code (no account), or create a group (needs one: the sign-in
/// sheet, A2). Everything else works without an account.
struct GroupEntryScreen: View {
    var changes = 0
    @Environment(AppModel.self) private var model
    @Environment(\.ski) private var ski
    @Environment(\.openURL) private var openURL
    @State private var code = ""
    @State private var signIn = AppModel.qaFlag("gsheet") == "signin"
    @State private var pending: [String] = []
    @State private var r = GRunner()
    private var api: GroupAPI { GroupService.shared.api }

    var body: some View {
        let c = ski.colors
        Screen(title: S.appSignGroup) {
            GPage {
                // what a group holds, as three icons (the site's group.empty_lead as shapes)
                GIconTrio(items: [("airplane", S.groupEmptyFlights), ("mappin.and.ellipse", S.groupTabMeetups), ("trophy", S.groupTabScores)])
                ForEach(pending, id: \.self) { gid in
                    GPendingCard { r.run { try await api.cancelRequest(gid); pending.removeAll { $0 == gid } } }
                }
                GSnowCard(edge: c.blue, seed: 31) {
                    GDisplay(S.appGHaveCode, 34).padding(.bottom, 12)
                    HStack(alignment: .bottom, spacing: 8) {
                        GField(label: S.appGCodeLabel, text: $code, hint: "KZBQRM", ltr: true, caps: true, max: 80)
                            .onChange(of: code) { _, v in
                                // a pasted invite link goes straight in (its token or code); anything else is typed letters
                                if let l = InviteCode.fromLink(v) { code = ""; open(l) } else if InviteCode.clean(v) != v { code = InviteCode.clean(v) }
                            }
                        GPrimary(text: S.appGJoin, full: false) { open(code) }
                    }
                }
                Spacer().frame(height: 6)
                GSnowCard(edge: c.ink, seed: 32) {
                    GDisplay(S.appGCreate, 34).padding(.bottom, 12)
                    GButton(text: S.appGCreate, look: .ink, icon: "person.2.fill") {
                        if api.me()?.registered == true { model.go(.groups(.new)) } else { signIn = true }
                    }
                }
                GErrorLine(runner: r)
            }
        }
        .task(id: changes) { pending = (try? await api.pendingRequests()) ?? [] }
        .sheet(isPresented: $signIn) {
            SignInSheet(title: S.groupCreateSigninTitle) {
                signIn = false
                model.go(.groups(.new))
            }
            .environment(\.ski, ski)
            .presentationDetents([.medium, .large])
        }
    }

    private func open(_ c: String) {
        model.go(.groups(InviteCode.isCode(c) || InviteCode.isToken(c) ? .join(c) : .code))
    }
}

/// Signing in (A2, round 20): one title, what signing in does not ask for as three icons, Apple (and Google once its
/// iOS client exists), "not now", and the privacy page.
struct SignInSheet: View {
    let title: String
    let onDone: () -> Void
    @Environment(AppModel.self) private var model
    @Environment(\.ski) private var ski
    @Environment(\.openURL) private var openURL
    @Environment(\.dismiss) private var dismiss
    @State private var r = GRunner()

    var body: some View {
        let c = ski.colors
        GSheet {
            GDisplay(title, 30)
            GIconTrio(items: [("key.slash", S.acctWhyNoPassword), ("envelope.badge.shield.half.filled", S.acctWhyNoEmail), ("person", S.acctWhyNameOnly)])
                .padding(.top, 8).padding(.bottom, 10)
            VStack(spacing: 10) {
                GAppleButton(text: S.acctApple) { r.run { try await SignIn.run("apple", model: model); onDone() } }
                if SignIn.googleReady || GroupService.shared.fake {
                    GGoogleButton(text: S.appAGoogleContinue) { r.run { try await SignIn.run("google", model: model); onDone() } }
                }
                GErrorLine(runner: r)
                GQuiet(text: S.appANotNow) { dismiss() }
            }
            Rectangle().fill(c.rule).frame(height: 1).padding(.top, 16)
            VStack(alignment: .leading, spacing: 10) {
                GNote(S.appAMovesOver, "icloud")
                Button { openURL(Site.privacy(ski.type.lang)) } label: {
                    Text(S.appAboutPrivacy).font(ski.type.text(13, .bold)).foregroundStyle(c.glacier).frame(minHeight: 44)
                }
                .buttonStyle(.plain)
            }
            .padding(.top, 14)
        }
        .environment(\.layoutDirection, ski.type.lang.rtl ? .rightToLeft : .leftToRight)
    }
}
