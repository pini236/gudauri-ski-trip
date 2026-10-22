import XCTest
import GudiData
@testable import GudiDomain

/// The 3D map's gestures keep the ground under the fingers: the same cases as Android's MovesTest.
final class Map3DMovesTests: XCTestCase {
    let w = 1080.0, h = 2000.0
    /// Sloping, bumpy snow: the camera's centre rises and falls with it, which is what broke the first gestures.
    let snow: (Double, Double) -> Double = { x, z in 2300 + 0.18 * x - 0.12 * z + 120 * sin(x / 600) * cos(z / 800) }
    var start: Orbit { Orbit(tx: 600, ty: snow(600, -500), tz: -500, dist: 6000, yaw: 0.35, pitch: 0.62) }

    func under(_ s: Orbit, _ x: Double, _ y: Double) -> P3 { Lens(s, w: w, h: h).hit(x, y, snow)! }
    func screen(_ s: Orbit, _ g: P3) -> (x: Double, y: Double) { Lens(s, w: w, h: h).project(g.x, g.y, g.z)! }
    func onSnow(_ s: Orbit) { XCTAssertEqual(snow(s.tx, s.tz), s.ty, accuracy: 0.5, "the camera's centre sits on the snow") }

    func testProjectAndHitAgree() {
        let lens = Lens(start, w: w, h: h)
        for (x, y) in [(540.0, 1000.0), (200, 1500), (900, 700)] {
            let g = under(start, x, y)
            XCTAssertEqual(snow(g.x, g.z), g.y, accuracy: 1)
            let p = lens.project(g.x, g.y, g.z)!
            XCTAssertEqual(p.x, x, accuracy: 0.5); XCTAssertEqual(p.y, y, accuracy: 0.5)
        }
        let c = lens.project(start.tx, start.ty, start.tz)!
        XCTAssertEqual(c.x, w / 2, accuracy: 0.5); XCTAssertEqual(c.y, h / 2, accuracy: 0.5)
    }

    func testPanKeepsTheSnowUnderTheFinger() {
        let g = under(start, 540, 1200)
        var s = start
        for i in 1...24 {
            let k0 = Double(i - 1) / 24, k1 = Double(i) / 24
            s = Moves.pan(s, w: w, h: h, from: 540 - 216 * k0, 1200 - 300 * k0, to: 540 - 216 * k1, 1200 - 300 * k1, ground: snow)
        }
        onSnow(s)
        let p = screen(s, g)
        XCTAssertEqual(p.x, 324, accuracy: 3); XCTAssertEqual(p.y, 900, accuracy: 3)
    }

    func testZoomKeepsThePointBetweenTheFingers() {
        let g = under(start, 300, 1400)
        var s = start
        for _ in 0..<20 { s = Moves.zoom(s, w: w, h: h, at: 300, 1400, factor: 1.07, ground: snow) }
        onSnow(s)
        let p = screen(s, g)
        XCTAssertEqual(p.x, 300, accuracy: 3); XCTAssertEqual(p.y, 1400, accuracy: 3)
    }

    func testZoomStopsAtTheLimits() {
        XCTAssertEqual(Moves.zoom(start, w: w, h: h, at: 540, 1000, factor: 1000, ground: snow).dist, Moves.minDist, accuracy: 0.01)
        XCTAssertEqual(Moves.zoom(start, w: w, h: h, at: 540, 1000, factor: 0.001, ground: snow).dist, Moves.maxDist, accuracy: 0.01)
    }

    func testTheMapTurnsWithTheFingers() {
        let a0 = (390.0, 1000.0), b0 = (690.0, 1000.0)
        let ga = under(start, a0.0, a0.1), gb = under(start, b0.0, b0.1), gm = under(start, 540, 1000)
        let turn = 20 * Double.pi / 180
        let next = Moves.rotate(start, w: w, h: h, at: 540, 1000, angle: turn, ground: snow)
        onSnow(next)
        let pa = screen(next, ga), pb = screen(next, gb)
        let ang = atan2(pb.y - pa.y, pb.x - pa.x) - atan2(b0.1 - a0.1, b0.0 - a0.0)
        XCTAssertTrue(ang > turn * 0.45 && ang < turn * 1.6, "the ground turned \(ang * 180 / .pi)°, not with the fingers")
        let mid = screen(next, gm)
        XCTAssertLessThan(hypot(mid.x - 540, mid.y - 1000), 3)
    }

