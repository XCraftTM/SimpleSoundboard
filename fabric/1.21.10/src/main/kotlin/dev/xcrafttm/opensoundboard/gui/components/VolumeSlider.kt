package dev.xcrafttm.opensoundboard.gui.components

import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.SliderWidget
import net.minecraft.text.Text
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

    // Custom setter for active that updates message
    fun setActive(value: Boolean) {
        super.active = value
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
    }

    override fun updateMessage() {
        if (!this.active) {
            // When inactive, show "None" message
            message = Text.translatable("gui.opensoundboard.volume.none_format", prefix)
        } else if (messageProvider != null) {
            message = messageProvider.invoke(value.toFloat())
        } else {
            val percent = (value * 100).roundToInt()
            message = Text.translatable("gui.opensoundboard.volume.format", prefix, percent)
        }
    }

    override fun applyValue() {
        onChange(value.toFloat())
    }
}