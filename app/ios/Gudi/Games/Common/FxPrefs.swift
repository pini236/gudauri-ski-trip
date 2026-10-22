import Foundation
import UIKit

/// Sound and vibration on or off (the settings, 13.7; the site's prefs.js; Android's fx/FxPrefs.kt): kept on the phone
/// under the keys "sound" and "haptics", both on by default, and they apply everywhere the games play or vibrate. The
/// settings screen (About) writes the same keys.
enum FxPrefs {
    static var sound: Bool { UserDefaults.standard.object(forKey: "sound") as? Bool ?? true }
    static var haptics: Bool { UserDefaults.standard.object(forKey: "haptics") as? Bool ?? true }
    static func set(_ key: String, _ on: Bool) { UserDefaults.standard.set(on, forKey: key) }
}

/// The games' taps (Android's fx/Haptics.kt): a tick (an edge biting, a hole of the perforation), a click, and a thud for
/// a landing. Off with the settings' switch.
@MainActor
enum GameHaptics {
    private static let light = UIImpactFeedbackGenerator(style: .light)
    private static let medium = UIImpactFeedbackGenerator(style: .medium)
    private static let heavy = UIImpactFeedbackGenerator(style: .heavy)

    static func tick(_ scale: CGFloat = 0.5) { if FxPrefs.haptics { light.impactOccurred(intensity: min(1, max(0.05, scale))) } }
    static func click(_ scale: CGFloat = 0.8) { if FxPrefs.haptics { medium.impactOccurred(intensity: min(1, max(0.05, scale))) } }
    static func thud(_ scale: CGFloat = 1) { if FxPrefs.haptics { heavy.impactOccurred(intensity: min(1, max(0.05, scale))) } }
}
