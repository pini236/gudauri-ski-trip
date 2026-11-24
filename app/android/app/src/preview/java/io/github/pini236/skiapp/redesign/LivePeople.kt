package io.github.pini236.skiapp.redesign

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.BuildConfig
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.fx.FxPrefs
import io.github.pini236.skiapp.fx.Haptics
import io.github.pini236.skiapp.group.ApiException
import io.github.pini236.skiapp.group.Group
import io.github.pini236.skiapp.group.GroupTab
import io.github.pini236.skiapp.group.InviteCode
import io.github.pini236.skiapp.group.JoinStatus
import io.github.pini236.skiapp.group.Me
import io.github.pini236.skiapp.group.Meetup
import io.github.pini236.skiapp.group.Member
import io.github.pini236.skiapp.group.MyGroup
import io.github.pini236.skiapp.group.Preview
import io.github.pini236.skiapp.group.Role as GRole
import io.github.pini236.skiapp.group.Score
import io.github.pini236.skiapp.group.errorRes
import io.github.pini236.skiapp.home.DayNight
import io.github.pini236.skiapp.home.People
import io.github.pini236.skiapp.i18n.Lang
import io.github.pini236.skiapp.meet.MeetPlan
import io.github.pini236.skiapp.meet.Reminders
import io.github.pini236.skiapp.qa.Qa
import io.github.pini236.skiapp.telemetry.Telemetry
import io.github.pini236.skiapp.trip.Trip
import io.github.pini236.skiapp.ui.Icons
import io.github.pini236.skiapp.ui.Plex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Part 3 of the redesign (design/handoff/2026-11-23-redesign-people): the group and the invite, the sign-in and the
 * account, the settings, the language and about, over the mountain. The data and the actions are today's
 * (group/GroupApi.kt, the about screen's settings); only how they look is new. The games (R11, R12) come last.
 */
internal sealed class PLayer(val tag: String) {
    object Me : PLayer("me")
    object SignIn : PLayer("signin")
    object Gate : PLayer("gate")
    class Join(val code: String) : PLayer("join")
    class Group(val id: String) : PLayer("group")
    class Invite(val id: String) : PLayer("invite")
    object NewGroup : PLayer("new")
    object Settings : PLayer("settings")
    object Language : PLayer("lang")
    object About : PLayer("about")
    object Delete : PLayer("delete")
}

/** What of part 3 is open over the mountain, as a stack: the back arrow and the system's back go one down. */
internal class PeopleUi {
    val stack = mutableStateListOf<PLayer>()
    val top: PLayer? get() = stack.lastOrNull()
    val open: Boolean get() = stack.isNotEmpty()
    /** Bumped when the account or the groups change, so the screens read them again. */
    var changes by mutableIntStateOf(0)
    var toast by mutableStateOf<String?>(null)
    fun go(l: PLayer) { stack.add(l); Qa.log("redesign people ${l.tag}") }
    fun back() { if (stack.isNotEmpty()) stack.removeAt(stack.size - 1); Qa.log("redesign people back ${top?.tag ?: "mountain"}") }
    fun close() { stack.clear(); Qa.log("redesign people closed") }
    /** Replace the top (a sign-in that leads on, a join that became the group). */
    fun swap(l: PLayer) { if (stack.isNotEmpty()) stack.removeAt(stack.size - 1); go(l) }
}

/** The person's circle (section 1): the first letter on their colour, white; no profile pictures. */
@Composable
internal fun Av(name: String?, id: String?, size: Int = 36, ring: Color? = null, group: Boolean = false) {
    val c = if (group) Color(PeopleRules.GROUP_COLOR) else Color(PeopleRules.COLORS[PeopleRules.colorIndex(id ?: name ?: "")])
    Box(Modifier.size(size.dp).let { m -> if (ring != null) m.border(2.dp, ring, CircleShape) else m }.padding(if (ring != null) 2.dp else 0.dp)
        .clip(CircleShape).background(c).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        Text(PeopleRules.initial(name), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = (size * .42f).sp, color = Color.White)
    }
}

/** The guest's circle: a person on the line colour. */
@Composable
private fun GuestAv(k: SheetInk, size: Int) =
    Box(Modifier.size(size.dp).clip(CircleShape).background(k.line).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        Icon(Icons.user, null, Modifier.size((size * .55f).dp), tint = k.ink)
    }

/** The main button (52, round, the accent). Half see-through and dead until [enabled]. */
@Composable
internal fun AccButton(text: String, k: SheetInk, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(modifier.fillMaxWidth().heightIn(min = 52.dp).graphicsAlpha(if (enabled) 1f else .45f).clip(RoundedCornerShape(26.dp)).background(k.acc)
        .semantics { role = Role.Button }.clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
        Text(text, fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White, maxLines = 1)
    }
}

private fun Modifier.graphicsAlpha(a: Float) = this.alpha(a)

/** A text link (14.5 bold, the accent), at least 44 high. */
@Composable
internal fun TextLink(text: String, k: SheetInk, color: Color = k.acc, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.heightIn(min = 44.dp).semantics { role = Role.Button }.clickable(onClick = onClick).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
        Text(text, fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 14.5.sp, color = color)
    }
}

@Composable
internal fun Small(text: String, k: SheetInk, modifier: Modifier = Modifier, center: Boolean = false, color: Color = k.ink2) =
    Text(text, modifier, fontFamily = Plex, fontSize = 12.5.sp, color = color, textAlign = if (center) TextAlign.Center else null)

@Composable
private fun Title(text: String, k: SheetInk) = Text(text, fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 21.sp, color = k.ink)

/** A box of rows (section 1): 12 from the sides, radius 22, the sheet's surface (opaque on Android). */
@Composable
private fun PBox(k: SheetInk, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(22.dp)).background(k.surf.copy(alpha = 1f))
        .border(1.dp, k.line, RoundedCornerShape(22.dp)).padding(horizontal = 16.dp, vertical = 4.dp), content = content)
}

/** A row (section 1): at least 54, a line under it but the last, the whole row is the button. */
@Composable
private fun PRow(title: String, k: SheetInk, sub: String? = null, end: String? = null, icon: ImageVector? = null, lead: (@Composable () -> Unit)? = null,
                 danger: Boolean = false, last: Boolean = false, trailing: (@Composable () -> Unit)? = null, onClick: (() -> Unit)? = null) {
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 54.dp).let { m -> if (onClick != null) m.semantics { role = Role.Button }.clickable(onClick = onClick) else m }
            .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (lead != null) lead()
            else if (icon != null) Box(Modifier.size(36.dp).clip(CircleShape).background(k.line), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(20.dp), tint = k.ink)
            }
            Column(Modifier.weight(1f)) {
                Text(title, fontFamily = Plex, fontWeight = if (danger) FontWeight.Bold else FontWeight.Medium, fontSize = 15.5.sp, color = if (danger) k.red else k.ink)
                if (sub != null) Small(sub, k)
            }
            if (end != null) Text(end, fontFamily = Plex, fontSize = 13.5.sp, color = k.ink2, maxLines = 1)
            if (trailing != null) trailing()
        }
        if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(k.line))
    }
}

@Composable
private fun PSwitch(on: Boolean, k: SheetInk, onChange: (Boolean) -> Unit) =
    Switch(on, onChange, colors = SwitchDefaults.colors(checkedTrackColor = k.acc, checkedThumbColor = Color.White, uncheckedTrackColor = k.line,
        uncheckedBorderColor = k.line, uncheckedThumbColor = Color.White))

/** A setting with a switch (R13): the whole row toggles; read as its name and "on" or "off". */
@Composable
private fun SwitchRow(title: String, sub: String?, on: Boolean, k: SheetInk, last: Boolean = false, onChange: (Boolean) -> Unit) {
    val st = stringResource(if (on) R.string.app_on else R.string.app_off)
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 54.dp).semantics(mergeDescendants = true) { role = Role.Switch; stateDescription = st }
            .clickable { onChange(!on) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, fontFamily = Plex, fontWeight = FontWeight.Medium, fontSize = 15.5.sp, color = k.ink)
                if (sub != null) Small(sub, k)
            }
            Box(Modifier.clearAndSetSemantics { }) { PSwitch(on, k, onChange) }
        }
        if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(k.line))
    }
}

/** A full screen over the dimmed mountain (R02, R07, R09, R10, R13, R15): the back arrow at the start, a title. */
@Composable
private fun BoxScope.FullScreen(k: SheetInk, title: String?, onBack: () -> Unit, top: (@Composable ColumnScope.() -> Unit)? = null,
                                content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.matchParentSize().background(k.veil).clickable(enabled = false) { })
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            val back = stringResource(R.string.app_g_back)
            Box(Modifier.size(44.dp).clip(CircleShape).background(k.surf.copy(alpha = 1f)).semantics { contentDescription = back; role = Role.Button }
                .clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.back, null, Modifier.size(22.dp), tint = k.ink)
            }
            if (title != null) Text(title, Modifier.padding(start = 12.dp), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White)
        }
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if (top != null) Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp), content = top)
            content()
        }
    }
}

/**
 * A sheet over the screen (R01, R08, R14): the whole screen dims evenly, no edge in the middle (Pini, 23.11.2026: an
 * edge under the pill reads as a line); a tap on the dim closes.
 */
