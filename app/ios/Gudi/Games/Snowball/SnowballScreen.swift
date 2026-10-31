import SwiftUI
import GudiDomain

/// The snowball fight on the iPhone (13.6), the site's game (site/games/snowball) and Android's SnowballScreen: you
/// behind your wall, the characters behind theirs; stand up and aim with a finger, let go to throw, and duck before
/// their ball arrives. A ladder of five rungs, each with three stars (to win, without being hit, the rung's goal); a
/// rung opens when the one before it has a star. No names (decision 55): coats and hats. `qa` ("play", "end") is for
/// the screenshots: straight into a fight (aiming), or won at once.
struct SnowballScreen: View {
    var qa: String? = nil
    @State private var m = SnowballModel()

    var body: some View {
        ZStack {
            SnowballField(m: m)
                .ignoresSafeArea()
                .gesture(DragGesture(minimumDistance: 0, coordinateSpace: .local)
                    .onChanged { v in m.touch(v.location) }
                    .onEnded { _ in m.release() }, including: m.phase == .play ? .all : .none)
                .accessibilityLabel(S.gameSnowballCanvasLabel)
            switch m.phase {
            case .menu: ArcadeShade(top: 0x9CC3E6) { SnowballMenu(m: m) }
            case .play: if let g = m.fight { SnowballPlay(m: m, g: g) }
            case .end: if let r = m.result { ArcadeShade(top: 0x9CC3E6) { SnowballEnd(m: m, r: r) } }
            }
        }
        .background(Color(rgb: 0x9CC3E6).ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .statusBarHidden(m.phase == .play)
        .onAppear {
            m.load()
            if !m.qaDone {
                m.qaDone = true
                switch qa {
                case "play": m.start(); m.qaAim = true
                case "end": m.start(); m.fight?.knockOut()
                default: break
                }
            }
            m.resume()
        }
        .onDisappear { m.leave() }
    }
}

/// What the snowball screen keeps: the choices (on the phone), the fight, and what it says.
@MainActor
@Observable
final class SnowballModel {
    enum Phase { case menu, play, end }
    var phase = Phase.menu
    var fight: SnowballFight?
    var result: SnowballFight.Result?
    var frame = 0
    var me = 5 { didSet { UserDefaults.standard.set(me, forKey: "snowball.me") } }
    var rung = 0 { didSet { UserDefaults.standard.set(rung, forKey: "snowball.rung") } }
    var starsV = 0
    var popText: String?; var popN = 0
    var hint = true
    /// The field's size in points (the fight's projection).
    var size: CGSize = .zero { didSet { place() } }
    /// For the screenshots: a finger held up in the middle, so the ring and the raised view show.
    @ObservationIgnored var qaAim = false
    @ObservationIgnored let painter = SnowballPainter()
    @ObservationIgnored private let ticker = ArcadeTicker()
    @ObservationIgnored let synth = Synth()
    @ObservationIgnored private var touching = false

    func load() {
        synth.start()
        let d = UserDefaults.standard
        me = min(max(d.object(forKey: "snowball.me") as? Int ?? 5, 0), Coats.count - 1)
        rung = min(max(d.object(forKey: "snowball.rung") as? Int ?? 0, 0), Rung.all.count - 1)
        ticker.onTick = { [weak self] dt in self?.tick(dt) }
    }

    func stars(_ i: Int) -> Int { Bests.level("snowball", i + 1) }
    func open(_ i: Int) -> Bool { i == 0 || stars(i - 1) > 0 }

    private func place() { if size.width > 0 { fight?.place(Float(size.width), Float(size.height), 1) } }

    func start() {
        result = nil; popText = nil; hint = true; touching = false
        fight = SnowballFight(rung: rung, me: me, out: self, still: Arcade.still)
        place()
        phase = .play
        started = Date()
        Telemetry.shared.event("game_start", ["game": "snowball", "level": rung + 1])
        ticker.start()
    }
    /// Usage statistics, as the site and Android: level = the rung, score = hits, completed = won.
    @ObservationIgnored private var started: Date?
    fileprivate func gameEnd(_ won: Bool, best: Bool) {
        guard let s = started else { return }
        started = nil
        Telemetry.shared.event("game_end", ["game": "snowball", "level": rung + 1, "score": fight?.stats.hits ?? 0,
            "seconds": Int(Date().timeIntervalSince(s).rounded()), "completed": won, "best": best])
    }
    func toMenu() { if phase == .play { gameEnd(false, best: false) }; ticker.stop(); fight = nil; phase = .menu; starsV += 1 }
    @ObservationIgnored var qaDone = false
    /// Back on the screen after it went away: the clock goes on.
    func resume() { if phase == .play || (phase == .end && fight != nil) { ticker.start() } }
    func leave() { if phase == .play { gameEnd(false, best: false) }; ticker.stop(); synth.stop() }

