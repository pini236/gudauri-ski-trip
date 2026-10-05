package io.github.pini236.skiapp.fx

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

/**
 * The games' little sounds made on the phone, as the site makes them with Web Audio (no files): grains of filtered noise
 * (the snow, the ice), tones that slide (a ping, a squeak), the crunch of packed snow and a swish. Everything goes into
 * one mixer (one stream to the speaker, stopped after two quiet seconds), so many grains a second cost little. Off with
 * the settings' sound switch.
 */
class Synth(private val context: Context) {
    enum class Filter { LOW, HIGH, BAND }

    private fun on() = FxPrefs.sound(context)

    /**
     * The site's grain(): noise of [dur] seconds through a [type] filter at [freq] Hz (Q [q]), falling away squared, with a
     * short rise; at [at] seconds from now.
     */
    fun grain(at: Double, dur: Double, vol: Float, type: Filter, freq: Double, q: Double) {
        if (!on()) return
        val n = maxOf(64, (RATE * dur).toInt())
        val f = Biquad(type, freq, q)
        val s = FloatArray(n) { i ->
            val e = (1 - i.toDouble() / n).pow(2) * (if (i < n * .05) i / (n * .05) else 1.0)
            f.next((Random.nextDouble() * 2 - 1) * e).toFloat()
        }
        Mixer.play(s, vol, (at * RATE).toInt())
    }

    /**
     * A tone sliding from [f0] to [f1] Hz over [dur] seconds (exponential, as Web Audio's ramp), its loudness falling the
     * same way from [vol]; a sine, a triangle ([tri], the games' tone()), a square ([square]), or a sawtooth through a band at [band] Hz
     * (the squeak of wet snow).
     */
    fun tone(at: Double, f0: Double, f1: Double, dur: Double, vol: Float, saw: Boolean = false, band: Double = 0.0, q: Double = 1.0, tri: Boolean = false, square: Boolean = false) {
        if (!on()) return
        val n = (RATE * dur).toInt()
        val f = if (band > 0) Biquad(Filter.BAND, band, q) else null
        var ph = 0.0
        val s = FloatArray(n) { i ->
            val k = i.toDouble() / n
            val fr = f0 * (f1 / f0).pow(k)
            ph += fr / RATE
            val w = if (saw) 2 * (ph - kotlin.math.floor(ph + .5)) else if (tri) 4 * kotlin.math.abs(ph % 1.0 - .5) - 1 else if (square) (if (ph % 1.0 < .5) 1.0 else -1.0) else sin(2 * PI * ph)
            val v = w * 0.001.pow(k) // to a thousandth of [vol] by the end
            (f?.next(v) ?: v).toFloat()
        }
        Mixer.play(s, vol, (at * RATE).toInt())
    }

    /** A merge: [lv] the new ball's level (0 a flake .. 11 the king). */
    fun crunch(lv: Int, volume: Float = .25f) {
        if (!on()) return
        val n = (RATE * (.09 + lv * .01)).toInt()
        val b = Biquad(Filter.BAND, 700.0 + lv * 260, 1.4)
        val s = FloatArray(n) { i ->
            val x = (Random.nextDouble() * 2 - 1) * (1 - i.toDouble() / n).pow(2) * (if (Random.nextDouble() < .3) 1.0 else .35)
            (b.next(x) * 3).toFloat()
        }
        Mixer.play(s, volume * 1.6f)
        if (lv >= 4) tone(0.0, 330 * 2.0.pow((lv - 4) / 6.0), 330 * 2.0.pow((lv - 4) / 6.0), .35, volume * .5f)
    }

    /** A slide. */
    fun swish() {
        if (!on()) return
        val n = (RATE * .07).toInt()
        var prev = 0.0
        Mixer.play(FloatArray(n) { i ->
            val x = (Random.nextDouble() * 2 - 1) * sin(PI * i / n)
            val y = x - prev; prev = x; y.toFloat() // high-pass: only the hiss
        }, .3f)
    }

    /**
     * The site's ding(): a triangle (or a square) at [f] Hz for [dur] seconds, its loudness falling from [vol] to a
     * thousandth, as Web Audio's exponential ramp.
     */
    fun ding(f: Double, dur: Double = .3, vol: Float = .18f, square: Boolean = false) {
        if (!on()) return
        val n = (RATE * dur).toInt()
        Mixer.play(FloatArray(n) { i ->
            val ph = (f * i / RATE) % 1.0
            val w = if (square) (if (ph < .5) 1.0 else -1.0) else 4 * kotlin.math.abs(ph - .5) - 1
            (w * (.001 / vol).pow(i.toDouble() / n)).toFloat()
        }, vol)
    }

