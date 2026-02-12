package dev.xcrafttm.opensoundboard.integration

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import net.minecraft.client.gui.screen.Screen
import dev.xcrafttm.opensoundboard.gui.SoundboardConfigScreen

class ModMenuIntegration : ModMenuApi {

    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> {
        return ConfigScreenFactory { parent: Screen? ->
            SoundboardConfigScreen(parent)
        }
    }

}