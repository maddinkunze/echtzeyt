package com.maddin.echtzeyt.components

import android.content.Context
import android.util.AttributeSet
import android.widget.ScrollView

class BottomFadeEdgeScrollView : ScrollView {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    override fun getTopFadingEdgeStrength(): Float { return 0.0f }
}