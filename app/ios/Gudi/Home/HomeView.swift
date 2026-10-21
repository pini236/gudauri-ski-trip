import SwiftUI
import GudiData
import GudiDomain

/// Where the home page's signs and buttons lead.
enum HomeAction { case map, meet, games, group, about, trip, status, privacy }

/// The home page (round 10, H1 to H4; decision 36), as Android's home/HomeScreen.kt: the sky and the view from the
/// village by the hour in Gudauri, the user's own boarding pass (or an empty one inviting to add it), the lift status
/// when there is no trip, and the post of signs. Left-to-right languages mirror it (LT1 to LT3).
struct HomeView: View {
    let frame: DayNight.Frame
    let mode: DayNight.Mode
    /// The user's own trip, kept only on the phone (decision 27); nil: the empty pass.
    let trip: Trip?
    let now: LocalMoment
    let status: LiftStatus?
    /// The forecast now (round 19): "today on the mountain" during the trip, the ski days ahead before it.
    var weather: WeatherNow? = nil
    let onMode: () -> Void
    /// A language picked on the home's tag, or nil for the phone's (decision 47).
    let onLang: (Lang?) -> Void
    let go: (HomeAction) -> Void
    @Environment(\.ski) private var ski
    @State private var langs = false

    /// During the trip "today on the mountain"; before it, once the first ski day is within 14 days, the days ahead.
    @ViewBuilder private func board(_ trip: Trip, _ w: WeatherNow) -> some View {
        switch trip.stage(now) {
        case .skiDay(let n, let of):
            TodayBoard(day: n, of: of, w: w, status: status ?? LiftStatus(names: [], report: nil, now: 0)) { go(.status) }
                .padding(.horizontal, 16).padding(.top, 30)
        case .before(let days):
            if let ski = trip.skiDays(), ski.lowerBound <= now.day.plus(days: 14) {
                AheadBoard(skiDays: (0...(ski.upperBound.epochDay - ski.lowerBound.epochDay)).map { ski.lowerBound.plus(days: $0) }, daysToFlight: days, w: w)
                    .padding(.horizontal, 16).padding(.top, 30)
            }
        default: EmptyView()
        }
    }

    var body: some View {
        let c = ski.colors
        GeometryReader { g in
            ScrollView {
                ZStack(alignment: .top) {
                    Hero(frame: frame, height: 250 + g.safeAreaInsets.top)
                    VStack(alignment: .leading, spacing: 0) {
                        HomeHead(frame: frame, mode: mode, onMode: onMode, onLang: { langs = true }) { go(.about) }
                        PhaseChip(frame: frame)
                        if let trip {
                            // with a return pass, its top shows above the outbound one, over the mountains
                            Spacer().frame(height: trip.ret != nil ? 0 : 30)
                            TripPass(trip: trip, now: now) { go(.trip) }.padding(.horizontal, 16)
                            if let weather { board(trip, weather) }
                            Spacer().frame(height: 38)
                        } else {
                            Spacer().frame(height: 32)
                            EmptyPass { go(.trip) }.padding(.horizontal, 16)
                            Spacer().frame(height: 40)
                            SeasonBoard(status: status) { go(.status) }.padding(.horizontal, 16)
                            Spacer().frame(height: 30)
                        }
                        // round 20: the signs without a line under them
                        SignPost(signs: [
                            SignSpec(title: S.navMap, sub: nil, color: c.blue, fg: c.onBoard, width: 0.90) { go(.map) },
                            SignSpec(title: S.navMeet, sub: nil, color: SignColors.gold, fg: SignColors.ink, width: 0.82) { go(.meet) },
                            SignSpec(title: S.navGames, sub: nil, color: c.green, fg: c.onBoard, width: 0.86) { go(.games) },
                            SignSpec(title: S.appSignGroup, sub: nil, color: c.ink, fg: c.paper, width: 0.78) { go(.group) },
                        ])
                        // the site's two links at the bottom of home
                        VStack(alignment: .leading, spacing: 0) {
                            HomeLink(text: S.homeAboutLink) { go(.about) }
                            HomeLink(text: S.homePrivacyLink) { go(.privacy) }
                        }
                        .padding(.horizontal, 16).padding(.top, 28).padding(.bottom, 24)
                    }
                    .padding(.top, g.safeAreaInsets.top)
                    .frame(maxWidth: 520)
                }
                .frame(maxWidth: .infinity)
            }
            .ignoresSafeArea(edges: .top)
            .background(c.snow)
        }
        .sheet(isPresented: $langs) {
            LangSheet { l in langs = false; onLang(l) }
                .environment(\.ski, ski)
                .presentationDetents([.medium, .large])
        }
    }
}

