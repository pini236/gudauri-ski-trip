import XCTest
@testable import GudiDomain

/// Every code in the contract's table of errors that must have its own message ("כן") has one, and its key is in
/// i18n/strings.json (as Android's ErrorsTest), also the join statuses and the client's own codes.
final class ErrorsTests: XCTestCase {
    func testEveryContractCodeHasItsOwnWords() throws {
        let root = URL(fileURLWithPath: #filePath).deletingLastPathComponent().appendingPathComponent("../../../../../..").standardized
        let contract = try String(contentsOf: root.appendingPathComponent("server/CONTRACT.md"), encoding: .utf8)
        let strings = JSON.parse(try Data(contentsOf: root.appendingPathComponent("i18n/strings.json")))?["strings"]?.object ?? [:]
        var own: [String] = []
        var inTable = false
        for line in contract.split(separator: "\n", omittingEmptySubsequences: false) {
            if line.hasPrefix("| קוד |") { inTable = true; continue }
            if inTable && !line.hasPrefix("|") { inTable = false }
            guard inTable, line.hasPrefix("| `") else { continue }
            let cells = line.split(separator: "|").map { $0.trimmingCharacters(in: .whitespaces) }
            guard cells.count >= 4, cells.last!.hasPrefix("כן") else { continue }
            own.append(cells[0].replacingOccurrences(of: "`", with: ""))
        }
        XCTAssertGreaterThan(own.count, 20, "the contract's table was read")
        own += ["invalid_code", "rate_limited", "group_full", "already_member", "no_such_member", "sign_in_instead", "not_allowed", "no_session"]
        for code in own {
            let key = ErrorKeys.key(code)
            XCTAssertNotEqual(key, ErrorKeys.general, "no message of its own for \(code)")
            XCTAssertNotNil(strings[key], "\(key) for \(code) is not in i18n/strings.json")
        }
        for key in Set(ErrorKeys.byCode.values) { XCTAssertNotNil(strings[key], key) }
    }
}
