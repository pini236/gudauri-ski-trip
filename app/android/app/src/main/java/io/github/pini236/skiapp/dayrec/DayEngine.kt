package io.github.pini236.skiapp.dayrec

import io.github.pini236.skiapp.dayrec.analysis.Analyzer
import org.json.JSONObject

/**
 * The analysis of a day while it is recorded (decision 76, docs/FEATURES.md section 10): the recorder hands it the points
 * and the blows as they come, and it answers two things, neither of them a screen:
 *
 * - [live]: what the skier is doing now ("probably": a causal view, only readings up to now), the maximum speed so far,
 *   the falls, and in a single-run recording the moment it stops by itself. Computed on the last [WINDOW_MS] of a day
 *   (a single run is short, and is computed whole), so its cost does not grow with the day.
 * - [ride]: the moment the skier gets on a lift after a descent, the summary of the descent that just ended, to be shown
 *   while there is time to look (the lift ride). It is computed by the final analysis on the last stretch, so its edges
 *   are exact to the second.
 *
 * Not thread-safe: one thread feeds it and asks it. [final] works on a copy of what was fed, so it may run on another
 * thread while the feeding goes on. Nothing in here leaves the phone.
 */
class DayEngine(private val analyzer: Analyzer, val header: JSONObject) {
    private val points = ArrayList<DayLog.Point>()
    private val events = ArrayList<JSONObject>()

    /** What the live view says. [act] is stand, walk, ski, up or lift; [lift] and [runs] by the act. */
    class Snapshot(
        val t: Long, val act: String, val lift: String?, val runs: List<String>?,
        val maxKmh: Double?, val falls: List<Analyzer.Fall>, val autostop: Analyzer.AutoStop?,
    )

    /** The descent that just ended, for the lift ride. [runs] in the order they were skied. */
    class Ride(
        val t0: Long, val t1: Long, val runs: List<String>, val distM: Double, val dropM: Double,
        val maxKmh: Double?, val maxAt: Analyzer.MaxAt?, val movingS: Int, val avgKmh: Double?,
    )

    val isRun: Boolean get() = header.optString("mode", "day") == "run"
    val size: Int get() = points.size

    private var dayMax: Double? = null
    private val falls = LinkedHashMap<Long, Analyzer.Fall>()
    private var lastAct: String? = null
    private var lastRideT0 = -1L
    @Volatile var snapshot: Snapshot? = null; private set
    @Volatile var lastRide: Ride? = null; private set
    /** Called when a descent ends and the lift begins. */
    var onRide: ((Ride) -> Unit)? = null

    fun add(p: DayLog.Point) { if (points.isEmpty() || p.t > points.last().t) points += p }

    /** A blow from the motion sensors, or the dismissal of one, as the log has it. */
    fun addEvent(e: JSONObject) { events += e }

    /** The live view, from the points so far. */
    fun live(): Snapshot? {
        if (points.isEmpty()) return null
        val from = if (isRun) 0 else windowStart()
        val r = analyzer.live(header, points.subList(from, points.size), eventsFrom(points[from].t))
        r.maxKmh?.let { m -> dayMax = dayMax?.let { maxOf(it, m) } ?: m }
        for (f in r.falls) falls.putIfAbsent(f.t, f)
        val last = r.events.lastOrNull()
        val act = last?.act ?: "stand"
        val s = Snapshot(points.last().t, act, last?.lift, last?.runs, dayMax, falls.values.sortedBy { it.t }, r.autostop)
        snapshot = s
        // between the descent and the lift there is usually a wait (a queue, a walk): so it is any arrival on a lift, and the
        // descent is the last one the final analysis of the stretch finds, once ([lastRideT0])
        if (lastAct != "lift" && act == "lift") summarise()
        lastAct = act
        return s
    }

    /** The whole day, smoothed backwards: the truth. Takes a copy, so it may run on another thread. */
    fun final(): Analyzer.DayResult {
        val p = ArrayList(points); val e = ArrayList(events)
        return analyzer.final(header, p, e)
    }

    private fun windowStart(): Int {
        val cut = points.last().t - WINDOW_MS
        // the points are sorted: a binary search for the first one inside the window
        var lo = 0; var hi = points.size - 1
        while (lo < hi) { val mid = (lo + hi) / 2; if (points[mid].t < cut) lo = mid + 1 else hi = mid }
        return lo
    }

    private fun eventsFrom(t: Long) = events.filter { it.optLong("t", 0L) >= t }

    /** The final analysis of the last stretch; its last descent is the one that just ended. */
    private fun summarise() {
        val cut = points.last().t - RIDE_LOOKBACK_MS
        val from = points.indexOfFirst { it.t >= cut }.coerceAtLeast(0)
        val r = analyzer.final(header, ArrayList(points.subList(from, points.size)), eventsFrom(points[from].t))
        val d = r.segments.lastOrNull { it.type == "descent" } ?: return
        if (d.t0 == lastRideT0) return
        lastRideT0 = d.t0
        val runs = LinkedHashSet<String>()
        for (l in d.legs) if (l.kind == "run") runs += l.runs
        val ride = Ride(d.t0, d.t1, runs.toList(), d.distM ?: 0.0, d.dropM ?: 0.0, d.maxKmh, d.maxAt, d.movingS, d.avgKmh)
        lastRide = ride
        onRide?.invoke(ride)
    }

    companion object {
        /** The live view looks at this much of the day: enough for the filter to settle and a Viterbi to decide. */
        const val WINDOW_MS = 20 * 60_000L
        /** The final analysis for the ride summary: a long descent, and the lift before it. */
        const val RIDE_LOOKBACK_MS = 30 * 60_000L
    }
}
