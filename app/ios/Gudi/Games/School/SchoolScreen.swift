import SwiftUI
import GudiDomain

// one look: a bright training slope, the same in both themes (the site's school page)
private let inkC = Color(rgb: 0x13233A), mutedC = Color(rgb: 0x4B5A6F), ruleC = Color(rgb: 0xCBD5DF), accentC = Color(rgb: 0xF4B942)
private let greenC = Color(rgb: 0x1B8A4C), redC = Color(rgb: 0xD1342B), blueC = Color(rgb: 0x1F5FC4), pageC = Color(rgb: 0xF4F8FB)

private func say(_ s: Say) -> String { s.text() }
private func text(_ key: String) -> String { Strings.shared.text(key) }
private func firstSentence(_ s: String) -> String { s.components(separatedBy: ".").first ?? s }

@MainActor
@Observable
private final class SchoolModel {
    enum Phase { case menu, brief, run, result }
    let synth = Synth()
    let view: SchoolView
    var phase = Phase.menu
    var cur = 0
    var starsV = 0
    var result: SchoolRun.Result?
    var coachLine: (Say, Int)?
    var checkList: [SchoolRun.Check] = []
    var popSay: Say?
    var popN = 0
    var kmh = 0
    var over = false
    var wedgePct = 15
    var skiL = false
    var skiR = false
    var wedgeOn = false
    @ObservationIgnored var hiss: Synth.Hiss?

    init(still: Bool, lang: Lang) {
        view = SchoolView(still: still)
        view.font = GameFonts.text(lang, 13, bold: true)
        view.words = { $0.text() }
        view.onFrame = { [weak self] r in self?.frame(r) }
    }

    func stars(_ i: Int) -> Int { Bests.lesson(i + 1) }

    private func frame(_ r: SchoolRun) {
        let k = Int((r.kmh + 0.5).rounded(.down)); if k != kmh { kmh = k }
        if r.over != over { over = r.over }
        let w = Int((r.wedgeCtl * 100 + 0.5).rounded(.down)); if w != wedgePct { wedgePct = w }
        // skis on the snow: louder and brighter with the speed, the skid and the wedge
        if let h = hiss, r.running {
            h.gain = min(0.22, r.s.v * 0.012 + r.s.skid * 0.12 + r.s.wedge * 0.05)
            h.freq = 900 + Double(r.s.v) * 80 + Double(r.s.skid) * 1500
        }
    }

    func stopRun() { hiss?.stop(); hiss = nil; view.run = nil; skiL = false; skiR = false; wedgeOn = false }
    func toMenu() { stopRun(); phase = .menu; starsV += 1 }
    func brief(_ i: Int) { stopRun(); cur = i; result = nil; phase = .brief }
    func start() {
        result = nil; coachLine = nil; popSay = nil; kmh = 0; over = false
        let run = SchoolRun(LESSONS[cur], out: self)
        view.run = run
        hiss?.stop(); hiss = synth.hiss()
        phase = .run
    }

    // what the lesson says
    func coach(_ say: Say, tone: Int) { coachLine = (say, tone) }
    func pop(_ say: Say) { popSay = say; popN += 1 }
    func checks(_ items: [SchoolRun.Check]) { checkList = items }
    func ding(_ f: Double, _ d: Double, _ v: Float, square: Bool) { synth.ding(f, d, v, square: square) }
    func buzz(_ strength: Float) { if strength >= 1 { GameHaptics.thud(0.9) } else { GameHaptics.click(CGFloat(strength)) } }
    func finished(_ r: SchoolRun.Result) {
        hiss?.gain = 0
        Bests.ended("school", level: String(cur + 1), score: r.stars)
        result = r; starsV += 1
        Task { @MainActor in
            try? await Task.sleep(for: .seconds(0.9))
            if phase == .run { stopRun(); phase = .result }
        }
    }
}

// the run calls it on the main thread (the frame clock and the touches)
extension SchoolModel: @preconcurrency SchoolOut {}

private struct SchoolCanvas: UIViewRepresentable {
    let view: SchoolView
    func makeUIView(context: Context) -> SchoolView { view }
    func updateUIView(_ v: SchoolView, context: Context) {}
}

