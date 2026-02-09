package org.kvxd.simplesoundboard.gui

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.Element
import net.minecraft.client.gui.Selectable
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.ElementListWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.client.gui.widget.TextWidget
import net.minecraft.client.input.KeyInput
import net.minecraft.client.util.InputUtil
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Util
import org.kvxd.simplesoundboard.SimpleSoundboardClient
import org.kvxd.simplesoundboard.SoundboardAudioSystem
import org.kvxd.simplesoundboard.config.SoundboardConfig
import org.lwjgl.glfw.GLFW
import java.awt.Color
import java.io.File

class SoundboardScreen(
    private val parent: Screen? = null
) : Screen(Text.translatable("gui.simplesoundboard.title")) {

    private val mc = MinecraftClient.getInstance()

    private val bottomPaneHeight = 125
    private val headerHeight = 55

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
        val padding = 10
        val searchFieldWidth = width - 2 * padding
        val contentWidth = width - 2 * padding

        val titleWidget = TextWidget(Text.translatable("gui.simplesoundboard.title").formatted(Formatting.BOLD), textRenderer)
        titleWidget.setPosition(width / 2 - titleWidget.width / 2, 5)
        addDrawableChild(titleWidget)

        queryField = TextFieldWidget(textRenderer, padding, 20, searchFieldWidth, 20, Text.translatable("gui.simplesoundboard.search_hint"))
        queryField.setChangedListener { scanSounds() }
        addDrawableChild(queryField)

        setupTopButtons()

        val detailsY = height - bottomPaneHeight + 5

        detailLabel = TextWidget(Text.translatable("gui.simplesoundboard.select_hint"), textRenderer)
        detailLabel.setPosition(width / 2 - detailLabel.width / 2, detailsY)
        addDrawableChild(detailLabel)

        detailLocalSlider = VolumeSlider(width / 2 - 155, detailsY + 15, 100, 20, Text.translatable("gui.simplesoundboard.volume.local"), 1.0f, {
            updateSelectedVolume(local = it)
        })
        detailLocalSlider.active = false
        addDrawableChild(detailLocalSlider)

        detailPlayerSlider = VolumeSlider(width / 2 - 50, detailsY + 15, 100, 20, Text.translatable("gui.simplesoundboard.volume.player"), 1.0f, {
            updateSelectedVolume(player = it)
        })
        detailPlayerSlider.active = false
        addDrawableChild(detailPlayerSlider)

        detailBindBtn = ButtonWidget.builder(Text.translatable("gui.simplesoundboard.keybind.none")) {
            if (selectedFile != null) {
                isBinding = true
                it.message = Text.translatable("gui.simplesoundboard.keybind.listening").formatted(Formatting.YELLOW)
            }
        }.size(100, 20).position(width / 2 + 55, detailsY + 15).build()
        detailBindBtn.active = false
        addDrawableChild(detailBindBtn)

        timelineSlider = VolumeSlider(width / 2 - 155, detailsY + 40, 310, 20, Text.translatable("gui.simplesoundboard.progress"), 0f, {
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
        timelineSlider.active = false
        addDrawableChild(timelineSlider)

        timeField = TextFieldWidget(textRenderer, width / 2 - 155, detailsY + 65, 60, 20, Text.translatable("gui.simplesoundboard.time_hint"))
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

        loopBtn = ButtonWidget.builder(Text.literal("🔁")) {
            val file = selectedFile ?: return@builder
            val data = SoundboardConfig[file.name]
            data.loop = !data.loop
            SoundboardConfig.save()
            SoundboardAudioSystem.setLooping(file.name, data.loop)
            it.message = Text.literal(if (data.loop) "🔁" else "🔄").formatted(if (data.loop) Formatting.WHITE else Formatting.GRAY)
        }.size(30, 20).position(width / 2 + 20, detailsY + 65).build()
        loopBtn.active = false
        addDrawableChild(loopBtn)

        setStartBtn = ButtonWidget.builder(Text.translatable("gui.simplesoundboard.set_start")) {
            val file = selectedFile ?: return@builder
            val data = SoundboardConfig[file.name]
            data.startingPoint = SoundboardAudioSystem.getProgress(file.name).coerceAtLeast(0f)
            SoundboardConfig.save()
            mc.player?.sendMessage(Text.translatable("message.simplesoundboard.start_point_set", file.name), true)
        }.size(100, 20).position(width / 2 + 55, detailsY + 65).build()
        setStartBtn.active = false
        addDrawableChild(setStartBtn)

        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done")) { close() }
            .size(150, 20)
            .position(width / 2 - 75, height - 23)
            .build())

        val listTop = headerHeight + 14
        val listBottom = height - bottomPaneHeight
        val itemHeight = 22

        resultsList = ResultListWidget(mc, contentWidth, listBottom - listTop, listTop, itemHeight)
        resultsList.setX(padding)
        addDrawableChild(resultsList)

        scanSounds()

        val activeName = SoundboardAudioSystem.getActiveSoundName()
        if (activeName != null) {
            val file = File(SimpleSoundboardClient.soundDir, activeName)
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
            ButtonWidget.builder(Text.translatable("gui.simplesoundboard.stop_all").formatted(Formatting.RED)) {
                SoundboardAudioSystem.stopAll()
            }.size(actionButtonWidth, actionButtonHeight)
                .position(currentX, actionsY).build()
        )
        currentX += actionButtonWidth + buttonSpacing

        addDrawableChild(
            ButtonWidget.builder(Text.translatable("gui.simplesoundboard.refresh")) {
                scanSounds()
            }.size(actionButtonWidth, actionButtonHeight)
                .position(currentX, actionsY).build()
        )
        currentX += actionButtonWidth + buttonSpacing

        addDrawableChild(
            ButtonWidget.builder(Text.translatable("gui.simplesoundboard.folder")) {
                Util.getOperatingSystem().open(SimpleSoundboardClient.soundDir)
            }.size(actionButtonWidth, actionButtonHeight)
                .position(currentX, actionsY).build()
        )
        currentX += actionButtonWidth + buttonSpacing

        addDrawableChild(
            ButtonWidget.builder(Text.translatable("gui.simplesoundboard.config")) {
                mc.setScreen(SoundboardConfigScreen(this))
            }.size(actionButtonWidth, actionButtonHeight)
                .position(currentX, actionsY).build()
        )
        currentX += actionButtonWidth + buttonSpacing

        addDrawableChild(
            ButtonWidget.builder(Text.translatable("gui.simplesoundboard.youtube")) {
                mc.setScreen(YtDlpScreen(this))
            }.size(actionButtonWidth, actionButtonHeight)
                .position(currentX, actionsY).build()
        )
    }

    private fun scanSounds() {
        val query = queryField.text.trim().lowercase()
        val allFiles = SimpleSoundboardClient.soundDir.listFiles { _, name -> name.endsWith(".mp3") } ?: emptyArray()

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
            detailLabel.message = Text.translatable("gui.simplesoundboard.select_hint").formatted(Formatting.GRAY)
            detailLabel.x = width / 2 - textRenderer.getWidth(detailLabel.message) / 2

            detailLocalSlider.active = false
            detailPlayerSlider.active = false
            detailBindBtn.active = false
            detailBindBtn.message = Text.translatable("gui.simplesoundboard.keybind.none")

            timelineSlider.active = false
            timeField.active = false
            pauseBtn.active = false
            setStartBtn.active = false
            backBtn.active = false
            forwardBtn.active = false
            loopBtn.active = false
        } else {
            val data = SoundboardConfig[file.name]

            detailLabel.message = Text.translatable("gui.simplesoundboard.settings_for", file.name).formatted(Formatting.YELLOW)
            detailLabel.x = width / 2 - textRenderer.getWidth(detailLabel.message) / 2

            detailLocalSlider.active = true
            detailLocalSlider.setValueQuietly(data.localVolume)

            detailPlayerSlider.active = true
            detailPlayerSlider.setValueQuietly(data.playerVolume)

            detailBindBtn.active = true
            updateBindButtonText(data.keybind)

            timelineSlider.active = true
            timelineSlider.setValueQuietly(SoundboardAudioSystem.getProgress(file.name))

            timeField.active = true
            timeField.text = formatTime(SoundboardAudioSystem.getTimeSeconds(file.name))

            pauseBtn.active = true
            pauseBtn.message = if (SoundboardAudioSystem.isPaused(file.name)) Text.literal("▶") else Text.literal("⏸")

            setStartBtn.active = true
            backBtn.active = true
            forwardBtn.active = true
            loopBtn.active = true
            loopBtn.message = Text.literal(if (data.loop) "🔁" else "🔄").formatted(if (data.loop) Formatting.WHITE else Formatting.GRAY)
        }
    }

    private fun updateSelectedVolume(local: Float? = null, player: Float? = null) {
        val file = selectedFile ?: return
        val data = SoundboardConfig[file.name]

        if (local != null) data.localVolume = local
        if (player != null) data.playerVolume = player

        SoundboardConfig.save()
        SoundboardAudioSystem.setVolume(file.name, data.localVolume, data.playerVolume)
    }

    private fun updateBindButtonText(keyCode: Int) {
        val keyName =
            if (keyCode > 0)
                InputUtil.fromKeyCode(KeyInput(keyCode, 0, 0)).localizedText
            else
                Text.translatable("gui.simplesoundboard.keybind.none")

        detailBindBtn.message = Text.translatable("gui.simplesoundboard.keybind.prefix").append(keyName)
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(context, mouseX, mouseY, delta)

        val lineY = height - bottomPaneHeight
        context.fill(10, lineY, width - 10, lineY + 1, Color.GRAY.rgb)
    }

    override fun close() {
        SoundboardConfig.save()
        mc.setScreen(parent)
    }

    override fun keyPressed(input: KeyInput): Boolean {
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
            timelineSlider.active = isPlaying
            timeField.active = isPlaying
            pauseBtn.active = isPlaying
            setStartBtn.active = isPlaying
            backBtn.active = isPlaying
            forwardBtn.active = isPlaying
            loopBtn.active = isPlaying

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
            timelineSlider.active = false
            timeField.active = false
            pauseBtn.active = false
            setStartBtn.active = false
            backBtn.active = false
            forwardBtn.active = false
            loopBtn.active = false
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

    private inner class ResultListWidget(
        client: MinecraftClient, width: Int, height: Int, y: Int, itemHeight: Int
    ) : ElementListWidget<ResultListWidget.Entry>(client, width, height, y, itemHeight) {

        fun setResults(results: List<File>) {
            clearEntries()
            results.forEach { addEntry(Entry(it)) }
            scrollY = 0.0
        }

        override fun getRowWidth(): Int = width - 20

        inner class Entry(val file: File) : ElementListWidget.Entry<Entry>() {

            private val playBtn: ButtonWidget
            private val favBtn: ButtonWidget
            private val elements = mutableListOf<Element>()
            private val selectables = mutableListOf<Selectable>()

            init {
                val data = SoundboardConfig[file.name]

                favBtn = ButtonWidget.builder(
                    Text.literal(if (data.favorite) "★" else "☆")
                        .formatted(if (data.favorite) Formatting.GOLD else Formatting.GRAY)
                ) {
                    data.favorite = !data.favorite
                    SoundboardConfig.save()
                    scanSounds()
                }.size(20, 20).build()

                playBtn = ButtonWidget.builder(Text.translatable("gui.simplesoundboard.play")) {
                    if (SoundboardAudioSystem.isPlaying(file.name)) {
                        SoundboardAudioSystem.stop(file.name)
                    } else {
                        val currentData = SoundboardConfig[file.name]
                        SoundboardAudioSystem.playFile(file, currentData.localVolume, currentData.playerVolume)
                    }
                }.size(40, 20).build()

                elements.add(favBtn)
                elements.add(playBtn)
                selectables.add(favBtn)
                selectables.add(playBtn)
            }

            override fun render(context: DrawContext, mouseX: Int, mouseY: Int, hovered: Boolean, deltaTicks: Float) {
                val isPlaying = SoundboardAudioSystem.isPlaying(file.name)
                playBtn.message =
                    if (isPlaying) Text.translatable("gui.simplesoundboard.stop").formatted(Formatting.RED) else Text.translatable("gui.simplesoundboard.play")

                if (selectedFile?.name == file.name) {
                    context.fill(x, y + 1, x + width, y + height - 1, 0x33FFFFFF)
                }

                val textY = y + (height - textRenderer.fontHeight) / 2 + 1
                var nameText = file.nameWithoutExtension

                if (textRenderer.getWidth(nameText) > width - 70) {
                    nameText = textRenderer.trimToWidth(nameText, width - 75) + "..."
                }

                val textColor = if (isPlaying) Color.YELLOW.rgb else Color.WHITE.rgb
                context.drawText(textRenderer, nameText, x + 25, textY, textColor, true)

                favBtn.x = x
                favBtn.y = y + (height - 20) / 2
                favBtn.render(context, mouseX, mouseY, deltaTicks)

                playBtn.x = x + width - playBtn.width
                playBtn.y = y + (height - 20) / 2
                playBtn.render(context, mouseX, mouseY, deltaTicks)
            }

            override fun mouseClicked(click: Click, doubled: Boolean): Boolean {
                if (favBtn.mouseClicked(click, doubled)) return true
                if (playBtn.mouseClicked(click, doubled)) return true

                selectSound(file)
                return true
            }

            override fun children() = elements
            override fun selectableChildren() = selectables
        }
    }
}
