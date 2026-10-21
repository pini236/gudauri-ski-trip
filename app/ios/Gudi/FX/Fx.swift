import AVFoundation
import UIKit

/// The pass's recorded sounds (site/audio: ticket-tear, ticket-slide, ticket-land) and the taps of the haptics, as
/// Android's fx/Sounds.kt and fx/Haptics.kt. The switches in settings will gate them (13.7).
@MainActor
final class Fx {
    static let shared = Fx()
    private var players: [String: AVAudioPlayer] = [:]

    init() {
        // mixed with whatever plays, and silent when the phone is on silent, as a game's effects
        try? AVAudioSession.sharedInstance().setCategory(.ambient)
    }

    func play(_ name: String, volume: Float = 1) {
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

    func tick(_ strength: CGFloat = 0.5) { UIImpactFeedbackGenerator(style: .light).impactOccurred(intensity: strength) }
    func click(_ strength: CGFloat = 1) { UIImpactFeedbackGenerator(style: .medium).impactOccurred(intensity: strength) }
}
