package com.maddin.echtzeyt.components

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.os.Build
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.children
import androidx.core.view.marginTop
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.dynamicanimation.animation.FlingAnimation
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import com.maddin.echtzeyt.R
import com.maddin.echtzeyt.fragments.EchtzeytPullupFragment
import com.maddin.echtzeyt.randomcode.DisablingParentScrollChild
import com.maddin.echtzeyt.randomcode.LazyView
import java.lang.IllegalArgumentException
import kotlin.math.roundToInt


@Suppress("MemberVisibilityCanBePrivate")
open class PullupScrollView : FrameLayout, GestureDetector.OnGestureListener, DisablingParentScrollChild {
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) { getAttributes(attrs, defStyleAttr, defStyleRes) }
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { getAttributes(attrs, defStyleAttr) }
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) { getAttributes(attrs) }
    constructor(context: Context) : super(context) { getAttributes() }

    override val changeParentScrollListeners = mutableListOf<(Boolean) -> Unit>()
    private val layoutChild by lazy { getChildAt(0) }
    private val layoutHeader: FrameLayout by LazyView(R.id.pu_layoutHeaderContent)
    private val layoutContent: FrameLayout by LazyView(R.id.pu_layoutContent)
    private val btnClose: ImageButton by LazyView(R.id.pu_btnClosePullup)
    private val btnBack: ImageButton by LazyView(R.id.pu_btnBackPullup)
    private val viewCloseRing: View by LazyView(R.id.pu_viewClosePullupRing)

    var manager: PullupManager? = null

    companion object {
        const val VARIANT_FIRST = 1
        const val VARIANT_SECOND = 2
    }
    var variant: Int = VARIANT_FIRST
        private set(value) {
            if (field == value) { return }
            field = value

            val backgroundRes = when (variant) {
                VARIANT_SECOND -> R.drawable.pullup_second
                else -> R.drawable.pullup
            }
            val backgroundColor = ContextCompat.getColor(context, when (variant) {
                VARIANT_SECOND -> R.color.backgroundPullupSecond
                else -> R.color.backgroundPullup
            })
            val buttonBackgroundRes = when (variant) {
                VARIANT_SECOND -> R.drawable.imagebutton_second
                else -> R.drawable.imagebutton
            }

            layoutChild.setBackgroundResource(backgroundRes)
            ViewCompat.setBackgroundTintList(viewCloseRing, ColorStateList(arrayOf(intArrayOf()), intArrayOf(backgroundColor)))
            this.buttonBackgroundRes = buttonBackgroundRes
        }
    var buttonBackgroundRes = R.drawable.imagebutton
        private set


    init {
        inflate(context, R.layout.comp_pullup_scroll2, this)
        clipChildren = false
        if (isInEditMode) { layoutChild.updateLayoutParams { height = 3200 } }
    }

    private val gestures by lazy { GestureDetector(context, this) }
    val onClosePullupListeners = mutableListOf<(View) -> Unit>()
    private var onBackListener: ((View, close: Boolean) -> Unit)? = null

    var minPullupScroll = 0; private set; val minPullupScrollSafe; get() = minPullupScroll.coerceAtMost(pullupScroll)
    var maxPullupScroll = 0; private set; val maxPullupScrollSafe; get() = maxPullupScroll.coerceAtLeast(pullupScroll)
    private val stateAnimator = ValueAnimator().apply {
        addUpdateListener { pullupScroll = (it.animatedValue as Int) }
        interpolator = LinearOutSlowInInterpolator()
    }
    fun updateScrollLimits(animateIntoBounds: Boolean=true) {
        recalculateScrollLimits()

        val scrollSafe = pullupScroll.coerceIn(minPullupScroll, maxPullupScroll)
        if (pullupScroll == scrollSafe) { return }
        scrollTo(scrollSafe, animateIntoBounds)
    }
    protected fun recalculateScrollLimits() {
        val shadowOffset = layoutChild.marginTop
        minPullupScroll = minimumVisibleHeight + additionalPaddingBottom + shadowOffset - height
        maxPullupScroll = (getInnerHeight() + shadowOffset - height).coerceAtLeast(minPullupScroll)
    }
    protected fun scrollTo(y: Int, animate: Boolean=true) {
        stateAnimator.cancel()
        if (animate) {
            stateAnimator.setIntValues(pullupScroll, y)
            stateAnimator.start()
        } else {
            pullupScroll = y
        }
    }

    private fun getInnerHeight(): Int {
        return getHeaderHeight() + getContentHeight() + (layoutHeader.parent as View).marginTop

    }

    private fun getHeaderHeight(): Int {
        layoutHeader.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST)
        )
        return layoutHeader.measuredHeight
    }

    private fun getContentHeight(): Int {
        layoutContent.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST)
        )
        return layoutContent.measuredHeight
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (isInEditMode) layoutContent.children.firstOrNull()?.let {
            layoutContent.minimumHeight = it.measuredHeight
            layoutChild.minimumHeight = measuredHeight + it.measuredHeight
        }
    }

    var minimumVisibleHeight = 0
    var additionalPaddingBottom = 0
        set(value) {
            val paddingBottomRaw = layoutContent.paddingBottom - field
            field = value
            layoutContent.updatePadding(bottom=paddingBottomRaw+value)
            updateScrollLimits(isVisible())
        }
    var minimumDisplayHeight = 0
    var durationFadeIn = 200
    var durationFadeOut = 200
    var durationFadeInRelative = 0f
    var durationFadeOutRelative = 0f

    private fun getAttributes(attrs: AttributeSet?=null, defStyleAttr: Int=0, defStyleRes: Int=0) {
        val styledAttr = context.theme.obtainStyledAttributes(attrs, R.styleable.PullupScrollView, defStyleAttr, defStyleRes)
        try {
            minimumVisibleHeight = styledAttr.getDimensionPixelSize(R.styleable.PullupScrollView_minimumVisibleHeight, minimumVisibleHeight)
            additionalPaddingBottom = styledAttr.getDimensionPixelSize(R.styleable.PullupScrollView_additionalPaddingBottom, additionalPaddingBottom)
            minimumDisplayHeight = minimumVisibleHeight + additionalPaddingBottom

            variant = styledAttr.getInt(R.styleable.PullupScrollView_variant, variant)

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

    private var mPullupScroll = 0
    var pullupScroll: Int
        set(valueT) {
            val value = valueT.coerceIn(minPullupScrollSafe, maxPullupScrollSafe)
            mPullupScroll = value
            if (value > 0) {
                scrollY = 0
                layoutContent.scrollY = value
            } else {
                scrollY = value
                layoutContent.scrollY = 0
            }
        }
        get() = mPullupScroll

    override fun onDown(e: MotionEvent): Boolean { return mCanReceiveFocus }

    override fun onShowPress(e: MotionEvent) {}

    override fun onSingleTapUp(e: MotionEvent): Boolean { return false }

    private var isScrollingY = false
    override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
        flingAnimation.cancel()
        if (!mCanReceiveFocus) { return false }

        if (distanceY > 0.3 * distanceX) {
            if (!isScrollingY) { disableParentScroll() }
            isScrollingY = true
        }

        pullupScroll += distanceY.roundToInt()

        return true
    }

    override fun onLongPress(e: MotionEvent) {}

    private val flingAnimation = FlingAnimation(FloatValueHolder()).addUpdateListener { _, value, _ -> pullupScroll = value.roundToInt() }
    override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
        enableParentScroll()
        if (!mCanReceiveFocus) { return false }

        flingAnimation.cancel()
        flingAnimation.setStartValue(pullupScroll.toFloat())
        flingAnimation.setStartVelocity(-velocityY)
        flingAnimation.start()

        return true
    }

    private var mFocus = false
    private var mCanReceiveFocus = true
    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        val ret = super.dispatchTouchEvent(ev)
        if (ev == null) { return ret }
        if (!mCanReceiveFocus) { return false }

        if (ev.action == MotionEvent.ACTION_UP) {
            isScrollingY = false
            enableParentScroll()
        }

        // is there any previous action that has to be completed
        return if (mFocus) {
            if (ev.action == MotionEvent.ACTION_UP) { mFocus = false }
            if (ev.action == MotionEvent.ACTION_POINTER_UP) { mFocus = false }
            if (ev.action == MotionEvent.ACTION_CANCEL) { mFocus = false }
            gestures.onTouchEvent(ev)
        } else {
            val slop = ev.size/2
            if (ev.x !in layoutChild.left-slop..layoutChild.right+slop) { return false }
            if (ev.y in 0f..-scrollY-slop) { return false }
            mFocus = gestures.onTouchEvent(ev)
            mFocus
        }
    }

    fun showPullup() : Long {
        onBackListener = null
        btnBack.visibility = View.GONE

        manager?.opened(this)

        val durationRaw = durationFadeOut
        var durationAdjusted = durationRaw
        val height = layoutParams.height
        val visibleHeight = height + scrollY
        if (height > 0) {
            durationAdjusted = (visibleHeight / height) * durationRaw
        }
        val duration = ((1-durationFadeOutRelative) * durationRaw + durationFadeOutRelative * durationAdjusted).toLong()

        if (!isVisible()) {
            alpha = 0f
            translationY = visibleHeight.toFloat()
        }
        translationY = translationY.coerceAtMost(visibleHeight.toFloat())
        visibility = VISIBLE

        animate().alpha(1f).translationY(0f).setDuration(duration).start()

        mCanReceiveFocus = true

        return duration
    }

    fun showPullupWithBack(callback: ((View, close: Boolean) -> Unit)?) : Long {
        onBackListener = callback
        btnBack.visibility = View.VISIBLE
        return showPullup()
    }

    fun hidePullup() : Long {
        manager?.closed(this)

        val durationRaw = durationFadeOut
        var durationAdjusted = durationRaw
        val visibleHeight = height + scrollY
        if (height > 0) {
            durationAdjusted = (visibleHeight / height) * durationRaw
        }
        val duration = ((1-durationFadeOutRelative) * durationRaw + durationFadeOutRelative * durationAdjusted).toLong()

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

    private var initialLayout = true
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        if (!changed) { return }
        updateScrollLimits(isVisible() && !initialLayout)
        if (initialLayout) {
            pullupScroll = minPullupScroll
        }
        initialLayout = false
    }

    override fun onFinishInflate() {
        super.onFinishInflate()

        btnClose.setOnClickListener { closePullup() }
        btnBack.setOnClickListener { backPressed() }
    }

    private fun closePullup() {
        hidePullup()
        onBackListener?.let { it(this, true) }
        onClosePullupListeners.forEach { it(this) }
    }

    fun backPressed() {
        onBackListener?.let {
            hidePullup()
            it(this, false)
        } ?: closePullup()
        onBackListener = null
    }

    @SuppressLint("GetContentDescriptionOverride")
    override fun getContentDescription(): CharSequence {
        if (isInEditMode) { return "." }
        return super.getContentDescription()
    }

    fun saveState() {}
    fun animateFromSavedState() {}

    @Suppress("KotlinConstantConditions")
    override fun addView(child: View?, index: Int, params: ViewGroup.LayoutParams?) {
        if (child == null) { return super.addView(child, index, params) }

        if (childCount < 1) {
            super.addView(child, index, params)
        } else if (layoutHeader.childCount < 1 || (layoutHeader.getChildAt(0).id == child.id && child.id != 0)) {
            layoutHeader.removeAllViews()
            layoutHeader.addView(child, index, params)
        } else if (layoutContent.childCount < 1 || (layoutContent.getChildAt(0).id == child.id && child.id != 0)) {
            layoutContent.removeAllViews()
            layoutContent.addView(child, index, params)
        } else if (isInEditMode) {
            layoutHeader.removeAllViews()
            layoutContent.removeAllViews()
            addView(child, index, params)
        } else {
            throw IllegalArgumentException("You can only add two direct children to a PullupScrollView (Header and Content), tried to add $child (${layoutHeader.getChildAt(0)})")
        }
    }

    override fun removeView(view: View?) {
        super.removeView(view)
        if (view == null) { return }
        layoutHeader.removeView(view)
        layoutContent.removeView(view)
    }

    override fun removeAllViews() {
        super.removeAllViews()
        layoutHeader.removeAllViews()
        layoutContent.removeAllViews()
    }
}

class PullupManager(val context: Any) {
    private var current: PullupScrollView? = null

    val onFirstOpenedListeners = mutableListOf<() -> Unit>()
    val onLastClosedListeners = mutableListOf<() -> Unit>()

    fun onBackPressed() {
        current?.backPressed()
    }

    fun opened(opened: PullupScrollView) {
        val last = current
        current = opened
        last?.hidePullup() ?: onFirstOpenedListeners.forEach { it() }
    }

    fun closed(closed: PullupScrollView) {
        if ((closed != current) && (current?.isVisible() == true)) { return }
        current = null

        onLastClosedListeners.forEach { it() }
    }
}

open class LazyPullup<T : PullupScrollView>(id: Int, val manager: () -> PullupManager) : LazyView<T>(id) {
    constructor(id: Int, manager: PullupManager) : this(id, { manager })
    constructor(id: Int, context: EchtzeytPullupFragment) : this(id, { context.pullupManager })

    override fun init(item: T) {
        item.manager = manager()
        super.init(item)
    }
}