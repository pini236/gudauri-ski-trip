package io.github.pini236.skiapp.group

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.home.paperGrain
import io.github.pini236.skiapp.home.shortDate
import io.github.pini236.skiapp.trip.Trip
import io.github.pini236.skiapp.trip.TripText
import io.github.pini236.skiapp.ui.Field
import io.github.pini236.skiapp.ui.Icons
import io.github.pini236.skiapp.ui.Karantina
import io.github.pini236.skiapp.ui.Note
import io.github.pini236.skiapp.ui.PrimaryButton
import io.github.pini236.skiapp.ui.QuietButton
import io.github.pini236.skiapp.ui.Ski
import io.github.pini236.skiapp.ui.TopBar
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import io.github.pini236.skiapp.trip.DateDialog
import io.github.pini236.skiapp.trip.PickField
import androidx.compose.ui.platform.LocalConfiguration

/**
 * A new group (Q1): its name, its own dates (its countdown, and how long its invite works), your name in it, and
 * whether your trip shows there. You are its admin; a second admin is recommended.
 */
@Composable
fun NewGroupScreen(api: GroupApi, myTrip: Trip?, today: LocalDate, onCancel: () -> Unit, onCreated: (String) -> Unit) {
    val c = Ski.colors
    var name by rememberSaveable { mutableStateOf("") }
    var from by rememberSaveable { mutableStateOf(myTrip?.out?.date) }
    var to by rememberSaveable { mutableStateOf(myTrip?.ret?.date) }
    var me by rememberSaveable { mutableStateOf(api.me()?.name.orEmpty()) }
    var showTrip by rememberSaveable { mutableStateOf(myTrip != null) }
    var pick by rememberSaveable { mutableStateOf<String?>(null) }
    val r = rememberRunner()
    Column(Modifier.fillMaxSize().background(c.snow).statusBarsPadding()) {
        TopBar(stringResource(R.string.app_g_new_title), stringResource(R.string.app_cancel), onCancel)
        Page {
            Field(stringResource(R.string.app_g_group_name), name, { name = it.take(60) }, hint = stringResource(R.string.app_g_group_name_hint))
            GroupDates(from, to, { pick = it })
            Note(stringResource(R.string.app_g_dates_note), Icons.clock)
            Field(stringResource(R.string.app_g_your_name), me, { me = it.take(40) }, hint = stringResource(R.string.app_g_your_name_hint))
            if (myTrip != null) Toggle(stringResource(R.string.group_show_my_trip),
                listOfNotNull(myTrip.out.flight.ifBlank { null }, shortDate(myTrip.out.date) + (myTrip.out.departs?.let { " · $it" } ?: "")).joinToString(", "),
                showTrip, { showTrip = it })
            val ok = name.isNotBlank() && me.isNotBlank()
            PrimaryButton(stringResource(R.string.app_g_create_it), Icons.people, {
                if (ok) r.run { onCreated(api.createGroup(name, me, from, to, if (showTrip) myTrip else null)) }
            })
            ErrorLine(r)
            Spacer(Modifier.height(8.dp))
            Note(stringResource(R.string.app_g_admin_note), Icons.key)
        }
    }
    when (pick) {
        "from" -> DateDialog(stringResource(R.string.app_g_from_date), from, today, today, null, { from = it; if (to != null && to!! < it) to = null }, { pick = null })
        "to" -> DateDialog(stringResource(R.string.app_g_to_date), to, from ?: today, from ?: today, null, { to = it }, { pick = null })
    }
}

/** The group's dates (Q1, Q10): from the calendar, the end never before the start; both may stay empty. */
@Composable
internal fun GroupDates(from: LocalDate?, to: LocalDate?, onPick: (String) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    fun day(d: LocalDate?) = d?.let { DateTimeFormatter.ofPattern("EEE · d.M.yyyy", locale).format(it) }
    val hint = stringResource(R.string.app_trip_pick_date)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PickField(stringResource(R.string.app_g_from_date), day(from), hint, Icons.calendar, { onPick("from") }, Modifier.weight(1f), ltr = false)
        PickField(stringResource(R.string.app_g_to_date), day(to), hint, Icons.calendar, { onPick("to") }, Modifier.weight(1f), ltr = false)
    }
}

/** Share text and a link with WhatsApp or anything else (the system's share sheet). */
fun shareInvite(context: Context, text: String, whatsapp: Boolean) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    if (whatsapp) {
        val wa = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/?text=" + Uri.encode(text)))
        runCatching { context.startActivity(wa) }.onFailure { context.startActivity(Intent.createChooser(send, null)) }
    } else context.startActivity(Intent.createChooser(send, null))
}

/**
 * Inviting friends (Q2): the invite card with its six letters and the link, WhatsApp and share, and for admins the
 * invite's settings: approve each joiner by hand, how long it works, cancel it and make a new one.
 */
