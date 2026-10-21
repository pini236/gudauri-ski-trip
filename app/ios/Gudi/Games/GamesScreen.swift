import SwiftUI

/// The games page (13.6): placeholder until the games land.
struct GamesScreen: View {
    var body: some View { Soon(title: S.navGames) }
}

/// One game, by its id (descent, snowball, merge, school, freshsnow).
struct GameScreen: View {
    let id: String
    var body: some View { Soon(title: S.navGames) }
}
