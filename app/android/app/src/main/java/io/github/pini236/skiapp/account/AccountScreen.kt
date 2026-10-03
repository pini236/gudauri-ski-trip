package io.github.pini236.skiapp.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.group.Avatar
import io.github.pini236.skiapp.group.Button2
import io.github.pini236.skiapp.group.Display
import io.github.pini236.skiapp.group.ErrorLine
import io.github.pini236.skiapp.group.GoogleButton
import io.github.pini236.skiapp.group.GroupApi
import io.github.pini236.skiapp.group.Look
import io.github.pini236.skiapp.group.Me
import io.github.pini236.skiapp.group.Muted
import io.github.pini236.skiapp.group.Page
import io.github.pini236.skiapp.group.rememberRunner
import io.github.pini236.skiapp.ui.Field
import io.github.pini236.skiapp.ui.Icons
import io.github.pini236.skiapp.ui.Note
import io.github.pini236.skiapp.ui.PrimaryButton
import io.github.pini236.skiapp.ui.Ski
import io.github.pini236.skiapp.ui.TopBar

/**
 * The account (A4): the name the groups see, the ways in (Google; Apple only if linked on an iPhone, docs/USERS.md), what
 * syncs, signing out, and deleting the account (asks twice; also from the site at /account). A guest sees the offer
 * to keep their place with Google instead.
 */
@Composable
fun AccountScreen(api: GroupApi, onBack: () -> Unit, signInGoogle: suspend () -> Unit, onGone: () -> Unit) {
    val c = Ski.colors
    var me by remember { mutableStateOf<Me?>(api.me()) }
    var naming by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf(me?.name ?: "") }
    var armed by remember { mutableStateOf(false) }
    val r = rememberRunner()
    LaunchedEffect(armed) { if (armed) { kotlinx.coroutines.delay(4000); armed = false } }
    Column(Modifier.fillMaxSize().background(c.snow).statusBarsPadding()) {
        TopBar(stringResource(R.string.app_a_account), stringResource(R.string.common_about_settings), onBack)
        Page {
            val m = me
            if (m == null) {
                Muted(stringResource(R.string.app_a_not_signed_in), size = 14f)
                GoogleButton(stringResource(R.string.app_a_google_continue), { r.run { signInGoogle(); me = api.me() } }, enabled = !r.busy)
                ErrorLine(r)
                return@Page
            }
            // who: the name the groups see, with "change"
            Row(Modifier.fillMaxWidth().background(c.paper).drawBehind { drawRect(c.glacier, Offset.Zero, Size(size.width, 6.dp.toPx())) }
                .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Avatar(m.name ?: "?", true, 52)
                Column(Modifier.weight(1f)) {
                    Display(m.name ?: stringResource(R.string.app_a_no_name), 30f)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Muted(stringResource(R.string.app_a_name_in_groups) + " · ", size = 13f)
                        Text(stringResource(R.string.app_a_change), Modifier.heightIn(min = 44.dp).clickable { naming = !naming }.padding(vertical = 6.dp),
                            style = Ski.type.bodyBold.copy(fontSize = 13.sp), color = c.glacier)
                    }
                }
            }
            if (naming) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field(stringResource(R.string.app_a_my_name), name, { name = it.take(40) })
                PrimaryButton(stringResource(R.string.app_save), Icons.check, {
                    if (name.isNotBlank()) r.run { api.rename(name.trim()); me = api.me(); naming = false }
                })
            }
            if (!m.registered) {
                Note(stringResource(R.string.app_a_guest_note), Icons.key)
                GoogleButton(stringResource(R.string.app_a_save_google), { r.run { signInGoogle(); me = api.me() } }, enabled = !r.busy)
            } else {
                Column {
                    Display(stringResource(R.string.app_a_ways_in), 28f)
                    Muted(stringResource(R.string.app_a_ways_in_sub), Modifier.padding(bottom = 6.dp), 13f)
                    Way(stringResource(R.string.app_a_google), m.google, mark = { GMark() }, connect = { r.run { signInGoogle(); me = api.me() } })
                    // on Android only Google (docs/USERS.md); Apple shows once it was linked on an iPhone
                    if (m.apple) Way(stringResource(R.string.app_a_apple), true, mark = { AppleMark() }, connect = null)
                }
                Display(stringResource(R.string.app_a_what_syncs), 28f, Modifier.padding(top = 8.dp))
                Note(stringResource(R.string.acct_sync_note), Icons.cloud)
            }
            ErrorLine(r)
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // a guest has no way back in: signing out would only lose the groups (the site shows it only when registered)
                if (m.registered) Button2(stringResource(R.string.app_a_sign_out), Look.GHOST, { r.run { api.signOut(); onGone() } }, icon = Icons.out)
                Button2(stringResource(if (armed) R.string.app_a_delete_confirm else R.string.app_a_delete), Look.DANGER, {
                    if (armed) r.run { api.deleteAccount(); onGone() } else armed = true
                }, icon = Icons.trash)
            }
            Muted(stringResource(R.string.app_a_delete_note, "gudauri-ski-trip.vercel.app/account"), size = 12.5f)
        }
    }
}

@Composable
private fun Way(name: String, on: Boolean, mark: @Composable () -> Unit, connect: (() -> Unit)?) {
    val c = Ski.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).drawBehind { drawRect(c.rule, Offset(0f, size.height - 1.dp.toPx()), Size(size.width, 1.dp.toPx())) },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        mark()
        Column(Modifier.weight(1f)) {
            Text(name, style = Ski.type.bodyBold, color = c.ink)
            Muted(stringResource(if (on) R.string.acct_connected else R.string.app_a_not_connected), size = 12.5f)
        }
        when {
            on -> Icon(Icons.check, null, Modifier.size(22.dp), tint = c.green)
            connect != null -> Button2(stringResource(R.string.app_a_connect), Look.GHOST, connect, small = true, full = false)
        }
    }
}

/** Google's official mark in the ring, as on the sign-in button (round 13, item 3; Google's branding rules). */
@Composable
private fun GMark() = Box(Modifier.size(28.dp).border(2.dp, Color(0xFF747775), CircleShape), contentAlignment = Alignment.Center) {
    androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.ic_google_g), null, Modifier.size(16.dp))
}

@Composable
private fun AppleMark() = Box(Modifier.size(28.dp).background(Color.Black, CircleShape), contentAlignment = Alignment.Center) {
    Text("A", style = Ski.type.bodyBold.copy(fontSize = 14.sp, textDirection = TextDirection.Ltr), color = Color.White)
}
