import SwiftUI
import UIKit
import QuartzCore
import GudiDomain

// What the descent and the snowball fight share on the phone, besides Games/Common (Synth, Bests, GameHaptics,
// SoundKey, GameBack): the frame clock and the menus' pieces (the site's games pages: .menu, h2, .how, .go, .pop).

/// The games' own look: a bright winter day, the same in both themes (the site's game pages).
enum Arcade {
    static let ink = Color(rgb: 0x13233A)
    static let muted = Color(rgb: 0x4B5A6F)
    static let rule = Color(rgb: 0xCBD5DF)
    static let accent = Color(rgb: 0xF4B942)
    static let warn = Color(rgb: 0xF08A3C)

    /// The phone's "reduce motion": the camera does not shake, nothing pulses.
    @MainActor static var still: Bool { UIAccessibility.isReduceMotionEnabled }

    /// A coat's colour, for a screen reader (no names in the games, decision 55).
    static func coatName(_ i: Int) -> String {
        switch i { case 0: S.commonColorRed; case 1: S.commonColorBlue; case 2: S.appColorYellow; case 3: S.commonColorGreen; case 4: S.appColorPurple; default: S.appColorOrange }
    }
    static func coat(_ i: Int) -> Color { Color(rgb: Coats.colors[min(max(i, 0), Coats.count - 1)]) }
}

/// A frame clock (CADisplayLink) that steps a game on the main thread, outside SwiftUI's updates.
@MainActor
final class ArcadeTicker: NSObject {
    private var link: CADisplayLink?
    private var last: CFTimeInterval = 0
    var onTick: ((Float) -> Void)?

    func start() {
        guard link == nil else { return }
        let l = CADisplayLink(target: self, selector: #selector(tick(_:)))
        l.preferredFrameRateRange = CAFrameRateRange(minimum: 30, maximum: 60, preferred: 60)
        l.add(to: .main, forMode: .common)
        link = l; last = 0
    }
    func stop() { link?.invalidate(); link = nil }

    @objc private func tick(_ l: CADisplayLink) {
        let now = l.timestamp
        let dt = last == 0 ? 1.0 / 60 : min(0.05, now - last)
        last = now
        onTick?(Float(dt))
    }
}

/// A small dark square of 44 that leaves the run or the fight for the menu (iPhone has no system back key in a game).
struct ArcadeExitKey: View {
    let label: String
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Image(systemName: "xmark").font(.system(size: 16, weight: .bold)).foregroundStyle(.white)
                .frame(width: 44, height: 44).background(Arcade.ink.opacity(0.7))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}

// ---------- the menus' pieces ----------

/// The site's .menu: the field shows through a gradient that darkens toward the bottom; the content scrolls.
struct ArcadeShade<Content: View>: View {
    let top: UInt32
    @ViewBuilder let content: Content
    var body: some View {
        ZStack {
            LinearGradient(stops: [.init(color: Color(rgb: top, opacity: 0.25), location: 0),
                                   .init(color: Color(rgb: 0x0D1522, opacity: 0.75), location: 0.58),
                                   .init(color: Color(rgb: 0x0D1522, opacity: 0.75), location: 1)],
                           startPoint: .top, endPoint: .bottom)
                .ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 14) { content }
                    .padding(.horizontal, 16).padding(.top, 12).padding(.bottom, 20)
                    .frame(maxWidth: 560, alignment: .leading)
                    .frame(maxWidth: .infinity)
            }
        }
    }
}

struct ArcadeH2: View {
    let text: String
    @Environment(\.ski) private var ski
    var body: some View { Text(text).font(ski.type.display(28)).foregroundStyle(.white) }
}

struct ArcadeTitle: View {
    let text: String
    @Environment(\.ski) private var ski
    var body: some View {
        Text(text).font(ski.type.display(58)).foregroundStyle(.white).shadow(color: Arcade.ink, radius: 0, x: 0, y: 3)
            .lineLimit(2).minimumScaleFactor(0.6)
    }
}

/// One step of "how to play" (the site's .how): a gold rule on top, a title and a line.
struct ArcadeHow: View {
    let title: String
    let text: String
    @Environment(\.ski) private var ski
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title).font(ski.type.display(22)).foregroundStyle(.white)
            Text(text).font(ski.type.text(12.5)).foregroundStyle(.white).fixedSize(horizontal: false, vertical: true)
        }
        .padding(.horizontal, 8).padding(.top, 11).padding(.bottom, 8)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(Color.white.opacity(0.14))
        .overlay(alignment: .top) { Arcade.accent.frame(height: 3) }
    }
}

