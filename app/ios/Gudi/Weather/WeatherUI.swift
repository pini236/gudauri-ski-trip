import SwiftUI
import GudiData
import GudiDomain

/// The weather screens of round 19 (Android's weather/WeatherUi.kt): the rows, the map's list, the run's conditions,
/// "today on the mountain" and the days ahead on home, the map's points and button.

private let sourceName = "Open-Meteo.com"
private let sourceURL = URL(string: "https://open-meteo.com/")!

/// "−4°", with the true minus, kept left to right; a value the answer left null is a dash.
func deg(_ t: Int?) -> String { t.map { "\u{2066}" + ($0 < 0 ? "−" : "") + "\(abs($0))°\u{2069}" } ?? "—" }

/// The wind in words: with the gusts, without them when they are missing, "no data" when the wind is.
func windWords(_ wind: Int?, _ gust: Int?, short: Bool = false) -> String {
    guard let wind else { return S.weatherNone }
    if short || gust == nil { return S.weatherWindShort(wind: Fmt.n(wind)) }
    return S.weatherWindGusts(wind: Fmt.n(wind), gusts: Fmt.n(gust!))
}

func pointName(_ id: String) -> String { id == "village" ? S.weatherPointVillage : id == "goodaura" ? S.weatherPointGoodaura : "Sadzele" }

/// "Updated 20 min ago" words: the lift status' (status.ago_*).
func ago(_ updated: Int64, _ now: Int64) -> String {
    let m = max(0, Int((now - updated) / 60_000))
    if m < 1 { return S.statusAgoNow }
    if m < 60 { return S.statusAgoMinutes(n: String(m)) }
    return S.statusAgoHours(n: String(Int((Double(m) / 60).rounded())))
}

func windFrom(_ dir: Int) -> String {
    switch ((Int((Double(dir) / 45).rounded()) % 8) + 8) % 8 {
    case 0: S.weatherWindFromN; case 1: S.weatherWindFromNe; case 2: S.weatherWindFromE; case 3: S.weatherWindFromSe
    case 4: S.weatherWindFromS; case 5: S.weatherWindFromSw; case 6: S.weatherWindFromW; default: S.weatherWindFromNw
    }
}

/// The points' heights as the canvas draws them, when there is no answer to say them.
let defaultAlt = ["village": 2170, "goodaura": 2710, "sadzele": 3240]
/// The three points where the answer has none (the lift ends in the site's data, as the server's).
let defaultLL: [String: (lat: Double, lon: Double)] = ["village": (42.471394, 44.49246), "goodaura": (42.492379, 44.494273), "sadzele": (42.508985, 44.503209)]

/// The wind's arrow: it points where the wind blows to (from + 180), north up; never alone, the words go beside it.
struct WindArrow: View {
    let dir: Int
    var size: CGFloat = 13
    @Environment(\.ski) private var ski
    var body: some View {
        Canvas { c, z in
            let k = z.width / 16
            var p = Path()
            p.move(to: CGPoint(x: 8 * k, y: 1.5 * k)); p.addLine(to: CGPoint(x: 8 * k, y: 13.5 * k))
            p.move(to: CGPoint(x: 3.5 * k, y: 6 * k)); p.addLine(to: CGPoint(x: 8 * k, y: 1.5 * k)); p.addLine(to: CGPoint(x: 12.5 * k, y: 6 * k))
            c.stroke(p, with: .color(ski.colors.ink), style: StrokeStyle(lineWidth: 2 * k, lineCap: .round, lineJoin: .round))
        }
        .frame(width: size, height: size)
        .rotationEffect(.degrees(Double(dir + 180)))
        .accessibilityLabel(windFrom(dir))
    }
}

