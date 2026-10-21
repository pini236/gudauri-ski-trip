import Foundation
import UserNotifications
import GudiDomain

/// The group's meetups remind a quarter of an hour before (Q8, "תזכורת רבע שעה לפני"), from the phone itself
/// (Android's meet/Reminders.kt): the notifications are scheduled on the phone when the meetups are known, so they
/// ring with no signal on the mountain. Each meetup's reminder is on unless the person turned it off (kept on the
/// phone). iOS keeps scheduled notifications across a restart, so nothing needs arming again.
///
/// The group part calls `Reminders.sync(meetups:)` with all the meetups of all my groups, whenever they change; the
/// app's root calls `Reminders.attach(_:)` once, so a tap on a reminder opens the meetup's card.
@MainActor
enum Reminders {
    private static let prefix = "meetup."
    private static let offKey = "remindersOff"
    private static let askedKey = "remindersAskedMeetups"

    static func isOn(_ id: String) -> Bool { !off.contains(id) }

    static func set(_ id: String, on: Bool) {
        var o = off
        if on { o.remove(id) } else { o.insert(id) }
        UserDefaults.standard.set(Array(o).sorted(), forKey: offKey)
    }

    private static var off: Set<String> { Set(UserDefaults.standard.stringArray(forKey: offKey) ?? []) }

    /// Schedules the reminders of these meetups (the ones turned on and still ahead), and takes away the ones that are
    /// no longer there. The whole list each time. The first time a meetup ahead would remind, it asks for permission
    /// to notify (once; the system shows its question once anyway).
    static func sync(meetups items: [MeetReminders.Item], now: Date = Date()) {
        let want = MeetReminders.due(items, off: off, now: Int64(now.timeIntervalSince1970 * 1000))
        let center = UNUserNotificationCenter.current()
        // the texts now, in the app's language, as Android's notification uses the app's language
        let made = want.map { it -> UNNotificationRequest in
            let c = UNMutableNotificationContent()
            c.title = S.appRemindTitle(place: it.place)
            c.body = S.appRemindBody(group: it.group, time: it.time)
            c.sound = .default
            c.userInfo = ["open": it.open]
            let wait = max(1, Double(it.ring) / 1000 - now.timeIntervalSince1970)
            return UNNotificationRequest(identifier: prefix + it.id, content: c, trigger: UNTimeIntervalNotificationTrigger(timeInterval: wait, repeats: false))
        }
        let ids = Set(made.map(\.identifier))
        Task { @MainActor in
            let stale = await center.pendingNotificationRequests().map(\.identifier).filter { $0.hasPrefix(prefix) && !ids.contains($0) }
            center.removePendingNotificationRequests(withIdentifiers: stale)
            guard !made.isEmpty else { return }
            if await center.notificationSettings().authorizationStatus == .notDetermined {
                guard !UserDefaults.standard.bool(forKey: askedKey) else { return }
                UserDefaults.standard.set(true, forKey: askedKey)
                _ = try? await center.requestAuthorization(options: [.alert, .sound])
            }
            for r in made { try? await center.add(r) }
        }
    }

    /// A tap on a reminder opens its meetup's card (the meeting point on that station, day and time).
    static func attach(_ app: AppModel) {
        Delegate.shared.app = app
        UNUserNotificationCenter.current().delegate = Delegate.shared
    }

    final class Delegate: NSObject, UNUserNotificationCenterDelegate, @unchecked Sendable {
        static let shared = Delegate()
        @MainActor weak var app: AppModel?

        func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
            guard let open = response.notification.request.content.userInfo["open"] as? String,
                  let l = MeetLink.parse(path: open), let s = l.station else { return }
            let arg = [s, l.time, l.day].compactMap { $0 }.joined(separator: "/")
            await MainActor.run { app?.go(.meet(station: arg)) }
        }

        /// Shown as a banner even while the app is open.
        func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification) async -> UNNotificationPresentationOptions {
            [.banner, .sound]
        }
    }
}
