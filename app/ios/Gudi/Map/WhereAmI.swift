import SwiftUI
import CoreLocation
import GudiData
import GudiDomain

/// "Where am I" on the map (round 19, decision 58; docs/ARCHITECTURE.md m-7), as Android's map/WhereAmI.kt, through
/// the system's CLLocationManager (no library; the GPS works without signal).
///
/// - **Nothing leaves the phone and nothing is kept.** No reading is sent, measured, logged or written; the last one
///   lives in memory while the button is on, and `off` drops it with what `Locator` remembered.
/// - Only while the button is on **and** the map is on screen; the button starts off on every opening, and going to
///   the background or leaving the map turns it off (m-7 section 5).
/// - "Approximate" (iOS's precise location off): a big circle and no snapping.
@MainActor
@Observable
final class WhereAmI: NSObject, CLLocationManagerDelegate {
    enum State: Equatable {
        case off, waiting, denied, unavailable
        case fixed(Locator.Fix, x: Double, y: Double, accuracy: Double, alt: Int, bearing: Double?)
    }

    private(set) var state: State = .off
    var on: Bool { if case .off = state { return false }; if case .denied = state { return false }; if case .unavailable = state { return false }; return true }
    /// The sheet that explains before the system asks (the r19-sheet).
    var asking = false

    @ObservationIgnored private var locator: Locator
    @ObservationIgnored private let terrain: Terrain
    @ObservationIgnored private let lm = CLLocationManager()

    init(runs: RunsAndLifts, terrain: Terrain) {
        locator = Locator(runs: runs, terrain: terrain)
        self.terrain = terrain
        super.init()
        lm.delegate = self
        lm.desiredAccuracy = kCLLocationAccuracyBest
        lm.distanceFilter = 5
        lm.activityType = .fitness
    }

    /// The button: off when on; on when allowed; the explaining sheet first when the system has not asked yet.
    func toggle() {
        if on { off(); return }
        switch lm.authorizationStatus {
        case .notDetermined: asking = true
        case .denied, .restricted: state = .denied; send("denied")
        default: start()
        }
    }

    /// location_toggle (docs/GROWTH.md, m-7): on, with the first result, once per turning on; off when it was on.
    /// No run, no coordinates, nothing about the place.
    @ObservationIgnored private var pending = false
    private func send(_ result: String) {
        pending = false
        Telemetry.shared.event("location_toggle", ["on": true, "result": result])
    }

    /// After the explaining sheet: the system's question; the answer comes to the delegate.
    func ask() { asking = false; state = .waiting; lm.requestWhenInUseAuthorization() }

    private func start() {
        guard CLLocationManager.locationServicesEnabled() else { state = .unavailable; send("unavailable"); return }
        pending = true
        locator.reset()
        state = .waiting
        lm.startUpdatingLocation()
        lm.startUpdatingHeading()
    }

    func off() {
        let was = on // only a button that was really on says so (not after a refusal or no location)
        if was { Telemetry.shared.event("location_toggle", ["on": false]) }
        pending = false
        lm.stopUpdatingLocation(); lm.stopUpdatingHeading()
        locator.reset()
        state = .off
    }

    nonisolated func locationManagerDidChangeAuthorization(_ m: CLLocationManager) {
        let s = m.authorizationStatus
        Task { @MainActor in
            guard case .waiting = state else { return }
            switch s {
            case .authorizedWhenInUse, .authorizedAlways: start()
            case .denied, .restricted: state = .denied; send("denied")
            default: break
            }
        }
    }

    nonisolated func locationManager(_ m: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let l = locations.last else { return }
        let approx = m.accuracyAuthorization == .reducedAccuracy
        let lat = l.coordinate.latitude, lon = l.coordinate.longitude
        let acc = l.horizontalAccuracy >= 0 ? l.horizontalAccuracy : 100
        let bearing = l.course >= 0 && l.speed > 1 ? l.course : nil
        Task { @MainActor in
            guard self.on else { return }
            let x = Geo.x(lon), y = Geo.y(lat)
            let fix = locator.feed(x: x, y: y, accuracy: acc, approximate: approx)
            let alt = Int((terrain.elev(x, y) / 10).rounded()) * 10
            state = .fixed(fix, x: x, y: y, accuracy: acc, alt: alt, bearing: bearing)
            if pending { send(fix == .outside ? "outside" : approx ? "approximate" : "granted") }
        }
    }

