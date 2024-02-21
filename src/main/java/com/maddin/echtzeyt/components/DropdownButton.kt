package com.maddin.echtzeyt.components

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.vectordrawable.graphics.drawable.Animatable2Compat
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import com.google.android.material.button.MaterialButton
import com.maddin.echtzeyt.R

class DropdownButton : MaterialButton {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    private class AnimationCallback(val parent: DropdownButton, val finalState: Int) : Animatable2Compat.AnimationCallback() {
        override fun onAnimationStart(drawable: Drawable?) {
            super.onAnimationStart(drawable)
            parent.nextState = null
            parent.isAnimationRunning = true
        }
        override fun onAnimationEnd(drawable: Drawable?) {
            super.onAnimationEnd(drawable)
            parent.currentState = finalState
            parent.isAnimationRunning = false
            parent.mNextAnimation()
        }
    }

    private val drawableClosedToOpen by lazy {
        val drawable = AnimatedVectorDrawableCompat.create(context, R.drawable.ic_arrow_dropdown_closed_to_open)
        drawable?.registerAnimationCallback(AnimationCallback(this, STATE_OPENED))
        return@lazy drawable
    }
    private val drawableOpenToClosed by lazy {
        val drawable = AnimatedVectorDrawableCompat.create(context, R.drawable.ic_arrow_dropdown_open_to_closed)
        drawable?.registerAnimationCallback(AnimationCallback(this, STATE_CLOSED))
        return@lazy drawable
    }

    companion object {
        const val STATE_CLOSED = 0
        const val STATE_OPENED = 1
    }

    private var currentState = STATE_CLOSED
    private var nextState: Int? = null
    private var isAnimationRunning = false

    override fun onFinishInflate() {
        super.onFinishInflate()
        icon = drawableClosedToOpen
    }

    private fun mClose() {
        icon = drawableOpenToClosed
        drawableOpenToClosed?.start()
    }

    fun close() {
        nextState = STATE_CLOSED
        mNextAnimation()
    }

    fun open() {
        nextState = STATE_OPENED
        mNextAnimation()
    }

    fun toggleArrow() {
        when (currentState) {
            STATE_CLOSED -> open()
            STATE_OPENED -> close()
        }
    }

    private fun mOpen() {
        icon = drawableClosedToOpen
        drawableClosedToOpen?.start()
    }

    private fun mNextAnimation() {
        if (isAnimationRunning) { return }
        if (nextState == null) { return }
        if (currentState == nextState) {
            nextState = null
            return
        }
        when (nextState) {
            STATE_CLOSED -> mClose()
            STATE_OPENED -> mOpen()
        }
    }
}