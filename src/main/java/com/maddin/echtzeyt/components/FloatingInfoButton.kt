package com.maddin.echtzeyt.components

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.children
import com.maddin.echtzeyt.R
import com.maddin.echtzeyt.randomcode.applyRandomViewId

class FloatingInfoButton(context: Context, private val attrs: AttributeSet?, private val defStyleAttr: Int) : LinearLayout(context, attrs, defStyleAttr) {
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context) : this(context, null)

    private val mShadow by lazy { DropShadow(context, attrs, defStyleAttr) }
    private var mShadowAttached = false

    private var mShadowSize = 0
    private var mRadiusInner = 0
    private var mColorStops: Map<Float, Int>? = null

    private var mShadowBelow = 0
    private var mShadowAboveIndex = 0

    val text by lazy { findViewById<TextView>(R.id.flib_label) }
    val button by lazy { findViewById<ImageButton>(R.id.flib_button) }

    init {
        applyRandomViewId(this)

        LayoutInflater.from(context).inflate(R.layout.button_info, this, true)

        val styledAttr = context.theme.obtainStyledAttributes(attrs, R.styleable.FloatingInfoButton, defStyleAttr, 0)
        try {
            text.text = styledAttr.getText(R.styleable.FloatingInfoButton_text) ?: "Info Button"
            val resId = styledAttr.getResourceId(R.styleable.FloatingInfoButton_icon, 0)
            if (resId > 0) { button.setImageResource(resId) }

            mShadowSize = styledAttr.getDimensionPixelSize(R.styleable.FloatingInfoButton_shadowSize, mShadowSize)
            mRadiusInner = styledAttr.getDimensionPixelSize(R.styleable.FloatingInfoButton_radiusInner, mRadiusInner)
            mColorStops = styledAttr.getShadowColors(R.styleable.FloatingInfoButton_shadowColors, R.styleable.FloatingInfoButton_shadowStops, resources) ?: mColorStops

            mShadowBelow = styledAttr.getResourceId(R.styleable.FloatingInfoButton_shadowBelow, mShadowBelow)
            mShadowAboveIndex = styledAttr.getInteger(R.styleable.FloatingInfoButton_shadowAboveIndex, mShadowAboveIndex)
        } finally {
            styledAttr.recycle()
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        addShadow()
    }

    private fun addShadow() {
        if (mShadowAttached) { return }
        if (mShadowSize <= 0) { return }

        val layout = parent as ViewGroup
        if (layout.children.contains(mShadow)) { return }

        applyRandomViewId(mShadow)
        mShadow.setShadowSize(mShadowSize)
        mShadow.setInnerRadius(mRadiusInner)
        mColorStops?.let { mShadow.setColorStops(it) }

        var belowView = layout.children.find { it.id == mShadowBelow }
        if (belowView == null) { belowView = this }
        var index = layout.children.indexOf(belowView)
        index = (index+mShadowAboveIndex).coerceAtLeast(0).coerceAtMost(layout.children.count())

        layout.addView(mShadow, index)

        if (layout !is ConstraintLayout) { return }

        val constraints = ConstraintSet()
        constraints.clone(layout)
        constraints.connect(mShadow.id, ConstraintSet.LEFT, id, ConstraintSet.LEFT)
        constraints.connect(mShadow.id, ConstraintSet.TOP, id, ConstraintSet.TOP)
        constraints.connect(mShadow.id, ConstraintSet.RIGHT, id, ConstraintSet.RIGHT)
        constraints.connect(mShadow.id, ConstraintSet.BOTTOM, id, ConstraintSet.BOTTOM)
        constraints.applyTo(layout)

        mShadowAttached = true
    }

    override fun onSetAlpha(alpha: Int): Boolean {
        mShadow.alpha = alpha / 255f
        return super.onSetAlpha(alpha)
    }
}