package com.example.audio

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class CyberVibrationManager(private val context: Context) {

    private val vibrator: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (_: Exception) {
        null
    }

    /**
     * Trigger proximity pulse when near active ICE entities.
     * Pulse intensity scales with closer distance.
     */
    fun triggerIceProximityVibration(distance: Double) {
        if (vibrator?.hasVibrator() != true) return
        val clampedDist = distance.coerceIn(0.5, 3.5)
        val duration = (70 - clampedDist * 15).toLong().coerceIn(20L, 80L)
        val intensity = ((1.0 - (clampedDist / 4.0)) * 255).toInt().coerceIn(40, 255)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, intensity))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(duration)
            }
        } catch (_: Exception) {}
    }

    /**
     * Trigger urgent warning pulse when hacking minigame timer is running low (<= 5 sec).
     */
    fun triggerLowTimerPulse() {
        if (vibrator?.hasVibrator() != true) return
        val pattern = longArrayOf(0, 35, 45, 35)
        val amplitudes = intArrayOf(0, 220, 0, 220)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        } catch (_: Exception) {}
    }

    /**
     * Trigger short success haptic burst upon completing hack node.
     */
    fun triggerHackSuccess() {
        if (vibrator?.hasVibrator() != true) return
        val pattern = longArrayOf(0, 30, 40, 60)
        val amplitudes = intArrayOf(0, 150, 0, 255)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        } catch (_: Exception) {}
    }

    /**
     * Trigger heavy error feedback when ICE trace spikes or hack fails.
     */
    fun triggerIceTraceWarning() {
        if (vibrator?.hasVibrator() != true) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(120L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(120L)
            }
        } catch (_: Exception) {}
    }
}