/// "Weather data by Open-Meteo.com", with the link (CC BY 4.0, m-6 section 6).
struct Credit: View {
    @Environment(\.ski) private var ski
    @Environment(\.openURL) private var openURL
    var body: some View {
        let parts = S.weatherCredit(source: "\u{1}").components(separatedBy: "\u{1}")
        Button { openURL(sourceURL) } label: {
            (Text(parts[0]) + Text(sourceName).foregroundColor(ski.colors.glacier).underline() + Text(parts.count > 1 ? parts[1] : ""))
                .font(ski.type.text(12.5)).foregroundStyle(ski.colors.muted).frame(minHeight: 44, alignment: .leading)
        }
        .buttonStyle(.plain)
    }
}

/// One point (or a run's top or bottom): its name and height, the temperature big, the wind and the new snow.
struct WeatherRow: View {
    let name: String
    let alt: Int
    let v: Forecast.Now?
    var action: (() -> Void)? = nil
    @Environment(\.ski) private var ski

    var body: some View {
        let row = HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 0) {
                Text("\u{2068}\(name)\u{2069}").font(ski.type.text(15, .bold)).foregroundStyle(ski.colors.ink)
                Text(Fmt.metres(alt)).font(ski.type.text(12.5)).foregroundStyle(ski.colors.muted)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Text(deg(v?.temp)).font(ski.type.display(32)).foregroundStyle(v?.temp == nil ? ski.colors.muted : ski.colors.ink)
            VStack(alignment: .trailing, spacing: 0) {
                if let v {
                    HStack(spacing: 4) {
                        if v.wind != nil, let d = v.dir { WindArrow(dir: d) }
                        Text(windWords(v.wind, v.gust))
                    }
                    if let s = v.snow24 { Text(S.weatherNewSnow(n: Fmt.n(s))) }
                } else { Text(S.weatherNone) }
            }
            .font(ski.type.text(13)).foregroundStyle(ski.colors.ink)
        }
        .padding(.vertical, 6).frame(minHeight: 56)
        .overlay(alignment: .bottom) { Rectangle().fill(ski.colors.rule).frame(height: 1) }
        .contentShape(Rectangle())
        if let action { Button(action: action) { row }.buttonStyle(.plain) } else { row.accessibilityElement(children: .combine) }
    }
}

/// The list under the map's weather button: the three points, top down; a tap brings the map to one.
struct WeatherList: View {
    let w: WeatherNow
    let onPoint: (String) -> Void
    @Environment(\.ski) private var ski

    var body: some View {
        let f = w.usable
        VStack(alignment: .leading, spacing: 0) {
            Text(S.weatherTitle).font(ski.type.display(30)).foregroundStyle(ski.colors.ink).accessibilityAddTraits(.isHeader)
            let sub: String = switch w.state {
            case .fresh: S.weatherSubFresh(ago: ago(f!.updated, w.now))
            case .stale: S.weatherStale + " · " + S.weatherSubStale(ago: ago(f!.updated, w.now))
            case .none: S.weatherSubNone
            }
            Text(sub).font(ski.type.text(13)).foregroundStyle(w.state == .stale ? ski.colors.ink : ski.colors.muted).padding(.top, 2).padding(.bottom, 8)
            ForEach(Forecast.pointIds, id: \.self) { id in
                let p = w.forecast?.point(id)
                WeatherRow(name: pointName(id), alt: p?.elevation ?? defaultAlt[id]!, v: f?.now(id, at: w.local), action: p == nil ? nil : { onPoint(id) })
            }
            Text(f == nil ? S.weatherNoteNone : S.weatherNote).font(ski.type.text(12.5)).foregroundStyle(ski.colors.muted).padding(.top, 8)
                .fixedSize(horizontal: false, vertical: true)
            Credit()
        }
    }
}

/// The run panel: the forecast at the run's top and bottom, after the details.
struct RunConditions: View {
    let top: Int, bottom: Int
    let w: WeatherNow
    /// The run's key: weather_view goes once for each run shown.
    var run = ""
    @Environment(\.ski) private var ski

