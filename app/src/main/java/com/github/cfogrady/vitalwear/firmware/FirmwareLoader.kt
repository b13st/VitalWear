package com.github.cfogrady.vitalwear.firmware

import android.graphics.Bitmap
import com.github.cfogrady.vb.dim.sprite.BemSpriteReader
import com.github.cfogrady.vb.dim.sprite.SpriteData.Sprite
import com.github.cfogrady.vb.dim.util.ByteUtils
import com.github.cfogrady.vb.dim.util.RelativeByteOffsetInputStream
import com.github.cfogrady.vitalwear.firmware.components.AdventureBitmaps
import com.github.cfogrady.vitalwear.firmware.components.BattleBitmaps
import com.github.cfogrady.vitalwear.firmware.components.CharacterIconBitmaps
import com.github.cfogrady.vitalwear.firmware.components.EmoteBitmaps
import com.github.cfogrady.vitalwear.firmware.components.MenuBitmaps
import com.github.cfogrady.vitalwear.firmware.components.TrainingBitmaps
import com.github.cfogrady.vitalwear.firmware.components.TransformationBitmaps
import com.github.cfogrady.vitalwear.common.card.SpriteBitmapConverter
import com.github.cfogrady.vitalwear.firmware.components.AdventureSpriteIndexes
import com.github.cfogrady.vitalwear.firmware.components.BattleSpriteIndexes
import com.github.cfogrady.vitalwear.firmware.components.CharacterIconSpriteIndexes
import com.github.cfogrady.vitalwear.firmware.components.EmoteSpriteIndexes
import com.github.cfogrady.vitalwear.firmware.components.MenuSpriteIndexes
import com.github.cfogrady.vitalwear.firmware.components.TrainingSpriteIndexes
import com.github.cfogrady.vitalwear.firmware.components.TransformationSpriteIndexes
import com.google.common.collect.Lists
import timber.log.Timber
import java.io.InputStream
import java.nio.charset.StandardCharsets

class FirmwareLoader(private val bemSpriteReader: BemSpriteReader, private val spriteBitmapConverter: SpriteBitmapConverter) {

    fun loadFirmware(fileInput: InputStream): Firmware {
        val input = RelativeByteOffsetInputStream(fileInput)
        val version = versionFromHeader(input.readNBytes(16))
        val firmwareSpriteIndexes = firmwareSpriteIndexesFromVersion(version)
        return internalLoadFirmware(firmwareSpriteIndexes, input)
    }

    fun loadFirmware(firmwareSpriteIndexes: FirmwareSpriteIndexes, fileInput: InputStream) {
        val input = RelativeByteOffsetInputStream(fileInput)
        internalLoadFirmware(firmwareSpriteIndexes, input)
    }

    private fun internalLoadFirmware(firmwareSpriteIndexes: FirmwareSpriteIndexes, input: RelativeByteOffsetInputStream): Firmware {
        val startFirmwareRead = System.currentTimeMillis()
        input.readToOffset(firmwareSpriteIndexes.spriteDimensionsLocation)
        val dimensionsList = bemSpriteReader.readSpriteDimensions(input)
        input.readToOffset(firmwareSpriteIndexes.spritePackageLocation)
        val sprites = bemSpriteReader.getSpriteData(input, dimensionsList).sprites
        Timber.i("Time to initialize firmware: ${System.currentTimeMillis() - startFirmwareRead}")
        val insertCardIcon = spriteBitmapConverter.getBitmap(sprites[firmwareSpriteIndexes.insertCardIcon])
        val greenBackground = spriteBitmapConverter.getBitmap(sprites[firmwareSpriteIndexes.greenBackground])
        val orangeBackground = spriteBitmapConverter.getBitmap(sprites[firmwareSpriteIndexes.orangeBackground])
        val blueBackground = spriteBitmapConverter.getBitmap(sprites[firmwareSpriteIndexes.blueBackground])

        val readyIcon = spriteBitmapConverter.getBitmap(sprites[firmwareSpriteIndexes.readyIdx])
        val goIcon = spriteBitmapConverter.getBitmap(sprites[firmwareSpriteIndexes.goIdx])

        val emoteBitmaps = buildEmoteBitmaps(firmwareSpriteIndexes.emoteSpriteIndexes, sprites)

        val battleBitmaps = buildBattleBitmaps(firmwareSpriteIndexes.battleSpriteIndexes, sprites)

        return Firmware(
            buildCharacterIconBitmaps(firmwareSpriteIndexes.characterIconSpriteIndexes, emoteBitmaps, sprites),
            buildMenuBitmaps(firmwareSpriteIndexes.menuSpriteIndexes, sprites, sprites[firmwareSpriteIndexes.battleSpriteIndexes.fightTextIdx]),
            buildAdventureBitmaps(firmwareSpriteIndexes.adventureSpriteIndexes, sprites),
            battleBitmaps,
            buildTrainingBitmaps(firmwareSpriteIndexes.trainingSpriteIndexes, sprites),
            buildTransformationBitmaps(firmwareSpriteIndexes.transformationSpriteIndexes, sprites),
            insertCardIcon,
            arrayListOf(greenBackground, orangeBackground, blueBackground, battleBitmaps.battleBackground),
            readyIcon,
            goIcon,
        )
    }

