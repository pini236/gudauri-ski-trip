package io.github.pini236.skiapp.game

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.fx.FxPrefs
import io.github.pini236.skiapp.fx.Haptics
import io.github.pini236.skiapp.fx.Synth
import io.github.pini236.skiapp.i18n.Lang
import io.github.pini236.skiapp.qa.Qa
import io.github.pini236.skiapp.telemetry.Telemetry
import io.github.pini236.skiapp.ui.Motion
import io.github.pini236.skiapp.ui.Ski
import kotlin.math.roundToInt

private val INK = Color(0xFF13233A)

/**
 * Fresh snow on the phone (13.6), the site's game (site/games/fresh-snow): one look, fresh snow in a low morning sun,
 * the same by day and by night. The snow or the frozen lake fills the screen; the title and the number at the top, and
 * the scenes, the kinds of snow and what to touch with at the bottom.
 */
@Composable
fun FreshSnowScreen(haptics: Haptics, onBack: () -> Unit, onBest: () -> Unit = {}) {
    val context = LocalContext.current
    val synth = remember { Synth(context) }
    var lake by remember { mutableStateOf(false) }
    var kind by remember { mutableStateOf(SnowKind.POWDER) }
    var tool by remember { mutableStateOf(FreshSnowView.Tool.HAND) }
    var stat by remember { mutableIntStateOf(0) }
    var touched by remember { mutableStateOf(false) }
    var sound by remember { mutableStateOf(FxPrefs.sound(context)) }
    val view = remember {
        FreshSnowView(context, synth, haptics, Motion.reduced(context)).apply {
            display = Lang.typeface(context, Lang.current(context.resources), true)
            chainText = { n -> context.getString(R.string.game_fresh_chain, n.toString()) }
        }
    }
    // usage statistics, as the site: no end here, so a game is the play in one scene (level: snow or lake), from the
    // first touch until switching the scene or leaving; score is the number on screen, and the record is the site's
    var started by remember { mutableStateOf(0L) }
    var level by remember { mutableStateOf("snow") }
    fun gameStart() { if (started == 0L) { started = System.currentTimeMillis(); level = if (lake) "lake" else "snow"; Telemetry.event("game_start", mapOf("game" to "fresh", "level" to level)) } }
    fun gameEnd() {
        if (started == 0L) return
        val best = Bests.ended(context, "fresh", level, stat)
        Telemetry.event("game_end", mapOf("game" to "fresh", "level" to level, "score" to stat, "seconds" to ((System.currentTimeMillis() - started) / 1000.0).roundToInt(),
            "completed" to true, "best" to best))
        started = 0L
        if (best) onBest()
    }
    view.listener = object : FreshSnowView.Listener {
        override fun onStat(percent: Int) { stat = percent; Qa.log("fresh ${if (view.lake) "lake" else "snow"} $percent%") }
        override fun onTouched() { touched = true; gameStart() }
        override fun onChain(n: Int, label: Boolean) { if (label) Qa.log("fresh chain $n") }
    }
    DisposableEffect(Unit) { onDispose { gameEnd() } }

    Box(Modifier.fillMaxSize().background(Color(0xFFE8F0F8))) {
        AndroidView({ view }, Modifier.fillMaxSize().semantics {
            contentDescription = context.getString(if (lake) R.string.game_fresh_lake_canvas_label else R.string.game_fresh_snow_canvas_label)
        })
        // the title, what the snow is like, and the number
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).alpha(.85f)) {
                Text(stringResource(R.string.game_fresh_title), style = Ski.type.title.copy(fontSize = 38.sp, lineHeight = 34.sp,
                    shadow = Shadow(Color.White, Offset(0f, 2f), 0f)), color = if (lake) Color.White else INK)
                Text(stringResource(if (lake) R.string.game_fresh_lake_desc else when (kind) {
                    SnowKind.POWDER -> R.string.game_fresh_kind_powder_desc; SnowKind.CRUST -> R.string.game_fresh_kind_crust_desc; SnowKind.WET -> R.string.game_fresh_kind_wet_desc
                }), style = Ski.type.small.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold), color = if (lake) Color.White else INK)
            }
            Column(Modifier.background(Color(0xD9FFFFFF)).padding(horizontal = 10.dp, vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$stat%", style = Ski.type.title.copy(fontSize = 26.sp, lineHeight = 26.sp), color = INK)
                Text(stringResource(if (lake) R.string.game_fresh_stat_broken else R.string.game_fresh_stat_tracks), style = Ski.type.bodyBold.copy(fontSize = 12.5.sp), color = INK)
            }
        }
        // the first hint, gone at the first touch
        val tip by animateFloatAsState(if (touched) 0f else 1f, tween(600), label = "tip")
        if (tip > 0f) Text(stringResource(R.string.game_fresh_tip), Modifier.align(Alignment.Center).padding(bottom = 60.dp).alpha(tip).widthIn(max = 320.dp)
            .background(Color(0xD113233A)).padding(horizontal = 16.dp, vertical = 10.dp), style = Ski.type.bodyBold.copy(fontSize = 15.sp), color = Color.White, textAlign = TextAlign.Center)
        // the bar
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0x0013233A), Color(0x5913233A))))
            .navigationBarsPadding().padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BarRow {
                Box(Modifier.heightIn(min = 44.dp).background(Color(0xE013233A)).clickable(role = Role.Button, onClick = onBack).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.game_fresh_back_to_games), style = Ski.type.bodyBold.copy(fontSize = 14.sp), color = Color.White)
                }
                Pick(stringResource(R.string.game_fresh_scene_snow), !lake, small = true) { if (lake) { gameEnd(); lake = false; view.lake = false } }
                Pick(stringResource(R.string.game_fresh_scene_lake), lake, small = true) { if (!lake) { gameEnd(); lake = true; view.lake = true } }
                val soundLabel = stringResource(R.string.game_fresh_sound)
                Pick(if (sound) "🔊" else "🔇", sound, small = true, label = soundLabel) {
                    sound = !sound; FxPrefs.set(context, "sound", sound); Telemetry.event("settings_change", mapOf("setting" to "sound", "on" to sound))
                }
            }
            if (lake) BarRow { Pick("🧊", false, sub = stringResource(R.string.game_fresh_refreeze)) { view.refreeze() } }
            else {
                BarRow {
                    for (k in SnowKind.entries) Pick(stringResource(when (k) {
                        SnowKind.POWDER -> R.string.game_fresh_kind_powder; SnowKind.CRUST -> R.string.game_fresh_kind_crust; SnowKind.WET -> R.string.game_fresh_kind_wet
                    }), kind == k, small = true) { kind = k; view.kind = k }
                }
                BarRow {
                    for (t in FreshSnowView.Tool.entries) Pick(when (t) {
                        FreshSnowView.Tool.HAND -> "☝️"; FreshSnowView.Tool.BOOT -> "🥾"; FreshSnowView.Tool.SKI -> "🎿"; FreshSnowView.Tool.BALL -> "⚪"; FreshSnowView.Tool.CAT -> "🚜"
                    }, tool == t, Modifier.weight(1f), sub = stringResource(when (t) {
                        FreshSnowView.Tool.HAND -> R.string.game_fresh_tool_hand; FreshSnowView.Tool.BOOT -> R.string.game_fresh_tool_boot; FreshSnowView.Tool.SKI -> R.string.game_fresh_tool_ski
                        FreshSnowView.Tool.BALL -> R.string.game_fresh_tool_ball; FreshSnowView.Tool.CAT -> R.string.game_fresh_tool_cat
                    })) { tool = t; view.tool = t }
                    Pick("❄️", false, Modifier.weight(1f), sub = stringResource(R.string.game_fresh_new_snow)) { view.newSnow() }
                }
            }
        }
    }
}

