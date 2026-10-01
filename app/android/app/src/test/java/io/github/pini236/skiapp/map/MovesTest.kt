package io.github.pini236.skiapp.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan2
import kotlin.math.hypot

/** The map gestures keep the ground under the fingers (docs/APP-NATIVE.md, emulator findings 1.10.2026). */
class MovesTest {
    private val w = 1080f; private val h = 2000f
    /** Sloping, bumpy snow: the camera's centre rises and falls with it, which is what broke the first gestures. */
    private val snow: (Float, Float) -> Float = { x, z -> 2300f + 0.18f * x - 0.12f * z + 120f * kotlin.math.sin(x / 600f) * kotlin.math.cos(z / 800f) }
    private val start = OrbitCamera.State(600f, snow(600f, -500f), -500f, 6000f, 0.35f, 0.62f)

    private fun under(s: OrbitCamera.State, x: Float, y: Float) = Lens(s, w, h).hit(x, y, snow) ?: throw AssertionError("no snow under $x,$y")
    private fun screenOf(s: OrbitCamera.State, g: FloatArray) = Lens(s, w, h).project(g[0], g[1], g[2]) ?: throw AssertionError("behind the camera")
    private fun onSnow(s: OrbitCamera.State) = assertEquals("the camera's centre sits on the snow", snow(s.tx, s.tz), s.ty, 0.5f)

    @Test fun projectAndHitAgree() {
        val lens = Lens(start, w, h)
        for ((x, y) in listOf(540f to 1000f, 200f to 1500f, 900f to 700f)) {
            val g = under(start, x, y)
            assertEquals(snow(g[0], g[2]), g[1], 1f)
            val p = lens.project(g[0], g[1], g[2])!!
            assertEquals(x, p[0], 0.5f); assertEquals(y, p[1], 0.5f)
        }
        val c = lens.project(start.tx, start.ty, start.tz)!!
        assertEquals(w / 2, c[0], 0.5f); assertEquals(h / 2, c[1], 0.5f)
    }

    @Test fun panKeepsTheSnowUnderTheFinger() {
        // a long drag in small steps, as the finger sends them
        val g = under(start, 540f, 1200f)
        var s = start
        for (i in 1..24) {
            val k0 = (i - 1) / 24f; val k1 = i / 24f
            s = Moves.pan(s, w, h, 540f - 216f * k0, 1200f - 300f * k0, 540f - 216f * k1, 1200f - 300f * k1, snow)
        }
        onSnow(s)
        val p = screenOf(s, g)
        assertEquals(324f, p[0], 3f); assertEquals(900f, p[1], 3f)
    }

    @Test fun zoomKeepsThePointBetweenTheFingers() {
        val g = under(start, 300f, 1400f)
        var s = start
        repeat(20) { s = Moves.zoom(s, w, h, 300f, 1400f, 1.07f, snow) }
        onSnow(s)
        val p = screenOf(s, g)
        assertEquals(300f, p[0], 3f); assertEquals(1400f, p[1], 3f)
    }

    @Test fun zoomStopsAtTheLimits() {
        assertEquals(Moves.MIN_DIST, Moves.zoom(start, w, h, 540f, 1000f, 1000f, snow).dist, 0.01f)
        assertEquals(Moves.MAX_DIST, Moves.zoom(start, w, h, 540f, 1000f, 0.001f, snow).dist, 0.01f)
    }

    @Test fun theMapTurnsWithTheFingers() {
        // two fingers turn clockwise by 20° around the middle of the screen
        val a0 = floatArrayOf(390f, 1000f); val b0 = floatArrayOf(690f, 1000f)
        val ga = under(start, a0[0], a0[1]); val gb = under(start, b0[0], b0[1]); val gm = under(start, 540f, 1000f)
        val turn = Math.toRadians(20.0).toFloat()
        val next = Moves.rotate(start, w, h, 540f, 1000f, turn, snow)
        onSnow(next)
        val pa = screenOf(next, ga); val pb = screenOf(next, gb)
        val ang = atan2(pb[1] - pa[1], pb[0] - pa[0]) - atan2(b0[1] - a0[1], b0[0] - a0[0])
        // clockwise on a y-down screen is a positive angle. Seen at a tilt, a turn of the ground looks smaller
        // across the screen (about sin(pitch) of it here), as on every phone map; the direction is what matters.
        // The spike turned the map against the fingers (found on the emulator, 1.10.2026).
        assertTrue("the ground turned ${Math.toDegrees(ang.toDouble())}°, not with the fingers", ang > turn * 0.45f && ang < turn * 1.6f)
        // and the snow between the fingers stayed put
        val mid = screenOf(next, gm)
        assertTrue(hypot(mid[0] - 540f, mid[1] - 1000f) < 3f)
    }

