import SwiftUI

/// The games page (13.6): placeholder until the games land.
struct GamesScreen: View {
    var body: some View { Soon(title: S.navGames) }
}

/// One game, by its id (descent, snowball, merge, school, freshsnow). For the screenshots an id can carry a state
/// after a space ("descent play", "snowball end"; in the shots list "game:descent_play").
struct GameScreen: View {
    let id: String
    var body: some View {
        let parts = id.split(separator: " ", maxSplits: 1).map(String.init)
        let qa = parts.count > 1 ? parts[1] : nil
        switch parts.first ?? "" {
        case "descent": DescentScreen(qa: qa)
        case "snowball": SnowballScreen(qa: qa)
        default: Soon(title: S.navGames)
        }
    }
}
