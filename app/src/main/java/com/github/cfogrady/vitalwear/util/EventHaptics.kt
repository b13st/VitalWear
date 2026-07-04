package com.github.cfogrady.vitalwear.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * App-wide haptics for game events, mirroring the vibration motor of the original
 * Vital Bracelet BE (which buzzes for partner-initiated events, never for menus).
 * All events are suppressed while the partner sleeps so the watch stays quiet at night.
 */
class EventHaptics(context: Context, private val isPartnerSleeping: () -> Boolean) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(VibratorManager::class.java)
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun transformationReady() {
        vibrate(VibrationEffect.createWaveform(longArrayOf(0L, 120L, 80L, 120L, 80L, 200L), -1))
    }

    fun adventureBoss() {
        vibrate(VibrationEffect.createWaveform(longArrayOf(0L, 150L, 100L, 150L), -1))
    }

    fun battleWon() {
        vibrate(VibrationEffect.createWaveform(longArrayOf(0L, 60L, 50L, 60L, 50L, 140L), -1))
    }

    fun battleLost() {
        vibrate(VibrationEffect.createOneShot(250L, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    fun trainingResult(great: Boolean) {
        if (great) {
            vibrate(VibrationEffect.createWaveform(longArrayOf(0L, 60L, 50L, 100L), -1))
        } else {
            vibrate(VibrationEffect.createOneShot(80L, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    private fun vibrate(effect: VibrationEffect) {
        if (isPartnerSleeping()) {
            return
        }
        val deviceVibrator = vibrator ?: return
        if (!deviceVibrator.hasVibrator()) {
            return
        }
        deviceVibrator.vibrate(effect)
    }
}
