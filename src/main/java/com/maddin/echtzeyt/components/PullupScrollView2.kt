package com.maddin.echtzeyt.components

import android.animation.Animator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ScrollView
import androidx.core.view.ViewCompat
import androidx.core.view.updatePadding
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.maddin.echtzeyt.R
import com.maddin.echtzeyt.randomcode.LazyView
import kotlin.math.absoluteValue
import kotlin.math.roundToInt


@Suppress("MemberVisibilityCanBePrivate")
open class PullupScrollView2(context: Context, private val attrs: AttributeSet?, private val defStyleAttr: Int) : ScrollView(context, attrs, defStyleAttr) {
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context) : this(context, null)

    private var mFocus = false
    private var mCanReceiveFocus = true
    private var mDeltaTopPosNoticable = 2
    private var mInitialAnimation = false

    var minimumVisibleHeight = 0
    var additionalPaddingBottom = 0
    private var mBackgroundPullup = 0
    var minimumDisplayHeight = 0
    var durationFadeIn = 200
    var durationFadeOut = 200
    var durationFadeInRelative = 0f
    var durationFadeOutRelative = 0f

    init {
        LayoutInflater.from(context).inflate(R.layout.comp_pullup_scroll, this)
        getAttributes()
    }

    val outerLayout: View by lazy { getChildAt(0) }
    val innerLayout: FrameLayout by LazyView(R.id.contentLayout)

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (!changed) { return }

        innerLayout.minimumHeight = minimumDisplayHeight
        updateVisiblePortion(minimumDisplayHeight)
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        innerLayout.minimumHeight = minimumDisplayHeight
        innerLayout.updatePadding(bottom=additionalPaddingBottom)
        if (visibility == GONE) { mInitialAnimation = true }
        if (mBackgroundPullup != 0) { innerLayout.setBackgroundResource(mBackgroundPullup) }

        // when in preview mode: get the display height and move the contents to the bottom once
        if (isInEditMode) { outerLayout.updatePadding(top=resources.displayMetrics.heightPixels-minimumDisplayHeight) }
    }

    private fun updateVisiblePortion(visible: Int) {
        updateTopPos(height - visible)
    }

    private fun updateTopPos(newPos: Int) {
        if ((newPos-outerLayout.paddingTop).absoluteValue < mDeltaTopPosNoticable) { return }

        outerLayout.updatePadding(top=newPos)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent?): Boolean {
        if (ev == null) { return super.onTouchEvent(null) }
        if (!mCanReceiveFocus) { return false }

        // is there any previous action that has to be completed
        if (mFocus) {
            if (ev.action == MotionEvent.ACTION_UP) { mFocus = false }
            if (ev.action == MotionEvent.ACTION_POINTER_UP) { mFocus = false }
            if (ev.action == MotionEvent.ACTION_CANCEL) { mFocus = false }
            return super.onTouchEvent(ev)
        }

        // is the touch event happening outside of the content -> ignore touch event (dont scroll, click, ...)
        val realY = ev.y + scrollY
        if (realY < innerLayout.top - getGraceArea()) { return false }
        if (realY > innerLayout.bottom) { return false }

        // is the touch event inside the content -> allow potential scrolling/clicking/...
        mFocus = super.onTouchEvent(ev)
        return mFocus
    }

    protected fun getGraceArea() : Int {
        return 50
    }

    private fun getAttributes() {
        val styledAttr = context.theme.obtainStyledAttributes(attrs, R.styleable.PullupScrollView, defStyleAttr, 0)
        try {
            minimumVisibleHeight = styledAttr.getDimensionPixelSize(R.styleable.PullupScrollView_minimumVisibleHeight, minimumVisibleHeight)
            additionalPaddingBottom = styledAttr.getDimensionPixelSize(R.styleable.PullupScrollView_additionalPaddingBottom, additionalPaddingBottom)
            minimumDisplayHeight = minimumVisibleHeight + additionalPaddingBottom

            //mBackgroundPullup = styledAttr.getResourceId(R.styleable.PullupScrollView_backgroundPullup, mBackgroundPullup)

            if (styledAttr.hasValue(R.styleable.PullupScrollView_fadeDuration)) {
                val durationFade = styledAttr.getInteger(R.styleable.PullupScrollView_fadeDuration, 0)
                durationFadeIn = durationFade
                durationFadeOut = durationFade
            }
            durationFadeIn = styledAttr.getInteger(R.styleable.PullupScrollView_fadeInDuration, durationFadeIn)
            durationFadeOut = styledAttr.getInteger(R.styleable.PullupScrollView_fadeOutDuration, durationFadeOut)

            if (styledAttr.hasValue(R.styleable.PullupScrollView_fadeDurationRelative)) {
                val durationRelative = styledAttr.getFloat(R.styleable.PullupScrollView_fadeDurationRelative, 0f)
                durationFadeInRelative = durationRelative
                durationFadeOutRelative = durationRelative
            }
            durationFadeInRelative = styledAttr.getFloat(R.styleable.PullupScrollView_fadeInDurationRelative, durationFadeInRelative)
            durationFadeOutRelative = styledAttr.getFloat(R.styleable.PullupScrollView_fadeOutDurationRelative, durationFadeOutRelative)
        } finally {
            styledAttr.recycle()
        }
    }

    fun showPullup() : Long {
        val durationRaw = durationFadeOut
        var durationAdjusted = durationRaw
        innerLayout.minimumHeight = minimumDisplayHeight
        val visibleHeight = minimumDisplayHeight + scrollY.coerceAtMost(height)
        if (minimumVisibleHeight > 0) {
            durationAdjusted = (visibleHeight / minimumVisibleHeight) * durationRaw
        }
        val duration = ((1-durationFadeOutRelative) * durationRaw + durationFadeOutRelative * durationAdjusted).toLong()

        if (mInitialAnimation) { translationY = visibleHeight.toFloat() }
        translationY = translationY.coerceAtMost(visibleHeight.toFloat())
        visibility = VISIBLE

        animate().alpha(1f).translationY(0f).setDuration(duration).start()

        mCanReceiveFocus = true

        return duration
    }

    fun hidePullup() : Long {
        val durationRaw = durationFadeOut
        var durationAdjusted = durationRaw
        val visibleHeight = minimumDisplayHeight + scrollY.coerceAtMost(height)
        if (minimumDisplayHeight > 0) {
            durationAdjusted = (visibleHeight / minimumVisibleHeight) * durationRaw
        }
        val duration = ((1-durationFadeOutRelative) * durationRaw + durationFadeOutRelative * durationAdjusted).toLong()

        innerLayout.minimumHeight = minimumDisplayHeight
        mCanReceiveFocus = false

        ViewCompat.animate(this).alpha(0f).translationY(visibleHeight.toFloat()).withEndAction {
            if (alpha > 0.001) { return@withEndAction }
            visibility = GONE
        }.setDuration(duration).start()

        return duration
    }

    fun isVisible() : Boolean {
        return (visibility == VISIBLE) && mCanReceiveFocus
    }

    override fun addView(child: View?) {
        if (child == null || childCount < 1) {
            super.addView(child)
        } else {
            innerLayout.addView(child)
        }
    }

    override fun addView(child: View?, index: Int) {
        if (child == null || childCount < 1) {
            super.addView(child, index)
        } else {
            innerLayout.addView(child, index)
        }
    }

    override fun addView(child: View, index: Int, params: ViewGroup.LayoutParams?) {
        if (childCount < 1) {
            super.addView(child, index, params)
        } else {
            innerLayout.addView(child, index, params)
        }
    }

    override fun addView(child: View?, params: ViewGroup.LayoutParams?) {
        if (childCount < 1) {
            super.addView(child, params)
        } else {
            innerLayout.addView(child, params)
        }
    }

    override fun addView(child: View?, width: Int, height: Int) {
        if (childCount < 1) {
            super.addView(child, width, height)
        } else {
            innerLayout.addView(child, width, height)
        }
    }

    // the component preview in android studio complains if no speakable description is provided
    // so if we are in edit mode, we return some non-empty garbage for android studio to chew on
    @SuppressLint("GetContentDescriptionOverride")
    override fun getContentDescription(): CharSequence {
        if (isInEditMode) { return "." }
        return super.getContentDescription()
    }

    private var mStateAnimator: Animator? = null
    protected fun saveState() {
        mStateAnimator?.cancel()
        innerLayout.minimumHeight = innerLayout.height
    }

    protected fun animateFromSavedState() {
        val parent = innerLayout.parent as View
        innerLayout.minimumHeight = minimumDisplayHeight
        innerLayout.measure(MeasureSpec.makeMeasureSpec(parent.width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(parent.height, MeasureSpec.AT_MOST))

        val heightFrom = innerLayout.height
        val heightTo = innerLayout.measuredHeight.coerceAtLeast(minimumDisplayHeight)
        if (heightTo >= heightFrom) { return }

        val durationRaw = durationFadeOut
        var durationAdjusted = durationRaw
        val deltaHeight = (heightTo - heightFrom).coerceAtMost(height)
        if (minimumVisibleHeight > 0) {
            durationAdjusted = (deltaHeight / minimumVisibleHeight) * durationRaw
        }
        val duration = ((1-durationFadeOutRelative) * durationRaw + durationFadeOutRelative * durationAdjusted).toLong()

        val animator = ValueAnimator.ofFloat(heightFrom.toFloat(), heightTo.toFloat())
        animator.addUpdateListener {
                v -> innerLayout.minimumHeight = (v.animatedValue as Float).roundToInt()
        }
        animator.addListener(object: Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {}
            override fun onAnimationEnd(animation: Animator) { innerLayout.minimumHeight = minimumDisplayHeight }
            override fun onAnimationCancel(animation: Animator) {}
            override fun onAnimationRepeat(animation: Animator) {}
        })
        animator.interpolator = FastOutSlowInInterpolator()
        animator.duration = duration
        animator.start()
        mStateAnimator = animator
    }
}