@Composable
private fun BoxScope.PSheet(k: SheetInk, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.matchParentSize().background(Color(0x61060B16)).clickable(onClick = onDismiss))
    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
        LiveSheet(k) { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp), content = content) }
    }
}

/** Three signs in a row, each in a 44 circle, its word under it (R01, R08); not buttons. Large text: a column. */
@Composable
private fun SignTrio(k: SheetInk, items: List<Pair<ImageVector, String>>) {
    val big = LocalContext.current.resources.configuration.fontScale >= 1.5f
    @Composable fun one(m: Modifier, i: ImageVector, t: String) = Column(m, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(k.line), contentAlignment = Alignment.Center) { Icon(i, null, Modifier.size(22.dp), tint = k.ink) }
        Text(t, fontFamily = Plex, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = k.ink, textAlign = TextAlign.Center)
    }
    if (big) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { for ((i, t) in items) one(Modifier.fillMaxWidth(), i, t) }
    else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { for ((i, t) in items) one(Modifier.weight(1f), i, t) }
}

/**
 * The six boxes of the code (section 1), always left to right: one field for the screen reader, typed in capitals,
 * only the server's letters (no I, no O); a pasted link fills them.
 */
@Composable
private fun CodeBoxes(code: String, k: SheetInk, bad: Boolean, enabled: Boolean, onChange: (String) -> Unit) {
    val aria = stringResource(R.string.app_g_code_aria, code.toList().joinToString(" "))
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BasicTextField(code, { t -> onChange(PeopleRules.codeFrom(t)) }, Modifier.fillMaxWidth().semantics { contentDescription = aria }, enabled = enabled,
            singleLine = true, cursorBrush = SolidColor(Color.Transparent), textStyle = TextStyle(color = Color.Transparent),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false)) { inner ->
            Box {
                Box(Modifier.size(1.dp)) { inner() }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                    for (i in 0 until 6) {
                        val active = i == code.length.coerceAtMost(5) && enabled
                        val border = when { bad -> k.red; active -> k.acc; else -> k.line }
                        Box(Modifier.size(46.dp, 56.dp).clip(RoundedCornerShape(14.dp)).border(if (bad || active) 2.dp else 1.dp, border, RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center) {
                            Text(code.getOrNull(i)?.toString() ?: "", fontFamily = Plex, fontWeight = FontWeight.Medium, fontSize = 27.sp, color = k.ink)
                        }
                    }
                }
            }
        }
    }
}

/** A text field (section 1): 52 high, radius 16, the accent's border when focused. */
@Composable
private fun PField(value: String, hint: String, k: SheetInk, onChange: (String) -> Unit) {
    var focus by remember { mutableStateOf(false) }
    BasicTextField(value, onChange, Modifier.fillMaxWidth().onFocusChanged0 { focus = it }, singleLine = true,
        textStyle = TextStyle(fontFamily = Plex, fontSize = 16.sp, color = k.ink), cursorBrush = SolidColor(k.acc)) { inner ->
        Box(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background(k.surf)
            .border(if (focus) 2.dp else 1.dp, if (focus) k.acc else k.line, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(hint, fontFamily = Plex, fontSize = 16.sp, color = k.ink2)
            inner()
        }
    }
}

private fun Modifier.onFocusChanged0(f: (Boolean) -> Unit) = this.onFocusChanged { f(it.isFocused) }

/** Errors of an action as a line in red; "offline" says so, a refusal its own words (errorRes). */
@Composable
private fun ErrLine(code: String?, k: SheetInk) {
    if (code == null) return
    Small(stringResource(if (code == "offline") R.string.app_err_offline else errorRes(code)), k, color = k.red)
}

/** Runs an action, keeping the server's code when it refuses. */
private class Act(private val scope: kotlinx.coroutines.CoroutineScope) {
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    fun run(block: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null
        scope.launch {
            try { block() } catch (e: ApiException) { error = e.code } catch (e: java.io.IOException) { error = "offline" }
            catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { error = "server_error" }
            finally { busy = false }
        }
    }
}

@Composable
private fun rememberAct(): Act { val s = rememberCoroutineScope(); return remember { Act(s) } }

/**
 * The people of part 3 over the mountain. [onOpenMeetup] and [onNewMeetup] hand over to the meeting point (Q05, Q04).
 */
@Composable
internal fun BoxScope.LivePeople(p: People, ui: PeopleUi, night: Boolean, haptics: Haptics, plan: MeetPlan?, trip: Trip?, today: LocalDate,
                                 onOpenMeetup: (String, Meetup) -> Unit, onNewMeetup: (String) -> Unit) {
    val k = remember(night) { SheetInk(night) }
    val l = ui.top ?: return
    BackHandler(enabled = ui.open) { ui.back() }
    when (l) {
        PLayer.Me -> MeScreen(p, ui, k)
        PLayer.SignIn -> SignInSheet(p, ui, k)
        PLayer.Gate -> GateSheet(p, ui, k)
        is PLayer.Join -> JoinScreen(p, ui, k, l.code)
        is PLayer.Group -> GroupSheet(p, ui, k, l.id, plan, trip, today, onOpenMeetup, onNewMeetup)
        is PLayer.Invite -> InviteScreen(p, ui, k, l.id)
        PLayer.NewGroup -> NewGroupScreen(p, ui, k, trip, today, haptics)
        PLayer.Settings -> SettingsScreen(p, ui, k)
        PLayer.Language -> { SettingsScreen(p, ui, k); LanguageSheet(p, ui, k) }
        PLayer.About -> AboutScreen(p, ui, k)
        PLayer.Delete -> DeleteScreen(p, ui, k)
    }
    ui.toast?.let { t ->
        LaunchedEffect(t) { delay(4000); ui.toast = null }
        Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp, start = 16.dp, end = 16.dp)
            .clip(RoundedCornerShape(22.dp)).background(k.ink).padding(horizontal = 18.dp, vertical = 12.dp)) {
            Text(t, fontFamily = Plex, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = if (night) Color(0xFF0E2233) else Color.White)
        }
    }
}

/** The "me" button on the mountain (section 1): 44, my letter on my colour, or a person for a guest. */
@Composable
internal fun MeButton(me: Me?, night: Boolean, onClick: () -> Unit) {
    val k = remember(night) { SheetInk(night) }
    val aria = stringResource(R.string.app_me_button_a11y)
    Box(Modifier.size(44.dp).shadow(10.dp, CircleShape, clip = false).clip(CircleShape).background(k.surf.copy(alpha = 1f)).border(1.dp, k.line, CircleShape)
        .semantics { contentDescription = aria; role = Role.Button }.clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        val name = me?.name
        if (name.isNullOrBlank()) GuestAv(k, 36) else Av(name, me?.userId, 36)
    }
}

// ---------------------------------------------------------------------------------------------- R09, R10: me

