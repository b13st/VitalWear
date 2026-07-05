package com.github.cfogrady.vitalwear.character.mission

import com.github.cfogrady.vitalwear.character.CharacterManager
import com.github.cfogrady.vitalwear.steps.StepChangeListener
import timber.log.Timber

/**
 * Tracks progress of the active character's special missions (assigned from VBHelper
 * and carried over transfer). Mission slots follow the Vital Bracelet semantics:
 * AVAILABLE slots activate lazily on their first progress event, progress toward a
 * goal, and either COMPLETE or FAIL when the time limit runs out. Rewards are claimed
 * back on VBHelper, so the watch only executes.
 */
class MissionService(
    private val characterManager: CharacterManager,
    private val specialMissionDao: SpecialMissionDao,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
): StepChangeListener {

    override fun processStepChanges(oldSteps: Int, newSteps: Int): Boolean {
        val delta = newSteps - oldSteps
        // The raw hardware counter resets on reboot; ignore non-positive deltas.
        if (delta > 0) {
            recordProgress(SpecialMissionEntity.TYPE_STEPS, delta)
        }
        return false
    }

    fun onVitalsGained(amount: Int) {
        if (amount > 0) {
            recordProgress(SpecialMissionEntity.TYPE_VITALS, amount)
        }
    }

    fun onBattle(won: Boolean) {
        recordProgress(SpecialMissionEntity.TYPE_BATTLES, 1)
        if (won) {
            recordProgress(SpecialMissionEntity.TYPE_WINS, 1)
        }
    }

    /**
     * Advances wall-clock time accounting on the periodic tick. Off-body gaps (the
     * alarm is cancelled while unworn) are counted correctly on the next tick because
     * elapsed time is derived from real timestamps, not tick counts.
     */
    fun onTick(nowMillis: Long = nowProvider()) {
        val characterId = activeCharacterId() ?: return
        val missions = specialMissionDao.getByCharacterId(characterId)
        val updated = mutableListOf<SpecialMissionEntity>()
        for (mission in missions) {
            if (mission.status != SpecialMissionEntity.STATUS_IN_PROGRESS) {
                continue
            }
            val elapsedMinutes = ((nowMillis - mission.lastProgressEpochMillis) / 60_000L).toInt()
            if (elapsedMinutes <= 0) {
                continue
            }
            mission.timeElapsedInMinutes = (mission.timeElapsedInMinutes + elapsedMinutes)
                .coerceAtMost(mission.timeLimitInMinutes.coerceAtLeast(0))
            mission.lastProgressEpochMillis += elapsedMinutes * 60_000L
            if (mission.timeLimitInMinutes > 0 && mission.timeElapsedInMinutes >= mission.timeLimitInMinutes) {
                mission.status = SpecialMissionEntity.STATUS_FAILED
                Timber.i("Special mission type ${mission.type} failed: time limit reached")
            }
            updated.add(mission)
        }
        if (updated.isNotEmpty()) {
            specialMissionDao.updateMany(updated)
        }
    }

    private fun recordProgress(type: Int, delta: Int) {
        val characterId = activeCharacterId() ?: return
        val missions = specialMissionDao.getByCharacterId(characterId)
        val updated = mutableListOf<SpecialMissionEntity>()
        for (mission in missions) {
            if (mission.type != type) {
                continue
            }
            if (mission.status != SpecialMissionEntity.STATUS_AVAILABLE &&
                mission.status != SpecialMissionEntity.STATUS_IN_PROGRESS) {
                continue
            }
            if (mission.status == SpecialMissionEntity.STATUS_AVAILABLE) {
                // Lazy activation: the clock starts with the first progress event.
                mission.status = SpecialMissionEntity.STATUS_IN_PROGRESS
                mission.lastProgressEpochMillis = nowProvider()
            }
            mission.progress = (mission.progress + delta).coerceAtMost(mission.goal.coerceAtLeast(0))
            if (mission.goal > 0 && mission.progress >= mission.goal) {
                mission.status = SpecialMissionEntity.STATUS_COMPLETED
                Timber.i("Special mission type ${mission.type} completed!")
            }
            updated.add(mission)
        }
        if (updated.isNotEmpty()) {
            specialMissionDao.updateMany(updated)
        }
    }

    private fun activeCharacterId(): Int? {
        return characterManager.getCurrentCharacter()?.characterStats?.id
    }
}
