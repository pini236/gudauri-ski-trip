import XCTest
@testable import GudiData

/// The refresh rules (like Android's SiteDataTest): a download is used only when valid, an ETag saves the download, an
/// old app version starts again from the packaged copy, and a failure never breaks anything.
final class SiteDataStoreTests: XCTestCase {
    private let root = URL(fileURLWithPath: #filePath).deletingLastPathComponent()
        .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
        .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
    private let name = "runs-and-lifts.json"

    private func site() throws -> Data { try Data(contentsOf: root.appendingPathComponent("site/data/runs-and-lifts.json")) }
    private func tmp() -> URL { FileManager.default.temporaryDirectory.appendingPathComponent("sd-" + UUID().uuidString) }

    private func store(_ dir: URL, version: String = "1", packaged: Data, now: Double = 1_000,
                       fetch: @escaping SiteDataStore.Fetcher) -> SiteDataStore {
        SiteDataStore(dir: dir, appVersion: version, packaged: { _ in packaged }, fetch: fetch, now: { now })
    }

    func testTheSitesFileIsValidAndBrokenOnesAreNot() throws {
        XCTAssertTrue(SiteDataStore.valid(name, try site()))
        XCTAssertFalse(SiteDataStore.valid(name, Data("{}".utf8)))
        XCTAssertFalse(SiteDataStore.valid(name, Data("not json".utf8)))
        let json = String(decoding: try site(), as: UTF8.self)
        let marked = Data(("{\"schema\":2," + json.dropFirst()).utf8)
        XCTAssertFalse(SiteDataStore.valid(name, marked), "a newer schema is ignored by this app")
    }

    func testADownloadIsUsedAfterItPassesAndANotModifiedKeepsIt() async throws {
        let dir = tmp(); let body = try site()
        let first = store(dir, packaged: Data("old".utf8)) { _, _ in .init(code: 200, etag: "\"a\"", body: body) }
        let r1 = await first.refresh(force: true)
        XCTAssertEqual(r1[name], .updated)
        XCTAssertEqual(first.read(name), body)
        // the next ask carries the ETag and gets 304
        let seen = Box()
        let second = store(dir, packaged: Data("old".utf8)) { _, etag in seen.etag = etag; return .init(code: 304, etag: nil, body: nil) }
        let r2 = await second.refresh(force: true)
        XCTAssertEqual(r2[name], .unchanged)
        XCTAssertEqual(seen.etag, "\"a\"")
        XCTAssertEqual(second.read(name), body)
    }

    func testABrokenOrFailedDownloadKeepsThePackagedCopy() async throws {
        let dir = tmp(); let packaged = Data("packaged".utf8)
        let bad = store(dir, packaged: packaged) { _, _ in .init(code: 200, etag: "\"b\"", body: Data(#"{"pistes":[],"lifts":[]}"#.utf8)) }
        let r = await bad.refresh(force: true)
        XCTAssertEqual(r[name], .rejected)
        XCTAssertEqual(bad.read(name), packaged)
        struct Down: Error {}
        let down = store(dir, packaged: packaged) { _, _ in throw Down() }
        let r2 = await down.refresh(force: true)
        XCTAssertEqual(r2[name], .failed)
        XCTAssertEqual(down.read(name), packaged)
    }

    func testItAsksAtMostEverySixHoursAndANewAppVersionStartsOverFromThePackagedCopy() async throws {
        let dir = tmp(); let body = try site(); let packaged = Data("packaged".utf8)
        let a = store(dir, packaged: packaged, now: 1_000) { _, _ in .init(code: 200, etag: "\"a\"", body: body) }
        _ = await a.refresh(force: true)
        let soon = store(dir, packaged: packaged, now: 1_000 + 3600) { _, _ in .init(code: 200, etag: "\"z\"", body: body) }
        let r = await soon.refresh()
        XCTAssertEqual(r[name], .skipped)
        let newer = store(dir, version: "2", packaged: packaged, now: 1_000) { _, _ in .init(code: 304, etag: nil, body: nil) }
        XCTAssertEqual(newer.read(name), packaged, "the downloads belong to the app version that fetched them")
    }

    final class Box: @unchecked Sendable { var etag: String? }
}
