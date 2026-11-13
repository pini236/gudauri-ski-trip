import Foundation
import GudiDomain

#if DAYREC
/// The words of a moment (decision 76; `docs/handoff/2026-10-30-dayrec-moments-copy/README.md`). The engine writes no
/// text: a moment is a key and numbers, and this turns it into the keys of `i18n/strings.json` (`moment.<...>`) and the
/// values of their placeholders. Android's `dayrec/ui/MomentText.kt`; here the placeholders are filled by name
/// (`Strings.text`), so there is no table of their order.
///
/// Nothing here reads a position, and nothing says a speed is a goal (a speed is a fact the skier recorded for their own eyes).
enum MomentText {
    /// A text key (without the variant number), its values by the name of the placeholder, and the count of a plural.
    struct Part {
        let key: String
        var args: [String: String] = [:]
        var qty: Int?
    }

    /// The title (when the moment has one) and the lines under it.
    struct Plan {
        var title: Part?
        var lines: [Part]
    }

    // MARK: the numbers, as the copy asks for them

    static func time(_ t: Int64, _ zone: TimeZone) -> String {
        var c = Calendar(identifier: .gregorian); c.timeZone = zone
        let d = Date(timeIntervalSince1970: Double(t) / 1000)
        return String(format: "%02d:%02d", c.component(.hour, from: d), c.component(.minute, from: d))
    }

    /// One decimal, in the decimal mark of the language (Russian: a comma).
    static func dec(_ v: Double) -> String {
        let s = String(format: "%.1f", v)
        return Strings.shared.lang == .ru ? s.replacingOccurrences(of: ".", with: ",") : s
    }

    /// "2.9" and "3" (a whole number has no ".0"), as the copy README says for {times}.
    static func times(_ v: Double) -> String { v == v.rounded() ? String(Int(v)) : dec(v) }

    /// The value of a record or a day's sum in the unit its line says: kilometres for a distance, a whole number otherwise.
    static func unit(_ what: String, _ v: Double) -> String {
        what == "longest_m" || what == "ski_dist_m" ? dec(v / 1000) : String(Int(v.rounded()))
    }

    private static func int(_ v: Double?) -> String { String(Int((v ?? 0).rounded())) }

    // MARK: from a moment to keys and values

