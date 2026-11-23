package io.github.pini236.skiapp.redesign

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
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
import io.github.pini236.skiapp.ui.Plex
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
                      elev: ((Float, Float) -> Float)? = null, onBack: () -> Unit) {
    BackHandler { onBack() }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val ink = if (night) Color(0xFFEAF0F7) else Color(0xFF0E2233)
    val muted = if (night) Color(0xB3EAF0F7) else Color(0xFF5B6B7B)
    val glass = if (night) Color(0xF20F1828) else Color(0xFAFFFFFF)
    val rule = if (night) Color(0x33FFFFFF) else Color(0x1F0E2233)
    val accent = Color(0xFF7C4FE0)
    var video by remember { mutableStateOf(false) }
    var pic by remember { mutableStateOf<Pair<Bitmap, String>?>(null) }
    var making by remember { mutableStateOf<Float?>(null) }
    var made by remember { mutableStateOf<File?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(story) {
        // the board's picture (L11); the moment kind for the measure still comes from the store's picture's rules
        pic = withContext(Dispatchers.Default) {
            runCatching {
                val top = storyPicture(ctx, story, sm, track(), name).let { (b, t) -> b.recycle(); t }
                val lang = Lang.current(ctx.resources)
                // the title in the text's bold face, not the old language's display face (the designer, point 6)
                StoryVideo.still(paint, hide, night, shareWords(ctx, story, sm), Lang.typeface(ctx, lang, false), regular(ctx, lang),
                    lang.rtl, sm.maxAt?.let { paint.track.at(it) }, mountainOf(ctx, night, elev)) to top
            }.getOrNull()
        }
        Qa.log("redesign share open")
    }
    val top = pic?.second ?: "other"
    fun measure(method: String) { if (!sample) Telemetry.event("story_share", mapOf("method" to method, "top" to top)) }
    /** The video, made once (on a background thread), then kept for share and save. */
    fun withVideo(then: (File) -> Unit) {
        made?.let { if (it.isFile) { then(it); return } }
        if (making != null) return
        making = 0f; failed = false
        scope.launch {
            val f = File(File(ctx.cacheDir, "share").apply { mkdirs() }, "day.mp4")
            val ok = withContext(Dispatchers.Default) { makeVideo(ctx, f, story, sm, paint, hide, night, mountainOf(ctx, night, elev)) { p -> making = p } }
            making = null
            Qa.log("redesign share video ${if (ok) "made ${f.length()}" else "failed"}")
            if (ok) { made = f; then(f) } else failed = true
        }
    }
    // the emulator's run makes the video and saves it (no chooser to drive)
    LaunchedEffect(RedesignQa.seq) {
        if (RedesignQa.cmd == "share:video") { video = true; withVideo { f -> scope.launch { val ok = withContext(Dispatchers.IO) { saveVideo(ctx, f) }; Qa.log("redesign share saved video $ok") } } }
    }
    Box(Modifier.fillMaxSize().background(Color(0x99050A16)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onBack() }) {
        // the screen's title (L11), then the picture as it will leave; in the video's mode the same frame with a play sign
        // (the video is the day's lines filling in on the mountain, under the same words)
        Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 14.dp, start = 36.dp, end = 36.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.app_meet_share_title), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White)
            pic?.first?.let { b ->
                Box(Modifier.fillMaxWidth().shadow(18.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
                    Image(b.asImageBitmap(), null, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                    if (video) androidx.compose.foundation.Canvas(Modifier.size(64.dp)) {
                        drawCircle(Color(0x99080E1C))
                        val r = size.minDimension / 2
                        drawPath(androidx.compose.ui.graphics.Path().apply {
                            moveTo(center.x - r * .28f, center.y - r * .38f); lineTo(center.x + r * .42f, center.y); lineTo(center.x - r * .28f, center.y + r * .38f); close()
                        }, Color.White)
                    }
                }
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(10.dp).fillMaxWidth()
            .shadow(18.dp, RoundedCornerShape(28.dp)).clip(RoundedCornerShape(28.dp)).background(glass).border(1.dp, rule, RoundedCornerShape(28.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // picture or the one-minute video
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).border(1.dp, rule, RoundedCornerShape(22.dp)).padding(3.dp)) {
                for ((on, key) in listOf(false to R.string.app_share_photo, true to R.string.app_share_video)) {
                    val sel = video == on
                    Text(stringResource(key), Modifier.weight(1f).clip(RoundedCornerShape(19.dp)).background(if (sel) accent else Color.Transparent)
                        .clickable(role = Role.Button) { haptics.tick(.2f); video = on; Qa.log("redesign share ${if (on) "video" else "photo"}") }.padding(vertical = 10.dp),
                        fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (sel) Color.White else ink, textAlign = TextAlign.Center)
                }
            }
            Text(stringResource(R.string.app_share_note), fontFamily = Plex, fontSize = 13.5.sp, color = muted)
            making?.let { p ->
                Text("${(p * 100).toInt()}%", Modifier.fillMaxWidth(), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ink,
                    textAlign = if (rtl) TextAlign.Right else TextAlign.Left)
            }
            if (failed) Text(stringResource(R.string.app_err_general), fontFamily = Plex, fontSize = 13.sp, color = Color(0xFFD1342B))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.app_day_share), Modifier.weight(1f).heightIn(min = 52.dp).clip(RoundedCornerShape(26.dp)).background(accent)
                    .clickable(role = Role.Button) {
                        haptics.tick(.4f)
                        if (!video) pic?.first?.let { io.github.pini236.skiapp.dayrec.ui.StoryImage.share(ctx, it, ctx.getString(R.string.common_site_name), ctx.getString(R.string.app_day_share)); measure("native") }
                        else withVideo { f -> shareVideo(ctx, f); measure("video") }
                    }.padding(vertical = 15.dp),
                    fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White, textAlign = TextAlign.Center)
                Text(stringResource(R.string.app_share_save), Modifier.weight(1f).heightIn(min = 52.dp).clip(RoundedCornerShape(26.dp)).border(1.dp, rule, RoundedCornerShape(26.dp))
                    .clickable(role = Role.Button) {
                        haptics.tick(.3f)
                        if (!video) pic?.first?.let { b -> scope.launch { val ok = withContext(Dispatchers.IO) { savePicture(ctx, b) }; Qa.log("redesign share saved photo $ok"); if (ok) measure("save") } }
                        else withVideo { f -> scope.launch { val ok = withContext(Dispatchers.IO) { saveVideo(ctx, f) }; Qa.log("redesign share saved video $ok"); if (ok) measure("save") } }
                    }.padding(vertical = 15.dp),
                    fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ink, textAlign = TextAlign.Center)
            }
        }
    }
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
    val numbers = listOf(sm.descents.toString() to ctx.getString(R.string.app_day_sum_descents),
        metres(sm.dropM) to ctx.getString(R.string.app_rec_m_down),
        String.format(Locale.ROOT, "%.1f", sm.distM / 1000) to ctx.getString(R.string.app_run_km)) +
        listOfNotNull(sm.maxKmh?.let { String.format(Locale.ROOT, "%.1f", it) to ctx.getString(R.string.app_rec_kmh_max) })
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
private fun savePicture(ctx: Context, b: Bitmap): Boolean {
    if (Build.VERSION.SDK_INT < 29) return false
    val v = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "gudi-day-${System.currentTimeMillis()}.png"); put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Gudi")
    }
    val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v) ?: return false
    return runCatching { ctx.contentResolver.openOutputStream(uri)!!.use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } }.getOrDefault(false)
}

private fun saveVideo(ctx: Context, f: File): Boolean {
    if (Build.VERSION.SDK_INT < 29) return false
    val v = ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, "gudi-day-${System.currentTimeMillis()}.mp4"); put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Gudi")
    }
    val uri = ctx.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, v) ?: return false
    return runCatching { ctx.contentResolver.openOutputStream(uri)!!.use { o -> f.inputStream().use { it.copyTo(o) } }; true }.getOrDefault(false)
}

/**
 * The painted mountain for the picture and the video (L11): the POC's whole-mountain view at noon (at night, its night),
 * with its projection; null without the ground's height or if the picture cannot be read (then the day is drawn from above).
 */
private fun mountainOf(ctx: Context, night: Boolean, elev: ((Float, Float) -> Float)?): StoryVideo.Mountain? {
    if (elev == null) return null
    return runCatching {
        val d = PeakData.load(ctx)
        val file = d.hero.images[if (night) "night" else "noon"] ?: return null
        val img = ctx.assets.open("redesign/$file").use { android.graphics.BitmapFactory.decodeStream(it) } ?: return null
        if (d.hero.proj == null) null else StoryVideo.Mountain(img, d.hero, elev)
    }.getOrNull()
}
