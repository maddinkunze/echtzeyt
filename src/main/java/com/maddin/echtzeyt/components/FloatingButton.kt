package com.maddin.echtzeyt.components

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageButton
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.children
import com.maddin.echtzeyt.R
import com.maddin.echtzeyt.randomcode.applyRandomViewId


class FloatingButton(context: Context, private val attrs: AttributeSet?, private val defStyleAttr: Int) : AppCompatImageButton(context, attrs, defStyleAttr) {
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context) : this(context, null)

    private val mShadow by lazy { DropShadow(context, attrs, defStyleAttr) }

    private var mShadowSize = 0
    private var mRadiusInner = 0
    private var mColorStops: Map<Float, Int>? = null

    private var mShadowBelow = 0
    private var mShadowAboveIndex = 0

    init {
        applyRandomViewId(this)

        val styledAttr = context.theme.obtainStyledAttributes(attrs, R.styleable.FloatingButton, defStyleAttr, 0)
        try {
            mShadowSize = styledAttr.getDimensionPixelSize(R.styleable.FloatingButton_shadowSize, mShadowSize)
            mRadiusInner = styledAttr.getDimensionPixelSize(R.styleable.FloatingButton_radiusInner, mRadiusInner)
            mColorStops = styledAttr.getShadowColors(R.styleable.FloatingButton_shadowColors, R.styleable.FloatingButton_shadowStops, resources) ?: mColorStops

            mShadowBelow = styledAttr.getResourceId(R.styleable.FloatingButton_shadowBelow, mShadowBelow)
            mShadowAboveIndex = styledAttr.getInteger(R.styleable.FloatingButton_shadowAboveIndex, mShadowAboveIndex)
        } finally {
            styledAttr.recycle()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        addShadow()
    }

    private fun addShadow() {
        if (mShadowSize <= 0) { return }

        val layout = parent as ViewGroup
        if (layout.children.contains(mShadow)) { return }

        println("MADDIN101: addshadow step3")
        applyRandomViewId(mShadow)
        mShadow.setShadowSize(mShadowSize)
        mShadow.setInnerRadius(mRadiusInner)
        mColorStops?.let { mShadow.setColorStops(it) }

        var belowView = layout.children.find { it.id == mShadowBelow }
        if (belowView == null) { belowView = this }
        var index = layout.children.indexOf(belowView)
        index = (index-1+mShadowAboveIndex).coerceAtLeast(0).coerceAtMost(layout.children.count())

        layout.addView(mShadow, index)

        if (layout !is ConstraintLayout) { return }

        val constraints = ConstraintSet()
        constraints.clone(layout)
        constraints.connect(mShadow.id, ConstraintSet.LEFT, id, ConstraintSet.LEFT)
        constraints.connect(mShadow.id, ConstraintSet.TOP, id, ConstraintSet.TOP)
        constraints.connect(mShadow.id, ConstraintSet.RIGHT, id, ConstraintSet.RIGHT)
        constraints.connect(mShadow.id, ConstraintSet.BOTTOM, id, ConstraintSet.BOTTOM)
        constraints.applyTo(layout)
    }
}