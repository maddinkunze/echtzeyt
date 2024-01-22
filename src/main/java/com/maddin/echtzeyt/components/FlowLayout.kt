package com.maddin.echtzeyt.components

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import com.maddin.echtzeyt.R


// thanks to (the first answer at the time of writing of)
// https://stackoverflow.com/questions/2961777/android-linearlayout-horizontal-with-wrapping-children
// for basically all of the following code
class FlowLayout : ViewGroup {
    private var heightLine = 0
    private var defaultLayoutParams = LayoutParams(1, 1)

    class LayoutParams
    /**
     * @param spacingHorizontal Pixels between items, horizontally
     * @param spacingVertical Pixels between items, vertically
     */(val spacingHorizontal: Int, val spacingVertical: Int) :
        ViewGroup.LayoutParams(0, 0) {
    }

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        loadAttributes(attrs)
    }
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        loadAttributes(attrs, defStyleAttr)
    }
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        loadAttributes(attrs, defStyleAttr, defStyleRes)
    }

    private fun loadAttributes(attrs: AttributeSet?, defStyleAttr: Int = 0, defStyleRes: Int = 0) {
        val styledAttr = context.theme.obtainStyledAttributes(attrs, R.styleable.FlowLayout, defStyleAttr, defStyleRes)
        try {
            val spacingHorizontal = styledAttr.getDimensionPixelSize(R.styleable.FlowLayout_layout_spacing_horizontal, 0)
            val spacingVertical = styledAttr.getDimensionPixelSize(R.styleable.FlowLayout_layout_spacing_vertical, 0)
            defaultLayoutParams = LayoutParams(spacingHorizontal, spacingVertical)
            this.layoutParams = defaultLayoutParams
        } finally {
            styledAttr.recycle()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        assert(MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED)
        val width = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        var height = MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom
        val count = childCount
        var heightLine = 0
        var xpos = paddingLeft
        var ypos = paddingTop
        var spacingVerticalLastLine = 0
        val childHeightMeasureSpec = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.AT_MOST -> MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST)
            else -> MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        }
        for (i in 0 until count) {
            val child = getChildAt(i)
            if (child.visibility == GONE) { continue }

            val lp = child.layoutParams as LayoutParams
            child.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), childHeightMeasureSpec)
            val widthChild = child.measuredWidth
            heightLine = heightLine.coerceAtLeast(child.measuredHeight + lp.spacingVertical)
            spacingVerticalLastLine = spacingVerticalLastLine.coerceAtLeast(lp.spacingVertical)
            if (xpos + widthChild > width) {
                xpos = paddingLeft
                ypos += heightLine
                spacingVerticalLastLine = lp.spacingVertical
            }
            xpos += widthChild + lp.spacingHorizontal
        }

        this.heightLine = heightLine
        height = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.UNSPECIFIED -> ypos + heightLine - spacingVerticalLastLine
            MeasureSpec.AT_MOST -> height.coerceAtMost(ypos + heightLine - spacingVerticalLastLine)
            else -> height
        }
        setMeasuredDimension(width, height)
    }

    override fun generateDefaultLayoutParams(): ViewGroup.LayoutParams {
        return defaultLayoutParams
    }

    override fun generateLayoutParams(p: ViewGroup.LayoutParams): ViewGroup.LayoutParams {
        return defaultLayoutParams
    }

    override fun checkLayoutParams(p: ViewGroup.LayoutParams): Boolean {
        return p is LayoutParams
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val count = childCount
        val width = r - l
        var xpos = paddingLeft
        var ypos = paddingTop
        for (i in 0 until count) {
            val child = getChildAt(i)
            if (child.visibility != GONE) {
                val childw = child.measuredWidth
                val childh = child.measuredHeight
                val lp = child.layoutParams as LayoutParams
                if (xpos + childw > width) {
                    xpos = paddingLeft
                    ypos += heightLine
                }
                child.layout(xpos, ypos, xpos + childw, ypos + childh)
                xpos += childw + lp.spacingHorizontal
            }
        }
    }
}