    private fun versionFromHeader(headerBytes: ByteArray): String {
        return headerBytes.toString(StandardCharsets.UTF_16LE) // UTF-16 Little Endian Byte Order
    }

    private fun firmwareSpriteIndexesFromVersion(version: String): FirmwareSpriteIndexes {
        if("VBBE_10B" == version) {
            return Firmware10BSpriteIndexes.instance
        } else if ("VBBE_20A" == version) {
            return Firmware20ASpriteIndexes.instance
        }
        throw IllegalArgumentException("Unknown firmware version: $version")
    }

    private fun buildEmoteBitmaps(emoteSpriteIndexes: EmoteSpriteIndexes, sprites: List<Sprite>) : EmoteBitmaps {
        val happyEmote = spriteBitmapConverter.getBitmaps(sprites.subList(
            emoteSpriteIndexes.happyEmoteStartIdx, emoteSpriteIndexes.happyEmoteEndIdx
        ))
        val loseEmote = spriteBitmapConverter.getBitmaps(sprites.subList(
            emoteSpriteIndexes.loseEmoteStartIdx, emoteSpriteIndexes.loseEmoteEndIdx
        ))
        val sweatEmote = spriteBitmapConverter.getBitmap(sprites[emoteSpriteIndexes.sweatEmoteIdx])
        val injuredEmote = spriteBitmapConverter.getBitmaps(sprites.subList(
            emoteSpriteIndexes.injuredEmoteStartIdx, emoteSpriteIndexes.injuredEmoteEndIdx
        ))
        val sleepEmote = spriteBitmapConverter.getBitmaps(sprites.subList(
            emoteSpriteIndexes.sleepEmoteStartIdx, emoteSpriteIndexes.sleepEmoteEndIdx
        ))
        return EmoteBitmaps(happyEmote, loseEmote, sweatEmote, injuredEmote, sleepEmote)
    }

    private fun buildCharacterIconBitmaps(indexLocations: CharacterIconSpriteIndexes, emoteBitmaps: EmoteBitmaps, sprites: List<Sprite>): CharacterIconBitmaps {
        val stepsIcon = spriteBitmapConverter.getBitmap(sprites[indexLocations.stepsIconIdx])
        val vitalsIcon = spriteBitmapConverter.getBitmap(sprites[indexLocations.vitalsIconIdx])
        val supportIcon = spriteBitmapConverter.getBitmap(sprites[indexLocations.supportIconIdx])
        return CharacterIconBitmaps(stepsIcon, vitalsIcon, supportIcon, emoteBitmaps)
    }

    private fun buildMenuBitmaps(menuSpriteIndexes: MenuSpriteIndexes, firmwareSprites: List<Sprite>, fightTextSprite: Sprite): MenuBitmaps {
        val statsMenuIcon = spriteBitmapConverter.getBitmap(firmwareSprites[menuSpriteIndexes.statsIconIdx])
        val characterSelectorIcon = spriteBitmapConverter.getBitmap(firmwareSprites[menuSpriteIndexes.characterSelectorIcon])
        val trainingMenuIcon = spriteBitmapConverter.getBitmap(firmwareSprites[menuSpriteIndexes.trainingMenuIcon])
        val adventureIcon = spriteBitmapConverter.getBitmap(firmwareSprites[menuSpriteIndexes.adventureMenuIcon])
        val connectMenuIcon = spriteBitmapConverter.getBitmap(firmwareSprites[menuSpriteIndexes.connectIcon])
        // The firmware has no dedicated battle menu sprite (battles lived under CONNECT on the
        // original device), so the 16x16 battles icon is tripled to match the pictogram size
        // of the other 80x80 menu icons.
        val battleMenuIcon = scaledSpriteBitmap(firmwareSprites[menuSpriteIndexes.battleIcon], 3)
        // The firmware's yellow FIGHT! text recolored to the menu's label white, so the
        // battle entry gets a pixel-font label like the ones baked into the other menu sprites.
        val battleText = whiteRecoloredBitmap(fightTextSprite)
        val stopText = spriteBitmapConverter.getBitmap(firmwareSprites[menuSpriteIndexes.stopText])
        val stopIcon = spriteBitmapConverter.getBitmap(firmwareSprites[menuSpriteIndexes.stopIcon])
        val settingsIcon = spriteBitmapConverter.getBitmap(firmwareSprites[menuSpriteIndexes.settingsMenuIcon])
        val sleepIcon = spriteBitmapConverter.getBitmap(firmwareSprites[menuSpriteIndexes.sleep])
        val wakeIcon = spriteBitmapConverter.getBitmap(firmwareSprites[menuSpriteIndexes.wakeup])
        return MenuBitmaps(statsMenuIcon, characterSelectorIcon, trainingMenuIcon, adventureIcon, stopText, stopIcon, connectMenuIcon, battleMenuIcon, battleText, settingsIcon, sleepIcon, wakeIcon)
    }

