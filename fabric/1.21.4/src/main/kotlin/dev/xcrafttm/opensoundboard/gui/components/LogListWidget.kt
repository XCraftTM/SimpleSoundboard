package dev.xcrafttm.opensoundboard.gui.components

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.Element
import net.minecraft.client.gui.Selectable
import net.minecraft.client.gui.widget.ElementListWidget
import net.minecraft.text.OrderedText
import net.minecraft.text.Text

class LogListWidget(
    client: MinecraftClient,
    listX: Int,
    width: Int,
    height: Int,
    listY: Int,
    itemHeight: Int
) : ElementListWidget<LogListWidget.LogEntry>(client, width, height, listY, itemHeight) {

    init {
        this.x = listX
    }

    override fun getRowWidth(): Int = width

    override fun getScrollbarX(): Int = x + width - 6

    fun addLine(line: String) {
        val cleanText = line.replace(Regex("\u001B\\[[;\\d]*m"), "")
            .replace(Regex("\u001B\\[[;\\d]*[A-K]"), "")
            .replace("\r", "")
            .trim()

        if (cleanText.isEmpty()) return

        val widthLimit = getRowWidth() - 10
        val wrappedLines = client.textRenderer.wrapLines(Text.literal(cleanText), widthLimit)

        for (wrappedLine in wrappedLines) {
            addEntry(LogEntry(wrappedLine))
        }

        // Scroll to the bottom
        val maxScroll = (children().size * itemHeight - (height - 4)).toDouble().coerceAtLeast(0.0)
        scrollY = maxScroll
    }

    fun clearLogs() {
        super.clearEntries()
        scrollY = 0.0
    }

    inner class LogEntry(val text: OrderedText) : ElementListWidget.Entry<LogEntry>() {
        override fun children(): List<Element> = emptyList()
        override fun selectableChildren(): List<Selectable> = emptyList()
        override fun render(
            context: DrawContext?,
            index: Int,
            y: Int,
            x: Int,
            entryWidth: Int,
            entryHeight: Int,
            mouseX: Int,
            mouseY: Int,
            hovered: Boolean,
            tickDelta: Float
        ) {
            val index = this@LogListWidget.children().indexOf(this)
            if (index == -1) return

            val entryX = this@LogListWidget.x + 5
            val entryY = this@LogListWidget.y + 4 + (index * itemHeight) - scrollY.toInt()

            // Culling: don't render if outside list's vertical viewport
            if (entryY + itemHeight < this@LogListWidget.y || entryY > this@LogListWidget.y + this@LogListWidget.height) return

            val textRenderer = client.textRenderer
            val textY = entryY + (itemHeight - textRenderer.fontHeight) / 2

            context?.drawText(textRenderer, text, entryX, textY, 0xFFFFFFFF.toInt(), true)
        }
    }
}
