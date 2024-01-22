package com.maddin.echtzeyt.components

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.view.updatePadding
import kotlin.math.roundToInt

class AnimatableImageButton: AppCompatImageButton {
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context) : super(context)

    private var mCurrentPaddingAnimation: ValueAnimator? = null
    fun animatePadding(from: Float, to: Float, duration: Long) {
        mCurrentPaddingAnimation?.cancel()
        val paddingAnimation = ValueAnimator.ofFloat(from, to)
        paddingAnimation.addUpdateListener {
            val pad = (it.animatedValue as Float).roundToInt()
            updatePadding(left=pad, top=pad, right=pad, bottom=pad)
        }
        mCurrentPaddingAnimation = paddingAnimation
        paddingAnimation.setDuration(duration).start()
    }

    fun animatePadding(to: Float, duration: Long) {
        animatePadding(paddingLeft.toFloat(), to, duration)
    }
}