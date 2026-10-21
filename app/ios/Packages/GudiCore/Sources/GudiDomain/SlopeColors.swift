/// The slope colours and thresholds of the site (`GudRelief.SLOPE` in site/js/relief.js, decision 10: 15°, 25°,
/// 30°), the same as Android's `SlopeColors`. The test reads them from the site's file.
public enum SlopeColors {
    public static let limits: [Double] = [15, 25, 30]
    public static let hex: [String] = ["#3FA85F", "#F2C13D", "#F08A3C", "#DC3B33"]

    /// The colour of a slope in degrees, as `R.slopeColor` on the site: the first band whose limit is above it.
    public static func hex(forDegrees deg: Double) -> String {
        hex[limits.firstIndex(where: { deg < $0 }) ?? limits.count]
    }

    /// 0...1 red, green, blue.
    public static func rgb(forDegrees deg: Double) -> (Double, Double, Double) {
        let h = Array(hex(forDegrees: deg).dropFirst())
        func c(_ i: Int) -> Double { Double(Int(String(h[i..<i + 2]), radix: 16)!) / 255 }
        return (c(0), c(2), c(4))
    }
}