/// The ski school on the phone (13.6), the site's game (site/games/school) and Android's SchoolScreen.kt: seven short
/// lessons from the first stop in a wedge to carving, each with one principle, its own control and a drill built for
/// it, and a coach who says what is happening and why. A lesson opens when the one before it has a star; the stars of
/// each lesson are kept on the phone, and their sum is the school's record (the group's table too).
struct SchoolScreen: View {
    @Environment(\.ski) private var ski
    @Environment(\.accessibilityReduceMotion) private var still
    @Environment(\.dismiss) private var dismiss
    @State private var m: SchoolModel?

    var body: some View {
        ZStack {
            pageC.ignoresSafeArea()
            if let m { content(m) }
        }
        .toolbar(.hidden, for: .navigationBar)
        .onAppear {
            if m == nil {
                m = SchoolModel(still: still, lang: ski.type.lang)
                if UserDefaults.standard.string(forKey: "qaRoute") == "game:school" { Bests.openAllLessons() }
            }
            m?.synth.start()
        }
        .onDisappear { m?.stopRun(); m?.synth.stop() }
    }

    @ViewBuilder private func content(_ m: SchoolModel) -> some View {
        // the slope stays put under the pages (one view for the whole visit), drawing only while a lesson runs
        SchoolCanvas(view: m.view).ignoresSafeArea().accessibilityLabel(S.gameSchoolCanvasLabel)
        switch m.phase {
        case .menu: pageC.ignoresSafeArea(); menu(m)
        case .brief: pageC.ignoresSafeArea(); brief(m)
        case .result: pageC.ignoresSafeArea(); if let r = m.result { resultCard(m, r) }
        case .run: running(m)
        }
    }

    // ---------- the menu, the brief and the result ----------

    private func page<C: View>(@ViewBuilder _ c: () -> C) -> some View {
        ScrollView { VStack(alignment: .leading, spacing: 14) { c() }.padding(.horizontal, 16).padding(.top, 12).padding(.bottom, 20) }
    }

    private func menu(_ m: SchoolModel) -> some View {
        page {
            HStack { GameBack(text: S.gameSchoolBackToGames, color: inkC); Spacer() }
            Text(S.gameSchoolTitle).font(ski.type.display(58)).foregroundStyle(inkC)
            Text(S.gameSchoolIntro).font(ski.type.text(14)).foregroundStyle(mutedC)
            VStack(spacing: 8) {
                let _ = m.starsV
                ForEach(0..<LESSONS.count, id: \.self) { i in
                    lessonRow(i, open: i == 0 || m.stars(i - 1) > 0, stars: m.stars(i)) { m.brief(i) }
                }
            }
            Text(S.gameSchoolDisclaimer).font(ski.type.text(12)).foregroundStyle(mutedC)
        }
    }

    private func lessonRow(_ i: Int, open: Bool, stars: Int, _ action: @escaping () -> Void) -> some View {
        let l = LESSONS[i]
        return Button(action: action) {
            HStack(spacing: 12) {
                Text("\(i + 1)").font(ski.type.display(36)).foregroundStyle(mutedC)
                VStack(alignment: .leading, spacing: 2) {
                    HStack {
                        Text(text(l.title)).font(ski.type.display(25)).foregroundStyle(inkC).frame(maxWidth: .infinity, alignment: .leading)
                        if open { starsText(stars, 18) } else { Image(systemName: "lock.fill").foregroundStyle(accentC) }
                    }
                    Text(firstSentence(text(l.drill)) + ".").font(ski.type.text(12.5)).foregroundStyle(mutedC).multilineTextAlignment(.leading)
                }
            }
            .padding(.leading, 22).padding(.trailing, 12).padding(.vertical, 10)
            .frame(maxWidth: .infinity, minHeight: 60, alignment: .leading)
            .background(alignment: .leading) { Color.white.overlay(alignment: .leading) { Color(rgb: l.color).frame(width: 10) } }
            .background(ruleC.offset(y: 2))
            .opacity(open ? 1 : 0.5)
        }
        .buttonStyle(.plain)
        .disabled(!open)
        .accessibilityLabel("\(i + 1). \(text(l.title))")
        .accessibilityValue(open ? "\(stars)/3" : "🔒")
    }

