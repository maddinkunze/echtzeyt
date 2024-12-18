package com.maddin.echtzeyt.randomcode

import android.content.Context
import android.content.res.Resources.NotFoundException
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.toRectF
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import com.maddin.echtzeyt.R
import com.maddin.echtzeyt.ThemedContext
import kotlin.math.roundToInt

open class LineDrawable(protected val context: ThemedContext, @ColorRes protected val backgroundRes: Int, @ColorRes protected val foregroundRes: Int, @ColorRes protected val foregroundHintRes: Int) : Drawable() {
    protected val paintBackground = Paint()
    protected val paintForeground = Paint()
    val hintColor = context.getColorCompat(foregroundHintRes)
    val textColor: Int; get() = paintForeground.color
    val backColor: Int; get() = paintBackground.color
    open var radius = 0.3
    open var paddingTop = 0.2
    open var paddingBottom = 0.2
    open var paddingLeft = 0.55
    open var paddingRight = 0.55
    private var radiusPx = 0f

    init {
        updateTheme()
    }

    override fun setBounds(left: Int, top: Int, right: Int, bottom: Int) {
        super.setBounds(left, top, right, bottom)
        radiusPx = (bounds.height() * radius).toFloat()
    }

    override fun draw(canvas: Canvas) {
        canvas.drawRoundRect(bounds.toRectF(), radiusPx, radiusPx, paintBackground)
    }

    private fun updateTheme() {
        paintBackground.color = context.getColorCompat(backgroundRes)
        paintForeground.color = context.getColorCompat(foregroundRes)
    }

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Deprecated("Deprecated in Java", ReplaceWith("", ""))
    override fun getOpacity(): Int { return PixelFormat.OPAQUE }

    override fun getMinimumWidth() = 0
    override fun getMinimumHeight() = 0

    open fun copy(): LineDrawable {
        val copy = LineDrawable(context, backgroundRes, foregroundRes, foregroundHintRes)
        copyAttributes(copy)
        return copy
    }

    protected fun copyAttributes(copy: LineDrawable) {
        copy.radius = radius
        copy.paddingTop = paddingTop
        copy.paddingLeft = paddingLeft
        copy.paddingRight = paddingRight
        copy.paddingBottom = paddingBottom
    }
}

open class IconLineDrawable(context: ThemedContext, @ColorRes backgroundRes: Int, @ColorRes foregroundRes: Int, @ColorRes foregroundHintRes: Int, @DrawableRes private val iconRes: Int) : LineDrawable(context, backgroundRes, foregroundRes, foregroundHintRes) {
    lateinit var icon: Drawable
        private set
    var iconSize = 0.9
    var iconTint = true
    var iconPaddingLeft = 0.3
    var iconPaddingRight = 0.0
    var iconGravityVertical = 0.5
    override var paddingLeft
        get() = super.paddingLeft + iconSize * icon.intrinsicWidth / icon.intrinsicHeight + iconPaddingLeft + iconPaddingRight
        set(value) { super.paddingLeft = value }

    init {
       updateTheme()
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        if (iconTint) { DrawableCompat.setTint(icon, paintForeground.color) }

        val height = bounds.height()
        val textSize = height / (paddingTop + paddingBottom + 1)
        val iconHeight = iconSize * textSize
        val iconWidth = iconHeight * (icon.intrinsicWidth / icon.intrinsicHeight)

        val iconBoundsT = icon.copyBounds()
        val iconBounds = icon.bounds
        iconBounds.top = ((height - iconHeight) * iconGravityVertical).roundToInt()
        iconBounds.left = (iconPaddingLeft * textSize).roundToInt()
        iconBounds.bottom = iconBounds.top + iconHeight.roundToInt()
        iconBounds.right = iconBounds.left + iconWidth.roundToInt()
        icon.bounds = iconBounds

        icon.draw(canvas)
        icon.bounds = iconBoundsT
    }

    private fun updateTheme() {
        icon = context.getDrawableCompat(iconRes)!!
    }

    override fun copy(): IconLineDrawable {
        val copy = IconLineDrawable(context, backgroundRes, foregroundRes, foregroundHintRes, iconRes)
        copyAttributes(copy)
        return copy
    }

    protected fun copyAttributes(copy: IconLineDrawable) {
        super.copyAttributes(copy)
        copy.paddingLeft = super.paddingLeft
        copy.iconPaddingLeft = iconPaddingLeft
        copy.iconPaddingRight = iconPaddingRight
        copy.iconSize = iconSize
        copy.iconTint = iconTint
        copy.iconGravityVertical = iconGravityVertical
    }
}