@Composable
private fun BoxScope.MeScreen(p: People, ui: PeopleUi, k: SheetInk) {
    val api = p.api
    var me by remember { mutableStateOf(api.me()) }
    var groups by remember { mutableStateOf<List<MyGroup>?>(null) }
    LaunchedEffect(ui.changes) { me = api.me(); groups = runCatching { api.myGroupRows() }.getOrNull() ?: groups ?: emptyList() }
    val kind = PeopleRules.meKind(me, groups?.size ?: 0)
    LaunchedEffect(kind, groups?.size) { if (groups != null) Qa.log("redesign people me ${kind.name.lowercase()} ${groups?.size ?: 0}") }
    val act = rememberAct()
    var renaming by remember { mutableStateOf(false) }
    var ways by remember { mutableStateOf(false) }
    FullScreen(k, stringResource(R.string.app_me_title), { ui.back() }) {
        // the head: 64, the name (27 bold), how I am in, and "change"
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (kind == PeopleRules.MeKind.ACCOUNT) Av(me?.name, me?.userId, 64) else GuestAv(k, 64)
            Column(Modifier.weight(1f)) {
                val name = when (kind) {
                    PeopleRules.MeKind.NOBODY -> stringResource(R.string.acct_no_account)
                    else -> me?.name ?: stringResource(R.string.acct_no_account)
                }
                if (renaming) {
                    var v by remember { mutableStateOf(me?.name ?: "") }
                    PField(v, stringResource(R.string.app_g_your_name_hint), k) { v = it.take(30) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextLink(stringResource(R.string.app_save), k) { if (v.isNotBlank()) act.run { api.rename(v.trim()); renaming = false; ui.changes++ } }
                        TextLink(stringResource(R.string.app_cancel), k, k.ink2) { renaming = false }
                    }
                } else Text(name, fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 27.sp, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(stringResource(when {
                    kind == PeopleRules.MeKind.ACCOUNT && me?.apple == true && me?.google != true -> R.string.acct_signed_apple
                    kind == PeopleRules.MeKind.ACCOUNT -> R.string.acct_signed_google
                    kind == PeopleRules.MeKind.GUEST -> R.string.acct_signed_guest
                    else -> R.string.app_a_not_signed_in
                }), fontFamily = Plex, fontSize = 13.5.sp, color = Color(0xE6FFFFFF))
            }
            if (kind == PeopleRules.MeKind.ACCOUNT && !renaming) SecondaryButton(stringResource(R.string.app_a_change), k.onDark()) { renaming = true }
        }
        ErrLine(act.error, k.onDark())
        // my days (S10): the first row, in a box of its own with the accent's edge, only once there is a day (the sample not counted)
        val ctx = LocalContext.current
        val days by androidx.compose.runtime.produceState(0) { value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { io.github.pini236.skiapp.dayrec.DayRec.store(ctx).days().size }.getOrDefault(0) } }
        LaunchedEffect(days) { Qa.log("redesign people me days $days") }
        if (days > 0) Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(22.dp)).background(k.surf.copy(alpha = 1f))
            .border(1.5.dp, k.acc, RoundedCornerShape(22.dp)).padding(horizontal = 16.dp, vertical = 4.dp)) {
            PRow(stringResource(R.string.app_days_title), k, sub = pluralStringResource(R.plurals.app_me_days_sub, days, days), icon = Icons.calendar, last = true) { p.onDays() }
        }
        // a guest in a group: keep the place (R10)
        if (kind == PeopleRules.MeKind.GUEST) PBox(k) {
            Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.app_guest_save_title), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = k.ink)
                Small(stringResource(R.string.app_a_guest_note), k)
                GoogleWide(stringResource(R.string.app_a_save_google), k, act.busy) { act.run { p.signInGoogle(); ui.changes++; p.onChanged() } }
            }
        }
        if (kind == PeopleRules.MeKind.NOBODY) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PBox(k) {
                PRow(stringResource(R.string.acct_signin_title), k, icon = Icons.key) { ui.go(PLayer.SignIn) }
                // until the village (part 4) holds "the group", the way to a code or a new group is here
                PRow(stringResource(R.string.app_grp_title), k, sub = stringResource(R.string.app_grp_sub), icon = Icons.people, last = true) { ui.go(PLayer.Gate) }
            }
            Small(stringResource(R.string.app_a_not_signed_in), k.onDark(), Modifier.padding(horizontal = 24.dp))
        }
        // my groups
        if (kind != PeopleRules.MeKind.NOBODY) PBox(k) {
            Text(stringResource(R.string.acct_my_groups), Modifier.padding(top = 12.dp, bottom = 4.dp), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = k.ink2)
            val gs = groups.orEmpty()
            if (gs.isEmpty()) {
                Small(stringResource(R.string.acct_no_groups), k, Modifier.padding(vertical = 6.dp))
                PRow(stringResource(R.string.app_g_create), k, icon = Icons.plus, last = true) { ui.go(PLayer.Gate) }
            }
            for ((i, g) in gs.withIndex()) PRow(g.name, k, sub = g.members?.let { pluralStringResource(R.plurals.app_g_members, it, it) },
                end = if (g.admin) stringResource(R.string.acct_role_admin) else null, last = i == gs.lastIndex,
                lead = { Av(g.name, g.id, 36, group = true) }) { ui.go(PLayer.Group(g.id)) }
        }
        PBox(k) {
            PRow(stringResource(R.string.about_settings), k, sub = stringResource(R.string.app_me_settings_sub), icon = Icons.gear) { ui.go(PLayer.Settings) }
            PRow(stringResource(R.string.about_language), k, end = Lang.current(LocalContext.current.resources).name, icon = Icons.dayNight) { ui.go(PLayer.Language) }
            PRow(stringResource(R.string.app_about_title), k, sub = stringResource(R.string.app_me_about_sub), icon = Icons.people, last = true) { ui.go(PLayer.About) }
        }
        if (kind == PeopleRules.MeKind.ACCOUNT) {
            PBox(k) {
                val gOn = me?.google == true; val aOn = me?.apple == true
                PRow(stringResource(R.string.app_a_ways_in), k, sub = listOf(
                    stringResource(R.string.acct_google) + " " + stringResource(if (gOn) R.string.acct_connected else R.string.acct_not_connected),
                    stringResource(R.string.acct_apple) + " " + stringResource(if (aOn) R.string.acct_connected else R.string.acct_not_connected)).joinToString(" · "),
                    icon = Icons.key) { ways = !ways }
                if (ways) {
                    Small(stringResource(R.string.app_a_ways_in_sub), k, Modifier.padding(vertical = 6.dp))
                    PRow(stringResource(R.string.acct_google), k, end = if (gOn) stringResource(R.string.acct_connected) else null,
                        trailing = { if (!gOn) TextLink(stringResource(R.string.app_a_connect), k) { act.run { p.signInGoogle(); ui.changes++ } } })
                    PRow(stringResource(R.string.acct_apple), k, end = stringResource(if (aOn) R.string.acct_connected else R.string.acct_not_connected))
                }
                PRow(stringResource(R.string.app_a_sign_out), k, icon = Icons.out) {
                    act.run { api.signOut(); Qa.log("redesign people signed out"); p.onChanged(); ui.changes++; ui.close() }
                }
                PRow(stringResource(R.string.app_a_delete), k, danger = true, last = true) { ui.go(PLayer.Delete) }
            }
            Small(stringResource(R.string.acct_sync_note), k.onDark(), Modifier.padding(horizontal = 24.dp))
        }
    }
}

/** The white sheet's ink over the veil: the head of a full screen is on the dimmed mountain, so it is white. */
private fun SheetInk.onDark(): SheetInk = SheetInk(true)

/** Google's button (section 1): white, the official G, the same by day and night; at least 52. */
@Composable
private fun GoogleWide(text: String, k: SheetInk, busy: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).graphicsAlpha(if (busy) .6f else 1f).clip(RoundedCornerShape(26.dp)).background(Color.White)
        .border(1.dp, Color(0xFF747775), RoundedCornerShape(26.dp)).semantics { role = Role.Button }.clickable(enabled = !busy, onClick = onClick)
        .padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
        Image(painterResource(R.drawable.ic_google_g), null, Modifier.size(20.dp))
        Text(if (busy) stringResource(R.string.app_loading) else text, fontFamily = Plex, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = Color(0xFF1F1F1F))
    }
}

