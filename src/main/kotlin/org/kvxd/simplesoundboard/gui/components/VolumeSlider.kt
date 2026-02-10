package org.kvxd.simplesoundboard.gui.components

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.SliderWidget
import net.minecraft.text.Text
import java.awt.Color
import kotlin.math.roundToInt

class VolumeSlider(
    x: Int, y: Int, width: Int, height: Int,
    private val prefix: Text,
    initialValue: Float,
    private val onChange: (Float) -> Unit,
    private val messageProvider: ((Float) -> Text)? = null
) : SliderWidget(x, y, width, height, Text.empty(), initialValue.toDouble()) {

    init {
        updateMessage()
    }

    fun setValue(newValue: Float) {
        this.value = newValue.toDouble()
        updateMessage()
        applyValue()
    }

    fun setValueQuietly(newValue: Float) {
        this.value = newValue.toDouble()
        updateMessage()
    }

    override fun renderWidget(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        super.renderWidget(context, mouseX, mouseY, delta)

        if (this.active) return

        val tr = MinecraftClient.getInstance().textRenderer

        val drawText = Text.translatable("gui.simplesoundboard.volume.none_format", prefix)

        val textWidth = tr.getWidth(drawText)
        val textX = this.x + (this.width - textWidth) / 2

        val textY = this.y + (this.height - tr.fontHeight) / 2 + 1

        context.drawText(tr, drawText, textX, textY, Color(160, 160, 160).rgb, false)
    }

    override fun updateMessage() {
        if (messageProvider != null) {
            message = messageProvider.invoke(value.toFloat())
        } else {
            val percent = (value * 100).roundToInt()
            message = Text.translatable("gui.simplesoundboard.volume.format", prefix, percent)
        }
    }

    override fun applyValue() {
        onChange(value.toFloat())
    }
}