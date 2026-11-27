package io.github.pini236.skiapp.redesign

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.dayrec.DayParams
import io.github.pini236.skiapp.dayrec.Moments
import io.github.pini236.skiapp.dayrec.ui.DayModel
import io.github.pini236.skiapp.dayrec.ui.MomentText
import io.github.pini236.skiapp.dayrec.ui.StoryData
import io.github.pini236.skiapp.dayrec.ui.Track
import io.github.pini236.skiapp.dayrec.ui.storyPicture
import io.github.pini236.skiapp.fx.Haptics
import io.github.pini236.skiapp.i18n.Lang
import io.github.pini236.skiapp.i18n.Lang.Language
import io.github.pini236.skiapp.i18n.Lang.Script
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import io.github.pini236.skiapp.qa.Qa
import io.github.pini236.skiapp.telemetry.Telemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Round 4, board L11: what leaves the phone, and only when chosen. A sheet over the story: the picture (today's
 * shareStory picture: no name, no fall, no off-piste) or the one-minute video (StoryVideo, made here), the note that
 * says so, "share" through the system's sheet and "save" to the phone. Made only after a tap; the video's progress is
 * shown while it is made. `story_share` gets `native` or `video`, and `save` (docs/GROWTH.md), never for the sample.
 */
@Composable
internal fun DayShare(story: StoryData, sm: DayModel.Summary, paint: DayPaint.Day, hide: List<LongArray>, track: () -> Track?,
                      name: (String) -> String, night: Boolean, sample: Boolean, haptics: Haptics,
                      elev: ((Float, Float) -> Float)? = null, startVideo: Boolean = false,
                      /** The picture the story page already made (DS4), so it is not drawn twice. */
                      pre: Pair<Bitmap, String>? = null,
                      /** "לראות את היום על ההר" under the video card: leaves the share and plays the day's film on the mountain. */
                      onSeeMountain: (() -> Unit)? = null, onBack: () -> Unit) {
    BackHandler { onBack() }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val ink = if (night) Color(0xFFEAF0F7) else Color(0xFF0E2233)
    val muted = if (night) Color(0xB3EAF0F7) else Color(0xFF5B6B7B)
    val glass = if (night) Color(0xF20F1828) else Color(0xFAFFFFFF)
    val rule = if (night) Color(0x33FFFFFF) else Color(0x1F0E2233)
    val accent = Color(0xFF7C4FE0)
    var video by remember { mutableStateOf(startVideo) }
    var pic by remember { mutableStateOf(pre) }
    var making by remember { mutableStateOf<Float?>(null) }
    var made by remember { mutableStateOf<File?>(null) }
    // the pressed button shows the progress, then a saved flash or a failure on itself (DV4-DV8); no dialog
    var pressed by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    var failKind by remember { mutableStateOf<String?>(null) }
    // the film plays at once in the card, drawn by the encoder's own scene (DV1); the file is made behind it for share and save
    var started by remember { mutableStateOf(false) }
    var scene by remember { mutableStateOf<StoryVideo.Scene?>(null) }
    var panelPx by remember { mutableStateOf(0) }
    // the one waiting behind the running button (DV: the other button stays enabled and queues), and where the file was saved
    var queued by remember { mutableStateOf<String?>(null) }
    var savedUri by remember { mutableStateOf<Uri?>(null) }
    // the in-card video (DV1-DV3): its view, state and clock
    var film by remember { mutableStateOf(StoryRules.Film.PLAYING) }
    var posMs by remember { mutableStateOf(0L) }
    val durMs = StoryVideo.SECONDS * 1000L
    val waiters = remember { mutableListOf<Pair<(File) -> Unit, () -> Unit>>() }
    LaunchedEffect(scene, film) {
        if (scene == null || film != StoryRules.Film.PLAYING) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            posMs = (posMs + (now - last) / 1_000_000L).coerceAtMost(durMs); last = now
            if (posMs >= durMs) { film = StoryRules.Film.ENDED; break }
        }
    }
    LaunchedEffect(story) {
        // the board's picture (L11); the moment kind for the measure still comes from the store's picture's rules
        if (pic == null) pic = sharePicture(ctx, story, sm, paint, hide, night, track, name, elev)
        Qa.log("redesign share open")
    }
    val top = pic?.second ?: "other"
    fun measure(method: String) { if (!sample) Telemetry.event("story_share", mapOf("method" to method, "top" to top)) }
    /** The video, made once (on a background thread), then kept for share and save. */
    fun withVideo(onFail: () -> Unit = {}, then: (File) -> Unit = {}) {
        made?.let { if (it.isFile) { then(it); return } }
        // a second asker (the card's tap, then a button) waits for the same encode
        waiters += then to onFail
        if (making != null) return
        making = 0f
        scope.launch {
            val f = File(File(ctx.cacheDir, "share").apply { mkdirs() }, "day.mp4")
            val ok = withContext(Dispatchers.Default) { makeVideo(ctx, f, story, sm, paint, hide, night, mountainOf(ctx, night, elev)) { p -> making = p } }
            making = null
            Qa.log("redesign share video ${if (ok) "made ${f.length()}" else "failed"}")
            val ws = waiters.toList(); waiters.clear()
            if (ok) { made = f; ws.forEach { it.first(f) } } else ws.forEach { it.second() }
        }
    }
    fun toggleFilm() {
        if (scene == null) return
        if (film == StoryRules.Film.ENDED) posMs = 0L
        film = StoryRules.toggle(film)
    }
    fun startFilm() {
        if (started) return
        started = true; film = StoryRules.Film.PLAYING; posMs = 0L
        scope.launch {
            val lang = Lang.current(ctx.resources)
            scene = withContext(Dispatchers.Default) {
                runCatching { StoryVideo.scene(paint, DayPaint.film(paint, sm.maxAt), hide, night, shareWords(ctx, story, sm),
                    Lang.typeface(ctx, lang, false), regular(ctx, lang), lang.rtl, mountainOf(ctx, night, elev)) }.getOrNull()
            }
            if (scene == null) started = false
        }
        withVideo()  // the encoder runs behind the live card; the buttons queue on it as before
    }
    fun openGallery() {
        val u = savedUri ?: return
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(u, ctx.contentResolver.getType(u)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    // the emulator's run makes the video and saves it (no chooser to drive)
    LaunchedEffect(RedesignQa.seq) {
        if (RedesignQa.cmd == "share:video") { video = true; withVideo { f -> scope.launch { val ok = withContext(Dispatchers.IO) { saveVideo(ctx, f) != null }; Qa.log("redesign share saved video $ok") } } }
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF0B1220)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onBack() }) {
        // the screen's title (L11), then the picture as it will leave; in the video's mode the same frame with a play sign
        // (the video is the day's lines filling in on the mountain, under the same words)
        // the top pill is the share's own (board L11, the designer 23.11.2026): drawn over the dimming, fully opaque, where
        // the story's pill is, so it covers it; back closes the share, then "picture to share", and "example" at its end
        Row(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(8.dp).fillMaxWidth().height(48.dp)
            .shadow(10.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp))
            .background(if (night) Color(0xFF0F1828) else Color.White).border(1.dp, rule, RoundedCornerShape(24.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            verticalAlignment = Alignment.CenterVertically) {
            val back = stringResource(R.string.app_g_back)
            Box(Modifier.size(48.dp).clickable(role = Role.Button) { haptics.tick(.2f); onBack() }
                .semantics { contentDescription = back }, contentAlignment = Alignment.Center) {
                Text(if (rtl) "›" else "‹", fontFamily = Gudi, fontSize = 20.sp, color = ink)
            }
            Text(stringResource(R.string.app_meet_share_title), Modifier.weight(1f), fontFamily = Gudi, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = ink, maxLines = 2)
            if (sample) Text(stringResource(R.string.app_day_sample), Modifier.padding(end = 14.dp).clip(RoundedCornerShape(10.dp))
                .background(Color(0x33A98BEE)).padding(horizontal = 8.dp, vertical = 2.dp), fontFamily = Gudi, fontSize = 12.sp, color = ink)
        }
        val panelDp = with(LocalDensity.current) { panelPx.toDp() }
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.align(Alignment.TopCenter).statusBarsPadding()
            .padding(top = 8.dp + 48.dp + 16.dp, start = 36.dp, end = 36.dp, bottom = panelDp + 30.dp).fillMaxSize()) {
          val linkDp = if (video && onSeeMountain != null) 58.dp else 0.dp
          val (cw, ch) = StoryRules.cardSize(maxWidth.value, (maxHeight - linkDp).value, video)
          Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            pic?.first?.let { b ->
                // a tap on the card never reaches the dismiss layer; in video mode it plays the video inside the card, then
                // toggles play and pause (DV1-DV3)
                Box(Modifier.width(cw.dp).height(ch.dp).shadow(18.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF0B1220))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        if (video && !started) startFilm() else if (video) toggleFilm()
                    }, contentAlignment = Alignment.Center) {
                    // the still only before the film starts and in picture mode; never behind or beside the film
                    if (!(video && started)) Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = if (video) ContentScale.Crop else ContentScale.FillWidth)
                    if (video && started) scene?.let { sc ->
                        androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
                            val t = posMs // read here so the canvas redraws with the clock
                            drawIntoCanvas { c -> sc.draw(c.nativeCanvas, t) }
                        }
                        val glassDark = Color(0x99080E1C)
                        val pauseLabel = stringResource(if (film == StoryRules.Film.PLAYING) R.string.app_ds_pause else R.string.app_ds_play)
                        if (film != StoryRules.Film.ENDED) Box(Modifier.align(Alignment.TopStart).padding(10.dp).size(44.dp).clip(CircleShape).background(glassDark)
                            .clickable(role = Role.Button) { haptics.tick(.2f); toggleFilm() }.semantics { contentDescription = pauseLabel }, contentAlignment = Alignment.Center) {
                            androidx.compose.foundation.Canvas(Modifier.size(18.dp)) {
                                if (film == StoryRules.Film.PLAYING) {
                                    val w = size.width * .28f
                                    drawRect(Color.White, androidx.compose.ui.geometry.Offset(size.width * .12f, 0f), androidx.compose.ui.geometry.Size(w, size.height))
                                    drawRect(Color.White, androidx.compose.ui.geometry.Offset(size.width * .60f, 0f), androidx.compose.ui.geometry.Size(w, size.height))
                                } else drawPath(androidx.compose.ui.graphics.Path().apply {
                                    moveTo(size.width * .18f, 0f); lineTo(size.width, size.height / 2); lineTo(size.width * .18f, size.height); close()
                                }, Color.White)
                            }
                        }
                        // "0:25 / 1:00", always left to right
                        Text("\u2066" + StoryRules.clockPair(posMs, durMs) + "\u2069", Modifier.align(Alignment.TopEnd).padding(10.dp).clip(RoundedCornerShape(14.dp)).background(glassDark)
                            .padding(horizontal = 10.dp, vertical = 6.dp), fontFamily = Gudi, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = Color.White)
                        // the progress line along the bottom edge, filling in the reading direction
                        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color.White.copy(alpha = .28f))) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(StoryRules.videoFrac(posMs, durMs)).background(Color.White))
                        }
                        if (film == StoryRules.Film.ENDED) {
                            val again = stringResource(R.string.app_ds_again)
                            Box(Modifier.size(72.dp).clip(CircleShape).background(glassDark).clickable(role = Role.Button) { haptics.tick(.3f); toggleFilm() }
                                .semantics { contentDescription = again }, contentAlignment = Alignment.Center) {
                                Text("\u21BB", fontFamily = Gudi, fontSize = 30.sp, color = Color.White)
                            }
                        }
                    }
                    if (video && !started) androidx.compose.foundation.Canvas(Modifier.size(64.dp)) {
                        drawCircle(Color(0x99080E1C))
                        val r = size.minDimension / 2
                        drawPath(androidx.compose.ui.graphics.Path().apply {
                            moveTo(center.x - r * .28f, center.y - r * .38f); lineTo(center.x + r * .42f, center.y); lineTo(center.x - r * .28f, center.y + r * .38f); close()
                        }, Color.White)
                    }
                }
                // the replay on the mountain is an explicit link under the card, never a tap on it (DV3)
                if (video && onSeeMountain != null) Row(Modifier.padding(top = 12.dp).clip(RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button) { haptics.tick(.3f); onSeeMountain() }.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.foundation.Canvas(Modifier.size(20.dp, 16.dp)) {
                        drawPath(androidx.compose.ui.graphics.Path().apply {
                            moveTo(0f, size.height); lineTo(size.width * .36f, size.height * .2f); lineTo(size.width * .56f, size.height * .55f)
                            lineTo(size.width * .72f, size.height * .36f); lineTo(size.width, size.height); close()
                        }, accent)
                    }
                    Text(stringResource(R.string.app_ds_see_mtn), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                }
            }
          }
        }
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(10.dp).fillMaxWidth()
            .onSizeChanged { panelPx = it.height }
            .shadow(18.dp, RoundedCornerShape(28.dp)).clip(RoundedCornerShape(28.dp)).background(glass).border(1.dp, rule, RoundedCornerShape(28.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // what to send: two tiles, then two buttons; all four 56 high, radius 18, border 1.5, two equal columns (DV, fixes of 27.11)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((on, key) in listOf(false to R.string.app_share_photo, true to R.string.app_share_video)) {
                    val sel = video == on
                    Text(stringResource(key), Modifier.weight(1f).heightIn(min = 56.dp).clip(RoundedCornerShape(18.dp))
                        .background(if (sel) accent.copy(alpha = .1f) else Color.Transparent).border(1.5.dp, if (sel) accent else rule, RoundedCornerShape(18.dp))
                        .clickable(role = Role.Button) { haptics.tick(.2f); video = on; if (!on) { started = false; scene = null; posMs = 0L }; Qa.log("redesign share ${if (on) "video" else "photo"}") }
                        .padding(vertical = 17.dp),
                        fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = if (sel) accent else ink, textAlign = TextAlign.Center)
                }
            }
            fun press(kind: String) {
                when (StoryRules.press(kind, pressed, queued)) {
                    StoryRules.Press.IGNORE -> return
                    StoryRules.Press.QUEUE -> { queued = kind; return }
                    StoryRules.Press.RUN -> {}
                }
                failKind = null; pressed = kind
                fun done(ok: Boolean, uri: Uri? = null) {
                    pressed = null
                    if (!ok) failKind = kind
                    else if (kind == "save") {
                        savedUri = uri; saved = true; haptics.click(.5f)
                        scope.launch { kotlinx.coroutines.delay(4000); saved = false }
                    }
                    // the other button, pressed meanwhile, goes now
                    queued?.let { q -> queued = null; press(q) }
                }
                if (!video) {
                    val b = pic?.first
                    if (b == null) { pressed = null; queued = null; return }
                    if (kind == "share") { io.github.pini236.skiapp.dayrec.ui.StoryImage.share(ctx, b, ctx.getString(R.string.common_site_name), ctx.getString(R.string.app_day_share)); measure("native"); done(true) }
                    else scope.launch { val u = withContext(Dispatchers.IO) { savePicture(ctx, b) }; Qa.log("redesign share saved photo ${u != null}"); if (u != null) measure("save"); done(u != null, u) }
                } else withVideo(onFail = { done(false) }) { f ->
                    if (kind == "share") { shareVideo(ctx, f); measure("video"); done(true) }
                    else scope.launch { val u = withContext(Dispatchers.IO) { saveVideo(ctx, f) }; Qa.log("redesign share saved video ${u != null}"); if (u != null) measure("save"); done(u != null, u) }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((kind, primary) in listOf("share" to true, "save" to false)) {
                    val st = StoryRules.button(kind, pressed, saved, failKind)
                    val busy = st == StoryRules.Btn.BUSY
                    val good = Color(0xFF198549); val warn = Color(0xFFB8741A)
                    val edge = when (st) { StoryRules.Btn.SAVED -> good; StoryRules.Btn.FAILED -> warn; else -> if (queued == kind) accent else if (primary) Color.Transparent else rule }
                    val label = when (st) {
                        StoryRules.Btn.BUSY -> stringResource(if (kind == "share") R.string.app_ds_preparing else R.string.app_ds_saving) +
                            if (video) " · " + (StoryRules.fill(true, making) * 100).toInt() + "%" else ""
                        StoryRules.Btn.SAVED -> "✓ " + stringResource(R.string.app_ds_saved)
                        StoryRules.Btn.FAILED -> "↻ " + stringResource(if (kind == "share") R.string.app_ds_share_fail else R.string.app_ds_save_fail)
                        else -> stringResource(if (kind == "share") R.string.app_day_share else R.string.app_share_save)
                    }
                    Box(Modifier.weight(1f).heightIn(min = 56.dp).clip(RoundedCornerShape(18.dp))
                        .background(if (primary && st == StoryRules.Btn.IDLE) accent else Color.Transparent)
                        .border(1.5.dp, edge, RoundedCornerShape(18.dp))
                        .clickable(role = Role.Button) { haptics.tick(if (primary) .4f else .3f); if (st == StoryRules.Btn.SAVED && savedUri != null) openGallery() else press(kind) }, contentAlignment = Alignment.Center) {
                        if (busy) Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterStart) {
                            // the fill grows from the reading start
                            Box(Modifier.fillMaxHeight().fillMaxWidth(StoryRules.fill(video, making)).background(if (primary) accent.copy(alpha = .55f) else accent.copy(alpha = .16f)))
                        }
                        Text(label, Modifier.padding(vertical = 17.dp, horizontal = 8.dp), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2,
                            color = when { st == StoryRules.Btn.SAVED -> good; st == StoryRules.Btn.FAILED -> warn; primary && (st == StoryRules.Btn.IDLE) -> Color.White; else -> ink },
                            textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

/** The share picture of the day (L11) and the kind of its top moment; null when it cannot be drawn. Used by the share sheet and the story page's own preview. */
internal suspend fun sharePicture(ctx: Context, story: StoryData, sm: DayModel.Summary, paint: DayPaint.Day, hide: List<LongArray>, night: Boolean,
                                  track: () -> Track?, name: (String) -> String, elev: ((Float, Float) -> Float)?): Pair<Bitmap, String>? = withContext(Dispatchers.Default) {
    runCatching {
        val top = storyPicture(ctx, story, sm, track(), name).let { (b, t) -> b.recycle(); t }
        val lang = Lang.current(ctx.resources)
        // the title in the text's bold face, not the old language's display face (the designer, point 6)
        StoryVideo.still(paint, hide, night, shareWords(ctx, story, sm), Lang.typeface(ctx, lang, false), regular(ctx, lang),
            lang.rtl, sm.maxAt?.let { paint.track.at(it) }, mountainOf(ctx, night, elev)) to top
    }.getOrNull()
}

/** The day's words for the video, the picture's: the kind of day, four numbers, "Gudi" and "Gudauri · date". */
private fun makeVideo(ctx: Context, out: File, story: StoryData, sm: DayModel.Summary, paint: DayPaint.Day, hide: List<LongArray>, night: Boolean,
                      mountain: StoryVideo.Mountain?, progress: (Float) -> Unit): Boolean {
    val lang = Lang.current(ctx.resources)
    val words = shareWords(ctx, story, sm)
    val film = DayPaint.film(paint, sm.maxAt)
    // every tenth in the test log, so a slow encoder (the emulator has none in hardware) is told apart from a stuck one
    var told = -1
    return StoryVideo.make(out, paint, film, hide, night, words, Lang.typeface(ctx, lang, false), regular(ctx, lang), lang.rtl, mountain) {
        val tenth = (it * 10).toInt(); if (tenth != told) { told = tenth; Qa.log("redesign share video ${tenth * 10}%") }
        progress(it)
    }
}

/** The text's regular face (the app's helper gives the bold one): Plex Hebrew, Plex Sans or Noto Sans Georgian at 400. */
private fun regular(ctx: Context, lang: Language): Typeface? = runCatching {
    when (lang.script) {
        Script.GEORGIAN -> variable(ctx, R.font.noto_sans_georgian, 400)
        Script.LATIN, Script.CYRILLIC -> variable(ctx, R.font.plex_sans, 400)
        else -> ResourcesCompat.getFont(ctx, R.font.plex_hebrew_regular)
    }
}.getOrNull()

private fun variable(ctx: Context, res: Int, weight: Int): Typeface? {
    val tf = ResourcesCompat.getFont(ctx, res) ?: return null
    return if (Build.VERSION.SDK_INT >= 28) Typeface.create(tf, weight, false) else tf
}

/** The words of the picture and the video (L11): the kind of day, four numbers, "Gudi" and "Gudauri · date". */
private fun shareWords(ctx: Context, story: StoryData, sm: DayModel.Summary): StoryVideo.Words {
    val locale = ctx.resources.configuration.locales[0]
    val head = MomentText.Words(ctx, locale, group = true).render(MomentText.head(story.theme, sm.descents, sm.dropM, sm.distM, locale), Moments.seed("story.head", sm.start)).first
    val date = DateTimeFormatter.ofPattern("d.M.yyyy", Locale.ROOT).format(Instant.ofEpochMilli(sm.start).atZone(ZoneId.of(DayParams.TZ)))
    val numbers = listOf(sm.descents.toString() to ctx.resources.getQuantityString(R.plurals.app_day_sum_descents_n, sm.descents),
        metres(sm.dropM) to ctx.getString(R.string.app_rec_m_down),
        String.format(Locale.ROOT, "%.1f", sm.distM / 1000) to ctx.getString(R.string.app_run_km)) +
        listOfNotNull(sm.maxKmh?.let { io.github.pini236.skiapp.dayrec.ui.LiveNumbers.maxKmh(it) to ctx.getString(R.string.app_rec_kmh_max) })
    // the numbers are digits in every language: Plex Sans light, as on the board
    return StoryVideo.Words(head, numbers, "Gudi", ctx.getString(R.string.common_site_name) + " · " + date,
        runCatching { variable(ctx, R.font.plex_sans, 300) }.getOrNull())
}

private fun shareVideo(ctx: Context, f: File) {
    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".share", f)
    val title = ctx.getString(R.string.app_day_share)
    val send = Intent(Intent.ACTION_SEND).setType("video/mp4").putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_TITLE, title)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Into the phone's pictures (Android 10 and up, no permission; before that the system asks, so it is only shared there). */
private fun savePicture(ctx: Context, b: Bitmap): Uri? {
    if (Build.VERSION.SDK_INT < 29) return null
    val v = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "gudi-day-${System.currentTimeMillis()}.png"); put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Gudi")
    }
    val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v) ?: return null
    return uri.takeIf { runCatching { ctx.contentResolver.openOutputStream(uri)!!.use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } }.getOrDefault(false) }
}

private fun saveVideo(ctx: Context, f: File): Uri? {
    if (Build.VERSION.SDK_INT < 29) return null
    val v = ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, "gudi-day-${System.currentTimeMillis()}.mp4"); put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Gudi")
    }
    val uri = ctx.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, v) ?: return null
    return uri.takeIf { runCatching { ctx.contentResolver.openOutputStream(uri)!!.use { o -> f.inputStream().use { it.copyTo(o) } }; true }.getOrDefault(false) }
}

/** The painted mountain for the picture and the video (L11); null without the ground's heights (then drawn from above). */
private fun mountainOf(ctx: Context, night: Boolean, elev: ((Float, Float) -> Float)?): StoryVideo.Mountain? =
    elev?.let { OnMountain.load(ctx, night, it) }