    func touch(_ p: CGPoint) {
        guard let g = fight else { return }
        if touching { g.move(Float(p.x), Float(p.y)) } else { touching = true; g.down(Float(p.x), Float(p.y)) }
    }
    func release() { touching = false; fight?.up() }

    private func tick(_ dt: Float) {
        guard let g = fight else { return }
        if qaAim, g.aim == nil, size.width > 0 { g.down(Float(size.width * 0.5), Float(size.height * 0.5)) }
        g.update(dt)
        if hint && g.t > 7 { hint = false }
        frame &+= 1
        if g.state == .end && g.hats.allSatisfy({ $0.y <= 0 }) && g.bits.isEmpty { ticker.stop() }
    }
}

extension SnowballModel: @preconcurrency SnowballOut {
    func hp() { frame &+= 1 }
    func pop(_ w: SnowballWord) { popText = SnowballText.word(w); popN += 1 }
    func noise(_ d: Double, _ v: Float, _ f: Double, _ type: SnowballFight.Noise) {
        synth.grain(0, d, v, type == .low ? .low : type == .high ? .high : .band, f, 1)
    }
    func tone(_ f: Double, _ d: Double, _ v: Float, _ e: Double) { synth.tone(0, f, f * e, d, v, tri: true) }
    func buzz(_ strong: Bool) { if strong { GameHaptics.thud(0.8) } else { GameHaptics.click(0.6) } }
    func finished(_ r: SnowballFight.Result) {
        Bests.setLevel("snowball", rung + 1, r.stars)
        // the record for the group's table: the most hits (the site's, Android's)
        let record = Bests.ended("snowball", level: nil, score: r.hits)
        gameEnd(r.won, best: record)
        result = r; starsV += 1; phase = .end
    }
}

/// The snowball fight's words, from the app's texts.
enum SnowballText {
    static func word(_ w: SnowballWord) -> String {
        switch w {
        case .arenaHotelYard: S.gameSnowballArenaHotelYard
        case .arenaLift: S.gameSnowballArenaLift
        case .arenaDeepSnow: S.gameSnowballArenaDeepSnow
        case .arenaRidge: S.gameSnowballArenaRidge
        case .arenaSummit: S.gameSnowballArenaSummit
        case .goalWinUntouched: S.gameSnowballGoalWinUntouched
        case .goalHat: S.gameSnowballGoalHat
        case .goalAccuracy: S.gameSnowballGoalAccuracy
        case .goalTwoHats: S.gameSnowballGoalTwoHats
        case .popMiss: S.gameSnowballPopMiss
        case .popOverYou: S.gameSnowballPopOverYou
        case .popHatOff: S.gameSnowballPopHatOff
        case .popHead: S.gameSnowballPopHead
        case .popHit: S.gameSnowballPopHit
        case .popOut: S.gameSnowballPopOut
        case .popGotHit: S.gameSnowballPopGotHit
        case .won: S.gameSnowballWon
        }
    }
}

private struct SnowballField: View {
    let m: SnowballModel
    var body: some View {
        let _ = m.frame
        let pano = Pano.image("pano-wide-noon")
        let img = pano.map { Image(uiImage: $0) }
        let aspect = pano.map { $0.size.width / max(1, $0.size.height) } ?? 0
        let g = m.fight
        let still = Arcade.still
        let painter = m.painter
        GeometryReader { geo in
            Canvas { ctx, size in painter.draw(&ctx, size, fight: g, pano: img, panoAspect: aspect, still: still) }
                .onAppear { m.size = geo.size }
                .onChange(of: geo.size) { m.size = geo.size }
        }
    }
}

/// Characters as on the field: `coats` side by side, feet at the bottom.
private struct SnowballCharacters: View {
    let coats: [Int]
    let metre: CGFloat
    var body: some View {
        Canvas { ctx, size in
            let step = 0.7 * metre, x0 = size.width / 2 - step * CGFloat(coats.count - 1) / 2
            for (k, i) in coats.enumerated() { SnowballPainter.character(&ctx, x0 + CGFloat(k) * step, size.height - 2, metre, Arcade.coat(i), 0) }
        }
    }
}

// ---------- the menu and the end ----------

private struct SnowballMenu: View {
    let m: SnowballModel
    @Environment(\.ski) private var ski

