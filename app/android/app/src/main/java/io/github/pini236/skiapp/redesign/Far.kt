package io.github.pini236.skiapp.redesign

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.data.Geo
import io.github.pini236.skiapp.data.Terrain
import io.github.pini236.skiapp.qa.Qa
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * UX part 7, far from the mountain (design/handoff/2026-11-26-redesign-ux-pass/part-7-far.md, boards NM1 to NM3).
 * The rule: no dot is ever drawn outside the model and the camera never follows a point outside it; instead the app says
 * how far Gudauri is and in which direction. Nothing here leaves the phone or is kept.
 */
internal object FarRules {
    /** The resort's centre: the Goodaura lift's bottom station (site/data/runs-and-lifts.json, its first point). */
    const val LAT = 42.471146
    const val LON = 44.492189
    private const val R_KM = 6371.0088

    /** Great-circle distance in km from [lat], [lon] to the resort's centre. */
    fun km(lat: Double, lon: Double): Double {
        val p1 = Math.toRadians(lat); val p2 = Math.toRadians(LAT)
        val dp = p2 - p1; val dl = Math.toRadians(LON - lon)
        val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * R_KM * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    /** The initial bearing from [lat], [lon] toward the resort, degrees clockwise from north, 0 to 360. */
    fun bearing(lat: Double, lon: Double): Double {
        val p1 = Math.toRadians(lat); val p2 = Math.toRadians(LAT); val dl = Math.toRadians(LON - lon)
        val y = sin(dl) * cos(p2); val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /** The arrow's turn on the screen: toward the resort relative to where the phone points, or to north (up) with no compass. */
    fun arrow(bearing: Double, heading: Float?): Float = (((bearing - (heading ?: 0f)) % 360.0 + 360.0) % 360.0).toFloat()

    /** Under 10 km one decimal ("3.2"), from 10 whole with the language's thousands separator ("1,430"). */
    fun formatKm(km: Double, locale: Locale): String {
        val f = NumberFormat.getNumberInstance(locale)
        f.isGroupingUsed = true
        if (km < 10) { f.minimumFractionDigits = 1; f.maximumFractionDigits = 1 } else f.maximumFractionDigits = 0
        return f.format(if (km < 10) km else Math.round(km).toDouble())
    }

    /** Whether a place (degrees) is outside the terrain model's box, as [io.github.pini236.skiapp.map.Locator.feed] decides. */
    fun outside(t: Terrain, lat: Double, lon: Double) = !t.contains(Geo.x(lon), Geo.y(lat))

    /** Whether a place (degrees) is outside the model's box, without the model (the same box as the recorder's area). */
    fun outside(lat: Double, lon: Double) = !io.github.pini236.skiapp.dayrec.IdleWatch.inside(Geo.x(lon), Geo.y(lat))

    /**
     * NM2: the phone's freshest known place from the last [maxAgeMs] (any provider, nothing new asked for), when it is
     * outside the model; null when there is none, it is inside, or there is no permission.
     */
    fun recentOutside(ctx: Context, maxAgeMs: Long = 120_000L): Pair<Double, Double>? {
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return null
        val now = SystemClock.elapsedRealtimeNanos()
        var best: Location? = null
        for (p in runCatching { lm.allProviders }.getOrDefault(emptyList())) {
            @Suppress("MissingPermission")
            val l = runCatching { lm.getLastKnownLocation(p) }.getOrNull() ?: continue
            val age = (now - l.elapsedRealtimeNanos) / 1_000_000L
            if (age !in 0..maxAgeMs) continue
            if (best == null || l.elapsedRealtimeNanos > best.elapsedRealtimeNanos) best = l
        }
        val b = best ?: return null
        return if (outside(b.latitude, b.longitude)) b.latitude to b.longitude else null
    }
}

/**
 * The phone's compass heading (degrees clockwise from north), or null with no compass. Listens only while shown, on the
 * main thread (qa/system-boundaries.md: the rotation vector, registered with the main looper's handler).
 */
@Composable
internal fun rememberHeading(): State<Float?> {
    val ctx = LocalContext.current
    val heading = remember { mutableStateOf<Float?>(null) }
    DisposableEffect(Unit) {
        val sm = ctx.getSystemService(SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val rot = FloatArray(9); val ori = FloatArray(3)
        val listener = object : SensorEventListener {
            // main-thread-ok: registered with the main looper's handler; it only sets a Compose state
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rot, e.values)
                SensorManager.getOrientation(rot, ori)
                val deg = ((Math.toDegrees(ori[0].toDouble()) + 360.0) % 360.0).toFloat()
                val old = heading.value
                if (old == null || kotlin.math.abs(((deg - old + 540f) % 360f) - 180f) > 1f) heading.value = deg
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (sensor != null) runCatching { sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI, Handler(Looper.getMainLooper())) }
        onDispose { runCatching { sm?.unregisterListener(listener) } }
    }
    return heading
}

/** The arrow toward Gudauri (a real direction: it does not mirror in right-to-left languages). */
@Composable
private fun FarArrow(lat: Double, lon: Double, color: Color, size: androidx.compose.ui.unit.Dp) {
    val heading by rememberHeading()
    val turn = FarRules.arrow(FarRules.bearing(lat, lon), heading)
    Canvas(Modifier.size(size).rotate(turn)) {
        val w = this.size.width; val h = this.size.height
        drawPath(Path().apply { moveTo(w * .5f, h * .06f); lineTo(w * .86f, h * .9f); lineTo(w * .5f, h * .7f); lineTo(w * .14f, h * .9f); close() }, color)
    }
}

/** The veil over the mountain under the sheet: rgba(8,16,30,.28). */
private val VEIL = Color(0x08, 0x10, 0x1E, 0x47)

/** The tint of the card's circle: the accent at 11% (night 17%). */
private fun tint(k: SheetInk) = k.acc.copy(alpha = if (k.night) .17f else .11f)

/** The distance with its number isolated, so "1,430" reads whole inside a Hebrew sentence. */
@Composable
private fun kmText(lat: Double, lon: Double): String {
    val loc = androidx.compose.ui.platform.LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    return "⁦" + FarRules.formatKm(FarRules.km(lat, lon), loc) + "⁩"
}

/** What "where am I" found outside the model: where, and when (the card stays 6 s). */
internal class FarSeen(val lat: Double, val lon: Double, val at: Long)

/**
 * NM1, NM3: the card under the top pill in place of "not in Gudauri right now": the arrow, "Gudauri · 1,430 km from here",
 * and the line under it. Six seconds, or until a touch anywhere; announced once. [onGone] clears it.
 */
@Composable
internal fun BoxScope.FarCard(seen: FarSeen?, night: Boolean, below: Boolean, onGone: () -> Unit) {
    val k = remember(night) { SheetInk(night) }
    LaunchedEffect(seen) { if (seen != null) { Qa.log("redesign where far"); kotlinx.coroutines.delay(6000); onGone() } }
    // a touch on the mountain closes it; watched before the mountain gets it, never taken from it
    if (seen != null) Box(Modifier.matchParentSize().pointerInput(seen) {
        awaitPointerEventScope { awaitPointerEvent(PointerEventPass.Initial); onGone() }
    })
    AnimatedVisibility(seen != null, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = if (below) 106.dp else 62.dp, start = 12.dp, end = 12.dp),
        enter = fadeIn(tween(150)), exit = fadeOut(tween(150))) {
        val s = seen ?: return@AnimatedVisibility
        val resort = stringResource(R.string.common_site_name)
        val km = kmText(s.lat, s.lon)
        val sr = stringResource(R.string.app_sr_far, resort, km)
        val host = LocalView.current
        LaunchedEffect(s) { host.announceForAccessibility(sr) }
        val shape = RoundedCornerShape(22.dp)
        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).shadow(10.dp, shape, clip = false).clip(shape).background(k.surf.copy(alpha = 1f)).border(1.dp, k.line, shape)
            .clearAndSetSemantics { contentDescription = sr }.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(tint(k)), contentAlignment = Alignment.Center) { FarArrow(s.lat, s.lon, k.acc, 22.dp) }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.app_far_title, resort, km), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = k.ink)
                Text(stringResource(R.string.app_far_where_sub), fontFamily = Gudi, fontSize = 13.sp, color = k.ink2)
            }
        }
    }
}

