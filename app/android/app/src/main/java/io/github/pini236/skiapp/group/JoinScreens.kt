package io.github.pini236.skiapp.group

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.home.DayNight
import io.github.pini236.skiapp.home.Head
import io.github.pini236.skiapp.home.Hero
import io.github.pini236.skiapp.home.dayRange
import io.github.pini236.skiapp.ui.Field
import io.github.pini236.skiapp.ui.Icons
import io.github.pini236.skiapp.ui.Karantina
import io.github.pini236.skiapp.ui.Note
import io.github.pini236.skiapp.ui.PrimaryButton
import io.github.pini236.skiapp.ui.QuietButton
import io.github.pini236.skiapp.ui.Ski
import io.github.pini236.skiapp.ui.TopBar

/**
 * The group sign as a guest (A1): join with a code (no account), or create a group (needs one: the sign-in sheet,
 * A2). Everything else works without an account.
 */
@Composable
fun GroupEntryScreen(api: GroupApi, onBack: () -> Unit, onCode: (String) -> Unit, onCreate: () -> Unit, signInGoogle: suspend () -> Unit, onPrivacy: () -> Unit,
                     /** My groups changed (GroupHub watches them): read the waiting requests again. */
                     changes: Int = 0) {
    val c = Ski.colors
    var code by rememberSaveable { mutableStateOf("") }
    var sheet by rememberSaveable { mutableStateOf(false) }
    val r = rememberRunner()
    // my requests still waiting for an admin, after a restart too (A-19): the same card as when it was sent (Q3)
    var pending by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(api, changes) { pending = runCatching { api.pendingRequests() }.getOrDefault(emptyList()) }
    Box(Modifier.fillMaxSize().background(c.snow)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TopBar(stringResource(R.string.app_sign_group), stringResource(R.string.nav_home), onBack)
            Page {
                Muted(stringResource(R.string.app_g_intro), size = 14f)
                Spacer(Modifier.height(4.dp))
                for (gid in pending) SnowCard(Color(0xFFF4B942), 61) {
                    Text(stringResource(R.string.app_g_pending_title), style = Ski.type.bodyBold.copy(fontSize = 14.5.sp), color = c.ink)
                    Muted(stringResource(R.string.app_g_pending_sub), size = 12.5f)
                    QuietButton(stringResource(R.string.app_g_pending_cancel), { r.run { api.cancelRequest(gid); pending = pending - gid } })
                }
                SnowCard(c.blue, 31) {
                    Display(stringResource(R.string.app_g_have_code), 34f)
                    Muted(stringResource(R.string.app_g_have_code_sub), Modifier.padding(top = 4.dp, bottom = 12.dp))
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // a pasted invite link goes straight in (its token or code); anything else is typed letters
                        Field(stringResource(R.string.app_g_code_label), code, { v -> InviteCode.fromLink(v)?.let(onCode) ?: run { code = InviteCode.clean(v) } },
                            Modifier.weight(1f), "KZBQRM", ltr = true)
                        PrimaryButton(stringResource(R.string.app_g_join), null, { onCode(code) }, full = false)
                    }
                }
                Spacer(Modifier.height(6.dp))
                SnowCard(c.ink, 32) {
                    Display(stringResource(R.string.app_g_create), 34f)
                    Muted(stringResource(R.string.app_g_create_sub), Modifier.padding(top = 4.dp, bottom = 12.dp))
                    Button2(stringResource(R.string.app_g_create), Look.INK, { if (api.me()?.registered == true) onCreate() else sheet = true }, icon = Icons.people)
                }
                Note(stringResource(R.string.app_g_rest_works), Icons.phone)
            }
        }
        if (sheet) Sheet({ sheet = false }) {
            Display(stringResource(R.string.app_a_sign_in_title), 36f)
            Muted(stringResource(R.string.app_a_sign_in_sub), Modifier.padding(top = 6.dp, bottom = 16.dp), 14f)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GoogleButton(stringResource(R.string.app_a_google), { r.run { signInGoogle(); sheet = false; onCreate() } }, enabled = !r.busy)
                ErrorLine(r)
                QuietButton(stringResource(R.string.app_a_not_now), { sheet = false })
            }
            Box(Modifier.padding(top = 16.dp).fillMaxWidth().height(1.dp).background(c.rule))
            Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Note(stringResource(R.string.app_a_moves_over), Icons.cloud)
                Note(stringResource(R.string.app_a_delete_any_time), Icons.trash)
                Text(stringResource(R.string.app_about_privacy), Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onPrivacy).padding(vertical = 12.dp),
                    style = Ski.type.bodyBold.copy(fontSize = 13.sp), color = c.glacier)
            }
        }
    }
}

