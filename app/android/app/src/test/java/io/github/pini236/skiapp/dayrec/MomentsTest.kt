package io.github.pini236.skiapp.dayrec

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * The moments engine against the reference tool's test values (tools/fixtures/dayrec-moments.json): the engine is run on the
 * expected final result of each case (not on this app's own analysis, so the engine is tested alone), with the history given,
 * and the output must be the same JSON, number for number (README, "Running the test values in an app", step 4). No parameter
 * is tuned here: they are the file's `moments` section.
 */
class MomentsTest {
    private val dir = File(System.getProperty("site.data") ?: "../../../site/data")
    private val root = dir.parentFile.parentFile
    private val fx = JSONObject(File(root, "tools/fixtures/dayrec-moments.json").readText())
    private val cases = JSONObject(File(root, "tools/fixtures/dayrec-cases.json").readText())
    private val M = JSONObject(File(root, "tools/fixtures/dayrec-params.json").readText()).getJSONObject("moments")
    private val runs by lazy { Moments.runsOf(JSONObject(File(dir, "runs-and-lifts.json").readText())) }

    private fun finalOf(name: String): JSONObject {
        val a = cases.getJSONArray("cases")
        for (i in 0 until a.length()) if (a.getJSONObject(i).getString("name") == name) return a.getJSONObject(i).getJSONObject("expected").getJSONObject("final")
        error("no case $name")
    }

    /** Equal as JSON: numbers by value (2 and 2.0 are the same), objects by key, arrays by order. */
    private fun same(a: Any?, b: Any?, path: String, errs: MutableList<String>) {
        val an = a == null || a == JSONObject.NULL; val bn = b == null || b == JSONObject.NULL
        when {
            an || bn -> if (an != bn) errs += "$path: $a != $b"
            a is JSONObject && b is JSONObject -> {
                val ka = a.keys().asSequence().toSet(); val kb = b.keys().asSequence().toSet()
                if (ka != kb) errs += "$path: keys $ka != $kb"
                for (k in ka intersect kb) same(a.get(k), b.get(k), "$path.$k", errs)
            }
            a is JSONArray && b is JSONArray -> {
                if (a.length() != b.length()) errs += "$path: length ${a.length()} != ${b.length()}"
                else for (i in 0 until a.length()) same(a.get(i), b.get(i), "$path[$i]", errs)
            }
            a is Number && b is Number -> if (abs(a.toDouble() - b.toDouble()) > 1e-9) errs += "$path: $a != $b"
            else -> if (a != b) errs += "$path: $a != $b"
        }
    }

    private fun check(name: String, got: JSONObject, exp: JSONObject, failures: MutableList<String>) {
        val errs = ArrayList<String>()
        same(got, exp, "", errs)
        if (errs.isNotEmpty()) failures += "$name: " + errs.take(4).joinToString("; ")
    }

    @Test fun theValuesAreOfTheVersionOfTheEngine() {
        assertEquals(fx.getInt("moments"), M.getInt("version"))
    }

    @Test fun everyCaseGivesTheSameMoments() {
        val failures = ArrayList<String>()
        val list = fx.getJSONArray("cases")
        for (i in 0 until list.length()) {
            val c = list.getJSONObject(i)
            check(c.getString("name"), Moments.moments(finalOf(c.getString("final")), c.getJSONObject("history"), runs, M), c.getJSONObject("expected"), failures)
        }
        assertTrue("${failures.size} of ${list.length()} cases differ:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun theDaysInARowGiveTheSameMomentsAndHistory() {
        val failures = ArrayList<String>()
        val chain = fx.getJSONArray("chain")
        var prev: JSONObject? = null
        for (i in 0 until chain.length()) {
            val c = chain.getJSONObject(i)
            val got = Moments.moments(finalOf(c.getString("final")), c.getJSONObject("history"), runs, M)
            check("chain[$i] ${c.getString("final")}", got, c.getJSONObject("expected"), failures)
            // each day starts from the history the one before it returned
            prev?.let { check("chain[$i] history", c.getJSONObject("history"), it, failures) }
            prev = got.getJSONObject("history")
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun theCardOnTheLiftIsTheSame() {
        val failures = ArrayList<String>()
        val list = fx.getJSONArray("lift_cards")
        for (i in 0 until list.length()) {
            val c = list.getJSONObject(i)
            val got = Moments.liftCard(c.getJSONObject("final"))
            val exp = c.opt("expected")
            if (exp == null || exp == JSONObject.NULL) { if (got != null) failures += "${c.getString("name")}: expected no card, got $got" }
            else if (got == null) failures += "${c.getString("name")}: expected a card, got none"
            else check(c.getString("name"), got, exp as JSONObject, failures)
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun noCardWhenTheLastSegmentIsNotALift() {
        assertNull(Moments.liftCard(JSONObject().put("segments", JSONArray())))
        assertNull(Moments.liftCard(finalOf("lift_basic").also { it.put("segments", JSONArray(listOf(JSONObject().put("type", "stop")))) }))
    }

    @Test fun theSeedIsTheSameCrcAsTheReference() {
        // crc32("fastest|1799645944000") & 0x7fffffff, as in the chain's first day
        assertEquals(880130906L, Moments.seed("fastest", 1799645944000L))
        assertEquals(482507523L, Moments.seed("longest_descent", 1799645870000L))
    }

    @Test fun theHistoryHoldsNoPosition() {
        // only counts and times, by the fixed keys: no "at", "lat" or "lon" anywhere in it
        val c = fx.getJSONArray("chain")
        for (i in 0 until c.length()) {
            val h = c.getJSONObject(i).getJSONObject("expected").getJSONObject("history")
            assertEquals(setOf("v", "days", "runs", "colors", "best"), h.keys().asSequence().toSet())
            for (k in listOf("runs", "colors", "best")) for (x in h.getJSONObject(k).keys()) assertTrue(x, h.getJSONObject(k).get(x) is Number)
        }
    }
}
