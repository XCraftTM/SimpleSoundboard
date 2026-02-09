package org.kvxd.simplesoundboard.config

import kotlinx.serialization.Serializable

@Serializable
data class ConfigData(
    var playLocally: Boolean = true,
    var playWhileMuted: Boolean = true,
    var globalLocalVolume: Float = 1.0f,
    var globalPlayerVolume: Float = 1.0f,
    var skipAmountSeconds: Int = 5,
    var syncAudio: Boolean = false,
    val sounds: MutableMap<String, SoundData> = mutableMapOf()
)