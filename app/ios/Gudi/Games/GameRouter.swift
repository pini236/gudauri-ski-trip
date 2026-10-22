import SwiftUI

/// One game, by its id as on Android and the site (descent, school, fresh, merge, snowball). The games are full screen
/// with their own way back; each opens here.
struct GameScreen: View {
    let id: String

    var body: some View {
        switch id {
        case "merge": MergeScreen()
        case "fresh": FreshSnowScreen()
        case "school": SchoolScreen()
        // the descent and the snowball fight: Games/Descent and Games/Snowball
        case "descent": Soon(title: S.gamesDescentName)
        case "snowball": Soon(title: S.gamesSnowballName)
        default: Soon(title: S.navGames)
        }
    }
}