    func testTwoFingersUpTogetherTilt() {
        XCTAssertTrue(Moves.isTilt(2, -40, -3, -38, spreadChange: 4, slop: 8))
        XCTAssertFalse(Moves.isTilt(30, -10, -30, 10, spreadChange: 60, slop: 8)) // a pinch is not a tilt
        XCTAssertLessThan(Moves.tilt(start, h: h, dy: -200).pitch, start.pitch) // up: towards the horizon
    }

    func testTheWholeRunFitsAndItsTopIsUp() {
        let w = 1080.0, h = 2100.0
        let path: [P3] = (0...60).map { i in
            let k = Double(i) / 60
            return P3(1000 - 900 * k + 200 * sin(k * 6), 3000 - 700 * k, -2000 + 1500 * k)
        }
        let s = Framing.fit(path, w: w, h: h, ground: { _, _ in 2600 })
        let box = Framing.Box()
        let b = Framing.bounds(s, path, stride: 1, w: w, h: h)!
        XCTAssertGreaterThanOrEqual(b.minX, box.left * w - 1); XCTAssertLessThanOrEqual(b.maxX, box.right * w + 1)
        XCTAssertGreaterThanOrEqual(b.minY, box.top * h - 1); XCTAssertLessThanOrEqual(b.maxY, box.bottom * h + 1)
        XCTAssertTrue(b.maxX - b.minX > (box.right - box.left) * w * 0.85 || b.maxY - b.minY > (box.bottom - box.top) * h * 0.85)
        let lens = Lens(s, w: w, h: h)
        let top = lens.project(path[0].x, path[0].y, path[0].z)!, bottom = lens.project(path[60].x, path[60].y, path[60].z)!
        XCTAssertLessThan(top.y, bottom.y, "the top of the run should be higher on the screen")
    }

    func testFlyDownGoesDownTheRunAndEnds() {
        let path: [P3] = (0...100).map { P3(Double($0) * 10, 3000 - Double($0) * 3, 0) }
        var f = FlyDown(path)
        XCTAssertEqual(f.length, 1000, accuracy: 0.01)
        let a = f.step(1)!
        XCTAssertEqual(f.info.along, 40, accuracy: 0.01) // at least 40 m a second
        XCTAssertEqual(a.dist, 520); XCTAssertEqual(f.info.deg, atan(0.3) * 180 / .pi, accuracy: 0.01)
        var n = 0
        while f.step(0.5) != nil { n += 1; if n > 1000 { break } }
        XCTAssertLessThan(n, 1000)
    }
}

/// The sky and the light: the same checks as Android's SkyTest.
final class Map3DSkyTests: XCTestCase {
    func at(_ iso: String) -> Int64 {
        let f = ISO8601DateFormatter()
        return Int64(f.date(from: iso)!.timeIntervalSince1970 * 1000)
    }
    func angle(_ a: P3, _ b: P3) -> Double { acos(min(max((a * b).sum(), -1), 1)) * 180 / .pi }

    func testJanuaryDayAndNightInGudauri() {
        let t = Sun.times(at("2027-01-12T08:00:00Z"))
        XCTAssertTrue((8.0...8.8).contains(t.rise), "sunrise \(t.rise)"); XCTAssertTrue((17.0...17.8).contains(t.set), "sunset \(t.set)")
        let noon = Sky.at(at("2027-01-12T08:30:00Z")) // 12:30 in Gudauri
        XCTAssertTrue(noon.sunUp && !noon.dark)
        XCTAssertGreaterThan(noon.skyTop.z, noon.skyTop.x + 0.3, "a blue sky at noon")
        let night = Sky.at(at("2027-01-12T19:00:00Z")) // 23:00
        XCTAssertTrue(night.dark && !night.sunUp)
        XCTAssertTrue(night.skyTop.x < 0.1 && night.skyTop.y < 0.1 && night.skyTop.z < 0.1, "a dark sky at night")
        XCTAssertLessThan(night.color.sum(), noon.color.sum(), "moonlight is dimmer than sunlight")
    }

    func testTheMoonIsOppositeTheSunInAnEclipseAndBesideItInAnother() {
        let lunar = Sky.at(at("2025-03-14T06:59:00Z"))
        XCTAssertEqual(angle(lunar.sunDir, lunar.moonDir), 180, accuracy: 3)
        let solar = Sky.at(at("2024-04-08T18:18:00Z"))
        XCTAssertEqual(angle(solar.sunDir, solar.moonDir), 0, accuracy: 3)
    }
}

