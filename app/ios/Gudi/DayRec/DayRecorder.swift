import Foundation
import CoreLocation
import CoreMotion
import GudiDomain

/// The ski day's recorder on iPhone (decision 76; docs/ARCHITECTURE.md m-8): the GPS and the accelerometer into the
/// day's raw journal on disk, and nothing else. What the day was is the analyser's (after the reference tool and its
/// cases in tools/dayrec are approved, m-8 condition 4); the screens wait for round 29 in the canvas.
///
/// - Starts only from a tap while the app is in front, with "while using" location (no "always"); the system's blue
///   pill shows while it records in the background (condition 2; `showsBackgroundLocationIndicator`).
/// - Precise location only: with the approximate one it asks once for precise location for this day, and does not
///   record without it (m-8 addition a).
/// - No barometer and no motion activity: the raw accelerometer needs no permission, and the recording is complete
///   without them (m-8 addition b). The pressure field stays empty.
/// - **Nothing leaves the phone:** no reading is sent, measured or logged; the folder is out of the iCloud backup.
@MainActor
@Observable
final class DayRecorder: NSObject, CLLocationManagerDelegate {
    enum State: Equatable {
        case idle
        /// The system has not asked yet: the explaining sheet, then `ask()`.
        case asking
        case denied
        /// Only approximate location was allowed, also after asking for precise: no recording (condition 2).
        case approximate
        case recording(since: Date, points: Int)
        /// The last day never got its stop (the app was gone): "the day was cut, go on?"
        case interrupted(since: Date, points: Int)
        /// The day reached 10 MB and stopped writing.
        case full
    }

    private(set) var state: State = .idle
    /// The last reading, for the live screen (in memory only).
    private(set) var last: DayJournal.Point?

    @ObservationIgnored private let store: DayStore?
    @ObservationIgnored private var writer: DayWriter?
    @ObservationIgnored private var points = 0
    @ObservationIgnored private var since = Date()
    @ObservationIgnored private let lm = CLLocationManager()
    @ObservationIgnored private let motion = CMMotionManager()
    @ObservationIgnored private let motionQueue = OperationQueue()
    /// The acceleration's magnitudes since the last reading: their variance goes into the point (m-8, answer 3).
    @ObservationIgnored private let accel = AccelWindow()
    @ObservationIgnored private var pendingStart = false

    static var folder: URL {
        FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("Days", isDirectory: true)
    }

    override init() {
        store = try? DayStore(dir: Self.folder)
        super.init()
        lm.delegate = self
        // m-8, answer 1: the best the receiver gives, every reading, never paused by the system
        lm.desiredAccuracy = kCLLocationAccuracyBestForNavigation
        lm.distanceFilter = kCLDistanceFilterNone
        lm.activityType = .fitness
        lm.pausesLocationUpdatesAutomatically = false
        motionQueue.maxConcurrentOperationCount = 1
        if let day = store?.interrupted()?.1 {
            state = .interrupted(since: Date(timeIntervalSince1970: Double(day.header.start) / 1000), points: day.points.count)
        }
    }

    var recording: Bool { if case .recording = state { return true }; return false }

    /// "Starting the day": asks first when it must; the answer comes back to the delegate.
    func start() {
        guard writer == nil, store != nil else { return }
        switch lm.authorizationStatus {
        case .notDetermined: state = .asking
        case .denied, .restricted: state = .denied
        default: precise()
        }
    }

    /// After the explaining sheet: the system's question.
    func ask() { pendingStart = true; lm.requestWhenInUseAuthorization() }

    /// Precise location for this day, or no recording.
    private func precise() {
        guard lm.accuracyAuthorization == .reducedAccuracy else { begin(); return }
        lm.requestTemporaryFullAccuracyAuthorization(withPurposeKey: "DayRecording") { [weak self] _ in
            Task { @MainActor in
                guard let self else { return }
                if self.lm.accuracyAuthorization == .fullAccuracy { self.begin() } else { self.state = .approximate }
            }
        }
    }

    private func begin() {
        guard let store else { return }
        let now = Date()
        let header = DayJournal.Header(tz: "Asia/Tbilisi", resort: "gudauri", device: Self.model, start: Self.ms(now))
        guard let w = try? store.begin(header) else { return }
        writer = w; points = 0; since = now
        run()
    }