    private fun whiteRecoloredBitmap(sprite: Sprite): Bitmap {
        val pixels = spriteBitmapConverter.createARGBIntArray(sprite)
        for (i in pixels.indices) {
            if (pixels[i] ushr 24 != 0) {
                pixels[i] = 0xFFFFFFFF.toInt()
            }
        }
        return Bitmap.createBitmap(pixels, sprite.width, sprite.height, Bitmap.Config.HARDWARE)
    }

    // Integer nearest-neighbor upscale done on the pixel array because the converter
    // produces HARDWARE bitmaps, which can't be redrawn through a software canvas.
    private fun scaledSpriteBitmap(sprite: Sprite, factor: Int): Bitmap {
        val pixels = spriteBitmapConverter.createARGBIntArray(sprite)
        val scaledWidth = sprite.width * factor
        val scaledHeight = sprite.height * factor
        val scaledPixels = IntArray(scaledWidth * scaledHeight) { i ->
            val x = (i % scaledWidth) / factor
            val y = (i / scaledWidth) / factor
            pixels[y * sprite.width + x]
        }
        return Bitmap.createBitmap(scaledPixels, scaledWidth, scaledHeight, Bitmap.Config.HARDWARE)
    }

    private fun buildAdventureBitmaps(adventureSpriteIndexes: AdventureSpriteIndexes, sprites: List<Sprite>): AdventureBitmaps {
        return AdventureBitmaps(
            spriteBitmapConverter.getBitmap(sprites[adventureSpriteIndexes.advImageIdx]),
            spriteBitmapConverter.getBitmap(sprites[adventureSpriteIndexes.missionImageIdx]),
            spriteBitmapConverter.getBitmap(sprites[adventureSpriteIndexes.nextMissionIdx]),
            spriteBitmapConverter.getBitmap(sprites[adventureSpriteIndexes.stageIdx]),
            spriteBitmapConverter.getBitmap(sprites[adventureSpriteIndexes.flagIdx]),
            spriteBitmapConverter.getBitmap(sprites[adventureSpriteIndexes.hiddenIdx]),
            spriteBitmapConverter.getBitmap(sprites[adventureSpriteIndexes.underlineIdx]),
        )
    }

    private fun buildBattleBitmaps(battleSpriteIndexes: BattleSpriteIndexes, sprites: List<Sprite>): BattleBitmaps {
        val battleBackground = spriteBitmapConverter.getBitmap(sprites[battleSpriteIndexes.battleBackgroundIdx])
        val attackSprites = spriteBitmapConverter.getBitmaps(sprites.subList(
            battleSpriteIndexes.attackStartIdx, battleSpriteIndexes.attackEndIdx
        ))
        val largeAttackSprites= spriteBitmapConverter.getBitmaps(sprites.subList(
            battleSpriteIndexes.criticalAttackStartIdx, battleSpriteIndexes.criticalAttackEndIdx
        ))
        val emptyHP = spriteBitmapConverter.getBitmap(sprites[battleSpriteIndexes.emptyHpIdx])
        val partnerHPIcons = Lists.newArrayList(emptyHP)
        partnerHPIcons.addAll(spriteBitmapConverter.getBitmaps(sprites.subList(
            battleSpriteIndexes.partnerHpStartIdx, battleSpriteIndexes.partnerHpEndIdx
        )))
        val opponentHPIcons = Lists.newArrayList(emptyHP)
        opponentHPIcons.addAll(spriteBitmapConverter.getBitmaps(sprites.subList(
            battleSpriteIndexes.opponentHpStartIdx, battleSpriteIndexes.opponentHpEndIdx
        )))
        val hitSprites = spriteBitmapConverter.getBitmaps(sprites.subList(battleSpriteIndexes.hitStartIdx, battleSpriteIndexes.hitEndIdx))
        val vitalRangeSprites = spriteBitmapConverter.getBitmaps(sprites.subList(
            battleSpriteIndexes.vitalsRangeStartIdx, battleSpriteIndexes.vitalsRangeEndIdx
        ))
        return BattleBitmaps(attackSprites, largeAttackSprites, battleBackground, partnerHPIcons, opponentHPIcons, hitSprites, vitalRangeSprites)
    }