    private func starsText(_ n: Int, _ size: CGFloat) -> some View {
        HStack(spacing: 1) {
            Text(String(repeating: "★", count: n)).foregroundStyle(accentC)
            Text(String(repeating: "☆", count: 3 - n)).foregroundStyle(accentC)
        }
        .font(.system(size: size))
    }

    /// The lesson's card (the site's .card): the trail colour on top.
    private func card<C: View>(_ color: Color, @ViewBuilder _ c: () -> C) -> some View {
        VStack(alignment: .leading, spacing: 10) { c() }
            .padding(.horizontal, 16).padding(.top, 24).padding(.bottom, 16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(alignment: .top) { Color.white.overlay(alignment: .top) { color.frame(height: 8) } }
            .background(ruleC.offset(y: 2))
    }

    private func box(_ label: String?, _ t: String) -> some View {
        Text((label.map { $0 + " " } ?? "") + t).font(ski.type.text(13.5)).foregroundStyle(inkC).lineSpacing(3)
            .padding(.horizontal, 10).padding(.vertical, 8).frame(maxWidth: .infinity, alignment: .leading).background(Color(rgb: 0xEEF2F5))
    }

    private func goals(_ l: Lesson, _ met: [Bool]?) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            ForEach(0..<3, id: \.self) { i in
                let ok = met?[i] == true
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Text(ok ? "★" : "☆").font(.system(size: 13.5)).foregroundStyle(ok ? accentC : ruleC)
                    Text(text(l.goals[i])).font(ski.type.text(13.5, ok ? .bold : .regular)).foregroundStyle(ok ? inkC : mutedC)
                }
            }
        }
    }

    private func goButton(_ t: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(t).font(ski.type.display(34)).foregroundStyle(inkC).multilineTextAlignment(.center)
                .frame(maxWidth: .infinity, minHeight: 60).background(accentC).background(inkC.offset(y: 4))
        }
        .buttonStyle(.plain)
    }

    private func outlineButton(_ t: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(t).font(ski.type.text(15, .bold)).foregroundStyle(inkC).multilineTextAlignment(.center).padding(.horizontal, 8)
                .frame(maxWidth: .infinity, minHeight: 48).overlay { Rectangle().stroke(inkC, lineWidth: 2) }
        }
        .buttonStyle(.plain)
    }

    private func brief(_ m: SchoolModel) -> some View {
        let l = LESSONS[m.cur]
        return page {
            card(Color(rgb: l.color)) {
                Text(S.gameSchoolBriefHeader(n: String(m.cur + 1), total: String(LESSONS.count))).font(ski.type.text(12, .bold)).foregroundStyle(mutedC)
                Text(text(l.title)).font(ski.type.display(40)).foregroundStyle(inkC)
                Figure(fig: l.fig)
                Text(text(l.rule)).font(ski.type.text(16)).foregroundStyle(inkC)
                box(S.gameSchoolBriefControls, text(l.ctrl))
                box(S.gameSchoolBriefDrill, text(l.drill))
                goals(l, nil)
            }
            goButton(S.gameSchoolGo) { m.start() }
            HStack(spacing: 8) { outlineButton(S.gameSchoolBackToLessons) { m.toMenu() }; Spacer().frame(maxWidth: .infinity) }
                .padding(.top, 4)
        }
    }

    private func resultCard(_ m: SchoolModel, _ r: SchoolRun.Result) -> some View {
        let i = m.cur, l = LESSONS[i]
        let next = i < LESSONS.count - 1 ? (r.stars > 0 ? S.gameSchoolNextLesson : S.gameSchoolTryAgain) : S.gameSchoolBackToLessons
        return page {
            card(Color(rgb: l.color)) {
                Text(S.gameSchoolResultHeader(n: String(i + 1), name: text(l.title))).font(ski.type.text(12, .bold)).foregroundStyle(mutedC)
                HStack(spacing: 4) {
                    Text(String(repeating: "★", count: r.stars)).foregroundStyle(accentC)
                    Text(String(repeating: "★", count: 3 - r.stars)).foregroundStyle(ruleC)
                }
                .font(.system(size: 48)).accessibilityLabel("\(r.stars)/3")
                Text(say(r.line)).font(ski.type.text(16)).foregroundStyle(inkC)
                goals(l, r.goals)
                box(nil, S.gameSchoolRemember(tip: text(l.tip)))
            }
            goButton(next) {
                if r.stars > 0 { if i < LESSONS.count - 1 { m.brief(i + 1) } else { m.toMenu() } } else { m.brief(i) }
            }
            HStack(spacing: 8) {
                outlineButton(S.gameSchoolRetry) { m.brief(i) }
                outlineButton(S.gameSchoolBackToLessons) { m.toMenu() }
            }
            .padding(.top, 4)
        }
    }

    // ---------- on the slope: the sign and the speed, the drill, the coach, the checklist, and the lesson's controls ----------

    @ViewBuilder private func running(_ m: SchoolModel) -> some View {
        let l = LESSONS[m.cur]
        hud(m, l)
        PopWord(say: m.popSay, n: m.popN, still: still)
        if l.mode == .wedge { slider(m.wedgePct) }
        if l.mode == .skis { skis(m) }
        if l.wedgeBtn { wedgeButton(m) }
        VStack {
            HStack {
                Button { m.toMenu() } label: {
                    Image(systemName: "xmark").font(.system(size: 14, weight: .bold)).foregroundStyle(inkC)
                        .frame(width: 44, height: 44).background(Color.white.opacity(0.85))
                }
                .buttonStyle(.plain).accessibilityLabel(S.gameSchoolBackToLessons)
                Spacer()
            }
            Spacer()
        }
        .padding(.horizontal, 12).padding(.top, 64)
    }

    private func hud(_ m: SchoolModel, _ l: Lesson) -> some View {
        VStack(spacing: 0) {
            HStack(alignment: .top) {
                Text("\(m.cur + 1). \(text(l.title))").font(ski.type.display(24)).foregroundStyle(.white).lineLimit(1)
                    .padding(.leading, 14).padding(.trailing, 26).padding(.vertical, 4)
                    .background(inkC, in: SignShape(arrow: 16, rtl: ski.type.lang.rtl))
                Spacer(minLength: 6)
                VStack(spacing: 0) {
                    Text("\(m.kmh)").font(ski.type.display(30)).foregroundStyle(.white).monospacedDigit()
                    Text(S.gameSchoolKmh).font(ski.type.text(11)).foregroundStyle(.white.opacity(0.85))
                }
                .padding(.horizontal, 10).padding(.vertical, 6).frame(minWidth: 76)
                .background(m.over ? redC : inkC.opacity(0.86))
                .accessibilityElement(children: .combine)
            }
            // the drill, in short
            Text(firstSentence(text(l.drill))).font(ski.type.text(ski.type.lang.rtl ? 14 : 13, .bold)).foregroundStyle(inkC)
                .lineLimit(2).multilineTextAlignment(.center)
                .padding(.horizontal, 12).padding(.vertical, 5).background(Color.white.opacity(0.92))
                .background(inkC.opacity(0.15).offset(y: 2))
                .padding(.top, 8)
            // the coach
            if let c = m.coachLine {
                let tone = c.1
                Text(say(c.0)).font(ski.type.text(15, .bold)).foregroundStyle(.white).multilineTextAlignment(.center)
                    .padding(.horizontal, 12).padding(.vertical, 6).frame(maxWidth: 480)
                    .background(tone == SchoolRun.good ? greenC : tone == SchoolRun.bad ? redC : inkC)
                    .padding(.top, 8)
                    .accessibilityAddTraits(.updatesFrequently)
            }
            // the checklist, on the right in every language
            HStack {
                Spacer()
                VStack(alignment: .trailing, spacing: 3) {
                    ForEach(Array(m.checkList.enumerated()), id: \.offset) { _, c in
                        let mark = c.mark == SchoolRun.ok ? "✓ " : c.mark == SchoolRun.no ? "✗ " : "☐ "
                        let col = c.mark == SchoolRun.ok ? greenC : c.mark == SchoolRun.no ? redC : mutedC
                        Text(mark + say(c.say)).font(ski.type.text(12.5, .bold)).foregroundStyle(col)
                            .padding(.horizontal, 8).padding(.vertical, 3).background(Color.white.opacity(0.9))
                            .environment(\.layoutDirection, ski.type.lang.rtl ? .rightToLeft : .leftToRight)
                    }
                }
                .frame(maxWidth: 240, alignment: .trailing)
            }
            .environment(\.layoutDirection, .leftToRight)
            .padding(.top, 10)
            Spacer()
        }
        .padding(.horizontal, 12).padding(.top, 10)
        .allowsHitTesting(false)
    }

    /// Lesson 1: the wedge's width, from the finger's height (the slider on the left only shows it).
    private func slider(_ pct: Int) -> some View {
        GeometryReader { g in
            let top = g.size.height * 0.3, h = g.size.height * 0.52
            ZStack(alignment: .topLeading) {
                LinearGradient(stops: [.init(color: Color(rgb: 0xEEF2F5), location: 0), .init(color: accentC, location: 0.6), .init(color: redC, location: 1)],
                               startPoint: .top, endPoint: .bottom)
                    .frame(width: 12, height: h).offset(x: 26)
                Text(S.gameSchoolSliderStraight).font(ski.type.text(11, .bold)).foregroundStyle(mutedC).fixedSize().offset(x: 44, y: -2)
                Text(S.gameSchoolSliderWide).font(ski.type.text(11, .bold)).foregroundStyle(mutedC).fixedSize().offset(x: 44, y: h - 14)
                Text(S.gameSchoolSliderPct(pct: String(pct))).font(ski.type.text(11, .bold)).foregroundStyle(.white).lineLimit(1).minimumScaleFactor(0.7)
                    .frame(width: 56, height: 30).background(inkC).offset(x: 4, y: h * CGFloat(pct) / 100 - 15)
            }
            .offset(x: 14, y: top)
        }
        .environment(\.layoutDirection, .leftToRight)
        .allowsHitTesting(false)
    }

    /// A button held down (a ski, the wedge): down when the finger lands, up when it leaves.
    private func hold(_ on: @escaping (Bool) -> Void) -> some Gesture {
        DragGesture(minimumDistance: 0).onChanged { _ in on(true) }.onEnded { _ in on(false) }
    }

    /// Lesson 2: the two skis, the left one on the left in every language.
    private func skis(_ m: SchoolModel) -> some View {
        VStack {
            Spacer()
            HStack(spacing: 12) {
                ForEach([false, true], id: \.self) { isRight in
                    let down = isRight ? m.skiR : m.skiL
                    VStack(spacing: 2) {
                        Text(isRight ? S.gameSchoolSkiRight : S.gameSchoolSkiLeft).font(ski.type.display(30)).foregroundStyle(inkC)
                        Text(S.gameSchoolPressIt).font(ski.type.text(11.5)).foregroundStyle(inkC)
                    }
                    .frame(maxWidth: .infinity, minHeight: 96)
                    .background(down ? accentC : Color.white.opacity(0.88))
                    .overlay { Rectangle().stroke(down ? accentC : inkC, lineWidth: 2) }
                    .contentShape(Rectangle())
                    .gesture(hold { d in
                        guard d != (isRight ? m.skiR : m.skiL) else { return }
                        if isRight { m.skiR = d } else { m.skiL = d }
                        m.view.run?.ski(right: isRight, down: d)
                        if d { GameHaptics.tick(0.4) }
                    })
                    .accessibilityElement(children: .combine)
                    .accessibilityAddTraits(.isButton)
                    .accessibilityAction { m.view.run?.ski(right: isRight, down: true); m.view.run?.ski(right: isRight, down: false) }
                }
            }
            .environment(\.layoutDirection, .leftToRight)
        }
        .padding(12)
    }

    /// Lesson 7: the wedge, held to slow down, at the bottom left.
    private func wedgeButton(_ m: SchoolModel) -> some View {
        VStack {
            Spacer()
            HStack {
                VStack(spacing: 2) {
                    Text(S.gameSchoolWedgeBtn).font(ski.type.display(30)).foregroundStyle(inkC)
                    Text(S.gameSchoolWedgeBtnHint).font(ski.type.text(11.5)).foregroundStyle(inkC).multilineTextAlignment(.center)
                }
                .padding(6)
                .frame(width: 170).frame(minHeight: 84)
                .background(m.wedgeOn ? accentC : Color.white.opacity(0.85))
                .overlay { Rectangle().stroke(m.wedgeOn ? accentC : inkC, lineWidth: 2) }
                .contentShape(Rectangle())
                .gesture(hold { d in
                    guard d != m.wedgeOn else { return }
                    m.wedgeOn = d; m.view.run?.wedgeBtn = d; if d { GameHaptics.tick(0.4) }
                })
                .accessibilityElement(children: .combine)
                .accessibilityAddTraits(.isButton)
                Spacer()
            }
            .environment(\.layoutDirection, .leftToRight)
        }
        .padding(12)
    }
}

