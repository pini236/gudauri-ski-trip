import XCTest
import GudiData
@testable import GudiDomain

/// The day analysis against the reference tool's cases (decision 76, m-8 condition 4): every case of
/// tools/fixtures/dayrec-cases.json, final and live, compared by tools/dayrec/compare.py's rules (by event, not by
/// floating number). A difference from a near tie in floating point is reported to the tool's owner; no parameter is
/// tuned here (tools/dayrec/README.md, "Running the test values in an app").
final class DayRecAnalyzerTests: XCTestCase {
    func url(_ p: String) -> URL { Repo.root.appendingPathComponent(p) }

    func testTheCasesAreOfThisVersion() throws {
        let cases = try JSONSerialization.jsonObject(with: Data(contentsOf: url("tools/fixtures/dayrec-cases.json"))) as! [String: Any]
        let params = try DayRec.Params(Data(contentsOf: url("tools/fixtures/dayrec-params.json")))
        XCTAssertEqual((cases["analysis"] as! NSNumber).intValue, params.analysis)
        XCTAssertEqual(cases["params"] as? String, "tools/fixtures/dayrec-params.json")
    }

    func testEveryCaseMatchesTheReference() throws {
        let cases = try JSONSerialization.jsonObject(with: Data(contentsOf: url("tools/fixtures/dayrec-cases.json"))) as! [String: Any]
        let params = try DayRec.Params(Data(contentsOf: url("tools/fixtures/dayrec-params.json")))
        let runs = try Data(contentsOf: url("site/data/runs-and-lifts.json"))
        let terrain = try Terrain.parse(Data(contentsOf: url("site/data/terrain.json")))
        let base = try DayRec.Net(runsAndLifts: runs, terrain: terrain, params: params)
        var failures: [String] = []
        let list = cases["cases"] as! [[String: Any]]
        for c in list {
            let name = c["name"] as! String
            var errs: [String] = []
            let day = try XCTUnwrap(DayJournal.read(Data(contentsOf: url(c["log"] as! String))), name)
            let want = (c["dropped_lines"] as! NSNumber).intValue
            if day.dropped != want { errs.append("dropped \(day.dropped) lines, expected \(want)") }
            let net = try (c["network_patch"] as? [String: Any]).map { try DayRec.Net(runsAndLifts: runs, terrain: terrain, params: params, patch: $0) } ?? base
            let a = DayRec.Analyzer(net: net, params: params)
            let exp = c["expected"] as! [String: Any]
            errs += Compare.final(exp["final"] as! [String: Any], try json(a.final(day)))
            errs += Compare.live(exp["live"] as! [String: Any], try json(a.live(day)))
            if !errs.isEmpty { failures.append("\(name): " + errs.prefix(4).joined(separator: "; ")) }
        }
        XCTAssertTrue(failures.isEmpty, "\(failures.count) of \(list.count) cases differ:\n" + failures.joined(separator: "\n"))
    }

    func json<T: Encodable>(_ v: T) throws -> [String: Any] {
        try JSONSerialization.jsonObject(with: DayRec.encoder.encode(v)) as! [String: Any]
    }
}

/// tools/dayrec/compare.py, rule by rule.
enum Compare {
    static let tMs = 3000.0, rel = 0.03, absM = 1.0, kmhTol = 0.1

    static func d(_ v: Any?) -> Double? { v is NSNull ? nil : (v as? NSNumber)?.doubleValue }
    static func s(_ v: Any?) -> String? { v as? String }
    static func list(_ v: Any?) -> [[String: Any]] { v as? [[String: Any]] ?? [] }
    static func strs(_ v: Any?) -> [String]? { v is NSNull ? nil : v as? [String] }

    static func num(_ a: Double?, _ b: Double?, _ path: String, _ errs: inout [String]) {
        guard let a, let b else { if (a == nil) != (b == nil) { errs.append("\(path): \(String(describing: a)) != \(String(describing: b))") }; return }
        if abs(a - b) > max(rel * max(abs(a), abs(b)), absM) { errs.append("\(path): \(a) != \(b)") }
    }

    static func time(_ a: Double?, _ b: Double?, _ path: String, _ errs: inout [String]) {
        guard let a, let b, abs(a - b) <= tMs else { errs.append("\(path): time \(String(describing: a)) != \(String(describing: b))"); return }
    }

    static func kmh(_ a: Double?, _ b: Double?, _ path: String, _ errs: inout [String]) {
        guard let a, let b else { if (a == nil) != (b == nil) { errs.append("\(path): \(String(describing: a)) != \(String(describing: b))") }; return }
        if abs(a - b) > kmhTol + 1e-9 { errs.append("\(path): \(a) != \(b) km/h") }
    }

    static func segKey(_ x: [String: Any]) -> [String?] {
        var k: [String?] = [s(x["type"])]
        if s(x["type"]) == "lift" {
            k += [s(x["lift"]), s((x["from"] as? [String: Any])?["station"]), s((x["to"] as? [String: Any])?["station"])]
        }
        if s(x["type"]) == "stop" { k.append(s(x["kind"])) }
        return k
    }