@Composable
fun InviteScreen(api: GroupApi, groupId: String, onBack: () -> Unit) {
    val c = Ski.colors
    val ctx = LocalContext.current
    var group by remember { mutableStateOf<Group?>(null) }
    val load = rememberRunner(); val r = rememberRunner()
    LaunchedEffect(groupId) { load.run { group = api.group(groupId) } }
    val g = group
    Column(Modifier.fillMaxSize().background(c.snow).statusBarsPadding()) {
        TopBar(stringResource(R.string.app_g_invite_title), stringResource(R.string.app_g_to_group), onBack)
        Page {
            if (g == null) { if (load.busy) Muted(stringResource(R.string.app_loading)) else ErrorLine(load); return@Page }
            val inv = g.invite
            Spacer(Modifier.height(10.dp))
            if (inv != null) InviteCard(g.name, inv) { copy(ctx, InviteCode.shared(inv.token)) }
            else Muted(stringResource(R.string.group_no_invite), size = 14f)
            val text = inv?.let { stringResource(R.string.app_g_invite_message, g.name, InviteCode.shared(it.token), it.code) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(stringResource(R.string.app_g_whatsapp), Icons.share, { text?.let { shareInvite(ctx, it, true) } }, Modifier.weight(1f))
                Button2(stringResource(R.string.app_g_share), Look.GHOST, { text?.let { shareInvite(ctx, it, false) } }, Modifier.weight(1f), icon = Icons.share)
            }
            if (g.admin) {
                Display(stringResource(R.string.app_g_invite_settings), 28f, Modifier.padding(top = 8.dp))
                Toggle(stringResource(R.string.app_g_manual_approval), stringResource(if (inv?.requiresApproval == true) R.string.app_g_manual_on else R.string.app_g_manual_off),
                    inv?.requiresApproval == true, { on -> r.run { api.newInvite(groupId, on); group = api.group(groupId) } }, enabled = !r.busy)
                Column(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(vertical = 8.dp)) {
                    Text(stringResource(R.string.app_g_valid), style = Ski.type.bodyBold.copy(fontSize = 15.5.sp), color = c.ink)
                    Muted(g.endsOn?.let { stringResource(R.string.app_g_valid_until, TripText.date(it)) } ?: stringResource(R.string.app_g_valid_90), size = 13f)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.rule))
                Button2(stringResource(R.string.app_g_replace_link), Look.DANGER, { r.run { api.newInvite(groupId, inv?.requiresApproval == true); group = api.group(groupId) } }, icon = Icons.x)
                Muted(stringResource(R.string.app_g_replace_note), size = 12.5f)
                // the invite stops working, with no new one (as on the site); "new code and link" makes the next
                if (inv != null) QuietButton(stringResource(R.string.group_revoke), { r.run { api.revokeInvite(inv.id); group = api.group(groupId) } }, danger = true)
                ErrorLine(r)
            }
        }
    }
}

private fun copy(ctx: Context, text: String) {
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("invite", text))
    Toast.makeText(ctx, ctx.getString(R.string.common_link_copied), Toast.LENGTH_SHORT).show()
}

/** The invite card (Q2): paper, tilted a little, with fresh snow; the group, the six letters and the link. */
@Composable
private fun InviteCard(groupName: String, inv: Invite, onCopy: () -> Unit) {
    val c = Ski.colors
    val codeLabel = stringResource(R.string.app_g_code_aria, inv.code)
    Box(Modifier.rotate(-1.2f)) {
        SnowCard(Color.Transparent, 41) {
            Row(Modifier.fillMaxWidth().background(c.accent).padding(horizontal = 16.dp, vertical = 7.dp)) {
                Text(stringResource(R.string.app_g_invite_strip), Modifier.weight(1f), style = Ski.type.label.copy(fontSize = 12.sp), color = c.onAccent)
                Text(groupName, style = Ski.type.label.copy(fontSize = 12.sp), color = c.onAccent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Muted(stringResource(R.string.app_g_invite_how), Modifier.padding(top = 14.dp, bottom = 12.dp), 13f)
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = codeLabel }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (ch in inv.code) Box(Modifier.weight(1f).height(64.dp).background(c.bpPaper2).paperGrain(c.dark), contentAlignment = Alignment.Center) {
                        Text(ch.toString(), style = TextStyle(fontFamily = Karantina, fontWeight = FontWeight.Bold, fontSize = 46.sp), color = c.ink)
                        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp).background(c.accent))
                    }
                }
            }
            Row(Modifier.padding(top = 12.dp).fillMaxWidth().border(1.5.dp, c.dash).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.link, null, Modifier.size(18.dp), tint = c.muted)
                Text(InviteCode.link(inv.token).removePrefix("https://"), Modifier.weight(1f), style = Ski.type.small.copy(fontSize = 13.5.sp, textDirection = TextDirection.Ltr),
                    color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val copyLabel = stringResource(R.string.app_g_copy_link)
                Box(Modifier.size(44.dp).clickable(role = Role.Button, onClick = onCopy).semantics { contentDescription = copyLabel }, contentAlignment = Alignment.Center) {
                    Icon(Icons.copy, null, Modifier.size(20.dp), tint = c.glacier)
                }
            }
        }
    }
}

