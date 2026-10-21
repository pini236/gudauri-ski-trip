import XCTest
@testable import GudiDomain

/// The app's tokens are the site's tokens: a colour changed in site/css/site.css fails here until it is carried over.
final class TokensTests: XCTestCase {
    private func block(_ selector: String) throws -> [String: String] {
        let css = try String(contentsOf: Repo.root.appendingPathComponent("site/css/site.css"), encoding: .utf8)
        let start = try XCTUnwrap(css.range(of: selector), selector)
        let open = try XCTUnwrap(css[start.lowerBound...].firstIndex(of: "{"))
        let close = try XCTUnwrap(css[open...].firstIndex(of: "}"))
        var out: [String: String] = [:]
        for line in css[css.index(after: open)..<close].split(separator: ";") {
            let parts = line.split(separator: ":", maxSplits: 1).map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            guard parts.count == 2, parts[0].hasPrefix("--"), parts[1].hasPrefix("#"), parts[1].count == 7 else { continue }
            out[String(parts[0].dropFirst(2))] = parts[1].uppercased()
        }
        return out
    }

    private func check(_ css: [String: String], _ p: Palette) {
        for (name, rgb) in p.byToken { XCTAssertEqual(css[name], String(format: "#%06X", rgb), "--\(name)") }
        if let edge = css["sn-edge"] { XCTAssertEqual(p.snEdge.map { String(format: "#%06X", $0) }, edge) } else { XCTAssertNil(p.snEdge) }
    }

    func testDayMatchesTheSite() throws { check(try block("\n:root{\n  --snow"), .day) }
    func testNightMatchesTheSite() throws { check(try block(":root[data-theme=\"dark\"]{"), .night) }
}
