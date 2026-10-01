package io.github.pini236.skiapp.game

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin

/**
 * "The crew's descent", the spike's port of site/games/descent: the same physics on a run's real
 * profile, with one button. Hold on the snow to crouch and load, let go to pop away from the slope;
 * hold in the air to flip. Units are metres and seconds; y is up.
 */
class Descent(profile: FloatArray, private val step: Float, val runLength: Float) {
    companion object {
        const val G = 9.81f
        const val VMAX = 30f
        const val EXAG = 1.5f
    }

    private val ground: FloatArray = profile.map { (it - profile[0]) * EXAG }.toFloatArray()

    // player
    var x = 4f; var y = 0f; var s = 7f
    var air = false; var vx = 0f; var vy = 0f
    var ang = 0f; var airRot = 0f; var airT = 0f
    var crouch = 0f; var crash = 0f; var squash = 0f
    var loading = false; var loadT = 0f; var flipArm = false; var holding = false
    var flips = 0; var landings = 0; var crashes = 0; var maxAir = 0f
    var finished = false

    /** Things for the screen to react to: sounds, haptics, snow. Reset by the screen after reading. */
    var eventJump = 0f; var eventLand = 0f; var eventCrash = false; var eventFlip = false

    init { y = groundAt(x) }

    fun groundAt(px: Float): Float {
        val i = (px / step).coerceIn(0f, ground.size - 1.0001f)
        val i0 = i.toInt(); val f = i - i0
        return ground[i0] * (1 - f) + ground[i0 + 1] * f
    }

    fun slope(px: Float) = atan2(groundAt(px + 1) - groundAt(px - 1), 2f)

    fun press() {
        if (finished) return
        holding = true
        if (air) flipArm = true else if (crash <= 0) { loading = true; loadT = 0f }
    }

    fun release() {
        val was = holding
        holding = false; flipArm = false
        if (!was || !loading) return
        loading = false
        if (air || crash > 0 || finished) return
        val k = (loadT / 0.32f).coerceAtMost(1f)
        val pop = 5.4f + 3.8f * k
        val a = slope(x)
        val nx = -sin(a); val ny = cos(a) // pop away from the snow, not straight up
        takeOff(s * cos(a) + pop * nx * 0.55f, s * sin(a) + pop * ny)
        squash = 1f
        eventJump = 0.4f + 0.6f * k
    }

    private fun takeOff(tvx: Float, tvy: Float) { air = true; vx = tvx; vy = tvy; airRot = 0f; airT = 0f; loading = false; flipArm = false }

    fun update(dtIn: Float) {
        val dt = dtIn.coerceAtMost(0.05f)
        if (finished) { s = (s - 9 * dt).coerceAtLeast(0f); x += s * dt; y = groundAt(x); return }
        val tucking = holding && (if (air) flipArm else crash <= 0)
        if (loading) loadT += dt
        squash *= Math.exp(-8.0 * dt).toFloat()
        val a = slope(x)
        if (crash > 0) {
            crash -= dt; s = (s - 6 * dt).coerceAtLeast(0f); x += s * cos(a) * dt; y = groundAt(x); ang += dt * 9
            if (crash <= 0) { ang = a; s = maxOf(s, 4f) }
        } else if (!air) {
            crouch += ((if (tucking) 1f else 0f) - crouch) * (dt * 8).coerceAtMost(1f)
            val drag = if (tucking) 0.0022f else 0.0034f
            val mu = 0.035f
            s += (-G * sin(a) - drag * s * s - mu * G * cos(a)) * dt
            s = s.coerceIn(3.2f, VMAX) // skating on the flats; a racer's top speed
            val nx = x + s * cos(a) * dt; val ty = y + s * sin(a) * dt
            // the snow bends away faster than gravity can hold you: airborne off the lip
            val bend = (a - slope(x + 2.5f)) / 2.5f
            if (s > 7 && bend * s * s > G * cos(a) * 1.05f) { takeOff(s * cos(a), s * sin(a)); x = nx; y = ty }
            else { x = nx; y = groundAt(nx) }
            ang += (a - ang) * (dt * 14).coerceAtMost(1f)
        } else {
            airT += dt; maxAir = maxOf(maxAir, airT)
            val v = hypot(vx, vy); val d = 1 - 0.0034f * v * dt
            vx *= d; vy *= d
            val gk = if (abs(vy) < 2.2f) 0.7f else if (vy < 0) 1.12f else 1f // a floaty top and a firmer fall
            vy -= G * gk * dt; x += vx * dt; y += vy * dt
            val spin = if (tucking) 7.6f else 0f
            ang += spin * dt; airRot += spin * dt
            if (!tucking) { // let go and the skier settles toward the snow below
                val tgt = slope(x)
                val rel = atan2(sin(ang - tgt), cos(ang - tgt))
                ang -= rel * (dt * 6).coerceAtMost(1f)
            }
            if (y <= groundAt(x)) land()
        }
        if (x >= runLength - 8) { finished = true; air = false; y = groundAt(x) }
    }

    private fun land() {
        val a = slope(x)
        val rel = atan2(sin(ang - a), cos(ang - a))
        val n = floor((abs(airRot) + 0.9f) / (2 * Math.PI.toFloat())).toInt()
        air = false; y = groundAt(x)
        if (abs(rel) > 0.75f) { crash = 1.1f; crashes++; eventCrash = true; return }
        val along = vx * cos(a) + vy * sin(a)
        s = along.coerceIn(4f, VMAX); ang = a; squash = -(airT * 0.9f).coerceAtMost(1f)
        landings++
        if (n > 0) { flips += n; eventFlip = true }
        eventLand = (airT / 1.4f).coerceIn(0.25f, 1f)
    }

    val speedKmh get() = (if (air) hypot(vx, vy) else s) * 3.6f
    val distance get() = x
}
