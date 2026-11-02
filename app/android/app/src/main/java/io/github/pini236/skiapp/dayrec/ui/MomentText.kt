package io.github.pini236.skiapp.dayrec.ui

import android.content.Context
import io.github.pini236.skiapp.dayrec.Moments
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The words of a moment (decision 76; docs/handoff/2026-10-30-dayrec-moments-copy/README.md). The engine writes no text: an item
 * is a key and numbers, and this turns it into the keys of i18n/strings.json (`moment.<...>`) and the values of their
 * placeholders. Two steps, so that a test can run the first without a phone: [plan] (pure: which keys, which values) and
 * [Words.render] (the Android resources: the variant `seed % number of variants`, the plural form, the language).
 *
 * Nothing here reads a position, and nothing says a speed is a goal (a speed is a fact the skier recorded for their own eyes).
 */
object MomentText {
    /** A text key (without the variant number), its values by the name of the placeholder, and the count of a plural. */
    class Part(val key: String, val args: Map<String, Any> = emptyMap(), val qty: Int? = null)

    /** The title (when the moment has one) and the lines under it. */
    class Plan(val title: Part?, val lines: List<Part>)

    /** The height reference of `drop_compare`: its text is a key of its own (`moment.ref.<id>`). */
    class Ref(val id: String)

    /**
     * The placeholders of each text, in the order of their first appearance in the Hebrew text (the numbering Android uses,
     * tools/build-app-strings.py). A test compares every row with i18n/strings.json.
     */
    internal val ORDER: Map<String, List<String>> = mapOf(
        "moment.story.sub" to listOf("drop", "km", "n"),
        "moment.fastest.run.late.line" to listOf("kmh", "run", "nth", "of"),
        "moment.fastest.run.early.line" to listOf("kmh", "run", "nth", "of"),
        "moment.fastest.off.line" to listOf("kmh", "nth", "of", "time"),
        "moment.fastest.run.single.line" to listOf("kmh", "run", "time"),
        "moment.fastest.off.single.line" to listOf("kmh", "time"),
        "moment.longest_descent.run.nonstop.line" to listOf("km", "run"),
        "moment.longest_descent.run.stops.line" to listOf("km", "drop", "run"),
        "moment.longest_descent.norun.nonstop.line" to listOf("km", "drop"),
        "moment.longest_descent.norun.stops.line" to listOf("km", "drop"),
        "moment.first_color.red.first.line" to listOf("run", "time", "day_n"),
        "moment.first_color.black.first.line" to listOf("run", "time", "day_n"),
        "moment.first_color.red.again.title" to listOf("n"),
        "moment.first_color.black.again.title" to listOf("n"),
        "moment.first_color.red.again.line" to listOf("run", "time"),
        "moment.first_color.black.again.line" to listOf("run", "time"),
        "moment.new_runs.line" to listOf("run", "done", "total", "runs", "n"),
        "moment.record.max_kmh.line" to listOf("value", "before"),
        "moment.record.longest_m.line" to listOf("value", "before"),
        "moment.record.drop_m.line" to listOf("value", "before"),
        "moment.record.descents.line" to listOf("value", "before"),
        "moment.record.ski_dist_m.line" to listOf("value", "before"),
        "moment.all_color.green.line" to listOf("n"),
        "moment.all_color.blue.line" to listOf("n"),
        "moment.all_color.red.line" to listOf("n"),
        "moment.all_color.black.line" to listOf("n"),
        "moment.queue.line" to listOf("min", "lift", "time", "all"),
        "moment.queue_pattern.line" to listOf("lift", "early", "split", "late"),
        "moment.drop_compare.line" to listOf("drop", "times", "ref"),
        "moment.fall.run.line" to listOf("run", "time"),
        "moment.fall.off.line" to listOf("time"),
        "moment.offpiste.line" to listOf("drop", "time"),
        "moment.last_descent.line" to listOf("run", "km", "drop", "min"),
        "moment.last_descent.falls" to listOf("n"),
        "moment.vs_day.prev.drop_m.line" to listOf("value", "before"),
        "moment.vs_day.prev.descents.line" to listOf("value", "before"),
        "moment.vs_day.prev.ski_dist_m.line" to listOf("value", "before"),
        "moment.vs_day.first.drop_m.line" to listOf("value", "times"),
        "moment.vs_day.first.descents.line" to listOf("value", "times"),
        "moment.vs_day.first.ski_dist_m.line" to listOf("value", "times"),
        "moment.flow.some.line" to listOf("am", "am_n", "pm", "pm_n"),
        "moment.flow.none.line" to listOf("am", "am_n", "pm_n"),
        "moment.favorite.line" to listOf("run", "n"),
        "moment.time_split.line" to listOf("ski", "lift_min", "queue"),
        "moment.steepest.ever.line" to listOf("run", "deg"),
        "moment.steepest.today.line" to listOf("run", "deg"),
        "moment.tomorrow.new_run.line" to listOf("run", "lift"),
        "moment.tomorrow.early_lift.line" to listOf("lift", "split"),
        "moment.tomorrow.finish_color.green.line" to listOf("runs", "n"),
        "moment.tomorrow.finish_color.blue.line" to listOf("runs", "n"),
        "moment.tomorrow.finish_color.red.line" to listOf("runs", "n"),
        "moment.tomorrow.finish_color.black.line" to listOf("runs", "n"),
        "moment.trip.line" to listOf("drop", "done", "days"),
        "moment.trip.favorite" to listOf("run", "n", "days"),
        "moment.trip.first.red" to listOf("run", "day_n"),
        "moment.trip.first.black" to listOf("run", "day_n"),
    )

