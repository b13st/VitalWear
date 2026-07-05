package com.github.cfogrady.vitalwear.transfer.hce

import com.github.cfogrady.vitalwear.VitalWearApp
import com.github.cfogrady.vitalwear.character.CharacterManager
import com.github.cfogrady.vitalwear.character.data.CharacterState
import com.github.cfogrady.vitalwear.protos.Character
import com.github.cfogrady.vitalwear.transfer.persistImportedSpecialMissions
import com.github.cfogrady.vitalwear.transfer.resolveImportedCardMeta
import com.github.cfogrady.vitalwear.transfer.sanitizeForImport
import com.github.cfogrady.vitalwear.transfer.toCharacterEntity
import com.github.cfogrady.vitalwear.transfer.toCharacterSettings
import com.github.cfogrady.vitalwear.transfer.toTransformationHistoryEntities
import com.github.cfogrady.vitalwear.transfer.validateForImport
import com.github.cfogrady.vitalwear.transfer.remapImportedRootCardName
import com.github.cfogrady.vitalwear.transfer.toProto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import timber.log.Timber

class VitalWearHceTransferRepository(
    private val app: VitalWearApp,
) {
    fun deleteCurrentCharacterAfterSuccessfulSend() {
        app.characterManager.deleteCurrentCharacter()
    }

    /**
     * Builds and returns the serialised protobuf payload for the currently active character,
     * or null if there is no active character.
     *
     * Called synchronously from [VitalWearHostApduService.processCommandApdu] (runs on the
     * HCE thread) via [runBlocking].  All DB work is pinned to [Dispatchers.IO] so the main
     * thread is never blocked through coroutine machinery itself — and the NFC 10-second
     * transaction timeout is more than sufficient for these fast queries.
     */
    fun getActiveCharacterPayload(): ByteArray? = runBlocking {
        withContext(Dispatchers.IO) {
            val character = app.characterManager.getCurrentCharacter()
                ?: return@withContext null
            val transformationHistory = app.characterManager.getTransformationHistory(character.characterStats.id)
            val maxAdventureByCard = app.adventureService
                .getMaxAdventureIdxByCardCompletedForCharacter(character.characterStats.id)
            character.toProto(
                transformationHistory = transformationHistory,
                maxAdventureCompletedByCard = maxAdventureByCard,
                currentExerciseLevel = app.heartRateService.currentExerciseLevel.value,
                heartRateCurrent = app.heartRateService.lastHeartRate.value,
                specialMissions = app.database.specialMissionDao().getByCharacterId(character.characterStats.id),
            ).toByteArray()
        }
    }

    suspend fun importCharacter(payload: ByteArray): Boolean {
        val incoming = Character.parseFrom(payload)
        val sanitized = incoming.sanitizeForImport()
        val speciesDao = app.database.speciesEntityDao()
        val matchedCard = sanitized.resolveImportedCardMeta(app.cardMetaEntityDao, speciesDao)
        val validationError = sanitized.validateForImport(app.cardMetaEntityDao, speciesDao, matchedCard)
        if (validationError != null) {
            return false
        }

        val importCharacter = sanitized.remapImportedRootCardName(matchedCard!!.cardName)
        val characterId = app.characterManager.addCharacter(
            importCharacter.cardName,
            importCharacter.characterStats.toCharacterEntity(importCharacter.cardName),
            importCharacter.settings.toCharacterSettings(),
            importCharacter.transformationHistoryList.toTransformationHistoryEntities()
        )
        // Missions are part of the committed payload, not a best-effort extra.
        persistImportedSpecialMissions(importCharacter, characterId, app.database.specialMissionDao())
        // The character is persisted at this point, so the transfer itself has succeeded.
        // Adventure completion and activating the character are best-effort extras: a failure
        // there must not report the whole transfer as failed to the user.
        runCatching {
            app.adventureService.addCharacterAdventures(characterId, importCharacter.maxAdventureCompletedByCardMap)
            app.characterManager.swapToCharacter(
                app.applicationContext,
                CharacterManager.SwapCharacterIdentifier.buildAnonymous(
                    importCharacter.cardName,
                    characterId,
                    importCharacter.characterStats.slotId,
                    CharacterState.STORED,
                )
            )
        }.onFailure {
            Timber.e(it, "Imported character persisted but post-import steps failed")
        }

        // Keep COMMIT fast on HCE: heavy sprite file checks can outlive NFC field and cause TagLost.
        return true
    }
}
