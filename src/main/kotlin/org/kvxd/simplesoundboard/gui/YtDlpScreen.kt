package org.kvxd.simplesoundboard.gui

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.Element
import net.minecraft.client.gui.Selectable
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.CyclingButtonWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.client.gui.widget.TextWidget
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import org.kvxd.simplesoundboard.SimpleSoundboardClient
import org.kvxd.simplesoundboard.YtDlpManager
import org.kvxd.simplesoundboard.gui.components.LogListWidget
import java.awt.Color
import java.util.concurrent.CompletableFuture

class YtDlpScreen(private val parent: Screen?) : Screen(Text.translatable("gui.simplesoundboard.youtube.title")) {

    private lateinit var urlField: TextFieldWidget
    private lateinit var audioToggle: CyclingButtonWidget<Boolean>
    private lateinit var downloadBtn: ButtonWidget

    private lateinit var statusLabel: TextWidget
    private lateinit var logList: LogListWidget

    private var progress: Int = 0
    private var currentProcess: Process? = null

    override fun init() {
        val actionButtonWidth = 80
        val buttonSpacing = 5
        val totalActionWidth = (actionButtonWidth * 5) + (buttonSpacing * 4)
        val contentWidth = totalActionWidth
        val startX = width / 2 - contentWidth / 2

        urlField =
            TextFieldWidget(textRenderer, startX, 30, contentWidth, 20, Text.translatable("gui.simplesoundboard.youtube.url_hint"))
        urlField.setPlaceholder(Text.translatable("gui.simplesoundboard.youtube.url_hint"))
        urlField.setMaxLength(1024)
        addDrawableChild(urlField)

        audioToggle = CyclingButtonWidget.onOffBuilder(true)
            .build(startX, 60, 150, 20, Text.translatable("gui.simplesoundboard.youtube.audio_only")) { _, _ -> }
        addDrawableChild(audioToggle)

        downloadBtn = ButtonWidget.builder(Text.translatable("gui.simplesoundboard.youtube.download")) {
            if (currentProcess != null) {
                cancelDownload()
                return@builder
            }

            val url = urlField.text.trim()
            if (url.isBlank()) {
                client?.player?.sendMessage(
                    Text.translatable("message.simplesoundboard.youtube.provide_url").formatted(Formatting.RED),
                    false
                )
                return@builder
            }
            startDownload(url, audioToggle.value)
        }.size(120, 20).position(startX + contentWidth - 120, 60).build()
        addDrawableChild(downloadBtn)

        addDrawableChild(
            ButtonWidget.builder(Text.translatable("gui.done")) { close() }
                .size(150, 20)
                .position(width / 2 - 75, height - 23)
                .build()
        )

        statusLabel = TextWidget(Text.literal(" "), textRenderer)
        statusLabel.setPosition(startX, 85)
        addDrawableChild(statusLabel)

        logList = LogListWidget(client!!, startX, contentWidth, height - 155, 100, 12)
        addDrawableChild(logList)

        logList.addLine("> Waiting for Command...")
    }

    private fun cancelDownload() {
        if (currentProcess != null) {
            logList.addLine("> Cancelling download...")
            currentProcess?.destroy()
            currentProcess = null
            statusLabel.message = Text.translatable("message.simplesoundboard.youtube.cancelled").formatted(Formatting.YELLOW)
        }
    }

    private fun startDownload(url: String, audioOnly: Boolean) {
        statusLabel.message = Text.translatable("message.simplesoundboard.youtube.downloading")
        progress = 0
        logList.clearLogs()
        logList.addLine("> Starting download...")

        CompletableFuture.runAsync {
            try {
                val result = YtDlpManager.downloadUrlIntoSoundDir(
                    url,
                    audioOnly,
                    onProgress = { line ->
                        val p = extractProgress(line)

                        client?.execute {
                            if (p != null) {
                                progress = p
                            }
                            logList.addLine(line)
                        }
                    },
                    onProcessStart = { proc ->
                        currentProcess = proc
                    }
                )

                client?.execute {
                    currentProcess = null
                    if (result.first) {
                        statusLabel.message = Text.translatable("message.simplesoundboard.youtube.finished").formatted(Formatting.GREEN)
                        progress = 100
                    } else {
                        // If we cancelled, the message might already be set, but the result will be false.
                        // We can check if status message is "Cancelled" to avoid overwriting it, or just overwrite it.
                        // Actually, if we cancelled, currentProcess was set to null in cancelDownload, but the background thread
                        // continues to the end of downloadUrlIntoSoundDir which returns.
                        // If we cancelled, the result.second likely contains an error from the stream closing or process kill.
                        statusLabel.message = Text.translatable("message.simplesoundboard.youtube.failed").formatted(Formatting.RED)
                    }
                }
            } catch (e: Exception) {
                client?.execute {
                    currentProcess = null
                    statusLabel.message = Text.translatable("message.simplesoundboard.youtube.failed").formatted(Formatting.RED)
                    logList.addLine("> Error: ${e.message}")
                }
            }
        }
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(context, mouseX, mouseY, delta)

        // Update button text periodically or every frame based on state
        if (currentProcess != null) {
            downloadBtn.message = Text.translatable("gui.simplesoundboard.youtube.cancel").formatted(Formatting.RED)
        } else {
            downloadBtn.message = Text.translatable("gui.simplesoundboard.youtube.download")
        }

        context.drawCenteredTextWithShadow(textRenderer, title.asOrderedText(), width / 2, 8, 0xFFFFFF)

        val actionButtonWidth = 80
        val buttonSpacing = 5
        val totalActionWidth = (actionButtonWidth * 5) + (buttonSpacing * 4)
        val barW = totalActionWidth
        val barX = width / 2 - barW / 2
        val barY = height - 55
        val filled = (barW * (progress / 100f)).toInt()

        context.fill(barX, barY, barX + barW, barY + 5, 0xFF555555.toInt())
        context.fill(barX, barY, barX + filled, barY + 5, 0xFF00AA00.toInt())

        val folderText = Text.translatable("gui.simplesoundboard.youtube.save_folder", SimpleSoundboardClient.soundDir.absolutePath)
        val folderTextWidth = textRenderer.getWidth(folderText)
        context.drawText(
            textRenderer,
            folderText.asOrderedText(),
            width / 2 - folderTextWidth / 2,
            height - 42,
            Color.GRAY.rgb,
            false
        )
    }

    override fun close() {
        client?.setScreen(parent)
    }

    private fun extractProgress(line: String): Int? {
        val match = Regex("""(\d{1,3}\.\d)%""").find(line)
            ?: Regex("""(\d{1,3})%""").find(line)

        return match?.groupValues?.get(1)?.toFloatOrNull()?.toInt()
    }
}