package com.maddin.echtzeyt.components

import android.content.Context
import android.util.AttributeSet
import androidx.constraintlayout.widget.ConstraintLayout
import com.maddin.echtzeyt.randomcode.applyRandomId

class FloatingConstraintLayout(context: Context, private val attrs: AttributeSet?, private val defStyleAttr: Int) : ConstraintLayout(context, attrs, defStyleAttr), DropShadowView {
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
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        addShadow()
    }

    override fun onSetAlpha(alpha: Int): Boolean {
        setShadowAlpha(alpha)
        return super.onSetAlpha(alpha)
    }
}