    @Test fun twoFingersUpTogetherTilt() {
        assertTrue(Moves.isTilt(2f, -40f, -3f, -38f, 4f, 8f))
        assertTrue(!Moves.isTilt(30f, -10f, -30f, 10f, 60f, 8f)) // a pinch is not a tilt
        assertTrue(Moves.tilt(start, h, -200f).pitch < start.pitch) // up: towards the horizon
    }
}

class FramingTest {
    private val w = 1080f; private val h = 2100f

    /** A run 1.8 km long going down to the south-west, with a bend: it must fit between the bars, top at the top. */
    @Test fun theWholeRunFitsAndItsTopIsUp() {
        val pts = ArrayList<Float>()
        for (i in 0..60) {
            val k = i / 60f
            pts += listOf(1000f - 900f * k + 200f * kotlin.math.sin(k * 6f), 3000f - 700f * k, -2000f + 1500f * k)
        }
        val path = pts.toFloatArray()
        val s = Framing.fit(path, w, h, { _, _ -> 2600f })
        val box = Framing.Box()
        val b = Framing.bounds(s, path, 1, w, h) ?: throw AssertionError("part of the run is behind the camera")
        assertTrue("left ${b[0]}", b[0] >= box.left * w - 1); assertTrue("right ${b[1]}", b[1] <= box.right * w + 1)
        assertTrue("top ${b[2]}", b[2] >= box.top * h - 1); assertTrue("bottom ${b[3]}", b[3] <= box.bottom * h + 1)
        // it fills the box in at least one direction (not a tiny run in the middle)
        assertTrue((b[1] - b[0]) > (box.right - box.left) * w * 0.85f || (b[3] - b[2]) > (box.bottom - box.top) * h * 0.85f)
        val lens = Lens(s, w, h)
        val top = lens.project(path[0], path[1], path[2])!!; val end = path.size - 3
        val bottom = lens.project(path[end], path[end + 1], path[end + 2])!!
        assertTrue("the top of the run should be higher on the screen", top[1] < bottom[1])
    }
}

class SkyTest {
    private fun at(iso: String) = java.time.Instant.parse(iso).toEpochMilli()
    private fun angle(a: FloatArray, b: FloatArray) = Math.toDegrees(Math.acos((a[0] * b[0] + a[1] * b[1] + a[2] * b[2]).toDouble().coerceIn(-1.0, 1.0)))

    @Test fun januaryDayAndNightInGudauri() {
        val t = Sky.sunTimes(at("2027-01-12T08:00:00Z"))
        assertTrue("sunrise ${t[0]}", t[0] in 8.0..8.8); assertTrue("sunset ${t[1]}", t[1] in 17.0..17.8)
        val noon = Sky.at(at("2027-01-12T08:30:00Z")) // 12:30 in Gudauri
        assertTrue(noon.sunUp && !noon.dark)
        assertTrue("a blue sky at noon", noon.skyTop[2] > noon.skyTop[0] + 0.3f)
        val night = Sky.at(at("2027-01-12T19:00:00Z")) // 23:00
        assertTrue(night.dark && !night.sunUp)
        assertTrue("a dark sky at night", night.skyTop.all { it < 0.1f })
        assertTrue("moonlight is dimmer than sunlight", night.color.sum() < noon.color.sum())
    }

    @Test fun theMoonIsOppositeTheSunInAnEclipseAndBesideItInAnother() {
        // total lunar eclipse, 14.3.2025 06:59 UTC: the moon is opposite the sun
        val lunar = Sky.at(at("2025-03-14T06:59:00Z"))
        assertEquals(180.0, angle(lunar.sunDir, lunar.moonDir), 3.0)
        // total solar eclipse, 8.4.2024 18:18 UTC: the moon is in front of the sun
        val solar = Sky.at(at("2024-04-08T18:18:00Z"))
        assertEquals(0.0, angle(solar.sunDir, solar.moonDir), 3.0)
    }
}
