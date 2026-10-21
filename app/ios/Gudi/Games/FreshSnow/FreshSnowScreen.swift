import SwiftUI
import GudiDomain

private let inkC = Color(rgb: 0x13233A)

@MainActor
@Observable
private final class FreshModel {
    let synth = Synth()
    let view: FreshSnowView
    var lake = false
    var kind = FreshSnowKind.powder
    var tool = FreshSnowView.Tool.hand
    var stat = 0
    var touched = false
    @ObservationIgnored var started = false
    @ObservationIgnored var level = "snow"
    @ObservationIgnored var lakePrev = UserDefaults.standard.integer(forKey: "fresh.lake_best")

    init(still: Bool, lang: Lang) {
        view = FreshSnowView(synth: synth, still: still)
        view.display = GameFonts.display(lang, 52)
        view.chainText = { S.gameFreshChain(n: String($0)) }
        // the lake's longest chain on this phone (the site's lake-best): written only by a longer chain (X-5)
        view.bestChain = lakePrev
        view.onStat = { [weak self] v in self?.stat = v }
        view.onTouched = { [weak self] in
            guard let self else { return }
            if !touched { withAnimation(.easeOut(duration: 0.6)) { self.touched = true } }
            if !started { started = true; level = lake ? "lake" : "snow" }
        }
        view.onChain = { n, _ in
            if n > UserDefaults.standard.integer(forKey: "fresh.lake_best") { UserDefaults.standard.set(n, forKey: "fresh.lake_best") }
        }
    }

    /// No end here, so a game is the play in one scene, from the first touch until switching the scene or leaving; the
    /// group's table keeps the highest number (Bests), as the site.
    func gameEnd() {
        guard started else { return }
        Bests.ended("fresh", level: level, score: stat)
        lakePrev = max(lakePrev, view.bestChain)
        started = false
    }

    func scene(_ lake: Bool) {
        guard lake != self.lake else { return }
        gameEnd(); self.lake = lake; view.lake = lake
    }
}

/// The snow or the lake, in SwiftUI.
private struct FreshCanvas: UIViewRepresentable {
    let view: FreshSnowView
    func makeUIView(context: Context) -> FreshSnowView { view }
    func updateUIView(_ v: FreshSnowView, context: Context) {}
}

/// Fresh snow on the phone (13.6), the site's game (site/games/fresh-snow) and Android's FreshSnowScreen.kt: one look,
/// fresh snow in a low morning sun, the same by day and by night. The snow or the frozen lake fills the screen; the
/// title and the number at the top, and the scenes, the kinds of snow and what to touch with at the bottom.
struct FreshSnowScreen: View {
    @Environment(\.ski) private var ski
    @Environment(\.accessibilityReduceMotion) private var still
    @Environment(\.dismiss) private var dismiss
    @State private var m: FreshModel?

    var body: some View {
        ZStack {
            Color(rgb: 0xE8F0F8).ignoresSafeArea()
            if let m { content(m) }
        }
        .toolbar(.hidden, for: .navigationBar)
        .onAppear {
            if m == nil { m = FreshModel(still: still, lang: ski.type.lang) }
            m?.synth.start()
        }
        .onDisappear { m?.gameEnd(); m?.synth.stop() }
    }

    private func content(_ m: FreshModel) -> some View {
        ZStack {
            FreshCanvas(view: m.view).ignoresSafeArea()
                .accessibilityLabel(m.lake ? S.gameFreshLakeCanvasLabel : S.gameFreshSnowCanvasLabel)
            VStack(spacing: 0) {
                top(m)
                Spacer()
                bar(m)
            }
            // the first hint, gone at the first touch
            if !m.touched {
                Text(S.gameFreshTip).font(ski.type.text(15, .bold)).foregroundStyle(.white).multilineTextAlignment(.center)
                    .padding(.horizontal, 16).padding(.vertical, 10).background(Color(rgb: 0x13233A, opacity: 0.82))
                    .frame(maxWidth: 320).padding(.bottom, 60).allowsHitTesting(false)
            }
        }
    }

