package io.github.pini236.skiapp.dayrec.ui

import java.util.Locale

/** The live screen's numbers as text (round 29): no sensor, no screen here, so they can be tested. */
object LiveNumbers {
    /** A speed in m/s as whole km/h; null while there is none. */
    fun kmh(ms: Double?): String = if (ms == null || ms < 0) "0" else Math.round(ms * 3.6).toString()

    /** A maximum in km/h, rounded. */
    fun maxKmh(kmh: Double?): String = if (kmh == null) "0" else Math.round(kmh).toString()

    /** Metres as kilometres with one decimal ("0.0", "12.4"), dot as a decimal point in every language, like the digits of the site. */
    fun km(m: Double?): String = String.format(Locale.ROOT, "%.1f", (m ?: 0.0) / 1000.0)

    fun metres(m: Double?): String = Math.round(m ?: 0.0).toString()

    /** How long the recording has gone on: "0:12" (minutes and seconds), and "1:05:03" after the first hour. */
    fun elapsed(ms: Long): String {
        val s = (ms / 1000L).coerceAtLeast(0L)
        val h = s / 3600; val m = s % 3600 / 60; val ss = s % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, ss) else String.format(Locale.ROOT, "%d:%02d", m, ss)
    }

    /** No reading for this long while recording: the signal is weak (a valley, a tunnel, a pocket in the cold). */
    const val WEAK_MS = 20_000L

    /** Whether to say "weak GPS signal": [lastFix] is the last reading (0: none yet), [startedAt] when the recording began. */
    fun weakGps(now: Long, lastFix: Long, startedAt: Long): Boolean {
        val since = if (lastFix > 0L) lastFix else startedAt
        return since > 0L && now - since > WEAK_MS
    }
}
