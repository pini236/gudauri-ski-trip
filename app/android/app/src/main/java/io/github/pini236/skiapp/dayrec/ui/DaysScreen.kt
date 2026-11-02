package io.github.pini236.skiapp.dayrec.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.dayrec.DayParams
import io.github.pini236.skiapp.dayrec.DayRec
import io.github.pini236.skiapp.dayrec.DayStore
import io.github.pini236.skiapp.ui.BackLink
import io.github.pini236.skiapp.ui.Ski
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * "My days" (round 29): the recorded days on the phone, the newest first, each one to open or to delete (two taps), and "delete
 * everything" (two taps), which also forgets the history the moments compare with. Nothing here leaves the phone.
 */
private class Row2(val file: File, val id: String, val start: Long, val descents: Int?, val maxKmh: Double?)

@Composable
fun DaysScreen(onOpen: (String) -> Unit, onBack: () -> Unit) {
    val c = Ski.colors
    val ctx = LocalContext.current
    var rows by remember { mutableStateOf<List<Row2>?>(null) }
    var version by remember { mutableStateOf(0) }
    var sure by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(version) {
        rows = withContext(Dispatchers.IO) {
            DayRec.store(ctx).days().asReversed().map { f ->
                val id = f.name.removePrefix("day-").removeSuffix(".jsonl")
                val start = id.toLongOrNull() ?: f.lastModified()
                val r = runCatching { JSONObject(DayStore.resultOf(f).readText()) }.getOrNull()
                val st = r?.optJSONObject("stats")
                Row2(f, id, start, st?.optInt("descents"), st?.let { if (it.has("max_kmh") && !it.isNull("max_kmh")) it.optDouble("max_kmh") else null })
            }
        }
    }
    Column(Modifier.fillMaxSize().background(c.snow).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.app_days_title), Modifier.weight(1f), style = Ski.type.title, color = c.ink)
            BackLink(stringResource(R.string.nav_home), onBack)
        }
        val list = rows
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (list != null && list.isEmpty()) Text(stringResource(R.string.app_days_empty), style = Ski.type.body, color = c.muted)
            val zone = ZoneId.of(DayParams.TZ)
            val fmt = DateTimeFormatter.ofPattern("EEEE d.M · HH:mm", Locale.getDefault())
            for (r in list.orEmpty()) {
                Row(Modifier.fillMaxWidth().background(c.paper).border(BorderStroke(1.dp, c.rule)).heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clickable(role = Role.Button) { onOpen(r.id) }.heightIn(min = 64.dp).padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.Center) {
                        Text(fmt.format(Instant.ofEpochMilli(r.start).atZone(zone)), style = Ski.type.bodyBold, color = c.ink)
                        Text(if (r.descents != null) stringResource(R.string.app_days_item, r.descents, LiveNumbers.maxKmh(r.maxKmh)) else stringResource(R.string.app_days_cut),
                            style = Ski.type.small, color = c.muted)
                    }
                    val asked = sure == r.id
                    Text(stringResource(if (asked) R.string.app_days_delete_sure else R.string.app_days_delete),
                        Modifier.clickable(role = Role.Button) { if (asked) { DayRec.store(ctx).delete(r.file); sure = null; version++ } else sure = r.id }
                            .heightIn(min = 64.dp).padding(horizontal = 14.dp).padding(vertical = 20.dp),
                        style = Ski.type.bodyBold, color = c.red)
                }
            }
            if (!list.isNullOrEmpty()) {
                val all = sure == ALL
                Text(stringResource(if (all) R.string.app_days_delete_all_sure else R.string.app_days_delete_all),
                    Modifier.fillMaxWidth().clickable(role = Role.Button) { if (all) { DayRec.store(ctx).deleteAll(); sure = null; version++ } else sure = ALL }
                        .heightIn(min = 52.dp).padding(vertical = 14.dp),
                    style = Ski.type.bodyBold, color = c.red)
            }
        }
    }
}

private const val ALL = "*"
