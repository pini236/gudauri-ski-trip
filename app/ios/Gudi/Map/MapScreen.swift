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
            model = m
        }
    }
}

/// What the sheet over the map shows.
private enum Panel: String, Identifiable {
    case run, lift, list, status
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

    private var panel: Binding<Panel?> {
        Binding {
            if app.statusSheet { return .status }
            if model.selected != nil { return .run }
            if model.lift != nil { return .lift }
            if model.list { return .list }
            return nil
        } set: { v in
            guard v == nil else { return }
            app.statusSheet = false
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
                OverviewMap(model: model, cam: $cam, status: app.status, forMe: forMe) { frameKobi() }
                    .ignoresSafeArea(edges: .bottom)
                controls
                StatusBar(s: app.status) { app.statusSheet = true }
                    .padding(.horizontal, 16).padding(.top, 56)
            }
            .onAppear { cam.size = g.size; fitMain() }
            .onChange(of: g.size) { cam.size = g.size }
        }
        .overlay(alignment: .bottom) {
            if panel.wrappedValue == nil {
                HStack(alignment: .bottom, spacing: 8) {
                    Text(S.appMapHint).font(ski.type.text(13)).foregroundStyle(Color(rgb: 0x13233A))
                        .padding(10).frame(maxWidth: .infinity, alignment: .leading).background(Color.white.opacity(0.9))
                    Button { model.list = true } label: {
                        Text(S.mapOverviewHeading).font(ski.type.text(15, .bold)).foregroundStyle(.white)
                            .padding(.horizontal, 16).frame(minHeight: 44).background(Color(rgb: 0x13233A))
                    }
                    .buttonStyle(.plain)
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
        .onChange(of: app.statusSheet) { _, on in
            if on { detent = .large; changes = app.status.changes(StatusSource.before()) }
            else if app.status.fresh { StatusSource.seen(app.status.snapshot()) }
        }
        .onAppear { if app.statusSheet { detent = .large; changes = app.status.changes(StatusSource.before()) } }
    }

    @ViewBuilder private func sheet(_ p: Panel) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                switch p {
                case .run:
                    if let r = model.selected {
                        RunHead(p: r, model: model, flying: false, canFly: false, onFly: {}, onStop: {})
                        RunBody(p: r, model: model)
                    }
                case .lift:
                    if let l = model.lift { LiftHead(l: l); LiftBody(l: l, model: model) }
                case .list:
                    Text(S.mapOverviewHeading).font(ski.type.display(32)).foregroundStyle(ski.colors.ink).accessibilityAddTraits(.isHeader)
                    RunList(model: model)
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

    // MARK: controls

    private var controls: some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 8) {
                Button { frameKobi() } label: {
                    HStack(spacing: 6) {
                        Rectangle().fill(ski.colors.blue).frame(width: 12, height: 12)
                        Text(S.mapKobiSide).font(ski.type.text(14, .bold)).foregroundStyle(ski.colors.ink)
                    }
                    .padding(.horizontal, 12).frame(minHeight: 44).background(ski.colors.paper.opacity(0.94), in: RoundedRectangle(cornerRadius: 4))
                    .overlay(RoundedRectangle(cornerRadius: 4).stroke(ski.colors.rule, lineWidth: 1))
                }
                .buttonStyle(.plain).accessibilityLabel(S.mapKobiSideAria)
                scale
            }
            Spacer()
            VStack(spacing: 6) {
                key("plus", S.mapZoomIn) { zoom(1.5) }
                key("minus", S.mapZoomOut) { zoom(1 / 1.5) }
                key("arrow.up.left.and.arrow.down.right", S.mapZoomFit) { fitMain(animated: true) }
            }
            .padding(.top, 56)
        }
        .padding(.horizontal, 12).padding(.top, 104)
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
