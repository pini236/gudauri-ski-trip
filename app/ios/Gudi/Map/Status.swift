import SwiftUI
import GudiDomain

/// The lift status screens of the site (round 3, S1 to S3; Android's status/StatusUi.kt): the bar over the map, the
/// snowy signs while there is no report, and the departures board when there is one.

func agoText(_ s: LiftStatus) -> String {
    let m = s.minutesAgo
    if m < 1 { return S.statusAgoNow }
    if m < 60 { return S.statusAgoMinutes(n: String(m)) }
    return S.statusAgoHours(n: String(Int((Double(m) / 60).rounded())))
}

/// S1's bar over the map: a grey dot, or a green one that pulses while the report is fresh. A tap opens the board.
struct StatusBar: View {
    let s: LiftStatus
    let action: () -> Void
    @Environment(\.ski) private var ski
    @Environment(\.accessibilityReduceMotion) private var still
    @State private var pulse = false

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                ZStack {
                    if s.fresh && !still {
                        Circle().fill(ski.colors.green.opacity(pulse ? 0 : 1)).frame(width: 10, height: 10).scaleEffect(pulse ? 2.6 : 1)
                    }
                    Circle().fill(s.fresh ? ski.colors.green : ski.colors.dash).frame(width: 10, height: 10)
                }
                summary.foregroundStyle(ski.colors.ink).lineLimit(1)
            }
            .padding(.horizontal, 12).padding(.vertical, 6).frame(minHeight: 44)
            .background(ski.colors.paper.opacity(0.94)).overlay(Rectangle().stroke(ski.colors.rule, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .onAppear { withAnimation(.easeOut(duration: 2).repeatForever(autoreverses: false)) { pulse = true } }
    }

    /// "11 of 12 lifts open · updated 6 min ago", the two numbers in the sign voice.
    private var summary: Text {
        let f = ski.type.text(13, .semibold)
        guard s.fresh else { return Text(s.report == nil ? S.statusBarNoDataYet : S.statusBarNoRecent).font(f) }
        let raw = S.statusBarSummary(open: "\u{1}", total: "\u{2}", ago: agoText(s))
        var out = Text("")
        var rest = Substring(raw)
        while let i = rest.firstIndex(where: { $0 == "\u{1}" || $0 == "\u{2}" }) {
            out = out + Text(rest[..<i]).font(f)
            out = out + Text(String(rest[i] == "\u{1}" ? s.open : s.names.count)).font(ski.type.display(20))
            rest = rest[rest.index(after: i)...]
        }
        return out + Text(rest).font(f)
    }
}

/// The board's content (the site's lstat block): the snowy signs without a fresh report (S3), the board with one (S2).
struct StatusBoard: View {
    let s: LiftStatus
    let changes: [(String, Bool)]
    @Binding var forMe: Bool
    let inSeason: Bool
    @Environment(\.ski) private var ski

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if !s.fresh {
                Text(inSeason ? S.statusNoRecentData : S.statusMountainAsleep).font(ski.type.display(30)).foregroundStyle(ski.colors.ink)
                    .accessibilityAddTraits(.isHeader)
                Text(inSeason ? S.statusLeadInSeason : S.statusLeadOffSeason).font(ski.type.text(15)).foregroundStyle(ski.colors.muted).padding(.top, 4)
                SnowySigns(names: Array(s.names.prefix(6)))
                (Text(S.statusNoGuessingBold).bold() + Text(" " + S.statusNoGuessingText))
                    .font(ski.type.text(13)).foregroundStyle(ski.colors.muted)
            } else {
                Text(S.statusHeadingLiftStatus).font(ski.type.display(30)).foregroundStyle(ski.colors.ink).accessibilityAddTraits(.isHeader)
                if !changes.isEmpty {
                    VStack(alignment: .leading, spacing: 2) {
                        ForEach(changes, id: \.0) { n, open in
                            Text(open ? S.statusChangeOpened(lift: n) : S.statusChangeClosed(lift: n)).font(ski.type.text(14, .bold))
                        }
                    }
                    .foregroundStyle(Color(rgb: 0x13233A)).padding(.horizontal, 12).padding(.vertical, 8)
                    .frame(maxWidth: .infinity, alignment: .leading).background(Color(rgb: 0xF4B942)).padding(.top, 10)
                }
                DepartureBoard(s: s).padding(.top, 10)
                // "only what's open for me": the site's chip (.fchip), square, 44 high
                Button { forMe.toggle(); Fx.shared.tick() } label: {
                    Text(S.statusOnlyOpenForMe).font(ski.type.text(14, .bold)).foregroundStyle(forMe ? .white : ski.colors.ink)
                        .padding(.horizontal, 14).frame(minHeight: 44).background(forMe ? ski.colors.ink : ski.colors.paper)
                        .overlay(Rectangle().stroke(ski.colors.ink, lineWidth: 1.5))
                }
                .buttonStyle(.plain).padding(.top, 10)
                .accessibilityAddTraits(.isToggle).accessibilityValue(forMe ? S.appOn : S.appOff)
                Text(S.statusBoardHint(open: String(s.open), total: String(s.names.count))).font(ski.type.text(13)).foregroundStyle(ski.colors.muted).padding(.top, 10)
            }
        }
    }
}

