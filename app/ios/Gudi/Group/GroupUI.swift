import SwiftUI
import UIKit
import GudiDomain

// The pieces the account and group screens share (design/round10/build.py, Android's group/GroupUi.kt and ui/Widgets.kt):
// the snowy cards, the switch, the buttons, the fields. Square corners, touch targets of at least 44, the tokens'
// colours. Named with a G so they never meet another part's.

let gGold = Color(rgb: 0xF4B942)

/// A paper card with a coloured top edge and fresh snow on it (A1, Q3; the site's .ac-card with data-snow-pile).
struct GSnowCard<Content: View>: View {
    let edge: Color
    let seed: Int
    @ViewBuilder let content: Content
    @Environment(\.ski) private var ski

    var body: some View {
        VStack(alignment: .leading, spacing: 0) { content }
            .padding(.horizontal, 16).padding(.top, 22).padding(.bottom, 16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(ski.colors.paper)
            .overlay(alignment: .top) { Rectangle().fill(edge).frame(height: 6) }
            .snow(seed: seed, kind: .pile, border: 6)
            .shadow(color: Color(rgb: 0x13233A, opacity: 0.1), radius: 6, y: 2)
            // the pile rises above the card: room for it, so it never covers what is above
            .padding(.top, 26)
    }
}

/// A big title in the sign voice.
struct GDisplay: View {
    let text: String
    var size: CGFloat = 34
    var color: Color? = nil
    @Environment(\.ski) private var ski
    init(_ text: String, _ size: CGFloat = 34, color: Color? = nil) { self.text = text; self.size = size; self.color = color }
    var body: some View { Text(text).font(ski.type.display(size)).foregroundStyle(color ?? ski.colors.ink).fixedSize(horizontal: false, vertical: true) }
}

struct GMuted: View {
    let text: String
    var size: CGFloat = 13.5
    @Environment(\.ski) private var ski
    init(_ text: String, _ size: CGFloat = 13.5) { self.text = text; self.size = size }
    var body: some View { Text(text).font(ski.type.text(size)).foregroundStyle(ski.colors.muted).fixedSize(horizontal: false, vertical: true) }
}

/// A short line with an icon: where something is kept, what happens next.
struct GNote: View {
    let text: String
    let icon: String
    @Environment(\.ski) private var ski
    init(_ text: String, _ icon: String) { self.text = text; self.icon = icon }
    var body: some View {
        HStack(alignment: .top, spacing: 8) {
            Image(systemName: icon).font(.system(size: 15)).foregroundStyle(ski.colors.muted).frame(width: 18).accessibilityHidden(true)
            Text(text).font(ski.type.text(13)).foregroundStyle(ski.colors.muted).fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// The main action: blue with the page's on-board text, 52 high.
struct GPrimary: View {
    let text: String
    var icon: String? = nil
    var full = true
    var enabled = true
    let action: () -> Void
    @Environment(\.ski) private var ski
    var body: some View {
        Button(action: { Fx.shared.tick(); action() }) {
            HStack(spacing: 8) {
                if let icon { Image(systemName: icon).font(.system(size: 18, weight: .semibold)).accessibilityHidden(true) }
                Text(text).font(ski.type.text(16.5, .bold)).lineLimit(1).minimumScaleFactor(0.7)
            }
            .foregroundStyle(ski.colors.onBoard)
            .padding(.horizontal, 20).frame(minHeight: 52)
            .frame(maxWidth: full ? .infinity : nil)
            .background(ski.colors.blue)
            .opacity(enabled ? 1 : 0.5)
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }
}

/// A quiet action: just the words, in the link colour (red for a destructive one).
struct GQuiet: View {
    let text: String
    var danger = false
    let action: () -> Void
    @Environment(\.ski) private var ski
    var body: some View {
        Button(action: action) {
            Text(text).font(ski.type.text(16.5, .bold)).foregroundStyle(danger ? ski.colors.red : ski.colors.glacier)
                .frame(maxWidth: .infinity, minHeight: 52)
        }
        .buttonStyle(.plain)
    }
}

enum GLook { case ink, ghost, danger, gold }

/// The other buttons of the canvas: ink, an outlined one, red for a destructive one.
struct GButton: View {
    let text: String
    let look: GLook
    var icon: String? = nil
    var small = false
    var full = true
    var enabled = true
    let action: () -> Void
    @Environment(\.ski) private var ski

    var body: some View {
        let c = ski.colors
        let (bg, fg, line): (Color, Color, Color?) = switch look {
        case .ink: (c.ink, c.paper, nil)
        case .ghost: (.clear, c.ink, c.ink)
        case .danger: (.clear, c.red, c.red)
        case .gold: (gGold, Color(rgb: 0x13233A), nil)
        }
        Button(action: action) {
            HStack(spacing: 8) {
                if let icon { Image(systemName: icon).font(.system(size: 17, weight: .semibold)).accessibilityHidden(true) }
                Text(text).font(ski.type.text(small ? 15 : 16.5, .bold)).lineLimit(1).minimumScaleFactor(0.7)
            }
            .foregroundStyle(fg.opacity(enabled ? 1 : 0.5))
            .padding(.horizontal, 18).frame(minHeight: small ? 44 : 52)
            .frame(maxWidth: full ? .infinity : nil)
            .background(bg)
            .overlay { if let line { Rectangle().inset(by: 1).stroke(line, lineWidth: 2) } }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }
}

/// A labelled text field (the canvas's .fld): 48 high, a 1.5 border, square.
struct GField: View {
    let label: String
    @Binding var text: String
    var hint = ""
    var ltr = false
    var caps = false
    var max = 60
    @FocusState private var focused: Bool
    @Environment(\.ski) private var ski

    var body: some View {
        let c = ski.colors
        VStack(alignment: .leading, spacing: 5) {
            Text(label).font(ski.type.text(13, .semibold)).foregroundStyle(c.muted)
            TextField("", text: $text, prompt: Text(hint).foregroundStyle(c.muted.opacity(0.75)))
                .font(ski.type.text(16)).foregroundStyle(c.ink)
                .textInputAutocapitalization(caps ? .characters : .words)
                .autocorrectionDisabled(ltr)
                .focused($focused)
                .padding(.horizontal, 12).frame(height: 48)
                .background(c.paper)
                .overlay(Rectangle().inset(by: focused ? 1 : 0.75).stroke(focused ? c.accent : c.rule, lineWidth: focused ? 2 : 1.5))
                .environment(\.layoutDirection, ltr ? .leftToRight : ski.type.lang.rtl ? .rightToLeft : .leftToRight)
                .accessibilityLabel(label)
                .onChange(of: text) { _, v in if v.count > max { text = String(v.prefix(max)) } }
        }
    }
}

/// The switch of the site's settings (.ab-sw, round 18): 52 by 30, grey when off and green when on, a paper knob of 24.
struct GToggle: View {
    let label: String
    var sub: String? = nil
    let on: Bool
    var enabled = true
    let onChange: (Bool) -> Void
    @Environment(\.ski) private var ski

    var body: some View {
        let c = ski.colors
        VStack(spacing: 0) {
            Button { Fx.shared.tick(); onChange(!on) } label: {
                HStack(spacing: 14) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(label).font(ski.type.text(15.5, .bold)).foregroundStyle(c.ink)
                        if let sub { Text(sub).font(ski.type.text(13)).foregroundStyle(c.muted) }
                    }
                    Spacer(minLength: 0)
                    ZStack(alignment: on ? .trailing : .leading) {
                        Rectangle().fill(on ? c.green : c.rule).frame(width: 52, height: 30)
                        Rectangle().fill(c.paper).frame(width: 24, height: 24).padding(3).shadow(radius: 1)
                    }
                    .animation(.easeOut(duration: 0.2), value: on)
                }
                .padding(.vertical, 8).frame(minHeight: 52).contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(!enabled)
            .accessibilityAddTraits(.isToggle)
            .accessibilityValue(on ? S.appOn : S.appOff)
            Rectangle().fill(c.rule).frame(height: 1)
        }
    }
}

/// "Sign in with Apple": the system's own button, as Apple's rules ask (App Store guideline 4.8).
struct GAppleButton: View {
    let text: String
    let action: () -> Void
    @Environment(\.ski) private var ski
    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                Image(systemName: "applelogo").font(.system(size: 19))
                Text(text).font(.system(size: 17, weight: .semibold))
            }
            .foregroundStyle(ski.colors.dark ? .black : .white)
            .frame(maxWidth: .infinity, minHeight: 52)
            .background(ski.colors.dark ? Color.white : Color.black)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(text)
    }
}

/// "Continue with Google": white, a grey outline and the G mark, as Google's button looks (hidden until its client exists).
struct GGoogleButton: View {
    let text: String
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                GoogleG().frame(width: 20, height: 20)
                Text(text).font(.system(size: 16.5, weight: .semibold)).foregroundStyle(Color(rgb: 0x1F1F1F))
            }
            .frame(maxWidth: .infinity, minHeight: 52)
            .background(Color.white)
            .overlay(Rectangle().inset(by: 0.5).stroke(Color(rgb: 0x747775), lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}

/// Google's own four-colour "G" (round 13, item 3), drawn as arcs.
struct GoogleG: View {
    var body: some View {
        Canvas { ctx, size in
            let r = min(size.width, size.height) / 2, c = CGPoint(x: size.width / 2, y: size.height / 2)
            let w = r * 0.38
            func arc(_ a: Double, _ b: Double, _ col: UInt32) {
                var p = Path()
                p.addArc(center: c, radius: r - w / 2, startAngle: .degrees(a), endAngle: .degrees(b), clockwise: false)
                ctx.stroke(p, with: .color(Color(rgb: col)), lineWidth: w)
            }
            arc(-40, 45, 0x4285F4); arc(45, 135, 0x34A853); arc(135, 210, 0xFBBC05); arc(210, 320, 0xEA4335)
            ctx.fill(Path(CGRect(x: c.x, y: c.y - w / 2, width: r, height: w)), with: .color(Color(rgb: 0x4285F4)))
        }
        .environment(\.layoutDirection, .leftToRight)
    }
}

/// A person's first letter in a square (A4, Q10): ink for admins, pale for members.
struct GAvatar: View {
    let name: String
    let strong: Bool
    var size: CGFloat = 36
    @Environment(\.ski) private var ski
    var body: some View {
        Text(String(name.trimmingCharacters(in: .whitespaces).prefix(1)))
            .font(ski.type.display(size * 0.66)).foregroundStyle(strong ? ski.colors.paper : ski.colors.ink)
            .frame(width: size, height: size)
            .background(strong ? ski.colors.ink : ski.colors.grid)
            .accessibilityHidden(true)
    }
}

/// Three things as icons with a word under each (round 20, .r20-why).
struct GIconTrio: View {
    let items: [(String, String)]
    @Environment(\.ski) private var ski
    var body: some View {
        HStack(alignment: .top, spacing: 18) {
            ForEach(items.indices, id: \.self) { i in
                VStack(spacing: 6) {
                    Image(systemName: items[i].0).font(.system(size: 24)).foregroundStyle(ski.colors.glacier).frame(height: 28)
                    Text(items[i].1).font(ski.type.text(13, .bold)).foregroundStyle(ski.colors.ink).multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity)
                .accessibilityElement(children: .combine)
            }
        }
        .padding(.vertical, 8)
    }
}

/// A request waiting for an admin (round 20, .r20-pend): an hourglass, "waiting for the admin", and the way to withdraw it.
struct GPendingCard: View {
    let onCancel: (() -> Void)?
    @Environment(\.ski) private var ski
    var body: some View {
        let c = ski.colors
        HStack(spacing: 14) {
            Image(systemName: "hourglass").font(.system(size: 26)).foregroundStyle(Color(rgb: 0xC98A00)).accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 4) {
                GDisplay(S.joinPendingTitle, 24)
                if let onCancel {
                    Button(action: onCancel) {
                        Text(S.joinCancelRequest).font(ski.type.text(14, .bold)).foregroundStyle(c.glacier).frame(minHeight: 44)
                    }
                    .buttonStyle(.plain)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(.leading, 22).padding(.trailing, 16).padding(.vertical, 14)
        .background(c.paper)
        .overlay(alignment: .leading) { Rectangle().fill(gGold).frame(width: 6) }
        .shadow(color: Color(rgb: 0x0D1522, opacity: 0.1), radius: 4, y: 1)
        .padding(.vertical, 6)
    }
}

/// The way a refusal from the server reads in the user's language: a message for every code in server/CONTRACT.md that
/// a person can meet (ד5); the table and its test are GudiDomain's ErrorKeys.
func errorText(_ code: String) -> String { Strings.shared.text(ErrorKeys.key(code)) }

/// One action at a time: busy while it runs, the error if it failed (Android's Runner).
@MainActor
@Observable
final class GRunner {
    var busy = false
    var error: String?

    func run(_ block: @escaping @MainActor () async throws -> Void) {
        guard !busy else { return }
        busy = true; error = nil
        Task { @MainActor in
            do { try await block() } catch let e as ApiError {
                if e.code != "cancelled" { error = e.code }
            } catch { self.error = "general" }
            busy = false
        }
    }
}

/// The error line under a form, in red, read out when it appears.
struct GErrorLine: View {
    let runner: GRunner
    @Environment(\.ski) private var ski
    var body: some View {
        if let e = runner.error {
            Text(errorText(e)).font(ski.type.text(14, .bold)).foregroundStyle(ski.colors.red)
                .frame(maxWidth: .infinity, alignment: .leading).fixedSize(horizontal: false, vertical: true)
                .onAppear { UIAccessibility.post(notification: .announcement, argument: errorText(e)) }
        }
    }
}

/// A plain scrolling page under the bar, with the canvas's side margins.
struct GPage<Content: View>: View {
    @ViewBuilder let content: Content
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) { content }
                .padding(.horizontal, 16).padding(.top, 8).padding(.bottom, 24)
                .frame(maxWidth: 560)
                .frame(maxWidth: .infinity)
        }
        .scrollDismissesKeyboard(.interactively)
    }
}

/// A sheet's frame, iPhone style: the system's sheet with its grabber, the page's paper, the language's direction.
struct GSheet<Content: View>: View {
    @ViewBuilder let content: Content
    @Environment(\.ski) private var ski
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) { content }
                .padding(.horizontal, 20).padding(.top, 24).padding(.bottom, 24)
        }
        .background(ski.colors.paper)
        .presentationDragIndicator(.visible)
        .presentationBackground(ski.colors.paper)
        // a sheet is a new hierarchy: the language's direction again
        .environment(\.layoutDirection, ski.type.lang.rtl ? .rightToLeft : .leftToRight)
    }
}

/// Dates as the app writes them: "ב׳ · 10.1.2027" with the day of the week in the app's language.
func gDayText(_ d: Day) -> String {
    var c = DateComponents(); c.year = d.year; c.month = d.month; c.day = d.day
    var cal = Calendar(identifier: .gregorian); cal.timeZone = TimeZone(secondsFromGMT: 0)!
    guard let date = cal.date(from: c) else { return d.iso }
    let f = DateFormatter()
    f.calendar = cal; f.timeZone = cal.timeZone
    f.locale = Locale(identifier: Strings.shared.lang.rawValue)
    f.dateFormat = "EEE"
    return "\(f.string(from: date)) · \(d.day).\(d.month).\(d.year)"
}

extension Day {
    /// The day of a date in the phone's calendar (a date picker's value).
    static func gOf(_ date: Date) -> Day {
        let c = Calendar.current.dateComponents([.year, .month, .day], from: date)
        return Day(c.year!, c.month!, c.day!)!
    }

    var gDate: Date {
        var c = DateComponents(); c.year = year; c.month = month; c.day = day; c.hour = 12
        return Calendar.current.date(from: c) ?? Date()
    }
}
