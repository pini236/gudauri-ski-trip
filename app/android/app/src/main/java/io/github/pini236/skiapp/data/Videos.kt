package io.github.pini236.skiapp.data

import org.json.JSONArray
import java.net.URI

/**
 * A video of a run (site/data/videos-seed.json, made by tools/build-videos.py): the run's key, the link, and what the
 * run's panel shows under it. Only videos whose title or description names the run with its number are in the file.
 */
class Video(val piste: String, val url: String, val title: String, val channel: String, val length: String, val by: String, val at: Long) {
    /** The YouTube id of a youtube.com or youtu.be link (the site's ytId), for the thumbnail; null for any other link. */
    val youtubeId: String? get() = runCatching {
        val u = URI(url)
        val host = u.host ?: return@runCatching null
        val id = when {
            host == "youtu.be" -> u.path.removePrefix("/")
            host == "youtube.com" || host.endsWith(".youtube.com") -> u.rawQuery?.split('&')?.firstOrNull { it.startsWith("v=") }?.removePrefix("v=")
            else -> null
        }
        id?.takeIf { ID.matches(it) }
    }.getOrNull()

    companion object {
        private val ID = Regex("[\\w-]{11}")

        fun parse(json: String): List<Video> {
            val a = JSONArray(json)
            return (0 until a.length()).map { i ->
                val v = a.getJSONObject(i)
                fun s(k: String) = if (v.isNull(k)) "" else v.optString(k, "")
                Video(s("piste"), s("url"), s("title"), s("channel"), s("length"), s("by"), v.optLong("at", 0))
            }.filter { it.url.startsWith("http") }
        }

        /** A run's videos, the newest first (the site's vidList). */
        fun of(all: List<Video>, key: String) = all.filter { it.piste == key }.sortedByDescending { it.at }
    }
}
