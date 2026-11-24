package io.github.pini236.skiapp.redesign

/**
 * K08, the lenses (design/handoff/2026-11-24-redesign-village-shelf-lenses, section 4): one at a time, each colouring
 * every run by one question, in the handoff's order of building: in the sun now and open, easy to hard, then new snow.
 */
internal enum class LensKind(val id: String) {
    SUN("sun"), OPEN("open"), SLOPE("slope"), SNOW("snow");

    companion object { fun of(id: String?): LensKind? = values().firstOrNull { it.id == id } }
}

internal object LensRules {
    /** What a lens counts: the named runs of kind "run" (not the beginner areas or the ski ways). */
    fun counted(named: Boolean, kind: String) = named && kind == "run"

    /** In the sun now: how many counted runs are at least half in the sun, and how many more are in the shade. */
    fun sun(shares: Map<String, Float>, counted: Collection<String>): Pair<Int, Int> {
        val n = counted.count { (shares[it] ?: 0f) >= .5f }
        return n to counted.size - n
    }

    /** A run under the "open" lens: open, closed, or with no lift in the data (shown faint and not counted). */
    enum class Open { OPEN, CLOSED, UNKNOWN }

    fun open(runOpen: Boolean?): Open = when (runOpen) { true -> Open.OPEN; false -> Open.CLOSED; null -> Open.UNKNOWN }

    /** Open: how many counted runs are open, and how many are closed (the unknown ones are in neither). */
    fun openCount(state: (String) -> Boolean?, counted: Collection<String>): Pair<Int, Int> {
        val s = counted.map { open(state(it)) }
        return s.count { it == Open.OPEN } to s.count { it == Open.CLOSED }
    }

    /**
     * New snow at an altitude: the last 24 hours' snow of the forecast's points ([points], their elevation and cm), linear
     * between the points above and below, the nearest one outside them (the run's conditions' rule, Forecast.at).
     */
    fun snowAt(alt: Float, points: List<Pair<Float, Double>>): Double {
        val ps = points.sortedBy { it.first }
        if (ps.isEmpty()) return 0.0
        if (alt <= ps.first().first) return ps.first().second
        if (alt >= ps.last().first) return ps.last().second
        for (i in 0 until ps.size - 1) {
            val (ea, a) = ps[i]; val (eb, b) = ps[i + 1]
            if (alt in ea..eb) return a + (alt - ea) / (eb - ea).toDouble() * (b - a)
        }
        return ps.last().second
    }

    /** The snow lens's three classes: 2 for 10 cm and over, 1 for 1 to 9, 0 for none (whole cm). */
    fun snowClass(cm: Double): Int = Math.round(cm).toInt().let { if (it >= 10) 2 else if (it >= 1) 1 else 0 }

    /** Which lens a choice leaves on: a tap on the lit chip turns it off; another chip moves to it. */
    fun tap(on: LensKind?, tapped: LensKind): LensKind? = if (on == tapped) null else tapped
}