/// The scene on the real data: the run lines on the snow, the cables above it, the shadows and the slope rule X-3,
/// on the values the site's own test writes (tools/fixtures/slope-x3.json), as Android's SlopeRuleTest.
final class Map3DSceneTests: XCTestCase {
    static let root = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
        .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
        .deletingLastPathComponent()
    static let terrain = try! Terrain.parse(Data(contentsOf: root.appendingPathComponent("site/data/terrain.json")))
    static let runs = try! RunsAndLifts.decode(Data(contentsOf: root.appendingPathComponent("site/data/runs-and-lifts.json")))
    static let scene = Scene3D(terrain: terrain, runs: runs)
    nonisolated(unsafe) static let cases = try! JSONSerialization.jsonObject(with: Data(contentsOf: root.appendingPathComponent("tools/fixtures/slope-x3.json"))) as! [String: Any]

    func testTheRuleIsTheSites() {
        XCTAssertEqual(Scene3D.groundD, (Self.cases["half"] as! NSNumber).doubleValue)
    }

    /// The site's sampleLine: every 10 m from the top, the longest of the run's lines.
    func sampled() -> (xs: [Double], zs: [Double], ds: [Double]) {
        let t = Self.terrain
        let run = Self.runs.pistes.first { $0.key == Self.cases["run"] as! String }!
        let all = run.lines.map { l -> (xs: [Double], zs: [Double], ds: [Double]) in
            let l = t.elev(l[0].x, l[0].y) < t.elev(l[l.count - 1].x, l[l.count - 1].y) ? Array(l.reversed()) : l
            var xs = [l[0].x], zs = [l[0].y], ds = [0.0], d = 0.0
            for i in 1..<l.count {
                let a = l[i - 1], b = l[i], len = hypot(b.x - a.x, b.y - a.y), n = max(1, Int((len / 10).rounded()))
                for k in 1...n { let f = Double(k) / Double(n); xs.append(a.x + (b.x - a.x) * f); zs.append(a.y + (b.y - a.y) * f); ds.append(d + len * f) }
                d += len
            }
            return (xs, zs, ds)
        }
        return all.max { $0.ds.last! < $1.ds.last! }!
    }

    func testAlongTheLineOver20MetresEachWay() {
        let (xs, zs, ds) = sampled()
        let line = (0..<xs.count).map { P3(xs[$0], Self.terrain.elev(xs[$0], zs[$0]), zs[$0]) }
        let slopes = Scene3D.lineSlopes(line)
        for (k, w) in (Self.cases["line"] as! [[String: Any]]).enumerated() {
            let i = (w["i"] as! NSNumber).intValue
            func v(_ s: String) -> Double { (w[s] as! NSNumber).doubleValue }
            XCTAssertEqual(xs[i], v("x"), accuracy: 0.02, "x of point \(k)")
            XCTAssertEqual(zs[i], v("y"), accuracy: 0.02, "z of point \(k)")
            XCTAssertEqual(ds[i], v("d"), accuracy: 0.05, "distance of point \(k)")
            XCTAssertEqual(slopes[i], v("deg"), accuracy: 0.02, "line point \(k)")
        }
    }

    func testTheGroundOver20MetresEachWay() {
        for (k, w) in (Self.cases["ground"] as! [[String: Any]]).enumerated() {
            func v(_ s: String) -> Double { (w[s] as! NSNumber).doubleValue }
            XCTAssertEqual(Scene3D.groundSlope(Self.terrain, v("x"), v("y")), v("deg"), accuracy: 0.02, "ground point \(k)")
        }
    }

    func testTheGroundLayerAroundTheRun() {
        let s = Self.scene
        let g = s.slopeLayer(s.topDown(Self.cases["run"] as! String))
        XCTAssertEqual(Double(g.w) * Scene3D.texel, g.width, accuracy: 0.01)
        let p = (Self.cases["ground"] as! [[String: Any]])[0]
        let px = (p["x"] as! NSNumber).doubleValue, py = (p["y"] as! NSNumber).doubleValue
        let i = Int((px - g.x0) / Scene3D.texel), j = Int((py - g.z0) / Scene3D.texel)
        let c = SlopeColors.rgb(forDegrees: Scene3D.groundSlope(Self.terrain, g.x0 + (Double(i) + 0.5) * 10, g.z0 + (Double(j) + 0.5) * 10))
        let o = (j * g.w + i) * 4
        XCTAssertEqual(Double(g.rgba[o]), (c.0 * 255).rounded(), accuracy: 1)
        XCTAssertEqual(Double(g.rgba[o + 1]), (c.1 * 255).rounded(), accuracy: 1)
        XCTAssertEqual(g.rgba[o + 3], 255, "whole near the line")
        XCTAssertEqual(g.rgba[3], 0, "the corner is 150 m out")
    }

