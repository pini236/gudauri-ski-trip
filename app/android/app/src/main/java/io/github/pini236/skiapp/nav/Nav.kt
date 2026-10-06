package io.github.pini236.skiapp.nav

import androidx.compose.runtime.mutableStateListOf
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Where the user is, as the site says it (site/js/app.js route()): `home`, `map`, `map/run/<key>`, `meet`,
 * `meet/<station>/<HHMM>/<YYYYMMDD>`, `games`, `games/<name>`, `about`, the invite link `j/<code or token>`, and the
 * app's own `trip`, `group`, `group/<id>/<tab>`, `group/new`, `group/<id>/invite`, `join`, `j/<code>/reclaim`,
 * `account` and `about/licenses[/<name>]`. The same words make the saved state
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
    data object About : Route { override val path = "about" }
    /** The open-source licenses (A-24): the list, and one license's full text, from the end of the credits. */
    data object Licenses : Route { override val path = "about/licenses" }
    data class License(val name: String) : Route { override val path = "about/licenses/$name" }
    /** The app's own places, not on the site: the trip form (H2) and the group (13.5). */
    data object Trip : Route { override val path = "trip" }
    /** The group sign: without an id it decides (sign in and join, or my group); with one, that group on a tab. */
    data class Group(val id: String? = null, val tab: String? = null) : Route {
        override val path = if (id == null) "group" else listOfNotNull("group", id, tab).joinToString("/")
    }
    data object GroupNew : Route { override val path = "group/new" }
    data class GroupInvite(val id: String) : Route { override val path = "group/$id/invite" }
    /** An admin fills in a new flight for a member (set_member_trip): the trip form, for them. */
    data class TripFor(val group: String, val user: String) : Route { override val path = "group/$group/trip/$user" }
    /** An invite link (the site's /j/<token>) or a typed code (Q3); "I'm already in the group" (Q5). */
    data class Join(val code: String) : Route { override val path = "j/$code" }
    data class Reclaim(val code: String) : Route { override val path = "j/$code/reclaim" }
    /** Typing the code (Q4). */
    data object JoinCode : Route { override val path = "join" }
    data object Account : Route { override val path = "account" }

    companion object {
        private val NAME = Regex("[a-z0-9-]{1,40}")
        private val STATION = Regex("[A-Za-z0-9_.-]{1,60}")
        private val ID = Regex("[A-Za-z0-9-]{1,60}")
        private val CODE = Regex("[A-Za-z0-9_-]{6,80}")
        private val TABS = setOf("flights", "meetups", "scores", "members")

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
                "about" -> when {
                    parts.size == 1 -> About
                    parts.size == 2 && parts[1] == "licenses" -> Licenses
                    parts.size == 3 && parts[1] == "licenses" && NAME.matches(parts[2]) -> License(parts[2])
                    else -> null
                }
                "trip" -> if (parts.size == 1) Trip else null
                "group" -> when {
                    parts.size == 1 -> Group()
                    parts.size == 2 && parts[1] == "new" -> GroupNew
                    parts.size == 2 && ID.matches(parts[1]) -> Group(parts[1])
                    parts.size == 3 && ID.matches(parts[1]) && parts[2] == "invite" -> GroupInvite(parts[1])
                    parts.size == 3 && ID.matches(parts[1]) && parts[2] in TABS -> Group(parts[1], parts[2])
                    parts.size == 4 && ID.matches(parts[1]) && parts[2] == "trip" && ID.matches(parts[3]) -> TripFor(parts[1], parts[3])
                    else -> null
                }
                "j" -> when {
                    parts.size == 2 && CODE.matches(parts[1]) -> Join(parts[1])
                    parts.size == 3 && CODE.matches(parts[1]) && parts[2] == "reclaim" -> Reclaim(parts[1])
                    else -> null
                }
                "join" -> when {
                    parts.size == 1 -> JoinCode
                    // the site's #join/<code>
                    parts.size == 2 && CODE.matches(parts[1]) -> Join(parts[1])
                    else -> null
                }
                "account" -> if (parts.size == 1) Account else null
                else -> null
            }
        }

        /** A link to the site (https://gudauri-ski-trip.vercel.app/#map/run/Tatra%202, …/games/descent/, …/j/<token>). */
        fun fromSiteLink(url: String): Route? {
            val u = runCatching { URI(url) }.getOrNull() ?: return null
            if (u.scheme != "https" || u.host != SITE_HOST) return null
            val path = u.rawPath.orEmpty().trim('/')
            return when {
                path.startsWith("games/") -> parse(path.removeSuffix("/index.html"))
                path.startsWith("j/") -> parse(path).takeIf { it is Join }
                // the site's /join/<code> (a code typed into a link): the same invite as /j/<code>
                path.startsWith("join/") -> parse("j/" + path.removePrefix("join/")).takeIf { it is Join }
                path.isEmpty() || path == "index.html" -> parse(u.rawFragment ?: "home")
                else -> null
            }
        }

        const val SITE_HOST = "gudauri-ski-trip.vercel.app"

        /**
         * What a link the app shares carries (a run, a meeting point, an invite; docs/GROWTH.md): it came from the
         * app (utm_source), shared (utm_medium), so its opening counts as app_open with source share_link.
         */
        const val SHARED = "utm_source=app&utm_medium=share"
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
