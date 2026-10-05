package io.github.pini236.skiapp.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.pini236.skiapp.data.Geo
import io.github.pini236.skiapp.data.Terrain
import io.github.pini236.skiapp.qa.Qa
import io.github.pini236.skiapp.telemetry.Telemetry
import kotlin.math.roundToInt

/**
 * "Where am I" on the map (round 19, decision 58; docs/ARCHITECTURE.md m-7): the phone's own location, through the
 * system's LocationManager (no new library; the GPS works without signal and without Google's services).
 *
 * - **Nothing leaves the phone and nothing is kept.** No reading is sent, measured, logged or written; the last one
 *   lives in memory while the button is on, and [off] drops it with what [Locator] remembered.
 * - Only while the button is on **and** the map is on screen in front ([resume] to [pause]); the button starts off on
 *   every opening, and going to the background or leaving the map turns it off (m-7 section 5).
 * - A reading every 2 seconds or 5 metres. "Approximate" (Android 12's choice): a big circle and no snapping.
 */
class WhereAmI(private val context: Context, private val locator: Locator, private val terrain: Terrain) {

    sealed interface State {
        /** The button is off. */
        data object Off : State
        /** On, before the first reading. */
        data object Waiting : State
        data object Denied : State
        /** No location on this phone, or it is switched off. */
        data object Unavailable : State
        data class Fixed(val fix: Locator.Fix, val x: Float, val y: Float, val accuracy: Float, val alt: Int, val bearing: Float?) : State
    }

    var state by mutableStateOf<State>(State.Off); private set
    /** The approximate-only permission (no snapping, and "precise location" is offered). */
    var approximate by mutableStateOf(false); private set
    val on get() = state is State.Waiting || state is State.Fixed

    private val lm = context.getSystemService(LocationManager::class.java)
    private var listening = false
    private var resumed = false
    private var lastGps = 0L
    /** location_toggle's result, sent once per switching on, when it is known (m-7, docs/GROWTH.md). */
    private var pending: String? = null

    private val listener = LocationListener { onLocation(it) }

    fun granted(p: String) = context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    val anyPermission get() = granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION)

    /** The button on, once a permission is there (the screen asks first). */
    fun start() {
        if (!anyPermission) { state = State.Denied; send("denied"); return }
        approximate = !granted(Manifest.permission.ACCESS_FINE_LOCATION)
        if (!enabled()) { state = State.Unavailable; send("unavailable"); return }
        pending = if (approximate) "approximate" else "granted"
        state = State.Waiting; locator.reset()
        listening = true
        if (resumed) register()
        Qa.log("where on ${if (approximate) "approximate" else "precise"}")
    }

    /** The system said no to the permission. */
    fun refused() { state = State.Denied; send("denied") }

    fun off() {
        val was = state != State.Off
        unregister(); listening = false; locator.reset(); pending = null
        state = State.Off; approximate = false
        if (was) { Telemetry.event("location_toggle", mapOf("on" to false)); Qa.log("where off") }
    }

    /** The map is in front: listen again if the button is on. */
    fun resume() { resumed = true; if (listening) register() }

    /** The app went to the background or the map closed: the button goes off (m-7 section 5). */
    fun pause() { resumed = false; if (state != State.Off) off() }

    private fun send(result: String) {
        pending = null
        Telemetry.event("location_toggle", mapOf("on" to true, "result" to result))
    }

    private fun enabled(): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= 28) lm.isLocationEnabled
        else lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }.getOrDefault(false)

    @SuppressLint("MissingPermission")
    private fun register() {
        unregister()
        val all = runCatching { lm.allProviders }.getOrDefault(emptyList())
        // precise: the GPS, and the network for a quick first fix; approximate: whatever the phone allows
        val want = if (approximate) listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            else listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        var any = false
        for (p in want) {
            if (p !in all) continue
            if (p != LocationManager.PASSIVE_PROVIDER && runCatching { lm.isProviderEnabled(p) }.getOrDefault(false).not()) continue
            any = runCatching { lm.requestLocationUpdates(p, 2000L, 5f, listener, Looper.getMainLooper()); true }.getOrDefault(false) || any
            if (approximate && any) break
        }
        if (!any) { state = State.Unavailable; send("unavailable"); listening = false; return }
        // a recent fix the phone already has (under half a minute) puts the dot at once
        for (p in want) runCatching { lm.getLastKnownLocation(p) }.getOrNull()?.let { l ->
            val age = (SystemClock.elapsedRealtimeNanos() - l.elapsedRealtimeNanos) / 1_000_000L
            if (age in 0..30_000) { onLocation(l); return }
        }
    }

    private fun unregister() { runCatching { lm.removeUpdates(listener) } }

    private fun onLocation(l: Location) {
        if (!listening) return
        val now = SystemClock.elapsedRealtime()
        if (l.provider == LocationManager.GPS_PROVIDER) lastGps = now
        // a network fix between GPS ones would make the run flicker away: the GPS wins for 10 seconds
        else if (!approximate && now - lastGps < 10_000) return
        val x = Geo.x(l.longitude); val y = Geo.y(l.latitude)
        val acc = if (l.hasAccuracy()) l.accuracy else 100f
        val fix = locator.feed(x, y, acc, approximate)
        val alt = (terrain.elev(x, y) / 10f).roundToInt() * 10
        val bearing = if (l.hasBearing() && l.hasSpeed() && l.speed > 1f) l.bearing else null
        state = State.Fixed(fix, x, y, acc, alt, bearing)
        pending?.let { send(if (fix == Locator.Fix.Outside) "outside" else it) }
        // the emulator run reads the state, never the place (m-7: no location in the device's log)
        Qa.log("where ${when (fix) {
            Locator.Fix.Outside -> "outside"; Locator.Fix.Approx -> "approx"; Locator.Fix.Low -> "low"; Locator.Fix.Free -> "free"
            is Locator.Fix.OnLift -> "lift ${fix.lift.name}"; is Locator.Fix.OnRun -> "run ${fix.piste.key}"
        }}")
    }
}
