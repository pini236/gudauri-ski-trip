import SwiftUI
import GudiDomain

/// The account (A4, K-4), as Android's account/AccountScreen.kt: the name the groups see, the ways in (Apple, and Google
/// once its iOS client exists), my groups, what syncs, signing out (not for guests: it would only lose the groups), and
/// deleting the account in the app (App Store 5.1.1(v); asks twice; also from the site at /account). A guest sees the
/// offer to keep their place instead.
struct AccountScreen: View {
    @Environment(AppModel.self) private var model
    @Environment(\.ski) private var ski
    @State private var me: Me?
    @State private var groups: [MyGroup]?
    @State private var changes = 0
    @State private var naming = false
    @State private var name = ""
    @State private var armed = false
    @State private var stop: (() -> Void)?
    @State private var r = GRunner()
    private var api: GroupAPI { GroupService.shared.api }

    var body: some View {
        let c = ski.colors
        Screen(title: S.appAAccount) {
            GPage {
                if let m = me {
                    // who: the name the groups see, with "change"
                    HStack(spacing: 14) {
                        GAvatar(name: m.name ?? "?", strong: true, size: 52)
                        VStack(alignment: .leading, spacing: 0) {
                            GDisplay(m.name ?? S.appANoName, 30)
                            HStack(spacing: 0) {
                                GMuted(S.appANameInGroups + " · ", 13)
                                Button { naming.toggle(); name = m.name ?? "" } label: {
                                    Text(S.appAChange).font(ski.type.text(13, .bold)).foregroundStyle(c.glacier).frame(minHeight: 44)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        Spacer(minLength: 0)
                    }
                    .padding(.horizontal, 16).padding(.top, 20).padding(.bottom, 14)
                    .background(c.paper)
                    .overlay(alignment: .top) { Rectangle().fill(c.glacier).frame(height: 6) }
                    if naming {
                        GField(label: S.acctNameHint, text: $name, max: 40)
                        GPrimary(text: S.appSave, icon: "checkmark", enabled: !name.trimmingCharacters(in: .whitespaces).isEmpty) {
                            r.run { try await api.rename(name.trimmingCharacters(in: .whitespaces)); me = api.me(); naming = false }
                        }
                    }
                    if !m.registered {
                        GNote(S.appAGuestNote, "key")
                        GAppleButton(text: S.acctApple) { signIn("apple") }
                        if SignIn.googleReady || GroupService.shared.fake { GGoogleButton(text: S.appASaveGoogle) { signIn("google") } }
                    } else {
                        VStack(alignment: .leading, spacing: 0) {
                            GDisplay(S.appAWaysIn, 28)
                            GMuted(S.appAWaysInSub, 13).padding(.bottom, 6)
                            way(S.appAApple, m.apple, mark: AnyView(Image(systemName: "applelogo").font(.system(size: 14)).foregroundStyle(.white)
                                .frame(width: 28, height: 28).background(Color.black, in: Circle()))) { signIn("apple") }
                            if SignIn.googleReady || m.google || GroupService.shared.fake {
                                way(S.appAGoogle, m.google, mark: AnyView(GoogleG().frame(width: 16, height: 16).frame(width: 28, height: 28)
                                    .overlay(Circle().stroke(Color(rgb: 0x747775), lineWidth: 2)))) { signIn("google") }
                            }
                        }
                    }
                    // my groups, as the site's list (K-4): by start date, and each opens its page
                    if let gs = groups {
                        VStack(alignment: .leading, spacing: 0) {
                            GDisplay(S.acctMyGroups, 28)
                            if gs.isEmpty { GMuted(S.acctNoGroups, 14) }
                            ForEach(gs) { g in groupRow(g) }
                        }
                    }
                    if m.registered {
                        GDisplay(S.appAWhatSyncs, 28).padding(.top, 8)
                        GNote(S.acctSyncNote, "icloud")
                    }
                    GErrorLine(runner: r)
                    VStack(spacing: 10) {
                        if m.registered {
                            GButton(text: S.appASignOut, look: .ghost, icon: "rectangle.portrait.and.arrow.right") {
                                r.run { try await api.signOut(); GroupService.shared.forgetReminders(); model.fromHome(nil) }
                            }
                        }
                        GButton(text: armed ? S.acctDeleteConfirm : S.appADelete, look: .danger, icon: "trash") {
                            if armed { r.run { try await api.deleteAccount(); GroupService.shared.forgetReminders(); model.fromHome(nil) } } else { armed = true; Fx.shared.tick() }
                        }
                    }
                    .padding(.top, 10)
                    GMuted(S.appADeleteNote(url: "gudauri-ski-trip.vercel.app/account"), 12.5)
                } else {
                    GMuted(S.appANotSignedIn, 14)
                    GAppleButton(text: S.acctApple) { signIn("apple") }
                    if SignIn.googleReady || GroupService.shared.fake { GGoogleButton(text: S.appAGoogleContinue) { signIn("google") } }
                    GErrorLine(runner: r)
                }
            }
        }
        .onAppear {
            me = api.me()
            stop = api.watchMine { _ in changes += 1 }
        }
        .onDisappear { stop?() }
        .task(id: "\(changes)-\(me?.userId ?? "")") { groups = (try? await api.myGroupRows()) ?? groups }
        .task(id: armed) { if armed { try? await Task.sleep(for: .seconds(4)); armed = false } }
    }

    private func signIn(_ p: String) {
        r.run { try await SignIn.run(p, model: model); me = api.me(); changes += 1 }
    }

    private func groupRow(_ g: MyGroup) -> some View {
        let c = ski.colors
        let sub = [g.members.map { S.groupMembersN(count: $0) }, g.admin ? S.acctRoleAdmin : nil].compactMap { $0 }.joined(separator: " · ")
        return Button { model.go(.groups(.page(g.id, .flights))) } label: {
            HStack(spacing: 12) {
                Text(String(g.name.trimmingCharacters(in: .whitespaces).prefix(1))).font(ski.type.display(24)).foregroundStyle(.white)
                    .frame(width: 36, height: 36).background(c.glacier)
                VStack(alignment: .leading, spacing: 1) {
                    Text(g.name).font(ski.type.text(15, .bold)).foregroundStyle(c.ink)
                    if !sub.isEmpty { GMuted(sub, 12.5) }
                }
                Spacer()
                Text(S.acctOpen).font(ski.type.text(14, .bold)).foregroundStyle(c.glacier)
            }
            .frame(minHeight: 60)
            .overlay(alignment: .bottom) { Rectangle().fill(c.rule).frame(height: 1) }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private func way(_ name: String, _ on: Bool, mark: AnyView, connect: @escaping () -> Void) -> some View {
        let c = ski.colors
        return HStack(spacing: 12) {
            mark
            VStack(alignment: .leading, spacing: 1) {
                Text(name).font(ski.type.text(15, .bold)).foregroundStyle(c.ink)
                GMuted(on ? S.acctConnected : S.appANotConnected, 12.5)
            }
            Spacer()
            if on { Image(systemName: "checkmark").font(.system(size: 18, weight: .bold)).foregroundStyle(c.green) }
            else { GButton(text: S.appAConnect, look: .ghost, small: true, full: false, action: connect) }
        }
        .frame(minHeight: 56)
        .overlay(alignment: .bottom) { Rectangle().fill(c.rule).frame(height: 1) }
    }
}