    /**
     * Skis on the snow (the ski school's hiss): noise through a band at [Hiss.freq] Hz, at [Hiss.gain], both changed any
     * time, on until [Hiss.stop]. Silent while the settings' sound is off.
     */
    fun hiss(): Hiss = Hiss().also { Mixer.add(it) }

    inner class Hiss internal constructor() {
        @Volatile var gain = 0f
        @Volatile var freq = 1800.0
        @Volatile internal var stopped = false
        internal val band = Biquad(Filter.BAND, 1800.0, .7)
        internal var g = 0f
        internal fun audible() = on()
        fun stop() { stopped = true }
    }

    /** A filter from the Web Audio cookbook (the same curves as BiquadFilterNode's lowpass, highpass and bandpass). */
    internal class Biquad(private val type: Filter, freq: Double, private val q: Double) {
        private var b0 = 0.0; private var b1 = 0.0; private var b2 = 0.0; private var a1 = 0.0; private var a2 = 0.0
        private var x1 = 0.0; private var x2 = 0.0; private var y1 = 0.0; private var y2 = 0.0
        init { set(freq) }
        /** A new frequency, keeping what is already ringing. */
        fun set(freq: Double) {
            val w = 2 * PI * freq.coerceIn(10.0, RATE * .45) / RATE
            val alpha = sin(w) / (2 * maxOf(q, .0001))
            val c = cos(w); val a0 = 1 + alpha
            when (type) {
                Filter.LOW -> { b0 = (1 - c) / 2 / a0; b1 = (1 - c) / a0; b2 = b0 }
                Filter.HIGH -> { b0 = (1 + c) / 2 / a0; b1 = -(1 + c) / a0; b2 = b0 }
                Filter.BAND -> { b0 = alpha / a0; b1 = 0.0; b2 = -alpha / a0 }
            }
            a1 = -2 * c / a0; a2 = (1 - alpha) / a0
        }
        fun next(x: Double): Double {
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = x; y2 = y1; y1 = y
            return y
        }
    }

    /** One stream for every sound: voices added from any thread, mixed in small pieces, soft at the top. */
    private object Mixer {
        private class Voice(val s: FloatArray, val vol: Float, var wait: Int) { var at = 0 }
        private val lock = Any()
        private val voices = ArrayList<Voice>()
        private val hisses = ArrayList<Synth.Hiss>()
        private var running = false

        fun play(s: FloatArray, vol: Float, wait: Int = 0) {
            synchronized(lock) {
                if (voices.size > 96) return // a storm of grains: the rest would not be heard anyway
                voices += Voice(s, vol, maxOf(0, wait))
                wake()
            }
        }

        fun add(h: Synth.Hiss) { synchronized(lock) { hisses += h; wake() } }

        private fun wake() { if (!running) { running = true; Thread(::loop, "synth").apply { isDaemon = true; start() } } }

        private fun loop() {
            val t = runCatching {
                AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(maxOf(AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT), CHUNK * 4))
                    .build().also { it.play() }
            }.getOrNull()
            if (t == null) { synchronized(lock) { voices.clear(); hisses.clear(); running = false }; return }
            val mix = FloatArray(CHUNK); val pcm = ShortArray(CHUNK)
            var quiet = 0
            while (true) {
                java.util.Arrays.fill(mix, 0f)
                synchronized(lock) {
                    if (voices.isEmpty() && hisses.isEmpty()) {
                        quiet += CHUNK
                        if (quiet > RATE * 2) { running = false; t.stop(); t.release(); return }
                    } else quiet = 0
                    val it = voices.iterator()
                    while (it.hasNext()) {
                        val v = it.next()
                        var i = 0
                        if (v.wait > 0) { val w = minOf(v.wait, CHUNK); v.wait -= w; i = w }
                        while (i < CHUNK && v.at < v.s.size) { mix[i] += v.s[v.at++] * v.vol; i++ }
                        if (v.at >= v.s.size) it.remove()
                    }
                    val hi = hisses.iterator()
                    while (hi.hasNext()) {
                        val h = hi.next()
                        val to = if (h.stopped || !h.audible()) 0f else h.gain
                        if (h.stopped && h.g < 1e-4f) { hi.remove(); continue }
                        h.band.set(h.freq)
                        // a smooth change of loudness, about a twentieth of a second
                        for (i in 0 until CHUNK) { h.g += (to - h.g) * .0005f; mix[i] += (h.band.next(Random.nextDouble() * 2 - 1) * h.g).toFloat() }
                    }
                }
                for (i in 0 until CHUNK) pcm[i] = (tanh(mix[i].toDouble()) * 30000).toInt().toShort()
                t.write(pcm, 0, CHUNK)
            }
        }
    }

    companion object {
        const val RATE = 44100
        private const val CHUNK = 512
    }
}
