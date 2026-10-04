package io.github.pini236.skiapp.fx

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * The games' little sounds made on the phone, as the site makes them with Web Audio (no files): the crunch of packed
 * snow, higher for bigger merges, with a soft tone over the big ones, and a swish. Off with the settings' sound switch.
 */
class Synth(private val context: Context) {
    private val rate = 22050
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "synth").apply { isDaemon = true } }

    private fun play(samples: FloatArray, volume: Float) {
        if (!FxPrefs.sound(context)) return
        worker.execute {
            runCatching {
                val pcm = ShortArray(samples.size) { (samples[it].coerceIn(-1f, 1f) * volume * Short.MAX_VALUE).toInt().toShort() }
                val t = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.size * 2).build()
                t.write(pcm, 0, pcm.size)
                t.play()
                Thread.sleep(pcm.size * 1000L / rate + 60)
                t.release()
            }
        }
    }

    /** Grainy noise through a band around [centre] Hz (a two-pole resonator, the site's bandpass). */
    private fun band(n: Int, centre: Double, q: Double, env: (Int) -> Double): FloatArray {
        val w = 2 * PI * centre / rate; val r = exp(-w / (2 * q)).coerceIn(0.0, 0.999)
        val a1 = 2 * r * kotlin.math.cos(w); val a2 = -r * r
        var y1 = 0.0; var y2 = 0.0
        return FloatArray(n) { i ->
            val x = (Random.nextDouble() * 2 - 1) * env(i) * (if (Random.nextDouble() < .3) 1.0 else .35)
            val y = (1 - r) * x + a1 * y1 + a2 * y2; y2 = y1; y1 = y; (y * 3).toFloat()
        }
    }

    /** A merge: [lv] the new ball's level (0 a flake .. 11 the king). */
    fun crunch(lv: Int, volume: Float = .25f) {
        val n = (rate * (.09 + lv * .01)).toInt()
        val s = band(n, 700.0 + lv * 260, 1.4) { i -> (1 - i.toDouble() / n).pow(2) }
        if (lv >= 4) {
            val f = 330 * 2.0.pow((lv - 4) / 6.0); val m = (rate * .35).toInt()
            val out = FloatArray(maxOf(n, m))
            for (i in out.indices) {
                val tone = if (i < m) sin(2 * PI * f * i / rate) * .32 * exp(-i.toDouble() / m * 4.6) else 0.0
                out[i] = (if (i < n) s[i] else 0f) + tone.toFloat()
            }
            play(out, volume * 1.6f)
        } else play(s, volume * 1.6f)
    }

    /** A slide. */
    fun swish() {
        val n = (rate * .07).toInt()
        var prev = 0.0
        play(FloatArray(n) { i ->
            val x = (Random.nextDouble() * 2 - 1) * sin(PI * i / n)
            val y = x - prev; prev = x; y.toFloat() // high-pass: only the hiss
        }, .3f)
    }
}
