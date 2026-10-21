import SwiftUI
import GudiDomain

/// The boarding pass's measures, as Android's home/Pass.kt and the site's .bp: a stub of 100 and half-circle bites of 11.
enum PassSize {
    static let stub: CGFloat = 100
    static let notch: CGFloat = 11
}

/// The pass's own colours (--bp-* on the site), and its glow at night.
struct PassInk {
    let paper, paper2, ink, muted, strip, onStrip, acc, dash, rule: Color
    let dark: Bool
}

/// A part of the boarding pass with round bites out of the two corners on the perforation (the site's .nl/.nr masks):
/// the main part has them at its end side, the stub at its start side, so the two meet in half-circles. In Hebrew the
/// stub is on the left, in English on the right (LT1).
struct PassShape: Shape {
    let seamAtEnd: Bool
    var r: CGFloat = PassSize.notch
    func path(in rect: CGRect) -> Path {
        // a clip shape is mirrored with the layout in right-to-left languages, so the path is drawn left to right
        let end = seamAtEnd
        let x = end ? rect.maxX : rect.minX
        var p = Path(rect)
        p.addEllipse(in: CGRect(x: x - r, y: rect.minY - r, width: 2 * r, height: 2 * r))
        p.addEllipse(in: CGRect(x: x - r, y: rect.maxY - r, width: 2 * r, height: 2 * r))
        return p
    }
}

struct PassLabel: View {
    let text: String
    let p: PassInk
    var lines = 1
    @Environment(\.ski) private var ski
    var body: some View {
        Text(text).font(ski.type.text(11, .semibold)).kerning(0.5).foregroundStyle(p.muted)
            .lineLimit(lines).multilineTextAlignment(lines > 1 ? .center : .leading)
    }
}

/// An airport code in the display face (Latin, so Karantina in every language).
struct PassCode: View {
    let code: String
    let color: Color
    var size: CGFloat = 46
    var body: some View {
        Text(code).font(.custom("Karantina-Bold", fixedSize: size)).foregroundStyle(color).lineLimit(1)
            .environment(\.layoutDirection, .leftToRight)
    }
}

/// The main part of the pass: the strip, then its body (AB5, H3).
struct PassMain<Content: View>: View {
    let p: PassInk
    let strip: String
    let date: String
    @ViewBuilder let content: Content
    @Environment(\.ski) private var ski
    @Environment(\.layoutDirection) private var dir

    var body: some View {
        let shape = PassShape(seamAtEnd: true)
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text(strip).font(ski.type.text(12, .bold)).kerning(0.5).foregroundStyle(p.onStrip).lineLimit(1)
                Spacer()
                Text(date).font(ski.type.text(12, .bold)).foregroundStyle(p.onStrip)
            }
            .padding(.horizontal, 16).padding(.vertical, 7)
            .background(p.strip)
            VStack(alignment: .leading, spacing: 10) { content }
                .padding(.horizontal, 16).padding(.top, 10).padding(.bottom, 12)
            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(p.paper)
        .clipShape(shape, style: FillStyle(eoFill: true))
        .shadow(color: .black.opacity(0.2), radius: 10, y: 3)
    }
}

/// The stub's face, and the dashed line of the perforation on its seam side.
struct PassStub<Content: View>: View {
    let p: PassInk
    @ViewBuilder let content: Content
    @Environment(\.layoutDirection) private var dir

    var body: some View {
        let shape = PassShape(seamAtEnd: false)
        VStack(spacing: 4) { content }
            .padding(.horizontal, 6).padding(.top, 12).padding(.bottom, 10)
            .frame(width: PassSize.stub)
            .frame(maxHeight: .infinity)
            .background(p.paper2)
            .overlay(alignment: .leading) {
                // the seam: the start side of the stub (right in Hebrew; .leading follows the direction)
                Rectangle().fill(.clear).frame(width: 2)
                    .overlay(Path { $0.move(to: .zero); $0.addLine(to: CGPoint(x: 0, y: 2000)) }
                        .stroke(p.dash, style: StrokeStyle(lineWidth: 2, dash: [6, 4])))
                    .clipped()
            }
            .clipShape(shape, style: FillStyle(eoFill: true))
            .shadow(color: .black.opacity(0.18), radius: 10, y: 3)
    }
}

/// A dashed frame around what is written but not set yet (the empty pass's TLV and TBS).
struct Dashed<Content: View>: View {
    let color: Color
    @ViewBuilder let content: Content
    var body: some View {
        content.padding(.horizontal, 8).padding(.vertical, 6)
            .overlay(Rectangle().inset(by: 0.75).stroke(color, style: StrokeStyle(lineWidth: 1.5, dash: [5, 4])))
    }
}

/// No trip yet (H1, round 20; Android's EmptyPass): the same pass, already from Tel Aviv to Tbilisi (dashed: not set
/// yet), and one question, "when do you fly?". The rest waits on the pass once the dates are in.
struct EmptyPass: View {
    let onWhen: () -> Void
    @Environment(\.ski) private var ski

    var body: some View {
        let c = ski.colors
        // the site's .bp-empty: the page's own colours, the strip in the run blue, the blanks in --rule
        let p = PassInk(paper: c.paper, paper2: c.paper, ink: c.ink, muted: c.muted, strip: c.blue, onStrip: c.onBoard,
                        acc: c.rule, dash: c.rule, rule: c.rule, dark: c.dark)
        HStack(spacing: 0) {
            PassMain(p: p, strip: S.appHomeTrip, date: S.appPassNotSet) {
                HStack(alignment: .bottom, spacing: 6) {
                    VStack(alignment: .leading, spacing: 2) {
                        PassLabel(text: S.ticketFrom, p: p)
                        Dashed(color: c.blue) { PassCode(code: "TLV", color: c.blue) }
                    }
                    Spacer()
                    Image(systemName: "airplane").font(.system(size: 22)).foregroundStyle(c.rule)
                        .flipsForRightToLeftLayoutDirection(true).frame(maxHeight: .infinity, alignment: .center)
                    Spacer()
                    VStack(alignment: .trailing, spacing: 2) {
                        PassLabel(text: S.ticketTo, p: p)
                        Dashed(color: c.blue) { PassCode(code: "TBS", color: c.blue) }
                    }
                }
                .fixedSize(horizontal: false, vertical: true)
                // the one question: a dashed button with the calendar
                Button(action: onWhen) {
                    HStack(spacing: 10) {
                        Image(systemName: "calendar").font(.system(size: 20)).foregroundStyle(c.blue)
                        Text(S.tripWhenFly).font(ski.type.display(30)).foregroundStyle(c.blue).lineLimit(1).minimumScaleFactor(0.55)
                    }
                    .padding(.horizontal, 18).frame(minHeight: 52)
                    .background(c.blue.opacity(0.08))
                    .overlay(Rectangle().inset(by: 1).stroke(c.blue, style: StrokeStyle(lineWidth: 2, dash: [6, 4])))
                }
                .buttonStyle(.plain)
                .frame(maxWidth: .infinity)
                .padding(.top, 6)
                .accessibilityLabel(S.tripWhenFly)
            }
            PassStub(p: p) {
                Spacer()
                PassLabel(text: S.appPassMore, p: p)
                Text("?").font(.custom("Karantina-Bold", fixedSize: 60)).foregroundStyle(c.rule)
                PassLabel(text: S.appPassDays(count: 5), p: p, lines: 2)
                Spacer()
            }
        }
        .frame(minHeight: 216)
        .fixedSize(horizontal: false, vertical: true)
    }
}
