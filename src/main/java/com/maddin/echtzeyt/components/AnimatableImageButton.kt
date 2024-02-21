package com.maddin.echtzeyt.components

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.view.marginLeft
import androidx.core.view.updateLayoutParams
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.maddin.echtzeyt.R
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

class AnimatableImageButton: AppCompatImageButton {
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        getAttributes(context, attrs, defStyleAttr)
    }
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        getAttributes(context, attrs)
    }
    constructor(context: Context) : super(context) {
        getAttributes(context)
    }

    private var mPreferredHeight = Float.NaN
    private var mPreferredPadding = Float.NaN
    private var mPreferredMargin = Float.NaN
    private var mMinimizedMargin = Float.NaN

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (!changed) { return }
        post { updatePadding((bottom-top).absoluteValue.toFloat()) }
    }

    private fun getAttributes(context: Context, attrs: AttributeSet?=null, defStyleAttr: Int=0, defStyleRes: Int=0) {
        val styledAttr = context.obtainStyledAttributes(attrs, R.styleable.AnimatableImageButton, defStyleAttr, defStyleRes)
        try {
            mPreferredHeight = styledAttr.getDimension(R.styleable.AnimatableImageButton_preferredHeight, mPreferredHeight)
            mPreferredPadding = styledAttr.getDimension(R.styleable.AnimatableImageButton_preferredPadding, mPreferredPadding)
            mPreferredMargin = styledAttr.getDimension(R.styleable.AnimatableImageButton_preferredMargin, mPreferredMargin)
            mMinimizedMargin = styledAttr.getDimension(R.styleable.AnimatableImageButton_minimizedMargin, mMinimizedMargin)
        } catch (_: Throwable) {
            styledAttr.recycle()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!mMinimizedMargin.isNaN()) { setMinimizedMargin() }
    }

    private fun calcWidth(height: Float): Double {
        var ratio = 1.0
        try { ratio = 0.0 + drawable.intrinsicWidth / drawable.intrinsicHeight } catch(_: Throwable) {}
        return ratio * height
    }

    private fun calcPadding(height: Float): Float {
        val scale = height / mPreferredHeight
        return scale * mPreferredPadding
    }

    fun updatePadding(height: Float) {
        val pad = calcPadding(height).roundToInt()
        setPadding(pad, pad, pad, pad)
    }

    fun updateMargin(marginLeft: Float) {
        updateLayoutParams<LinearLayout.LayoutParams> { leftMargin = marginLeft.roundToInt() }
    }

    fun setMinimizedMargin() {
        updateMargin(mMinimizedMargin)
    }

    private val mMarginAnimator = ValueAnimator().apply {
        addUpdateListener { updateMargin(it.animatedValue as Float) }
        interpolator = FastOutSlowInInterpolator()
    }
    private fun animateMargin(to: Float, duration: Long) {
        mMarginAnimator.cancel()
        mMarginAnimator.setFloatValues(marginLeft.toFloat(), to)
        mMarginAnimator.duration = duration
        mMarginAnimator.start()
    }

    fun animateToMinimizedState(duration: Long) {
        if (mMinimizedMargin.isNaN()) { return }
        animateMargin(mMinimizedMargin, duration)
    }

    fun animateToPreferredState(duration: Long) {
        if (mPreferredMargin.isNaN()) { return }
        animateMargin(mPreferredMargin, duration)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        setMeasuredDimension(calcWidth(measuredHeight.toFloat()).roundToInt(), measuredHeight)
        post { updatePadding(measuredHeight.toFloat()) }
    }
}