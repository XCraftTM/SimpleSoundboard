package dev.xcrafttm.opensoundboard

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.MinecraftClient
import net.minecraft.util.Identifier
import dev.xcrafttm.opensoundboard.config.SoundboardConfig
import dev.xcrafttm.opensoundboard.gui.SoundboardScreen
import net.minecraft.client.option.KeyBinding
import org.lwjgl.glfw.GLFW
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

class OpenSoundboardClient : ClientModInitializer {

    companion object {

        const val MOD_ID = "opensoundboard"
        val LOGGER: Logger? = LoggerFactory.getLogger(MOD_ID)

        val KEY_CATEGORY: String = Identifier.of(MOD_ID, "main").toString()

        lateinit var OPEN_GUI_KEY: KeyBinding
        lateinit var STOP_ALL_KEY: KeyBinding

        private val pressedKeys = mutableSetOf<Int>()

        private val rootDir = File(FabricLoader.getInstance().gameDir.toFile(), "soundboard")
        val soundDir = File(rootDir, "sounds")
        val modDir = File(rootDir, "bin")
    }

    override fun onInitializeClient() {
        OPEN_GUI_KEY = KeyBindingHelper.registerKeyBinding(
            KeyBinding(
                "key.$MOD_ID.open",
                GLFW.GLFW_KEY_X,
                KEY_CATEGORY
            )
        )

        STOP_ALL_KEY = KeyBindingHelper.registerKeyBinding(
            KeyBinding(
                "key.$MOD_ID.stop_all",
                GLFW.GLFW_KEY_K,
                KEY_CATEGORY
            )
        )

        if (!soundDir.exists())
            soundDir.mkdirs()

        if (!modDir.exists())
            modDir.mkdirs()

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (client.player == null) return@register

            if (OPEN_GUI_KEY.wasPressed()) {
                client.setScreen(SoundboardScreen())
            }

            if (STOP_ALL_KEY.wasPressed()) {
                SoundboardAudioSystem.stopAll()
            }

            if (client.currentScreen == null) {
                handleSoundKeybinds(client)
            }
        }

        ClientLifecycleEvents.CLIENT_STOPPING.register {
            SoundboardConfig.save()
        }
    }

    private fun handleSoundKeybinds(client: MinecraftClient) {
        for ((filename, data) in SoundboardConfig.data.sounds) {
            val keyCode = data.keybind
            if (keyCode <= 0 || keyCode == GLFW.GLFW_KEY_ESCAPE) continue

            val isPressed = GLFW.glfwGetKey(client.window.handle, keyCode) == GLFW.GLFW_PRESS
            val wasPressed = pressedKeys.contains(keyCode)

            if (isPressed && !wasPressed) {
                pressedKeys.add(keyCode)

                val file = File(soundDir, filename)
                if (file.exists()) {
                    if (SoundboardAudioSystem.isPlaying(filename)) {
                        SoundboardAudioSystem.stop(filename)
                    } else {
                        SoundboardAudioSystem.playFile(file, data.localVolume, data.playerVolume)
                    }
                }
            } else if (!isPressed && wasPressed) {
                pressedKeys.remove(keyCode)
            }
        }
    }
}