    /// The story card of one moment: its title and lines. Nil for a key with no words of its own.
    static func plan(_ m: DayRec.Moment, zone: TimeZone, nextIsTomorrowNext: Bool = false, name: (String) -> String = { $0 }) -> Plan? {
        let p = m.p
        func num(_ k: String) -> Double? { if case .num(let n) = p[k] ?? .null { return n }; return nil }
        func str(_ k: String) -> String? { if case .str(let s) = p[k] ?? .null { return s }; return nil }
        func flag(_ k: String) -> Bool { if case .bool(let b) = p[k] ?? .null { return b }; return false }
        func i(_ k: String, _ d: Int = 0) -> Int { num(k).map { Int($0.rounded()) } ?? d }
        // the engine speaks in keys of runs (a stable id); the skier reads names (PARITY 17)
        func runs() -> [String] {
            if case .list(let a) = p["runs"] ?? .null { return a.compactMap { if case .str(let s) = $0 { return name(s) }; return nil } }
            return []
        }
        func part(_ k: String, _ args: [String: String] = [:], qty: Int? = nil) -> Part { Part(key: "moment." + k, args: args, qty: qty) }
        let t = m.t

        switch m.key {
        case "fastest":
            let kmh = int(num("kmh")), nth = i("nth", 1), of = i("of", 1)
            let run = runs().first
            let onRun = str("kind") == "run" && run != nil
            let clock = time(t, zone)
            let line: Part
            if of <= 1 && onRun {
                line = part("fastest.run.single.line", ["kmh": kmh, "run": run!, "time": clock])
            } else if of <= 1 {
                line = part("fastest.off.single.line", ["kmh": kmh, "time": clock])
            } else if onRun {
                line = part("fastest.run.\(str("when") == "early" ? "early" : "late").line",
                            ["kmh": kmh, "run": run!, "nth": String(nth), "of": String(of)])
            } else {
                line = part("fastest.off.line", ["kmh": kmh, "nth": String(nth), "of": String(of), "time": clock])
            }
            return Plan(title: part("fastest.title"), lines: [line])

        case "longest_descent":
            let run = runs().first
            let which = (run != nil ? "run" : "norun") + "." + (i("stops") == 0 ? "nonstop" : "stops")
            var args = ["km": dec((num("dist_m") ?? 0) / 1000), "drop": int(num("drop_m"))]
            if let run { args["run"] = run }
            return Plan(title: part("longest_descent.title"), lines: [part("longest_descent.\(which).line", args)])

        case "first_color":
            guard let color = str("color") else { return nil }
            let run = name(str("run") ?? ""), clock = time(t, zone)
            if flag("first_ever") {
                return Plan(title: part("first_color.\(color).first.title"),
                            lines: [part("first_color.\(color).first.line", ["run": run, "time": clock, "day_n": String(i("day_n", 1))])])
            }
            let n = i("n", 1)
            return Plan(title: part("first_color.\(color).again.title", ["n": String(n)], qty: n),
                        lines: [part("first_color.\(color).again.line", ["run": run, "time": clock])])

        case "new_runs":
            let rs = runs()
            return Plan(title: part("new_runs.title"),
                        lines: [part("new_runs.line", ["run": rs.first ?? "", "done": String(i("done")), "total": String(i("total")),
                                                       "runs": rs.joined(separator: ", "), "n": String(rs.count)], qty: rs.count)])

        case "record":
            guard let what = str("what") else { return nil }
            return Plan(title: part("record.\(what).title"),
                        lines: [part("record.\(what).line", ["value": unit(what, num("value") ?? 0), "before": unit(what, num("before") ?? 0)])])

        case "all_color":
            guard let c = str("color") else { return nil }
            let n = i("n", 1)
            return Plan(title: part("all_color.\(c).title"), lines: [part("all_color.\(c).line", ["n": String(n)], qty: n)])

        case "queue":
            return Plan(title: part("queue.title"),
                        lines: [part("queue.line", ["min": int((num("s") ?? 0) / 60), "lift": str("lift") ?? "",
                                                    "time": time(t, zone), "all": int((num("total_s") ?? 0) / 60)])])

        case "queue_pattern":
            return Plan(title: part("queue_pattern.title"),
                        lines: [part("queue_pattern.line", ["lift": str("lift") ?? "", "early": int(num("early")),
                                                            "split": String(format: "%02d:00", i("split")), "late": int(num("late"))])])

        case "drop_compare":
            let ref = str("ref") ?? ""
            let refText = Strings.shared.has("moment.ref.\(ref)") ? Strings.shared.text("moment.ref.\(ref)") : ref
            return Plan(title: part("drop_compare.title"),
                        lines: [part("drop_compare.line", ["drop": int(num("drop_m")), "times": times(num("times") ?? 0), "ref": refText])])

        case "fall":
            let run = runs().first, clock = time(t, zone)
            if str("kind") == "run", let run {
                return Plan(title: part("fall.run.title"), lines: [part("fall.run.line", ["run": run, "time": clock])])
            }
            return Plan(title: part("fall.off.title"), lines: [part("fall.off.line", ["time": clock])])

        case "offpiste":
            return Plan(title: part("offpiste.title"),
                        lines: [part("offpiste.line", ["drop": int(num("drop_m")), "time": time(t, zone),
                                                       "km": dec((num("dist_m") ?? 0) / 1000)])])

        case "vs_day":
            guard let vs = str("vs"), let what = str("what") else { return nil }
            let args = vs == "first"
                ? ["value": unit(what, num("value") ?? 0), "times": times(num("times") ?? 0)]
                : ["value": unit(what, num("value") ?? 0), "before": unit(what, num("before") ?? 0)]
            return Plan(title: part("vs_day.\(vs).title"), lines: [part("vs_day.\(vs).\(what).line", args)])

        case "flow":
            let none = i("pm") == 0
            return Plan(title: part("flow.title"),
                        lines: [part(none ? "flow.none.line" : "flow.some.line",
                                     ["am": int(num("am")), "am_n": int(num("am_n")), "pm": int(num("pm")), "pm_n": int(num("pm_n"))])])

        case "favorite":
            let n = i("n", 1)
            return Plan(title: part("favorite.title"), lines: [part("favorite.line", ["run": name(str("run") ?? ""), "n": String(n)], qty: n)])

        case "time_split":
            return Plan(title: part("time_split.title"),
                        lines: [part("time_split.line", ["ski": int(num("ski")), "lift_min": int(num("lift_min")), "queue": int(num("queue"))])])

        case "steepest":
            return Plan(title: part("steepest.title"),
                        lines: [part("steepest.\(flag("ever") ? "ever" : "today").line", ["run": name(str("run") ?? ""), "deg": int(num("deg"))])])

        case "tomorrow":
            let title = part(nextIsTomorrowNext ? "tomorrow.next.title" : "tomorrow.tomorrow.title")
            switch str("kind") {
            case "new_run":
                return Plan(title: title, lines: [part("tomorrow.new_run.line", ["run": name(str("run") ?? ""), "lift": str("lift") ?? ""])])
            case "early_lift":
                return Plan(title: title, lines: [part("tomorrow.early_lift.line", ["lift": str("lift") ?? "", "split": String(format: "%02d:00", i("split"))])])
            case "finish_color":
                let rs = runs(), n = i("n", rs.count)
                return Plan(title: title, lines: [part("tomorrow.finish_color.\(str("color") ?? "blue").line",
                                                       ["runs": rs.joined(separator: ", "), "n": String(n)], qty: n)])
            default: return nil
            }

        case "trip":
            let days = i("days", 1)
            var lines = [part("trip.line", ["drop": String(i("drop")), "done": String(i("done")), "days": String(days)], qty: days)]
            if let run = str("run"), !run.isEmpty {
                lines.append(part("trip.favorite", ["run": name(run), "n": String(i("n")), "days": String(days)], qty: days))
            }
            if case .obj(let first) = p["first"] ?? .null {
                for c in ["red", "black"] {
                    guard case .obj(let f) = first[c] ?? .null else { continue }
                    var run = ""; if case .str(let s) = f["run"] ?? .null { run = s }
                    var dayN = 1; if case .num(let n) = f["day_n"] ?? .null { dayN = Int(n) }
                    lines.append(part("trip.first.\(c)", ["run": name(run), "day_n": String(dayN)]))
                }
            }
            return Plan(title: part("trip.title"), lines: lines)

        default: return nil
        }
    }

