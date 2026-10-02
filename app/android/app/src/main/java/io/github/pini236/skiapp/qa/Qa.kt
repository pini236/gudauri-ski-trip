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
    fun parseCamera(s: String): FloatArray? = s.split(',').mapNotNull { it.trim().toFloatOrNull() }.takeIf { it.size == 5 }?.toFloatArray()
}
