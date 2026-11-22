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

    private fun file(vararg boxes: ByteArray): File = File.createTempFile("v", ".mp4").apply { writeBytes(boxes.fold(ByteArray(0)) { a, b -> a + b }); deleteOnExit() }

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
}
