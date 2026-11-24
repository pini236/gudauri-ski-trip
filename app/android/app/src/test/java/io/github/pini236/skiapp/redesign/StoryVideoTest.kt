package io.github.pini236.skiapp.redesign

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File

/** Round 4, board L11 (the architect's condition): no place in the video's metadata, and no off-piste stretch drawn. */
class StoryVideoTest {
    private fun box(type: String, body: ByteArray): ByteArray {
        val o = ByteArrayOutputStream(); val d = DataOutputStream(o)
        d.writeInt(8 + body.size); d.write(type.toByteArray(Charsets.ISO_8859_1)); d.write(body); return o.toByteArray()
    }
    private val xyz = byteArrayOf(0xA9.toByte(), 'x'.code.toByte(), 'y'.code.toByte(), 'z'.code.toByte())

    private fun file(vararg boxes: ByteArray): File = File.createTempFile("video", ".mp4").apply { writeBytes(boxes.fold(ByteArray(0)) { a, b -> a + b }); deleteOnExit() }

    @Test fun aFileWithoutAPlaceMayLeave() {
        val f = file(box("ftyp", "isom".toByteArray()), box("moov", box("mvhd", ByteArray(100)) + box("trak", ByteArray(64))), box("mdat", ByteArray(500)))
        assertFalse(Mp4Meta.hasPlace(f))
    }

    @Test fun aPlaceInTheMoviesUserDataIsFound() {
        val f = file(box("ftyp", "isom".toByteArray()), box("moov", box("udta", box(String(xyz, Charsets.ISO_8859_1), "+42.48+44.48/".toByteArray()))), box("mdat", ByteArray(10)))
        assertTrue(Mp4Meta.hasPlace(f))
        assertTrue(Mp4Meta.hasPlace(file(box("moov", box("udta", box("loci", ByteArray(20)))))))
    }

    @Test fun thePicturesOwnBytesAreNotReadAsAPlace() {
        assertFalse(Mp4Meta.hasPlace(file(box("ftyp", "isom".toByteArray()), box("mdat", "....loci....".toByteArray() + xyz))))
    }

    @Test fun anOffPisteStretchIsHidden() {
        val spans = listOf(longArrayOf(1_000, 2_000))
        assertTrue(StoryVideo.hidden(1_500, spans)); assertTrue(StoryVideo.hidden(2_000, spans))
        assertFalse(StoryVideo.hidden(2_001, spans)); assertFalse(StoryVideo.hidden(500, emptyList()))
    }

    /** The picture on the painted mountain goes from the map's metres back to latitude and longitude (point 6). */
    @Test fun theMapsMetresGoBackToTheSamePlace() {
        for ((lat, lon) in listOf(42.4826 to 44.4834, 42.51 to 44.495, 42.4711 to 44.4922)) {
            val y = io.github.pini236.skiapp.data.Geo.y(lat); val x = io.github.pini236.skiapp.data.Geo.x(lon)
            org.junit.Assert.assertEquals(lat, MapMetres.lat(y), 1e-5)
            org.junit.Assert.assertEquals(lon, MapMetres.lon(x), 1e-5)
        }
    }

    /** The emulator's crash (21 November's trip card): at the covering scale the picture's edge is a rounding hair inside
     *  the card, and the offset's range went empty. For every card size and any day, the placement never throws and
     *  the picture covers the card. */
    @Test fun thePlacementNeverThrowsAndAlwaysCovers() {
        val v = PeakData.parse(File("src/preview/assets/redesign/peak-poc.json").readText()).hero
        val days = listOf(emptyList(), listOf(floatArrayOf(100f, 200f, 300f, 400f)), listOf(floatArrayOf(5f, 5f)), listOf(floatArrayOf(0f, 0f, v.w, v.h)))
        for (w in listOf(1f, 100f, 333.3f, 359.7f, 391.5f, 1007f, 1080f)) for (h in listOf(1f, 120f, 300f, 787.5f, 1350f, 2400f))
            for (day in days) for (area in listOf(h, h - 120f, 1f)) {
                val p = OnMountain.place(v, day, w, h, minOf(w, h) * .08f, area)
                val k = p[0]; val ox = p[1]; val oy = p[2]
                assertTrue("$w x $h", ox <= 0f && oy <= 0f && ox + v.w * k >= w - 0.5f && oy + v.h * k >= h - 0.5f)
            }
    }
}
