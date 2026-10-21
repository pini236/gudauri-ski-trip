import XCTest
@testable import GudiDomain

/// The slope colours are the site's (`R.SLOPE` in site/js/relief.js), like Android's SlopeColorsTest.
final class SlopeColorsTests: XCTestCase {
    func testTheSitesThresholdsAndColours() throws {
        let relief = try String(contentsOf: Repo.root.appendingPathComponent("site/js/relief.js"), encoding: .utf8)
        let line = try XCTUnwrap(relief.split(separator: "\n").first { $0.hasPrefix("R.SLOPE=[") })
        // [15,'#3FA85F',...],[25,'#F2C13D',...] -> (15, #3FA85F), ...
        var steps: [(Double, String)] = []
        for part in line.split(separator: "[").dropFirst() {
            let f = part.split(separator: ",")
            if f.count >= 2, let n = Double(f[0]) { steps.append((n, f[1].trimmingCharacters(in: CharacterSet(charactersIn: "'")).uppercased())) }
        }
        XCTAssertEqual(steps.map(\.1), SlopeColors.hex)
        XCTAssertEqual(steps.dropLast().map(\.0), SlopeColors.limits)
    }

    func testASlopeGetsItsColour() {
        XCTAssertEqual(SlopeColors.hex(forDegrees: 14.9), "#3FA85F")
        XCTAssertEqual(SlopeColors.hex(forDegrees: 15), "#F2C13D")
        XCTAssertEqual(SlopeColors.rgb(forDegrees: 20).0, 0.949, accuracy: 0.001)
        XCTAssertEqual(SlopeColors.rgb(forDegrees: 45).0, 0.863, accuracy: 0.001)
    }
}

/// The repository's root, from this file's place in it (app/ios/Packages/GudiCore/Tests/<target>/<file>).
enum Repo {
    static let root = URL(fileURLWithPath: #filePath).deletingLastPathComponent()
        .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
        .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
}