    /// Goes on with the day that was cut.
    func resume() {
        guard let store, let cut = store.interrupted(), let w = try? store.resume(cut.0, at: Self.ms(Date())) else { return }
        let day = cut.1
        writer = w; points = day.points.count
        since = Date(timeIntervalSince1970: Double(day.header.start) / 1000)
        switch lm.authorizationStatus {
        case .authorizedWhenInUse, .authorizedAlways: run()
        default: try? w.close(Self.ms(Date()), reason: "user"); writer = nil; start()
        }
    }

    /// Closes the cut day as it is, without going on.
    func dismissInterrupted() {
        guard let store, let cut = store.interrupted(), let w = try? store.resume(cut.0, at: Self.ms(Date())) else { return }
        let day = cut.1
        try? w.close(day.points.last?.t ?? day.header.start, reason: "crash_recovery")
        state = .idle
    }

    private func run() {
        lm.allowsBackgroundLocationUpdates = true
        lm.showsBackgroundLocationIndicator = true
        lm.startUpdatingLocation()
        if motion.isAccelerometerAvailable {
            motion.accelerometerUpdateInterval = 1.0 / 25
            let w = accel
            motion.startAccelerometerUpdates(to: motionQueue) { d, _ in
                guard let a = d?.acceleration else { return }
                w.add((a.x * a.x + a.y * a.y + a.z * a.z).squareRoot())
            }
        }
        state = .recording(since: since, points: points)
    }

    /// The stop: user, idle or outside (the measurement contract's reasons).
    func stop(reason: String = "user") {
        lm.stopUpdatingLocation()
        lm.allowsBackgroundLocationUpdates = false
        motion.stopAccelerometerUpdates()
        try? writer?.close(Self.ms(Date()), reason: reason)
        writer = nil; last = nil
        state = .idle
    }

    /// Lines to the disk now (going to the background); the 30-second rule covers the rest.
    func flush() { try? writer?.sync(now: Self.ms(Date())) }

    nonisolated func locationManagerDidChangeAuthorization(_ m: CLLocationManager) {
        let s = m.authorizationStatus
        Task { @MainActor in
            guard pendingStart else { return }
            switch s {
            case .authorizedWhenInUse, .authorizedAlways: pendingStart = false; precise()
            case .denied, .restricted: pendingStart = false; state = .denied
            default: break
            }
        }
    }

    nonisolated func locationManager(_ m: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        // to journal points here, so only plain values cross to the main actor
        let ps = locations.filter { $0.horizontalAccuracy >= 0 }.map { l in
            DayJournal.Point(
                t: Self.ms(l.timestamp), lat: l.coordinate.latitude, lon: l.coordinate.longitude,
                alt: l.verticalAccuracy >= 0 ? l.altitude : nil,
                spd: l.speed >= 0 ? l.speed : nil, sacc: l.speedAccuracy >= 0 ? l.speedAccuracy : nil,
                hacc: l.horizontalAccuracy, vacc: l.verticalAccuracy >= 0 ? l.verticalAccuracy : nil,
                crs: l.course >= 0 ? l.course : nil)
        }
        Task { @MainActor in self.write(ps) }
    }

    private func write(_ ps: [DayJournal.Point]) {
        guard let writer, !ps.isEmpty else { return }
        let mv = accel.drain()
        for var p in ps {
            p.mv = mv.map { $0 * 9.80665 * 9.80665 }   // g² to (m/s²)², the reference tool's unit
            if !writer.append(.point(p), now: Self.ms(Date())) {
                stop(reason: "user"); state = .full; return
            }
            points += 1
            last = p
        }
        state = .recording(since: since, points: points)
    }

    nonisolated static func ms(_ d: Date) -> Int64 { Int64((d.timeIntervalSince1970 * 1000).rounded()) }

    /// The phone's model ("iPhone15,2"), for calibration; never a name or an id.
    static var model: String {
        var s = utsname(); uname(&s)
        return withUnsafeBytes(of: &s.machine) { String(decoding: $0.prefix { $0 != 0 }, as: UTF8.self) }
    }
}

/// The accelerometer's magnitudes between two readings, filled on the motion queue and drained on the main actor.
private final class AccelWindow: @unchecked Sendable {
    private let lock = NSLock()
    private var n = 0.0, sum = 0.0, sq = 0.0

    func add(_ g: Double) { lock.lock(); n += 1; sum += g; sq += g * g; lock.unlock() }

    /// The variance since the last drain (g²), nil without samples.
    func drain() -> Double? {
        lock.lock(); defer { n = 0; sum = 0; sq = 0; lock.unlock() }
        guard n > 1 else { return nil }
        let mean = sum / n
        return max(0, sq / n - mean * mean)
    }
}
