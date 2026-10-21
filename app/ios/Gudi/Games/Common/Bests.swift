import Foundation

/// Each game's high score on this phone (the site keeps them in the browser; Android's game/Bests.kt), cleared by the
/// settings' "reset the high scores". `offer` keeps a higher one and says whether it was. The group's table gets it too
/// (submit_score, only the best counts there): `Bests.onRecord` is the hook the group's part sets, and `unsent` /
/// `markSent` say what the table has not had yet.
///
/// Stored in UserDefaults as dictionaries under Android's names ("bests", "bests_school", "bests_levels_<game>",
/// "bests_sent"). What a game keeps on the phone besides its record (a saved board, best times, ghosts, choices) goes
/// under keys that start with "<game>." (e.g. "merge.save", "fresh.lake_best"), so the reset clears it too.
@MainActor
enum Bests {
    private static let prefs = "bests"
    private static let levels = "bests_school"
    private static let stars = "bests_levels_"
    private static let sent = "bests_sent"
    private static let levelGames = ["snowball", "descent"]

    /// The games with a table in the group, by their key on the server and the site (account.js GAMES).
    static let games = ["descent", "school", "fresh", "snowball", "merge"]

    /// Called with (game, score) when a game sets a new record on this phone: the group's part sends it to the table.
    static var onRecord: ((String, Int) -> Void)?

    private static func dict(_ name: String) -> [String: Int] { UserDefaults.standard.dictionary(forKey: name) as? [String: Int] ?? [:] }
    private static func put(_ name: String, _ key: String, _ v: Int) {
        var d = dict(name); d[key] = v; UserDefaults.standard.set(d, forKey: name)
    }

    static func get(_ game: String) -> Int { dict(prefs)[game] ?? 0 }

    @discardableResult
    static func offer(_ game: String, _ score: Int) -> Bool {
        if score <= get(game) { return false }
        put(prefs, game, score)
        onRecord?(game, score)
        return true
    }

    /// The end of a game (game_end), as the site's telemetry.js best(): the ski school counts its stars, the best of each
    /// lesson added up; every other game its best score. True when it is a new record (game_end's `best`).
    @discardableResult
    static func ended(_ game: String, level: String?, score: Int) -> Bool {
        var s = max(0, score)
        if game == "school", let level {
            if s > dict(levels)[level] ?? 0 { put(levels, level, s) }
            s = dict(levels).values.reduce(0, +)
        }
        return offer(game, s)
    }

    /// The ski school's stars in a lesson (`level` its number, from 1): the best of it, and what opens the next one.
    static func lesson(_ level: Int) -> Int { dict(levels)[String(level)] ?? 0 }

    /// For the screenshots: every lesson with a star, so all of them are open.
    static func openAllLessons() { for i in 1...7 { put(levels, String(i), max(1, lesson(i))) } }

    /// The stars of each level in the other games with levels (the snowball fight's rungs, the descent's runs): the
    /// best of each, which opens the next one. They do not add up to the record.
    static func level(_ game: String, _ level: Int) -> Int { dict(stars + game)[String(level)] ?? 0 }

    /// Keeps `n` stars for `level` when more than before; true when it was.
    @discardableResult
    static func setLevel(_ game: String, _ level: Int, _ n: Int) -> Bool {
        if n <= self.level(game, level) { return false }
        put(stars + game, String(level), n)
        return true
    }

    /// The settings' reset, as the site's (every game key in the browser, GUD_GAME_KEYS): the records, the stars, the
    /// best times and ghosts, the saved board, the choices, and what was sent to the group.
    static func reset() {
        let d = UserDefaults.standard
        for k in [prefs, levels, sent] + levelGames.map({ stars + $0 }) { d.removeObject(forKey: k) }
        for k in d.dictionaryRepresentation().keys where games.contains(where: { k.hasPrefix($0 + ".") }) { d.removeObject(forKey: k) }
    }

    static func all() -> [String: Int] { dict(prefs) }

    /// The high scores the group's table has not had yet.
    static func unsent() -> [String: Int] {
        let s = dict(sent)
        return all().filter { $0.value > (s[$0.key] ?? 0) }
    }

    static func markSent(_ game: String, _ score: Int) { put(sent, game, score) }
}