@Composable
private fun BoxScope.DeleteScreen(p: People, ui: PeopleUi, k: SheetInk) {
    val act = rememberAct()
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) { if (armed) { delay(4000); armed = false } }
    FullScreen(k, stringResource(R.string.acct_delete_title), { ui.back() }) {
        PBox(k) {
            Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.acct_delete_what), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 15.5.sp, color = k.ink)
                for (r in listOf(R.string.acct_delete_1, R.string.acct_delete_2, R.string.acct_delete_3)) Text("• " + stringResource(r), fontFamily = Plex, fontSize = 14.sp, color = k.ink)
                Small(stringResource(R.string.app_a_delete_note, "gudauri-ski-trip.vercel.app/account"), k)
                Box(Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(26.dp)).background(k.red).semantics { role = Role.Button }
                    .clickable(enabled = !act.busy) {
                        if (!armed) armed = true
                        else act.run { p.api.deleteAccount(); Qa.log("redesign people deleted"); p.onChanged(); ui.changes++; ui.close(); ui.toast = null }
                    }, contentAlignment = Alignment.Center) {
                    Text(stringResource(if (armed) R.string.acct_delete_confirm else R.string.acct_delete_now), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                }
                ErrLine(act.error, k)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------- R08: sign in

@Composable
private fun BoxScope.SignInSheet(p: People, ui: PeopleUi, k: SheetInk) {
    val act = rememberAct()
    val cancelled = stringResource(R.string.app_err_cancelled)
    PSheet(k, { ui.back() }) {
        Title(stringResource(R.string.acct_signin_title), k)
        Small(stringResource(R.string.app_signin_sub), k)
        SignTrio(k, listOf(Icons.user to stringResource(R.string.acct_why_name_only), Icons.noMail to stringResource(R.string.acct_why_no_email),
            Icons.noKey to stringResource(R.string.acct_why_no_password)))
        GoogleWide(stringResource(R.string.app_a_google_continue), k, act.busy) {
            act.run {
                try { p.signInGoogle() } catch (e: androidx.credentials.exceptions.GetCredentialCancellationException) { ui.toast = cancelled; return@run }
                Qa.log("redesign people signed in"); ui.changes++; p.onChanged()
                // back to what asked for it: a new group, or me
                ui.back()
            }
        }
        ErrLine(act.error, k)
        TextLink(stringResource(R.string.app_a_not_now), k, modifier = Modifier.align(Alignment.CenterHorizontally)) { ui.back() }
        Small(stringResource(R.string.app_a_moves_over), k, Modifier.fillMaxWidth(), center = true)
    }
}

// ---------------------------------------------------------------------------------------------- R01: no group yet

@Composable
private fun BoxScope.GateSheet(p: People, ui: PeopleUi, k: SheetInk) {
    var code by remember { mutableStateOf("") }
    var state by remember { mutableStateOf<JoinStatus?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    LaunchedEffect(code) {
        if (!InviteCode.isCode(code) && !InviteCode.isToken(code)) return@LaunchedEffect
        // with the sixth letter the check starts by itself
        checking = true; err = null; state = null
        try {
            if (p.api.me() == null) p.api.signInGuest()
            val pv = p.api.preview(code)
            if (pv.status == JoinStatus.OK || pv.status == JoinStatus.ALREADY_MEMBER) { Qa.log("redesign people code ok"); ui.go(PLayer.Join(code)) }
            else { state = pv.status; Qa.log("redesign people code ${pv.status.name.lowercase()}") }
        } catch (e: ApiException) { err = e.code } catch (e: java.io.IOException) { err = "offline" }
        checking = false
    }
    PSheet(k, { ui.back() }) {
        Title(stringResource(R.string.app_grp_title), k)
        Small(stringResource(R.string.app_grp_sub), k)
        SignTrio(k, listOf(Icons.plane to stringResource(R.string.group_empty_flights), Icons.pin to stringResource(R.string.app_grp_why_meet),
            Icons.trophy to stringResource(R.string.app_grp_why_scores)))
        Box(Modifier.fillMaxWidth().height(1.dp).background(k.line))
        Text(stringResource(R.string.app_g_have_code), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = k.ink)
        val bad = state == JoinStatus.INVALID_CODE
        CodeBoxes(code, k, bad, enabled = !checking && state != JoinStatus.RATE_LIMITED) { c -> code = c; if (bad) state = null }
        when (state) {
            JoinStatus.INVALID_CODE -> { Small(stringResource(R.string.app_g_bad_code), k, color = k.red); Small(stringResource(R.string.app_g_bad_code_sub), k) }
            JoinStatus.RATE_LIMITED -> { Small(stringResource(R.string.app_g_too_many_tries), k, color = k.red); Small(stringResource(R.string.app_g_too_many_tries_sub), k) }
            JoinStatus.GROUP_FULL -> Small(stringResource(R.string.app_g_full), k, color = k.red)
            else -> if (code.isNotEmpty() && code.length < 6) Small(stringResource(R.string.app_g_code_letters), k)
        }
        ErrLine(err, k)
        AccButton(stringResource(if (checking) R.string.join_loading else R.string.app_g_join), k, enabled = InviteCode.isCode(code) && !checking) {
            ui.go(PLayer.Join(code))
        }
        SecondaryButton(stringResource(R.string.app_g_create), k, Modifier.fillMaxWidth()) {
            if (p.api.me()?.registered == true) ui.go(PLayer.NewGroup) else { ui.go(PLayer.NewGroup); ui.go(PLayer.SignIn) }
        }
        Small(stringResource(R.string.app_g_rest_works), k)
    }
}

// ---------------------------------------------------------------------------------------------- R02: an invite

@Composable
private fun BoxScope.JoinScreen(p: People, ui: PeopleUi, k: SheetInk, code: String) {
    val api = p.api
    var pv by remember { mutableStateOf<Preview?>(null) }
    var failed by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(code) {
        try { if (api.me() == null) api.signInGuest(); pv = api.preview(code) } catch (e: ApiException) { failed = e.code } catch (e: java.io.IOException) { failed = "offline" }
    }
    val v = pv
    LaunchedEffect(v) { if (v != null && (v.alreadyMember || v.status == JoinStatus.ALREADY_MEMBER) && v.groupId != null) ui.swap(PLayer.Group(v.groupId)) }
    var pending by remember { mutableStateOf(false) }
    var reclaim by remember { mutableStateOf(false) }
    val act = rememberAct()
    // a request decided while this shows: the group opens by itself
    DisposableEffect(pending) {
        val stop = if (pending) api.watchMine { approved -> v?.groupId?.let { g -> if (g in approved) { ui.changes++; ui.swap(PLayer.Group(g)) } } } else ({})
        onDispose { stop() }
    }
    FullScreen(k, null, { ui.back() }, top = {
        Spacer(Modifier.height(24.dp))
        if (v != null && v.status == JoinStatus.OK) {
            Text(stringResource(R.string.app_g_invited_to), fontFamily = Plex, fontSize = 15.sp, color = Color.White)
            Text(v.name, fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = if (v.name.length > 14) 34.sp else 44.sp, color = Color.White,
                textAlign = TextAlign.Center, maxLines = 2, lineHeight = if (v.name.length > 14) 38.sp else 48.sp)
            if (v.startsOn != null && v.endsOn != null) Text(stringResource(R.string.app_trip_range, io.github.pini236.skiapp.season.shortDate(v.startsOn),
                io.github.pini236.skiapp.season.shortDate(v.endsOn)), fontFamily = Plex, fontSize = 15.sp, color = Color.White)
            if (!v.requiresApproval && v.members.isNotEmpty()) {
                val (n, more) = PeopleRules.circles(v.members.size)
                Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                    for ((id, name) in v.members.take(n)) Av(name, id, 28, ring = k.surf.copy(alpha = 1f))
                    if (more > 0) Box(Modifier.size(28.dp).clip(CircleShape).background(k.line), contentAlignment = Alignment.Center) {
                        Text("+$more", fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White)
                    }
                }
            }
            if (v.members.isNotEmpty()) Text(pluralStringResource(R.plurals.app_g_members, v.members.size, v.members.size), Modifier.clip(RoundedCornerShape(14.dp))
                .background(Color(0x33FFFFFF)).padding(horizontal = 12.dp, vertical = 4.dp), fontFamily = Plex, fontSize = 13.sp, color = Color.White)
        }
    }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            LiveSheet(k) {
                when {
                    failed != null -> ErrLine(failed, k)
                    v == null -> Small(stringResource(R.string.app_loading), k)
                    v.status != JoinStatus.OK -> {
                        Text(stringResource(R.string.app_g_bad_code), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = k.ink)
                        Small(stringResource(R.string.app_g_bad_code_sub), k)
                        AccButton(stringResource(R.string.app_g_retype), k) { ui.swap(PLayer.Gate) }
                    }
                    pending -> {
                        Text(stringResource(R.string.join_pending_title), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = k.ink)
                        Small(stringResource(R.string.join_st_pending), k)
                        TextLink(stringResource(R.string.join_cancel_request), k) { act.run { v.groupId?.let { api.cancelRequest(it) }; pending = false } }
                    }
                    reclaim -> Reclaim(p, ui, k, code, v)
                    else -> {
                        var name by remember { mutableStateOf(api.me()?.name ?: "") }
                        Text(stringResource(R.string.app_g_your_name), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = k.ink)
                        PField(name, stringResource(R.string.app_g_your_name_hint), k) { name = it.take(30) }
                        AccButton(stringResource(if (act.busy) R.string.join_loading else if (v.requiresApproval) R.string.app_g_ask_to_join else R.string.app_g_join), k,
                            enabled = name.isNotBlank() && !act.busy) {
                            act.run {
                                if (api.me() == null) api.signInGuest()
                                val r = api.join(code, name.trim())
                                Qa.log("redesign people join ${r.status.name.lowercase()}")
                                when (r.status) {
                                    JoinStatus.PENDING -> pending = true
                                    JoinStatus.JOINED, JoinStatus.OK, JoinStatus.ALREADY_MEMBER -> r.groupId?.let { g ->
                                        ui.changes++; p.onChanged(); ui.swap(PLayer.Group(g)); ui.toast = null
                                    }
                                    else -> throw ApiException(r.status.name.lowercase())
                                }
                            }
                        }
                        ErrLine(act.error, k)
                        if (!v.requiresApproval && v.members.isNotEmpty()) TextLink(stringResource(R.string.app_g_already_in), k) { reclaim = true }
                        Small(stringResource(R.string.app_join_later_note), k)
                    }
                }
            }
        }
    }
}

/** "I'm already in the group" (R02): pick my name; an admin approves. A member with an account signs in instead. */
@Composable
private fun Reclaim(p: People, ui: PeopleUi, k: SheetInk, code: String, v: Preview) {
    val act = rememberAct()
    var picked by remember { mutableStateOf<String?>(null) }
    var signIn by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    Small(stringResource(R.string.app_g_reclaim_intro, v.name), k)
    for ((id, name) in v.members) {
        val sel = picked == id
        Row(Modifier.fillMaxWidth().heightIn(min = 54.dp).clip(RoundedCornerShape(14.dp)).let { if (sel) it.background(k.ridgeFill) else it }
            .semantics { selected = sel; role = Role.RadioButton }.clickable { picked = id }.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Av(name, id, 36)
            Text("⁨$name⁩", Modifier.weight(1f), fontFamily = Plex, fontWeight = FontWeight.Medium, fontSize = 15.5.sp, color = k.ink)
            if (sel) Icon(Icons.check, null, Modifier.size(20.dp), tint = k.acc)
        }
    }
    when {
        sent -> { Text(stringResource(R.string.join_pending_title), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = k.ink); Small(stringResource(R.string.join_st_pending), k) }
        signIn -> {
            Small(stringResource(R.string.app_g_sign_in_instead), k)
            AccButton(stringResource(R.string.join_sign_in_btn), k) { ui.go(PLayer.SignIn) }
        }
        else -> AccButton(stringResource(R.string.app_g_send_request), k, enabled = picked != null && !act.busy) {
            act.run {
                if (p.api.me() == null) p.api.signInGuest()
                val r = p.api.reclaim(code, picked!!)
                Qa.log("redesign people reclaim ${r.status.name.lowercase()}")
                when (r.status) {
                    JoinStatus.SIGN_IN_INSTEAD -> signIn = true
                    JoinStatus.PENDING -> sent = true
                    JoinStatus.JOINED, JoinStatus.OK -> r.groupId?.let { ui.changes++; ui.swap(PLayer.Group(it)) }
                    else -> throw ApiException(r.status.name.lowercase())
                }
            }
        }
    }
    ErrLine(act.error, k)
}