    static func legKey(_ l: [String: Any]) -> String { "\(s(l["kind"]) ?? "")\(strs(l["runs"]) ?? [])" }

    static func final(_ exp: [String: Any], _ got: [String: Any]) -> [String] {
        var errs: [String] = []
        let es = list(exp["segments"]), gs = list(got["segments"])
        let ek = es.map(segKey), gk = gs.map(segKey)
        if ek != gk { return ["segments differ: expected \(ek), got \(gk)"] }
        for (n, (e, g)) in zip(es, gs).enumerated() {
            let p = "segments[\(n)] \(s(e["type"]) ?? "")"
            time(d(e["t0"]), d(g["t0"]), p + ".t0", &errs); time(d(e["t1"]), d(g["t1"]), p + ".t1", &errs)
            if s(e["type"]) == "descent" {
                let el = list(e["legs"]).map(legKey), gl = list(g["legs"]).map(legKey)
                if el != gl { errs.append("\(p).legs: expected \(el), got \(gl)"); continue }
                for (m, (a, b)) in zip(list(e["legs"]), list(g["legs"])).enumerated() {
                    time(d(a["t0"]), d(b["t0"]), "\(p).legs[\(m)].t0", &errs); time(d(a["t1"]), d(b["t1"]), "\(p).legs[\(m)].t1", &errs)
                    num(d(a["dist_m"]), d(b["dist_m"]), "\(p).legs[\(m)].dist_m", &errs)
                }
                for k in ["dist_m", "drop_m"] { num(d(e[k]), d(g[k]), "\(p).\(k)", &errs) }
                let eo = list(e["offpiste"]), go = list(g["offpiste"])
                if eo.count != go.count { errs.append("\(p).offpiste: \(eo.count) != \(go.count)") } else {
                    for (m, (a, b)) in zip(eo, go).enumerated() {
                        time(d(a["t0"]), d(b["t0"]), "\(p).offpiste[\(m)].t0", &errs); time(d(a["t1"]), d(b["t1"]), "\(p).offpiste[\(m)].t1", &errs)
                        num(d(a["drop_m"]), d(b["drop_m"]), "\(p).offpiste[\(m)].drop_m", &errs)
                    }
                }
                kmh(d(e["max_kmh"]), d(g["max_kmh"]), p + ".max_kmh", &errs)
            }
            if s(e["type"]) == "walk" { num(d(e["dist_m"]), d(g["dist_m"]), p + ".dist_m", &errs) }
        }
        let est = exp["stats"] as? [String: Any] ?? [:], gst = got["stats"] as? [String: Any] ?? [:]
        for k in ["descents", "lifts"] where d(est[k]) != d(gst[k]) { errs.append("stats.\(k): \(String(describing: est[k])) != \(String(describing: gst[k]))") }
        for k in ["ski_dist_m", "drop_m"] { num(d(est[k]), d(gst[k]), "stats.\(k)", &errs) }
        kmh(d(est["max_kmh"]), d(gst["max_kmh"]), "stats.max_kmh", &errs)
        events(list(exp["falls"]), list(got["falls"]), "falls", &errs)
        let eg = list(exp["gaps"]).count, gg = list(got["gaps"]).count
        if eg != gg { errs.append("gaps: \(eg) != \(gg)") }
        return errs
    }

    static func liveKey(_ e: [String: Any]) -> String {
        "\(s(e["act"]) ?? "")|\(s(e["lift"]) ?? "-")|\(strs(e["runs"]).map { "\($0)" } ?? "nil")"
    }

    static func live(_ exp: [String: Any], _ got: [String: Any]) -> [String] {
        let ee = list(exp["events"]), ge = list(got["events"])
        let ek = ee.map(liveKey), gk = ge.map(liveKey)
        if ek != gk { return ["live events differ: expected \(ek), got \(gk)"] }
        var errs: [String] = []
        for (n, (a, b)) in zip(ee, ge).enumerated() { time(d(a["t"]), d(b["t"]), "live[\(n)]", &errs) }
        kmh(d(exp["max_kmh"]), d(got["max_kmh"]), "live.max_kmh", &errs)
        events(list(exp["falls"]), list(got["falls"]), "live.falls", &errs)
        let ea = exp["autostop"] as? [String: Any], ga = got["autostop"] as? [String: Any]
        if (ea == nil) != (ga == nil) || (ea != nil && s(ea!["reason"]) != s(ga!["reason"])) {
            errs.append("live.autostop: \(String(describing: ea)) != \(String(describing: ga))")
        } else if let ea, let ga {
            time(d(ea["t"]), d(ga["t"]), "live.autostop", &errs)
        }
        return errs
    }

    /// Falls: the same number, each within 3 s.
    static func events(_ e: [[String: Any]], _ g: [[String: Any]], _ path: String, _ errs: inout [String]) {
        if e.count != g.count { errs.append("\(path): \(e.count) != \(g.count)"); return }
        for (n, (a, b)) in zip(e, g).enumerated() { time(d(a["t"]), d(b["t"]), "\(path)[\(n)]", &errs) }
    }
}
