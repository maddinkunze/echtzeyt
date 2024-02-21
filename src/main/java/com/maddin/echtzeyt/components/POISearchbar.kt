package com.maddin.echtzeyt.components

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Resources.NotFoundException
import android.os.Parcelable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.DimenRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.children
import androidx.core.view.marginTop
import androidx.core.view.updateLayoutParams
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import com.maddin.echtzeyt.R
import com.maddin.echtzeyt.randomcode.LazyView
import com.maddin.echtzeyt.randomcode.applyRandomId
import kotlin.math.roundToInt

fun View.isUserInteractionEnabled(enabled: Boolean) {
    isEnabled = enabled
    if (this is ViewGroup && this.childCount > 0) {
        this.children.forEach {
            it.isUserInteractionEnabled(enabled)
        }
    }
}

class POISearchbar : ConstraintLayout {
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        getAttributes(context, attrs, defStyleAttr)
        inflate()
    }
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        getAttributes(context, attrs)
        inflate()
    }
    constructor(context: Context) : super(context) {
        getAttributes(context)
        inflate()
    }

    var isDeleted = false
        private set
    private var isInitialized = false
    private var isLayoutInflated = false
    private var mGap = 0f
    private var mIcon = 0
    private var mHint: String? = null
    private var mGapIcon = Float.NaN
    private var mTextSize = Float.NaN
    private var mPreferredHeightButtons = Float.NaN
    private val mViewIcon: ImageView by LazyView(R.id.searchbar_imgIcon)
    private val mEdtSearch by LazyView<POISearchTextView>(R.id.searchbar_edtSearch).init { it.applyRandomId() }
    private val mLayoutButtons: LinearLayout by LazyView(R.id.searchbar_layoutButtons)
    private var mIsFocused = false
    private var mAnimationMargin = ValueAnimator().apply { addUpdateListener {
        val marg = (it.animatedValue as Float).roundToInt()
        mLayoutButtons.updateLayoutParams<LayoutParams> { topMargin = marg; bottomMargin = marg }
    }; interpolator = FastOutSlowInInterpolator() }

    var searchStationAPI
        get() = mEdtSearch.searchPOIAPI
        set(value) { mEdtSearch.searchPOIAPI = value }
    var currentPOI
        get() = mEdtSearch.currentPOI
        set(value) { mEdtSearch.currentPOI = value }
    val onItemSelectedListeners get() = mEdtSearch.onItemSelectedListeners
    val onFocusChangeListeners get() = mEdtSearch.onFocusChangeListeners

    private fun inflate() {
        inflate(context, R.layout.comp_searchbar_station, this)
        isLayoutInflated = true
    }

    private fun getAttributes(context: Context, attrs: AttributeSet?=null, defStyleAttr: Int=0, defStyleRes: Int=0) {
        val styledAttr = context.obtainStyledAttributes(attrs, R.styleable.StationSearchbar, defStyleAttr, defStyleRes)
        try {
            mGap = styledAttr.getDimension(R.styleable.StationSearchbar_gap, mGap)
            mIcon = styledAttr.getResourceId(R.styleable.StationSearchbar_srcIcon, mIcon)
            mHint = styledAttr.getString(R.styleable.StationSearchbar_hint)
            mGapIcon = styledAttr.getDimension(R.styleable.StationSearchbar_gapIcon, mGapIcon)
            mTextSize = styledAttr.getDimension(R.styleable.StationSearchbar_textSize, mTextSize)
            mPreferredHeightButtons = styledAttr.getDimension(R.styleable.StationSearchbar_preferredHeightButtons, mPreferredHeightButtons)
        } finally {
            styledAttr.recycle()
        }
    }

    fun setHint(hint: String?) {
        if (hint == null) { return }
        mEdtSearch.hint = hint
    }

    fun setHint(@StringRes hintRes: Int) {
        if (hintRes == 0) { return }
        mEdtSearch.setHint(hintRes)
    }

    fun setText(text: String) {
        mEdtSearch.setText(text)
    }

    fun setTextSize(@DimenRes resId: Int) {
        if (resId == 0) { return }
        setTextSize(resources.getDimension(resId))
    }

    fun setTextSize(size: Float) {
        if (size.isNaN()) { return }
        mEdtSearch.setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
    }

    fun setIcon(@DrawableRes resId: Int) {
        if (resId == 0) {
            mViewIcon.visibility = View.GONE
            return
        }

        try {
            mViewIcon.setImageResource(resId)
        } catch (_: NotFoundException) {
            mViewIcon.setImageDrawable(VectorDrawableCompat.create(resources, resId, null))
        }
        mViewIcon.visibility = View.VISIBLE
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        init()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        init()
        animateIntoExistence()
    }

    private fun init() {
        if (isInitialized) { return }
        isInitialized = true
        isLayoutInflated = true
        clipChildren = false
        mLayoutButtons.clipChildren = false
        mLayoutButtons.updateLayoutParams<LayoutParams> { topMargin = (mGap/2).roundToInt(); bottomMargin = (mGap/2).roundToInt() }

        if (mIcon != 0) { setIcon(mIcon) }
        mViewIcon.setOnClickListener { mEdtSearch.requestFocus() }
        if (!mGapIcon.isNaN()) { mEdtSearch.updateLayoutParams<LayoutParams> { leftMargin = mGapIcon.roundToInt() } }

        setHint(mHint)
        setTextSize(mTextSize)
        if (!mPreferredHeightButtons.isNaN()) mEdtSearch.onFocusChangeListeners.add { focused ->
            val duration = 250L
            mIsFocused = focused
            if (focused) {
                animateMargins((height-mPreferredHeightButtons)/2f, duration)
                mLayoutButtons.children.forEach { (it as? AnimatableImageButton)?.animateToPreferredState(duration) }
                fadeButtonsInAndOthersOut(duration)
            } else post {
                animateMargins(mGap/2, duration)
                mLayoutButtons.children.forEach { (it as? AnimatableImageButton)?.animateToMinimizedState(duration) }
                fadeOtherButtonsInIfNoOtherChildHasFocus(duration)
            }
        }
    }

    fun hasButtonFocus(): Boolean {
        return mIsFocused
    }

    private fun fadeButtonsInAndOthersOut(duration: Long) {
        fadeButtonsIn(duration)
        val p = parent as ViewGroup
        for (child in p.children) {
            if (child !is POISearchbar) { continue }
            if (child == this) { continue }
            child.fadeButtonsOut(duration)
        }
    }

    private fun fadeOtherButtonsInIfNoOtherChildHasFocus(duration: Long) {
        val p = parent as ViewGroup
        var anotherChildHasFocus = false

        for (child in p.children) {
            if (child !is POISearchbar) { continue }
            anotherChildHasFocus = anotherChildHasFocus || child.hasButtonFocus()
        }

        if (anotherChildHasFocus) { return }

        for (child in p.children) {
            if (child !is POISearchbar) { continue }
            child.fadeButtonsIn(duration)
        }
    }

    fun fadeButtonsIn(duration: Long=200L) {
        mLayoutButtons.isUserInteractionEnabled(true)
        mLayoutButtons.animate().alpha(1f).setDuration(duration).start()
    }

    fun fadeButtonsOut(duration: Long=200L) {
        ViewCompat.animate(mLayoutButtons).alpha(0f).setDuration(duration).withEndAction { mLayoutButtons.isUserInteractionEnabled(false) }.start()
    }

    private fun animateMargins(to: Float, duration: Long) {
        mAnimationMargin.cancel()
        val from = mLayoutButtons.marginTop.toFloat()
        mAnimationMargin.setFloatValues(from, to)
        mAnimationMargin.setDuration(duration)
        mAnimationMargin.start()
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        super.onRestoreInstanceState(state)
        clipChildren = false
        clipToPadding = false
        mLayoutButtons.clipChildren = false
        mLayoutButtons.clipToPadding = false
    }

    override fun clearFocus() {
        mEdtSearch.clearFocus()
    }

    fun updateSearch() {
        mEdtSearch.updateSearch()
    }

    fun ntUpdateSearch() {
        mEdtSearch.ntUpdateSearch()
    }

    private fun shouldAddViewsToInnerLayout(): Boolean {
        return isLayoutInflated
    }

    override fun addView(child: View?) {
        if (!shouldAddViewsToInnerLayout()) { super.addView(child); return }
        mLayoutButtons.addView(child)
    }
    override fun addView(child: View?, index: Int) {
        if (!shouldAddViewsToInnerLayout()) { super.addView(child, index); return }
        mLayoutButtons.addView(child, index)
    }
    override fun addView(child: View?, params: ViewGroup.LayoutParams?) {
        if (!shouldAddViewsToInnerLayout()) { super.addView(child, params); return }
        mLayoutButtons.addView(child, convertLayoutParams(params))
    }
    override fun addView(child: View?, index: Int, params: ViewGroup.LayoutParams?) {
        if (!shouldAddViewsToInnerLayout()) { super.addView(child, index, params); return }
        mLayoutButtons.addView(child, index, convertLayoutParams(params))
    }
    override fun addView(child: View?, width: Int, height: Int) {
        if (!shouldAddViewsToInnerLayout()) { super.addView(child, width, height); return }
        mLayoutButtons.addView(child, width, height)
    }

    private fun convertLayoutParams(params: ViewGroup.LayoutParams?): LinearLayout.LayoutParams? {
        if (params == null) { return null }
        val paramsC = LinearLayout.LayoutParams(params)
        if (params is LayoutParams) {
            paramsC.leftMargin = params.leftMargin
            paramsC.topMargin = params.topMargin
            paramsC.rightMargin = params.rightMargin
            paramsC.bottomMargin = params.bottomMargin
        }
        return paramsC
    }

    fun removeFromParent(duration: Long) {
        clearFocus()
        isDeleted = true
        isUserInteractionEnabled(false)

        animateVerticalMargins(marginTop.toFloat(), -height/2f, duration)
        ViewCompat.animate(this).scaleY(0f).alpha(0.2f).setDuration(duration).setInterpolator(mMarginsVerticalInterpolator).withEndAction {
            (this.parent as? ViewGroup)?.removeView(this)
        }.start()
    }

    private var mAnimateIntoExistenceDuration = 0L
    fun animateIntoExistence(duration: Long) {
        mAnimateIntoExistenceDuration = duration
        scaleY = 0f
        alpha = 0.2f
    }

    private fun animateIntoExistence() {
        if (mAnimateIntoExistenceDuration <= 0) { return }
        measure( // get expected height -> animate from this expected height
            MeasureSpec.makeMeasureSpec((parent as? View)?.width ?: 0, MeasureSpec.UNSPECIFIED),
            MeasureSpec.makeMeasureSpec((parent as? View)?.height ?: 0, MeasureSpec.AT_MOST)
        )
        animateVerticalMargins(-measuredHeight/2f, 0f, mAnimateIntoExistenceDuration)
        ViewCompat.animate(this).scaleY(1f).alpha(1f).setDuration(mAnimateIntoExistenceDuration).setInterpolator(mMarginsVerticalInterpolator).start()
    }

    private var mMarginsVerticalInterpolator = FastOutSlowInInterpolator()
    private var mMarginsVerticalAnimator = ValueAnimator().apply {
        addUpdateListener {
            updateLayoutParams<LinearLayout.LayoutParams> { val marg = (it.animatedValue as Float).roundToInt(); topMargin = marg; bottomMargin = marg }
        }
        interpolator = mMarginsVerticalInterpolator
    }
    private fun animateVerticalMargins(from: Float, to: Float, duration: Long) {
        mMarginsVerticalAnimator.cancel()
        mMarginsVerticalAnimator.setFloatValues(from, to)
        mMarginsVerticalAnimator.duration = duration
        mMarginsVerticalAnimator.start()
    }
}