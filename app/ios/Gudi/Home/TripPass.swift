import SwiftUI
import GudiDomain

/// The boarding pass for the user's own trip (decisions 19 and 27, H3 and H4; Android's home/Pass.kt TripPass): the
/// return pass behind, its top showing (AB5, AB6); a tap on the one behind brings it to the front, a swipe on the front
/// one too. A tap on the stub tears it along the perforation, hole by hole, with the recorded tear and a tick of the
/// haptics per hole; it comes back. In left-to-right languages the stack mirrors (LT1).
struct TripPass: View {
    let trip: Trip
    let now: LocalMoment
    let onEdit: () -> Void
    @Environment(\.ski) private var ski
    @Environment(\.layoutDirection) private var dir
    @Environment(\.accessibilityReduceMotion) private var still

    @State private var showRet = false
    @State private var shuffle: CGFloat = 0 // 0..1 while the two cards change places
    @State private var drag: CGFloat = 0
    @State private var tear: CGFloat = 0 // 0..1 down the perforation
    @State private var fall: CGFloat = 0 // then it falls
    @State private var busy = false
    /// The field of the pass being edited (round 20).
    @State private var edit: PassEdit?

    /// How far the card behind shows above the one in front (its strip).
    static let peek: CGFloat = 46
    static let holes = 14

    private var ink: PassInk {
        let c = ski.colors
        return PassInk(paper: c.bpPaper, paper2: c.bpPaper2, ink: c.bpInk, muted: c.bpMuted, strip: c.bpStrip, onStrip: c.bpOnStrip,
                       acc: c.bpAccent, dash: c.dash, rule: c.dark ? Color(rgb: 0x3A4A66) : c.rule, dark: c.dark)
    }

    var body: some View {
        let hasRet = trip.ret != nil
        let frontIsRet = showRet && hasRet
        // the card behind sits up and toward the end, leaning back (the site's poses, as Android)
        let s: CGFloat = dir == .rightToLeft ? 1 : -1
        VStack(alignment: .leading, spacing: 10) {
            ZStack {
                if hasRet {
                    card(isRet: !frontIsRet, front: false)
                        .scaleEffect(0.955, anchor: UnitPoint(x: 0.5, y: 0.6))
                        .rotationEffect(.degrees(2.6 * s))
                        .offset(x: -12 * s, y: -Self.peek + shuffle * 40)
                        .overlay(Color.black.opacity(ink.dark ? 0.28 : 0.10).allowsHitTesting(false))
                        .zIndex(shuffle > 0.4 ? 1 : 0)
                        .onTapGesture { swap() }
                        .accessibilityElement(children: .ignore)
                        .accessibilityLabel(frontIsRet ? S.ticketSwapOut : S.ticketSwapReturn)
                        .accessibilityAddTraits(.isButton)
                        .accessibilityAction { swap() }
                }
                card(isRet: frontIsRet, front: true)
                    .offset(x: drag + 26 * s * shuffle, y: 84 * shuffle)
                    .rotationEffect(.degrees(Double(-7 * s * shuffle)))
                    .zIndex(shuffle > 0.4 ? 0 : 1)
                    .gesture(DragGesture(minimumDistance: 12).onChanged { v in
                        if !busy { drag = v.translation.width * (hasRet ? 0.45 : 0.2) }
                    }.onEnded { v in
                        if hasRet && abs(v.translation.width) > 80 { swap() } else { withAnimation(.spring(dampingFraction: 0.6)) { drag = 0 } }
                    })
            }
            .padding(.top, hasRet ? Self.peek : 0)
            // under the pass: what the gestures do, and editing the trip
            HStack(alignment: .center) {
                Text((hasRet ? S.ticketHintSwap.trimmingCharacters(in: .whitespaces) + " " : "") + S.ticketHintTear)
                    .font(ski.type.text(12)).foregroundStyle(ski.colors.muted)
                Spacer()
                Button(action: onEdit) {
                    Label(S.appEdit, systemImage: "pencil").font(ski.type.text(13.5, .bold)).foregroundStyle(ink.acc)
                        .frame(minHeight: 44)
                }
                .buttonStyle(.plain)
            }
        }
        .sheet(item: $edit) { PassEditor(edit: $0) }
    }