    nonisolated func locationManager(_ m: CLLocationManager, didFailWithError error: Error) {
        let denied = (error as? CLError)?.code == .denied
        Task { @MainActor in if denied { self.state = .denied; self.lm.stopUpdatingLocation(); if self.pending { self.send("denied") } } }
    }
}

/// The accuracy as the canvas writes it: "about 12 m", "about 400 m".
func roundAcc(_ a: Double) -> Int { a < 20 ? max(1, Int(a)) : a < 100 ? Int(a / 5) * 5 : Int(a / 50) * 50 }

/// What the line at the bottom says for a state: its title, its second line, and its action if it has one.
struct WhereWords { let title: String; let sub: String; let dotOff: Bool; var action: String? = nil }

@MainActor func whereWords(_ s: WhereAmI.State, runs: RunsAndLifts) -> WhereWords? {
    switch s {
    case .off: return nil
    case .waiting: return WhereWords(title: S.locWaitingTitle, sub: S.locWaitingSub, dotOff: true)
    case .denied: return WhereWords(title: S.locDeniedTitle, sub: S.locDeniedSubPhone, dotOff: true, action: S.locDeniedActionPhone)
    case .unavailable: return WhereWords(title: S.locUnavailTitle, sub: S.locUnavailSubPhone, dotOff: true, action: S.locUnavailActionPhone)
    case let .fixed(fix, _, _, accuracy, alt, _):
        let acc = Fmt.n(roundAcc(accuracy))
        let where_ = Fmt.metres(alt) + " · " + S.locAccuracy(n: acc)
        switch fix {
        case .outside: return WhereWords(title: S.locOutTitle, sub: S.locOutSub, dotOff: true)
        case .approx: return WhereWords(title: S.locApproxTitle(n: acc), sub: S.locApproxSub, dotOff: false, action: S.locApproxAction)
        case .low: return WhereWords(title: S.locLowTitle(n: acc), sub: S.locLowSub, dotOff: false)
        case .free: return WhereWords(title: S.locFree, sub: where_, dotOff: false)
        case .onRun(let key):
            let color = runs.pistes.first { $0.key == key }?.color ?? "black"
            let name = color == "green" ? S.commonColorGreen : color == "blue" ? S.commonColorBlue : color == "red" ? S.commonColorRed : S.commonColorBlack
            return WhereWords(title: S.locOnRun(run: "\u{2068}\(key)\u{2069}", color: name), sub: where_, dotOff: false)
        case .onLift(let name):
            return WhereWords(title: S.locOnLift(lift: "\u{2068}\(name)\u{2069}"), sub: S.locGoingUp + " · " + S.locAccuracy(n: acc), dotOff: false)
        }
    }
}

/// "Where am I": off on paper, lit in the glacier blue.
struct LocateKey: View {
    let on: Bool
    let action: () -> Void
    @Environment(\.ski) private var ski
    var body: some View {
        Button(action: action) {
            Image(systemName: on ? "location.fill" : "location").font(.system(size: 20, weight: .semibold))
                .foregroundStyle(on ? ski.colors.paper : ski.colors.ink)
                .frame(width: 52, height: 52).background(on ? ski.colors.glacier : ski.colors.paper)
                .overlay(Rectangle().stroke(on ? ski.colors.glacier : ski.colors.rule, lineWidth: 1.5))
        }
        .buttonStyle(.plain).accessibilityLabel(S.locButton).accessibilityValue(on ? S.appOn : S.appOff)
    }
}

