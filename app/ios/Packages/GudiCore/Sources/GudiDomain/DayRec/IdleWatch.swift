import Foundation

extension DayRec {
    /// When to stop a day on its own (m-8, condition 2): 20 minutes without moving outside the resort, 60 inside it,
    /// with a notice two minutes before. "Moving" is getting more than 30 m from the place where the stillness started,
    /// so a phone resting in a lodge counts as still and a walk to the lift does not.
    /// Android's `dayrec/IdleWatch.kt`, with the same numbers (its `DayParams`), and as pure, so a test runs a whole day
    /// through it without a phone.
    public struct IdleWatch: Sendable {
        public enum Verdict: Equatable, Sendable {
            case fine
            /// Milliseconds left before the day is stopped.
            case warn(leftMs: Int64)
            /// `outside`: it was the 20-minute rule.
            case stop(outside: Bool)
        }

        public static let outsideMs: Int64 = 20 * 60_000
        public static let insideMs: Int64 = 60 * 60_000
        public static let warnMs: Int64 = 2 * 60_000
        public static let moveM = 30.0
        /// The bounds of the height model, as Android's `AREA_*`.
        public static let area = (x0: -7795.76, y0: -7792.4, x1: 6975.15, y1: 7792.4)

        private var ax = 0.0, ay = 0.0
        private var since: Int64 = -1

        public init() {}

        public static func inside(_ x: Double, _ y: Double) -> Bool {
            x >= area.x0 && x <= area.x1 && y >= area.y0 && y <= area.y1
        }

        /// One reading in the resort's metres; `t` in any increasing millisecond clock.
        public mutating func feed(_ t: Int64, _ x: Double, _ y: Double) -> Verdict {
            if since < 0 || hypot(x - ax, y - ay) > Self.moveM { ax = x; ay = y; since = t; return .fine }
            let outside = !Self.inside(x, y)
            let limit = outside ? Self.outsideMs : Self.insideMs
            let still = t - since
            if still >= limit { return .stop(outside: outside) }
            if still >= limit - Self.warnMs { return .warn(leftMs: limit - still) }
            return .fine
        }

        public mutating func reset() { since = -1 }
    }
}
