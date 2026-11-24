package io.github.pini236.skiapp.redesign

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32

/**
 * The meeting point's picture (Q06): a file with a place in its metadata is never shared (the architect's condition 6).
 * The check reads the PNG's chunks; the emulator's run checks the file the phone really writes ("redesign meet image file clean").
 */
class MeetShareImageTest {
    private fun chunk(type: String, body: ByteArray): ByteArray {
        val o = ByteArrayOutputStream()
        val n = body.size
        o.write(byteArrayOf((n ushr 24).toByte(), (n ushr 16).toByte(), (n ushr 8).toByte(), n.toByte()))
        val t = type.toByteArray(Charsets.US_ASCII); o.write(t); o.write(body)
        val c = CRC32().apply { update(t); update(body) }.value
        o.write(byteArrayOf((c ushr 24).toByte(), (c ushr 16).toByte(), (c ushr 8).toByte(), c.toByte()))
        return o.toByteArray()
    }
    private fun png(vararg chunks: ByteArray) = ByteArrayOutputStream().apply {
        write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        write(chunk("IHDR", ByteArray(13)))
        chunks.forEach { write(it) }
        write(chunk("IDAT", ByteArray(20)))
        write(chunk("IEND", ByteArray(0)))
    }.toByteArray()

    @Test fun aPlainPngHasNoPlace() = assertFalse(MeetShareImage.hasPlace(png()))
    @Test fun aSoftwareNoteIsNoPlace() = assertFalse(MeetShareImage.hasPlace(png(chunk("tEXt", "Software\u0000Gudi".toByteArray()))))
    @Test fun anExifChunkIsAPlace() = assertTrue(MeetShareImage.hasPlace(png(chunk("eXIf", ByteArray(30)))))
    @Test fun gpsInATextChunkIsAPlace() = assertTrue(MeetShareImage.hasPlace(png(chunk("iTXt", "XML:com.adobe.xmp\u0000\u0000\u0000\u0000\u0000<exif:GPSLatitude>42</exif:GPSLatitude>".toByteArray()))))
}