    private fun buildTransformationBitmaps(transformationSpriteIndexes: TransformationSpriteIndexes, sprites: List<Sprite>): TransformationBitmaps {
        val blackBackground = spriteBitmapConverter.getBitmap(sprites[transformationSpriteIndexes.blackBackground])
        val weakPulse = spriteBitmapConverter.getBitmap(sprites[transformationSpriteIndexes.weakPulse])
        val strongPulse = spriteBitmapConverter.getBitmap(sprites[transformationSpriteIndexes.strongPulse])
        val newBackgrounds = spriteBitmapConverter.getBitmaps(sprites.subList(
            transformationSpriteIndexes.newBackgroundStartIdx, transformationSpriteIndexes.newBackgroundEndIdx
        ))
        val rayOfLightBackground = spriteBitmapConverter.getBitmap(sprites[transformationSpriteIndexes.rayOfLightBackground])
        return TransformationBitmaps(
            blackBackground,
            weakPulse,
            strongPulse,
            newBackgrounds,
            rayOfLightBackground,
            spriteBitmapConverter.getBitmap(sprites[transformationSpriteIndexes.star]),
            spriteBitmapConverter.getBitmap(sprites[transformationSpriteIndexes.hourglass]),
            spriteBitmapConverter.getBitmap(sprites[transformationSpriteIndexes.vitalsIcon]),
            spriteBitmapConverter.getBitmap(sprites[transformationSpriteIndexes.battlesIcon]),
            spriteBitmapConverter.getBitmap(sprites[transformationSpriteIndexes.winRatioIcon]),
            spriteBitmapConverter.getBitmap(sprites[transformationSpriteIndexes.ppIcon]),
            spriteBitmapConverter.getBitmap(sprites[transformationSpriteIndexes.squatIcon]),
            spriteBitmapConverter.getBitmap(sprites[transformationSpriteIndexes.locked])
        )
    }

    private fun buildTrainingBitmaps(trainingSpriteIndexes: TrainingSpriteIndexes, sprites: List<Sprite>): TrainingBitmaps {
        return TrainingBitmaps(
            spriteBitmapConverter.getBitmap(sprites[trainingSpriteIndexes.squatTextIdx]),
            spriteBitmapConverter.getBitmap(sprites[trainingSpriteIndexes.squatIconIdx]),
            spriteBitmapConverter.getBitmap(sprites[trainingSpriteIndexes.crunchTextIdx]),
            spriteBitmapConverter.getBitmap(sprites[trainingSpriteIndexes.crunchIconIdx]),
            spriteBitmapConverter.getBitmap(sprites[trainingSpriteIndexes.punchTextIdx]),
            spriteBitmapConverter.getBitmap(sprites[trainingSpriteIndexes.punchIconIdx]),
            spriteBitmapConverter.getBitmap(sprites[trainingSpriteIndexes.dashTextIdx]),
            spriteBitmapConverter.getBitmap(sprites[trainingSpriteIndexes.dashIconIdx]),
            spriteBitmapConverter.getBitmaps(sprites.subList(trainingSpriteIndexes.trainingStateStartIdx, trainingSpriteIndexes.trainingStateEndIdx)),
            spriteBitmapConverter.getBitmap(sprites[trainingSpriteIndexes.goodIdx]),
            spriteBitmapConverter.getBitmap(sprites[trainingSpriteIndexes.greatIdx]),
            spriteBitmapConverter.getBitmap(sprites[trainingSpriteIndexes.bpIdx]),
            spriteBitmapConverter.getBitmap(sprites[trainingSpriteIndexes.hpIdx]),
            spriteBitmapConverter.getBitmap(sprites[trainingSpriteIndexes.apIdx]),
            spriteBitmapConverter.getBitmap(sprites[trainingSpriteIndexes.ppIdx]),
            spriteBitmapConverter.getBitmap(sprites[trainingSpriteIndexes.mission]),
            spriteBitmapConverter.getBitmap(sprites[trainingSpriteIndexes.clear]),
            spriteBitmapConverter.getBitmap(sprites[trainingSpriteIndexes.failed]),
        )
    }
}