/// The big word in the middle (the site's .pop): it jumps in, stays a moment and floats away.
private struct PopWord: View {
    let say: Say?
    let n: Int
    let still: Bool
    @Environment(\.ski) private var ski
    @State private var born: Date?

    var body: some View {
        TimelineView(.animation(paused: born == nil)) { tl in
            GeometryReader { g in
                if let say, let b = born {
                    let k = min(1, tl.date.timeIntervalSince(b) / 1.1)
                    let alpha = still ? 1 : k < 0.2 ? k / 0.2 : k < 0.75 ? 1 : 1 - (k - 0.75) / 0.25
                    let sc = still ? 1 : k < 0.2 ? 0.6 + (k / 0.2) * 0.46 : k < 0.3 ? 1.06 - (k - 0.2) / 0.1 * 0.06 : 1
                    let dy = still ? 0 : k < 0.2 ? 20 * (1 - k / 0.2) : k < 0.75 ? 0 : -25 * (k - 0.75) / 0.25
                    if k < 1 {
                        Text(say.text()).font(ski.type.display(min(54, max(34, g.size.width * 0.1)))).foregroundStyle(inkC).lineLimit(1).fixedSize()
                            .shadow(color: .white, radius: 7, y: 4)
                            .scaleEffect(sc).opacity(alpha)
                            .position(x: g.size.width / 2, y: g.size.height * 0.3 + dy)
                    }
                }
            }
        }
        .allowsHitTesting(false)
        .onChange(of: n) { _, _ in born = .now; Task { @MainActor in try? await Task.sleep(for: .seconds(1.15)); if let b = born, Date.now.timeIntervalSince(b) >= 1.1 { born = nil } } }
    }
}