    /** A variant whose placeholders differ from the first one's (the order of the key + `.N`). */
    internal val ORDER_VARIANT: Map<String, List<String>> = mapOf("moment.offpiste.line.2" to listOf("km"))

    /** The placeholders of the text [full] (the key with its variant number), in Android's order. */
    internal fun orderOf(full: String): List<String> {
        ORDER_VARIANT[full]?.let { return it }
        return ORDER[full.substringBeforeLast('.')] ?: emptyList()
    }

    private fun zonedTime(t: Long, zone: ZoneId) = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT).format(Instant.ofEpochMilli(t).atZone(zone))

    private fun int(o: JSONObject, k: String) = Math.round(o.optDouble(k, 0.0)).toInt()

    /** One decimal, in the decimal mark of the language (Russian: a comma). */
    internal fun dec(v: Double, locale: Locale) = String.format(locale, "%.1f", v)

    /** "2.9" and "3" (a whole number has no ".0"), as the copy README says for {times}. */
    internal fun times(v: Double, locale: Locale): String = if (v == Math.rint(v)) v.toInt().toString() else dec(v, locale)

    private fun keys(p: JSONObject): List<String> = p.optJSONArray("runs")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()

    /** The value of a record or a day sum in the unit its line says: kilometres for a distance, a whole number otherwise. */
    private fun unit(what: String, v: Double, locale: Locale): String = when (what) {
        "longest_m", "ski_dist_m" -> dec(v / 1000.0, locale)
        else -> v.roundToInt().toString()
    }

    /** The key of an item's story card: title and lines, from the engine's key and parameters. Null for a key with no words. */
    fun plan(item: JSONObject, zone: ZoneId, locale: Locale, nextIsTomorrowNext: Boolean = false, name: (String) -> String = { it }): Plan? {
        val key = item.getString("key")
        val p = item.getJSONObject("p")
        val t = item.optLong("t")
        // the engine speaks in keys of runs (a stable id); the skier reads names (PARITY 17)
        fun runs(q: JSONObject) = keys(q).map(name)
        fun part(k: String, args: Map<String, Any> = emptyMap(), qty: Int? = null) = Part("moment.$k", args, qty)
        return when (key) {
            "fastest" -> {
                val kmh = int(p, "kmh"); val nth = p.optInt("nth", 1); val of = p.optInt("of", 1)
                val run = runs(p).firstOrNull(); val onRun = p.optString("kind") == "run" && run != null
                val time = zonedTime(t, zone)
                val line = when {
                    of <= 1 && onRun -> part("fastest.run.single.line", mapOf("kmh" to kmh, "run" to run!!, "time" to time))
                    of <= 1 -> part("fastest.off.single.line", mapOf("kmh" to kmh, "time" to time))
                    onRun -> part("fastest.run.${if (p.optString("when") == "early") "early" else "late"}.line", mapOf("kmh" to kmh, "run" to run!!, "nth" to nth, "of" to of))
                    else -> part("fastest.off.line", mapOf("kmh" to kmh, "nth" to nth, "of" to of, "time" to time))
                }
                Plan(part("fastest.title"), listOf(line))
            }
            "longest_descent" -> {
                val run = runs(p).firstOrNull()
                val which = (if (run != null) "run" else "norun") + "." + (if (p.optInt("stops", 0) == 0) "nonstop" else "stops")
                val km = dec(p.optDouble("dist_m") / 1000.0, locale); val drop = int(p, "drop_m")
                val args = LinkedHashMap<String, Any>(); args["km"] = km; args["drop"] = drop; if (run != null) args["run"] = run
                Plan(part("longest_descent.title"), listOf(part("longest_descent.$which.line", args)))
            }
            "first_color" -> {
                val color = p.getString("color"); val first = p.optBoolean("first_ever", false)
                val run = name(p.optString("run")); val time = zonedTime(t, zone)
                if (first) Plan(part("first_color.$color.first.title"), listOf(part("first_color.$color.first.line", mapOf("run" to run, "time" to time, "day_n" to p.optInt("day_n", 1)))))
                else {
                    val n = p.optInt("n", 1)
                    Plan(part("first_color.$color.again.title", mapOf("n" to n), n), listOf(part("first_color.$color.again.line", mapOf("run" to run, "time" to time))))
                }
            }
            "new_runs" -> {
                val rs = runs(p)
                Plan(part("new_runs.title"), listOf(part("new_runs.line", mapOf("run" to (rs.firstOrNull() ?: ""), "done" to p.optInt("done"), "total" to p.optInt("total"),
                    "runs" to rs.joinToString(", "), "n" to rs.size), rs.size)))
            }
            "record" -> {
                val what = p.getString("what")
                Plan(part("record.$what.title"), listOf(part("record.$what.line", mapOf("value" to unit(what, p.optDouble("value"), locale), "before" to unit(what, p.optDouble("before"), locale)))))
            }
            "all_color" -> {
                val c = p.getString("color"); val n = p.optInt("n", 1)
                Plan(part("all_color.$c.title"), listOf(part("all_color.$c.line", mapOf("n" to n), n)))
            }
            "queue" -> Plan(part("queue.title"), listOf(part("queue.line", mapOf("min" to Math.round(p.optDouble("s") / 60.0).toInt(), "lift" to p.optString("lift"),
                "time" to zonedTime(t, zone), "all" to Math.round(p.optDouble("total_s") / 60.0).toInt()))))
            "queue_pattern" -> Plan(part("queue_pattern.title"), listOf(part("queue_pattern.line", mapOf("lift" to p.optString("lift"), "early" to int(p, "early"),
                "split" to "%02d:00".format(p.optInt("split")), "late" to int(p, "late")))))
            "drop_compare" -> Plan(part("drop_compare.title"), listOf(part("drop_compare.line", mapOf("drop" to int(p, "drop_m"), "times" to times(p.optDouble("times"), locale), "ref" to Ref(p.getString("ref"))))))
            "fall" -> {
                val run = runs(p).firstOrNull(); val time = zonedTime(t, zone)
                if (p.optString("kind") == "run" && run != null) Plan(part("fall.run.title"), listOf(part("fall.run.line", mapOf("run" to run, "time" to time))))
                else Plan(part("fall.off.title"), listOf(part("fall.off.line", mapOf("time" to time))))
            }
            "offpiste" -> Plan(part("offpiste.title"), listOf(part("offpiste.line", mapOf("drop" to int(p, "drop_m"), "time" to zonedTime(t, zone), "km" to dec(p.optDouble("dist_m") / 1000.0, locale)))))
            "vs_day" -> {
                val vs = p.getString("vs"); val what = p.getString("what")
                val args = if (vs == "first") mapOf("value" to unit(what, p.optDouble("value"), locale), "times" to times(p.optDouble("times"), locale))
                else mapOf("value" to unit(what, p.optDouble("value"), locale), "before" to unit(what, p.optDouble("before"), locale))
                Plan(part("vs_day.$vs.title"), listOf(part("vs_day.$vs.$what.line", args)))
            }
            "flow" -> {
                val none = p.optInt("pm", 0) == 0
                Plan(part("flow.title"), listOf(part(if (none) "flow.none.line" else "flow.some.line",
                    mapOf("am" to p.optInt("am"), "am_n" to p.optInt("am_n"), "pm" to p.optInt("pm"), "pm_n" to p.optInt("pm_n")))))
            }
            "favorite" -> {
                val n = p.optInt("n", 1)
                Plan(part("favorite.title"), listOf(part("favorite.line", mapOf("run" to name(p.optString("run")), "n" to n), n)))
            }
            "time_split" -> Plan(part("time_split.title"), listOf(part("time_split.line", mapOf("ski" to int(p, "ski"), "lift_min" to int(p, "lift_min"), "queue" to int(p, "queue")))))
            "steepest" -> Plan(part("steepest.title"), listOf(part("steepest.${if (p.optBoolean("ever", false)) "ever" else "today"}.line", mapOf("run" to name(p.optString("run")), "deg" to int(p, "deg")))))
            "tomorrow" -> {
                val kind = p.optString("kind")
                val title = part(if (nextIsTomorrowNext) "tomorrow.next.title" else "tomorrow.tomorrow.title")
                val line = when (kind) {
                    "new_run" -> part("tomorrow.new_run.line", mapOf("run" to name(p.optString("run")), "lift" to p.optString("lift")))
                    "early_lift" -> part("tomorrow.early_lift.line", mapOf("lift" to p.optString("lift"), "split" to "%02d:00".format(p.optInt("split"))))
                    "finish_color" -> {
                        val rs = runs(p); val n = p.optInt("n", rs.size)
                        part("tomorrow.finish_color.${p.optString("color")}.line", mapOf("runs" to rs.joinToString(", "), "n" to n), n)
                    }
                    else -> return null
                }
                Plan(title, listOf(line))
            }
            "trip" -> {
                val days = p.optInt("days", 1)
                val lines = ArrayList<Part>()
                lines += part("trip.line", mapOf("drop" to p.optInt("drop"), "done" to p.optInt("done"), "days" to days), days)
                if (p.has("run") && !p.isNull("run")) lines += part("trip.favorite", mapOf("run" to name(p.optString("run")), "n" to p.optInt("n"), "days" to days))
                p.optJSONObject("first")?.let { f ->
                    for (c in listOf("red", "black")) f.optJSONObject(c)?.let { lines += part("trip.first.$c", mapOf("run" to name(it.optString("run")), "day_n" to it.optInt("day_n"))) }
                }
                Plan(part("trip.title"), lines)
            }
            else -> null
        }
    }

    /** The head of the story card: "a day of discoveries" over the numbers of the day (the theme, not a place or a speed). */
    fun head(theme: String?, descents: Int, dropM: Double, distM: Double, locale: Locale): Plan {
        val t = theme ?: "steady"
        return Plan(Part("moment.story.head.$t"), listOf(Part("moment.story.sub", mapOf("drop" to dropM.roundToInt(), "km" to dec(distM / 1000.0, locale), "n" to descents), descents)))
    }

    /** The Android side: picks the variant and fills the placeholders. */
    class Words(private val ctx: Context, private val locale: Locale) {
        private val res get() = ctx.resources

        private fun id(type: String, full: String) = res.getIdentifier(full.replace('.', '_'), type, ctx.packageName)

        /** The number of variants of a text: `.1`, `.2`, ... while they exist (both apps use `seed % this`). */
        private fun variants(base: String, type: String): Int {
            var n = 0
            while (id(type, "$base.${n + 1}") != 0) n++
            return n
        }

        fun render(part: Part, seed: Long): String? {
            val plural = part.qty != null
            val type = if (plural) "plurals" else "string"
            val n = variants(part.key, type)
            if (n == 0) return null
            val full = "${part.key}.${(seed % n).toInt() + 1}"
            val args = orderOf(full).map { name ->
                when (val v = part.args[name]) {
                    null -> ""
                    is Ref -> res.getIdentifier("moment_ref_${v.id}_1", "string", ctx.packageName).let { r -> if (r != 0) res.getString(r) else v.id }
                    else -> v
                }
            }.toTypedArray()
            val rid = id(type, full)
            return if (plural) res.getQuantityString(rid, part.qty!!, *args) else res.getString(rid, *args)
        }

        fun render(plan: Plan, seed: Long): Pair<String?, List<String>> =
            Pair(plan.title?.let { render(it, seed) }, plan.lines.mapNotNull { render(it, seed) })
    }

    /** The seed of the head and the lines that come from no engine item: the same on both apps. */
    fun seedOf(key: String, t: Long) = Moments.seed(key, t)
}
