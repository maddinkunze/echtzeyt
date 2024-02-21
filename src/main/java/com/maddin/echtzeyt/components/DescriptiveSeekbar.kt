package com.maddin.echtzeyt.components

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.maddin.echtzeyt.R
import com.maddin.echtzeyt.randomcode.LazyView

class DescriptiveSeekbar : ConstraintLayout {
    constructor(context: Context) : super(context) { init(context) }
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) { init(context, attrs) }
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { init(context, attrs, defStyleAttr) }

    val seekbar: LabeledDiscreteSeekBar by LazyView(R.id.dsk_seekbar)
    val txtTitle: TextView by LazyView(R.id.dsk_txtTitle)
    val txtDescription: TextView by LazyView(R.id.dsk_txtDescription)

    private fun init(context: Context, attrs: AttributeSet?=null, defStyleAttr: Int=0, defStyleRes: Int=0) {
        inflate(context, R.layout.comp_descriptive_seekbar, this)

        val styledAttr = context.obtainStyledAttributes(attrs, R.styleable.DescriptiveSeekbar, defStyleAttr, defStyleRes)
        try {
            txtTitle.text = styledAttr.getString(R.styleable.DescriptiveSeekbar_title) ?: ""
            txtDescription.text = styledAttr.getString(R.styleable.DescriptiveSeekbar_description) ?: ""
            if (txtDescription.text.isBlank()) { txtDescription.visibility = GONE }
        } catch(_: Throwable) {
            styledAttr.recycle()
        }
    }
}