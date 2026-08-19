package app.kasa.core.util

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Dokunsal geri bildirim.
 *
 * Tasarımdaki `HAPTIC` haritasının birebir karşılığı: her etkileşim türünün
 * kendi kısa titreşim deseni var. Genlik denetimi olan cihazlarda desenler
 * genlikle, olmayanlarda sade süreyle çalınır.
 */
class Haptics(context: Context) {

    private val vibrator: Vibrator? =
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator

    @Volatile
    var enabled: Boolean = true

    enum class Kind { TAP, NAV, MEDIUM, SUCCESS, WARNING, TOGGLE, TICK }

    fun play(kind: Kind) {
        if (!enabled) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return

        val (timings, amplitudes) = pattern(kind)
        try {
            if (v.hasAmplitudeControl()) {
                v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                v.vibrate(VibrationEffect.createWaveform(timings, -1))
            }
        } catch (t: Throwable) {
            // Titreşim asla akışı bozmamalı.
        }
    }

    private fun pattern(kind: Kind): Pair<LongArray, IntArray> = when (kind) {
        Kind.TAP -> longArrayOf(0, 8) to intArrayOf(0, 90)
        Kind.NAV -> longArrayOf(0, 12) to intArrayOf(0, 120)
        Kind.MEDIUM -> longArrayOf(0, 14) to intArrayOf(0, 160)
        Kind.SUCCESS -> longArrayOf(0, 10, 28, 18) to intArrayOf(0, 120, 0, 200)
        Kind.WARNING -> longArrayOf(0, 26, 36, 26) to intArrayOf(0, 200, 0, 200)
        Kind.TOGGLE -> longArrayOf(0, 5, 18, 9) to intArrayOf(0, 80, 0, 140)
        Kind.TICK -> longArrayOf(0, 4) to intArrayOf(0, 60)
    }
}
