package io.github.pini236.skiapp.redesign

import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.dayrec.DayAnalysis
import io.github.pini236.skiapp.dayrec.DayParams
import io.github.pini236.skiapp.dayrec.SampleDay
import io.github.pini236.skiapp.dayrec.ui.DayModel
import io.github.pini236.skiapp.fx.Haptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** What an ended recording with no descents says (EM1 to EM4): pure, so it is tested. */
internal object EmptyRules {
    /** The sample card's numbers: a whole number of km/h, metres rounded to tens. */
    fun roundKmh(k: Double?): Int = Math.round(k ?: 0.0).toInt()
    fun roundTens(m: Double): Int = (Math.round(m / 10.0) * 10).toInt()
    /** "In Gudauri" when at least one reading is inside the resort's area; no location: neither (shown as a dash). */
    fun where(located: Boolean, anyInside: Boolean): Boolean? = if (!located) null else anyInside
}

/**
 * The end of a recording with no descents (handoff 2026-11-25-redesign-empty-end): what there was (time, location, where), and a sample
 * day's numbers, to show what a real day gives. "Back to the mountain" and the X leave (the recording stays in "my days");
 * "delete" takes two touches.
 */
@Composable
internal fun BoxScope.EmptyEnd(startMs: Long, endMs: Long, located: Boolean, anyInside: Boolean, night: Boolean, haptics: Haptics, onSample: () -> Unit,
                               onDelete: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val zone = ZoneId.of(DayParams.TZ)
    val ink = if (night) Color(0xFFEAF0F7) else Color(0xFF0E2233)
    val muted = if (night) Color(0xB3EAF0F7) else Color(0xFF5B6B7B)
    val glass = if (night) Color(0xEB0F1828) else Color(0xF5FFFFFF)
    val rule = if (night) Color(0x33FFFFFF) else Color(0x1F0E2233)
    val accent = if (night) Color(0xFFB79CFF) else Color(0xFF6B3FD4)
    val warn = if (night) Color(0xFFFFB454) else Color(0xFFB45F06)
    val red = if (night) Color(0xFFFF7468) else Color(0xFFD1342B)
    // the sample day's numbers (the same day as "my days" shows), computed off the main thread
    var sample by remember { mutableStateOf<DayModel.Summary?>(null) }
    LaunchedEffect(Unit) {
        sample = withContext(Dispatchers.Default) {
            runCatching {
                val f = DayAnalysis.ensure(context, SampleDay.file(context)) ?: return@runCatching null
                DayModel.build(JSONObject(f.readText()), zone).summary
            }.getOrNull()
        }
    }
    var sure by remember { mutableStateOf(false) }
    LaunchedEffect(sure) { if (sure) { delay(3000); sure = false } }
    val hm = DateTimeFormatter.ofPattern("HH:mm")
    val whenText = stringResource(R.string.app_empty_when, hm.format(Instant.ofEpochMilli(endMs).atZone(zone)))
    val title = stringResource(R.string.app_empty_title)
    val back = stringResource(R.string.app_vill_back)
    val exampleTag = stringResource(R.string.app_day_sample)

    // the top: the example tag, and the X
    Row(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(glass).border(1.dp, rule, CircleShape).semantics { contentDescription = back; role = Role.Button }
            .clickable { haptics.tick(.3f); onBack() }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(16.dp)) {
                val s = Stroke(2.dp.toPx(), cap = StrokeCap.Round)
                drawLine(ink, Offset(0f, 0f), Offset(size.width, size.height), s.width, StrokeCap.Round)
                drawLine(ink, Offset(size.width, 0f), Offset(0f, size.height), s.width, StrokeCap.Round)
            }
        }
        Box(Modifier.weight(1f))
        Row(Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).background(glass).border(1.dp, rule, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.app_empty_sample_title), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ink)
            Text(exampleTag, fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = accent)
        }
    }

    // the sheet
    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().shadow(16.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
        .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(glass).navigationBarsPadding()
        .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 30.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(rule))
        Text(title, Modifier.semantics { heading() }, fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = ink, textAlign = TextAlign.Center)
        Text(whenText, fontFamily = Gudi, fontSize = 15.sp, color = muted, textAlign = TextAlign.Center)

        // three facts in one frame
        val where = EmptyRules.where(located, anyInside)
        val facts = listOf(
            Triple("⁦" + io.github.pini236.skiapp.dayrec.ui.LiveNumbers.elapsed((endMs - startMs).coerceAtLeast(0L)) + "⁩", stringResource(R.string.app_empty_rec), ink),
            Triple(stringResource(if (located) R.string.app_empty_loc_found else R.string.app_empty_loc_none), stringResource(R.string.app_empty_loc), if (located) ink else warn),
            Triple(when (where) { true -> stringResource(R.string.app_empty_where_in); false -> stringResource(R.string.app_empty_where_out); null -> "—" }, stringResource(R.string.app_empty_where), ink),
        )
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(18.dp)).border(1.dp, rule, RoundedCornerShape(18.dp))) {
            facts.forEachIndexed { i, (v, label, c) ->
                if (i > 0) Box(Modifier.fillMaxHeightPx().width(1.dp).background(rule))
                Column(Modifier.weight(1f).padding(vertical = 12.dp, horizontal = 6.dp).clearAndSetSemantics { contentDescription = "$v, $label" },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(v, fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = c, textAlign = TextAlign.Center)
                    Text(label, fontFamily = Gudi, fontSize = 12.5.sp, color = muted, textAlign = TextAlign.Center)
                }
            }
        }

        // the sample day: a touch opens it
        val sa = stringResource(R.string.app_empty_sample_a11y)
        val tint = if (night) Color(0x1FB79CFF) else Color(0x176B3FD4)
        val tintLine = if (night) Color(0x47B79CFF) else Color(0x386B3FD4)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(tint).border(1.dp, tintLine, RoundedCornerShape(18.dp))
            .semantics { contentDescription = sa; role = Role.Button }.clickable { haptics.tick(.3f); onSample() }.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.app_empty_sample_title), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 15.5.sp, color = ink)
                Text(exampleTag, fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = accent)
            }
            sample?.let { sm ->
                val kmh = EmptyRules.roundKmh(sm.maxKmh); val m = java.text.NumberFormat.getIntegerInstance().format(EmptyRules.roundTens(sm.dropM))
                // the arguments by the order of the placeholders in the Hebrew text: {kmh}, {m}, {n}
                Text(pluralStringResource(R.plurals.app_empty_sample_line, sm.descents, kmh.toString(), m, sm.descents.toString()),
                    fontFamily = Gudi, fontSize = 13.5.sp, color = muted)
            }
        }

        // the buttons: back to the mountain, delete (two touches)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(26.dp)).background(accent).semantics { role = Role.Button }
                .clickable { haptics.tick(.4f); onBack() }, contentAlignment = Alignment.Center) {
                Text(back, fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (night) Color(0xFF0F1828) else Color.White, maxLines = 1)
            }
            Box(Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(26.dp)).border(BorderStroke(1.5.dp, rule), RoundedCornerShape(26.dp)).semantics { role = Role.Button }
                .clickable { haptics.tick(.4f); if (sure) onDelete() else sure = true }, contentAlignment = Alignment.Center) {
                Text(stringResource(if (sure) R.string.app_empty_delete_confirm else R.string.app_days_delete), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = red, maxLines = 1)
            }
        }
    }
}

private fun Modifier.fillMaxHeightPx(): Modifier = this.then(Modifier.fillMaxHeight())
