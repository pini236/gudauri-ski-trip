import Foundation

/// The accelerometer's magnitudes between two readings: filled on the motion queue (a system queue, not the main
/// actor) and drained on the main actor. Nothing here is actor-isolated on purpose: it is called from the system's
/// threads. See `app/ios/qa/system-boundaries.md`.
public final class AccelWindow: @unchecked Sendable {
    private let lock = NSLock()
    private var n = 0.0, sum = 0.0, sq = 0.0

    public init() {}

    /// One reading, in g.
    public func add(x: Double, y: Double, z: Double) { add((x * x + y * y + z * z).squareRoot()) }

    public func add(_ g: Double) { lock.lock(); n += 1; sum += g; sq += g * g; lock.unlock() }

    /// The variance since the last drain (g²), nil without samples.
    public func drain() -> Double? {
        lock.lock(); defer { n = 0; sum = 0; sq = 0; lock.unlock() }
        guard n > 1 else { return nil }
        let mean = sum / n
        return max(0, sq / n - mean * mean)
    }
}
