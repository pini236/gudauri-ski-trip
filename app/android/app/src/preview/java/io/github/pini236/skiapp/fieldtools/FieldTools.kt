package io.github.pini236.skiapp.fieldtools

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import io.github.pini236.skiapp.BuildConfig
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.dayrec.DayParams
import io.github.pini236.skiapp.ui.Ski
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The field testers' tools, in the test build only (src/preview; the store build has an empty stand-in). Two places call
 * it: the about page (type the code Pini gave you, turn the mode off, withdraw the consent) and a ski day's page (send that
 * day's raw log). Nothing here is measured, logged or sent by the app: the file goes where the tester chooses in the system's
 * share sheet, cut to the site's rectangle (RawExport), under a name without his own.
 */
object FieldTools {
    @Composable
    fun AboutRow() {
        val ctx = LocalContext.current
        var on by remember { mutableStateOf(TesterGate.unlocked(ctx)) }
        var open by remember { mutableStateOf(false) }
        var code by remember { mutableStateOf("") }
        var bad by remember { mutableStateOf(false) }
        Action(stringResource(R.string.tester_about_row) + if (on) " · " + stringResource(R.string.tester_on) else "") { open = true; bad = false; code = "" }
        if (open) AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.tester_about_row)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!on) {
                        Text(stringResource(R.string.tester_unlock_hint), style = Ski.type.small)
                        OutlinedTextField(code, { code = it; bad = false }, singleLine = true, label = { Text(stringResource(R.string.tester_unlock_title)) },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters), isError = bad)
                        if (bad) Text(stringResource(R.string.tester_unlock_bad), style = Ski.type.small, color = Ski.colors.red)
                    } else {
                        if (TesterGate.consented(ctx)) TextButton({ TesterGate.withdraw(ctx); open = false }) { Text(stringResource(R.string.tester_consent_withdraw)) }
                        TextButton({ TesterGate.lock(ctx); on = false; open = false }) { Text(stringResource(R.string.tester_turn_off)) }
                    }
                }
            },
            confirmButton = {
                if (!on) TextButton({ if (TesterGate.unlock(ctx, code)) { on = true; open = false } else bad = true }) { Text(stringResource(R.string.tester_unlock_go)) }
            },
            dismissButton = { TextButton({ open = false }) { Text(stringResource(R.string.tester_cancel)) } },
        )
    }

    /** The recording's diagnostic log (qa/Diag: states, counts and milliseconds, never a position), sent through the system's share sheet. */
    @Composable
    fun DiagRow() {
        val ctx = LocalContext.current
        val scope = rememberCoroutineScope()
        Action(stringResource(R.string.tester_diag_row)) {
            scope.launch {
                val out = withContext(Dispatchers.IO) {
                    runCatching {
                        io.github.pini236.skiapp.qa.Diag.file()?.let { f ->
                            val dir = File(ctx.cacheDir, "share").also { it.mkdirs() }
                            dir.listFiles { x -> x.name.startsWith("diag-") }?.forEach { it.delete() }
                            File(dir, "diag-" + java.util.UUID.randomUUID().toString().take(6) + ".txt").also { it.writeText(f.readText()) }
                        }
                    }.getOrNull()
                }
                if (out == null) Toast.makeText(ctx, R.string.tester_diag_empty, Toast.LENGTH_LONG).show()
                else {
                    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".share", out)
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    ctx.startActivity(Intent.createChooser(send, ctx.getString(R.string.tester_share_title)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        }
    }

    @Composable
    fun DayExtras(file: File) {
        val ctx = LocalContext.current
        if (!TesterGate.unlocked(ctx)) return
        val scope = rememberCoroutineScope()
        var consent by remember { mutableStateOf(false) }
        var warn by remember { mutableStateOf(false) }
        Action(stringResource(R.string.tester_export)) { if (TesterGate.consented(ctx)) warn = true else consent = true }
        if (consent) ConsentDialog(onAgree = { impacts -> TesterGate.consent(ctx, impacts); consent = false; warn = true }, onCancel = { consent = false })
        if (warn) AlertDialog(
            onDismissRequest = { warn = false },
            title = { Text(stringResource(R.string.tester_warn_title)) },
            text = { Text(stringResource(R.string.tester_warn_body), style = Ski.type.body) },
            confirmButton = {
                TextButton({
                    warn = false
                    scope.launch {
                        val out = withContext(Dispatchers.IO) { runCatching { build(ctx, file) }.getOrNull() }
                        if (out == null) Toast.makeText(ctx, R.string.tester_empty, Toast.LENGTH_LONG).show()
                        else {
                            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".share", out)
                            val send = Intent(Intent.ACTION_SEND).setType("application/octet-stream").putExtra(Intent.EXTRA_STREAM, uri)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            // no default target: the system's own sheet, every time
                            ctx.startActivity(Intent.createChooser(send, ctx.getString(R.string.tester_share_title)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
                }) { Text(stringResource(R.string.tester_send)) }
            },
            dismissButton = { TextButton({ warn = false }) { Text(stringResource(R.string.tester_cancel)) } },
        )
    }

    @Composable
    private fun ConsentDialog(onAgree: (Boolean) -> Unit, onCancel: () -> Unit) {
        var age by remember { mutableStateOf(false) }
        var impacts by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = onCancel,
            title = { Text(stringResource(R.string.tester_consent_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.tester_consent_body), style = Ski.type.body)
                    Check(stringResource(R.string.tester_consent_age), age) { age = it }
                    Check(stringResource(R.string.tester_consent_impacts), impacts) { impacts = it }
                }
            },
            confirmButton = { TextButton({ onAgree(impacts) }, enabled = age) { Text(stringResource(R.string.tester_consent_agree)) } },
            dismissButton = { TextButton(onCancel) { Text(stringResource(R.string.tester_cancel)) } },
        )
    }

    @Composable
    private fun Check(text: String, on: Boolean, set: (Boolean) -> Unit) = Row(
        Modifier.fillMaxWidth().clickable(role = Role.Checkbox) { set(!on) }.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(on, null)
        Text(text, Modifier.padding(start = 8.dp), style = Ski.type.small)
    }

    @Composable
    private fun Action(text: String, onClick: () -> Unit) = Text(
        text, Modifier.fillMaxWidth().border(BorderStroke(1.5.dp, Ski.colors.ink)).clickable(role = Role.Button, onClick = onClick).heightIn(min = 48.dp).padding(vertical = 12.dp),
        textAlign = TextAlign.Center, style = Ski.type.bodyBold, color = Ski.colors.ink)

    /** The file to share: the cut log under a random name with the day's date (never the tester's own), or null if nothing of the day is inside. */
    private fun build(ctx: android.content.Context, day: File): File? {
        val read = { name: String -> ctx.assets.open("data/$name").bufferedReader().use { it.readText() } }
        val margin = JSONObject(read("dayrec-params.json")).getJSONObject("export").getDouble("margin_m")
        val (proj, rect) = RawExport.rectOf(JSONObject(read("terrain.json")), margin)
        val text = day.readText(Charsets.UTF_8)
        val parsed = io.github.pini236.skiapp.dayrec.DayLog.parse(text.lineSequence())
        val extra = JSONObject().put("app", BuildConfig.VERSION_NAME).put("platform", "android").put("baro", parsed.points.any { it.pressure != null })
        val cut = RawExport.crop(text, proj, rect, extra, TesterGate.impactsAgreed(ctx)) ?: return null
        val date = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(Instant.ofEpochMilli(parsed.header.optLong("start", day.lastModified())).atZone(ZoneId.of(DayParams.TZ)))
        val dir = File(ctx.cacheDir, "share").also { it.mkdirs() }
        dir.listFiles { f -> f.name.endsWith(".jsonl") }?.forEach { it.delete() }
        val name = java.util.UUID.randomUUID().toString().take(8)
        return File(dir, "$name-$date.jsonl").also { it.writeText(cut, Charsets.UTF_8) }
    }
}