@Composable
private fun BarRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) { content() }
}

/** A button of the bar (the site's .row button): white on the snow, ink when it is the one chosen. */
@Composable
private fun Pick(text: String, on: Boolean, modifier: Modifier = Modifier, small: Boolean = false, sub: String? = null, label: String? = null, onClick: () -> Unit) {
    Column(modifier.heightIn(min = if (small) 40.dp else 48.dp).widthIn(min = if (sub != null) 0.dp else 52.dp)
        .shadow(1.dp, RectangleShape).background(if (on) INK else Color(0xE0FFFFFF), RoundedCornerShape(0.dp))
        .clickable(role = Role.Button, onClick = onClick).semantics { selected = on; if (label != null) contentDescription = label }
        .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(text, style = Ski.type.bodyBold.copy(fontSize = if (sub != null) 20.sp else if (small) 12.5.sp else 13.5.sp, lineHeight = if (sub != null) 22.sp else 16.sp),
            color = if (on) Color.White else INK, maxLines = 1)
        // one word (Snowcat, Ратрак) shrinks to fit rather than break in the middle; two words take two lines
        if (sub != null) BasicText(sub, style = Ski.type.bodyBold.copy(fontSize = 11.5.sp, lineHeight = 13.sp, color = if (on) Color.White else INK, textAlign = TextAlign.Center),
            maxLines = if (' ' in sub.trim()) 2 else 1, autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 11.5.sp, stepSize = .5.sp))
    }
}