    var body: some View {
        let _ = m.starsV
        GameBack(text: S.gameSnowballBackToGames, color: .white)
        ArcadeTitle(text: S.gameSnowballTitle)
        Text(S.gameSnowballIntro).font(ski.type.text(14)).foregroundStyle(.white).lineSpacing(4).fixedSize(horizontal: false, vertical: true)
        ArcadeH2(text: S.gameSnowballYourCoat)
        // the six coats, three in a row, each with its character
        VStack(spacing: 8) {
            ForEach([0, 3], id: \.self) { row in
                HStack(spacing: 8) {
                    ForEach(row..<row + 3, id: \.self) { i in
                        Button { m.me = i } label: {
                            SnowballCharacters(coats: [i], metre: 26).frame(width: 48, height: 56)
                                .frame(maxWidth: .infinity, minHeight: 56, alignment: .bottom)
                                .background(i == m.me ? Color.white : Color.white.opacity(0.9))
                                .overlay { if i == m.me { Rectangle().strokeBorder(Arcade.accent, lineWidth: 3) } }
                                .padding(.top, 8)
                                .background(alignment: .top) { Arcade.coat(i).frame(height: 8) }
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(Arcade.coatName(i))
                        .accessibilityAddTraits(i == m.me ? [.isSelected] : [])
                    }
                }
            }
        }
        .accessibilityElement(children: .contain).accessibilityLabel(S.gameSnowballChooseCoatLabel)
        ArcadeH2(text: S.gameSnowballLadder)
        VStack(spacing: 8) {
            ForEach(Rung.all.indices, id: \.self) { i in SnowballRungRow(i: i, foes: Rung.foes(i, me: m.me), open: m.open(i), on: i == m.rung, stars: m.stars(i)) { m.rung = i } }
        }
        HStack(spacing: 6) {
            ArcadeHow(title: S.gameSnowballHowDragTitle, text: S.gameSnowballHowDragText)
            ArcadeHow(title: S.gameSnowballHowReleaseTitle, text: S.gameSnowballHowReleaseText)
            ArcadeHow(title: S.gameSnowballHowDuckTitle, text: S.gameSnowballHowDuckText)
        }
        .fixedSize(horizontal: false, vertical: true)
        ArcadeGo(text: S.gameSnowballGo) { m.start() }
    }
}

private struct SnowballRungRow: View {
    let i: Int
    let foes: [Int]
    let open: Bool
    let on: Bool
    let stars: Int
    let action: () -> Void
    @Environment(\.ski) private var ski

