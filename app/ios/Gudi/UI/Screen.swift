import SwiftUI

/// A screen's frame, iPhone style: the system's navigation bar with its back button and edge swipe, the title in the
/// sign voice (Karantina, or the language's display face), and the page's snow behind.
struct Screen<Content: View>: View {
    let title: String
    @ViewBuilder let content: Content
    @Environment(\.ski) private var ski

    var body: some View {
        content
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(ski.colors.snow)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    Text(title).font(ski.type.display(26)).foregroundStyle(ski.colors.ink).lineLimit(1)
                }
            }
            .toolbarBackground(ski.colors.snow, for: .navigationBar)
    }
}

/// A screen not built yet on iPhone (removed as each part lands).
struct Soon: View {
    let title: String
    @Environment(\.ski) private var ski
    var body: some View {
        Screen(title: title) {
            Text(title).font(ski.type.display(44)).foregroundStyle(ski.colors.muted)
        }
    }
}
