package io.github.pini236.skiapp.redesign

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.status.reducedMotion

/**
 * Part 6, the edges (design/handoff/2026-11-23-redesign-edges, section 2): the mountain is always there and no screen
 * is blocked. A state says itself in a line inside its sheet ([StatusLine]); a failed action says so right above the
 * button that was pressed ([ErrorNear]); what waits for signal carries a dashed tag ([WaitTag]); what waits for the
 * server the first time shows rows in the shape of the content ([LoadingRows]).
 */
internal enum class EdgeIcon { NO_SIGNAL, CLOCK, WARN, BELL_OFF }

/** A small line icon (18, 1.6 stroke), drawn: the app's icon set has no "no signal" or "bell off". */
@Composable
internal fun EdgeGlyph(icon: EdgeIcon, color: Color, size: Dp = 18.dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.width / 24f
        val st = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, Offset(x1 * s, y1 * s), Offset(x2 * s, y2 * s), st.width, StrokeCap.Round)
        when (icon) {
            EdgeIcon.NO_SIGNAL -> {
                // four bars, the last two hollow, and a slash
                for (i in 0 until 4) {
                    val h = 4f + i * 4f; val x = 4f + i * 5f
                    if (i < 2) drawRect(color, Offset(x * s, (20f - h) * s), androidx.compose.ui.geometry.Size(3f * s, h * s))
                    else drawRect(color, Offset(x * s, (20f - h) * s), androidx.compose.ui.geometry.Size(3f * s, h * s), style = Stroke(1.2.dp.toPx()))
                }
                line(3f, 3f, 21f, 21f)
            }
            EdgeIcon.CLOCK -> { drawCircle(color, 9f * s, Offset(12f * s, 12f * s), style = st); line(12f, 7f, 12f, 12f); line(12f, 12f, 15.5f, 14f) }
            EdgeIcon.WARN -> {
                val p = androidx.compose.ui.graphics.Path().apply { moveTo(12f * s, 3.5f * s); lineTo(21.5f * s, 20f * s); lineTo(2.5f * s, 20f * s); close() }
                drawPath(p, color, style = st); line(12f, 9.5f, 12f, 14f); drawCircle(color, 1.1f * s, Offset(12f * s, 17f * s))
            }
            EdgeIcon.BELL_OFF -> {
                val p = androidx.compose.ui.graphics.Path().apply {
                    moveTo(6f * s, 16f * s); lineTo(6f * s, 11f * s); cubicTo(6f * s, 7.7f * s, 8.7f * s, 5f * s, 12f * s, 5f * s)
                    cubicTo(15.3f * s, 5f * s, 18f * s, 7.7f * s, 18f * s, 11f * s); lineTo(18f * s, 16f * s); lineTo(20f * s, 18f * s); lineTo(4f * s, 18f * s); close()
                }
                drawPath(p, color, style = st); line(10f, 20.5f, 14f, 20.5f); line(3f, 3f, 21f, 21f)
            }
        }
    }
}

/**
 * A state line inside a sheet (`ofl`): `line` behind it, radius 14, 9 by 12, text 13 at 500, an 18 icon at its start.
 * [bad] (`ofl bad`): red at 14 % behind, the icon red, the text the usual ink, and a 44 close at its end; it stays until
 * closed. A screen reader hears it when it appears (status, or alert when bad).
 */
@Composable
internal fun StatusLine(text: String, k: SheetInk, icon: EdgeIcon, modifier: Modifier = Modifier, bad: Boolean = false, onClose: (() -> Unit)? = null,
                        action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (bad) k.red.copy(alpha = .14f) else k.line)
        .semantics(mergeDescendants = true) { liveRegion = if (bad) LiveRegionMode.Assertive else LiveRegionMode.Polite }
        .padding(start = 12.dp, end = if (onClose != null) 0.dp else 12.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        EdgeGlyph(icon, if (bad) k.red else k.ink2)
        Text(text, Modifier.weight(1f), fontFamily = Gudi, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = k.ink)
        action?.invoke()
        if (onClose != null) {
            val close = stringResource(R.string.common_close)
            Box(Modifier.size(44.dp).semantics { contentDescription = close; role = Role.Button }.clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(14.dp)) {
                    val w = 1.6.dp.toPx()
                    drawLine(k.ink, Offset.Zero, Offset(size.width, size.height), w, StrokeCap.Round)
                    drawLine(k.ink, Offset(size.width, 0f), Offset(0f, size.height), w, StrokeCap.Round)
                }
            }
        }
    }
}

/** A failed action, right above the button pressed (`errl`): 13.5 at 500 in red with an 18 warning, read as an alert. */
@Composable
internal fun ErrorNear(text: String, k: SheetInk, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Assertive },
        verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.padding(top = 1.dp)) { EdgeGlyph(EdgeIcon.WARN, k.red) }
        Text(text, Modifier.weight(1f), fontFamily = Gudi, fontWeight = FontWeight.Medium, fontSize = 13.5.sp, color = k.red)
    }
}

