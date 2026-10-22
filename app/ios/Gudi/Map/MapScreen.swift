import SwiftUI
import GudiData
import GudiDomain

/// The trail map (13.3), Android's map/MapScreen.kt and the site's #map: the mountain, and over it the map's panel (a
/// chosen run, a lift, or the list of all runs with the filters), the lift status bar (S1) and its board (S2, S3), and
/// the controls. iPhone style: the panel is a sheet that rests low over the map and pulls up, the map stays live
/// behind it, and back is the system's.
struct MapScreen: View {
    let run: String?
    @Environment(AppModel.self) private var app
    @Environment(\.ski) private var ski
    @State private var model: MapModel?

    var body: some View {
        Group {
            if let model { MapContent(model: model, app: app) }
            else { Text(S.appMapLoading).font(ski.type.text(16)).foregroundStyle(ski.colors.ink).frame(maxWidth: .infinity, maxHeight: .infinity) }
        }
        .background(ski.colors.snow)
        .onAppear {
            guard model == nil, let runs = app.runs else { return }
            let m = MapModel(runs: runs, terrainURL: app.terrainURL)
            if let run { m.select(run) }
            // "see on the trail map" from the meeting point: frame and open that lift, once
            if let name = app.mapLift, let l = runs.lifts.first(where: { $0.name == name }) { m.showLift(l) }
            app.mapLift = nil
            model = m
        }
    }
}

/// What the sheet over the map shows.
private enum Panel: String, Identifiable {
    case run, lift, list, status, weather, ask
    var id: String { rawValue }
}

private struct MapContent: View {
    @Bindable var model: MapModel
    @Bindable var app: AppModel
    @Environment(\.ski) private var ski
    @Environment(\.accessibilityReduceMotion) private var still
    @State private var cam = MapCamera()
    @State private var fitted = false
    @State private var forMe = false
    @State private var changes: [(String, Bool)] = []
    @State private var detent: PresentationDetent = .height(250)
    // the 3D map (Map3D/): its controls, whether it flies down a run, and the light of the app's day and night
    @State private var map3d: Map3DController?
    @State private var flying = false
    @State private var now = Date()
    @AppStorage("dayNight") private var dayNightRaw = DayNight.Mode.auto.rawValue

    /// 3D when chosen and this phone has Metal; else the map from above, without the switch (as the site without three.js).
    private var threeD: Bool { model.mode == "3d" && Map3DView.available }
    private var lightTime: Date {
        let ms = Int64(now.timeIntervalSince1970 * 1000)
        return Date(timeIntervalSince1970: Double(DayNight.lightTime(ms, DayNight.Mode(rawValue: dayNightRaw) ?? .auto)) / 1000)
    }
    /// The weather layer (round 19): off until tapped; on, the three points on the map and the list.
    @State private var weatherOn = false
    @State private var weatherSheet = false
    /// "Where am I" (round 19): made once the elevation model is read; off on every opening.
    @State private var where_: WhereAmI?
    @State private var looked = false
    @Environment(\.scenePhase) private var phase
    @Environment(\.openURL) private var openURL

    private var panel: Binding<Panel?> {
        Binding {
            if app.statusSheet { return .status }
            if weatherSheet { return .weather }
            if where_?.asking == true { return .ask }
            if model.selected != nil { return .run }
            if model.lift != nil { return .lift }
            if model.list { return .list }
            return nil
        } set: { v in
            guard v == nil else { return }
            app.statusSheet = false; weatherSheet = false; where_?.asking = false
            model.selected = nil; model.lift = nil; model.list = false
        }
    }

    /// The share of the screen's height the sheet covers, for framing above it.
    private var covered: Double {
        guard panel.wrappedValue != nil else { return 0 }
        return detent == .large ? 0.85 : detent == .medium ? 0.5 : 0.36
    }