/// The line at the bottom of the map (.r19-where): a dot (grey when there is no place), the words, and an action.
struct WhereLine: View {
    let w: WhereWords
    let onAction: () -> Void
    @Environment(\.ski) private var ski
    var body: some View {
        HStack(spacing: 10) {
            let dot = w.dotOff ? ski.colors.dash : ski.colors.glacier
            Circle().fill(dot).frame(width: 15, height: 15).overlay(Circle().fill(ski.colors.paper).padding(1.5)).overlay(Circle().fill(dot).padding(3))
            VStack(alignment: .leading, spacing: 0) {
                Text(w.title).font(ski.type.text(14.5, .bold)).foregroundStyle(ski.colors.ink)
                Text(w.sub).font(ski.type.text(12.5)).foregroundStyle(ski.colors.muted)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            if let a = w.action {
                Button(action: onAction) {
                    Text(a).font(ski.type.text(13, .bold)).foregroundStyle(ski.colors.glacier).padding(.horizontal, 10).frame(minHeight: 44)
                        .background(ski.colors.paper).overlay(Rectangle().stroke(ski.colors.rule, lineWidth: 1.5))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 12).padding(.vertical, 8).frame(minHeight: 48)
        .background(ski.colors.paper.opacity(0.96)).overlay(Rectangle().stroke(ski.colors.ink, lineWidth: 1.5))
        .shadow(color: .black.opacity(0.15), radius: 6, y: 2)
        .accessibilityElement(children: .combine)
    }
}

/// The dot on the map from above: the accuracy circle, the heading's arrow when moving, and the dot.
struct MeDot: View {
    let x: CGFloat, y: CGFloat
    let radius: CGFloat
    let bearing: Double?
    @Environment(\.ski) private var ski
    var body: some View {
        Canvas { c, _ in
            let p = CGPoint(x: x, y: y), r = max(14, radius)
            let circle = Path(ellipseIn: CGRect(x: p.x - r, y: p.y - r, width: 2 * r, height: 2 * r))
            c.fill(circle, with: .color(ski.colors.glacier.opacity(0.16)))
            c.stroke(circle, with: .color(ski.colors.glacier.opacity(0.6)), lineWidth: 1.5)
            if let b = bearing {
                let a = b * .pi / 180, ux = sin(a), uy = -cos(a)
                var t = Path()
                t.move(to: CGPoint(x: p.x + ux * 23, y: p.y + uy * 23))
                t.addLine(to: CGPoint(x: p.x + ux * 12 - uy * 7, y: p.y + uy * 12 + ux * 7))
                t.addLine(to: CGPoint(x: p.x + ux * 12 + uy * 7, y: p.y + uy * 12 - ux * 7)); t.closeSubpath()
                c.fill(t, with: .color(ski.colors.glacier))
            }
            func dot(_ r: CGFloat, _ col: Color, dy: CGFloat = 0) { c.fill(Path(ellipseIn: CGRect(x: p.x - r, y: p.y - r + dy, width: 2 * r, height: 2 * r)), with: .color(col)) }
            dot(10.5, .black.opacity(0.24), dy: 1); dot(9, ski.colors.paper); dot(6, ski.colors.glacier)
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

/// Before the system asks (the r19-sheet): the location stays on the phone, only while the map is open, and offline.
struct AskSheet: View {
    let onGo: () -> Void
    let onLater: () -> Void
    @Environment(\.ski) private var ski
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Rectangle().fill(ski.colors.glacier).frame(height: 6)
            Text(S.locAskTitle).font(ski.type.display(34)).foregroundStyle(ski.colors.ink).padding(.vertical, 12)
            bullet("iphone", Text(S.locAskStaysB).bold() + Text(" " + S.locAskStays))
            bullet("map", Text(S.locAskMap))
            bullet("mappin.and.ellipse", Text(S.locAskOffline))
            HStack(spacing: 10) {
                Button(action: onGo) {
                    Text(S.locAskGo).font(ski.type.text(15, .bold)).foregroundStyle(ski.colors.p.dark ? ski.colors.ink : .white)
                        .frame(maxWidth: .infinity, minHeight: 48).background(ski.colors.accent)
                }
                Button(action: onLater) {
                    Text(S.locAskLater).font(ski.type.text(15, .bold)).foregroundStyle(ski.colors.ink)
                        .frame(maxWidth: .infinity, minHeight: 48).background(ski.colors.paper).overlay(Rectangle().stroke(ski.colors.ink, lineWidth: 1.5))
                }
            }
            .buttonStyle(.plain).padding(.top, 4).padding(.bottom, 12)
            Text(S.locAskAfterPhone).font(ski.type.text(12.5)).foregroundStyle(ski.colors.muted).fixedSize(horizontal: false, vertical: true)
        }
        .padding(.horizontal, 16).padding(.bottom, 20)
    }

    private func bullet(_ icon: String, _ text: Text) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: icon).font(.system(size: 18)).foregroundStyle(ski.colors.glacier).frame(width: 22).padding(.top, 2).accessibilityHidden(true)
            text.font(ski.type.text(14.5)).foregroundStyle(ski.colors.ink).fixedSize(horizontal: false, vertical: true)
        }
        .padding(.bottom, 10)
    }
}