    var body: some View {
        let f = w.usable
        VStack(alignment: .leading, spacing: 0) {
            Text(S.runCondTitle).font(ski.type.display(28)).foregroundStyle(ski.colors.ink).padding(.top, 14).padding(.bottom, 2)
            if w.state == .stale { Text(S.weatherStale).font(ski.type.text(13)).foregroundStyle(ski.colors.ink) }
            WeatherRow(name: S.runCondTop, alt: top, v: f?.at(alt: Double(top), at: w.local))
            WeatherRow(name: S.runCondBottom, alt: bottom, v: f?.at(alt: Double(bottom), at: w.local))
            Text(f.map { S.runCondNote(ago: ago($0.updated, w.now)) } ?? S.weatherSubNone).font(ski.type.text(12.5)).foregroundStyle(ski.colors.muted).padding(.top, 8)
            Credit()
        }
        // weather_view once per run shown (as Android's LaunchedEffect(run))
        .task(id: run) { Telemetry.shared.event("weather_view", ["where": "run", "state": w.state.rawValue]) }
    }
}

/// The card's frame: the season board's (white, a rule on top, a soft shadow).
private struct Card<C: View>: View {
    @ViewBuilder let content: C
    @Environment(\.ski) private var ski
    var body: some View {
        VStack(alignment: .leading, spacing: 0) { content }
            .padding(.horizontal, 14).padding(.top, 20).padding(.bottom, 6)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(ski.colors.paper)
            .overlay(alignment: .top) { Rectangle().fill(ski.colors.ink).frame(height: 6) }
            .shadow(color: Color(rgb: 0x13233A, opacity: 0.1), radius: 6, y: 2)
    }
}

private struct Head: View {
    let title: String
    let w: WeatherNow
    @Environment(\.ski) private var ski
    var body: some View {
        HStack(alignment: .bottom, spacing: 10) {
            Text(title).font(ski.type.display(30)).foregroundStyle(ski.colors.ink).frame(maxWidth: .infinity, alignment: .leading)
            Text(w.state == .fresh ? S.todayUpdated(ago: ago(w.forecast!.updated, w.now)) : w.state == .stale ? S.weatherStale : S.weatherNone)
                .font(ski.type.text(12.5, .bold)).foregroundStyle(w.state == .stale ? ski.colors.ink : ski.colors.muted)
        }
    }
}

/// A day as the site writes it on a chip: "ו׳ 12.1" in Hebrew, "Tue 12 Jan" elsewhere.
func dayLabel(_ d: Day) -> String {
    var c = Calendar(identifier: .gregorian); c.timeZone = TimeZone(secondsFromGMT: 0)!
    let date = c.date(from: DateComponents(year: d.year, month: d.month, day: d.day, hour: 12))!
    let lang = Strings.shared.lang
    let f = DateFormatter(); f.calendar = c; f.timeZone = c.timeZone; f.locale = Locale(identifier: lang.rawValue)
    if lang == .he { f.dateFormat = "EEEEE"; return f.string(from: date) + " \(d.day).\(d.month)" }
    f.dateFormat = "EEE d MMM"; return f.string(from: date)
}

private func dayFromISO(_ s: String) -> Day? {
    let p = s.split(separator: "-").compactMap { Int($0) }
    return p.count == 3 ? Day(p[0], p[1], p[2]) : nil
}

/// "Today on the mountain", during the trip: the day of the trip, the three heights now, the wind closure estimate for
/// Sadzele and Kudebi (a live MTA report on a lift replaces it), and the lift status inside.
struct TodayBoard: View {
    let day: Int, of: Int
    let w: WeatherNow
    let status: LiftStatus
    let onStatus: () -> Void
    @Environment(\.ski) private var ski

    private func levelWord(_ l: String) -> String { l == "low" ? S.todayRiskLow : l == "medium" ? S.todayRiskMedium : S.todayRiskHigh }

