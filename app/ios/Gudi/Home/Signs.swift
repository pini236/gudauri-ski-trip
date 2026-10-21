import SwiftUI

/// The trail sign's board: square corners and an arrow at the end of the reading direction (left in Hebrew, right in
/// English; LT1 to LT3).
struct SignShape: Shape {
    var arrow: CGFloat = 22
    var rtl: Bool
    func path(in r: CGRect) -> Path {
        var p = Path()
        if rtl {
            p.move(to: CGPoint(x: r.minX, y: r.midY)); p.addLine(to: CGPoint(x: r.minX + arrow, y: r.minY))
            p.addLine(to: CGPoint(x: r.maxX, y: r.minY)); p.addLine(to: CGPoint(x: r.maxX, y: r.maxY)); p.addLine(to: CGPoint(x: r.minX + arrow, y: r.maxY))
        } else {
            p.move(to: CGPoint(x: r.minX, y: r.minY)); p.addLine(to: CGPoint(x: r.maxX - arrow, y: r.minY))
            p.addLine(to: CGPoint(x: r.maxX, y: r.midY)); p.addLine(to: CGPoint(x: r.maxX - arrow, y: r.maxY)); p.addLine(to: CGPoint(x: r.minX, y: r.maxY))
        }
        p.closeSubpath()
        return p
    }
}

/// The trail colours of the day, for what keeps them at night too (the meeting point's gold sign).
enum SignColors {
    static let gold = Color(rgb: 0xF4B942)
    static let ink = Color(rgb: 0x13233A)
}

struct SignSpec: Identifiable {
    let id = UUID()
    let title: String
    let sub: String?
    let color: Color
    let fg: Color
    /// The share of the row the sign takes, as on the site and Android.
    let width: CGFloat
    let action: () -> Void
}

/// The post with its signs (the site's home, H1 and H3): a dark post at the start side, the signs hanging from it and
/// pointing on. The post runs down past the last sign, to the bottom of the page.
struct SignPost: View {
    let signs: [SignSpec]
    @Environment(\.ski) private var ski
    @Environment(\.layoutDirection) private var dir

    var body: some View {
        GeometryReader { g in
            VStack(alignment: .leading, spacing: 12) {
                ForEach(signs) { s in Sign(s: s, width: (g.size.width - 30) * s.width) }
            }
            .padding(.leading, 30)
            .background(alignment: .topLeading) {
                Rectangle().fill(ski.colors.ink).frame(width: 6, height: 2000).offset(x: 20, y: -8)
            }
        }
        .frame(height: CGFloat(signs.count) * 76 + 40)
    }
}

private struct Sign: View {
    let s: SignSpec
    let width: CGFloat
    @Environment(\.ski) private var ski
    @Environment(\.layoutDirection) private var dir

    var body: some View {
        let shape = SignShape(rtl: dir == .rightToLeft)
        Button(action: s.action) {
            VStack(alignment: .leading, spacing: 0) {
                Text(s.title).font(ski.type.display(34)).foregroundStyle(s.fg).lineLimit(1).minimumScaleFactor(0.7)
                if let sub = s.sub, !sub.isEmpty {
                    Text(sub).font(ski.type.text(13)).foregroundStyle(s.fg.opacity(0.92))
                }
            }
            .padding(.leading, 18).padding(.trailing, 34).padding(.vertical, 10)
            .frame(width: width, alignment: .leading)
            .frame(minHeight: 64)
            .background(s.color, in: shape)
            .contentShape(shape)
            .shadow(color: Color(rgb: 0x13233A, opacity: 0.16), radius: 5, y: 2)
        }
        .buttonStyle(.plain)
    }
}