/**
 * Typing the code (Q4): six letters in six boxes, for whoever installed from the store and lost the link on the way.
 * One hidden field under the boxes takes the typing; letters only, without I and O (the server's alphabet).
 */
@Composable
fun CodeScreen(initial: String, onBack: () -> Unit, onContinue: (String) -> Unit) {
    val c = Ski.colors
    var code by rememberSaveable { mutableStateOf(InviteCode.clean(initial)) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val label = stringResource(R.string.app_g_code_label)
    Column(Modifier.fillMaxSize().background(c.snow).statusBarsPadding()) {
        TopBar(stringResource(R.string.app_g_code_title), stringResource(R.string.app_sign_group), onBack)
        Page {
            Muted(stringResource(R.string.app_g_code_why), size = 14f)
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.app_g_six_letters), style = Ski.type.label.copy(fontSize = 13.sp), color = c.muted)
            Box {
                BasicTextField(code, { v -> InviteCode.fromLink(v)?.let(onContinue) ?: run { code = InviteCode.clean(v) } },
                    Modifier.matchParentSize().focusRequester(focus).semantics { contentDescription = label },
                    textStyle = TextStyle(color = Color.Transparent), cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.Transparent), singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false))
                // the boxes read left to right in every language (Latin letters)
                androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(Modifier.fillMaxWidth().clickable { runCatching { focus.requestFocus() } }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (i in 0 until 6) {
                            val ch = code.getOrNull(i)
                            Box(Modifier.weight(1f).height(64.dp).background(c.paper).border(2.dp, if (ch != null || i == code.length) c.ink else c.rule), contentAlignment = Alignment.Center) {
                                Text(ch?.toString() ?: "", style = TextStyle(fontFamily = Karantina, fontWeight = FontWeight.Bold, fontSize = 44.sp), color = c.ink)
                            }
                        }
                    }
                }
            }
            PrimaryButton(stringResource(R.string.app_g_continue), null, { if (InviteCode.isCode(code)) onContinue(code) })
            Note(stringResource(R.string.app_g_code_letters), Icons.key)
        }
    }
}

/**
 * The invitation (Q3): who it is from and to what, your name in the group, and in. A guest gets an anonymous identity
 * first, without being asked (docs/USERS.md: "invited without signing up"). Also the way in for "I'm already in the
 * group" (Q5), and the answers that are not "joined": waiting for an admin, a code that does not work, too many tries.
 */
