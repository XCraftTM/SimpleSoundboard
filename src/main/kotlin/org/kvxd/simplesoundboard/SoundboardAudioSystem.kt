package org.kvxd.simplesoundboard

import de.maxhenkel.voicechat.api.VoicechatApi
import de.maxhenkel.voicechat.api.VoicechatClientApi
import de.maxhenkel.voicechat.api.audiochannel.ClientStaticAudioChannel
import de.maxhenkel.voicechat.api.events.ClientVoicechatConnectionEvent
import de.maxhenkel.voicechat.api.events.MergeClientSoundEvent
import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text
import org.kvxd.simplesoundboard.config.SoundboardConfig
import java.io.BufferedInputStream
import java.io.File
import java.nio.file.Files
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.min

object SoundboardAudioSystem {

    private var api: VoicechatApi? = null
    private var clientApi: VoicechatClientApi? = null
    private var localAudioChannel: ClientStaticAudioChannel? = null

    private val activeSounds = ConcurrentLinkedQueue<PlayingSound>()
    private const val FRAME_SIZE = 960

    fun initialize(api: VoicechatApi) {
        this.api = api
    }

    fun onClientConnection(event: ClientVoicechatConnectionEvent) {
        if (event.isConnected) {
            clientApi = event.voicechat
            val category = clientApi!!.volumeCategoryBuilder()
                .setId("soundboard")
                .setName("Soundboard")
                .build()
            clientApi!!.registerClientVolumeCategory(category)

            localAudioChannel = clientApi!!.createStaticAudioChannel(UUID.randomUUID())
            localAudioChannel?.category = category.id
        } else {
            stopAll()
            clientApi = null
            localAudioChannel = null
        }
    }

