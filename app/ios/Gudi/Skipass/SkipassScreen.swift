import SwiftUI
import GudiDomain

/// The ski-pass screen (round 40, alternative ב, approved 18.11.2026; Android's skipass/SkipassScreen.kt): what the ski days
/// cost on a pass against day tickets, by the trip's ski days or by a number the user sets, for an adult or a child. The
/// prices are the resort's own (MTA, foreign visitors, GEL), each with where they come from and the day they were checked;
/// past `until` it says there is no information, and it never shows an estimate. Nothing is bought here: the tickets are at the
/// resort's ticket offices, and the resort's own pass app is a plain link to its store page.
struct SkipassScreen: View {
    let prices: Prices?
    let tripDays: Int?
    let today: Day
    @Environment(\.ski) private var ski
    @Environment(\.openURL) private var openURL
    @State private var child = false
    @State private var days = 4
    @State private var fromTrip = false
    @State private var started = false

    private static let maxDays = 16

    var body: some View {
        let c = ski.colors
        Screen(title: S.appSkipassTitle) {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    who(c)
                    card(c)
                    if let p = prices { footer(p, c) }
                }
                .padding(.horizontal, 16).padding(.bottom, 24).padding(.top, 8)
                .frame(maxWidth: 520)
                .frame(maxWidth: .infinity)
            }
        }
        .onAppear {
            guard !started else { return }
            started = true
            if let t = tripDays, (1...Self.maxDays).contains(t) { days = t; fromTrip = true }
        }
    }

    private func gel(_ n: Int) -> String { S.appSkipassPrice(n: String(n)) }
    private func gelOne(_ x: Double) -> String { S.appSkipassPrice(n: x == x.rounded() ? String(Int(x)) : String(format: "%.1f", locale: Locale(identifier: "en_US"), x)) }

    /// Adult or child.
    private func who(_ c: SkiColors) -> some View {
        HStack(spacing: 0) {
            ForEach([false, true], id: \.self) { kid in
                let on = child == kid
                let text = kid ? S.appSkipassChild(from: String(prices?.childFrom ?? 6), to: String(prices?.childTo ?? 12)) : S.appSkipassAdult
                Button { child = kid } label: {
                    Text(text).font(ski.type.text(14, .bold)).foregroundStyle(on ? c.paper : c.muted)
                        .frame(maxWidth: .infinity, minHeight: 44).background(on ? c.ink : c.snow)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(text)
                .accessibilityAddTraits(on ? [.isButton, .isSelected] : .isButton)
            }
        }
        .overlay(Rectangle().stroke(c.rule, lineWidth: 1))
    }

    private func card(_ c: SkiColors) -> some View {
        let valid = prices != nil && prices!.valid(today)
        return VStack(alignment: .leading, spacing: 8) {
            Text(fromTrip ? S.appSkipassFromTrip : S.appSkipassDays).font(ski.type.text(13)).foregroundStyle(c.muted)
            // the days, with a big plus and minus
            HStack {
                step("−", S.appSkipassDaysLess, days > 1) { days -= 1; fromTrip = false }
                Spacer()
                VStack(spacing: 0) {
                    Text(String(days)).font(ski.type.display(40)).foregroundStyle(c.ink).environment(\.layoutDirection, .leftToRight)
                    Text(S.appSkipassDays).font(ski.type.text(13)).foregroundStyle(c.muted)
                }
                Spacer()
                step("+", S.appSkipassDaysMore, days < Self.maxDays) { days += 1; fromTrip = false }
            }
            if !valid || prices == nil {
                Text("—").font(ski.type.display(68)).foregroundStyle(c.muted)
                warn(S.appSkipassNoInfo + "\n" + S.appSkipassCheckSite, c)
            } else if let p = prices, let q = p.price(days: days, child: child) {
                answer(p, q, c)
            } else {
                warn(S.appSkipassTooLong(n: String(days)), c)
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(c.paper)
        .overlay(Rectangle().stroke(c.rule, lineWidth: 1))
    }

    @ViewBuilder private func answer(_ p: Prices, _ q: Quote, _ c: SkiColors) -> some View {
        HStack(alignment: .lastTextBaseline) {
            Text(gel(q.total)).font(ski.type.display(68)).foregroundStyle(c.ink).environment(\.layoutDirection, .leftToRight)
            Spacer()
            Text(S.appSkipassPerDay(price: gelOne(Double(q.total) / Double(days)))).font(ski.type.text(13)).foregroundStyle(c.muted)
        }
        if let daily = p.daily(child: child) {
            let all = daily * days
            bar(S.appSkipassDailyX(n: String(days)), gel(all), 1, c.dash, c)
            bar(q.parts.count == 1 ? S.appSkipassPassDays(count: days) : q.parts.map(String.init).joined(separator: " + "), gel(q.total), Double(q.total) / Double(all), c.green, c)
            if all > q.total {
                Text(S.appSkipassSave(amount: gel(all - q.total), pct: String(Int((Double(all - q.total) / Double(all) * 100).rounded()))))
                    .font(ski.type.text(16, .bold)).foregroundStyle(c.green)
            }
        }
        if q.parts.count == 2 {
            Text(S.appSkipassNoPass(n: String(days), combo: S.appSkipassAnd(a: S.appSkipassPart(count: q.parts[0]), b: S.appSkipassPart(count: q.parts[1]))))
                .font(ski.type.text(13)).foregroundStyle(c.muted)
            Text(S.appSkipassTwoTickets).font(ski.type.text(12.5)).foregroundStyle(c.muted)
        }
    }

    /// What the price list says besides the prices, the store link and where it all comes from.
    @ViewBuilder private func footer(_ p: Prices, _ c: SkiColors) -> some View {
        let lines: [String] = [
            p.nightAdult.map { S.appSkipassNight(price: gel($0)) },
            p.freeUnder.map { S.appSkipassChildFree(age: String($0)) },
        ].compactMap { $0 } + p.rules.compactMap { rule($0, p) }
        if !lines.isEmpty {
            Text(lines.joined(separator: "\n")).font(ski.type.text(12.5)).foregroundStyle(c.muted).lineSpacing(3)
        }
        Text(S.appSkipassBuy).font(ski.type.text(15, .bold)).foregroundStyle(c.ink)
        if let link = p.buyIos.flatMap(URL.init(string:)) {
            // the resort's own app, opened as it is: no referrer, no campaign (this app is not what its pass app counts)
            Button { openURL(link) } label: {
                Text(S.appSkipassApp(store: "App Store") + " ↗").font(ski.type.text(14, .bold)).foregroundStyle(c.ink)
                    .frame(maxWidth: .infinity, minHeight: 48).overlay(Rectangle().stroke(c.ink, lineWidth: 1.5))
            }
            .buttonStyle(.plain)
        }
        Text(S.appSkipassChecked(date: shortDateText(p.checked, ski.type.lang), src: p.src)).font(ski.type.text(11.5)).foregroundStyle(c.muted)
    }

    /// The words for one of the price list's rules, by its key; nil when this app has none (an unknown key is skipped, never shown as a key).
    private func rule(_ key: String, _ p: Prices) -> String? {
        switch key {
        case "app.skipass_card": return p.deposit.map { S.appSkipassCard(deposit: gel($0)) }
        case "app.skipass_foreign": return S.appSkipassForeign
        case "app.skipass_named": return S.appSkipassNamed
        default: return nil
        }
    }

    private func step(_ sign: String, _ label: String, _ on: Bool, _ action: @escaping () -> Void) -> some View {
        let c = ski.colors
        return Button(action: action) {
            Text(sign).font(ski.type.text(24, .bold)).foregroundStyle(on ? c.ink : c.rule)
                .frame(width: 48, height: 48).overlay(Rectangle().stroke(on ? c.ink : c.rule, lineWidth: 1.5))
        }
        .buttonStyle(.plain)
        .disabled(!on)
        .accessibilityLabel(label)
    }

    /// A bar for what something costs, as long as it is against the day tickets' (the longest, `share` = 1).
    private func bar(_ label: String, _ value: String, _ share: Double, _ color: Color, _ c: SkiColors) -> some View {
        HStack(spacing: 8) {
            Text(label).font(ski.type.text(13)).foregroundStyle(c.muted).frame(width: 96, alignment: .leading)
            GeometryReader { g in
                Rectangle().fill(color).frame(width: g.size.width * min(1, max(0.05, share)), height: 24)
            }
            .frame(height: 24)
            Text(value).font(ski.type.text(13, .bold)).foregroundStyle(c.ink).environment(\.layoutDirection, .leftToRight)
        }
    }

    private func warn(_ text: String, _ c: SkiColors) -> some View {
        let amber = c.dark ? Color(rgb: 0xFFD27A) : Color(rgb: 0x8A5A00)
        let bg = c.dark ? Color(rgb: 0x3A2E10) : Color(rgb: 0xFFF3D6)
        return Text(text).font(ski.type.text(13)).foregroundStyle(amber)
            .padding(.horizontal, 10).padding(.vertical, 8).frame(maxWidth: .infinity, alignment: .leading)
            .background(bg).overlay(Rectangle().stroke(amber, lineWidth: 1))
    }
}
