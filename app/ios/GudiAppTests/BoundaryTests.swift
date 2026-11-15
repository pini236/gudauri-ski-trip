import XCTest
import AuthenticationServices
import CoreMotion
import GudiDomain
@testable import Gudi

/// System callbacks arrive on queues other than the main actor (build 55 crash). Each handler is built here and
/// called from a side queue; a wrongly isolated closure traps in the runtime check and fails the run.
final class BoundaryTests: XCTestCase {
    private func onSideQueue(_ work: @escaping @Sendable () -> Void) {
        let done = expectation(description: "side queue")
        DispatchQueue(label: "boundary.side").async { work(); done.fulfill() }
        wait(for: [done], timeout: 10)
    }

    func testTelemetryTickFromSideQueue() {
        let tick = Telemetry.tick()
        let t = Timer(timeInterval: 60, repeats: false) { _ in }
        nonisolated(unsafe) let timer = t
        onSideQueue { tick(timer) }
        RunLoop.main.run(until: Date().addingTimeInterval(0.2))
    }

    func testAppleResumerFromSideQueue() async {
        let result: Result<Int, Error> = await {
            do {
                _ = try await withCheckedThrowingContinuation { (k: CheckedContinuation<ASAuthorizationAppleIDCredential, Error>) in
                    let r = SignIn.resumer(k)
                    DispatchQueue(label: "boundary.side").async { r(.failure(ApiError("cancelled"))) }
                }
                return .success(0)
            } catch { return .failure(error) }
        }()
        if case .success = result { XCTFail("expected the failure to be delivered") }
    }

    func testWebDoneFromSideQueue() async {
        do {
            _ = try await withCheckedThrowingContinuation { (k: CheckedContinuation<URL, Error>) in
                let h = SignIn.webDone(k)
                DispatchQueue(label: "boundary.side").async { h(URL(string: "gudi://cb"), nil) }
            }
        } catch { XCTFail("expected the URL to be delivered: \(error)") }
    }

    #if DAYREC
    func testAccelHandlerFromSideQueue() {
        let w = AccelWindow()
        let h = DayRecorder.accelHandler(w)
        onSideQueue { h(nil, nil) }
    }

    func testAccuracyDoneFromSideQueue() {
        let rec = DayRecorder()
        let h = DayRecorder.accuracyDone(rec)
        onSideQueue { h(nil) }
        RunLoop.main.run(until: Date().addingTimeInterval(0.3))
    }
    #endif
}
