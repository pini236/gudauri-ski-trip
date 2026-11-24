package io.github.pini236.skiapp.redesign

import androidx.compose.ui.geometry.Offset
import io.github.pini236.skiapp.home.DayNight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The painted mountain's geometry and light (the redesign POC, decision 85), on the designer's handoff itself
 * (assets/redesign/peak-poc.json): the projection against its check points, the touch under zoom and drag (the bug of
 * 0.1.573: after a pinch the touch was tested in the projection of the first frame), and the picture by the sun.
 */
class PeakGeoTest {
    private val d = PeakData.parse(File("src/redesign/assets/redesign/peak-poc.json").readText())
    private val runs = d.runs.keys
    // a phone in portrait (Pixel 6 in pixels) and a small one
    private val screens = listOf(1080f to 2400f, 720f to 1520f)

    @Test fun projectionMeetsTheHandoffsCheckPoints() {
        assertTrue(d.check.size >= 10)
        for (c in d.check) for ((v, want) in listOf(d.hero to c.hero, d.zoom to c.zoom)) {
            val p = v.proj!!.project(c.lat, c.lon, c.z)
            assertNotNull(c.what, p)
            assertEquals("${c.what} x", want.x, p!!.x, 0.1f)
            assertEquals("${c.what} y", want.y, p.y, 0.1f)
        }
    }

    @Test fun theZoomIsTheHeroEnlarged() {
        val (k, tx, ty) = d.zoom.fromHero!!
        // below y 50 of the close-up (above it the far mountain straightens up, the handoff says)
        for (c in d.check.filter { it.zoom.y >= 50f }) {
            assertEquals(c.what, c.zoom.x, c.hero.x * k + tx, 0.25f)
            assertEquals(c.what, c.zoom.y, c.hero.y * k + ty, 0.25f)
        }
    }

    /** A point of [key]'s line in the hero picture where that run is the only one near (so a touch there must pick it). */
    private fun own(key: String): Offset {
        val pl = Place(1f, 0f, 0f)
        return PeakGeo.spot(d.hero, runs, key, pl, d.hero.w, d.hero.h, 22f) ?: error("no clear point on $key")
    }

    @Test fun aTouchPicksTheRunUnderTheFingerAtEveryZoomAndPan() {
        val keys = d.hero.sel.keys.filter { it in runs }
        assertTrue(keys.size >= 10)
        for ((w, h) in screens) for (key in keys) {
            val p = runCatching { own(key) }.getOrNull() ?: continue
            for (z in listOf(1f, 1.4f, 2.2f, 2.6f)) for (c in listOf(p, Offset(d.hero.w / 2, d.hero.h / 2), Offset(40f, 60f), Offset(d.hero.w - 30f, d.hero.h - 40f))) {
                val pl = PeakGeo.free(w, h, d.hero, z, c)
                val s = pl.on(p)
                if (s.x !in 0f..w || s.y !in 0f..h) continue
                assertEquals("$key at zoom $z around $c on $w x $h", key, PeakGeo.hit(d.hero, runs, pl, s, 22f * w / 390f))
            }
        }
    }

    @Test fun theTouchOfTheBugIsCaught() {
        // the old projection (the first frame, zoom 1) for a touch made after a pinch picks something else: the test above
        // is what would have caught 0.1.573
        val (w, h) = screens[0]
        var differs = 0
        for (key in d.hero.sel.keys.filter { it in runs }) {
            val p = runCatching { own(key) }.getOrNull() ?: continue
            val now = PeakGeo.free(w, h, d.hero, 2.2f, p)
            val first = PeakGeo.free(w, h, d.hero, 1f, Offset(d.hero.w / 2, d.hero.h / 2))
            if (PeakGeo.hit(d.hero, runs, first, now.on(p), 22f * w / 390f) != key) differs++
        }
        assertTrue(differs > 0)
    }

    @Test fun thePictureAlwaysCoversTheScreen() {
        for ((w, h) in screens) for (z in listOf(1f, 1.3f, 2.2f, 2.6f)) for (c in listOf(Offset(-500f, -500f), Offset(0f, 0f), Offset(195f, 422f), Offset(2000f, 3000f))) {
            val pl = PeakGeo.free(w, h, d.hero, z, c)
            val tl = pl.on(Offset(0f, 0f)); val br = pl.on(Offset(d.hero.w, d.hero.h))
            assertTrue("left edge in at zoom $z, $c", tl.x <= 0.5f); assertTrue("top edge in at zoom $z, $c", tl.y <= 0.5f)
            assertTrue("right edge in at zoom $z, $c", br.x >= w - 0.5f); assertTrue("bottom edge in at zoom $z, $c", br.y >= h - 0.5f)
        }
    }

    @Test fun thePinchKeepsTheMiddle() {
        // the centre a pinch writes back (PeakGeo.centre) gives the same place again: no jump on the next frame
        val (w, h) = screens[0]
        for (z in listOf(1f, 1.7f, 2.6f)) for (c in listOf(Offset(120f, 300f), Offset(300f, 700f))) {
            val pl = PeakGeo.free(w, h, d.hero, z, c)
            val again = PeakGeo.free(w, h, d.hero, z, PeakGeo.centre(w, h, pl))
            assertEquals(pl.x, again.x, 0.01f); assertEquals(pl.y, again.y, 0.01f)
        }
    }

    @Test fun backFromACloseUpIsTheWholeMountain() {
        // clear() sets zoom 1 around the middle: the fit, centred, whatever the view was
        val (w, h) = screens[0]
        val pl = PeakGeo.free(w, h, d.hero, 1f, Offset(d.hero.w / 2, d.hero.h / 2))
        val k = PeakGeo.fit(w, h, d.hero)
        assertEquals(k, pl.k, 1e-4f)
        assertEquals((w - d.hero.w * k) / 2, pl.x, 0.01f); assertEquals((h - d.hero.h * k) / 2, pl.y, 0.01f)
    }

    @Test fun thePictureFollowsTheSun() {
        val by = d.lights.associateBy { it.name }
        // each day picture at its own hour of 12 January (the hours the designer drew them for)
        for (l in d.lights.filter { !it.night }) assertEquals(l.name, PeakLight.pick(d.lights, PeakLight.janAt(l.hour), DayNight.Mode.AUTO).name)
        assertEquals("blue", PeakLight.pick(d.lights, PeakLight.janAt(by.getValue("blue").hour), DayNight.Mode.AUTO).name)
        assertEquals("night", PeakLight.pick(d.lights, PeakLight.janAt(22f), DayNight.Mode.AUTO).name)
        assertEquals("night", PeakLight.pick(d.lights, PeakLight.janAt(5f), DayNight.Mode.AUTO).name)
        // the switch: day is the morning picture, night is night, whatever the hour
        assertEquals("morning", PeakLight.pick(d.lights, PeakLight.janAt(22f), DayNight.Mode.DAY).name)
        assertEquals("night", PeakLight.pick(d.lights, PeakLight.janAt(13f), DayNight.Mode.NIGHT).name)
    }

    @Test fun theClock() {
        assertEquals("13:00", PeakLight.clock(13f))
        assertEquals("17:21", PeakLight.clock(17.35f))
        assertEquals(13f, PeakLight.localHour(PeakLight.janAt(13f)), 1e-3f)
    }
}
