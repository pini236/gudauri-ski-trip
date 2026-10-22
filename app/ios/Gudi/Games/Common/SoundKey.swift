import SwiftUI

/// The sound on and off inside a game, as the site's button in the descent and the snowball fight (.snd, A-41; Android's
/// SoundKey.kt): a dark square of 44 with the speaker. It is the settings' sound ("sound" in UserDefaults), so it holds
/// in every game and in the settings too.
struct SoundKey: View {
    let label: String
    var background = Color(rgb: 0x13233A, opacity: 0.7)
    var size = CGSize(width: 44, height: 44)
    @AppStorage("sound") private var on = true

    var body: some View {
        Button { on.toggle() } label: {
            Image(systemName: on ? "speaker.wave.2.fill" : "speaker.slash.fill")
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: size.width, height: size.height)
                .background(background)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
        .accessibilityValue(on ? "on" : "off")
    }
}

/// Back to the games page (the site's "→ All games"), the arrow pointing back in the reading direction.
struct GameBack: View {
    let text: String
    var color: Color
    @Environment(\.ski) private var ski
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        Button { dismiss() } label: {
            HStack(spacing: 4) {
                Image(systemName: "chevron.backward").font(.system(size: 16, weight: .semibold))
                Text(text.trimmingCharacters(in: CharacterSet(charactersIn: "→← "))).font(ski.type.text(15, .bold))
            }
            .foregroundStyle(color)
            .frame(minHeight: 44)
            .padding(.horizontal, 4)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}
