package com.maddin.echtzeyt.components

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.constraintlayout.widget.ConstraintLayout
import com.maddin.echtzeyt.R
import com.maddin.echtzeyt.randomcode.LazyView

class DescriptiveSwitch : ConstraintLayout {
    constructor(context: Context) : super(context) { init(context) }
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) { init(context, attrs) }
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { init(context, attrs, defStyleAttr) }

    val switch: SwitchCompat by LazyView(R.id.dsw_switch)
    val txtTitle: TextView by LazyView(R.id.dsw_txtTitle)
    val txtDescription: TextView by LazyView(R.id.dsw_txtDescription)
    fun init(context: Context, attrs: AttributeSet?=null, defStyleAttr: Int=0, defStyleRes: Int=0) {
        inflate(context, R.layout.comp_descriptive_switch, this)

        val styledAttr = context.obtainStyledAttributes(attrs, R.styleable.DescriptiveSwitch, defStyleAttr, defStyleRes)
        try {
            txtTitle.text = styledAttr.getString(R.styleable.DescriptiveSwitch_title) ?: ""
            txtDescription.text = styledAttr.getString(R.styleable.DescriptiveSwitch_description) ?: ""
            if (txtDescription.text.isBlank()) { txtDescription.visibility = GONE }
        } catch(_: Throwable) {
            styledAttr.recycle()
        }

        txtTitle.setOnClickListener { switch.toggle() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        switch.requestLayout()
    }
}