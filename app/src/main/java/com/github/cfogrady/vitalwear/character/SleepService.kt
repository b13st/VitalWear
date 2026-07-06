package com.github.cfogrady.vitalwear.character

import android.content.SharedPreferences
import com.github.cfogrady.vitalwear.SaveService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber

/**
 * Owns the partner's sleep state. Sleep is toggled manually from the menu; a full
 * rest also heals injuries. Optionally the training-limit timer can be frozen while
 * the partner sleeps.
 */
class SleepService(
    private val characterManager: CharacterManager,
    private val saveService: SaveService,
    private val sharedPreferences: SharedPreferences,
) {
    companion object {
        const val PAUSE_TRAINING_WHILE_SLEEPING = "PAUSE_TRAINING_WHILE_SLEEPING"
    }

    // When on, the training-limit timer does not tick down while the partner sleeps.
    private val _pauseTrainingWhileSleeping = MutableStateFlow(sharedPreferences.getBoolean(PAUSE_TRAINING_WHILE_SLEEPING, false))
    val pauseTrainingWhileSleeping: StateFlow<Boolean> = _pauseTrainingWhileSleeping

    fun setPauseTrainingWhileSleeping(enabled: Boolean) {
        _pauseTrainingWhileSleeping.value = enabled
        sharedPreferences.edit().putBoolean(PAUSE_TRAINING_WHILE_SLEEPING, enabled).apply()
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
}