@Composable
fun InvitedScreen(api: GroupApi, code: String, frame: DayNight.Frame, mode: DayNight.Mode, onMode: () -> Unit, onAbout: () -> Unit,
                  onBack: () -> Unit, onTypeCode: () -> Unit, onReclaim: () -> Unit, onJoined: (groupId: String, guest: Boolean) -> Unit) {
    val c = Ski.colors
    var preview by remember { mutableStateOf<Preview?>(null) }
    var result by remember { mutableStateOf<JoinStatus?>(null) }
    var name by rememberSaveable { mutableStateOf(api.me()?.name.orEmpty()) }
    val load = rememberRunner()
    val r = rememberRunner()
    LaunchedEffect(code) {
        load.run {
            if (api.me() == null) api.signInGuest()
            val p = api.preview(code)
            preview = p
            if (p.alreadyMember && p.groupId != null) onJoined(p.groupId, false)
        }
    }
    // waiting for an admin: the approval opens the group, live (A-19)
    if (result == JoinStatus.PENDING) preview?.groupId?.let { gid ->
        DisposableEffect(gid) { val stop = api.watchMine { approved -> if (gid in approved) onJoined(gid, api.me()?.registered != true) }; onDispose { stop() } }
    }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(Modifier.fillMaxSize().background(c.snow)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding()) {
            Box {
                Hero(frame, 230.dp + top)
                Column(Modifier.padding(top = top)) {
                    Head(frame, mode, onMode, onAbout = onAbout)
                    Spacer(Modifier.height(60.dp))
                    val p = preview
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        SnowCard(c.blue, 51) {
                            when {
                                load.busy || (p == null && load.error == null) -> Muted(stringResource(R.string.app_loading))
                                p == null -> { ErrorLine(load); QuietButton(stringResource(R.string.app_g_retype), onTypeCode) }
                                p.status != JoinStatus.OK -> {
                                    Display(stringResource(if (p.status == JoinStatus.RATE_LIMITED) R.string.app_g_too_many_tries else R.string.app_g_bad_code), 30f)
                                    Muted(stringResource(if (p.status == JoinStatus.RATE_LIMITED) R.string.app_g_too_many_tries_sub else R.string.app_g_bad_code_sub), Modifier.padding(top = 6.dp))
                                    Spacer(Modifier.height(10.dp))
                                    PrimaryButton(stringResource(R.string.app_g_retype), null, onTypeCode)
                                }
                                result == JoinStatus.PENDING -> {
                                    Display(stringResource(R.string.app_g_pending_title), 30f)
                                    Muted(stringResource(R.string.app_g_pending_sub), Modifier.padding(top = 6.dp))
                                    p.groupId?.let { gid -> QuietButton(stringResource(R.string.app_g_pending_cancel), { r.run { api.cancelRequest(gid); onBack() } }) }
                                }
                                else -> {
                                    Muted(stringResource(R.string.app_g_invited_to))
                                    Display(p.name, 50f)
                                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                        if (p.startsOn != null && p.endsOn != null) Text("${dayRange(p.startsOn..p.endsOn)}.${p.endsOn.year}",
                                            style = Ski.type.small.copy(fontSize = 13.5.sp, textDirection = TextDirection.Ltr), color = c.muted)
                                        // an invite that needs approval shows no names to who is not in yet (the server's rule)
                                        if (p.members.isNotEmpty()) Muted(pluralStringResource(R.plurals.app_g_members, p.members.size, p.members.size))
                                    }
                                    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Field(stringResource(R.string.app_g_your_name), name, { name = it.take(40) }, hint = stringResource(R.string.app_g_your_name_hint))
                                        PrimaryButton(stringResource(if (p.requiresApproval) R.string.app_g_ask_to_join else R.string.app_g_enter), null, {
                                            if (name.isNotBlank()) r.run {
                                                val j = api.join(code, name)
                                                result = j.status
                                                if ((j.status == JoinStatus.JOINED || j.status == JoinStatus.ALREADY_MEMBER) && j.groupId != null) onJoined(j.groupId, api.me()?.registered != true)
                                            }
                                        })
                                        ErrorLine(r)
                                        // the preview was fine, but joining was refused: the code changed meanwhile, or too many tries
                                        when (result) {
                                            JoinStatus.GROUP_FULL -> Text(stringResource(R.string.app_g_full), style = Ski.type.bodyBold, color = c.red)
                                            JoinStatus.INVALID_CODE -> Column {
                                                Text(stringResource(R.string.app_g_bad_code), style = Ski.type.bodyBold, color = c.red)
                                                Muted(stringResource(R.string.app_g_bad_code_sub), size = 13f)
                                            }
                                            JoinStatus.RATE_LIMITED -> Column {
                                                Text(stringResource(R.string.app_g_too_many_tries), style = Ski.type.bodyBold, color = c.red)
                                                Muted(stringResource(R.string.app_g_too_many_tries_sub), size = 13f)
                                            }
                                            else -> {}
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (preview?.status == JoinStatus.OK && result != JoinStatus.PENDING) Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Note(stringResource(R.string.app_g_no_signup), Icons.lock)
                        // "I'm already in the group" needs the names to pick from; without them, joining asks the admin as usual
                        if (preview?.members?.isNotEmpty() == true) Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).background(c.paper).border(1.5.dp, c.rule).clickable(role = Role.Button, onClick = onReclaim).padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.app_g_already_in), style = Ski.type.bodyBold, color = c.ink)
                                Muted(stringResource(R.string.app_g_already_in_sub), size = 12.5f)
                            }
                            Icon(Icons.forward, null, Modifier.size(20.dp), tint = c.glacier)
                        }
                    }
                    if (preview != null) Box(Modifier.padding(horizontal = 16.dp)) { QuietButton(stringResource(R.string.nav_home), onBack) }
                }
            }
        }
    }
}