    // the site's shuffle: the front card slides down and out, the one behind comes forward, and they settle swapped
    private func swap() {
        guard trip.ret != nil, !busy else { return }
        busy = true
        Fx.shared.play("ticket-slide", volume: 0.7); Fx.shared.tick(0.5)
        if still {
            showRet.toggle(); drag = 0; busy = false; return
        }
        withAnimation(.easeIn(duration: 0.26)) { shuffle = 1; drag = 0 }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.42) {
            Fx.shared.play("ticket-land", volume: 0.75); Fx.shared.click(0.6)
            showRet.toggle()
            withAnimation(.easeOut(duration: 0.38)) { shuffle = 0 }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { busy = false }
        }
    }

    private func tearStub() {
        guard !busy else { return }
        busy = true
        Fx.shared.play("ticket-tear", volume: 0.85)
        // a tick per hole as the tear runs down the perforation
        for i in 0..<Self.holes {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.52 * Double(i) / Double(Self.holes)) {
                Fx.shared.tick(0.35 + CGFloat(i % 3) * 0.1)
            }
        }
        withAnimation(.linear(duration: 0.52)) { tear = 1 }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.52) {
            Fx.shared.click(1)
            withAnimation(.easeIn(duration: 0.62)) { fall = 1 }
            DispatchQueue.main.asyncAfter(deadline: .now() + 2.1) {
                tear = 0; fall = 0
                Fx.shared.play("ticket-land", volume: 0.6); Fx.shared.click(0.5)
                busy = false
            }
        }
    }

    @ViewBuilder
    private func card(isRet: Bool, front: Bool) -> some View {
        let p = ink
        let leg = isRet ? trip.ret! : trip.out
        // the site's strip: "your trip · return", and " · overnight" on a leg that leaves before 06:00 (A-33)
        let night = (leg.departs.map { $0.hour < 6 } ?? false) ? S.ticketNoteOvernight : ""
        HStack(spacing: 0) {
            PassMain(p: p, strip: isRet ? S.ticketStripMineReturn(note: night) : S.ticketStripMineOut(note: night), date: shortDate(leg.date)) {
                HStack(alignment: .bottom, spacing: 6) {
                    VStack(alignment: .leading, spacing: 2) {
                        PassLabel(text: S.ticketFrom, p: p)
                        PassCode(code: leg.fromCode ?? leg.fromCity, color: p.ink, size: 48)
                        Text(leg.fromCity).font(ski.type.text(12.5, .semibold)).foregroundStyle(p.ink).lineLimit(1)
                    }
                    .editing(front) { edit = .place(from: true) }
                    Spacer()
                    Image(systemName: "airplane").font(.system(size: 22)).foregroundStyle(p.acc)
                        .flipsForRightToLeftLayoutDirection(true).frame(maxHeight: .infinity)
                    Spacer()
                    VStack(alignment: .trailing, spacing: 2) {
                        PassLabel(text: S.ticketTo, p: p)
                        PassCode(code: leg.toCode ?? leg.toCity, color: p.ink, size: 48)
                        Text(leg.toCity).font(ski.type.text(12.5, .semibold)).foregroundStyle(p.ink).lineLimit(1)
                    }
                    .editing(front) { edit = .place(from: false) }
                }
                .fixedSize(horizontal: false, vertical: true)
                grid(p) {
                    // round 20: the pass is the form; each field opens its own sheet (Trip/PassEditor), front card only
                    cell(S.ticketFlight, p) { value(leg.flight.isEmpty ? S.tripAddNumber : leg.flight, p, add: leg.flight.isEmpty) }
                        .editing(front) { edit = .flight(ret: isRet) }
                    cell(S.ticketDeparts, p) { value(leg.departs?.text ?? S.tripAddTime, p, add: leg.departs == nil) }
                        .editing(front) { edit = .time(ret: isRet, departs: true) }
                    cell(S.ticketArrives, p) { value(leg.arrives?.text ?? S.tripAddTime, p, add: leg.arrives == nil) }
                        .editing(front) { edit = .time(ret: isRet, departs: false) }
                }
                grid(p) {
                    cell(S.appPassTraveller, p) { value(S.appPassMe, p) }
                    cell(S.ticketSkiDays, p) { value(trip.skiDays().map(dayRange) ?? "—", p) }
                        .editing(front) { edit = .dates }
                    let other = isRet ? trip.out : trip.ret
                    cell(isRet ? S.appPassOut : S.appPassRet, p) { value(other.map { shortDate($0.date) } ?? "—", p) }
                        .editing(front) { edit = .dates }
                }
            }
            ZStack {
                // the slot: what is left once the stub is gone
                if front {
                    Text(S.ticketTorn + "\n" + S.ticketSeeYou).font(ski.type.text(12)).multilineTextAlignment(.center)
                        .foregroundStyle(p.muted).padding(6)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .overlay(Rectangle().stroke(p.dash.opacity(0.8), lineWidth: 2)).padding(.vertical, 2)
                }
                PassStub(p: p) { stub(isRet: isRet, leg: leg, p: p) }
                    .rotationEffect(.degrees(Double((dir == .rightToLeft ? -1 : 1) * (9 * (front ? tear : 0) + 25 * (front ? fall : 0)))),
                                    anchor: dir == .rightToLeft ? .bottomTrailing : .bottomLeading)
                    .offset(x: (dir == .rightToLeft ? -1 : 1) * (4 * (front ? tear : 0) + 58 * (front ? fall : 0)),
                            y: 2 * (front ? tear : 0) + 210 * (front ? fall * fall : 0))
                    .opacity(1 - min(max((front ? fall : 0) * 1.4 - 0.4, 0), 1))
                    .onTapGesture { if front { tearStub() } }
                    .accessibilityAddTraits(front ? .isButton : [])
                    .accessibilityLabel(front ? S.ticketTearStub : "")
            }
            .frame(width: PassSize.stub)
        }
        .frame(minHeight: 230)
        .fixedSize(horizontal: false, vertical: true)
    }

    @ViewBuilder
    private func stub(isRet: Bool, leg: Leg, p: PassInk) -> some View {
        if !isRet {
            switch trip.stage(now) {
            case .before(let days):
                PassLabel(text: S.appPassMore, p: p)
                big(String(days), p)
                PassLabel(text: days == 0 ? S.ticketStubDeparting : (p.dark ? S.appPassNights(count: days) : S.appPassDays(count: days)), p: p, lines: 2)
            // during the trip: "ski day 2 of 4" (A-43); after the return the stub stays, with no count
            case .skiDay(let n, let of):
                PassLabel(text: S.ticketStubSkiDay, p: p)
                big(String(n), p)
                PassLabel(text: S.ticketStubOf(m: String(of)), p: p, lines: 2)
            case .over:
                EmptyView()
            }
        } else {
            let n = trip.skiDayCount()
            PassLabel(text: S.appPassFirst, p: p)
            big(n > 0 ? String(n) : "—", p)
            PassLabel(text: S.appPassSkiDays(count: n > 0 ? n : 5), p: p, lines: 2)
        }
        if let f = leg.fromCode, let t = leg.toCode {
            Text("\(f) › \(t)").font(.custom("Karantina-Bold", fixedSize: 20)).foregroundStyle(p.ink)
                .environment(\.layoutDirection, .leftToRight).padding(.top, 4)
        }
        Spacer(minLength: 0)
        Ridge(color: p.ink)
    }

    private func big(_ t: String, _ p: PassInk) -> some View {
        Text(t).font(.custom("Karantina-Bold", fixedSize: 60)).foregroundStyle(p.acc)
    }

    private func grid<C: View>(_ p: PassInk, @ViewBuilder _ content: () -> C) -> some View {
        HStack(alignment: .top, spacing: 8) { content() }
            .padding(.top, 8)
            .overlay(alignment: .top) { Rectangle().fill(p.rule).frame(height: 1) }
    }

    private func cell<C: View>(_ label: String, _ p: PassInk, @ViewBuilder _ content: () -> C) -> some View {
        VStack(alignment: .leading, spacing: 2) { PassLabel(text: label, p: p); content() }
            .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func value(_ t: String, _ p: PassInk, add: Bool = false) -> some View {
        Text(t).font(ski.type.text(14, .bold)).foregroundStyle(add ? p.acc : p.ink).lineLimit(2)
            .shadow(color: p.dark ? Color(rgb: 0xFFC85A, opacity: 0.65) : .clear, radius: p.dark ? 7 : 0)
    }
}

