package com.maddin.echtzeyt.components

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.view.updatePadding
import com.maddin.echtzeyt.R
import com.maddin.echtzeyt.randomcode.applyRandomId
import kotlin.math.roundToInt


class FloatingButton(context: Context, private val attrs: AttributeSet?, private val defStyleAttr: Int) : AppCompatImageButton(context, attrs, defStyleAttr), DropShadowView {
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context) : this(context, null)

    // implementing member variables for DropShadowView
    override val _this = this

    override val mShadow by lazy { DropShadow(context, attrs, defStyleAttr) }
    override var mShadowAttached = false

    override var mShadowSize = 0
    override var mRadiusInner = 0
    override var mColorStops: Map<Float, Int>? = null

    override var mShadowBelow = 0
    override var mShadowAboveIndex = 0

    init {
        applyRandomId()
        readAttributes(context, attrs, defStyleAttr, 0)

        var offsetIconY = 0f

        val styledAttr = context.theme.obtainStyledAttributes(attrs, R.styleable.FloatingButton, defStyleAttr, 0)
        offsetIconY = styledAttr.getFloat(R.styleable.FloatingButton_offsetIconY, offsetIconY)
        styledAttr.recycle()

        val pRef = offsetIconY * drawable.intrinsicHeight // if (offsetIconY < 0) paddingTop else paddingBottom
        val pT = (paddingTop + pRef).roundToInt()
        val pB = (paddingBottom - pRef).roundToInt()
        if (pT != paddingTop && pB != paddingBottom) { updatePadding(top=pT, bottom=pB) }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        addShadow()
    }

    override fun onSetAlpha(alpha: Int): Boolean {
        setShadowAlpha(alpha)
        return super.onSetAlpha(alpha)
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        setShadowVisibility(visibility)
    }
}