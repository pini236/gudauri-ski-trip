import XCTest
@testable import Gudi

/// Proves the test target is wired to the app target (host app, @testable). The handler tests of the system
/// boundaries (docs/ARCHITECTURE.md, m-13 addendum, 14.11.2026) live beside it.
final class WiringTests: XCTestCase {
    func testHostAppIsLoaded() {
        XCTAssertNotNil(Bundle(for: WiringTests.self))
    }
}
