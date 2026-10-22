import SwiftUI
import UIKit

/// A game on the games page, as the site lists it (site/index.html, #gamesPage) and Android's GamesScreen.kt: its trail
/// colour, dark words on the light colours, its place in the site's order (which sets how far its sign reaches), and
/// the first one wider.
struct GameCard: Identifiable {
    let key: String
    let order: Int
    let color: Color
    let ink: Bool
    let name: String
    var thumb: String
    var id: String { key }

    /// The games the app has (13.6), in the site's order; round 20: their names and pictures, no tag or label.
    static var all: [GameCard] { [
        GameCard(key: "descent", order: 0, color: Color(rgb: 0x1F5FC4), ink: false, name: S.gamesDescentName, thumb: "descent"),
        GameCard(key: "school", order: 1, color: Color(rgb: 0x1B8A4C), ink: false, name: S.gamesSchoolName, thumb: "school"),
        GameCard(key: "fresh", order: 2, color: Color(rgb: 0x5B9BFF), ink: true, name: S.gamesFreshName, thumb: "fresh-snow"),
        GameCard(key: "merge", order: 3, color: SignColors.gold, ink: true, name: S.gamesMergeName, thumb: "merge"),
        GameCard(key: "snowball", order: 4, color: Color(rgb: 0x13233A), ink: false, name: S.gamesSnowballName, thumb: "snowball"),
    ] }
}

/// How far each sign reaches from the post, by its place in the site's list (.games-list li:nth-child).
private let reach: [CGFloat] = [0.97, 0.90, 0.84, 0.90, 0.80]

/// The games page (13.6), the site's #games of round 8 (GP2, decision 20): every game a trail sign in its colour on
/// one post, fresh snow on each, its picture with a play mark, and the sign leans a little under the finger.
struct GamesScreen: View {
    @Environment(AppModel.self) private var app
    @Environment(\.ski) private var ski

    var body: some View {
        Screen(title: S.navGames) {
            ScrollView {
                // round 20: no opening line, only the signs and their pictures
                GeometryReader { g in
                    let w = g.size.width - 34
                    VStack(alignment: .leading, spacing: 34) {
                        ForEach(Array(GameCard.all.enumerated()), id: \.element.id) { i, card in
                            GameSign(card: card, index: i, width: w * reach[min(card.order, reach.count - 1)]) { app.go(.game(card.key)) }
                        }
                    }
                    .padding(.leading, 34).padding(.top, 30)
                    .background(alignment: .topLeading) {
                        // the post at the start side, with a cap of snow (.games-list::before and ::after)
                        ZStack(alignment: .top) {
                            Rectangle().fill(ski.colors.ink).frame(width: 10).padding(.top, 6)
                            RoundedRectangle(cornerRadius: 8).fill(Color.black.opacity(0.25)).frame(width: 20, height: 14).offset(y: 2)
                            RoundedRectangle(cornerRadius: 8).fill(Color.white).frame(width: 20, height: 14)
                        }
                        .frame(width: 20).padding(.leading, 5)
                    }
                }
                .frame(height: Self.height)
                .padding(.horizontal, 12).padding(.top, 6).padding(.bottom, 32)
            }
        }
    }

    /// The post's height: the signs, the gaps and the snow above and below.
    private static var height: CGFloat { 122 + 4 * 96 + 4 * 34 + 60 }
}

private struct GameSign: View {
    let card: GameCard
    let index: Int
    let width: CGFloat
    let action: () -> Void
    @Environment(\.ski) private var ski
    @Environment(\.layoutDirection) private var dir

    var body: some View {
        let rtl = dir == .rightToLeft
        let feature = card.order == 0
        let shape = SignShape(arrow: 30, rtl: rtl)
        Button(action: action) {
            HStack(spacing: 0) {
                Thumb(name: card.thumb, width: feature ? 124 : 96)
                // round 20: the name only, no line under it
                Text(card.name)
                    .font(ski.type.display(feature ? 29 : 25))
                    .foregroundStyle(card.ink ? SignColors.ink : .white)
                    .lineLimit(2).minimumScaleFactor(0.7)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.leading, 10).padding(.trailing, 32).padding(.top, 8)
            }
            .frame(width: width, height: feature ? 122 : 96)
            .background(card.color)
            .clipShape(shape)
            .contentShape(shape)
            .shadow(color: Color(rgb: 0x13233A, opacity: 0.2), radius: 6, y: 2)
            .snow(seed: index + 7, arrow: 30)
        }
        .buttonStyle(SwingStyle(rtl: rtl))
        .accessibilityLabel(card.name)
    }
}

/// The sign swings on the post a little under the finger (the site's :active), not with reduced motion.
private struct SwingStyle: ButtonStyle {
    let rtl: Bool
    @Environment(\.accessibilityReduceMotion) private var still
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .rotationEffect(.degrees(configuration.isPressed && !still ? (rtl ? -0.6 : 0.6) : 0), anchor: rtl ? .trailing : .leading)
            .animation(.spring(response: 0.25, dampingFraction: 0.45), value: configuration.isPressed)
    }
}

/// The game's picture and a play mark pointing the way of reading (round 20: no label on it).
private struct Thumb: View {
    let name: String
    let width: CGFloat

    var body: some View {
        ZStack {
            Color(rgb: 0xD5DFE8)
            if let img = Thumb.image(name) {
                Image(uiImage: img).resizable().scaledToFill().frame(width: width).clipped()
            }
            ZStack {
                Circle().fill(Color(rgb: 0x13233A, opacity: 0.82))
                Image(systemName: "play.fill").font(.system(size: 11, weight: .bold)).foregroundStyle(.white).offset(x: 1)
                    .flipsForRightToLeftLayoutDirection(true)
            }
            .frame(width: 28, height: 28)
        }
        .frame(width: width)
        .frame(maxHeight: .infinity)
        .overlay(alignment: .trailing) { Rectangle().fill(Color.white.opacity(0.55)).frame(width: 4) }
        .clipped()
    }

    @MainActor private static var cache: [String: UIImage] = [:]
    @MainActor static func image(_ name: String) -> UIImage? {
        if let i = cache[name] { return i }
        guard let url = Bundle.main.url(forResource: name, withExtension: "webp", subdirectory: "Data/thumbs"),
              let d = try? Data(contentsOf: url), let i = UIImage(data: d) else { return nil }
        cache[name] = i
        return i
    }
}