    var body: some View {
        GeometryReader { g in
            ZStack(alignment: .top) {
                if threeD {
                    Map3DView(model: model, status: app.status, forMe: forMe, lightTime: lightTime, bottom: covered,
                              controller: $map3d, onRun: { model.select($0); Fx.shared.tick() },
                              onLift: { model.showLift($0); Fx.shared.tick() }, onFlying: { flying = $0 },
                              overlay: { p in AnyView(over3D(p, g.size)) })
                        .ignoresSafeArea(edges: .bottom)
                } else {
                    OverviewMap(model: model, cam: $cam, status: app.status, forMe: forMe) { frameKobi() }
                        .ignoresSafeArea(edges: .bottom)
                    if weatherOn { pins(g.size) { CGPoint(x: cam.sx($0), y: cam.sy($1)) } }
                    if case let .fixed(fix, x, y, acc, _, bearing)? = where_?.state, fix != .outside {
                        MeDot(x: cam.sx(x), y: cam.sy(y), radius: acc * cam.k, bearing: fix == .approx || fix == .low ? nil : bearing)
                            .frame(width: g.size.width, height: g.size.height)
                    }
                }
                if !flying { controls }
                StatusBar(s: app.status) { app.statusSheet = true }
                    .padding(.horizontal, 16).padding(.top, 56)
            }
            .onAppear { cam.size = g.size; fitMain() }
            .onChange(of: g.size) { cam.size = g.size }
        }
        .overlay(alignment: .bottom) {
            if panel.wrappedValue == nil {
                VStack(spacing: 10) {
                HStack(alignment: .bottom) {
                    if let w = where_ { LocateKey(on: w.on) { w.toggle(); Fx.shared.tick() } }
                    Spacer()
                    WeatherKey(on: weatherOn) {
                        weatherOn.toggle(); weatherSheet = weatherOn; Fx.shared.tick()
                    }
                }
                HStack(alignment: .bottom, spacing: 8) {
                    if let w = where_, let words = whereWords(w.state, runs: model.runs) {
                        WhereLine(w: words) { whereAction() }
                    } else {
                    Text(S.appMapHint).font(ski.type.text(13)).foregroundStyle(Color(rgb: 0x13233A))
                        .padding(10).frame(maxWidth: .infinity, alignment: .leading).background(Color.white.opacity(0.9))
                    }
                    Button { model.list = true } label: {
                        Text(S.mapOverviewHeading).font(ski.type.text(15, .bold)).foregroundStyle(.white)
                            .padding(.horizontal, 16).frame(minHeight: 44).background(Color(rgb: 0x13233A))
                    }
                    .buttonStyle(.plain)
                }
                }
                .padding(12)
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .principal) { Text(S.navMap).font(ski.type.display(26)).foregroundStyle(ski.colors.ink) }
        }
        .toolbarBackground(ski.colors.snow.opacity(0.9), for: .navigationBar)
        .sheet(item: panel) { p in
            sheet(p)
                .environment(\.ski, ski)
                .presentationDetents(p == .run ? [.height(250), .medium, .large] : [.medium, .large], selection: $detent)
                .presentationBackgroundInteraction(.enabled(upThrough: .medium))
                .presentationBackground(ski.colors.paper)
                .presentationDragIndicator(.visible)
                .presentationCornerRadius(14)
        }
        .onChange(of: model.selected?.key) { _, k in
            detent = k != nil ? .height(250) : .medium
            if k != nil { frameRun() }
        }
        .onChange(of: model.lift?.id) { _, id in if id != nil { detent = .medium; frameLift() } }
        .onChange(of: model.list) { _, on in if on { detent = .medium } }
        .onChange(of: weatherSheet) { _, on in if on { detent = .medium } }
        // the screenshots' weather layer (qa flag weather=...): on, and its list when no run is open
        .onAppear {
            if UserDefaults.standard.string(forKey: "qa.weather") != nil, !weatherOn {
                weatherOn = true
                weatherSheet = model.selected == nil
            }
        }
        .onChange(of: where_?.asking) { _, on in if on == true { detent = .medium } }
        .onChange(of: model.terrain != nil, initial: true) { _, has in
            if has, where_ == nil, let t = model.terrain { where_ = WhereAmI(runs: model.runs, terrain: t) }
        }
        // the first reading brings the map to the dot, once each time the button goes on
        .onChange(of: where_?.state) { _, s in
            if s == .off || s == nil { looked = false }
            if case let .fixed(fix, x, y, _, _, _)? = s, fix != .outside, !looked {
                looked = true
                move(true) { c in c.k = max(c.k, c.size.width / 2500); c.x = x - c.size.width / c.k / 2; c.y = y - c.size.height * (1 - covered) / c.k / 2 }
            }
        }
        // the button goes off in the background and when the map closes (m-7 section 5)
        .onChange(of: phase) { _, p in if p != .active { where_?.off() } }
        .onDisappear { where_?.off() }

        .onChange(of: app.statusSheet) { _, on in
            if on { detent = .large; changes = app.status.changes(StatusSource.before()) }
            else if app.status.fresh { StatusSource.seen(app.status.snapshot()) }
        }
        .onAppear { if app.statusSheet { detent = .large; changes = app.status.changes(StatusSource.before()) } }
        .task {
            // the light follows the clock in Gudauri (auto)
            while !Task.isCancelled { try? await Task.sleep(for: .seconds(60)); now = Date() }
        }
    }