// ---------- the lesson cards' drawings (the site's D), forward up as on the slope; the words in a legend under them ----------

private struct Figure: View {
    let fig: SchoolFig
    @Environment(\.ski) private var ski

    var body: some View {
        let legend: [(String, Color)] = switch fig {
        case .wedge: [("d_tips_close", mutedC), ("d_tails_apart", mutedC)]
        case .outer: [("d_press_right", redC), ("d_turn_left", mutedC)]
        case .shape: [("d_straight_fast", redC), ("d_across_slow", blueC)]
        case .rhythm: [("d_tick", mutedC)]
        case .look: [("d_eyes_far", mutedC)]
        case .carve: [("d_carve_lines", inkC), ("d_skid_smear", mutedC)]
        case .final: []
        }
        let h: CGFloat = switch fig { case .outer, .shape: 130; case .final: 110; default: 120 }
        VStack(spacing: 6) {
            Canvas { c, size in
                c.scaleBy(x: size.width / 220, y: size.width / 220)
                Figure.draw(&c, fig)
            }
            .environment(\.layoutDirection, .leftToRight)
            .aspectRatio(220 / h, contentMode: .fit)
            .frame(maxWidth: 220)
            if !legend.isEmpty {
                CenteredFlow(spacing: 14, lineSpacing: 6) {
                    ForEach(0..<legend.count, id: \.self) { i in
                        let k = legend[i].0, col = legend[i].1
                        HStack(spacing: 6) {
                            RoundedRectangle(cornerRadius: 2).fill(col).frame(width: 12, height: 4)
                            Text(text("game.school." + k)).font(ski.type.text(13)).foregroundStyle(col)
                        }
                    }
                }
            }
        }
        .frame(maxWidth: .infinity)
        .accessibilityHidden(true)
    }

