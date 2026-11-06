#if DAYREC
import Foundation
import ActivityKit
import GudiDomain

/// Feeds the ski day's Live Activity from the recorder's readings (docs/APPLE.md 5.9). The app updates it, every few
/// seconds at most; nothing is sent anywhere, and no coordinate is in it (only km/h and km).
@MainActor
final class DayLive {
    private var activity: Activity<DayLiveAttributes>?
    private var maxMs = 0.0, metres = 0.0
    private var prev: DayJournal.Point?
    private var lastUpdate = Date.distantPast

    func begin(since: Date) {
        guard activity == nil, ActivityAuthorizationInfo().areActivitiesEnabled else { return }
        maxMs = 0; metres = 0; prev = nil
        let state = DayLiveAttributes.ContentState(speedKmh: 0, maxKmh: 0, distanceKm: 0)
        activity = try? Activity.request(attributes: DayLiveAttributes(startedAt: since), content: .init(state: state, staleDate: nil))
    }

    func add(_ p: DayJournal.Point) {
        guard activity != nil else { return }
        if let q = prev {
            let dlat = (p.lat - q.lat) * 111_320, dlon = (p.lon - q.lon) * 111_320 * cos(p.lat * .pi / 180)
            let d = (dlat * dlat + dlon * dlon).squareRoot()
            if d < 200 { metres += d }
        }
        prev = p
        if let s = p.spd, s >= 0, (p.sacc ?? 0) <= 1 { maxMs = max(maxMs, s) }
        let now = Date()
        guard now.timeIntervalSince(lastUpdate) >= 5 else { return }
        lastUpdate = now
        let state = DayLiveAttributes.ContentState(speedKmh: Int(((p.spd ?? 0) * 3.6).rounded()), maxKmh: Int((maxMs * 3.6).rounded()), distanceKm: metres / 1000)
        let a = activity
        Task { await a?.update(.init(state: state, staleDate: nil)) }
    }

    func end() {
        let a = activity
        activity = nil; prev = nil
        Task { await a?.end(nil, dismissalPolicy: .immediate) }
    }
}
#endif
