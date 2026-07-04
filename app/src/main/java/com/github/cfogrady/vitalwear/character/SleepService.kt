package com.github.cfogrady.vitalwear.character

import android.content.SharedPreferences
import com.github.cfogrady.vitalwear.SaveService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
import java.time.LocalDateTime

/**
 * Owns the partner's sleep state. Supports the manual menu toggle and an optional
 * automatic schedule like the original Vital Bracelet, where the partner goes to bed
 * and wakes up on its own at fixed hours. A full rest also heals injuries.
 */
class SleepService(
    private val characterManager: CharacterManager,
    private val saveService: SaveService,
    private val sharedPreferences: SharedPreferences,
) {
    companion object {
        const val AUTO_SLEEP_ENABLED = "AUTO_SLEEP_ENABLED"
        const val AUTO_SLEEP_BED_HOUR = "AUTO_SLEEP_BED_HOUR"
        const val AUTO_SLEEP_WAKE_HOUR = "AUTO_SLEEP_WAKE_HOUR"
        const val DEFAULT_BED_HOUR = 22
        const val DEFAULT_WAKE_HOUR = 7
    }

    private val _autoSleepEnabled = MutableStateFlow(sharedPreferences.getBoolean(AUTO_SLEEP_ENABLED, false))
    val autoSleepEnabled: StateFlow<Boolean> = _autoSleepEnabled

    private val _bedHour = MutableStateFlow(sharedPreferences.getInt(AUTO_SLEEP_BED_HOUR, DEFAULT_BED_HOUR))
    val bedHour: StateFlow<Int> = _bedHour

    private val _wakeHour = MutableStateFlow(sharedPreferences.getInt(AUTO_SLEEP_WAKE_HOUR, DEFAULT_WAKE_HOUR))
    val wakeHour: StateFlow<Int> = _wakeHour

    fun setAutoSleepEnabled(enabled: Boolean) {
        _autoSleepEnabled.value = enabled
        sharedPreferences.edit().putBoolean(AUTO_SLEEP_ENABLED, enabled).apply()
    }

    fun setBedHour(hour: Int) {
        val bounded = ((hour % 24) + 24) % 24
        _bedHour.value = bounded
        sharedPreferences.edit().putInt(AUTO_SLEEP_BED_HOUR, bounded).apply()
    }

    fun setWakeHour(hour: Int) {
        val bounded = ((hour % 24) + 24) % 24
        _wakeHour.value = bounded
        sharedPreferences.edit().putInt(AUTO_SLEEP_WAKE_HOUR, bounded).apply()
    }

    fun toggleSleep() {
        characterManager.getCurrentCharacter()?.let {
            setSleeping(!it.characterStats.sleeping)
        }
    }

    fun setSleeping(sleeping: Boolean) {
        val character = characterManager.getCurrentCharacter() ?: return
        if (character.characterStats.sleeping == sleeping) {
            return
        }
        character.characterStats.sleeping = sleeping
        if (!sleeping) {
            healOnWake(character)
        }
        saveService.saveAsync()
    }

    // A full rest cures injuries, like a night of sleep on the original device.
    private fun healOnWake(character: VBCharacter) {
        if (character.characterStats.injured) {
            Timber.i("Partner woke up rested: injury healed")
            character.characterStats.injured = false
        }
    }

    /**
     * Called on the periodic mood-update tick (~5 min). When the schedule is enabled it
     * governs the sleep state, like the original device where bedtime is automatic.
     */
    fun enforceSchedule(now: LocalDateTime) {
        if (!_autoSleepEnabled.value) {
            return
        }
        val bed = _bedHour.value
        val wake = _wakeHour.value
        if (bed == wake) {
            return // degenerate window, nothing to enforce
        }
        val hour = now.hour
        val shouldSleep = if (bed > wake) {
            hour >= bed || hour < wake
        } else {
            hour in bed until wake
        }
        setSleeping(shouldSleep)
    }
}
