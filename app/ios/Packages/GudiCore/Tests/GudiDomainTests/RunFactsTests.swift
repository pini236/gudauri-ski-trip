import XCTest
import GudiData
@testable import GudiDomain

/// The run view says what the site says about a run (13.3): the numbers the site's own code printed on the real data
/// (3.10.2026), the same values as Android's RunFactsTest.
final class RunFactsTests: XCTestCase {
    struct Site { let n: Int; let dmax, h0, h1, steepD, steepG, g0: Double; let top, bot, drop: Int; let maxG: Double; let steepAs, longAs: String? }

    let site: [String: Site?] = [
        "Soliko 1": Site(n: 284, dmax: 2825.61, h0: 2690.74, h1: 2149.46, steepD: 1116.71, steepG: 0.32029, g0: 0.11447, top: 2691, bot: 2149, drop: 541, maxG: 0.31808, steepAs: "Kikilo 2", longAs: "Kobi"),
        "Kudebi 3": Site(n: 53, dmax: 526.67, h0: 2985.06, h1: 2808.27, steepD: 209.78, steepG: 0.47584, g0: 0.26579, top: 2985, bot: 2808, drop: 177, maxG: 0.4751, steepAs: "Sadzele 3", longAs: "Khada"),
        "Kudebi 1": Site(n: 85, dmax: 830.94, h0: 2808.27, h1: 2660.96, steepD: 157.7, steepG: 0.26407, g0: 0.13182, top: 2988, bot: 2661, drop: 327, maxG: 0.38008, steepAs: "Tatra 2", longAs: "Sadzele 3"),
        "Kikilo 1": Site(n: 117, dmax: 1150.35, h0: 2765.72, h1: 2449.71, steepD: 623.54, steepG: 0.34282, g0: 0.23107, top: 2766, bot: 2347, drop: 419, maxG: 0.32976, steepAs: "Sportuli 1", longAs: "Sadzele 2"),
        "Snow Park": Site(n: 97, dmax: 946.73, h0: 2828.04, h1: 2701.39, steepD: 470.78, steepG: 0.19441, g0: 0.11616, top: 2830, bot: 2701, drop: 129, maxG: 0.19807, steepAs: "Firni 2", longAs: "Pirveli"),
        "Pirveli": Site(n: 118, dmax: 1167.88, h0: 2152.05, h1: 1995.2, steepD: 563.34, steepG: 0.24613, g0: 0.08552, top: 2152, bot: 1993, drop: 159, maxG: 0.24234, steepAs: "Firni 1", longAs: "Snow Park"),
        "Khada": Site(n: 65, dmax: 617.82, h0: 2803.96, h1: 2689.1, steepD: 20.8, steepG: 0.27419, g0: 0.21709, top: 2806, bot: 2689, drop: 117, maxG: 0.27242, steepAs: "Tatra 1", longAs: "Firni 1"),
        "Goodaura 2": Site(n: 138, dmax: 1309.9, h0: 2389.23, h1: 2161.32, steepD: 0.0, steepG: 0.31113, g0: 0.28412, top: 2611, bot: 2160, drop: 451, maxG: 0.34485, steepAs: "Sportuli 2", longAs: "Tatra 2"),
        "Sportuli 1": Site(n: 191, dmax: 1914.33, h0: 2568.66, h1: 2150.32, steepD: 176.13, steepG: 0.34953, g0: 0.19649, top: 2569, bot: 2150, drop: 418, maxG: 0.33314, steepAs: "Goodaura 1", longAs: "Kikilo 1"),
        "Sadzele 2": Site(n: 88, dmax: 886.37, h0: 3235.02, h1: 3037.33, steepD: 669.32, steepG: 0.33436, g0: 0.0984, top: 3236, bot: 2931, drop: 305, maxG: 0.33436, steepAs: "Goodaura 1", longAs: "Firni 2"),
        "Sadzele 3": Site(n: 177, dmax: 1746.78, h0: 3235.02, h1: 2900.08, steepD: 677.53, steepG: 0.56505, g0: 0.02069, top: 3241, bot: 2825, drop: 416, maxG: 0.48107, steepAs: "Kudebi 3", longAs: "Kudebi 1"),
        "Kikilo 2": Site(n: 110, dmax: 1090.29, h0: 2728.91, h1: 2449.71, steepD: 761.85, steepG: 0.34597, g0: 0.20489, top: 2729, bot: 2450, drop: 279, maxG: 0.32303, steepAs: "Soliko 2", longAs: "Kudebi 2"),
        "Tatra 1": Site(n: 295, dmax: 2936.48, h0: 2706.79, h1: 2172.74, steepD: 1289.58, steepG: 0.29052, g0: 0.09592, top: 2707, bot: 2170, drop: 536, maxG: 0.28604, steepAs: "Kobi", longAs: "Kobi"),
        "u473280432": nil,
        "Zuma": Site(n: 37, dmax: 348.53, h0: 2189.17, h1: 2136.63, steepD: 165.41, steepG: 0.18966, g0: 0.15971, top: 2189, bot: 2136, drop: 53, maxG: 0.2125, steepAs: "Firni 1", longAs: "New Goodaura"),
        "u668794712": Site(n: 24, dmax: 230.93, h0: 2200.38, h1: 2171.01, steepD: 162.57, steepG: 0.15844, g0: 0.11311, top: 2200, bot: 2171, drop: 29, maxG: 0.15337, steepAs: nil, longAs: nil),
        "Goodaura 1": Site(n: 279, dmax: 2743.6, h0: 2705.82, h1: 2161.32, steepD: 762.62, steepG: 0.36735, g0: 0.08681, top: 2706, bot: 2159, drop: 547, maxG: 0.33319, steepAs: "Sportuli 1", longAs: "Soliko 1"),
        "Kobi": Site(n: 238, dmax: 2370.66, h0: 2950.4, h1: 2512.35, steepD: 667.2, steepG: 0.29095, g0: 0.0571, top: 2950, bot: 2512, drop: 438, maxG: 0.28924, steepAs: "Tatra 1", longAs: "Soliko 1"),
        "New Goodaura": Site(n: 70, dmax: 699.72, h0: 2346.73, h1: 2292.71, steepD: 376.23, steepG: 0.15072, g0: 0.03468, top: 2347, bot: 2293, drop: 54, maxG: 0.14546, steepAs: "Snow Park", longAs: "Zuma"),
        "u1217683225": Site(n: 38, dmax: 370.65, h0: 2388.1, h1: 2346.73, steepD: 41.08, steepG: 0.18502, g0: 0.1182, top: 2391, bot: 2347, drop: 44, maxG: 0.1695, steepAs: nil, longAs: nil),
        "u1234557981": Site(n: 52, dmax: 500.65, h0: 2388.1, h1: 2305.51, steepD: 37.42, steepG: 0.23383, g0: 0.17009, top: 2390, bot: 2306, drop: 85, maxG: 0.20739, steepAs: nil, longAs: nil),
        "Kudebi 2": Site(n: 65, dmax: 644.83, h0: 2808.27, h1: 2690.74, steepD: 213.43, steepG: 0.25195, g0: 0.19032, top: 2975, bot: 2691, drop: 285, maxG: 0.49505, steepAs: "Sadzele 3", longAs: "Kikilo 2"),
        "u1259783829": Site(n: 18, dmax: 170.5, h0: 2840.5, h1: 2824.99, steepD: 101.39, steepG: 0.13922, g0: 0.08142, top: 2841, bot: 2825, drop: 16, maxG: 0.11805, steepAs: nil, longAs: nil),
        "Sportuli 2": Site(n: 49, dmax: 491.08, h0: 2368.95, h1: 2276.79, steepD: 269.65, steepG: 0.23501, g0: 0.17722, top: 2474, bot: 2242, drop: 232, maxG: 0.34502, steepAs: "Goodaura 2", longAs: "Snow Park"),
        "u1259783838": Site(n: 13, dmax: 107.14, h0: 2803.96, h1: 2784.89, steepD: 19.78, steepG: 0.22328, g0: 0.17804, top: 2806, bot: 2785, drop: 21, maxG: 0.22328, steepAs: nil, longAs: nil),
        "u1259783839": Site(n: 11, dmax: 95.91, h0: 2784.89, h1: 2765.72, steepD: 0.0, steepG: 0.19984, g0: 0.19984, top: 2785, bot: 2766, drop: 19, maxG: 0.19984, steepAs: nil, longAs: nil),
        "Sadzele 1": Site(n: 223, dmax: 2227.49, h0: 3231.99, h1: 2817.47, steepD: 60.39, steepG: 0.54626, g0: 0.41932, top: 3232, bot: 2771, drop: 460, maxG: 0.52208, steepAs: "Kudebi 2", longAs: "Goodaura 2"),
        "Baby": Site(n: 17, dmax: 162.56, h0: 2708.74, h1: 2690.95, steepD: 98.11, steepG: 0.1276, g0: 0.10974, top: 2709, bot: 2691, drop: 18, maxG: 0.11521, steepAs: nil, longAs: nil),
        "Bombora": Site(n: 32, dmax: 310.25, h0: 2689.1, h1: 2663.96, steepD: 170.93, steepG: 0.13359, g0: 0.05234, top: 2690, bot: 2664, drop: 26, maxG: 0.12621, steepAs: nil, longAs: nil),
        "Shino": Site(n: 129, dmax: 1288.22, h0: 2309.33, h1: 2178.31, steepD: 984.64, steepG: 0.16766, g0: 0.10647, top: 2309, bot: 2178, drop: 131, maxG: 0.13992, steepAs: nil, longAs: nil),
        "Firni 1": Site(n: 71, dmax: 695.56, h0: 2950.4, h1: 2842.26, steepD: 76.47, steepG: 0.25343, g0: 0.13667, top: 2950, bot: 2842, drop: 108, maxG: 0.22104, steepAs: "Zuma", longAs: "Zuma"),
        "Firni 2": Site(n: 164, dmax: 1620.91, h0: 2842.26, h1: 2708.31, steepD: 714.72, steepG: 0.2294, g0: 0.13653, top: 2842, bot: 2708, drop: 134, maxG: 0.20039, steepAs: "Snow Park", longAs: "Sadzele 2"),
        "Tatra 2": Site(n: 229, dmax: 2294.48, h0: 2664.57, h1: 2172.74, steepD: 656.31, steepG: 0.36568, g0: 0.08822, top: 2665, bot: 2170, drop: 494, maxG: 0.36267, steepAs: "Kudebi 1", longAs: "Goodaura 2"),
        "Soliko 2": Site(n: 38, dmax: 373.94, h0: 2495.01, h1: 2404.99, steepD: 59.57, steepG: 0.3315, g0: 0.32466, top: 2495, bot: 2405, drop: 90, maxG: 0.32606, steepAs: "Kikilo 2", longAs: "Kudebi 3"),
    ]

