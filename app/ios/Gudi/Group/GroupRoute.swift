import SwiftUI
import GudiDomain

/// The group's own screens (Android's nav routes Group, GroupNew, GroupInvite, Join, JoinCode, Reclaim, Account), all
/// under one case of the app's Route: `.groups(...)`.
enum GroupRoute: Hashable {
    case page(String, GroupTab)
    case new
    case invite(String)
    case join(String)
    case code
    case reclaim(String)
    case account

    /// The screenshots' route after "grp:": "page:g1:meetups", "new", "invite:g1", "join:KZBQRM", "code", "reclaim:KZBQRM", "account".
    init?(qa: String) {
        let p = qa.split(separator: ":").map(String.init)
        switch p.first {
        case "page": guard p.count > 1 else { return nil }; self = .page(p[1], GroupTab(rawValue: p.count > 2 ? p[2] : "") ?? .flights)
        case "new": self = .new
        case "invite": guard p.count > 1 else { return nil }; self = .invite(p[1])
        case "join": guard p.count > 1 else { return nil }; self = .join(p[1])
        case "code": self = .code
        case "reclaim": guard p.count > 1 else { return nil }; self = .reclaim(p[1])
        case "account": self = .account
        default: return nil
        }
    }
}

enum GroupTab: String, CaseIterable, Hashable { case flights, meetups, scores, members }

/// Where `.groups(...)` leads.
struct GroupFlow: View {
    let route: GroupRoute
    var body: some View {
        switch route {
        case .page(let id, let tab): GroupPage(groupId: id, tab: tab)
        case .new: NewGroupScreen()
        case .invite(let id): InviteScreen(groupId: id)
        case .join(let code): InvitedScreen(code: code)
        case .code: CodeScreen()
        case .reclaim(let code): ReclaimScreen(code: code)
        case .account: AccountScreen()
        }
    }
}

extension AppModel {
    /// The screen on top is replaced (a tab, the group a hub opened), as Android's nav.replaceTop.
    func replaceTop(_ r: Route) {
        if path.isEmpty { path = [r] } else { path[path.count - 1] = r }
    }

    /// Back to home, then on to [r] (after joining: home, then the group).
    func fromHome(_ r: Route?) {
        path = r.map { [$0] } ?? []
    }

    /// A one-launch switch of the screenshots ("+gsheet=signin"): read once.
    static func qaFlag(_ k: String) -> String? { UserDefaults.standard.string(forKey: "qa." + k) }
}
