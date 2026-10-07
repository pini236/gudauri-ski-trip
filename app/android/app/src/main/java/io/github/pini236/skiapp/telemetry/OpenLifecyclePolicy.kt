package io.github.pini236.skiapp.telemetry

/**
 * Keep one instance for the app process, not for an Activity. Recreating an Activity for a language or rotation
 * must not count another cold app_open; a newly started process counts one even if Android restores saved state.
 * Background returns continue through MainActivity's existing warm-open flag.
 */
class OpenLifecyclePolicy {
    private var coldOpenClaimed = false

    @Synchronized
    fun claimColdOpenOnce(): Boolean {
        if (coldOpenClaimed) return false
        coldOpenClaimed = true
        return true
    }
}
