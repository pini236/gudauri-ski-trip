package io.github.pini236.skiapp.qa

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Test hooks for the emulator run in GitHub (app/android/qa/README.md). Debug builds only: a release build
 * ignores the "qa." extras and logs nothing, so nothing here reaches Pini's phone or the store.
 */
object Qa {
    private const val TAG = "SkiQa"
    @Volatile var enabled = false
        private set

    fun init(ctx: Context) { enabled = ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 }

    /** One line per milestone; the QA script waits on these. */
    fun log(msg: String) { if (enabled) Log.i(TAG, msg) }

    /** "2027-01-12T10:00" is local time in Gudauri: UTC+4 all year, no daylight saving. */
    fun gudauriTime(s: String): Long = LocalDateTime.parse(s).toInstant(ZoneOffset.ofHours(4)).toEpochMilli()

    /** "x,z,dist,yawDeg,pitchDeg" in the map's metres (the same frame as terrain.json). */
    /**
     * A made-up trip for the home page's screenshots (flight numbers that do not exist). Never the crew's flight:
     * their data is not packed into the app (docs/USERS.md).
     */
    fun sampleTrip() = io.github.pini236.skiapp.trip.Trip(
        io.github.pini236.skiapp.trip.Flight(java.time.LocalDate.of(2027, 1, 10), "GD 101", "TLV · תל אביב", "TBS · טביליסי", java.time.LocalTime.of(16, 0), java.time.LocalTime.of(20, 35)),
        io.github.pini236.skiapp.trip.Flight(java.time.LocalDate.of(2027, 1, 15), "GD 102", "TBS · טביליסי", "TLV · תל אביב", java.time.LocalTime.of(1, 35), java.time.LocalTime.of(2, 15)),
    )

    fun parseCamera(s: String): FloatArray? = s.split(',').mapNotNull { it.trim().toFloatOrNull() }.takeIf { it.size == 5 }?.toFloatArray()
}