/**
 * NM2: the tap on record far from the mountain (or the first reading of a recording far away): a bottom sheet over the
 * veiled mountain, the distance and the arrow, "see a sample ski day" and "record anyway". Back, a pull or a tap on the
 * veil dismiss it.
 */
@Composable
internal fun FarSheet(night: Boolean, lat: Double, lon: Double, onSample: () -> Unit, onAnyway: () -> Unit, onDismiss: () -> Unit) {
    val k = remember(night) { SheetInk(night) }
    LaunchedEffect(Unit) { Qa.log("redesign rec far") }
    val resort = stringResource(R.string.common_site_name)
    val km = kmText(lat, lon)
    val title = stringResource(R.string.app_far_title, resort, km)
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnBackPress = true, dismissOnClickOutside = false)) {
        BackHandler { onDismiss() }
        Box(Modifier.fillMaxSize().background(VEIL)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() }) {
            val top = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().pullSheet(onDown = onDismiss)
                .shadow(8.dp, top).clip(top).background(k.surf.copy(alpha = 1f)).border(1.dp, k.line, top)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .semantics { paneTitle = title }
                .navigationBarsPadding().padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(38.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(k.line))
                Box(Modifier.size(56.dp).clip(CircleShape).background(tint(k)), contentAlignment = Alignment.Center) { FarArrow(lat, lon, k.acc, 26.dp) }
                Text(title, fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 21.sp, color = k.ink, textAlign = TextAlign.Center)
                Text(stringResource(R.string.app_far_rec_sub), fontFamily = Gudi, fontSize = 13.5.sp, color = k.ink2, textAlign = TextAlign.Center)
                AccButton(stringResource(R.string.app_far_sample), k) { Qa.log("redesign rec far sample"); onSample() }
                TextLink(stringResource(R.string.app_far_rec_anyway), k) { Qa.log("redesign rec far anyway"); onAnyway() }
            }
        }
    }
}
