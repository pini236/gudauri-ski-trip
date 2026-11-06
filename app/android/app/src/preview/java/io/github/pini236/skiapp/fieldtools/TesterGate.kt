package io.github.pini236.skiapp.fieldtools

import android.content.Context
import java.security.MessageDigest

/**
 * The field testers' gate (docs/ARCHITECTURE.md 31.10.2026, conditions ו and ז): the export is off until a tester types the
 * code Pini gave him, apart from the app. The code is not in the repository, only its hash. The consent is kept on this phone
 * only (when, and whether the motion windows were agreed to); withdrawing it, or turning the mode off, deletes it here.
 */
object TesterGate {
    private const val PREFS = "field_tester"
    // SHA-256 of "gudi-tester:" + the code without dashes, upper case
    private const val HASH = "bb7f606df28f6617e1b0898c1276006d9b6d007466a4346f91f539447b16c086"

    fun hash(code: String): String {
        val norm = code.filter { it.isLetterOrDigit() }.uppercase()
        return MessageDigest.getInstance("SHA-256").digest(("gudi-tester:$norm").toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun p(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun unlocked(c: Context) = p(c).getBoolean("on", false)
    fun unlock(c: Context, code: String): Boolean = (hash(code) == HASH).also { if (it) p(c).edit().putBoolean("on", true).apply() }
    fun lock(c: Context) = p(c).edit().clear().apply()

    fun consented(c: Context) = p(c).getLong("consent_at", 0L) > 0L
    fun impactsAgreed(c: Context) = consented(c) && p(c).getBoolean("impacts", false)
    fun consent(c: Context, impacts: Boolean) = p(c).edit().putLong("consent_at", System.currentTimeMillis()).putBoolean("impacts", impacts).apply()
    fun withdraw(c: Context) = p(c).edit().remove("consent_at").remove("impacts").apply()
}