    // the title, what the snow is like, and the number
    private func top(_ m: FreshModel) -> some View {
        let fg = m.lake ? Color.white : inkC
        let desc: String = if m.lake { S.gameFreshLakeDesc } else {
            switch m.kind {
            case .powder: S.gameFreshKindPowderDesc
            case .crust: S.gameFreshKindCrustDesc
            case .wet: S.gameFreshKindWetDesc
            }
        }
        return HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 0) {
                Text(S.gameFreshTitle).font(ski.type.display(38)).foregroundStyle(fg).shadow(color: .white.opacity(m.lake ? 0 : 1), radius: 0, y: 2)
                Text(desc).font(ski.type.text(12, .semibold)).foregroundStyle(fg)
            }
            .opacity(0.85)
            Spacer(minLength: 8)
            VStack(spacing: 0) {
                Text("\(m.stat)%").font(ski.type.display(26)).foregroundStyle(inkC).monospacedDigit()
                Text(m.lake ? S.gameFreshStatBroken : S.gameFreshStatTracks).font(ski.type.text(12.5, .bold)).foregroundStyle(inkC)
            }
            .padding(.horizontal, 10).padding(.vertical, 5).background(Color.white.opacity(0.85))
            .accessibilityElement(children: .combine)
        }
        .padding(.horizontal, 12).padding(.top, 12)
        .allowsHitTesting(false)
    }

    private func bar(_ m: FreshModel) -> some View {
        VStack(spacing: 8) {
            HStack(spacing: 6) {
                Button { dismiss() } label: {
                    Text(S.gameFreshBackToGames).font(ski.type.text(14, .bold)).foregroundStyle(.white).lineLimit(1).minimumScaleFactor(0.8)
                        .padding(.horizontal, 14).frame(minHeight: 44).background(Color(rgb: 0x13233A, opacity: 0.88))
                }
                .buttonStyle(.plain)
                pick(S.gameFreshSceneSnow, on: !m.lake, small: true) { m.scene(false) }
                pick(S.gameFreshSceneLake, on: m.lake, small: true) { m.scene(true) }
                SoundKey(label: S.gameFreshSound, background: Color(rgb: 0x13233A, opacity: 0.88))
            }
            if m.lake {
                HStack { pick("🧊", on: false, sub: S.gameFreshRefreeze) { m.view.refreeze() } }
            } else {
                HStack(spacing: 6) {
                    ForEach(FreshSnowKind.allCases, id: \.self) { k in
                        pick(kindName(k), on: m.kind == k, small: true) { m.kind = k; m.view.kind = k }
                    }
                }
                HStack(spacing: 6) {
                    ForEach(FreshSnowView.Tool.allCases, id: \.self) { t in
                        pick(toolIcon(t), on: m.tool == t, sub: toolName(t), wide: true) { m.tool = t; m.view.tool = t }
                    }
                    pick("❄️", on: false, sub: S.gameFreshNewSnow, wide: true) { m.view.newSnow() }
                }
            }
        }
        .padding(.horizontal, 12).padding(.top, 10).padding(.bottom, 12)
        .background(LinearGradient(colors: [Color(rgb: 0x13233A, opacity: 0), Color(rgb: 0x13233A, opacity: 0.35)], startPoint: .top, endPoint: .bottom)
            .ignoresSafeArea())
    }

    private func kindName(_ k: FreshSnowKind) -> String {
        switch k { case .powder: S.gameFreshKindPowder; case .crust: S.gameFreshKindCrust; case .wet: S.gameFreshKindWet }
    }
    private func toolIcon(_ t: FreshSnowView.Tool) -> String {
        switch t { case .hand: "☝️"; case .boot: "🥾"; case .ski: "🎿"; case .ball: "⚪"; case .cat: "🚜" }
    }
    private func toolName(_ t: FreshSnowView.Tool) -> String {
        switch t {
        case .hand: S.gameFreshToolHand; case .boot: S.gameFreshToolBoot; case .ski: S.gameFreshToolSki
        case .ball: S.gameFreshToolBall; case .cat: S.gameFreshToolCat
        }
    }

    /// A button of the bar (the site's .row button): white on the snow, ink when it is the one chosen.
    private func pick(_ text: String, on: Bool, small: Bool = false, sub: String? = nil, wide: Bool = false, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 0) {
                Text(text).font(sub != nil ? .system(size: 20) : ski.type.text(small ? 12.5 : 13.5, .bold)).lineLimit(1)
                if let sub {
                    Text(sub).font(ski.type.text(11.5, .bold)).multilineTextAlignment(.center)
                        .lineLimit(sub.trimmingCharacters(in: .whitespaces).contains(" ") ? 2 : 1).minimumScaleFactor(0.7)
                }
            }
            .foregroundStyle(on ? Color.white : inkC)
            .padding(.horizontal, wide ? 2 : 8).padding(.vertical, 2)
            .frame(minWidth: sub != nil ? 0 : 52, maxWidth: wide ? .infinity : nil, minHeight: small ? 44 : 52)
            .background(on ? inkC : Color.white.opacity(0.88))
            .shadow(color: .black.opacity(0.15), radius: 1, y: 1)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(sub ?? text)
        .accessibilityAddTraits(on ? .isSelected : [])
    }
}
