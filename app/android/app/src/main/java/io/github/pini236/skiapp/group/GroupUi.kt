package io.github.pini236.skiapp.group

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.home.drawSnow
import io.github.pini236.skiapp.home.snowCap
import io.github.pini236.skiapp.ui.Ski
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/*
 * The pieces the account and group screens share (design/round10/build.py: the snowy cards, the sheet, the switch,
 * the buttons). Square corners, touch targets of at least 44, the tokens' colours.
 */

/** A paper card with a coloured top edge and fresh snow on it (A1, Q3). */
@Composable
fun SnowCard(edge: Color, seed: Int, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = Ski.colors
    Column(
        modifier.fillMaxWidth()
            .shadow(6.dp, RectangleShape, ambientColor = Color(0x1A13233A), spotColor = Color(0x1A13233A))
            .background(c.paper)
            .drawWithCache {
                val snow = snowCap(seed, size.width, density)
                onDrawWithContent {
                    drawContent()
                    drawRect(edge, Offset.Zero, Size(size.width, 6.dp.toPx()))
                    drawSnow(snow, density)
                }
            }
            .padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 16.dp),
        content = content,
    )
}

/** A big title in the sign voice, at a given canvas size. */
@Composable
fun Display(text: String, size: Float, modifier: Modifier = Modifier, color: Color = Ski.colors.ink) =
    Text(text, modifier, style = Ski.type.title.copy(fontSize = Ski.type.title.fontSize * (size / 44f)), color = color)

@Composable
fun Muted(text: String, modifier: Modifier = Modifier, size: Float = 13.5f) =
    Text(text, modifier, style = Ski.type.small.copy(fontSize = size.sp), color = Ski.colors.muted)

/** A bottom sheet over the dimmed screen (A2, A5, Q7): a tap outside or Back closes it. */
@Composable
fun BoxScope.Sheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    BackHandler(onBack = onDismiss)
    Box(Modifier.matchParentSize().background(Color(0x8C0D1522)).clickable(MutableInteractionSource(), null, onClick = onDismiss))
    Column(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .shadow(16.dp, RectangleShape).background(Ski.colors.paper)
            .clickable(MutableInteractionSource(), null) {} // taps inside stay inside
            .navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 24.dp),
    ) {
        Box(Modifier.align(Alignment.CenterHorizontally).width(44.dp).height(5.dp).background(Ski.colors.rule))
        Box(Modifier.height(14.dp))
        content()
    }
}

/** The square switch of the canvas (.sw): ink border, blue when on. */
@Composable
fun Toggle(label: String, sub: String?, on: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val c = Ski.colors
    val state = stringResource(if (on) R.string.app_on else R.string.app_off)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(enabled = enabled, role = Role.Switch) { onChange(!on) }
            .semantics { stateDescription = state }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = Ski.type.bodyBold.copy(fontSize = 15.5.sp), color = c.ink)
            if (sub != null) Text(sub, style = Ski.type.small, color = c.muted)
        }
        Box(Modifier.width(46.dp).height(28.dp).background(if (on) c.accent else c.paper).border(2.dp, if (on) c.accent else c.ink)) {
            Box(Modifier.align(if (on) Alignment.CenterStart else Alignment.CenterEnd).padding(horizontal = 3.dp).size(18.dp).background(if (on) c.onAccent else c.ink))
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.rule))
}

enum class Look { INK, GHOST, DANGER, GOLD }

/** The other buttons of the canvas: ink, an outlined one, red for a destructive one. */
@Composable
fun Button2(text: String, look: Look, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, small: Boolean = false, full: Boolean = true, enabled: Boolean = true) {
    val c = Ski.colors
    val (bg, fg, line) = when (look) {
        Look.INK -> Triple(if (c.dark) Color(0xFF2C3E5C) else c.ink, Color.White, null)
        Look.GHOST -> Triple(Color.Transparent, c.ink, c.ink)
        Look.DANGER -> Triple(Color.Transparent, c.red, c.red)
        Look.GOLD -> Triple(Color(0xFFF4B942), Color(0xFF13233A), null)
    }
    Row(
        modifier.let { if (full) it.fillMaxWidth() else it }.background(bg).let { if (line != null) it.border(2.dp, line) else it }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).heightIn(min = if (small) 44.dp else 52.dp).padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(20.dp), tint = fg)
        Text(text, style = Ski.type.bodyBold.copy(fontSize = if (small) 15.sp else 16.5.sp), color = if (enabled) fg else fg.copy(alpha = .5f))
    }
}

/**
 * "Continue with Google": white, a grey outline and the G mark, as Google's button looks on Android. The real
 * sign-in sheet comes from the system (Credential Manager).
 */
@Composable
fun GoogleButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().background(Color.White).border(1.dp, Color(0xFF747775)).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 52.dp).padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(20.dp).border(2.dp, Color(0xFF747775), androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
            Text("G", style = Ski.type.bodyBold.copy(fontSize = 12.sp, textAlign = TextAlign.Center), color = Color(0xFF1F1F1F))
        }
        Text(text, style = Ski.type.bodyBold.copy(fontSize = 16.5.sp), color = Color(0xFF1F1F1F))
    }
}

/** A person's first letter in a square (A4, Q10): ink for admins, pale for members. */
@Composable
fun Avatar(name: String, strong: Boolean, sizeDp: Int = 36) {
    val c = Ski.colors
    Box(Modifier.size(sizeDp.dp).background(if (strong) c.ink else c.grid), contentAlignment = Alignment.Center) {
        Text(name.trim().take(1), style = Ski.type.title.copy(fontSize = (sizeDp * .66f).sp), color = if (strong) c.paper else c.ink)
    }
}

/** The way a refusal from the server reads in the user's language. */
@Composable
fun errorText(e: Throwable?): String? {
    if (e == null) return null
    val code = (e as? ApiException)?.code ?: "offline"
    return stringResource(when (code) {
        "not_admin" -> R.string.app_err_not_admin
        "last_admin" -> R.string.app_err_last_admin
        "admin_must_register" -> R.string.app_err_admin_must_register
        "not_registered", "not_a_guest" -> R.string.app_err_not_registered
        "too_many_groups" -> R.string.app_err_too_many_groups
        "google_not_ready" -> R.string.app_err_google_not_ready
        "cancelled" -> R.string.app_err_cancelled
        "offline", "network" -> R.string.app_err_offline
        else -> R.string.app_err_general
    })
}

/** One action at a time: busy while it runs, the error if it failed. */
class Runner(private val scope: CoroutineScope) {
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<Throwable?>(null)
    fun run(block: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null
        scope.launch {
            try { block() } catch (e: Throwable) { if (e is kotlinx.coroutines.CancellationException) throw e; error = e } finally { busy = false }
        }
    }
}

@Composable
fun rememberRunner(): Runner { val scope = rememberCoroutineScope(); return remember { Runner(scope) } }

/** The error line under a form, in red, read out when it appears. */
@Composable
fun ErrorLine(r: Runner) {
    val t = errorText(r.error) ?: return
    Text(t, Modifier.fillMaxWidth().semantics { contentDescription = t }, style = Ski.type.bodyBold.copy(fontSize = 14.sp), color = Ski.colors.red)
}

/** A plain scrolling page under the top bar, with the canvas's side margins. */
@Composable
fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
}
