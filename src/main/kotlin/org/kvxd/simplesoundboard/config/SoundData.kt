package org.kvxd.simplesoundboard.config

import kotlinx.serialization.Serializable

@Serializable
data class SoundData(
    var localVolume: Float = 1.0f,
    var playerVolume: Float = 1.0f,
    var favorite: Boolean = false,
    var keybind: Int = -1,
    var startingPoint: Float = 0.0f,
    var loop: Boolean = false
)