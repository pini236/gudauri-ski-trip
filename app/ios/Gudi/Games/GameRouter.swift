import SwiftUI

/// One game, by its id as on Android and the site (descent, school, fresh, merge, snowball). The games are full screen
/// with their own way back; each opens here.
struct GameScreen: View {
    let id: String

    var body: some View {
        // for the screenshots an id can carry a state after a space ("descent play", "snowball end"; "game:descent_play")
        let parts = id.split(separator: " ", maxSplits: 1).map(String.init)
        let qa = parts.count > 1 ? parts[1] : nil
        switch parts.first ?? "" {
        case "merge": MergeScreen()
        case "fresh": FreshSnowScreen()
        case "school": SchoolScreen()
        // the descent and the snowball fight: Games/Descent and Games/Snowball
        case "descent": DescentScreen(qa: qa)
        case "snowball": SnowballScreen(qa: qa)
        default: Soon(title: S.navGames)
        }
    }
}