    fun onMergeSound(event: MergeClientSoundEvent) {
        val api = clientApi ?: return

        if (api.isDisabled || (api.isMuted && !SoundboardConfig.data.playWhileMuted)) {
            if (activeSounds.isNotEmpty()) activeSounds.clear()
            return
        }

        if (activeSounds.isEmpty()) return

        val playLocally = SoundboardConfig.data.playLocally
        var hasAudio = false

        // Use IntArray accumulators to prevent intermediate clipping
        val accumulatorPlayer = IntArray(FRAME_SIZE)
        val accumulatorLocal = IntArray(FRAME_SIZE)

        val iterator = activeSounds.iterator()
        val globalLocal = SoundboardConfig.data.globalLocalVolume
        val globalPlayer = SoundboardConfig.data.globalPlayerVolume

        while (iterator.hasNext()) {
            val sound = iterator.next()

            if (sound.isFinished) {
                iterator.remove()
                continue
            }

            if (sound.isPaused) continue

            hasAudio = true

            val samplesToRead = min(FRAME_SIZE, sound.remaining)
            val pVol = sound.playerVolume * globalPlayer
            val lVol = sound.localVolume * globalLocal

            for (i in 0 until samplesToRead) {
                val rawSample = sound.readNext()
                accumulatorPlayer[i] += (rawSample * pVol).toInt()
                if (playLocally) {
                    accumulatorLocal[i] += (rawSample * lVol).toInt()
                }
            }
        }

        if (hasAudio) {
            val mixedAudioPlayer = ShortArray(FRAME_SIZE)
            val mixedAudioLocal = ShortArray(FRAME_SIZE)

            for (i in 0 until FRAME_SIZE) {
                mixedAudioPlayer[i] = accumulatorPlayer[i].coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                if (playLocally) {
                    mixedAudioLocal[i] = accumulatorLocal[i].coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
            }

            event.mergeAudio(mixedAudioPlayer)

            if (playLocally) {
                localAudioChannel?.play(mixedAudioLocal)
            }
        }
    }

    private fun resample(input: ShortArray, inputRate: Int, outputRate: Int): ShortArray {
        // Cubic Catmull-Rom Interpolation
        val factor = inputRate.toDouble() / outputRate.toDouble()
        val outputSize = (input.size / factor).toInt()
        val output = ShortArray(outputSize)

        for (i in 0 until outputSize) {
            val inputIndex = i * factor
            val index = inputIndex.toInt()
            val fraction = inputIndex - index

            val p0 = if (index > 0) input[index - 1].toDouble() else input[index].toDouble()
            val p1 = input[index].toDouble()
            val p2 = if (index < input.size - 1) input[index + 1].toDouble() else p1
            val p3 = if (index < input.size - 2) input[index + 2].toDouble() else p2

            val a = -0.5 * p0 + 1.5 * p1 - 1.5 * p2 + 0.5 * p3
            val b = p0 - 2.5 * p1 + 2.0 * p2 - 0.5 * p3
            val c = -0.5 * p0 + 0.5 * p2
            val d = p1

            val sample = (a * fraction * fraction * fraction) + (b * fraction * fraction) + (c * fraction) + d

            output[i] = sample.coerceIn(Short.MIN_VALUE.toDouble(), Short.MAX_VALUE.toDouble()).toInt().toShort()
        }
        return output
    }

    fun playFile(file: File, localVol: Float, playerVol: Float) {
        val client = MinecraftClient.getInstance()
        val api = clientApi

        if (api == null) {
            client.player?.sendMessage(Text.translatable("message.simplesoundboard.vc_not_connected"), true)
            return
        }

        if (api.isMuted && !SoundboardConfig.data.playWhileMuted) {
            client.player?.sendMessage(Text.translatable("message.simplesoundboard.muted_error"), true)
            return
        }

        if (SoundboardConfig.data.singleSongAtATime) {
            stopAll()
        }

        CompletableFuture.runAsync {
            try {
                val pcmData = decodeMp3(file)
                if (pcmData != null && pcmData.isNotEmpty()) {
                    val sound = PlayingSound(file.name, pcmData, localVol, playerVol)
                    val data = SoundboardConfig[file.name]
                    if (data.startingPoint > 0f) {
                        sound.setCursor(data.startingPoint)
                    }
                    sound.isLooping = SoundboardConfig.data.loopAll
                    activeSounds.add(sound)
                } else {
                    client.execute {
                        client.player?.sendMessage(Text.translatable("message.simplesoundboard.decode_failed", file.name), false)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun isPlaying(file: String): Boolean {
        return activeSounds.any { it.name == file && !it.isFinished }
    }

    fun stop(file: String) {
        activeSounds.removeIf { it.name == file }
    }

    fun pause(file: String) {
        activeSounds.forEach { if (it.name == file) it.isPaused = true }
    }

    fun resume(file: String) {
        activeSounds.forEach { if (it.name == file) it.isPaused = false }
    }

    fun setGlobalLooping(looping: Boolean) {
        activeSounds.forEach { it.isLooping = looping }
    }

    fun setCursor(file: String, progress: Float) {
        activeSounds.forEach { if (it.name == file) it.setCursor(progress) }
    }

    fun skip(file: String, seconds: Int) {
        activeSounds.forEach { if (it.name == file) it.skip(seconds) }
    }

    fun getProgress(file: String): Float {
        return activeSounds.find { it.name == file }?.progress ?: -1f
    }

    fun getTimeSeconds(file: String): Int {
        return activeSounds.find { it.name == file }?.timeSeconds ?: 0
    }

    fun getDurationSeconds(file: String): Int {
        return activeSounds.find { it.name == file }?.durationSeconds ?: 0
    }

    fun isPaused(file: String): Boolean {
        return activeSounds.any { it.name == file && it.isPaused }
    }

    fun getActiveSoundName(): String? {
        return activeSounds.firstOrNull { !it.isFinished }?.name
    }

    fun setVolume(file: String, localVol: Float, playerVol: Float) {
        for (sound in activeSounds) {
            if (sound.name == file) {
                sound.localVolume = localVol
                sound.playerVolume = playerVol
            }
        }
    }

    fun stopAll() {
        activeSounds.clear()
    }

    private fun decodeMp3(file: File): ShortArray? {
        val currentApi = api ?: return null

        return try {
            BufferedInputStream(Files.newInputStream(file.toPath())).use { stream ->
                val decoder = currentApi.createMp3Decoder(stream) ?: return null
                var rawPcm = decoder.decode()
                val format = decoder.audioFormat

                if (format.channels == 2) {
                    rawPcm = stereoToMono(rawPcm)
                }

                if (format.sampleRate != 48000f) {
                    rawPcm = resample(rawPcm, format.sampleRate.toInt(), 48000)
                }
                rawPcm
            }
        } catch (e: Exception) {
            System.err.println("Error decoding ${file.name}: ${e.message}")
            null
        }
    }



    private fun stereoToMono(stereo: ShortArray): ShortArray {
        val mono = ShortArray(stereo.size / 2)
        for (i in mono.indices) {
            val left = stereo[i * 2].toInt()
            val right = stereo[i * 2 + 1].toInt()
            mono[i] = ((left + right) / 2).toShort()
        }
        return mono
    }

    private class PlayingSound(
        val name: String,
        private val samples: ShortArray,
        @Volatile var localVolume: Float,
        @Volatile var playerVolume: Float
    ) {

        private var cursor = 0
        @Volatile var isPaused = false
        @Volatile var isLooping = false

        val isFinished: Boolean
            get() = !isLooping && cursor >= samples.size

        val remaining: Int
            get() = if (isLooping) FRAME_SIZE else samples.size - cursor

        val progress: Float
            get() = if (samples.isEmpty()) 0f else cursor.toFloat() / samples.size.toFloat()

        val timeSeconds: Int
            get() = cursor / 48000

        val durationSeconds: Int
            get() = samples.size / 48000

        fun setCursor(progress: Float) {
            cursor = (progress * samples.size).toInt().coerceIn(0, samples.size)
        }

        fun skip(seconds: Int) {
            val sampleDelta = seconds * 48000
            cursor = (cursor + sampleDelta).coerceIn(0, samples.size)
        }

        fun readNext(): Short {
            if (cursor >= samples.size) {
                if (isLooping) {
                    cursor = 0
                } else {
                    return 0
                }
            }
            return if (cursor < samples.size) samples[cursor++] else 0
        }
    }
}