import XCTest
@testable import GudiDomain

/// The system calls our closures back on its own queues. These run them from side queues, as the system does.
/// A closure built inside a @MainActor type inherits the main actor and traps there (TestFlight build 55);
/// the pattern below (built in a nonisolated static function) must not.
final class SystemBoundaryTests: XCTestCase {
    func testAccelWindowFilledFromSideQueue() {
        let w = AccelWindow()
        let q = OperationQueue(); q.maxConcurrentOperationCount = 1
        let done = expectation(description: "filled")
        for i in 0..<50 { q.addOperation { w.add(x: 0, y: 0, z: i % 2 == 0 ? 1 : 2) } }
        q.addOperation { done.fulfill() }
        wait(for: [done], timeout: 5)
        let v = w.drain()
        XCTAssertNotNil(v)
        XCTAssertEqual(v!, 0.25, accuracy: 1e-9)
        XCTAssertNil(w.drain(), "drain empties the window")
    }

    func testAccelWindowFromManyQueuesAtOnce() {
        let w = AccelWindow()
        DispatchQueue.concurrentPerform(iterations: 1000) { _ in w.add(1) }
        XCTAssertEqual(w.drain(), 0)
    }

    /// The pattern the app uses for every system callback: the closure is made in a nonisolated static function.
    @MainActor final class Owner {
        let window = AccelWindow()
        nonisolated static func handler(_ w: AccelWindow) -> @Sendable (Double, Double, Double) -> Void {
            { x, y, z in w.add(x: x, y: y, z: z) }
        }
    }

    @MainActor func testNonisolatedHandlerRunsOffTheMainQueue() {
        let owner = Owner()
        let h = Owner.handler(owner.window)
        let done = expectation(description: "called")
        DispatchQueue.global().async { for _ in 0..<10 { h(0, 0, 1) }; done.fulfill() }
        wait(for: [done], timeout: 5)
        XCTAssertEqual(owner.window.drain(), 0)
    }
}