    static func draw(_ c: inout GraphicsContext, _ f: SchoolFig) {
        func line(_ a: CGPoint, _ b: CGPoint) -> Path { var p = Path(); p.move(to: a); p.addLine(to: b); return p }
        func P(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: x, y: y) }
        let dash = { (a: CGFloat, b: CGFloat, w: CGFloat) in StrokeStyle(lineWidth: w, dash: [a, b]) }
        switch f {
        case .wedge:
            c.stroke(line(P(98, 14), P(60, 106)), with: .color(inkC), style: StrokeStyle(lineWidth: 10, lineCap: .square))
            c.stroke(line(P(122, 14), P(160, 106)), with: .color(inkC), style: StrokeStyle(lineWidth: 10, lineCap: .square))
        case .outer:
            var p = Path(); p.move(to: P(110, 120)); p.addQuadCurve(to: P(50, 14), control: P(110, 60))
            c.stroke(p, with: .color(ruleC), style: dash(6, 5, 3))
            var g = c; g.translateBy(x: 104, y: 70); g.rotate(by: .degrees(-20))
            g.fill(Path(CGRect(x: -20, y: -36, width: 8, height: 70)), with: .color(inkC)); g.fill(Path(CGRect(x: 12, y: -36, width: 8, height: 70)), with: .color(redC))
        case .shape:
            var p = Path(); p.move(to: P(110, 124)); p.addCurve(to: P(110, 66), control1: P(200, 104), control2: P(200, 80))
            p.addCurve(to: P(110, 6), control1: P(20, 52), control2: P(20, 28))
            c.stroke(p, with: .color(blueC), lineWidth: 4)
            c.stroke(line(P(110, 6), P(110, 124)), with: .color(redC), style: dash(5, 5, 3))
        case .rhythm:
            var p = Path(); p.move(to: P(110, 116)); p.addCurve(to: P(110, 64), control1: P(170, 100), control2: P(170, 80))
            p.addCurve(to: P(110, 8), control1: P(50, 48), control2: P(50, 28))
            c.stroke(p, with: .color(blueC), lineWidth: 4)
            c.fill(Path(ellipseIn: CGRect(x: 145, y: 83, width: 14, height: 14)), with: .color(accentC))
            c.fill(Path(ellipseIn: CGRect(x: 61, y: 29, width: 14, height: 14)), with: .color(accentC))
        case .look:
            c.fill(Path(ellipseIn: CGRect(x: 100, y: 98, width: 20, height: 20)), with: .color(Color(rgb: 0xF07A2E)))
            c.stroke(line(P(110, 98), P(150, 14)), with: .color(mutedC), style: dash(4, 4, 2))
            c.fill(Path(CGRect(x: 80, y: 70, width: 4, height: 16)), with: .color(blueC.opacity(0.25)))
            c.fill(Path(CGRect(x: 132, y: 42, width: 4, height: 16)), with: .color(blueC)); c.fill(Path(CGRect(x: 150, y: 12, width: 4, height: 16)), with: .color(blueC))
        case .carve:
            var a = Path(); a.move(to: P(30, 110)); a.addQuadCurve(to: P(190, 10), control: P(110, 70)); c.stroke(a, with: .color(inkC), lineWidth: 2)
            var b = Path(); b.move(to: P(40, 114)); b.addQuadCurve(to: P(200, 14), control: P(120, 74)); c.stroke(b, with: .color(inkC), lineWidth: 2)
            var s = Path(); s.move(to: P(20, 40)); s.addQuadCurve(to: P(80, 70), control: P(50, 50)); c.stroke(s, with: .color(Color(rgb: 0x9FB5CB).opacity(0.6)), lineWidth: 16)
        case .final:
            var m = Path(); m.move(to: P(10, 100)); for q in [P(80, 30), P(120, 60), P(160, 14), P(210, 100)] { m.addLine(to: q) }; m.closeSubpath()
            c.fill(m, with: .color(Color(rgb: 0xE9F1F8))); c.stroke(m, with: .color(Color(rgb: 0x9FB5CB)), lineWidth: 2)
            var p = Path(); p.move(to: P(150, 26)); p.addCurve(to: P(130, 90), control1: P(120, 50), control2: P(170, 60)); c.stroke(p, with: .color(blueC), lineWidth: 3)
        }
    }
}
