package org.kvxd.simplesoundboard.gui

import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.CyclingButtonWidget
import net.minecraft.text.Text
import org.kvxd.simplesoundboard.config.SoundboardConfig

class SoundboardConfigScreen(private val parent: Screen?) : Screen(Text.translatable("gui.simplesoundboard.config.title")) {

    override fun init() {
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.simplesoundboard.back")) { close() }
            .position(width / 2 - 100, height - 30)
            .size(200, 20)
            .build()
        )

        addDrawableChild(
            CyclingButtonWidget.onOffBuilder(SoundboardConfig.data.playLocally)
                .build(width / 2 - 100, 50, 200, 20, Text.translatable("gui.simplesoundboard.config.play_locally")) { _, value ->
                    SoundboardConfig.data.playLocally = value
                    SoundboardConfig.save()
                }
        )

        addDrawableChild(
            CyclingButtonWidget.onOffBuilder(SoundboardConfig.data.playWhileMuted)
                .build(width / 2 - 100, 75, 200, 20, Text.translatable("gui.simplesoundboard.config.play_while_muted")) { _, value ->
                    SoundboardConfig.data.playWhileMuted = value
                    SoundboardConfig.save()
                }
        )

        addDrawableChild(
            VolumeSlider(width / 2 - 100, 100, 200, 20, Text.translatable("gui.simplesoundboard.config.global_local_volume"), SoundboardConfig.data.globalLocalVolume, {
                SoundboardConfig.data.globalLocalVolume = it
                SoundboardConfig.save()
            })
        )

        addDrawableChild(
            VolumeSlider(width / 2 - 100, 125, 200, 20, Text.translatable("gui.simplesoundboard.config.global_player_volume"), SoundboardConfig.data.globalPlayerVolume, {
                SoundboardConfig.data.globalPlayerVolume = it
                SoundboardConfig.save()
            })
        )

        val skipAmounts = listOf(5, 10, 15, 30)
        addDrawableChild(
            ButtonWidget.builder(Text.translatable("gui.simplesoundboard.config.skip_amount").append(": ${SoundboardConfig.data.skipAmountSeconds} s")) { button ->
                val currentIndex = skipAmounts.indexOf(SoundboardConfig.data.skipAmountSeconds)
                val nextIndex = if (currentIndex == -1) 0 else (currentIndex + 1) % skipAmounts.size
                SoundboardConfig.data.skipAmountSeconds = skipAmounts[nextIndex]
                SoundboardConfig.save()
                button.message = Text.translatable("gui.simplesoundboard.config.skip_amount").append(": ${SoundboardConfig.data.skipAmountSeconds} s")
            }.position(width / 2 - 100, 150).size(200, 20).build()
        )
    }

    override fun close() {
        client?.setScreen(parent)
    }
}