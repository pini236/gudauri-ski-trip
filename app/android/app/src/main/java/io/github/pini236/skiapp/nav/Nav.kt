package io.github.pini236.skiapp.nav

import androidx.compose.runtime.mutableStateListOf
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Where the user is, as the site says it (site/js/app.js route()): `home`, `map`, `map/run/<key>`, `meet`,
 * `meet/<station>/<HHMM>/<YYYYMMDD>`, `games`, `games/<name>`, `about`, and the app's own `trip` (the trip form). The same words make the saved state
 * (the system may close the app in the background, and it must come back to the same place) and read the site's
 * shared links (a run, a meeting point), so a link from WhatsApp opens the same screen in the app.
 */
sealed interface Route {
    val path: String

    data object Home : Route { override val path = "home" }
    data class Map(val run: String? = null) : Route { override val path = if (run == null) "map" else "map/run/" + enc(run) }
    data class Meet(val station: String? = null, val time: String? = null, val day: String? = null) : Route {
        override val path = if (station == null) "meet" else listOfNotNull("meet", station, time, day).joinToString("/")
    }
    data object Games : Route { override val path = "games" }
    data class Game(val name: String) : Route { override val path = "games/$name" }
    data object Ticket : Route { override val path = "ticket" }
    data object About : Route { override val path = "about" }
    /** The form for "your trip" (round 10, H2). App only: the site has no such page yet. */
    data object Trip : Route { override val path = "trip" }

    companion object {
        private val NAME = Regex("[a-z0-9-]{1,40}")
        private val STATION = Regex("[A-Za-z0-9_.-]{1,60}")

        /** encodeURIComponent, as the site writes run keys into links */
        fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
        private fun dec(s: String): String? = runCatching { URLDecoder.decode(s.replace("+", "%2B"), "UTF-8") }.getOrNull()

        /** A path or a hash (with or without '#'). Unknown or odd input is null, never a crash. */
        fun parse(raw: String?): Route? {
            val p = raw?.trim()?.removePrefix("#")?.trimEnd('/') ?: return null
            val parts = p.split('/')
            return when (parts[0]) {
                "", "home" -> if (parts.size == 1) Home else null
                "map" -> when {
                    parts.size == 1 -> Map()
                    parts.size == 3 && parts[1] == "run" -> dec(parts[2])?.takeIf { it.isNotBlank() && it.length <= 80 }?.let { Map(it) }
                    else -> null
                }
                "meet" -> when {
                    parts.size == 1 -> Meet()
                    parts.size == 4 && STATION.matches(parts[1]) && Regex("\\d{4}").matches(parts[2]) && Regex("\\d{8}").matches(parts[3]) -> Meet(parts[1], parts[2], parts[3])
                    else -> null
                }
                "games" -> when {
                    parts.size == 1 -> Games
                    parts.size == 2 && NAME.matches(parts[1]) -> Game(parts[1])
                    else -> null
                }
                "ticket" -> if (parts.size == 1) Ticket else null
                "about" -> if (parts.size == 1) About else null
                "trip" -> if (parts.size == 1) Trip else null
                else -> null
            }
        }

        /** A link to the site (https://gudauri-ski-trip.vercel.app/#map/run/Tatra%202, …/games/descent/). */
        fun fromSiteLink(url: String): Route? {
            val u = runCatching { URI(url) }.getOrNull() ?: return null
            if (u.scheme != "https" || u.host != SITE_HOST) return null
            val path = u.rawPath.orEmpty().trim('/')
            return when {
                path.startsWith("games/") -> parse(path.removeSuffix("/index.html"))
                path.isEmpty() || path == "index.html" -> parse(u.rawFragment ?: "home")
                else -> null
            }
        }

        const val SITE_HOST = "gudauri-ski-trip.vercel.app"
    }
}

/**
 * The back stack. Top-level places (the tabs) replace each other on top of the start place; inner places stack.
 * System back pops; at the start place it leaves the app. Saved and restored as a list of paths.
 */
class Nav(start: Route, saved: List<String>? = null) {
    private val start = start
    private val stack = mutableStateListOf<Route>().apply {
        val restored = saved?.mapNotNull { Route.parse(it) }.orEmpty()
        addAll(if (restored.isNotEmpty() && restored[0]::class == start::class) restored else listOf(start))
    }

    val top: Route get() = stack.last()
    val canBack: Boolean get() = stack.size > 1

    /** A top-level place: the stack becomes [start, place] (or just [start]). */
    fun switchTo(r: Route) {
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
        if (r::class != start::class) stack.add(r) else stack[0] = r
    }

    /** An inner place, on top of where the user is. */
    fun push(r: Route) { if (r != top) stack.add(r) }

    /** The same place with new details (the chosen run), without a new back step. */
    fun replaceTop(r: Route) { stack[stack.lastIndex] = r }

    /** Back to the start place, keeping its details (the chosen run). */
    fun toStart() { while (stack.size > 1) stack.removeAt(stack.lastIndex) }

    /** The deepest place of this kind in the stack (the map under a game keeps its chosen run). */
    inline fun <reified T : Route> find(): T? = routes.lastOrNull { it is T } as T?

    val routes: List<Route> get() = stack

    fun back(): Boolean = if (canBack) { stack.removeAt(stack.lastIndex); true } else false

    fun save(): ArrayList<String> = ArrayList(stack.map { it.path })
}