/// The "barcode": the ridge above New Gudauri drawn as bars, the mountain, not a code anyone could scan (as on the site).
struct Ridge: View {
    let color: Color
    private static let h: [CGFloat] = [0.30, 0.42, 0.55, 0.48, 0.62, 0.80, 0.70, 0.58, 0.66, 0.92, 1, 0.86, 0.74, 0.60, 0.68, 0.78,
                                       0.64, 0.50, 0.44, 0.56, 0.70, 0.62, 0.48, 0.36, 0.42, 0.30, 0.24, 0.34, 0.28, 0.20, 0.26, 0.18]
    var body: some View {
        Canvas { ctx, size in
            let w = size.width / CGFloat(Self.h.count)
            for (i, v) in Self.h.enumerated() {
                let bw: CGFloat = (i % 3 == 0 ? 2 : i % 2 == 1 ? 1.2 : 0.7) * size.width / 80
                ctx.fill(Path(CGRect(x: CGFloat(i) * w, y: size.height * (1 - v), width: bw, height: size.height * v)), with: .color(color))
            }
        }
        .frame(width: 80, height: 28)
        .environment(\.layoutDirection, .leftToRight)
        .accessibilityHidden(true)
    }
}


private extension View {
    /// A field of the front pass that opens its editor (round 20, "the card is the form").
    @ViewBuilder func editing(_ on: Bool, _ open: @escaping () -> Void) -> some View {
        if on { contentShape(Rectangle()).onTapGesture { Fx.shared.tick(); open() }.accessibilityAddTraits(.isButton) } else { self }
    }
}
