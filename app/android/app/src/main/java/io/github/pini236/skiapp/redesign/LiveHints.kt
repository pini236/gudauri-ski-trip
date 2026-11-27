package io.github.pini236.skiapp.redesign

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.map.MapScene
import io.github.pini236.skiapp.qa.Qa
import kotlinx.coroutines.delay
import kotlin.math.hypot

/**
 * The first time (S09): no tour, one hint at a time on the mountain itself, each once ever (kept on the phone, nothing
 * measured). Hint 1, a ring on a blue run near the middle, 1,200 ms after the mountain is up on the first opening. Hints 2
 * and 3 point at "where to?" and "the village" in the bottom bar, which comes with the village and the shelf (K17, K06).
 */
internal object Hints {
    private const val PREFS = "redesign"
    const val MOUNTAIN = "hints_mountain"
    const val VILLAGE = "hints_village"
    /** UX pass part 1: the two hints that were in the village now come with the menu; who finished the village ones does not see them again. */
    const val MENU = "hints_menu"
    private fun sp(ctx: android.content.Context) = ctx.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
    /** The next step of a sequence (0 for the first): -1 once it was finished or closed. Kept on the phone only. */
    fun next(ctx: android.content.Context, key: String): Int = sp(ctx).getInt(key, 0)
    fun setNext(ctx: android.content.Context, key: String, n: Int) { sp(ctx).edit().putInt(key, n).apply() }
    fun seen(ctx: android.content.Context, id: String) = sp(ctx).getBoolean(id, false)
    fun done(ctx: android.content.Context, id: String) { sp(ctx).edit().putBoolean(id, true).apply(); Qa.log("redesign hint done $id") }
    /** Both sequences again (Settings, and the emulator's qa.redesign hint:reset). */
    fun reset(ctx: android.content.Context) {
        val p = sp(ctx); val e = p.edit(); p.all.keys.filter { it.startsWith("hint") }.forEach { e.remove(it) }; e.apply()
    }
    /** Bumped by Settings' "show the hints again": the mountain starts its hints from the first again. */
    var again by androidx.compose.runtime.mutableIntStateOf(0)
}
