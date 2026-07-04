package com.github.cfogrady.vitalwear.character.mood

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.github.cfogrady.vitalwear.character.SleepService
import timber.log.Timber
import java.time.LocalDateTime

class MoodBroadcastReceiver(
    private val moodService: MoodService,
    private val sleepService: SleepService) : BroadcastReceiver() {
    companion object {
        const val MOOD_UPDATE = "MOOD_UPDATE_INTENT"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if(intent?.action == MOOD_UPDATE) {
            Timber.i("MOOD_UPDATE_INTENT broadcast intent received")
            val now = LocalDateTime.now()
            // Runs before the mood update so an auto wake-up gets mood/vitals flowing again.
            sleepService.enforceSchedule(now)
            moodService.updateMood(now)
        }
    }
}