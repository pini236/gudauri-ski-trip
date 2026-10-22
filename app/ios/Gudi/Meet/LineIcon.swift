import SwiftUI

/// The canvas's line icons (design/round10/build.py and round 20's, Android's ui/Icons.kt): a 24-unit grid, stroke 2,
/// square ends, drawn from the same SVG path data. Tinted by the caller.
struct LineIcon: View {
    let d: [String]
    var size: CGFloat = 22
    var color: Color

    var body: some View {
        Canvas { ctx, sz in
            let s = sz.width / 24
            var g = ctx
            g.scaleBy(x: s, y: s)
            for p in d { g.stroke(SVGPath.parse(p), with: .color(color), style: StrokeStyle(lineWidth: 2, lineCap: .square, lineJoin: .miter)) }
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }

    static func circle(_ cx: Double, _ cy: Double, _ r: Double) -> String { "M\(cx - r) \(cy) a\(r) \(r) 0 1 0 \(2 * r) 0 a\(r) \(r) 0 1 0 \(-2 * r) 0" }

    static let x = ["M6 6l12 12M18 6L6 18"]
    static let check = ["M4 12.5l5 5L20 6.5"]
    static let link = ["M10 14a4.5 4.5 0 0 0 6.4 0l3-3a4.5 4.5 0 0 0-6.4-6.4l-1 1", "M14 10a4.5 4.5 0 0 0-6.4 0l-3 3a4.5 4.5 0 0 0 6.4 6.4l1-1"]
    static let pin = ["M12 21s-6.5-6.4-6.5-11.2a6.5 6.5 0 0 1 13 0C18.5 14.6 12 21 12 21z", circle(12, 9.8, 2.4)]
    static let whatsapp = ["M4 20l1.3-4A8 8 0 1 1 8 18.7z", "M9 9c0 3 3 6 6 6l1-1.5-2-1-1 1c-1-.5-2-1.5-2.5-2.5l1-1-1-2z"]
    static let image = ["M3 5h18v14H3z", "M3 16l5-5 4 4 3-3 6 6", circle(16, 9, 1.6)]
    static let map = ["M3 6l6-2 6 2 6-2v14l-6 2-6-2-6 2z", "M9 4v14M15 6v14"]
    static let clockOther = [circle(12, 12, 9), "M12 7v5l3 2", "M19 3v4M17 5h4"]
}

/// Enough of SVG's path data for the icons: M L H V C S A Z, absolute and relative.
enum SVGPath {
    static func parse(_ d: String) -> Path {
        var nums: [Double] = []
        var tokens: [(Character, [Double])] = []
        var cmd: Character?
        var buf = ""
        func flushNum() { if let v = Double(buf) { nums.append(v) }; buf = "" }
        func flushCmd() { flushNum(); if let c = cmd { tokens.append((c, nums)) }; nums = [] }
        for ch in d {
            if ch.isLetter && ch != "e" { flushCmd(); cmd = ch }
            else if ch == "-" { if !buf.isEmpty && buf.last != "e" { flushNum() }; buf.append(ch) }
            else if ch == "." { if buf.contains(".") { flushNum() }; buf.append(ch) }
            else if ch == " " || ch == "," { flushNum() }
            else { buf.append(ch) }
        }
        flushCmd()

        var p = Path()
        var cur = CGPoint.zero, start = CGPoint.zero, lastCtl: CGPoint?
        for (c, a) in tokens {
            let rel = c.isLowercase
            func pt(_ x: Double, _ y: Double) -> CGPoint { rel ? CGPoint(x: cur.x + x, y: cur.y + y) : CGPoint(x: x, y: y) }
            switch c.uppercased().first! {
            case "M":
                var i = 0
                while i + 1 < a.count {
                    let q = pt(a[i], a[i + 1])
                    if i == 0 { p.move(to: q); start = q } else { p.addLine(to: q) }
                    cur = q; i += 2
                }
                lastCtl = nil
            case "L":
                var i = 0
                while i + 1 < a.count { let q = pt(a[i], a[i + 1]); p.addLine(to: q); cur = q; i += 2 }
                lastCtl = nil
            case "H":
                for v in a { cur = CGPoint(x: rel ? cur.x + v : v, y: cur.y); p.addLine(to: cur) }
                lastCtl = nil
            case "V":
                for v in a { cur = CGPoint(x: cur.x, y: rel ? cur.y + v : v); p.addLine(to: cur) }
                lastCtl = nil
            case "C":
                var i = 0
                while i + 5 < a.count {
                    let c1 = pt(a[i], a[i + 1]), c2 = pt(a[i + 2], a[i + 3]), q = pt(a[i + 4], a[i + 5])
                    p.addCurve(to: q, control1: c1, control2: c2); lastCtl = c2; cur = q; i += 6
                }
            case "S":
                var i = 0
                while i + 3 < a.count {
                    let c1 = lastCtl.map { CGPoint(x: 2 * cur.x - $0.x, y: 2 * cur.y - $0.y) } ?? cur
                    let c2 = pt(a[i], a[i + 1]), q = pt(a[i + 2], a[i + 3])
                    p.addCurve(to: q, control1: c1, control2: c2); lastCtl = c2; cur = q; i += 4
                }
            case "A":
                var i = 0
                while i + 6 < a.count {
                    let q = pt(a[i + 5], a[i + 6])
                    arc(&p, from: cur, to: q, rx: a[i], ry: a[i + 1], large: a[i + 3] != 0, sweep: a[i + 4] != 0)
                    cur = q; i += 7
                }
                lastCtl = nil
            case "Z":
                p.closeSubpath(); cur = start; lastCtl = nil
            default: break
            }
        }
        return p
    }

    /// An SVG arc (endpoint form, no rotation) as a circle or ellipse arc around its centre (SVG 1.1, F.6.5).
    static func arc(_ p: inout Path, from a: CGPoint, to b: CGPoint, rx rx0: Double, ry ry0: Double, large: Bool, sweep: Bool) {
        var rx = abs(rx0), ry = abs(ry0)
        guard rx > 0, ry > 0, a != b else { p.addLine(to: b); return }
        let x1 = (a.x - b.x) / 2, y1 = (a.y - b.y) / 2
        let lam = (x1 * x1) / (rx * rx) + (y1 * y1) / (ry * ry)
        if lam > 1 { rx *= lam.squareRoot(); ry *= lam.squareRoot() }
        let num = max(0, rx * rx * ry * ry - rx * rx * y1 * y1 - ry * ry * x1 * x1)
        let den = rx * rx * y1 * y1 + ry * ry * x1 * x1
        var co = (num / den).squareRoot()
        if large == sweep { co = -co }
        let cx1 = co * rx * y1 / ry, cy1 = -co * ry * x1 / rx
        let cx = cx1 + (a.x + b.x) / 2, cy = cy1 + (a.y + b.y) / 2
        func ang(_ ux: Double, _ uy: Double) -> Double { atan2(uy, ux) }
        let t1 = ang((x1 - cx1) / rx, (y1 - cy1) / ry)
        var dt = ang((-x1 - cx1) / rx, (-y1 - cy1) / ry) - t1
        if sweep && dt < 0 { dt += 2 * .pi }
        if !sweep && dt > 0 { dt -= 2 * .pi }
        let n = max(4, Int(abs(dt) / (.pi / 16)))
        for k in 1...n {
            let t = t1 + dt * Double(k) / Double(n)
            p.addLine(to: CGPoint(x: cx + rx * cos(t), y: cy + ry * sin(t)))
        }
    }
}
