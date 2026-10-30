import XCTest
import GudiData
@testable import GudiDomain

/// The moments engine against tools/fixtures/dayrec-moments.json, exactly (README, "The moments"): each case on the
/// reference's expected final result (not this app's analysis, so the engine is tested on its own), the chain of four
/// days, each from the history the one before returned, and the cards on the lift.
final class DayRecMomentsTests: XCTestCase {
    func url(_ p: String) -> URL { Repo.root.appendingPathComponent(p) }

    func load() throws -> (fx: [String: Any], finals: [String: Any], runs: [String: DayRec.RunInfo], M: DayRec.MomentConfig) {
        let fx = try JSONSerialization.jsonObject(with: Data(contentsOf: url("tools/fixtures/dayrec-moments.json"))) as! [String: Any]
        let cases = try JSONSerialization.jsonObject(with: Data(contentsOf: url("tools/fixtures/dayrec-cases.json"))) as! [String: Any]
        var finals: [String: Any] = [:]
        for c in cases["cases"] as! [[String: Any]] { finals[c["name"] as! String] = (c["expected"] as! [String: Any])["final"] }
        let params = try DayRec.Params(Data(contentsOf: url("tools/fixtures/dayrec-params.json")))
        let net = try DayRec.Net(runsAndLifts: Data(contentsOf: url("site/data/runs-and-lifts.json")),
                                 terrain: Terrain.parse(Data(contentsOf: url("site/data/terrain.json"))), params: params)
        let M = try XCTUnwrap(params.moments)
        XCTAssertEqual((fx["moments"] as! NSNumber).intValue, M.version)
        return (fx, finals, net.runs, M)
    }

    func analysis(_ o: Any) throws -> DayRec.Analysis {
        let d = JSONDecoder(); d.keyDecodingStrategy = .convertFromSnakeCase
        return try d.decode(DayRec.Analysis.self, from: JSONSerialization.data(withJSONObject: o))
    }

    func history(_ o: Any) throws -> DayRec.History {
        try JSONDecoder().decode(DayRec.History.self, from: JSONSerialization.data(withJSONObject: o))
    }

    func json<T: Encodable>(_ v: T?) throws -> Any {
        guard let v else { return NSNull() }
        return try JSONSerialization.jsonObject(with: JSONEncoder().encode(v), options: .fragmentsAllowed)
    }

    func testEveryCase() throws {
        let (fx, finals, runs, M) = try load()
        for c in fx["cases"] as! [[String: Any]] {
            let r = try analysis(finals[c["final"] as! String]!)
            let got = DayRec.moments(r, history: try history(c["history"]!), runs: runs, M)
            let diff = Self.diff(c["expected"]!, try json(got), "")
            XCTAssertNil(diff, "\(c["name"]!): \(diff ?? "")")
        }
    }

    func testTheChain() throws {
        let (fx, finals, runs, M) = try load()
        var h: DayRec.History?
        for c in fx["chain"] as! [[String: Any]] {
            let given = try history(c["history"]!)
            if let h { XCTAssertEqual(h, given, "the chain's history") }
            let got = DayRec.moments(try analysis(finals[c["final"] as! String]!), history: h ?? given, runs: runs, M)
            let diff = Self.diff(c["expected"]!, try json(got), "")
            XCTAssertNil(diff, "chain \(c["final"]!): \(diff ?? "")")
            h = got.history
        }
    }

    func testTheLiftCards() throws {
        let (fx, _, _, M) = try load()
        for c in fx["lift_cards"] as! [[String: Any]] {
            let got = DayRec.liftCard(try analysis(c["final"]!), M)
            let diff = Self.diff(c["expected"]!, try json(got), "")
            XCTAssertNil(diff, "\(c["name"]!): \(diff ?? "")")
        }
    }

    func testTheSeedIsCRC32() {
        XCTAssertEqual(DayRec.seed("queue", 1799645400000), 60599824)
    }

    /// The same JSON: equal keys, values and order of arrays; numbers by value.
    static func diff(_ a: Any, _ b: Any, _ path: String) -> String? {
        switch (a, b) {
        case (is NSNull, is NSNull): return nil
        case let (x as String, y as String): return x == y ? nil : "\(path): \(x) != \(y)"
        case let (x as [Any], y as [Any]):
            if x.count != y.count { return "\(path): \(x.count) items != \(y.count)" }
            for (i, (p, q)) in zip(x, y).enumerated() { if let d = diff(p, q, "\(path)[\(i)]") { return d } }
            return nil
        case let (x as [String: Any], y as [String: Any]):
            if Set(x.keys) != Set(y.keys) { return "\(path): keys \(x.keys.sorted()) != \(y.keys.sorted())" }
            for k in x.keys.sorted() { if let d = diff(x[k]!, y[k]!, "\(path).\(k)") { return d } }
            return nil
        case let (x as Bool, y as Bool) where !(a is NSNumber && b is NSNumber):
            return x == y ? nil : "\(path): \(x) != \(y)"
        case let (x as NSNumber, y as NSNumber):
            return x.doubleValue == y.doubleValue ? nil : "\(path): \(x) != \(y)"
        default: return "\(path): \(a) != \(b)"
        }
    }
}
