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

    /**
     * The counter's time: always from the tap ([startedAt]), never from the first reading. The first reading comes later (a cold
     * GPS can take a minute), and counting from it would send the counter back to 0:00 at that moment. Zero before the recording began.
     */
    fun sinceTap(now: Long, startedAt: Long): Long = if (startedAt > 0L) (now - startedAt).coerceAtLeast(0L) else 0L

    /**
     * When the counter starts counting from: the tap, as soon as there is one. The recorder writes its own start time a moment
     * later (and a start that is slow, or a phone that is slow to start the service, leaves the last recording's time there), so
     * a start time older than the tap is not this recording's: the counter then goes by the tap and never sits on 0:00 or shows
     * the last day's hours.
     */
    fun began(tapAt: Long, startedAt: Long): Long = if (tapAt > 0L && startedAt < tapAt) tapAt else startedAt

    /** Waiting for the first reading: recording, and none has come yet (the screen breathes meanwhile, see DayLiveScreen). */
    fun searching(recording: Boolean, lastFix: Long): Boolean = recording && lastFix == 0L

    /**
     * The recording ended from outside the screen (Stop in the notification, the system, the one-descent run reaching the bottom):
     * it was going, the day's file had been seen, and now it is not. A start that has not made its file yet does not count, so a
     * slow start is never taken for an end.
     */
    fun endedOutside(wasRecording: Boolean, recording: Boolean, sawFile: Boolean): Boolean = wasRecording && !recording && sawFile

    /** No reading for this long while recording: the signal is weak (a valley, a tunnel, a pocket in the cold). */
    const val WEAK_MS = 20_000L

    /** The first reading of a recording can take a minute or more (a cold start, a phone indoors or under a roof): the warning waits longer for it than for a gap in a recording that is going. */
    const val WEAK_FIRST_MS = 90_000L

    /**
     * Whether to say "weak GPS signal": [lastFix] is the last reading (0: none yet), [startedAt] when the recording began.
     * With no reading yet it is [WEAK_FIRST_MS] from the start (a normal wait for satellites is not a weak signal); once
     * readings have come, [WEAK_MS] from the last one.
     */
    fun weakGps(now: Long, lastFix: Long, startedAt: Long): Boolean =
        if (lastFix > 0L) now - lastFix > WEAK_MS else startedAt > 0L && now - startedAt > WEAK_FIRST_MS
}
