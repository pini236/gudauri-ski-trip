import XCTest
@testable import GudiDomain

final class BackgroundModeTests: XCTestCase {
    func testDeclaredOnlyWithLocation() {
        XCTAssertTrue(BackgroundMode.declaresLocation(["UIBackgroundModes": ["audio", "location"]]))
        XCTAssertFalse(BackgroundMode.declaresLocation(["UIBackgroundModes": ["audio"]]))
        XCTAssertFalse(BackgroundMode.declaresLocation(["UIBackgroundModes": []]))
        XCTAssertFalse(BackgroundMode.declaresLocation([:]))
        XCTAssertFalse(BackgroundMode.declaresLocation(nil))
        XCTAssertFalse(BackgroundMode.declaresLocation(["UIBackgroundModes": "location"]))
    }
}
