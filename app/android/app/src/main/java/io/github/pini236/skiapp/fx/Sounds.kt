package io.github.pini236.skiapp.fx

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/** Short sounds from assets/audio (copied read-only from site/audio). SoundPool keeps them decoded for low latency. */
class Sounds(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        ).build()

    private val ids = HashMap<String, Int>()

    init {
        for (name in listOf("ticket-tear", "ticket-slide", "ticket-land")) {
            context.assets.openFd("audio/$name.wav").use { ids[name] = pool.load(it, 1) }
        }
    }

    fun play(name: String, volume: Float = 1f, rate: Float = 1f) {
        val id = ids[name] ?: return
        pool.play(id, volume, volume, 1, 0, rate.coerceIn(0.5f, 2f))
    }

    fun release() = pool.release()
}
