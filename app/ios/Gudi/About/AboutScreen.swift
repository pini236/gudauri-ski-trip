import SwiftUI
import GudiDomain

/// About and settings (13.7): placeholder until it lands.
struct AboutScreen: View {
    let mode: DayNight.Mode
    let onMode: () -> Void
    let onLang: (Lang?) -> Void
    var body: some View { Soon(title: S.commonAboutSettings) }
}