// ---------------------------------------------------------------------------------------------- a new group

@Composable
private fun BoxScope.NewGroupScreen(p: People, ui: PeopleUi, k: SheetInk, trip: Trip?, today: LocalDate, haptics: Haptics) {
    val api = p.api
    var name by remember { mutableStateOf("") }
    var me by remember { mutableStateOf(api.me()?.name ?: "") }
    var from by remember { mutableStateOf(trip?.out?.date) }
    var to by remember { mutableStateOf(trip?.ret?.date) }
    var show by remember { mutableStateOf(trip != null) }
    var cal by remember { mutableStateOf(false) }
    val act = rememberAct()
    FullScreen(k, stringResource(R.string.app_g_new_title), { ui.back() }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            LiveSheet(k) {
                Text(stringResource(R.string.app_g_group_name), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = k.ink)
                PField(name, stringResource(R.string.app_g_group_name_hint), k) { name = it.take(60) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((label, d) in listOf(R.string.app_g_from_date to from, R.string.app_g_to_date to to)) {
                        SecondaryButton(stringResource(label) + (d?.let { " · " + io.github.pini236.skiapp.season.shortDate(it) } ?: ""), k, Modifier.weight(1f)) { cal = true }
                    }
                }
                Text(stringResource(R.string.app_g_your_name), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = k.ink)
                PField(me, stringResource(R.string.app_g_your_name_hint), k) { me = it.take(30) }
                if (trip != null) SwitchRow(stringResource(R.string.group_show_my_trip), null, show, k, last = true) { show = it }
                AccButton(stringResource(if (act.busy) R.string.app_loading else R.string.app_g_create_it), k, enabled = name.isNotBlank() && me.isNotBlank() && !act.busy) {
                    act.run {
                        val id = api.createGroup(name.trim(), me.trim(), from, to, if (show) trip else null)
                        Qa.log("redesign people group created"); ui.changes++; p.onChanged()
                        ui.swap(PLayer.Group(id)); ui.go(PLayer.Invite(id))
                    }
                }
                ErrLine(act.error, k)
            }
        }
    }
    if (cal) {
        Box(Modifier.matchParentSize().background(Color(0x61060B16)).clickable { cal = false })
        Box(Modifier.align(Alignment.BottomCenter)) {
            val f0 = from
            LiveCalendar(f0?.let { Trip(io.github.pini236.skiapp.trip.Leg(it), to?.let { r -> io.github.pini236.skiapp.trip.Leg(r) }) }, today, k, haptics) { nt ->
                from = nt.out.date; to = nt.ret?.date; cal = false
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------- R03 to R06: the group

@Composable
private fun BoxScope.GroupSheet(p: People, ui: PeopleUi, k: SheetInk, id: String, plan: MeetPlan?, trip: Trip?, today: LocalDate,
                                onOpenMeetup: (String, Meetup) -> Unit, onNewMeetup: (String) -> Unit) {
    val api = p.api
    val watch = remember(id) { api.watch(id) }
    DisposableEffect(watch) { watch.open(); onDispose { watch.close() } }
    val shown by watch.state.collectAsState(watch.now)
    val g = shown.group
    LaunchedEffect(g?.meetups) { if (g != null) p.onMeetups() }
    var tab by remember(id) { mutableStateOf<GroupTab?>(null) }
    LaunchedEffect(g?.startsOn, g?.endsOn) { if (tab == null && g != null) tab = PeopleRules.openingTab(g.startsOn, g.endsOn, today) }
    val t = tab ?: GroupTab.MEETUPS
    // the emulator's tabs (people:tab:meetups)
    LaunchedEffectQa { c -> if (c.startsWith("people:tab:")) tab = GroupTab.of(c.removePrefix("people:tab:")) }
    LaunchedEffect(t, g != null) { if (g != null) Qa.log("redesign people group ${t.key}") }
    val act = rememberAct()
    Box(Modifier.matchParentSize().clickable { ui.back() })
    Box(Modifier.fillMaxSize().statusBarsPadding().padding(top = (PeopleRules.sheetTop(t) - 44).dp)) {
        LiveSheet(k) {
            // the head: the name, the members and the dates; "invite" at the end
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).let { m -> if (g?.admin == true) m.clickable { ui.go(PLayer.Invite(id)) } else m }) {
                    Text(g?.name ?: "…", fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 21.sp, color = k.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (g != null) Small(listOfNotNull(pluralStringResource(R.plurals.app_g_members, g.members.size, g.members.size),
                        if (g.startsOn != null && g.endsOn != null) stringResource(R.string.app_trip_range, io.github.pini236.skiapp.season.shortDate(g.startsOn),
                            io.github.pini236.skiapp.season.shortDate(g.endsOn)) else null).joinToString(" · "), k)
                }
                Row(Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).border(1.dp, k.line, RoundedCornerShape(22.dp)).semantics { role = Role.Button }
                    .clickable { ui.go(PLayer.Invite(id)) }.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.plus, null, Modifier.size(18.dp), tint = k.acc)
                    Text(stringResource(R.string.app_g_invite_short), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = k.acc)
                }
            }
            // the tabs: one picker, 44 a tab
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(k.line)) {
                for ((tb, label) in listOf(GroupTab.MEETUPS to R.string.app_g_tab_meetups, GroupTab.FLIGHTS to R.string.app_g_tab_flights,
                    GroupTab.SCORES to R.string.group_tab_scores, GroupTab.MEMBERS to R.string.app_g_tab_members)) {
                    val on = tb == t
                    Box(Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).let { if (on) it.background(k.ink) else it }
                        .semantics { role = Role.Tab; selected = on }.clickable { tab = tb }, contentAlignment = Alignment.Center) {
                        Text(stringResource(label), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (on) k.surf.copy(alpha = 1f) else k.ink, maxLines = 1)
                    }
                }
            }
            when {
                g == null && shown.gone -> Small(stringResource(R.string.app_g_gone), k)
                g == null && shown.offline -> Small(stringResource(R.string.app_g_offline_never), k)
                g == null -> Small(stringResource(R.string.app_loading), k)
            }
            if (g != null) {
                if (shown.offline) Small(stringResource(R.string.app_g_offline, savedHm(shown.readAt)), k)
                if (shown.waiting) Small(stringResource(R.string.app_g_waiting), k)
                shown.refused?.let { c -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Small(stringResource(R.string.app_g_refused, stringResource(errorRes(c))), k, Modifier.weight(1f), color = k.red)
                    TextLink("✕", k, k.ink) { watch.clearRefused() }
                } }
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (t) {
                        GroupTab.MEETUPS -> Meetups(g, k, plan, onOpen = { m -> onOpenMeetup(id, m) }, onNew = { onNewMeetup(id) }, onRemind = p.onMeetups,
                            onDelete = { m -> act.run { api.removeMeetup(id, m.id); watch.refresh() } })
                        GroupTab.FLIGHTS -> Flights(g, k, trip, act, onSame = { tid -> act.run { p.onMyTrip(api.sameFlight(id, tid)); watch.refresh() } },
                            onShowMine = { act.run { api.showMyTrip(id, g.me?.name ?: "", trip); watch.refresh() } }, onAdd = p.onTripForm)
                        GroupTab.SCORES -> Scores(p, g, k)
                        GroupTab.MEMBERS -> Members(p, ui, g, k, act, reload = { watch.refresh() })
                    }
                    ErrLine(act.error, k)
                }
            }
        }
    }
}

private fun savedHm(ms: Long?): String = ms?.let { java.time.Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalTime().let { t -> "%02d:%02d".format(t.hour, t.minute) } } ?: "—"

