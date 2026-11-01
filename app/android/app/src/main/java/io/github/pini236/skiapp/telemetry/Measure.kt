package io.github.pini236.skiapp.telemetry

/**
 * A phone whose clock is set to Europe keeps no stored visitor id (architect m-5, decision 37): a new random id for each
 * run of the app, in memory only. The same rule as the site (site/js/telemetry.js, europe()) and the iPhone (Measure.europe):
 * every Europe/ zone, five Atlantic islands and Cyprus.
 */
object Measure {
    private val atlantic = setOf("Atlantic/Azores", "Atlantic/Canary", "Atlantic/Faroe", "Atlantic/Madeira", "Atlantic/Reykjavik")
    private val cyprus = setOf("Asia/Nicosia", "Asia/Famagusta")

    fun europe(zone: String): Boolean = zone.startsWith("Europe/") || zone in atlantic || zone in cyprus
}
