package com.pini.gudauri.data

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

enum class AppPage { HOME, MAP, MEET }
data class AppLink(val page: AppPage, val runKey: String? = null, val meeting: MeetingChoice? = null)

/** The website stays unchanged; these links are unverified and can also be imported explicitly. */
object AppLinks {
    const val site = "https://gudauri-ski-trip.vercel.app/"
    fun run(key: String): String = site + "#map/run/" + URLEncoder.encode(key, "UTF-8").replace("+", "%20")
    fun meeting(choice: MeetingChoice): String = site + "#meet/${choice.stationId}/${choice.time.format(DateTimeFormatter.ofPattern("HHmm"))}/${choice.day.format(DateTimeFormatter.BASIC_ISO_DATE)}"
    fun parse(value: String): AppLink? = try {
        val text = value.trim()
        if (text.length > 4096) null else {
            val uri = URI(text)
            val validSite = uri.scheme == "https" && uri.host == "gudauri-ski-trip.vercel.app" &&
                uri.userInfo == null && uri.port == -1 && uri.path in listOf("", "/")
            if (!validSite) null else when (val fragment = uri.rawFragment) {
                "home", null, "" -> AppLink(AppPage.HOME)
                "map" -> AppLink(AppPage.MAP)
                "meet" -> AppLink(AppPage.MEET)
                else -> if (fragment.startsWith("map/run/")) {
                    val key = URLDecoder.decode(fragment.removePrefix("map/run/").replace("+", "%2B"), "UTF-8")
                    if (key.isNotBlank() && key.length <= 200 && key.none { it.isISOControl() || it == '/' }) AppLink(AppPage.MAP, key) else null
                } else if (fragment.matches(Regex("meet/[0-9]{1,19}[bt]/[0-9]{4}/[0-9]{8}"))) {
                    val parts = fragment.split('/')
                    val time = LocalTime.of(parts[2].take(2).toInt(), parts[2].takeLast(2).toInt())
                    val day = LocalDate.parse(parts[3], DateTimeFormatter.BASIC_ISO_DATE)
                    if (day.year !in 1..9999) null else AppLink(AppPage.MEET, meeting=MeetingChoice(parts[1],day,time))
                } else null
            }
        }
    } catch (_: Exception) { null }
}
