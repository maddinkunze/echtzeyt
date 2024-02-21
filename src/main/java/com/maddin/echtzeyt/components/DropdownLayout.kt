package com.maddin.echtzeyt.components

import android.content.Context
import android.util.AttributeSet
import android.widget.Button
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.util.TypedValueCompat
import androidx.core.view.ViewCompat

open class DropdownLayout: ConstraintLayout {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    private val scaleYClosed = 0.3f
    private val scaleYOpened = 1f
    private val alphaClosed = 0f
    private val alphaOpened = 1f
    private val translateYClosed by lazy { TypedValueCompat.dpToPx(-5f, resources.displayMetrics) }
    private val translateYOpened = 0f

    init {
        if (!isInEditMode) {
            visibility = GONE
            scaleY = scaleYClosed
            alpha = alphaClosed
            translationY = translateYClosed
        }
        pivotY = 0f
    }

    private var opened = false
    protected var mButton: DropdownButton? = null
    protected var mOthers: List<DropdownLayout> = emptyList()
    protected val mDurationFade = 200L
    protected val mDurationFadeIn = mDurationFade
    protected val mDurationFadeOut = mDurationFade
    protected val mInterpolator = androidx.interpolator.view.animation.LinearOutSlowInInterpolator()

    fun open() {
        if (opened) { return }
        opened = true
        mButton?.open()
        setPositionLeft()
        visibility = VISIBLE
        animate().scaleY(scaleYOpened).alpha(alphaOpened).translationY(translateYOpened).setInterpolator(mInterpolator).setDuration(mDurationFadeIn).start()
        mOthers.forEach { if (it == this) { return@forEach }; it.close() }
    }

    fun close() {
        if (!opened) { return }
        opened = false
        mButton?.close()
        ViewCompat.animate(this).scaleY(scaleYClosed).alpha(alphaClosed).translationY(translateYClosed).setInterpolator(mInterpolator).setDuration(mDurationFadeOut).withEndAction {
            visibility = GONE
        }.start()
    }

    fun toggle() {
        if (opened) { close() }
        else { open() }
    }

    open fun setListeners(button: Button, others: List<DropdownLayout>) {
        mButton = button as? DropdownButton
        mOthers = others
        button.setOnClickListener {
            toggle()
        }
    }

    protected open fun setPositionLeft() {
        mButton?.let { translationX = it.x }
    }
}