package dev.xcrafttm.opensoundboard.gui

import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.CyclingButtonWidget
import net.minecraft.text.Text
import dev.xcrafttm.opensoundboard.config.SoundboardConfig
import dev.xcrafttm.opensoundboard.gui.components.VolumeSlider

class SoundboardConfigScreen(private val parent: Screen?) : Screen(Text.translatable("gui.opensoundboard.config.title")) {

    override fun init() {
        clearChildren()
        setupWidgets()
    }

    private fun setupWidgets() {
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.opensoundboard.back")) { close() }
            .position(width / 2 - 100, height - 30)
            .size(200, 20)
            .build()
        )

        addDrawableChild(
            CyclingButtonWidget.onOffBuilder(SoundboardConfig.data.playLocally)
                .build(width / 2 - 100, 50, 200, 20, Text.translatable("gui.opensoundboard.config.play_locally")) { _, value ->
                    SoundboardConfig.data.playLocally = value
                    SoundboardConfig.save()
                }
        )

        addDrawableChild(
            CyclingButtonWidget.onOffBuilder(SoundboardConfig.data.playWhileMuted)
                .build(width / 2 - 100, 75, 200, 20, Text.translatable("gui.opensoundboard.config.play_while_muted")) { _, value ->
                    SoundboardConfig.data.playWhileMuted = value
                    SoundboardConfig.save()
                }
        )

        addDrawableChild(
            CyclingButtonWidget.onOffBuilder(SoundboardConfig.data.syncAudio)
                .build(width / 2 - 100, 100, 200, 20, Text.translatable("gui.opensoundboard.config.sync_audio")) { _, value ->
                    SoundboardConfig.data.syncAudio = value
                    SoundboardConfig.save()
                    init() // Refresh screen to show/hide sliders
                }
        )

        addDrawableChild(
            CyclingButtonWidget.onOffBuilder(SoundboardConfig.data.singleSongAtATime)
                .build(width / 2 - 100, 125, 200, 20, Text.translatable("gui.opensoundboard.config.single_song")) { _, value ->
                    SoundboardConfig.data.singleSongAtATime = value
                    SoundboardConfig.save()
                }
        )

        addDrawableChild(
            CyclingButtonWidget.onOffBuilder(SoundboardConfig.data.loopAll)
                .build(width / 2 - 100, 150, 200, 20, Text.translatable("gui.opensoundboard.config.loop_all")) { _, value ->
                    SoundboardConfig.data.loopAll = value
                    SoundboardConfig.save()
                    dev.xcrafttm.opensoundboard.SoundboardAudioSystem.setGlobalLooping(value)
                }
        )

        val slidersY = 175
        if (SoundboardConfig.data.syncAudio) {
            addDrawableChild(
                VolumeSlider(width / 2 - 100, slidersY, 200, 20, Text.translatable("gui.opensoundboard.volume.synced"), SoundboardConfig.data.globalLocalVolume, {
                    SoundboardConfig.data.globalLocalVolume = it
                    SoundboardConfig.data.globalPlayerVolume = it
                    SoundboardConfig.save()
                })
            )
        } else {
            addDrawableChild(
                VolumeSlider(width / 2 - 100, slidersY, 200, 20, Text.translatable("gui.opensoundboard.config.global_local_volume"), SoundboardConfig.data.globalLocalVolume, {
                    SoundboardConfig.data.globalLocalVolume = it
                    SoundboardConfig.save()
                })
            )

            addDrawableChild(
                VolumeSlider(width / 2 - 100, slidersY + 25, 200, 20, Text.translatable("gui.opensoundboard.config.global_player_volume"), SoundboardConfig.data.globalPlayerVolume, {
                    SoundboardConfig.data.globalPlayerVolume = it
                    SoundboardConfig.save()
                })
            )
        }

        val skipAmounts = listOf(5, 10, 15, 30)
        val skipY = if (SoundboardConfig.data.syncAudio) slidersY + 25 else slidersY + 50
        addDrawableChild(
            ButtonWidget.builder(Text.translatable("gui.opensoundboard.config.skip_amount").append(": ${SoundboardConfig.data.skipAmountSeconds} s")) { button ->
                val currentIndex = skipAmounts.indexOf(SoundboardConfig.data.skipAmountSeconds)
                val nextIndex = if (currentIndex == -1) 0 else (currentIndex + 1) % skipAmounts.size
                SoundboardConfig.data.skipAmountSeconds = skipAmounts[nextIndex]
                SoundboardConfig.save()
                button.message = Text.translatable("gui.opensoundboard.config.skip_amount").append(": ${SoundboardConfig.data.skipAmountSeconds} s")
            }.position(width / 2 - 100, skipY).size(200, 20).build()
        )
    }

    override fun close() {
        client?.setScreen(parent)
    }
}