/** R03: the next meetup in a box, the rest as rows; the bell remembers on the phone (meet/Reminders.kt). */
@Composable
private fun Meetups(g: Group, k: SheetInk, plan: MeetPlan?, onOpen: (Meetup) -> Unit, onNew: () -> Unit, onRemind: () -> Unit, onDelete: (Meetup) -> Unit) {
    val ctx = LocalContext.current
    val zone = io.github.pini236.skiapp.meet.Meet.GUDAURI
    val now = Instant.now()
    val list = PeopleRules.upcoming(g.meetups, now)
    var toggled by remember { mutableIntStateOf(0) }
    var armed by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(armed) { if (armed != null) { delay(4000); armed = null } }
    fun hm(m: Meetup) = m.at.atOffset(zone).toLocalTime().let { "%02d:%02d".format(it.hour, it.minute) }
    @Composable fun where(m: Meetup) = plan?.byId?.get(m.station)
    if (list.isEmpty()) Small(stringResource(R.string.app_g_no_meetups), k)
    val next = list.firstOrNull { it.at > now }
    if (next != null) {
        val st = where(next)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).border(1.dp, k.line, RoundedCornerShape(22.dp)).semantics { role = Role.Button }
            .clickable { onOpen(next) }.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.app_grp_next_meetup), Modifier.weight(1f), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = k.ink2)
                val mins = java.time.Duration.between(now, next.at).toMinutes()
                Text(stringResource(R.string.app_g_in, inWords0(mins, ctx)), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = k.acc)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(hm(next), fontFamily = Plex, fontWeight = FontWeight.Light, fontSize = 40.sp, color = k.ink)
                Column {
                    Text("⁦${st?.name ?: next.station}⁩", fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = k.ink)
                    if (st != null) Small(stationSub(st), k)
                }
            }
            val on = remember(next.id, toggled) { Reminders.isOn(ctx, next.id) }
            SwitchRow(stringResource(R.string.app_g_remind), null, on, k, last = true) { Reminders.set(ctx, next.id, it); toggled++; onRemind() }
        }
    }
    for (m in list.filter { it != next }) {
        val st = where(m)
        val d = m.at.atOffset(zone).toLocalDate()
        val day = when (d) {
            LocalDate.now(zone) -> stringResource(R.string.app_grp_today)
            LocalDate.now(zone).plusDays(1) -> stringResource(R.string.app_grp_tomorrow)
            else -> io.github.pini236.skiapp.season.shortDate(d)
        }
        val on = remember(m.id, toggled) { Reminders.isOn(ctx, m.id) }
        Row(Modifier.fillMaxWidth().heightIn(min = 54.dp).semantics { role = Role.Button }
            .androidxLongClick({ onOpen(m) }, { armed = m.id }), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("$day · ${hm(m)} · ⁦${st?.name ?: m.station}⁩", fontFamily = Plex, fontWeight = FontWeight.Medium, fontSize = 15.5.sp, color = k.ink)
                Small(listOfNotNull(st?.let { stationSub(it) }, m.byName?.let { stringResource(R.string.app_g_set_by, it) }).joinToString(" · "), k)
                if (armed == m.id) TextLink(stringResource(R.string.app_g_meetup_delete), k, k.red) { armed = null; onDelete(m) }
            }
            val bell = stringResource(R.string.app_g_remind)
            val st2 = stringResource(if (on) R.string.app_on else R.string.app_off)
            Box(Modifier.size(44.dp).semantics { contentDescription = bell; stateDescription = st2; role = Role.Switch }
                .clickable { Reminders.set(ctx, m.id, !on); toggled++; onRemind() }, contentAlignment = Alignment.Center) {
                Icon(Icons.bell, null, Modifier.size(20.dp), tint = if (on) k.acc else k.ink2)
            }
        }
    }
    AccButton(stringResource(R.string.app_g_new_meetup), k, onClick = onNew)
    Small(stringResource(R.string.app_g_meetups_offline), k)
}

/** A row: a tap opens it, a long press offers deleting (Android, R03). */
private fun Modifier.androidxLongClick(onClick: () -> Unit, onLong: () -> Unit) = this.combinedClickable(onClick = onClick, onLongClick = onLong)

private fun inWords0(minutes: Long, context: android.content.Context): String {
    val loc = context.resources.configuration.locales[0]
    val f = android.icu.text.MeasureFormat.getInstance(loc, android.icu.text.MeasureFormat.FormatWidth.WIDE)
    val m = minutes.coerceAtLeast(1)
    return when {
        m < 60 -> f.formatMeasures(android.icu.util.Measure(m, android.icu.util.MeasureUnit.MINUTE))
        m < 24 * 60 -> if (m % 60 == 0L) f.formatMeasures(android.icu.util.Measure(m / 60, android.icu.util.MeasureUnit.HOUR))
            else f.formatMeasures(android.icu.util.Measure(m / 60, android.icu.util.MeasureUnit.HOUR), android.icu.util.Measure(m % 60, android.icu.util.MeasureUnit.MINUTE))
        else -> f.formatMeasures(android.icu.util.Measure((m + 24 * 60 - 1) / (24 * 60), android.icu.util.MeasureUnit.DAY))
    }
}

/** R04: a small paper card per flight, who is on it in its stub; who has no flight yet under them. */
@Composable
private fun Flights(g: Group, k: SheetInk, trip: Trip?, act: Act, onSame: (String) -> Unit, onShowMine: () -> Unit, onAdd: () -> Unit) {
    var ret by remember { mutableStateOf(false) }
    val cards = remember(g, ret) { PeopleRules.cards(g.members, ret) }
    val me = g.me
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((r, label) in listOf(false to R.string.app_pass_out, true to R.string.app_pass_ret)) {
            val on = r == ret
            Box(Modifier.heightIn(min = 44.dp).padding(vertical = 4.dp).clip(RoundedCornerShape(18.dp)).background(if (on) k.ink else k.line)
                .semantics { role = Role.Tab; selected = on }.clickable { ret = r }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(label), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = if (on) k.surf.copy(alpha = 1f) else k.ink)
            }
        }
        Spacer(Modifier.weight(1f))
        cards.firstOrNull()?.let { Small(io.github.pini236.skiapp.season.shortDate(it.leg.date), k) }
    }
    if (me != null && me.trip == null) {
        if (trip != null) SecondaryButton(stringResource(R.string.app_g_show_mine), k, Modifier.fillMaxWidth(), onShowMine)
        else SecondaryButton(stringResource(R.string.group_add_my_trip), k, Modifier.fillMaxWidth(), onAdd)
    }
    if (cards.isEmpty()) Small(stringResource(R.string.group_no_flights), k)
    val paper = PassPaper(k.acc == Color(0xFFB79CFF))
    val dash = k.line
    for (c in cards) {
        var open by remember(c.key) { mutableStateOf(false) }
        val l = c.leg
        val a11y = listOfNotNull(stringResource(if (ret) R.string.app_pass_ret else R.string.app_pass_out), io.github.pini236.skiapp.season.shortDate(l.date),
            "${l.fromCode ?: l.fromCity} ${l.toCode ?: l.toCity}", l.departs?.toString(), l.flight.takeIf { it.isNotBlank() }?.let { stringResource(R.string.ticket_flight) + " " + it },
            pluralStringResource(R.plurals.app_grp_on_flight_n, c.members.size, c.members.size)).joinToString(", ")
        Column(Modifier.fillMaxWidth().shadow(6.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(paper.paper)
            .border(1.dp, k.line, RoundedCornerShape(18.dp)).semantics(mergeDescendants = true) { contentDescription = a11y }.clickable { open = !open }
            .padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CompositionLocalProvider(LocalLayoutDirection provides LocalLayoutDirection.current) {
                    Column { Text(l.fromCode ?: l.fromCity.ifBlank { "—" }, fontFamily = Plex, fontWeight = FontWeight.Light, fontSize = 30.sp, color = paper.ink)
                        l.departs?.let { Text(it.toString(), fontFamily = Plex, fontSize = 12.sp, color = paper.ink2) } }
                    Icon(Icons.plane, null, Modifier.padding(horizontal = 10.dp).size(20.dp), tint = k.acc)
                    Column { Text(l.toCode ?: l.toCity.ifBlank { "—" }, fontFamily = Plex, fontWeight = FontWeight.Light, fontSize = 30.sp, color = paper.ink)
                        l.arrives?.let { Text(it.toString(), fontFamily = Plex, fontSize = 12.sp, color = paper.ink2) } }
                }
                Spacer(Modifier.weight(1f))
                if (l.flight.isNotBlank()) Column(horizontalAlignment = Alignment.End) {
                    Text(stringResource(R.string.ticket_flight), fontFamily = Plex, fontSize = 11.5.sp, color = paper.ink2)
                    Text("⁦${l.flight}⁩", fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = paper.ink)
                }
            }
            Box(Modifier.padding(vertical = 8.dp).fillMaxWidth().height(2.dp).dashed(dash))
            Row(Modifier.heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) { for (m in c.members.take(6)) Av(m.name, m.userId, 28, ring = paper.paper) }
                Text(if (c.members.size == 1) "⁨${c.members[0].name}⁩" else pluralStringResource(R.plurals.app_grp_on_flight_n, c.members.size, c.members.size),
                    Modifier.padding(start = 10.dp).weight(1f), fontFamily = Plex, fontSize = 13.sp, color = paper.ink)
                if (c.mine) Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.check, null, Modifier.size(16.dp), tint = k.good)
                    Text(stringResource(R.string.group_you), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = k.good)
                } else if (!ret) c.members.firstOrNull { it.tripId != null }?.tripId?.let { tid ->
                    TextLink(stringResource(R.string.app_g_same_flight), k) { onSame(tid) }
                }
            }
            c.byAdmin?.let { Small(stringResource(R.string.group_entered_by_admin), k, color = paper.ink2) }
            if (open) for (m in c.members) Text("⁨${m.name}⁩", fontFamily = Plex, fontSize = 14.sp, color = paper.ink)
        }
    }
    val none = g.members.filter { it.trip == null && !it.me }
    for (m in none) PRow("⁨${m.name}⁩", k, sub = stringResource(R.string.group_no_flight), lead = { Av(m.name, m.userId, 36) }, last = m == none.last())
    Small(stringResource(R.string.app_g_flights_private), k)
}