    var body: some View {
        let f = w.usable, c = ski.colors
        Card {
            Head(title: S.todayTitle, w: w)
            let today = dayFromISO(w.today)
            Text(S.tripSkiDayOf(n: String(day), m: String(of)) + (today.map { " · " + dayLabel($0) } ?? ""))
                .font(ski.type.text(13)).foregroundStyle(c.muted).padding(.top, 2).padding(.bottom, 8)
            Rectangle().fill(c.rule).frame(height: 1)
            HStack(spacing: 0) {
                ForEach(Array(["village", "goodaura", "sadzele"].enumerated()), id: \.offset) { i, id in
                    let v = f?.now(id, at: w.local)
                    VStack(alignment: .leading, spacing: 1) {
                        Text("\u{2068}\(pointName(id))\u{2069}").font(ski.type.text(11.5)).foregroundStyle(c.muted)
                        Text(Fmt.metres(f?.point(id)?.elevation ?? defaultAlt[id]!)).font(ski.type.text(11.5)).foregroundStyle(c.muted)
                        Text(deg(v?.temp)).font(ski.type.display(32)).foregroundStyle(v?.temp == nil ? c.muted : c.ink).padding(.top, 2)
                        if let v {
                            HStack(spacing: 3) {
                                if v.wind != nil, let d = v.dir { WindArrow(dir: d, size: 12) }
                                Text(windWords(v.wind, nil, short: true))
                            }
                            if let s = v.snow24 { Text(S.weatherSnowShort(n: Fmt.n(s))) }
                        } else { Text(S.weatherNone) }
                    }
                    .font(ski.type.text(12)).foregroundStyle(c.ink)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.leading, i == 0 ? 0 : 8).padding(.trailing, 8).padding(.vertical, 8)
                    .overlay(alignment: .leading) { if i > 0 { Rectangle().fill(c.rule).frame(width: 1) } }
                }
            }
            Rectangle().fill(c.rule).frame(height: 1)
            // the wind closure estimate: the server's, per lift; a live report on a lift is said instead
            VStack(alignment: .leading, spacing: 6) {
                Text(S.todayRiskTitle).font(ski.type.text(13, .bold)).foregroundStyle(c.muted)
                let live: [String: Bool?] = Dictionary(uniqueKeysWithValues: Forecast.riskLifts.map { ($0, status.isOpen($0)) })
                ForEach(Forecast.riskLifts, id: \.self) { lift in
                    let open = live[lift] ?? nil
                    if open != nil || f == nil || f!.hasRisk(lift) {
                        let level = f?.risk(lift, date: w.today)
                        HStack(spacing: 10) {
                            Text("\u{2066}\(lift)\u{2069}").font(ski.type.text(14, .bold)).foregroundStyle(c.ink).frame(maxWidth: .infinity, alignment: .leading)
                            if let open {
                                Text(open ? S.todayOpenMta : S.todayClosedMta).font(ski.type.text(13.5, .bold)).foregroundStyle(c.ink)
                                Color.clear.frame(width: 54)
                            } else if let level {
                                Text(levelWord(level)).font(ski.type.text(13.5, .bold)).foregroundStyle(c.ink)
                                Meter(level: Forecast.levels.firstIndex(of: level) ?? 0)
                            } else {
                                Text(S.weatherNone).font(ski.type.text(13.5, .bold)).foregroundStyle(c.muted)
                                Color.clear.frame(width: 54)
                            }
                        }
                        .frame(minHeight: 36)
                    }
                }
                let reported = Forecast.riskLifts.filter { (live[$0] ?? nil) != nil }
                Text(reported.isEmpty ? S.todayRiskWhy : reported.count == live.count ? S.todayRiskWhyAll
                     : S.todayRiskWhyMixed(lift: "\u{2066}\(reported[0])\u{2069}", other: "\u{2066}\(Forecast.riskLifts.first { !reported.contains($0) }!)\u{2069}"))
                    .font(ski.type.text(12.5)).foregroundStyle(c.muted).fixedSize(horizontal: false, vertical: true)
            }
            .padding(.top, 10)
            // the lift status inside the board (it replaces the season board)
            Rectangle().fill(c.rule).frame(height: 1).padding(.top, 8)
            Button(action: onStatus) {
                HStack(spacing: 9) {
                    Rectangle().fill(status.fresh ? c.green : c.dash).frame(width: 10, height: 10)
                    Text(status.fresh ? S.statusBarSummary(open: String(status.open), total: String(status.names.count), ago: agoText(status)) : S.todayLiftsNone)
                        .font(ski.type.text(13.5, .bold)).foregroundStyle(c.ink).frame(maxWidth: .infinity, alignment: .leading)
                    Text(S.statusHeadingLiftStatus).font(ski.type.text(12.5, .bold)).foregroundStyle(c.glacier)
                }
                .frame(minHeight: 44).padding(.top, 6).contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            Credit()
        }
        .onAppear { Telemetry.shared.event("weather_view", ["where": "home", "state": w.state.rawValue]) }
    }
}

