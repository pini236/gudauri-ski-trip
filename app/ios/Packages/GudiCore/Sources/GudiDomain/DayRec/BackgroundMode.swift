import Foundation

/// Whether an Info.plist declares the `location` background mode. Without it, turning on background location updates
/// throws at run time, so a build that does not declare it (1.0) must refuse to record (docs/ARCHITECTURE.md m-13).
public enum BackgroundMode {
    public static func declaresLocation(_ info: [String: Any]?) -> Bool {
        (info?["UIBackgroundModes"] as? [String])?.contains("location") ?? false
    }
}
