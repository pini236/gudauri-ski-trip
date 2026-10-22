import AVFoundation
import UIKit

/// The pass's recorded sounds (site/audio: ticket-tear, ticket-slide, ticket-land) and the taps of the haptics, as
/// Android's fx/Sounds.kt and fx/Haptics.kt. The switches in settings gate them everywhere, the pass and the games
/// alike (13.7, Android's fx/FxPrefs.kt, the site's prefs.js): UserDefaults `sound` and `haptics`, both on by default.
@MainActor
final class Fx {
    static let shared = Fx()
    private var players: [String: AVAudioPlayer] = [:]

    init() {
        // mixed with whatever plays, and silent when the phone is on silent, as a game's effects
        try? AVAudioSession.sharedInstance().setCategory(.ambient)
    }

    /// Sound on (the settings' switch; on until it is turned off).
    static var soundOn: Bool { UserDefaults.standard.object(forKey: "sound") as? Bool ?? true }
    /// Haptics on (the settings' switch; on until it is turned off).
    static var hapticsOn: Bool { UserDefaults.standard.object(forKey: "haptics") as? Bool ?? true }

    func play(_ name: String, volume: Float = 1) {
        guard Self.soundOn else { return }
        let p = players[name] ?? {
            guard let url = Bundle.main.url(forResource: name, withExtension: "wav", subdirectory: "Data/audio"),
                  let p = try? AVAudioPlayer(contentsOf: url) else { return nil }
            p.prepareToPlay()
            players[name] = p
            return p
        }()
        guard let p else { return }
        p.volume = volume
        p.currentTime = 0
        p.play()
    }

    func tick(_ strength: CGFloat = 0.5) { guard Self.hapticsOn else { return }; UIImpactFeedbackGenerator(style: .light).impactOccurred(intensity: strength) }
    func click(_ strength: CGFloat = 1) { guard Self.hapticsOn else { return }; UIImpactFeedbackGenerator(style: .medium).impactOccurred(intensity: strength) }
}
