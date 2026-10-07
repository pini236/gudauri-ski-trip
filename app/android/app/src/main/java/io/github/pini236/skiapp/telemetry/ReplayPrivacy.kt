package io.github.pini236.skiapp.telemetry

/** Recording consent and upload permission are separate: old segments never become valid after a later opt-in. */
internal class ReplayPrivacy {
    data class Eligibility(val consent: Boolean, val analytics: Boolean, val device: Boolean, val foreground: Boolean, val publicScreen: Boolean) {
        val allowed get() = consent && analytics && device && foreground && publicScreen
    }

    private var state = Eligibility(false, false, false, false, false)
    private var generation = 0L
    private var replay: String? = null
    private val segments = HashMap<String, Long>()
    private val requests = HashSet<() -> Unit>()

    /** Invalidates the whole old recording before content/consent changes, including its queued videos. */
    fun update(next: Eligibility): Boolean {
        val cancel: List<() -> Unit>
        synchronized(this) {
            if (next == state) return false
            state = next
            generation++
            replay = null
            segments.clear()
            cancel = requests.toList()
            requests.clear()
        }
        cancel.forEach { runCatching(it) }
        return true
    }

    @Synchronized fun ticket(): Long? = generation.takeIf { state.allowed }
    @Synchronized fun bind(ticket: Long, replayId: String): Boolean {
        if (ticket != generation || !state.allowed || replayId.isBlank() || replayId == "00000000000000000000000000000000") return false
        replay = replayId
        return true
    }
    @Synchronized fun permit(replayId: String, eventId: String): Boolean {
        if (!state.allowed || replay != replayId || eventId.isBlank()) return false
        segments[eventId] = generation
        return true
    }
    @Synchronized fun maySend(eventId: String?): Boolean = state.allowed && eventId != null && segments[eventId] == generation
    @Synchronized fun beginSend(eventId: String?, cancel: () -> Unit): Boolean {
        if (!maySend(eventId)) return false
        requests.add(cancel)
        return true
    }
    @Synchronized fun endSend(cancel: () -> Unit) { requests.remove(cancel) }

    companion object {
        // Fail closed for new routes. Home and settings contain names/trips; meet may contain a personal plan.
        fun publicScreen(path: String): Boolean = path == "games" || path in setOf(
            "games/merge", "games/fresh", "games/school", "games/snowball", "games/descent",
        )
    }
}
