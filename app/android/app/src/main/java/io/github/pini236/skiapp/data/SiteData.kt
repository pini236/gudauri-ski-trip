package io.github.pini236.skiapp.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * The site's data in the app (docs/APP-NATIVE.md, "Data"): everything is packaged, so the app works with no signal
 * from the first launch, and the files that change during the season (runs and lifts, videos) are refreshed from the
 * site without a new version. The site is not touched: the app asks with the ETag the server already sends, and only
 * downloads what changed.
 *
 * Rules, so an old app in someone's phone never breaks:
 * - A download is used only after it parses with this app's own parser and passes sanity checks. Anything else
 *   (an unknown structure, a broken or half file) is dropped and the previous copy stays.
 * - A file may declare `"schema": n` at its top level. An app that knows a lower number ignores it.
 * - Downloads belong to the app version that fetched them. A new version starts again from its packaged copy, which
 *   is at least as new as the release.
 * - New data is read at the next launch, never swapped under the user's feet.
 */
class SiteData(
    private val dir: File,
    private val appVersion: Long,
    private val packaged: (String) -> String,
    private val fetch: Fetcher = HttpFetcher(),
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** One file the app can refresh, with the checks a download must pass. */
    class Spec(val name: String, val schema: Int, val valid: (String) -> Boolean)

    class Response(val code: Int, val etag: String?, val body: ByteArray?)

    fun interface Fetcher { fun get(url: String, etag: String?): Response }

    enum class Outcome { UPDATED, UNCHANGED, REJECTED, FAILED, SKIPPED }

    companion object {
        const val BASE = "https://gudauri-ski-trip.vercel.app/data/"
        const val EVERY_MS = 6 * 3600_000L
        const val MAX_BYTES = 4 shl 20
        private const val META = "meta.json"

        val FILES = listOf(
            Spec("runs-and-lifts.json", 1) { validRuns(it) },
            Spec("videos-seed.json", 1) { validVideos(it) },
        )

        /** The top-level "schema" of an object file, 1 when it has none. */
        private fun schemaOf(json: String): Int {
            val t = json.trimStart()
            return if (t.startsWith("{")) JSONObject(t).optInt("schema", 1) else 1
        }

        fun validRuns(json: String): Boolean = runCatching {
            if (schemaOf(json) > 1) return false
            val r = Runs.parse(json)
            r.pistes.size >= 10 && r.lifts.size >= 5 && r.pistes.all { it.key.isNotBlank() } && r.pistes.count { it.lines.isNotEmpty() } >= 10
        }.getOrDefault(false)

        fun validVideos(json: String): Boolean = runCatching {
            val t = json.trimStart()
            if (!t.startsWith("[")) return false // an unknown structure: keep what we have
            val a = JSONArray(t)
            (0 until a.length()).all { i ->
                val v = a.getJSONObject(i)
                v.optString("piste").isNotBlank() && v.optString("url").startsWith("https://")
            }
        }.getOrDefault(false)
    }

    private val meta: JSONObject by lazy { loadMeta() }

    private fun loadMeta(): JSONObject {
        dir.mkdirs()
        val m = runCatching { JSONObject(File(dir, META).readText()) }.getOrNull()
        if (m == null || m.optLong("app", -1) != appVersion) {
            // a new app version: its packaged files win, and the next refresh starts from them
            FILES.forEach { File(dir, it.name).delete() }
            return JSONObject().put("app", appVersion).put("files", JSONObject())
        }
        return m
    }

    /**
     * The checks and ETags on disk. A failed write only means the next launch asks the site again, so it never stops
     * the app (Sentry GUDI-ANDROID-3: a write that failed while the phone restarted crashed the app).
     */
    private fun saveMeta() { runCatching { writeAtomic(File(dir, META), meta.toString().toByteArray()) } }

    private fun writeAtomic(f: File, bytes: ByteArray) {
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(f)) { f.delete(); if (!tmp.renameTo(f)) throw IOException("cannot replace ${f.name}") }
    }

    private fun spec(name: String) = FILES.firstOrNull { it.name == name }

    /** The newest valid copy of a data file: the downloaded one if there is one, otherwise the packaged one. */
    @Synchronized
    fun read(name: String): String {
        val s = spec(name) ?: return packaged(name)
        val files = meta.getJSONObject("files") // first: a new app version drops the old downloads here
        val f = File(dir, name)
        if (f.isFile) {
            val text = runCatching { f.readText() }.getOrNull()
            if (text != null && s.valid(text)) return text
            f.delete() // damaged on disk: forget it, and its ETag, so the next refresh downloads it again
            files.remove(name)
            saveMeta()
        }
        return packaged(name)
    }

    /** Where each file comes from now: "packaged" or the ETag of the download. */
    @Synchronized
    fun source(name: String): String {
        val files = meta.getJSONObject("files")
        return if (File(dir, name).isFile) files.optJSONObject(name)?.optString("etag", "?") ?: "?" else "packaged"
    }

    /** Asks the site for every updatable file (at most every 6 hours unless forced). Safe to call with no network. */
    @Synchronized
    fun refresh(force: Boolean = false): Map<String, Outcome> {
        val files = meta.getJSONObject("files")
        val out = LinkedHashMap<String, Outcome>()
        for (s in FILES) {
            val m = files.optJSONObject(s.name) ?: JSONObject()
            if (!force && now() - m.optLong("checked", 0) < EVERY_MS) { out[s.name] = Outcome.SKIPPED; continue }
            val local = File(dir, s.name)
            // the ETag we may send: of the downloaded copy, or of a site file that matched the packaged one
            val known = m.optString("etag").ifEmpty { null }?.takeIf { local.isFile || m.optBoolean("packaged") }
            val r = try { fetch.get(BASE + s.name, known) } catch (_: Exception) { null }
            val outcome = when {
                r == null -> Outcome.FAILED
                r.code == 304 && known != null -> Outcome.UNCHANGED
                r.code == 200 && r.body != null -> {
                    val text = r.body.toString(Charsets.UTF_8)
                    when {
                        text == runCatching { packaged(s.name) }.getOrNull() -> {
                            local.delete() // the site has what the app already carries: nothing to keep
                            m.put("packaged", true).put("etag", r.etag ?: "")
                            Outcome.UNCHANGED
                        }
                        // a file that cannot be written is a failed refresh, tried again next time
                        s.valid(text) -> if (runCatching { writeAtomic(local, r.body) }.isSuccess) {
                            m.put("packaged", false).put("etag", r.etag ?: "")
                            Outcome.UPDATED
                        } else Outcome.FAILED
                        else -> Outcome.REJECTED
                    }
                }
                else -> Outcome.FAILED
            }
            out[s.name] = outcome
            if (outcome != Outcome.FAILED) files.put(s.name, m.put("checked", now()))
        }
        saveMeta()
        return out
    }
}

/** HTTPS only, short timeouts, a size cap: a slow or odd answer is simply a failed refresh. */
class HttpFetcher : SiteData.Fetcher {
    override fun get(url: String, etag: String?): SiteData.Response {
        require(url.startsWith("https://"))
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8000
            c.readTimeout = 15000
            c.instanceFollowRedirects = false
            c.setRequestProperty("Accept", "application/json")
            if (etag != null) c.setRequestProperty("If-None-Match", etag)
            val code = c.responseCode
            if (code != 200) return SiteData.Response(code, c.getHeaderField("ETag"), null)
            if (c.contentLengthLong > SiteData.MAX_BYTES) return SiteData.Response(413, null, null)
            val body = c.inputStream.use { inp ->
                val buf = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(16384)
                while (true) {
                    val n = inp.read(chunk)
                    if (n < 0) break
                    buf.write(chunk, 0, n)
                    if (buf.size() > SiteData.MAX_BYTES) return SiteData.Response(413, null, null)
                }
                buf.toByteArray()
            }
            return SiteData.Response(200, c.getHeaderField("ETag"), body)
        } finally {
            c.disconnect()
        }
    }
}