/**
 * "I'm already in the group" (Q5): pick your name; an admin approves, and you are back with what you had. A name
 * that belongs to a registered account signs in instead.
 */
@Composable
fun ReclaimScreen(api: GroupApi, code: String, onBack: () -> Unit, signInGoogle: suspend () -> Unit, onDone: (groupId: String) -> Unit) {
    val c = Ski.colors
    var preview by remember { mutableStateOf<Preview?>(null) }
    var pick by rememberSaveable { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<JoinStatus?>(null) }
    val load = rememberRunner(); val r = rememberRunner()
    LaunchedEffect(code) { load.run { if (api.me() == null) api.signInGuest(); preview = api.preview(code) } }
    // waiting for an admin: the approval opens the group, live (A-19)
    if (status == JoinStatus.PENDING) preview?.groupId?.let { gid ->
        DisposableEffect(gid) { val stop = api.watchMine { approved -> if (gid in approved) onDone(gid) }; onDispose { stop() } }
    }
    Column(Modifier.fillMaxSize().background(c.snow).statusBarsPadding()) {
        TopBar(stringResource(R.string.app_g_already_in), stringResource(R.string.app_g_back), onBack)
        Page {
            val p = preview
            if (p == null) { if (load.busy) Muted(stringResource(R.string.app_loading)) else ErrorLine(load); return@Page }
            Muted(stringResource(R.string.app_g_reclaim_intro, p.name), size = 14f)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((id, n) in p.members) {
                    val sel = pick == id
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).background(if (sel) c.grid else c.paper).border(if (sel) 2.dp else 1.dp, if (sel) c.blue else c.rule)
                        .clickable(role = Role.RadioButton) { pick = id }.semantics { selected = sel }.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(20.dp).border(2.dp, if (sel) c.blue else c.muted, androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                            if (sel) Box(Modifier.size(10.dp).background(c.blue, androidx.compose.foundation.shape.CircleShape))
                        }
                        Text(n, style = Ski.type.bodyBold.copy(fontSize = 15.5.sp), color = c.ink)
                    }
                }
            }
            when (status) {
                JoinStatus.PENDING -> SnowCard(Color(0xFFF4B942), 61) {
                    Text(stringResource(R.string.app_g_pending_title), style = Ski.type.bodyBold.copy(fontSize = 14.5.sp), color = c.ink)
                    Muted(stringResource(R.string.app_g_pending_sub), size = 12.5f)
                    p.groupId?.let { gid -> QuietButton(stringResource(R.string.app_g_pending_cancel), { r.run { api.cancelRequest(gid); onBack() } }) }
                }
                JoinStatus.SIGN_IN_INSTEAD -> {
                    Text(stringResource(R.string.app_g_sign_in_instead), style = Ski.type.bodyBold, color = c.ink)
                    GoogleButton(stringResource(R.string.app_a_google), { r.run { signInGoogle(); p.groupId?.let(onDone) } })
                }
                else -> {
                    PrimaryButton(stringResource(R.string.app_g_send_request), null, {
                        pick?.let { m -> r.run { val j = api.reclaim(code, m); status = j.status; if (j.status == JoinStatus.ALREADY_MEMBER) j.groupId?.let(onDone) } }
                    })
                    // every answer its own words (ד5), the site's (join.st_*)
                    when (status) {
                        JoinStatus.NO_SUCH_MEMBER -> R.string.join_st_no_such_member
                        JoinStatus.INVALID_CODE -> R.string.join_st_invalid_code
                        JoinStatus.RATE_LIMITED -> R.string.join_st_rate_limited
                        JoinStatus.GROUP_FULL -> R.string.join_st_group_full
                        else -> null
                    }?.let { Text(stringResource(it), color = c.red, style = Ski.type.bodyBold) }
                }
            }
            ErrorLine(r)
        }
    }
}
