import SwiftUI

#if DAYREC
/// The ski day in one tap from the map (round 29): a dark bar with the red dot, over the mountain and above the keys,
/// only when no run, lift or list is open. Android's `RecordButton`.
struct RecordSign: View {
    let action: () -> Void
    @Environment(\.ski) private var ski

    var body: some View {
        let c = ski.colors
        Button(action: action) {
            HStack(spacing: 14) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(S.appRecSign).font(ski.type.display(30)).foregroundStyle(c.paper).lineLimit(1)
                    Text("\(S.appRecOne) · \(S.appRecAll)").font(ski.type.text(12)).foregroundStyle(c.paper.opacity(0.85)).lineLimit(1)
                }
                Spacer(minLength: 0)
                RecDot().frame(width: 44, height: 44)
            }
            .padding(.horizontal, 16).padding(.vertical, 8)
            .frame(maxWidth: .infinity, minHeight: 64)
            .background(c.ink)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(S.appRecSign)
    }
}

/// The red record circle with its ring.
struct RecDot: View {
    var body: some View {
        Circle().fill(Color(rgb: 0xE5322D)).padding(5)
            .overlay(Circle().stroke(Color(rgb: 0xE5322D).opacity(0.53), lineWidth: 2))
    }
}
#endif
