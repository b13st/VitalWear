package com.github.cfogrady.vitalwear.character.mission

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One special mission slot for a character, mirroring the Vital Bracelet's four
 * NFC mission slots. Type/status use the lib-vb-nfc ordinals (also the proto enum
 * numbers): type NONE=0/STEPS=1/VITALS=2/BATTLES=3/WINS=4; status UNAVAILABLE=0/
 * IN_PROGRESS=1/FAILED=2/COMPLETED=3/AVAILABLE=4.
 */
@Entity(tableName = "special_missions")
data class SpecialMissionEntity(
    @PrimaryKey(autoGenerate = true) var id: Int = 0,
    @ColumnInfo(name = "character_id", defaultValue = "0") var characterId: Int = 0,
    @ColumnInfo(name = "slot", defaultValue = "0") var slot: Int = 0,
    @ColumnInfo(name = "type", defaultValue = "0") var type: Int = 0,
    @ColumnInfo(name = "status", defaultValue = "0") var status: Int = 0,
    @ColumnInfo(name = "watch_id", defaultValue = "0") var watchId: Int = 0,
    @ColumnInfo(name = "goal", defaultValue = "0") var goal: Int = 0,
    @ColumnInfo(name = "progress", defaultValue = "0") var progress: Int = 0,
    @ColumnInfo(name = "time_limit_in_minutes", defaultValue = "0") var timeLimitInMinutes: Int = 0,
    @ColumnInfo(name = "time_elapsed_in_minutes", defaultValue = "0") var timeElapsedInMinutes: Int = 0,
    // Watch-only bookkeeping for wall-clock time accounting; never exported.
    @ColumnInfo(name = "last_progress_epoch_millis", defaultValue = "0") var lastProgressEpochMillis: Long = 0,
) {
    companion object {
        const val TYPE_NONE = 0
        const val TYPE_STEPS = 1
        const val TYPE_VITALS = 2
        const val TYPE_BATTLES = 3
        const val TYPE_WINS = 4

        const val STATUS_UNAVAILABLE = 0
        const val STATUS_IN_PROGRESS = 1
        const val STATUS_FAILED = 2
        const val STATUS_COMPLETED = 3
        const val STATUS_AVAILABLE = 4
    }
}
