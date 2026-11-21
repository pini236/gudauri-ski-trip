import Foundation
import ActivityKit

/// The ski day's Live Activity (decision 76, docs/APPLE.md 5.9): what the lock screen and the Dynamic Island show while
/// the day records. Shared by the app and its extension (project-dayrec.yml); no location, only the live numbers, and the
/// app alone updates it (no push). Small on purpose: the whole state stays far under ActivityKit's 4 KB.
public struct DayLiveAttributes: ActivityAttributes {
    public struct ContentState: Codable, Hashable, Sendable {
        /// Km/h now, the day's top so far, and the distance skied or ridden so far in km.
        public var speedKmh: Int
        public var maxKmh: Int
        public var distanceKm: Double
        public init(speedKmh: Int, maxKmh: Int, distanceKm: Double) {
            self.speedKmh = speedKmh; self.maxKmh = maxKmh; self.distanceKm = distanceKm
        }
    }
    /// When the recording started, for the running clock.
    public var startedAt: Date
    public init(startedAt: Date) { self.startedAt = startedAt }
}