/// S3: the lifts' signs on a post, under soft heaps of snow, with a question mark instead of a state.
private struct SnowySigns: View {
    let names: [String]
    @Environment(\.ski) private var ski
    @Environment(\.layoutDirection) private var dir

    var body: some View {
        VStack(alignment: .leading, spacing: 22) {
            ForEach(Array(names.enumerated()), id: \.offset) { i, n in
                let fg = (i % 2 == 0 ? ski.colors.onBoard : .white).opacity(0.74)
                HStack {
                    Text(n).font(ski.type.display(28)).lineLimit(1)
                    Spacer()
                    Text("?").font(ski.type.display(28))
                }
                .foregroundStyle(fg)
                .padding(.leading, 14).padding(.trailing, 30).padding(.top, 12)
                .frame(maxWidth: .infinity, minHeight: 52, maxHeight: 52)
                .background(i % 2 == 0 ? ski.colors.blue : Color(rgb: ski.colors.p.lsDark), in: SignShape(arrow: 18, rtl: dir == .rightToLeft))
                .snow(seed: i, kind: .low, arrow: 18)
            }
        }
        .padding(.leading, 27).padding(.top, 34).padding(.bottom, 30)
        .background(alignment: .leading) {
            // the post at the start side, a little out from the signs, past the first and the last one
            Rectangle().fill(Color(rgb: 0x13233A)).frame(width: 7).padding(.leading, 6).padding(.top, 22)
        }
    }
}

/// S2: a departures board, dark in day and night, its rows flipping in one after the other.
private struct DepartureBoard: View {
    let s: LiftStatus
    @Environment(\.ski) private var ski
    @Environment(\.accessibilityReduceMotion) private var still
    @State private var turned = false

    private func reason(_ r: String) -> String {
        switch r { case "wind": S.statusReasonWind; case "weather": S.statusReasonWeather; case "maintenance": S.statusReasonMaintenance; case "season": S.statusReasonSeason; default: r }
    }

    var body: some View {
        let head = Color(rgb: 0xA3B3C8), line = Color(rgb: 0x2A3B55), fg = Color(rgb: 0xEAF0F7)
        VStack(spacing: 0) {
            HStack(spacing: 8) {
                Text(S.statusBoardColLift).frame(width: 132, alignment: .leading)
                Text(S.statusBoardColState).frame(width: 70, alignment: .leading)
                Text(S.statusBoardColNote).frame(maxWidth: .infinity, alignment: .leading)
            }
            .font(ski.type.text(11.5)).foregroundStyle(head).frame(minHeight: 26)
            .overlay(alignment: .bottom) { Rectangle().fill(line).frame(height: 1) }
            ForEach(Array(s.names.enumerated()), id: \.offset) { i, n in
                let o = s.isOpen(n)
                HStack(spacing: 8) {
                    // the name in a fixed column (the longest, New Goodaura, fits), so the reason keeps the rest of the row
                    Text(n).font(ski.type.display(22)).foregroundStyle(fg).lineLimit(1).minimumScaleFactor(0.7).frame(width: 132, alignment: .leading)
                    Text(o == true ? S.statusLiftOpen : o == false ? S.statusLiftClosed : "—").font(ski.type.text(14, .bold))
                        .foregroundStyle(o == true ? Color(rgb: 0x3CC47C) : o == false ? Color(rgb: 0xFF6A5F) : fg).frame(width: 70, alignment: .leading)
                    Text(s.reason(n).map(reason) ?? "").font(ski.type.text(13)).foregroundStyle(head).lineLimit(2).frame(maxWidth: .infinity, alignment: .leading)
                }
                .frame(minHeight: 36)
                .overlay(alignment: .bottom) { Rectangle().fill(line).frame(height: 1) }
                .rotation3DEffect(.degrees(turned || still ? 0 : 90), axis: (x: 1, y: 0, z: 0))
                .opacity(turned || still ? 1 : 0)
                .animation(.easeOut(duration: 0.5).delay(0.06 * Double(i)), value: turned)
                .accessibilityElement(children: .combine)
            }
        }
        .padding(.horizontal, 10).padding(.vertical, 6).background(Color(rgb: 0x0D1522))
        .accessibilityLabel(S.statusBoardAria)
        .onAppear { turned = true }
    }
}
