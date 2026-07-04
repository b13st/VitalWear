package com.github.cfogrady.vitalwear.battle.composable

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.github.cfogrady.vitalwear.battle.data.PreBattleModel
import com.github.cfogrady.vitalwear.composable.util.BitmapScaler
import kotlinx.coroutines.delay

class OpponentSplashFactory(private val bitmapScaler: BitmapScaler) {

    @Composable
    fun OpponentSplash(battleModel: PreBattleModel, stateUpdater: (FightTargetState) -> Unit) {
        var leftScreenEarly = remember { false }
        BackHandler {
            leftScreenEarly = true
            stateUpdater.invoke(FightTargetState.END_FIGHT)
        }
        // Black instead of the battle scenery so the 1:2 splash art gets clean
        // black side bars, like the full-screen splash on the original device.
        Box(modifier = Modifier
            .fillMaxSize()
            .background(Color.Black))
        val battleCharacter = battleModel.opponent
        bitmapScaler.FillHeightBitmap(bitmap = battleCharacter.battleSprites.splashBitmap, contentDescription = "Opponent", alignment = Alignment.BottomCenter,
            modifier = Modifier.clickable {
                leftScreenEarly = true
                stateUpdater.invoke(FightTargetState.READY)
            })
        LaunchedEffect(Unit) {
            delay(1000)
            if(!leftScreenEarly) {
                stateUpdater.invoke(FightTargetState.OPPONENT_NAME)
            }
        }
    }
}