    func load() throws -> (RunsAndLifts, Terrain) {
        let dir = Repo.root.appendingPathComponent("site/data")
        return (try RunsAndLifts.decode(Data(contentsOf: dir.appendingPathComponent("runs-and-lifts.json"))),
                try Terrain.parse(Data(contentsOf: dir.appendingPathComponent("terrain.json"))))
    }

    func testEveryRunMatchesTheSite() throws {
        let (runs, t) = try load()
        XCTAssertEqual(Set(site.keys), Set(runs.pistes.map(\.key)))
        var cache: [String: Double] = [:]
        for p in runs.pistes {
            let got = RunFacts.of(t, p)
            guard let want = site[p.key] ?? nil else { XCTAssertNil(got, p.key); continue }
            guard let got else { XCTFail(p.key); continue }
            XCTAssertEqual(got.points.count, want.n, "\(p.key) points")
            XCTAssertEqual(got.length, want.dmax, accuracy: 0.5, "\(p.key) length")
            XCTAssertEqual(got.points.first!.h, want.h0, accuracy: 0.5, p.key)
            XCTAssertEqual(got.points.last!.h, want.h1, accuracy: 0.5, p.key)
            XCTAssertEqual(got.steepD, want.steepD, accuracy: 0.5, p.key)
            XCTAssertEqual(got.steepG, want.steepG, accuracy: 0.001, p.key)
            XCTAssertEqual(got.g0, want.g0, accuracy: 0.001, p.key)
            XCTAssertEqual(got.top, want.top, p.key)
            XCTAssertEqual(got.bot, want.bot, p.key)
            XCTAssertEqual(got.drop, want.drop, p.key)
            XCTAssertEqual(got.maxG, want.maxG, accuracy: 0.001, p.key)
            let c = RunFacts.compare(t, runs.pistes, key: p.key, cache: &cache)
            XCTAssertEqual(c?.steep, want.steepAs, p.key)
            XCTAssertEqual(c?.long, want.longAs, p.key)
        }
    }

    func testTheSignStepsThroughTheRunsAsOnTheSite() throws {
        let (runs, _) = try load()
        XCTAssertEqual(RunFacts.order(runs.pistes), ["Baby", "Bombora", "Pirveli", "Snow Park", "Zuma", "Firni 1", "Firni 2", "Goodaura 1", "Goodaura 2", "Khada", "Kikilo 1", "Kikilo 2", "Kobi", "New Goodaura", "Shino", "Soliko 1", "Soliko 2", "Sportuli 1", "Sportuli 2", "Tatra 1", "Tatra 2", "Kudebi 1", "Kudebi 2", "Kudebi 3", "Sadzele 3", "Sadzele 1", "Sadzele 2"])
    }

    func testNumbersSortAsNumbers() {
        XCTAssertEqual(["Kudebi 10", "kudebi 11", "Kudebi 2", "Kudebi 1"].sorted { RunFacts.natural($0, $1) < 0 },
                       ["Kudebi 1", "Kudebi 2", "Kudebi 10", "kudebi 11"])
    }
}
