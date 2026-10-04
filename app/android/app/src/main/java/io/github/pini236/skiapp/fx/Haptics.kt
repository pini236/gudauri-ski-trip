package io.github.pini236.skiapp.fx

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Rich haptics where the phone supports them (composition primitives), with simpler fallbacks.
 * [level] says which path this phone got, so the spike can report it.
 */
class Haptics(private val context: Context) {
    private val vib: Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)

    private val primitives: Boolean = Build.VERSION.SDK_INT >= 30 && vib?.areAllPrimitivesSupported(
        VibrationEffect.Composition.PRIMITIVE_TICK, VibrationEffect.Composition.PRIMITIVE_CLICK,
    ) == true

    private val thudPrimitive: Boolean = Build.VERSION.SDK_INT >= 31 && primitives &&
        vib?.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_THUD) == true

    val level: String = when {
        vib == null || !vib.hasVibrator() -> "אין רטט"
        primitives -> "רטט עשיר"
        Build.VERSION.SDK_INT >= 29 -> "רטט בסיסי"
        else -> "רטט פשוט"
    }

    /** The settings' switch (13.7): off, nothing vibrates. */
    private val on get() = FxPrefs.haptics(context)

    private fun prim(id: Int, scale: Float) {
        if (!on) return
        if (Build.VERSION.SDK_INT >= 30) vib?.vibrate(VibrationEffect.startComposition().addPrimitive(id, scale.coerceIn(0f, 1f)).compose())
    }

    private fun predefined(id: Int, ms: Long, amp: Int) {
        if (!on) return
        if (Build.VERSION.SDK_INT >= 29) vib?.vibrate(VibrationEffect.createPredefined(id))
        else vib?.vibrate(VibrationEffect.createOneShot(ms, amp.coerceIn(1, 255)))
    }

    /** One hole of the perforation, one ski edge biting. */
    fun tick(scale: Float = 0.5f) =
        if (primitives) prim(VibrationEffect.Composition.PRIMITIVE_TICK, scale) else predefined(VibrationEffect.EFFECT_TICK, 8, (90 * scale).toInt())

    fun click(scale: Float = 0.8f) =
        if (primitives) prim(VibrationEffect.Composition.PRIMITIVE_CLICK, scale) else predefined(VibrationEffect.EFFECT_CLICK, 15, (160 * scale).toInt())

    /** A landing: heavier the longer you were in the air. */
    fun thud(scale: Float = 1f) = when {
        thudPrimitive -> if (Build.VERSION.SDK_INT >= 31) prim(VibrationEffect.Composition.PRIMITIVE_THUD, scale) else Unit
        primitives -> prim(VibrationEffect.Composition.PRIMITIVE_CLICK, scale)
        else -> predefined(VibrationEffect.EFFECT_HEAVY_CLICK, 30, (255 * scale).toInt())
    }
}