    var body: some View {
        let r = Rung.all[i]
        Button(action: action) {
            HStack(spacing: 12) {
                Text("\(i + 1)").font(ski.type.display(34)).foregroundStyle(Arcade.muted)
                VStack(alignment: .leading, spacing: 1) {
                    HStack {
                        Text(SnowballText.word(r.arena)).font(ski.type.display(24)).foregroundStyle(Arcade.ink).lineLimit(1).minimumScaleFactor(0.7)
                        Spacer(minLength: 4)
                        if open {
                            Text(String(repeating: "★", count: stars) + String(repeating: "☆", count: 3 - stars)).font(.system(size: 16)).tracking(1).foregroundStyle(Arcade.accent)
                        } else {
                            Image(systemName: "lock.fill").font(.system(size: 14)).foregroundStyle(Arcade.accent)
                        }
                    }
                    Text(S.gameSnowballRungWind(n: "\(r.windMax)") + " · ★ " + SnowballText.word(r.goal)).font(ski.type.text(12)).foregroundStyle(Arcade.muted)
                        .fixedSize(horizontal: false, vertical: true)
                }
                // who stands behind the far wall on this rung
                SnowballCharacters(coats: foes, metre: 15).frame(width: CGFloat(12 + 11 * foes.count), height: 32)
            }
            .padding(.leading, 22).padding(.trailing, 12).padding(.vertical, 8)
            .frame(maxWidth: .infinity, minHeight: 58, alignment: .leading)
            .background(Color.white)
            .overlay(alignment: .leading) { Arcade.coat(foes.first ?? 0).frame(width: 10) }
            .overlay { if on { Rectangle().strokeBorder(Arcade.accent, lineWidth: 3) } }
            .opacity(open ? 1 : 0.55)
        }
        .buttonStyle(.plain)
        .disabled(!open)
        // read as one: "2. <arena>", its stars or locked, and the wind (not the stars and boxes as signs)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(i + 1). \(SnowballText.word(r.arena))")
        .accessibilityValue(open ? S.gameDescentStarsLabel(count: stars) : S.appA11yLocked)
        .accessibilityHint(S.gameSnowballRungWind(n: "\(r.windMax)"))
        .accessibilityAddTraits(on ? [.isButton, .isSelected] : [.isButton])
    }
}

private struct SnowballEnd: View {
    let m: SnowballModel
    let r: SnowballFight.Result
    @Environment(\.ski) private var ski

    var body: some View {
        let rung = Rung.all[m.rung]
        ArcadeH2(text: r.won ? S.gameSnowballWon : S.gameSnowballLost)
        VStack(alignment: .leading, spacing: 8) {
            Text("\(m.rung + 1). \(SnowballText.word(rung.arena))").font(ski.type.text(12, .bold)).foregroundStyle(Arcade.muted).textCase(.uppercase)
            // hits : taken, as on the site (numbers read left to right in every language)
            Text("\(r.hits) : \(r.taken)").font(ski.type.display(58)).foregroundStyle(Arcade.ink).environment(\.layoutDirection, .leftToRight)
            HStack(spacing: 4) { ForEach(0..<3, id: \.self) { k in Text("★").font(.system(size: 30)).foregroundStyle(r.goals[k] ? Arcade.accent : Arcade.rule) } }
                .accessibilityElement().accessibilityLabel(S.gameDescentStarsLabel(count: r.stars))
            HStack(spacing: 6) {
                ArcadeStat(label: S.gameSnowballStatHits, value: "\(r.hits)")
                ArcadeStat(label: S.gameSnowballStatHats, value: "\(r.hats)")
                ArcadeStat(label: S.gameSnowballStatAccuracy, value: "\(r.thrown > 0 ? Int((Float(r.hits) * 100 / Float(r.thrown)).rounded()) : 0)%")
            }
            .padding(.top, 8).overlay(alignment: .top) { Arcade.rule.frame(height: 1) }
            ArcadeGoals(lines: [S.gameSnowballGoalWin, S.gameSnowballGoalNoHitTaken, SnowballText.word(rung.goal)], ok: r.goals)
        }
        .padding(14)
        .background(Color.white)
        .shadow(color: .black.opacity(0.3), radius: 10, y: 4)
        .rotationEffect(.degrees(-2))
        .padding(.vertical, 6)
        ArcadeGo(text: S.gameSnowballAgain) { m.start() }
        HStack(spacing: 8) {
            if r.won && m.rung < Rung.all.count - 1 {
                ArcadeOutline(text: S.gameSnowballNextLevel) { m.rung += 1; m.start() }
            } else { Color.clear.frame(maxWidth: .infinity, minHeight: 48) }
            ArcadeOutline(text: S.gameSnowballToMenu) { m.toMenu() }
        }
    }
}

// ---------- in the fight: your balls on the left, the rung and the wind in the middle, theirs on the right ----------

private struct SnowballPlay: View {
    let m: SnowballModel
    let g: SnowballFight
    @Environment(\.ski) private var ski

