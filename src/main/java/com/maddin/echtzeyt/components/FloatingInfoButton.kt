package com.maddin.echtzeyt.components

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.maddin.echtzeyt.R
import com.maddin.echtzeyt.randomcode.LazyView
import com.maddin.echtzeyt.randomcode.applyRandomId

class FloatingInfoButton(context: Context, private val attrs: AttributeSet?, private val defStyleAttr: Int) : LinearLayout(context, attrs, defStyleAttr), DropShadowView {
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context) : this(context, null)

    override val mShadow by lazy { DropShadow(context, attrs, defStyleAttr) }
    override var mShadowAttached = false

    override var mShadowSize = 0
    override var mRadiusInner = 0
    override var mColorStops: Map<Float, Int>? = null

    override var mShadowBelow = 0
    override var mShadowAboveIndex = 0

    val text: TextView by LazyView(R.id.flib_label)
    val button: ImageButton by LazyView(R.id.flib_button)

    init {
        applyRandomId()
        readAttributes(context, attrs, defStyleAttr, 0)

        LayoutInflater.from(context).inflate(R.layout.comp_button_info, this, true)

        val styledAttr = context.theme.obtainStyledAttributes(attrs, R.styleable.FloatingInfoButton, defStyleAttr, 0)
        try {
            text.text = styledAttr.getText(R.styleable.FloatingInfoButton_text) ?: "Info Button"
            val resId = styledAttr.getResourceId(R.styleable.FloatingInfoButton_icon, 0)
            if (resId != 0) { button.setImageResource(resId) }
        } finally {
            styledAttr.recycle()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
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

    override fun setOnClickListener(l: OnClickListener?) {
        super.setOnClickListener(l)
        button.setOnClickListener(l)
    }
}