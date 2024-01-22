package com.maddin.echtzeyt.components

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.AdapterView
import androidx.appcompat.widget.AppCompatAutoCompleteTextView
import androidx.core.animation.doOnCancel
import androidx.core.animation.doOnEnd
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.maddin.echtzeyt.R
import kotlin.math.roundToInt

// Inspired by https://stackoverflow.com/questions/70612110/why-wont-the-autocompletetextview-showdropdown-not-trigger-on-call
open class InstantAutoCompleteTextView : AppCompatAutoCompleteTextView {
    constructor(context: Context) : super(context) {
        getAttributes(context)
    }
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        getAttributes(context, attrs)
    }
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        getAttributes(context, attrs, defStyleAttr)
    }

    val onFocusChangeListeners = mutableListOf<(Boolean) -> Unit>()

    private var mTextSizeNormal = Float.NaN
    private var mTextSizeFocus = Float.NaN
    private var mHeightNormal = Float.NaN
    private var mHeightFocus = Float.NaN

    private var mAnimationHeight: ValueAnimator? = null
    private var mAnimationTextSize: ValueAnimator? = null

    var isAnimated = true
        set(value) { field = value; if (!value) { setTextSize(TypedValue.COMPLEX_UNIT_PX, mTextSizeFocus) } }

    private fun getAttributes(context: Context, attrs: AttributeSet?=null, defStyleAttr: Int=0, defStyleRes: Int=0) {
        val styledAttr = context.obtainStyledAttributes(attrs, R.styleable.InstantAutoCompleteTextView, defStyleAttr, defStyleRes)
        try {
            mTextSizeFocus = styledAttr.getDimension(R.styleable.InstantAutoCompleteTextView_textSizeFocus, mTextSizeFocus)
            mHeightFocus = styledAttr.getDimension(R.styleable.InstantAutoCompleteTextView_heightFocus, mHeightFocus)
        } finally {
            styledAttr.recycle()
        }
    }

    override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect)

        // TODO: this may include a bug where the animation does not correctly return to its original state
        // when the textview is focused and the layout/orientation/... is changed the new "mHeightNormal" and "mTextSizeNormal" may be different from what they were the first time around
        if (isAnimated) if (focused) {
            if (!mHeightFocus.isNaN()) {
                if (mHeightNormal.isNaN()) { mHeightNormal = height.toFloat() }
                animateHeight(mHeightNormal, mHeightFocus, 200)
            }
            if (!mTextSizeFocus.isNaN()) {
                if (mTextSizeNormal.isNaN()) { mTextSizeNormal = textSize }
                animateTextSize(mTextSizeNormal, mTextSizeFocus, 200)
            }
        } else {
            if (!mHeightNormal.isNaN()) {
                animateHeight(height.toFloat(), mHeightNormal, 200)
            }
            if (!mTextSizeNormal.isNaN()) {
                animateTextSize(textSize, mTextSizeNormal, 200)
            }
        }

        onFocusChangeListeners.forEach { it(focused) }
    }

    private fun animateHeight(from: Float, to: Float, duration: Long): ValueAnimator {
        mAnimationHeight?.cancel()
        val animationHeight = ValueAnimator.ofFloat(from, to)
        animationHeight.addUpdateListener {
            updateLayoutParams { height = (it.animatedValue as Float).roundToInt() }
        }
        mAnimationHeight = animationHeight
        animationHeight.setDuration(duration).start()
        return animationHeight
    }

    private fun animateTextSize(from: Float, to: Float, duration: Long): ValueAnimator {
        mAnimationTextSize?.cancel()
        val animationTextSize = ValueAnimator.ofFloat(from, to)
        animationTextSize.addUpdateListener {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, it.animatedValue as Float)
        }
        mAnimationTextSize = animationTextSize
        animationTextSize.setDuration(duration).start()
        return animationTextSize
    }

    override fun enoughToFilter(): Boolean {
        return true
    }

    // basically showDropDown but somehow works
    fun showSuggestions(): Boolean {
        return if (windowVisibility == VISIBLE) {
            performFiltering("", 0)
            showDropDown()
            true
        } else {
            false
        }
    }

    override fun performFiltering(text: CharSequence?, keyCode: Int) {}

    fun addOnTextChangedListener(listener: (text: CharSequence?) -> Unit) {
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, i1: Int, i2: Int, i3: Int) {}
            override fun onTextChanged(text: CharSequence?, i1: Int, i2: Int, i3: Int) { listener(text) }
            override fun afterTextChanged(text: Editable?) {}
        })
    }

    fun addOnItemSelectedListener(listener: () -> Unit) {
        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) { listener() }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
        onItemClickListener = AdapterView.OnItemClickListener { _, _, _, _ -> listener() }
    }
}