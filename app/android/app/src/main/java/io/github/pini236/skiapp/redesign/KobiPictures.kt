package io.github.pini236.skiapp.redesign

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import io.github.pini236.skiapp.qa.Qa
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * The Kobi side's seven pictures (P.17, the architect's conditions of 27.11.2026): downloaded from the site, never packed,
 * from `img/peak/kobi/<rev>/` with one rev, each checked against the bytes and the sha256 listed in site/data/peak.json,
 * and kept outside the backup. A new rev replaces the old folder. A picture is in [file] only when it arrived whole and
 * right, so the side is usable after the first one ([nearest] gives the nearest light that is here); the rest come in the
 * background while the app is on the screen, and a half-arrived file is resumed (a Range request), never started again.
 */
internal class KobiSet(context: Context, private val v: PeakContract.View) {
    private val root = File(context.noBackupFilesDir, "peak/kobi")
    private val dir = File(root, v.rev ?: "none")

    /** counts the pictures that arrived: a screen that reads it draws again when one comes in */
    var arrived by mutableIntStateOf(0); private set

    private fun image(light: String) = v.images.firstOrNull { it.light == light }

    /** the picture of [light], only if it is here whole */
    fun file(light: String): File? = image(light)?.let { im -> File(dir, im.file).takeIf { it.isFile && it.length() == im.bytes.toLong() } }

    fun have(): List<String> = v.images.map { it.light }.filter { file(it) != null }
    /** how many are here, read once from the folder (off the main thread) and kept up as they arrive */
    fun scan() { arrived = have().size }
    /** the side can be shown: at least one picture is here (a state read, so a screen that asks draws again when one comes) */
    fun usable() = arrived > 0

    /** the light to draw for [want]: itself, or the nearest that is here (a night picture for a night, by the order of the day) */
    fun nearest(want: String): String? = Rules.nearest(v.images.map { it.light to it.night }, have().toSet(), want)

    /**
     * Fetches what is missing, [want] first and then by nearness to it, while [active] says so (the app on the screen).
     * Stops at the first failure (no reception): it tries again the next time the picture is opened.
     */
    suspend fun fetchAll(want: String, active: () -> Boolean): Boolean = withContext(Dispatchers.IO) {
        val path = v.path ?: run { Qa.log("redesign kobi fetch: no path"); return@withContext false }
        runCatching { root.listFiles()?.filter { it.isDirectory && it.name != dir.name }?.forEach { it.deleteRecursively() } }
        runCatching { dir.mkdirs(); dir.listFiles()?.filter { it.name.endsWith(".part") && v.images.none { im -> im.file + ".part" == it.name } }?.forEach { it.delete() } }
        for (light in Rules.order(v.images.map { it.light to it.night }, want)) {
            if (!active()) return@withContext false
            if (file(light) != null) continue
            val im = image(light) ?: continue
            val ok = runCatching { download(SITE + path + im.file, im, active) }.getOrElse { Qa.log("redesign kobi set failed ${im.file}: $it"); false }
            if (!ok) { Qa.log("redesign kobi set not arrived ${im.file}"); return@withContext false }
            arrived = have().size
            Qa.log("redesign kobi set arrived ${im.file} ${have().size}/${v.images.size}")
        }
        true
    }

    /** one picture; false when it did not arrive (the part stays if the app left the screen, goes if the bytes are wrong) */
    private fun download(url: String, im: PeakContract.Image, active: () -> Boolean): Boolean {
        val part = File(dir, im.file + ".part")
        var have = if (part.isFile) part.length() else 0L
        if (have > im.bytes) { part.delete(); have = 0 }
        if (have < im.bytes) {
            val c = URL(url).openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 10_000; c.readTimeout = 15_000
                if (have > 0) c.setRequestProperty("Range", "bytes=$have-")
                val code = c.responseCode
                if (code != 200 && code != 206) { Qa.log("redesign kobi http $code $url"); return false }
                val append = code == 206 && have > 0
                if (!append) have = 0
                FileOutputStream(part, append).use { out ->
                    c.inputStream.use { ins ->
                        val buf = ByteArray(32 * 1024)
                        while (true) {
                            if (!active()) return false
                            val n = ins.read(buf); if (n < 0) break
                            out.write(buf, 0, n)
                        }
                    }
                }
            } catch (e: IOException) { Qa.log("redesign kobi io ${e.javaClass.simpleName} ${e.message}"); return false } finally { c.disconnect() }
        }
        if (part.length() != im.bytes.toLong() || sha256(part) != im.sha256) { Qa.log("redesign kobi bad bytes ${im.file} ${part.length()}/${im.bytes}"); part.delete(); return false }
        return part.renameTo(File(dir, im.file))
    }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256"); val buf = ByteArray(64 * 1024)
        f.inputStream().use { s -> while (true) { val n = s.read(buf); if (n < 0) break; md.update(buf, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** the order and the nearness of the lights: pure, for the tests */
    object Rules {
        /** [lights] in the order of the day, each with whether it is a night picture */
        fun nearest(lights: List<Pair<String, Boolean>>, here: Set<String>, want: String): String? {
            if (here.isEmpty()) return null
            if (want in here) return want
            val wi = lights.indexOfFirst { it.first == want }
            if (wi < 0) return here.first()
            val wn = lights[wi].second
            return lights.withIndex().filter { it.value.first in here }
                .minWithOrNull(compareBy<IndexedValue<Pair<String, Boolean>>>({ if (it.value.second == wn) 0 else 1 }, { kotlin.math.abs(it.index - wi) }, { it.index }))?.value?.first ?: here.first()
        }

        /** the lights in the order to fetch them: [want] first, then the nearer ones (the same kind first) */
        fun order(lights: List<Pair<String, Boolean>>, want: String): List<String> {
            val wi = lights.indexOfFirst { it.first == want }.coerceAtLeast(0)
            val wn = lights.getOrNull(wi)?.second ?: false
            return lights.withIndex().sortedWith(compareBy<IndexedValue<Pair<String, Boolean>>>({ if (it.value.first == want) 0 else 1 }, { if (it.value.second == wn) 0 else 1 }, { kotlin.math.abs(it.index - wi) }, { it.index })).map { it.value.first }
        }
    }

    companion object {
        /** the site: the pictures are in its `img/peak/kobi/<rev>/` (the host as SiteData.BASE's) */
        const val SITE = "https://gudauri-ski-trip.vercel.app/"

        /** the lifecycle of the screen the context belongs to, to fetch only while the app is on it */
        fun lifecycleOf(context: Context): Lifecycle? {
            var c: Context? = context
            while (c != null) { if (c is LifecycleOwner) return c.lifecycle; c = (c as? ContextWrapper)?.baseContext }
            return null
        }
    }
}