    var body: some View {
        let _ = m.frame
        ZStack {
            // laid out as the site's (direction: ltr): you on the left, they on the right, in every language
            HStack(alignment: .top) {
                SnowballTag(coat: g.me, hp: g.myHp, max: 3).frame(maxWidth: .infinity, alignment: .leading)
                VStack(spacing: 4) {
                    Text("\(g.rungIndex + 1) · \(SnowballText.word(g.rung.arena))").font(ski.type.display(24)).foregroundStyle(Arcade.ink).lineLimit(1)
                        .padding(.horizontal, 12).padding(.vertical, 3).background(Arcade.accent)
                    let w = g.wind
                    Text(abs(w) < 0.25 ? S.gameSnowballNoWind : S.gameSnowballWind(n: Self.fmt(abs(w)), arrow: w > 0 ? "⟶" : "⟵"))
                        .font(ski.type.text(12.5, .bold)).foregroundStyle(Arcade.ink)
                        .padding(.horizontal, 10).padding(.vertical, 3).background(Color.white.opacity(0.88))
                }
                .fixedSize()
                VStack(alignment: .trailing, spacing: 4) {
                    ForEach(g.foes.indices, id: \.self) { k in SnowballTag(coat: g.foes[k].coat, hp: g.foes[k].hp, max: g.rung.hp) }
                }
                .frame(maxWidth: .infinity, alignment: .trailing)
            }
            .padding(.horizontal, 12).padding(.top, 10)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            .allowsHitTesting(false)
            ArcadePop(text: m.popText, n: m.popN, at: 0.28, maxSize: 58)
            // how to play, at the bottom, until seven seconds into the fight; clear of the keys in the corners
            Text(S.gameSnowballHint).font(ski.type.text(14, .bold)).foregroundStyle(Arcade.ink).fixedSize(horizontal: false, vertical: true)
                .padding(.horizontal, 12).padding(.vertical, 7)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color.white.opacity(0.92))
                .padding(.horizontal, 64).padding(.bottom, 16)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
                .opacity(m.hint ? 1 : 0)
                .animation(.easeOut(duration: 0.5), value: m.hint)
                .allowsHitTesting(false)
            // the sound at the bottom right, as the site's (A-41), and the way out at the bottom left
            HStack {
                ArcadeExitKey(label: S.gameSnowballToMenu) { m.toMenu() }
                Spacer()
                SoundKey(label: S.gameSnowballSound)
            }
            .padding(.horizontal, 12).padding(.bottom, 14)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
        }
        .environment(\.layoutDirection, .leftToRight)
    }

    static func fmt(_ v: Float) -> String { v == v.rounded() ? "\(Int(v))" : "\(v)" }
}

/// A coat's tag (the site's .tag): its colour on top, and the balls left, full or dashed.
private struct SnowballTag: View {
    let coat: Int
    let hp: Int
    let max: Int
    var body: some View {
        HStack(spacing: 3) {
            ForEach(0..<max, id: \.self) { k in
                Canvas { ctx, size in
                    let r = min(size.width, size.height) / 2, c = CGPoint(x: size.width / 2, y: size.height / 2)
                    let e = Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: 2 * r, height: 2 * r))
                    if k < hp {
                        ctx.fill(e, with: .color(.white))
                        ctx.fill(Path(ellipseIn: CGRect(x: c.x - r * 0.9, y: c.y + r * 0.25 - r * 0.9, width: r * 1.8, height: r * 1.8)), with: .color(Color(rgb: 0xC9D8E7, opacity: 0.6)))
                        ctx.stroke(Path(ellipseIn: CGRect(x: c.x - r + 0.5, y: c.y - r + 0.5, width: 2 * r - 1, height: 2 * r - 1)), with: .color(Arcade.ink.opacity(0.3)), lineWidth: 1)
                    } else {
                        ctx.stroke(Path(ellipseIn: CGRect(x: c.x - r + 0.5, y: c.y - r + 0.5, width: 2 * r - 1, height: 2 * r - 1)), with: .color(.white.opacity(0.8)), style: StrokeStyle(lineWidth: 1, dash: [3, 3]))
                    }
                }
                .frame(width: 14, height: 14)
            }
        }
        .padding(.horizontal, 10).padding(.vertical, 6)
        .background(Arcade.ink.opacity(0.86))
        .padding(.top, 5)
        .background(alignment: .top) { Arcade.coat(coat).frame(height: 5) }
        .accessibilityElement().accessibilityLabel("\(Arcade.coatName(coat)) \(hp)/\(max)")
    }
}