/** A dashed rule, 2 high. */
private fun Modifier.dashed(c: Color) = this.drawBehind {
    drawLine(c, androidx.compose.ui.geometry.Offset(0f, size.height / 2), androidx.compose.ui.geometry.Offset(size.width, size.height / 2), size.height,
        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
}

/** R05: the games with scores as pills, the table with my row lit, ties sharing a place. */
@Composable
private fun Scores(p: People, g: Group, k: SheetInk) {
    val games = listOf("merge" to R.string.games_merge_name, "descent" to R.string.games_descent_name, "school" to R.string.games_school_name,
        "snowball" to R.string.games_snowball_name)
    var game by remember { mutableStateOf(games.first().first) }
    var rows by remember { mutableStateOf<List<Score>?>(null) }
    val act = rememberAct()
    LaunchedEffect(game, g.id) { rows = null; act.run { rows = p.api.leaderboard(g.id, game); Qa.log("redesign people scores $game ${rows?.size}") } }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((key, name) in games) {
            val on = key == game
            Box(Modifier.heightIn(min = 44.dp).padding(vertical = 4.dp).clip(RoundedCornerShape(18.dp)).background(if (on) k.ink else k.line)
                .semantics { role = Role.Tab; selected = on }.clickable { game = key }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(name), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = if (on) k.surf.copy(alpha = 1f) else k.ink)
            }
        }
    }
    val list = rows
    when {
        list == null -> if (act.busy) Small(stringResource(R.string.app_loading), k) else ErrLine(act.error, k)
        list.isEmpty() -> Small(stringResource(R.string.app_g_no_scores), k)
        else -> {
            val places = PeopleRules.places(list)
            val meSuffix = stringResource(R.string.app_g_me_suffix)
            for ((i, s) in list.withIndex()) {
                val a11y = "${places[i]}, ${s.name}, ${"%,d".format(s.best)}"
                Row(Modifier.fillMaxWidth().heightIn(min = 54.dp).let { if (s.me) it.offset(x = 0.dp).clip(RoundedCornerShape(14.dp)).background(k.ridgeFill) else it }
                    .semantics(mergeDescendants = true) { contentDescription = a11y }.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(places[i].toString(), Modifier.widthIn(min = 26.dp), fontFamily = Plex, fontWeight = FontWeight.Light, fontSize = 23.sp, color = k.ink)
                    Av(s.name, s.userId, 36)
                    Text("⁨${s.name}⁩" + if (s.me) " $meSuffix" else "", Modifier.weight(1f), fontFamily = Plex, fontWeight = FontWeight.Medium, fontSize = 15.5.sp, color = k.ink)
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text("%,d".format(s.best), fontFamily = Plex, fontWeight = FontWeight.Light, fontSize = 25.sp, color = k.ink)
                    }
                }
            }
        }
    }
    Small(stringResource(R.string.app_g_scores_note), k)
    SecondaryButton(stringResource(R.string.app_grp_play_now), k, Modifier.fillMaxWidth()) { p.onGame(game) }
}

/** R06: requests for admins, the members, and the group's actions; red ones ask twice. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun Members(p: People, ui: PeopleUi, g: Group, k: SheetInk, act: Act, reload: () -> Unit) {
    val api = p.api
    var armed by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(armed) { if (armed != null) { delay(4000); armed = null } }
    var menuFor by remember { mutableStateOf<Member?>(null) }
    if (g.admin && g.requests.isNotEmpty()) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).border(1.dp, k.line, RoundedCornerShape(22.dp))
        .padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.app_g_requests), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = k.ink2)
        for (q in g.requests) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Av(q.name, q.userId, 36)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(if (q.reclaim) R.string.app_g_request_reclaim else R.string.app_g_request_join, q.name), fontFamily = Plex, fontWeight = FontWeight.Medium, fontSize = 15.5.sp, color = k.ink)
                    Small(stringResource(if (q.reclaim) R.string.app_g_request_reclaim_sub else R.string.app_g_request_join_sub), k)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccButton(stringResource(R.string.group_approve), k, Modifier.weight(1f)) { act.run { api.decide(q.id, true); reload() } }
                SecondaryButton(stringResource(R.string.app_g_reject), k, Modifier.weight(1f)) { act.run { api.decide(q.id, false); reload() } }
            }
        }
    }
    val meSuffix = stringResource(R.string.app_g_me_suffix)
    for (m in g.members) {
        val leg = m.trip?.out
        val sub = if (leg != null) stringResource(R.string.app_grp_flight_line, stringResource(R.string.app_pass_out), io.github.pini236.skiapp.season.shortDate(leg.date),
            leg.flight.ifBlank { leg.toCode ?: "" }) else stringResource(R.string.group_no_flight)
        PRow("⁨${m.name}⁩" + if (m.me) " $meSuffix" else "", k, sub = sub, lead = { Av(m.name, m.userId, 36) },
            trailing = {
                if (m.role == GRole.ADMIN) Text(stringResource(R.string.app_g_role_admin), Modifier.height(20.dp).border(1.dp, k.line, RoundedCornerShape(9.dp)).padding(horizontal = 7.dp),
                    fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = k.ink2)
                if (g.admin && !m.me) {
                    val label = stringResource(R.string.app_g_actions_on, m.name)
                    Box(Modifier.size(44.dp).semantics { contentDescription = label; role = Role.Button }.clickable { menuFor = m }, contentAlignment = Alignment.Center) {
                        Icon(Icons.more, null, Modifier.size(22.dp), tint = k.ink2)
                    }
                }
            })
    }
    PBoxless(k) {
        if (g.admin) PRow(stringResource(R.string.app_g_name_dates), k) { ui.go(PLayer.Invite(g.id)) }
        PRow(stringResource(if (armed == "leave") R.string.group_leave_confirm else R.string.app_g_leave), k, danger = true, last = !g.admin) {
            if (armed == "leave") act.run { api.leave(g.id); ui.changes++; p.onChanged(); ui.back() } else armed = "leave"
        }
        if (g.admin) PRow(stringResource(if (armed == "delete") R.string.group_delete_confirm else R.string.app_g_delete), k, danger = true, last = true) {
            if (armed == "delete") act.run { api.deleteGroup(g.id); ui.changes++; p.onChanged(); ui.back() } else armed = "delete"
        }
    }
    if (g.members.none { it.role == GRole.ADMIN } && api.me()?.registered == true)
        TextLink(stringResource(R.string.group_claim_admin), k) { act.run { api.claimAdmin(g.id); reload() } }
    // the three dots: Android's bottom sheet of the member's actions
    menuFor?.let { m ->
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { menuFor = null }, containerColor = k.surf.copy(alpha = 1f)) {
            Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding()) {
                Text("⁨${m.name}⁩", Modifier.padding(bottom = 8.dp), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = k.ink)
                if (m.role == GRole.MEMBER) PRow(stringResource(R.string.app_g_make_admin), k) { menuFor = null; act.run { api.setRole(g.id, m.userId, GRole.ADMIN); reload() } }
                else PRow(stringResource(R.string.app_g_unmake_admin), k) { menuFor = null; act.run { api.setRole(g.id, m.userId, GRole.MEMBER); reload() } }
                val sure = armed == "rm:" + m.userId
                PRow(if (sure) stringResource(R.string.group_remove_confirm, m.name) else stringResource(R.string.app_g_remove), k, danger = true, last = true) {
                    if (sure) { menuFor = null; armed = null; act.run { api.removeMember(g.id, m.userId); reload() } } else armed = "rm:" + m.userId
                }
            }
        }
    }
}

/** Rows with no box around them (inside the group's sheet). */
@Composable
private fun PBoxless(k: SheetInk, content: @Composable ColumnScope.() -> Unit) = Column(Modifier.fillMaxWidth(), content = content)

// ---------------------------------------------------------------------------------------------- R07: invite

