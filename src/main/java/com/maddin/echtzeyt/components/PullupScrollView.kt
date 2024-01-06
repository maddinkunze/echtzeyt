package com.maddin.echtzeyt.components

import android.animation.ObjectAnimator
import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.view.updatePadding
import com.maddin.echtzeyt.R
import kotlin.math.absoluteValue

@Suppress("MemberVisibilityCanBePrivate")
class PullupScrollView(context: Context, private val attrs: AttributeSet?, private val defStyleAttr: Int) : ScrollView(context, attrs, defStyleAttr) {
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context) : this(context, null)

    private lateinit var mContentView: View
    private var mFocus = false
    private var mCanReceiveFocus = true
    private var mDeltaTopPosNoticable = 2
    private lateinit var mChildLayout: LinearLayout

    var minimumVisibleHeight = 0
    var durationFadeIn = 200
    var durationFadeOut = 200
    var durationFadeInRelative = 0f
    var durationFadeOutRelative = 0f

    private val animations = mutableListOf<ObjectAnimator>()

    init {
        getAttributes()
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (!changed) { return }

        mChildLayout = getChildAt(0) as LinearLayout
        mContentView = mChildLayout.getChildAt(0)
        updateVisiblePortion(minimumVisibleHeight)
    }

    private fun updateVisiblePortion(visible: Int) {
        updateTopPos(height - visible)
    }

    private fun updateTopPos(newPos: Int) {
        if ((newPos-mChildLayout.paddingTop).absoluteValue < mDeltaTopPosNoticable) { return }

        mChildLayout.updatePadding(top=newPos)
    }

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
        if (realY < mContentView.top - getGraceArea()) { return false }
        if (realY > mContentView.bottom) { return false }

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
        val visibleHeight = (minimumVisibleHeight + scrollY).coerceAtMost(height)
        if (minimumVisibleHeight > 0) {
            durationAdjusted = (visibleHeight / minimumVisibleHeight) * durationRaw
        }
        val duration = ((1-durationFadeOutRelative) * durationRaw + durationFadeOutRelative * durationAdjusted).toLong()

        visibility = VISIBLE
        translationY = translationY.coerceAtMost(visibleHeight.toFloat())

        val alphaAnimator = ObjectAnimator.ofFloat(this, "alpha", 1f)
        alphaAnimator.duration = duration
        val translationAnimator = ObjectAnimator.ofFloat(this, "translationY", 0f)
        translationAnimator.duration = duration

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            alphaAnimator.setAutoCancel(true)
            translationAnimator.setAutoCancel(true)
        }

        alphaAnimator.start()
        translationAnimator.start()

        println("MADDIN101: show pullup")
        mCanReceiveFocus = true

        return duration
    }

    fun hidePullup() : Long {
        val durationRaw = durationFadeOut
        var durationAdjusted = durationRaw
        val visibleHeight = (minimumVisibleHeight + scrollY).coerceAtMost(height)
        if (minimumVisibleHeight > 0) {
            durationAdjusted = (visibleHeight / minimumVisibleHeight) * durationRaw
        }
        val duration = ((1-durationFadeOutRelative) * durationRaw + durationFadeOutRelative * durationAdjusted).toLong()

        mCanReceiveFocus = false

        val alphaAnimator = ObjectAnimator.ofFloat(this, "alpha", 0f)
        alphaAnimator.duration = duration
        val translationAnimator = ObjectAnimator.ofFloat(this, "translationY", visibleHeight.toFloat())
        translationAnimator.duration = duration

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            alphaAnimator.setAutoCancel(true)
            translationAnimator.setAutoCancel(true)
        }

        alphaAnimator.start()
        translationAnimator.start()
        this.postDelayed({ if (alpha > 0.001) { return@postDelayed }; visibility = GONE }, duration)

        return duration
    }

    fun isVisible() : Boolean {
        return (visibility == VISIBLE) && mCanReceiveFocus
    }
}