/// The head over the sky: the place, the time there, the day-and-night button and the way to about and settings.
struct HomeHead: View {
    let frame: DayNight.Frame
    let mode: DayNight.Mode
    let onMode: () -> Void
    let onLang: () -> Void
    let onAbout: () -> Void
    @Environment(\.ski) private var ski

    var body: some View {
        let c = ski.colors
        // a soft halo keeps the words readable over any sky (the site's --sky-halo)
        let halo = c.dark ? Color(rgb: 0x0D1522, opacity: 0.6) : Color.white.opacity(0.6)
        HStack(spacing: 0) {
            Text(S.homeLocation).font(ski.type.text(15, .semibold)).foregroundStyle(c.ink).shadow(color: halo, radius: 5)
            Spacer()
            VStack(alignment: .trailing, spacing: 0) {
                Text(S.navGudauriTime).font(ski.type.text(11)).foregroundStyle(c.muted).shadow(color: halo, radius: 5)
                Text(frame.clock).font(ski.type.display(22)).foregroundStyle(c.ink).shadow(color: halo, radius: 5)
                    .environment(\.layoutDirection, .leftToRight)
            }
            .padding(.trailing, 6)
            LangTag(action: onLang)
            DayNightButton(mode: mode, action: onMode)
            Button(action: onAbout) {
                Image(systemName: "gearshape").font(.system(size: 20)).foregroundStyle(c.ink).frame(width: 44, height: 44)
            }
            .accessibilityLabel(S.commonAboutSettings)
        }
        .padding(.leading, 16).padding(.trailing, 10).padding(.top, 18)
    }
}

/// The day-and-night button as on the site (.dn-btn, P-K2): a round 44 in paper with an ink ring, and the mode it is
/// in: half a circle for auto, the gold sun for day, the crescent for night.
struct DayNightButton: View {
    let mode: DayNight.Mode
    let action: () -> Void
    @Environment(\.ski) private var ski

    private func name(_ m: DayNight.Mode) -> String {
        switch m { case .auto: S.daynightModeAuto; case .day: S.daynightModeDay; case .night: S.daynightModeNight }
    }

    var body: some View {
        let c = ski.colors
        let (icon, tint): (String, Color) = switch mode {
        case .auto: ("circle.lefthalf.filled", c.ink)
        case .day: ("sun.max.fill", Color(rgb: 0xF4B942))
        case .night: ("moon.fill", c.ink)
        }
        Button(action: action) {
            Image(systemName: icon).font(.system(size: 20)).foregroundStyle(tint)
                .frame(width: 44, height: 44)
                .background(c.paper, in: Circle())
                .overlay(Circle().stroke(c.ink, lineWidth: 1.5))
                .contentTransition(.symbolEffect(.replace))
        }
        .padding(.horizontal, 4)
        .accessibilityLabel(S.daynightButtonAria(mode: name(mode), next: name(mode.next)))
    }
}

/// The part of the day over the sky (the site's .sky-phase): a small chip at the start, under the place.
struct PhaseChip: View {
    let frame: DayNight.Frame
    @Environment(\.ski) private var ski
    var body: some View {
        let c = ski.colors
        let t: String = switch frame.phase {
        case "sunrise": S.daynightPhaseSunrise
        case "golden_hour": S.daynightPhaseGoldenHour
        case "sunset": S.daynightPhaseSunset
        case "twilight": S.daynightPhaseTwilight
        case "night": S.daynightPhaseNight
        default: S.daynightPhaseDay
        }
        Text(t).font(ski.type.text(12.5, .bold)).foregroundStyle(c.ink)
            .padding(.horizontal, 10).padding(.vertical, 3)
            .background(c.dark ? Color(rgb: 0x0D1522, opacity: 0.55) : Color.white.opacity(0.55))
            .padding(.leading, 16).padding(.top, 8)
    }
}

struct HomeLink: View {
    let text: String
    let action: () -> Void
    @Environment(\.ski) private var ski
    var body: some View {
        Button(action: action) {
            Text(text).font(ski.type.text(13.5)).underline().foregroundStyle(ski.colors.muted)
                .padding(.vertical, 12).frame(minHeight: 44)
        }
        .buttonStyle(.plain)
    }
}
