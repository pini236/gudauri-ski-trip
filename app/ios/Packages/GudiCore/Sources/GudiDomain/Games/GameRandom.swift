import Foundation

/// The games' dice: seeded for the tests (the same board every run), from the system otherwise. SplitMix64.
public final class GameRandom: @unchecked Sendable {
    private var state: UInt64

    public init(seed: UInt64? = nil) { state = seed ?? UInt64.random(in: 0 ... .max) }

    public func next() -> UInt64 {
        state &+= 0x9E37_79B9_7F4A_7C15
        var z = state
        z = (z ^ (z >> 30)) &* 0xBF58_476D_1CE4_E5B9
        z = (z ^ (z >> 27)) &* 0x94D0_49BB_1331_11EB
        return z ^ (z >> 31)
    }

    /// 0 ..< 1
    public func double() -> Double { Double(next() >> 11) / Double(UInt64(1) << 53) }
    /// 0 ..< 1
    public func float() -> Float { Float(next() >> 40) / Float(1 << 24) }
    /// 0 ..< n
    public func int(_ n: Int) -> Int { n <= 0 ? 0 : Int(next() % UInt64(n)) }
    public func bool() -> Bool { next() & 1 == 1 }
}