/** "Waiting for signal" (`wtag`): 20 high, radius 9, a dashed ink2 edge, 11 at 700, a 12 clock. */
@Composable
internal fun WaitTag(k: SheetInk) {
    Row(Modifier.heightIn(min = 20.dp).drawBehind {
        drawRoundRect(k.ink2, cornerRadius = CornerRadius(9.dp.toPx()), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f))))
    }.padding(horizontal = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        EdgeGlyph(EdgeIcon.CLOCK, k.ink2, 12.dp)
        Text(stringResource(R.string.app_g_wait_tag), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = k.ink2)
    }
}

/**
 * U11: a sheet waiting for the server the first time: [n] rows in the shape of the content (a 36 circle, a 12 bar and a
 * 10 one under it, radius 6, `line`), and "loading…" under them (status for a screen reader). No spinner; they pulse
 * softly, and stay still with the system's animations off.
 */
@Composable
internal fun LoadingRows(k: SheetInk, n: Int = 3) {
    val still = reducedMotion()
    val a = if (still) 1f else rememberInfiniteTransition(label = "skl").animateFloat(.55f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "skl").value
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.alpha(a), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            for (i in 0 until n) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(k.line))
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(Modifier.width(listOf(170, 130, 150)[i % 3].dp).height(12.dp).clip(RoundedCornerShape(6.dp)).background(k.line))
                    Box(Modifier.width(listOf(110, 90, 120)[i % 3].dp).height(10.dp).clip(RoundedCornerShape(6.dp)).background(k.line))
                }
            }
        }
        Text(stringResource(R.string.app_loading), Modifier.semantics { liveRegion = LiveRegionMode.Polite }, fontFamily = Gudi, fontSize = 12.5.sp, color = k.ink2)
    }
}

/** U01: how often our sheet offers the reminders before the phone's own question (section 3): at most twice, once per meetup. */
internal object NotifOffer {
    const val MAX = 2
    /** Offer for [meetups] ahead with a reminder on, given the ids already offered for: a new one, and fewer than two offers so far. */
    fun next(meetups: List<String>, offered: Set<String>, count: Int): String? =
        if (count >= MAX) null else meetups.firstOrNull { it !in offered }
}

/** U01's offers, kept on the phone: which meetups were offered for, and how many times (nothing is measured). */
internal object NotifOffers {
    private const val PREFS = "redesign"
    private fun prefs(c: android.content.Context) = c.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
    fun offered(c: android.content.Context): Set<String> = prefs(c).getStringSet("notif_offered", emptySet())!!
    fun count(c: android.content.Context): Int = prefs(c).getInt("notif_offers", 0)
    fun offer(c: android.content.Context, id: String) {
        prefs(c).edit().putStringSet("notif_offered", offered(c) + id).putInt("notif_offers", count(c) + 1).apply()
    }
}

/**
 * U05: whether the phone has a network (the architect, 24.11.2026): true only with a validated default network, false
 * with none or one that is not validated, null until the first answer (the pill keeps its usual words then). Watched
 * only while the app is started, on the main thread. For the pill only: nothing waits on it, and nothing is sent, kept or
 * measured. [onBack]: the network came back after none (the group's queue goes out at once).
 */
internal object Online {
    fun validated(caps: android.net.NetworkCapabilities?): Boolean =
        caps != null && caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)

    /** The pill's words go "no recent information · no signal" only in the season, with no live report, and no network known. */
    fun pillOffline(online: Boolean?, live: Boolean, inSeason: Boolean): Boolean = online == false && !live && inSeason
}

@Composable
internal fun rememberOnline(onBack: () -> Unit): Boolean? {
    val context = androidx.compose.ui.platform.LocalContext.current
    var online by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Boolean?>(null) }
    val back by androidx.compose.runtime.rememberUpdatedState(onBack)
    androidx.compose.runtime.DisposableEffect(Unit) {
        val cm = context.getSystemService(android.net.ConnectivityManager::class.java)
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        var on = false
        fun set(v: Boolean) {
            val was = online
            if (was == v) return
            online = v
            io.github.pini236.skiapp.qa.Qa.log("redesign edge online $v")
            if (was == false && v) back()
        }
        val cb = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: android.net.Network, caps: android.net.NetworkCapabilities) = set(Online.validated(caps))
            override fun onLost(network: android.net.Network) = set(false)
            override fun onUnavailable() = set(false)
        }
        fun start() { if (on || cm == null) return; on = true
            // with no default network at all, no callback comes: say so after a moment
            runCatching { cm.registerDefaultNetworkCallback(cb, mainHandler) }.onFailure { on = false; return }
            if (cm.activeNetwork == null) set(false)
        }
        fun stop() { if (!on) return; on = false; runCatching { cm?.unregisterNetworkCallback(cb) } }
        val life = (context as? androidx.lifecycle.LifecycleOwner)?.lifecycle
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            when (e) {
                androidx.lifecycle.Lifecycle.Event.ON_START -> start()
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> stop()
                else -> {}
            }
        }
        life?.addObserver(obs)
        if (life?.currentState?.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED) != false) start()
        onDispose { life?.removeObserver(obs); stop() }
    }
    return online
}