@Composable
private fun BoxScope.InviteScreen(p: People, ui: PeopleUi, k: SheetInk, id: String) {
    val api = p.api
    val ctx = LocalContext.current
    var g by remember { mutableStateOf<Group?>(null) }
    val act = rememberAct()
    LaunchedEffect(id) { act.run { g = api.group(id) } }
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) { if (armed) { delay(4000); armed = false } }
    val gg = g
    val inv = gg?.invite
    val copied = stringResource(R.string.app_g_code_copied)
    val linkCopied = stringResource(R.string.common_link_copied)
    FullScreen(k, stringResource(R.string.app_g_invite_title), { ui.back() }, top = {
        if (gg != null) {
            Text(stringResource(R.string.app_g_to_group), fontFamily = Plex, fontSize = 15.sp, color = Color.White)
            Text("⁨${gg.name}⁩", fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 27.sp, color = Color.White, textAlign = TextAlign.Center)
            if (inv != null) {
                val aria = stringResource(R.string.app_g_code_aria, inv.code.toList().joinToString(" "))
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(Modifier.semantics(mergeDescendants = true) { contentDescription = aria; role = Role.Button }.clickable {
                        copyText(ctx, inv.code); ui.toast = copied; Qa.log("redesign people invite copied")
                    }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (ch in inv.code) Box(Modifier.size(50.dp, 64.dp).clip(RoundedCornerShape(14.dp)).background(k.surf.copy(alpha = 1f)), contentAlignment = Alignment.Center) {
                            Text(ch.toString(), fontFamily = Plex, fontWeight = FontWeight.Light, fontSize = 33.sp, color = k.ink)
                        }
                    }
                }
                Text(stringResource(R.string.app_g_invite_how), fontFamily = Plex, fontSize = 13.5.sp, color = Color.White, textAlign = TextAlign.Center)
            }
        }
    }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            LiveSheet(k) {
                when {
                    gg == null -> if (act.busy) Small(stringResource(R.string.app_loading), k) else ErrLine(act.error, k)
                    inv == null -> {
                        Small(stringResource(R.string.group_no_invite), k)
                        if (gg.admin) AccButton(stringResource(R.string.group_new_invite), k) { act.run { api.newInvite(id, false); g = api.group(id) } }
                    }
                    else -> {
                        val text = stringResource(R.string.app_g_invite_message, gg.name, InviteCode.shared(inv.token), inv.code)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AccButton(stringResource(R.string.app_g_whatsapp), k, Modifier.weight(1f)) { io.github.pini236.skiapp.group.shareInvite(ctx, text, true) }
                            RoundIcon(Icons.link, stringResource(R.string.app_g_copy_link), k) { copyText(ctx, InviteCode.shared(inv.token)); ui.toast = linkCopied }
                            RoundIcon(Icons.share, stringResource(R.string.app_g_share), k) { io.github.pini236.skiapp.group.shareInvite(ctx, text, false) }
                        }
                        if (gg.admin) {
                            Text(stringResource(R.string.app_g_invite_settings), Modifier.padding(top = 8.dp), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = k.ink)
                            SwitchRow(stringResource(R.string.app_g_manual_title), stringResource(if (inv.requiresApproval) R.string.app_g_manual_on else R.string.app_g_manual_off),
                                inv.requiresApproval, k) { on -> act.run { api.newInvite(id, on); g = api.group(id) } }
                            PRow(stringResource(R.string.app_g_valid), k, sub = gg.endsOn?.let { stringResource(R.string.app_g_valid_until, io.github.pini236.skiapp.season.shortDate(it)) }
                                ?: stringResource(R.string.app_g_valid_90))
                            PRow(stringResource(if (armed) R.string.trip_delete_confirm else R.string.app_g_replace_link), k,
                                sub = stringResource(R.string.app_g_replace_note), danger = true, last = true) {
                                if (armed) { armed = false; act.run { api.newInvite(id, inv.requiresApproval); g = api.group(id) } } else armed = true
                            }
                        }
                        ErrLine(act.error, k)
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, label: String, k: SheetInk, onClick: () -> Unit) =
    Box(Modifier.size(52.dp).clip(CircleShape).border(1.dp, k.line, CircleShape).semantics { contentDescription = label; role = Role.Button }
        .clickable(onClick = onClick), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(22.dp), tint = k.ink) }

private fun copyText(ctx: android.content.Context, text: String) {
    val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    cm.setPrimaryClip(android.content.ClipData.newPlainText("invite", text))
}

// ---------------------------------------------------------------------------------------------- R13 to R15

@Composable
private fun BoxScope.SettingsScreen(p: People, ui: PeopleUi, k: SheetInk) {
    val ctx = LocalContext.current
    var sound by remember { mutableStateOf(FxPrefs.sound(ctx)) }
    var haptic by remember { mutableStateOf(FxPrefs.haptics(ctx)) }
    var analytics by remember { mutableStateOf(Telemetry.enabled(ctx)) }
    var mode by remember { mutableStateOf(p.mode) }
    var armed by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(armed) { if (armed) { delay(4000); armed = false } }
    val canBuzz = remember { (ctx.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator)?.hasVibrator() == true }
    FullScreen(k, stringResource(R.string.about_settings), { ui.back() }) {
        PBox(k) {
            SwitchRow(stringResource(R.string.about_sound), stringResource(R.string.about_sound_sub), sound, k, last = !canBuzz) {
                sound = it; FxPrefs.set(ctx, "sound", it); Telemetry.event("settings_change", mapOf("setting" to "sound", "on" to it))
            }
            if (canBuzz) SwitchRow(stringResource(R.string.about_haptics), null, haptic, k, last = true) {
                haptic = it; FxPrefs.set(ctx, "haptics", it); Telemetry.event("settings_change", mapOf("setting" to "haptics", "on" to it))
            }
        }
        PBox(k) {
            Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.about_display), fontFamily = Plex, fontWeight = FontWeight.Medium, fontSize = 15.5.sp, color = k.ink)
                if (mode == DayNight.Mode.AUTO) Small(stringResource(R.string.app_set_display_auto), k)
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(k.line)) {
                    for ((m, label) in listOf(DayNight.Mode.AUTO to R.string.daynight_mode_auto, DayNight.Mode.DAY to R.string.daynight_mode_day, DayNight.Mode.NIGHT to R.string.daynight_mode_night)) {
                        val on = m == mode
                        Box(Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).let { if (on) it.background(k.ink) else it }
                            .semantics { role = Role.RadioButton; selected = on }.clickable { mode = m; p.onMode(m); Qa.log("redesign people mode ${m.name.lowercase()}") },
                            contentAlignment = Alignment.Center) {
                            Text(stringResource(label), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = if (on) k.surf.copy(alpha = 1f) else k.ink)
                        }
                    }
                }
            }
        }
        PBox(k) {
            val auto = io.github.pini236.skiapp.i18n.Lang.chosen(ctx) == null
            PRow(stringResource(R.string.about_language), k, sub = if (auto) stringResource(R.string.app_set_lang_auto) else null,
                end = Lang.current(ctx.resources).name, last = true) { ui.swap(PLayer.Language) }
        }
        PBox(k) {
            SwitchRow(stringResource(R.string.about_analytics), stringResource(if (analytics) R.string.about_analytics_on else R.string.about_analytics_off), analytics, k) {
                analytics = it; Telemetry.setEnabled(ctx, it, BuildConfig.FLAVOR)
            }
            PRow(stringResource(R.string.about_analytics_what), k, last = true) { p.onPrivacy() }
        }
        PBox(k) {
            PRow(stringResource(when { done -> R.string.about_reset_done; armed -> R.string.about_reset_confirm; else -> R.string.about_reset }), k, danger = armed, last = true) {
                if (armed) { armed = false; done = true; p.onResetBests(); Telemetry.event("best_reset") } else { armed = true; done = false }
            }
        }
    }
}

@Composable
private fun BoxScope.LanguageSheet(p: People, ui: PeopleUi, k: SheetInk) {
    val ctx = LocalContext.current
    val auto = Lang.chosen(ctx) == null
    val cur = Lang.current(ctx.resources)
    PSheet(k, { ui.swap(PLayer.Settings) }) {
        Title(stringResource(R.string.about_language), k)
        for (l in Lang.ALL) {
            val on = !auto && l.tag == cur.tag
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics { role = Role.RadioButton; selected = on }.clickable { p.onLang(l.tag) },
                verticalAlignment = Alignment.CenterVertically) {
                Text(l.name, Modifier.weight(1f), fontFamily = if (l.script == Lang.Script.HEBREW || l.script == Lang.Script.LATIN) Plex else androidx.compose.ui.text.font.FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 19.sp, color = k.ink)
                if (on) Icon(Icons.check, null, Modifier.size(22.dp), tint = k.acc)
            }
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics { role = Role.RadioButton; selected = auto }.clickable { p.onLang(null) },
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.app_lang_phone), fontFamily = Plex, fontWeight = FontWeight.Medium, fontSize = 19.sp, color = k.ink)
                Small(stringResource(R.string.app_lang_auto_note), k)
            }
            if (auto) Icon(Icons.check, null, Modifier.size(22.dp), tint = k.acc)
        }
    }
}

@Composable
private fun BoxScope.AboutScreen(p: People, ui: PeopleUi, k: SheetInk) {
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    FullScreen(k, stringResource(R.string.app_about_title), { ui.back() }, top = {
        Text("Gudi", fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 44.sp, color = Color.White)
        Text(stringResource(R.string.app_about_version, p.version), fontFamily = Plex, fontSize = 13.5.sp, color = Color.White)
        Text(stringResource(R.string.about_unofficial), fontFamily = Plex, fontSize = 13.5.sp, color = Color.White, textAlign = TextAlign.Center)
    }) {
        PBox(k) {
            PRow(stringResource(R.string.about_builder_name), k, sub = stringResource(R.string.about_who) + " · " + stringResource(R.string.app_about_with, "Claude"),
                end = "GitHub", last = true) { uri.openUri("https://github.com/pini236") }
        }
        PBox(k) {
            Text(stringResource(R.string.app_about_data_title), Modifier.padding(top = 12.dp, bottom = 4.dp), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = k.ink2)
            PRow(stringResource(R.string.about_credit_osm_label), k, sub = stringResource(R.string.about_credit_osm_link) + " · ODbL") { uri.openUri("https://www.openstreetmap.org/copyright") }
            PRow(stringResource(R.string.about_credit_terrain_label), k, sub = stringResource(R.string.about_credit_terrain))
            PRow(stringResource(R.string.about_credit_weather_label), k, sub = "Open-Meteo · CC BY 4.0") { uri.openUri("https://open-meteo.com/") }
            Small(stringResource(R.string.about_credit_mta), k, Modifier.padding(vertical = 10.dp))
        }
        PBox(k) {
            PRow(stringResource(R.string.app_about_privacy), k) { p.onPrivacy() }
            PRow(stringResource(R.string.about_support), k) { p.onSupport() }
            PRow(stringResource(R.string.app_licenses_row), k, sub = stringResource(R.string.app_licenses_sub), last = true) { p.onLicenses() }
        }
    }
}