    func testTheLinesLieOnTheSnowAndTheCablesAboveIt() {
        let s = Self.scene, t = Self.terrain
        for (_, lines) in s.draped { for l in lines { for p in l { XCTAssertEqual(p.y, t.elev(p.x, p.z) + 4, accuracy: 0.01) } } }
        // no gap longer than 18 m between two points of a run
        for l in s.draped["Tatra 2"]! { for i in 1..<l.count { XCTAssertLessThanOrEqual(hypot(l[i].x - l[i - 1].x, l[i].z - l[i - 1].z), 18.01) } }
        for c in s.cables { for p in c { XCTAssertGreaterThanOrEqual(p.y, t.elev(p.x, p.z) + 8.99) } }
        // a chosen run is painted from its top: its first line starts highest, and the progress grows to 1
        let p = s.paint("Tatra 2")!
        XCTAssertGreaterThanOrEqual(p.lines[0][0].y, p.lines[0].last!.y)
        XCTAssertEqual(p.progress.last!.last!, 1, accuracy: 1e-9)
        XCTAssertEqual(p.progress[0][0], 0)
    }

    func testTheMeshAndTheShadows() {
        let t = Self.terrain
        let m = TerrainMesh.build(t)
        XCTAssertEqual(m.vertices.count, t.nx * t.ny * TerrainMesh.stride)
        XCTAssertEqual(m.indices.count, (t.nx - 1) * (t.ny - 1) * 6)
        // normals point up
        for i in Swift.stride(from: 0, to: t.nx * t.ny, by: 997) { XCTAssertGreaterThan(m.vertices[i * 10 + 4], 0) }
        // a low sun from the south-west leaves the north faces in the shade, and the high summit in the sun
        let sh = Sun.shadows(t, toward: Sun.direction(az: 225 * .pi / 180, alt: 8 * .pi / 180))
        let shaded = sh.filter { $0 > 0.5 }.count
        XCTAssertGreaterThan(shaded, sh.count / 50); XCTAssertLessThan(shaded, sh.count * 9 / 10)
        let noon = Sun.shadows(t, toward: Sun.direction(az: .pi, alt: 89 * .pi / 180))
        XCTAssertLessThan(noon.filter { $0 > 0.5 }.count, sh.count / 100, "the sun overhead casts almost no shade")
    }

    func testTheFirstViewHoldsTheWholeMainSide() {
        let s = Scene3D(terrain: Self.terrain, runs: Self.runs)
        let pts = s.mainArea()
        XCTAssertGreaterThan(pts.count, 200)
        XCTAssertTrue(pts.allSatisfy { $0.z >= Scene3D.kobiZ - 3000 }, "Kobi's side stays out")
        let box = Framing.Box(top: 0.32, bottom: 0.78)
        let o = Framing.fitArea(pts, w: 390, h: 844, ground: s.elev, box: box)
        XCTAssertEqual(o.yaw, Orbit.start.yaw, accuracy: 1e-9); XCTAssertEqual(o.pitch, Orbit.start.pitch, accuracy: 1e-9)
        let b = Framing.bounds(o, pts, stride: 1, w: 390, h: 844)!
        XCTAssertGreaterThanOrEqual(b.minX, 0.08 * 390 - 1); XCTAssertLessThanOrEqual(b.maxX, 0.92 * 390 + 1)
        XCTAssertGreaterThanOrEqual(b.minY, 0.32 * 844 - 1); XCTAssertLessThanOrEqual(b.maxY, 0.78 * 844 + 1)
        // centred, and filling the band one way or the other
        XCTAssertEqual((b.minX + b.maxX) / 2, 195, accuracy: 20)
        XCTAssertTrue(b.maxX - b.minX > 0.7 * 0.84 * 390 || b.maxY - b.minY > 0.7 * 0.46 * 844)
    }
}