    @ViewBuilder private func sheet(_ p: Panel) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                switch p {
                case .run:
                    if let r = model.selected {
                        RunHead(p: r, model: model, flying: flying, canFly: threeD,
                                onFly: { map3d?.flyDown() }, onStop: { map3d?.stopFly() })
                        RunBody(p: r, model: model, flyAt: { map3d?.flyInfo()?.d }, weather: app.weather(Date()))
                    }
                case .lift:
                    if let l = model.lift { LiftHead(l: l); LiftBody(l: l, model: model) }
                case .list:
                    Text(S.mapOverviewHeading).font(ski.type.display(32)).foregroundStyle(ski.colors.ink).accessibilityAddTraits(.isHeader)
                    RunList(model: model)
                case .weather:
                    WeatherList(w: app.weather(Date())) { id in
                        weatherSheet = false
                        if let ll = defaultLL[id] { let x = Geo.x(ll.lon), y = Geo.y(ll.lat); move(true) { $0.k = max($0.k, $0.size.width / 3000); $0.x = x - $0.size.width / $0.k / 2; $0.y = y - $0.size.height / $0.k / 2 } }
                    }
                case .ask:
                    AskSheet(onGo: { where_?.ask() }, onLater: { where_?.asking = false }).padding(.horizontal, -16)
                case .status:
                    let month = Calendar(identifier: .gregorian).component(.month, from: Date())
                    StatusBoard(s: app.status, changes: changes, forMe: $forMe, inSeason: LiftStatus.inSeason(month: month))
                }
            }
            .padding(.horizontal, 16).padding(.top, 20).padding(.bottom, 30)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .scrollBounceBehavior(.basedOnSize)
    }

    /// The weather points over the map from above: a dot at each, its card above it (below when it would go under the
    /// map's own buttons and bar).
    private func pins(_ size: CGSize, at: @escaping (Double, Double) -> CGPoint?) -> some View {
        let placed = Self.placePins(pinTexts(app.weather(Date())).compactMap { p in at(p.x, p.y).map { (p, $0) } }, size: size)
        return ZStack(alignment: .topLeading) {
            ForEach(placed) { e in
                Circle().fill(ski.colors.ink).frame(width: 8, height: 8).overlay(Circle().stroke(ski.colors.paper, lineWidth: 2))
                    .position(e.dot)
                PinCard(p: e.p).fixedSize().position(e.card)
            }
        }
        .frame(width: size.width, height: size.height)
        .environment(\.layoutDirection, .leftToRight)
        .allowsHitTesting(false)
    }

    /// Where each card goes: above its dot, or below it when it would go under the map's own buttons and bar (the top
    /// band, down to the view switcher and the Kobi key on a phone); and a card that would cover another one moves
    /// above it (or below, when the band leaves no room). A card is about 180 by 68.
    struct PlacedPin: Identifiable { let p: PinText; let dot: CGPoint; let card: CGPoint; var id: String { p.id } }

    static func placePins(_ pts: [(PinText, CGPoint)], size: CGSize) -> [PlacedPin] {
        let band: CGFloat = 210, half = CGSize(width: 90, height: 34), gap: CGFloat = 14
        var out: [PlacedPin] = []
        for (p, q) in pts.sorted(by: { $0.1.y < $1.1.y }) {
            let x = min(max(q.x, half.width + 8), size.width - half.width - 8)
            var y = q.y - gap - half.height
            if y - half.height < band { y = max(q.y + gap + half.height, band + half.height) }
            // a card on top of another goes above it when there is room under the band (the sheet covers the bottom),
            // else below it
            for o in out where abs(o.card.x - x) < half.width * 2 && abs(o.card.y - y) < half.height * 2 + 4 {
                let up = o.card.y - half.height * 2 - 4
                y = up - half.height >= band ? up : o.card.y + half.height * 2 + 4
            }
            out.append(PlacedPin(p: p, dot: q, card: CGPoint(x: x, y: y)))
        }
        return out
    }

    /// Over the mountain in 3D: the weather points and where-am-I dot, on the snow where they are.
    @ViewBuilder
    private func over3D(_ p: Map3DProjector, _ size: CGSize) -> some View {
        ZStack {
            if weatherOn { pins(size) { p.point(x: $0, y: $1) } }
            if case let .fixed(fix, x, y, acc, _, bearing)? = where_?.state, fix != .outside, let q = p.point(x: x, y: y) {
                MeDot(x: q.x, y: q.y, radius: acc * p.pointsPerMetre(x: x, y: y), bearing: fix == .approx || fix == .low ? nil : bearing.map { $0 + p.heading })
                    .frame(width: size.width, height: size.height)
            }
        }
        .frame(width: size.width, height: size.height)
    }

    /// The line's action: the settings for a refusal, precise location, or location services that are off.
    private func whereAction() {
        if let u = URL(string: UIApplication.openSettingsURLString) { openURL(u) }
    }

    // MARK: controls

    private var controls: some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 8) {
                if Map3DView.available { viewSwitch }
                if !threeD { kobiKey; scale }
            }
            Spacer()
            VStack(spacing: 6) {
                if threeD {
                    compass
                } else {
                    key("plus", S.mapZoomIn) { zoom(1.5) }
                    key("minus", S.mapZoomOut) { zoom(1 / 1.5) }
                    key("arrow.up.left.and.arrow.down.right", S.mapZoomFit) { fitMain(animated: true) }
                }
            }
            .padding(.top, 56)
        }
        .padding(.horizontal, 12).padding(.top, 104)
    }

    /// The view switch (A-30), as the site's and Android's: 3D, from above.
    private var viewSwitch: some View {
        HStack(spacing: 0) {
            ForEach([("3d", S.mapView3d), ("2d", S.mapViewTop)], id: \.0) { item in
                let m = item.0, label = item.1
                let on = (m == "2d") != threeD
                Button {
                    guard model.mode != m else { return }
                    model.mode = m; Fx.shared.tick()
                    if m == "2d" { fitMain() }
                } label: {
                    Text(label).font(ski.type.text(14, .bold)).foregroundStyle(on ? ski.colors.paper : ski.colors.ink)
                        .padding(.horizontal, 14).frame(minHeight: 44)
                        .background(on ? ski.colors.ink : .clear, in: RoundedRectangle(cornerRadius: 4))
                }
                .buttonStyle(.plain)
                .accessibilityLabel(label).accessibilityAddTraits(on ? .isSelected : [])
            }
        }
        .background(ski.colors.paper.opacity(0.94), in: RoundedRectangle(cornerRadius: 4))
        .overlay(RoundedRectangle(cornerRadius: 4).stroke(ski.colors.rule, lineWidth: 1))
        .accessibilityElement(children: .contain).accessibilityLabel(S.mapViewType)
    }

    /// The site's compass: red to the north, ink to the south; turns the camera back to look north.
    private var compass: some View {
        Button { map3d?.north(); Fx.shared.tick() } label: {
            Canvas { c, s in
                let w = s.width, h = s.height
                var n = Path(); n.move(to: CGPoint(x: w / 2, y: h * 0.1)); n.addLine(to: CGPoint(x: w * 0.7, y: h / 2)); n.addLine(to: CGPoint(x: w * 0.3, y: h / 2)); n.closeSubpath()
                var so = Path(); so.move(to: CGPoint(x: w / 2, y: h * 0.9)); so.addLine(to: CGPoint(x: w * 0.7, y: h / 2)); so.addLine(to: CGPoint(x: w * 0.3, y: h / 2)); so.closeSubpath()
                c.fill(n, with: .color(ski.colors.red)); c.fill(so, with: .color(ski.colors.ink))
            }
            .frame(width: 20, height: 20)
            .frame(width: 44, height: 44).background(ski.colors.paper.opacity(0.94), in: RoundedRectangle(cornerRadius: 4))
            .overlay(RoundedRectangle(cornerRadius: 4).stroke(ski.colors.rule, lineWidth: 1))
        }
        .buttonStyle(.plain).accessibilityLabel(S.mapCompass)
    }

    private var kobiKey: some View {
                Button { frameKobi() } label: {
                    HStack(spacing: 6) {
                        Rectangle().fill(ski.colors.blue).frame(width: 12, height: 12)
                        Text(S.mapKobiSide).font(ski.type.text(14, .bold)).foregroundStyle(ski.colors.ink)
                    }
                    .padding(.horizontal, 12).frame(minHeight: 44).background(ski.colors.paper.opacity(0.94), in: RoundedRectangle(cornerRadius: 4))
                    .overlay(RoundedRectangle(cornerRadius: 4).stroke(ski.colors.rule, lineWidth: 1))
                }
                .buttonStyle(.plain).accessibilityLabel(S.mapKobiSideAria)
    }

    private func key(_ icon: String, _ label: String, _ action: @escaping () -> Void) -> some View {
        Button { action(); Fx.shared.tick() } label: {
            Image(systemName: icon).font(.system(size: 17, weight: .bold)).foregroundStyle(ski.colors.ink)
                .frame(width: 44, height: 44).background(ski.colors.paper.opacity(0.94), in: RoundedRectangle(cornerRadius: 4))
                .overlay(RoundedRectangle(cornerRadius: 4).stroke(ski.colors.rule, lineWidth: 1))
        }
        .buttonStyle(.plain).accessibilityLabel(label)
    }

    /// The round length that is at least 70 points long (the site's scale).
    private var scale: some View {
        let mpp = 1 / cam.k
        let m = [50, 100, 200, 250, 500, 1000, 2000].first { Double($0) / mpp >= 70 } ?? 2000
        return VStack(alignment: .leading, spacing: 2) {
            Rectangle().fill(ski.colors.ink).frame(width: Double(m) / mpp, height: 3)
            Text(m >= 1000 ? "\(m / 1000) km" : "\(m) m").font(ski.type.text(11)).foregroundStyle(ski.colors.ink)
        }
        .padding(.horizontal, 6).padding(.vertical, 3).background(ski.colors.paper.opacity(0.85))
        .environment(\.layoutDirection, .leftToRight)
        .accessibilityHidden(true)
    }

    // MARK: framing

    private func box(_ lines: [[(x: Double, y: Double)]]) -> CGRect? {
        var a = Double.infinity, b = Double.infinity, c = -Double.infinity, d = -Double.infinity
        for l in lines { for q in l { a = min(a, q.x); c = max(c, q.x); b = min(b, q.y); d = max(d, q.y) } }
        return a > c ? nil : CGRect(x: a, y: b, width: c - a, height: d - b)
    }

    private func move(_ animated: Bool, _ f: (inout MapCamera) -> Void) {
        var c = cam; f(&c)
        if animated && !still { withAnimation(.easeInOut(duration: 0.6)) { cam = c } } else { cam = c }
    }

    /// The main side, with room above for the peaks (the site's fit: 350 m more at the top).
    private func fitMain(animated: Bool = false) {
        guard var b = box(model.runs.mainPistes.flatMap(\.lines) + model.runs.mainLifts.map(\.line)) else { return }
        b.origin.y -= 350; b.size.height += 350
        move(animated) { $0.frame(b, pad: 1.12, bottom: covered) }
        fitted = true
    }

    private func frameKobi() {
        let k = RunsAndLifts.kobiLat
        guard let b = box(model.runs.pistes.filter { $0.lat > k }.flatMap(\.lines) + model.runs.lifts.filter { $0.lat > k }.map(\.line)) else { return }
        move(true) { $0.frame(b, pad: 1.3, minSpan: 900, bottom: covered) }
    }

    private func frameRun() {
        guard let p = model.selected, let b = box(p.lines) else { return }
        move(true) { $0.frame(b, pad: 1.5, minSpan: 900, bottom: 0.36) }
    }

    private func frameLift() {
        guard let l = model.lift, let b = box([l.line]) else { return }
        move(true) { $0.frame(b, pad: 1.6, minSpan: 900, bottom: 0.5) }
    }

    private func zoom(_ f: Double) {
        move(true) { $0.zoom(f, at: CGPoint(x: $0.size.width / 2, y: $0.size.height / 2)) }
    }
}