/// The big gold button with its ink shadow (the site's .go).
struct ArcadeGo: View {
    let text: String
    let action: () -> Void
    @Environment(\.ski) private var ski
    var body: some View {
        Button(action: action) {
            Text(text).font(ski.type.display(34)).foregroundStyle(Arcade.ink)
                .frame(maxWidth: .infinity, minHeight: 60)
                .background(Arcade.accent)
                .background(Arcade.ink.offset(y: 5))
        }
        .buttonStyle(.plain)
        .padding(.bottom, 5)
    }
}

struct ArcadeOutline: View {
    let text: String
    let action: () -> Void
    @Environment(\.ski) private var ski
    var body: some View {
        Button(action: action) {
            Text(text).font(ski.type.text(15, .bold)).foregroundStyle(.white).multilineTextAlignment(.center)
                .padding(.horizontal, 8).frame(maxWidth: .infinity, minHeight: 48)
                .overlay(Rectangle().stroke(Color.white, lineWidth: 2))
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

/// A result's number with its label (the end card).
struct ArcadeStat: View {
    let label: String
    let value: String
    @Environment(\.ski) private var ski
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(label).font(ski.type.text(11.5)).foregroundStyle(Arcade.muted).lineLimit(1).minimumScaleFactor(0.7)
            Text(value).font(ski.type.display(28)).foregroundStyle(Arcade.ink).lineLimit(1).minimumScaleFactor(0.6)
                .environment(\.layoutDirection, .leftToRight)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// The three goals under a result, each with its star.
struct ArcadeGoals: View {
    let lines: [String]
    let ok: [Bool]
    @Environment(\.ski) private var ski
    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            ForEach(lines.indices, id: \.self) { k in
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Text(ok[k] ? "★" : "☆").font(.system(size: 13)).foregroundStyle(ok[k] ? Arcade.accent : Arcade.rule)
                    Text(lines[k]).font(ski.type.text(13, ok[k] ? .bold : .regular)).foregroundStyle(ok[k] ? Arcade.ink : Arcade.muted)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.top, 8)
        .overlay(alignment: .top) { Arcade.rule.frame(height: 1) }
    }
}

/// The big word (the site's .pop): white with an ink shadow, it jumps in, stays a moment and floats away.
struct ArcadePop: View {
    let text: String?
    let n: Int
    let at: CGFloat
    let maxSize: CGFloat
    @Environment(\.ski) private var ski
    @State private var start = Date.distantPast

    var body: some View {
        GeometryReader { geo in
            TimelineView(.animation(paused: Date().timeIntervalSince(start) > 1.2)) { tl in
                let k = CGFloat(tl.date.timeIntervalSince(start) / 1.1)
                if let text, k < 1 {
                    let still = Arcade.still
                    let alpha = still ? 1 : k < 0.2 ? k / 0.2 : k < 0.75 ? 1 : 1 - (k - 0.75) / 0.25
                    let sc = still ? 1 : k < 0.2 ? 0.6 + (k / 0.2) * 0.48 : k < 0.3 ? 1.08 - (k - 0.2) / 0.1 * 0.08 : 1
                    let dy = still ? 0 : k < 0.2 ? 20 * (1 - k / 0.2) : k < 0.75 ? 0 : -30 * (k - 0.75) / 0.25
                    Text(text).font(ski.type.display(min(maxSize, max(34, geo.size.width * 0.11))))
                        .foregroundStyle(.white).shadow(color: Arcade.ink, radius: 0, x: 0, y: 6)
                        .lineLimit(1).fixedSize()
                        .scaleEffect(sc).opacity(alpha)
                        .position(x: geo.size.width / 2, y: geo.size.height * at + dy)
                }
            }
        }
        .allowsHitTesting(false)
        .onChange(of: n) { start = Date() }
    }
}

/// The top half of an ellipse around `c` (from the left, over the top, to the right), as points: no doubt about which
/// way an arc turns in a flipped space. `close` makes it a filled half.
func arcadeArc(_ c: CGPoint, _ rx: CGFloat, _ ry: CGFloat, close: Bool = false, steps: Int = 16) -> Path {
    var p = Path()
    for i in 0...steps {
        let a = CGFloat.pi + CGFloat.pi * CGFloat(i) / CGFloat(steps)
        let q = CGPoint(x: c.x + rx * cos(a), y: c.y + ry * sin(a))
        if i == 0 { p.move(to: q) } else { p.addLine(to: q) }
    }
    if close { p.closeSubpath() }
    return p
}