/// The three steps of the risk meter, ink only (never green and red: those are run colours here).
private struct Meter: View {
    let level: Int
    @Environment(\.ski) private var ski
    var body: some View {
        HStack(spacing: 3) {
            ForEach(0..<3, id: \.self) { i in
                Rectangle().fill(i <= level ? ski.colors.ink : .clear).frame(height: 10).overlay(Rectangle().stroke(ski.colors.ink, lineWidth: 1.5))
            }
        }
        .frame(width: 54)
    }
}

/// Before the trip, once its first ski day is inside the forecast (m-6 section 5): one line per ski day, at the top of
/// the Goodaura gondola; a day still beyond the forecast says when it opens.
struct AheadBoard: View {
    let skiDays: [Day]
    let daysToFlight: Int
    let w: WeatherNow
    @Environment(\.ski) private var ski

    var body: some View {
        let f = w.usable, c = ski.colors
        Card {
            Head(title: S.todayAheadTitle, w: w)
            let count = daysToFlight == 0 ? S.ticketStubDeparting : S.ticketStubDays(count: daysToFlight)
            Text(pointName("goodaura") + ", " + Fmt.metres(f?.point("goodaura")?.elevation ?? defaultAlt["goodaura"]!) + " · " + count)
                .font(ski.type.text(13)).foregroundStyle(c.muted).padding(.top, 2).padding(.bottom, 8)
            let span = max(w.forecast?.dayCount ?? 15, 1)
            ForEach(Array(skiDays.enumerated()), id: \.offset) { i, d in
                let v = f?.day("goodaura", date: d.iso)
                HStack(spacing: 10) {
                    VStack(alignment: .leading, spacing: 0) {
                        Text(dayLabel(d)).font(ski.type.text(14, .bold)).foregroundStyle(c.ink)
                        Text(S.todayAheadDay(n: String(i + 1))).font(ski.type.text(12)).foregroundStyle(c.muted)
                    }
                    .frame(width: 72, alignment: .leading)
                    if let v {
                        HStack(spacing: 4) {
                            if v.wind != nil, let dir = v.dir { WindArrow(dir: dir, size: 12) }
                            Text(windWords(v.wind, v.gust) + (v.snow.map { $0 > 0 ? " · " + S.weatherNewSnow(n: Fmt.n($0)) : " · " + S.weatherNoNewSnow } ?? ""))
                                .font(ski.type.text(13)).foregroundStyle(c.ink)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        Text(deg(v.tmax)).font(ski.type.display(26)).foregroundStyle(v.tmax == nil ? c.muted : c.ink)
                    } else {
                        let opens = d.plus(days: -(span - 1))
                        let later = f != nil && dayFromISO(w.today).map { opens > $0 } == true
                        Text(later ? S.todayAheadLater(date: "\(opens.day).\(opens.month)") : S.weatherNone)
                            .font(ski.type.text(13)).foregroundStyle(c.muted).frame(maxWidth: .infinity, alignment: .leading)
                        Text("—").font(ski.type.display(26)).foregroundStyle(c.muted)
                    }
                }
                .frame(minHeight: 48)
                .overlay(alignment: .bottom) { if i < skiDays.count - 1 { Rectangle().fill(c.rule).frame(height: 1) } }
            }
            Text(S.todayAheadNote).font(ski.type.text(12.5)).foregroundStyle(c.muted).padding(.top, 8).fixedSize(horizontal: false, vertical: true)
            Credit()
        }
        .onAppear { Telemetry.shared.event("weather_view", ["where": "home", "state": w.state.rawValue]) }
    }
}

/// The map's "weather" button: a cloud and the word, lit while the layer is on.
struct WeatherKey: View {
    let on: Bool
    let action: () -> Void
    @Environment(\.ski) private var ski
    var body: some View {
        let fg = on ? ski.colors.paper : ski.colors.ink
        Button(action: action) {
            HStack(spacing: 7) {
                Image(systemName: "cloud").font(.system(size: 16, weight: .semibold))
                Text(S.weatherButton).font(ski.type.text(14, .bold))
            }
            .foregroundStyle(fg).padding(.horizontal, 14).frame(minHeight: 44)
            .background(on ? ski.colors.ink : ski.colors.paper).overlay(Rectangle().stroke(ski.colors.ink, lineWidth: 1.5))
        }
        .buttonStyle(.plain).accessibilityAddTraits(on ? .isSelected : [])
    }
}

/// One point of the map's weather layer, in words, at its place in the site's projection.
struct PinText: Identifiable {
    let id: String
    let x: Double, y: Double
    let title: String, temp: String, wind: String?, snow: String?, dir: Int?
}

func pinTexts(_ w: WeatherNow) -> [PinText] {
    let f = w.usable
    let stale = w.state == .stale ? "\n" + S.weatherStale : ""
    return Forecast.pointIds.map { id in
        let p = w.forecast?.point(id), ll = defaultLL[id]!
        let v = f?.now(id, at: w.local)
        return PinText(id: id, x: Geo.x(ll.lon), y: Geo.y(ll.lat),
                       title: "\u{2068}\(pointName(id))\u{2069} · \(Fmt.metres(p?.elevation ?? defaultAlt[id]!))\(stale)",
                       temp: deg(v?.temp), wind: windWords(v?.wind, nil, short: true), snow: v?.snow24.map { S.weatherNewSnow(n: Fmt.n($0)) },
                       dir: v?.wind != nil ? v?.dir : nil)
    }
}

/// A point's card on the map, hanging above its spot (or below, when it would go under the map's own buttons).
struct PinCard: View {
    let p: PinText
    @Environment(\.ski) private var ski
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(p.title).font(ski.type.text(11.5, .bold)).foregroundStyle(ski.colors.muted)
            HStack(spacing: 8) {
                Text(p.temp).font(ski.type.display(30)).foregroundStyle(p.temp == "—" ? ski.colors.muted : ski.colors.ink)
                VStack(alignment: .leading, spacing: 0) {
                    HStack(spacing: 4) {
                        if let d = p.dir { WindArrow(dir: d, size: 12) }
                        if let wd = p.wind { Text(wd) }
                    }
                    if let s = p.snow { Text(s) }
                }
                .font(ski.type.text(12)).foregroundStyle(ski.colors.ink)
            }
        }
        .padding(.horizontal, 9).padding(.top, 6).padding(.bottom, 7)
        .background(ski.colors.paper).overlay(Rectangle().stroke(ski.colors.ink, lineWidth: 1.5))
        .shadow(color: .black.opacity(0.15), radius: 4, y: 2)
        .accessibilityElement(children: .combine)
    }
}
