package dev.xcrafttm.opensoundboard.config

import kotlinx.serialization.json.Json
import net.fabricmc.loader.api.FabricLoader
import java.io.File

object SoundboardConfig {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val configFile = File(FabricLoader.getInstance().configDir.toFile(), "opensoundboard.json")

    var data: ConfigData =
        ConfigData()

    init {
        load()
    }

    operator fun get(filename: String): SoundData {
        return data.sounds.computeIfAbsent(filename) { SoundData() }
    }

    fun save() {
        try {
            if (!configFile.parentFile.exists()) configFile.parentFile.mkdirs()

            configFile.writeText(json.encodeToString(data))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun load() {
        if (!configFile.exists()) return
        try {
            data = json.decodeFromString<ConfigData>(configFile.readText())

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

}