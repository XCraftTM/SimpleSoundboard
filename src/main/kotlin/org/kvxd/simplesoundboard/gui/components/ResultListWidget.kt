package org.kvxd.simplesoundboard.gui.components

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.Element
import net.minecraft.client.gui.Selectable
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.ElementListWidget
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import org.kvxd.simplesoundboard.SoundboardAudioSystem
import org.kvxd.simplesoundboard.config.SoundboardConfig
import java.awt.Color
import java.io.File

class ResultListWidget(
    client: MinecraftClient,
    val listX: Int,
    width: Int,
    height: Int,
    val listY: Int,
    itemHeight: Int,
    private val onSelect: (File) -> Unit,
    private val onRefresh: () -> Unit,
    private val getSelectedFile: () -> File?
) : ElementListWidget<ResultListWidget.Entry>(client, width, height, listY, itemHeight) {

    private val horizontalPadding = 6

    init {
        this.x = listX
    }

    fun setResults(results: List<File>) {
        clearEntries()
        results.forEach { addEntry(Entry(it)) }
        scrollY = 0.0
    }

    override fun getRowWidth(): Int = width - (horizontalPadding * 2)

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
                onRefresh()
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
            val index = this@ResultListWidget.children().indexOf(this)
            if (index == -1) return

            val entryWidth = getRowWidth()
            val entryX = this@ResultListWidget.x + horizontalPadding
            val entryY = this@ResultListWidget.y + 4 + (index * itemHeight) - scrollY.toInt()

            // Culling: don't render if outside list's vertical viewport
            if (entryY + itemHeight < this@ResultListWidget.y || entryY > this@ResultListWidget.y + this@ResultListWidget.height) return

            if (getSelectedFile()?.name == file.name) {
                context.fill(entryX, entryY, entryX + entryWidth, entryY + itemHeight, 0x33FFFFFF)
            }

            val isPlaying = SoundboardAudioSystem.isPlaying(file.name)
            playBtn.message =
                if (isPlaying) Text.translatable("gui.simplesoundboard.stop").formatted(Formatting.RED) else Text.translatable("gui.simplesoundboard.play")

            val textRenderer = client.textRenderer
            val textY = entryY + (itemHeight - textRenderer.fontHeight) / 2
            var nameText = file.nameWithoutExtension

            if (textRenderer.getWidth(nameText) > entryWidth - 70) {
                nameText = textRenderer.trimToWidth(nameText, entryWidth - 75) + "..."
            }

            val textColor = if (isPlaying) 0xFFFFFF00.toInt() else 0xFFFFFFFF.toInt()
            context.drawText(textRenderer, nameText, entryX + 25, textY, textColor, true)

            val btnY = entryY + (itemHeight - 20) / 2
            favBtn.x = entryX
            favBtn.y = btnY
            favBtn.render(context, mouseX, mouseY, deltaTicks)

            playBtn.x = entryX + entryWidth - playBtn.width
            playBtn.y = btnY
            playBtn.render(context, mouseX, mouseY, deltaTicks)
        }

        override fun mouseClicked(click: Click, doubled: Boolean): Boolean {
            val index = this@ResultListWidget.children().indexOf(this)
            if (index == -1) return false

            val entryX = this@ResultListWidget.x + horizontalPadding
            val entryY = this@ResultListWidget.y + 4 + (index * itemHeight) - scrollY.toInt()

            // Update hitboxes before check
            favBtn.x = entryX
            favBtn.y = entryY + (itemHeight - 20) / 2
            playBtn.x = entryX + getRowWidth() - playBtn.width
            playBtn.y = favBtn.y

            if (favBtn.mouseClicked(click, doubled)) return true
            if (playBtn.mouseClicked(click, doubled)) return true

            onSelect(file)

            if (doubled) {
                if (SoundboardAudioSystem.isPlaying(file.name)) {
                    SoundboardAudioSystem.stop(file.name)
                } else {
                    val currentData = SoundboardConfig[file.name]
                    SoundboardAudioSystem.playFile(file, currentData.localVolume, currentData.playerVolume)
                }
            }

            return true
        }

        override fun children() = elements
        override fun selectableChildren() = selectables
    }
}