    /// The head of the story card: "a day of discoveries" over the numbers of the day (the theme, not a place or a speed).
    static func head(theme: String?, descents: Int, dropM: Double, distM: Double) -> Plan {
        Plan(title: Part(key: "moment.story.head.\(theme ?? "steady")"),
             lines: [Part(key: "moment.story.sub", args: ["drop": String(Int(dropM.rounded())), "km": dec(distM / 1000), "n": String(descents)], qty: descents)])
    }

    // MARK: the words themselves

    /// How many variants a text has (`.1`, `.2`, … while they exist): both apps pick `seed % this`.
    static func variants(_ base: String) -> Int {
        var n = 0
        while Strings.shared.has("\(base).\(n + 1)") { n += 1 }
        return n
    }

    /// The text of a part in the language of the app, with the variant the seed picks. Nil when the key has no text.
    static func render(_ part: Part, seed: Int) -> String? {
        let n = variants(part.key)
        guard n > 0 else { return nil }
        let full = "\(part.key).\(seed % n + 1)"
        return Strings.shared.text(full, count: part.qty, part.args)
    }

    /// A whole plan: its title and the lines that have words.
    static func render(_ plan: Plan, seed: Int) -> (title: String?, lines: [String]) {
        (plan.title.flatMap { render($0, seed: seed) }, plan.lines.compactMap { render($0, seed: seed) })
    }

    /// The kind of a moment as the measurement may name it: the closed list of `docs/GROWTH.md`, and `other` for the rest.
    static func kind(_ key: String) -> String { kinds.contains(key) ? key : "other" }
    private static let kinds: Set<String> = ["fastest", "longest_descent", "first_color", "new_runs", "record", "all_color", "queue",
                                             "queue_pattern", "vs_day", "flow", "favorite", "time_split", "steepest", "drop_compare",
                                             "tomorrow", "trip"]
}
#endif
