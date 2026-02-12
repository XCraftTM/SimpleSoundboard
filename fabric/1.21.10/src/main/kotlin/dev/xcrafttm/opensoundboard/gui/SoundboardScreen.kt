package dev.xcrafttm.opensoundboard.gui

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.client.gui.widget.TextWidget
import net.minecraft.client.input.KeyInput
import net.minecraft.client.util.InputUtil
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Util
import dev.xcrafttm.opensoundboard.OpenSoundboardClient
import dev.xcrafttm.opensoundboard.SoundboardAudioSystem
import dev.xcrafttm.opensoundboard.config.SoundboardConfig
import dev.xcrafttm.opensoundboard.gui.components.ResultListWidget
import dev.xcrafttm.opensoundboard.gui.components.VolumeSlider
import org.lwjgl.glfw.GLFW
import java.io.File

class SoundboardScreen(
    private val parent: Screen? = null
) : Screen(Text.translatable("gui.opensoundboard.title")) {

    private val mc = MinecraftClient.getInstance()

    private val bottomPaneHeight = 120
    private val headerHeight = 50

    private lateinit var queryField: TextFieldWidget
    private lateinit var resultsList: ResultListWidget

    private lateinit var detailLocalSlider: VolumeSlider
    private lateinit var detailPlayerSlider: VolumeSlider
    private lateinit var detailBindBtn: ButtonWidget
    private lateinit var detailLabel: TextWidget

    private lateinit var timelineSlider: VolumeSlider
    private lateinit var timeField: TextFieldWidget
    private lateinit var pauseBtn: ButtonWidget
    private lateinit var setStartBtn: ButtonWidget
    private lateinit var backBtn: ButtonWidget
    private lateinit var forwardBtn: ButtonWidget
    private lateinit var loopBtn: ButtonWidget

    private var selectedFile: File? = null
    private var isBinding = false
    private var results: List<File> = emptyList()

    override fun init() {
        val actionButtonWidth = 80
        val buttonSpacing = 5
        val totalActionWidth = (actionButtonWidth * 5) + (buttonSpacing * 4)

        val searchFieldWidth = totalActionWidth
        val contentWidth = totalActionWidth

        val titleWidget = TextWidget(Text.translatable("gui.opensoundboard.title").formatted(Formatting.BOLD), textRenderer)
        titleWidget.setPosition(width / 2 - titleWidget.width / 2, 5)
        addDrawableChild(titleWidget)

        queryField = TextFieldWidget(textRenderer, width / 2 - searchFieldWidth / 2, 20, searchFieldWidth, 20, Text.translatable("gui.opensoundboard.search_hint"))
        queryField.setChangedListener { scanSounds() }
        addDrawableChild(queryField)

        setupTopButtons()

        val detailsY = height - bottomPaneHeight + 5

        detailLabel = TextWidget(Text.translatable("gui.opensoundboard.select_hint"), textRenderer)
        detailLabel.setPosition(width / 2 - detailLabel.width / 2, detailsY)
        addDrawableChild(detailLabel)

        if (SoundboardConfig.data.syncAudio) {
            detailLocalSlider = VolumeSlider(width / 2 - 155, detailsY + 15, 205, 20, Text.translatable("gui.opensoundboard.volume.synced"), 1.0f, {
                updateSelectedVolume(local = it, player = it)
            })
            detailLocalSlider.setActive(false)
            addDrawableChild(detailLocalSlider)

            // Still need to initialize player slider to avoid lateinit initialization errors, but we won't show/use it
            detailPlayerSlider = VolumeSlider(0, 0, 0, 0, Text.empty(), 0f, {})
            detailPlayerSlider.visible = false
        } else {
            detailLocalSlider = VolumeSlider(width / 2 - 155, detailsY + 15, 100, 20, Text.translatable("gui.opensoundboard.volume.local"), 1.0f, {
                updateSelectedVolume(local = it)
            })
            detailLocalSlider.setActive(false)
            addDrawableChild(detailLocalSlider)

            detailPlayerSlider = VolumeSlider(width / 2 - 50, detailsY + 15, 100, 20, Text.translatable("gui.opensoundboard.volume.player"), 1.0f, {
                updateSelectedVolume(player = it)
            })
            detailPlayerSlider.setActive(false)
            addDrawableChild(detailPlayerSlider)
        }

        detailBindBtn = ButtonWidget.builder(Text.translatable("gui.opensoundboard.keybind.none")) {
            if (selectedFile != null) {
                isBinding = true
                it.message = Text.translatable("gui.opensoundboard.keybind.listening").formatted(Formatting.YELLOW)
            }
        }.size(100, 20).position(width / 2 + 55, detailsY + 15).build()
        detailBindBtn.active = false
        addDrawableChild(detailBindBtn)

        timelineSlider = VolumeSlider(width / 2 - 155, detailsY + 40, 310, 20, Text.translatable("gui.opensoundboard.progress"), 0f, {
            val file = selectedFile
            if (file != null && SoundboardAudioSystem.isPlaying(file.name)) {
                SoundboardAudioSystem.setCursor(file.name, it)
            }
        }, { progress ->
            val file = selectedFile
            if (file != null && SoundboardAudioSystem.isPlaying(file.name)) {
                val duration = SoundboardAudioSystem.getDurationSeconds(file.name)
                val current = (progress * duration).toInt()
                Text.literal("${formatTime(current)} / ${formatTime(duration)}")
            } else {
                Text.literal("0:00 / 0:00")
            }
        })
        timelineSlider.setActive(false)
        addDrawableChild(timelineSlider)

        timeField = TextFieldWidget(textRenderer, width / 2 - 155, detailsY + 65, 60, 20, Text.translatable("gui.opensoundboard.time_hint"))
        timeField.setChangedListener {
            if (timeField.isFocused) {
                val file = selectedFile ?: return@setChangedListener
                if (SoundboardAudioSystem.isPlaying(file.name)) {
                    val seconds = parseTime(it)
                    if (seconds != -1) {
                        val duration = SoundboardAudioSystem.getDurationSeconds(file.name)
                        if (duration > 0) {
                            SoundboardAudioSystem.setCursor(file.name, (seconds.toFloat() / duration.toFloat()).coerceIn(0f, 1f))
                        }
                    }
                }
            }
        }
        timeField.active = false
        addDrawableChild(timeField)

        backBtn = ButtonWidget.builder(Text.literal("⏪")) {
            val file = selectedFile ?: return@builder
            SoundboardAudioSystem.skip(file.name, -SoundboardConfig.data.skipAmountSeconds)
        }.size(30, 20).position(width / 2 - 85, detailsY + 65).build()
        backBtn.active = false
        addDrawableChild(backBtn)

        pauseBtn = ButtonWidget.builder(Text.literal("⏸")) {
            val file = selectedFile ?: return@builder
            if (SoundboardAudioSystem.isPaused(file.name)) {
                SoundboardAudioSystem.resume(file.name)
            } else {
                SoundboardAudioSystem.pause(file.name)
            }
        }.size(30, 20).position(width / 2 - 50, detailsY + 65).build()
        pauseBtn.active = false
        addDrawableChild(pauseBtn)

        forwardBtn = ButtonWidget.builder(Text.literal("⏩")) {
            val file = selectedFile ?: return@builder
            SoundboardAudioSystem.skip(file.name, SoundboardConfig.data.skipAmountSeconds)
        }.size(30, 20).position(width / 2 - 15, detailsY + 65).build()
        forwardBtn.active = false
        addDrawableChild(forwardBtn)

        loopBtn = ButtonWidget.builder(Text.literal(if (SoundboardConfig.data.loopAll) "🔁" else "🔄")) {
            SoundboardConfig.data.loopAll = !SoundboardConfig.data.loopAll
            SoundboardConfig.save()
            SoundboardAudioSystem.setGlobalLooping(SoundboardConfig.data.loopAll)
            it.message = Text.literal(if (SoundboardConfig.data.loopAll) "🔁" else "🔄").formatted(if (SoundboardConfig.data.loopAll) Formatting.WHITE else Formatting.GRAY)
        }.size(30, 20).position(width / 2 + 20, detailsY + 65).build()
        loopBtn.active = true
        addDrawableChild(loopBtn)

        setStartBtn = ButtonWidget.builder(Text.translatable("gui.opensoundboard.set_start")) {
            val file = selectedFile ?: return@builder
            val data = SoundboardConfig[file.name]
            data.startingPoint = SoundboardAudioSystem.getProgress(file.name).coerceAtLeast(0f)
            SoundboardConfig.save()
            mc.player?.sendMessage(Text.translatable("message.opensoundboard.start_point_set", file.name), true)
        }.size(100, 20).position(width / 2 + 55, detailsY + 65).build()
        setStartBtn.active = false
        addDrawableChild(setStartBtn)

        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done")) { close() }
            .size(150, 20)
            .position(width / 2 - 75, height - 23)
            .build())

        val listTop = headerHeight + 25
        val listBottom = height - bottomPaneHeight
        val itemHeight = 22
        val startX = width / 2 - contentWidth / 2

        resultsList = ResultListWidget(
            mc,
            startX,
            contentWidth,
            listBottom - listTop,
            listTop,
            itemHeight,
            onSelect = { selectSound(it) },
            onRefresh = { scanSounds() },
            getSelectedFile = { selectedFile }
        )
        addDrawableChild(resultsList)

        scanSounds()

        val activeName = SoundboardAudioSystem.getActiveSoundName()
        if (activeName != null) {
            val file = File(OpenSoundboardClient.soundDir, activeName)
            if (file.exists()) {
                selectSound(file)
                val entries = resultsList.children()
                val entry = entries.find { it.file.name == activeName }
                if (entry != null) {
                    resultsList.scrollY = (entries.indexOf(entry) * 22.0)
                }
            }
        }

        setInitialFocus(queryField)
    }

    private fun setupTopButtons() {
        val actionButtonWidth = 80
        val actionButtonHeight = 20
        val buttonSpacing = 5
        val actionsY = 45

        val totalActionWidth = (actionButtonWidth * 5) + (buttonSpacing * 4)
        var currentX = width / 2 - totalActionWidth / 2

        addDrawableChild(
            ButtonWidget.builder(Text.translatable("gui.opensoundboard.stop_all").formatted(Formatting.RED)) {
                SoundboardAudioSystem.stopAll()
            }.size(actionButtonWidth, actionButtonHeight)
                .position(currentX, actionsY).build()
        )
        currentX += actionButtonWidth + buttonSpacing

        addDrawableChild(
            ButtonWidget.builder(Text.translatable("gui.opensoundboard.refresh")) {
                scanSounds()
            }.size(actionButtonWidth, actionButtonHeight)
                .position(currentX, actionsY).build()
        )
        currentX += actionButtonWidth + buttonSpacing

        addDrawableChild(
            ButtonWidget.builder(Text.translatable("gui.opensoundboard.folder")) {
                Util.getOperatingSystem().open(OpenSoundboardClient.soundDir)
            }.size(actionButtonWidth, actionButtonHeight)
                .position(currentX, actionsY).build()
        )
        currentX += actionButtonWidth + buttonSpacing

        addDrawableChild(
            ButtonWidget.builder(Text.translatable("gui.opensoundboard.config")) {
                mc.setScreen(SoundboardConfigScreen(this))
            }.size(actionButtonWidth, actionButtonHeight)
                .position(currentX, actionsY).build()
        )
        currentX += actionButtonWidth + buttonSpacing

        addDrawableChild(
            ButtonWidget.builder(Text.translatable("gui.opensoundboard.youtube")) {
                mc.setScreen(YtDlpScreen(this))
            }.size(actionButtonWidth, actionButtonHeight)
                .position(currentX, actionsY).build()
        )
    }

    private fun scanSounds() {
        val query = queryField.text.trim().lowercase()
        val allFiles = OpenSoundboardClient.soundDir.listFiles { _, name -> name.endsWith(".mp3") } ?: emptyArray()

        results = allFiles.filter { it.name.lowercase().contains(query) }
            .sortedWith(compareByDescending<File> { SoundboardConfig[it.name].favorite }
                .thenBy { it.name })

        resultsList.setResults(results)

        if (selectedFile != null && results.none { it.name == selectedFile!!.name }) {
            selectSound(null)
        }
    }

    fun selectSound(file: File?) {
        this.selectedFile = file
        isBinding = false

        if (file == null) {
            detailLabel.message = Text.translatable("gui.opensoundboard.select_hint").formatted(Formatting.GRAY)
            detailLabel.x = width / 2 - textRenderer.getWidth(detailLabel.message) / 2

            detailLocalSlider.setActive(false)
            detailPlayerSlider.setActive(false)
            detailBindBtn.active = false
            detailBindBtn.message = Text.translatable("gui.opensoundboard.keybind.none")

            timelineSlider.setActive(false)
            timeField.active = false
            pauseBtn.active = false
            setStartBtn.active = false
            backBtn.active = false
            forwardBtn.active = false
        } else {
            val data = SoundboardConfig[file.name]

            detailLabel.message = Text.translatable("gui.opensoundboard.settings_for", file.name).formatted(Formatting.YELLOW)
            detailLabel.x = width / 2 - textRenderer.getWidth(detailLabel.message) / 2

            detailLocalSlider.setActive(true)
            detailLocalSlider.setValueQuietly(data.localVolume)

            detailPlayerSlider.setActive(true)
            detailPlayerSlider.setValueQuietly(data.playerVolume)

            detailBindBtn.active = true
            updateBindButtonText(data.keybind)

            timelineSlider.setActive(true)
            timelineSlider.setValueQuietly(SoundboardAudioSystem.getProgress(file.name))

            timeField.active = true
            timeField.text = formatTime(SoundboardAudioSystem.getTimeSeconds(file.name))

            pauseBtn.active = true
            pauseBtn.message = if (SoundboardAudioSystem.isPaused(file.name)) Text.literal("▶") else Text.literal("⏸")

            setStartBtn.active = true
            backBtn.active = true
            forwardBtn.active = true
            // loopBtn state is now global and always managed
        }
    }

    private fun updateSelectedVolume(local: Float? = null, player: Float? = null) {
        val file = selectedFile ?: return
        val data = SoundboardConfig[file.name]

        if (SoundboardConfig.data.syncAudio) {
            val vol = local ?: player ?: 1.0f
            data.localVolume = vol
            data.playerVolume = vol
        } else {
            if (local != null) data.localVolume = local
            if (player != null) data.playerVolume = player
        }

        SoundboardConfig.save()
        SoundboardAudioSystem.setVolume(file.name, data.localVolume, data.playerVolume)
    }

    private fun updateBindButtonText(keyCode: Int) {
        val keyName =
            if (keyCode > 0)
                InputUtil.fromKeyCode(KeyInput(keyCode, 0, 0)).localizedText
            else
                Text.translatable("gui.opensoundboard.keybind.none")

        detailBindBtn.message = Text.translatable("gui.opensoundboard.keybind.prefix").append(keyName)
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(context, mouseX, mouseY, delta)
    }

    override fun close() {
        SoundboardConfig.save()
        mc.setScreen(parent)
    }

    override fun keyPressed(input: KeyInput): Boolean {
        if (queryField.isFocused && (input.key == GLFW.GLFW_KEY_ENTER || input.key == GLFW.GLFW_KEY_KP_ENTER)) {
            if (results.isNotEmpty()) {
                val file = results[0]
                selectSound(file)
                val data = SoundboardConfig[file.name]

                if (SoundboardAudioSystem.isPlaying(file.name)) {
                    SoundboardAudioSystem.stop(file.name)
                } else {
                    SoundboardAudioSystem.playFile(file, data.localVolume, data.playerVolume)
                }
                return true
            }
        }

        if (isBinding && selectedFile != null) {
            val file = selectedFile!!.name
            val data = SoundboardConfig[file]

            if (input.key == GLFW.GLFW_KEY_ESCAPE) {
                data.keybind = -1
            } else {
                data.keybind = input.key
            }

            SoundboardConfig.save()
            updateBindButtonText(data.keybind)
            isBinding = false

            return true
        }

        if (input.key == GLFW.GLFW_KEY_ESCAPE) {
            close()
            return true
        }

        return super.keyPressed(input)
    }

    override fun tick() {
        super.tick()
        val file = selectedFile
        if (file != null) {
            val isPlaying = SoundboardAudioSystem.isPlaying(file.name)
            timelineSlider.setActive(isPlaying)
            timeField.active = isPlaying
            pauseBtn.active = isPlaying
            setStartBtn.active = isPlaying
            backBtn.active = isPlaying
            forwardBtn.active = isPlaying

            if (isPlaying) {
                val mouseX = mc.mouse.x * width.toDouble() / mc.window.width.toDouble()
                val mouseY = mc.mouse.y * height.toDouble() / mc.window.height.toDouble()
                if (!timelineSlider.isMouseOver(mouseX, mouseY)) {
                    timelineSlider.setValueQuietly(SoundboardAudioSystem.getProgress(file.name))
                }
                if (!timeField.isFocused) {
                    timeField.text = formatTime(SoundboardAudioSystem.getTimeSeconds(file.name))
                }
                pauseBtn.message = if (SoundboardAudioSystem.isPaused(file.name)) Text.literal("▶") else Text.literal("⏸")
            } else {
                timelineSlider.setValueQuietly(0f)
                if (!timeField.isFocused) timeField.text = "0:00"
            }
        } else {
            timelineSlider.setActive(false)
            timeField.active = false
            pauseBtn.active = false
            setStartBtn.active = false
            backBtn.active = false
            forwardBtn.active = false
        }
    }

    private fun formatTime(seconds: Int): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return "%d:%02d".format(mins, secs)
    }

    private fun parseTime(input: String): Int {
        return try {
            if (input.contains(":")) {
                val parts = input.split(":")
                val mins = parts[0].toIntOrNull() ?: 0
                val secs = parts[1].toIntOrNull() ?: 0
                mins * 60 + secs
            } else {
                input.toIntOrNull() ?: -1
            }
        } catch (_: Exception) {
            -1
        }
    }
}
