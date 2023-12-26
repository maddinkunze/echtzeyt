package com.maddin.echtzeyt.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RoundRectShape
import android.util.AttributeSet
import android.util.TypedValue
import android.widget.SeekBar
import androidx.appcompat.widget.AppCompatSeekBar
import com.maddin.echtzeyt.R
import kotlin.math.max
import kotlin.math.roundToInt


class LabeledDiscreteSeekBar(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : AppCompatSeekBar(context, attrs, defStyleAttr) {
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context) : this(context, null)

    private var paintText = Paint()
    private var mPaddingThumb = floatArrayOf(0f, 0f, 0f, 0f)

    private var mItems = mutableListOf<String>()
    private var mListeners = mutableListOf<OnSelectBarSelectListener>()

    private var mPaddingInternal = arrayOf(0, 0, 0, 0)
    private var mPaddingExternal = arrayOf(0, 0, 0, 0)

    private var mThumb: ShapeDrawable
    private var mBackground: ShapeDrawable


    init {
        val displayMetrics = context.resources.displayMetrics

        var textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10f, displayMetrics)
        var thumbPadding = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2f, displayMetrics)
        var thumbRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3f, displayMetrics)
        var thumbColor: Int = Color.rgb(160, 160, 180)
        var trackColor: Int = Color.rgb(200, 200, 200)
        var textColor: Int = Color.rgb(20, 20, 20)
        var trackHeight = 0f

        val styledAttr = context.theme.obtainStyledAttributes(attrs, R.styleable.LabeledDiscreteSeekBar, defStyleAttr, 0)
        try {
            textSize = styledAttr.getDimension(R.styleable.LabeledDiscreteSeekBar_thumbTextSize, textSize)
            thumbPadding = styledAttr.getDimension(R.styleable.LabeledDiscreteSeekBar_thumbPadding, thumbPadding)
            mPaddingThumb[0] = styledAttr.getDimension(R.styleable.LabeledDiscreteSeekBar_thumbPaddingLeft, thumbPadding)
            mPaddingThumb[1] = styledAttr.getDimension(R.styleable.LabeledDiscreteSeekBar_thumbPaddingTop, thumbPadding)
            mPaddingThumb[2] = styledAttr.getDimension(R.styleable.LabeledDiscreteSeekBar_thumbPaddingRight, thumbPadding)
            mPaddingThumb[3] = styledAttr.getDimension(R.styleable.LabeledDiscreteSeekBar_thumbPaddingBottom, thumbPadding)
            thumbRadius = styledAttr.getDimension(R.styleable.LabeledDiscreteSeekBar_thumbRadius, thumbRadius)
            thumbColor = styledAttr.getColor(R.styleable.LabeledDiscreteSeekBar_thumbTint, thumbColor)
            trackColor = styledAttr.getColor(R.styleable.LabeledDiscreteSeekBar_trackTint, trackColor)
            trackHeight = styledAttr.getDimension(R.styleable.LabeledDiscreteSeekBar_trackHeight, trackHeight)
            textColor = styledAttr.getColor(R.styleable.LabeledDiscreteSeekBar_labelColor, textColor)
        } finally {
            styledAttr.recycle()
        }


        //then obtain typed array
        val paddingAttr = context.obtainStyledAttributes(attrs, intArrayOf(android.R.attr.paddingLeft, android.R.attr.paddingTop, android.R.attr.paddingRight, android.R.attr.paddingBottom))

        try {
            for (i in mPaddingExternal.indices) {
                if (!paddingAttr.hasValue(i)) { continue }
                mPaddingExternal[i] = paddingAttr.getDimensionPixelOffset(i, 0)
            }
        } finally {
            paddingAttr.recycle()
        }


        paintText.textSize = textSize
        paintText.color = textColor
        val textHeight = paintText.fontMetrics.descent - paintText.fontMetrics.ascent

        super.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                for (listener in mListeners) {
                    if (listener !is OnSelectBarChangeListener) { continue }
                    listener.onStopTrackingTouch(this@LabeledDiscreteSeekBar)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                for (listener in mListeners) {
                    if (listener !is OnSelectBarChangeListener) { continue }
                    listener.onStartTrackingTouch(this@LabeledDiscreteSeekBar)
                }
            }

            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val index = progress.coerceIn(0, max(0, mItems.size-1))
                var item = ""
                if ((index > 0) && (index < mItems.size)) { item = mItems[index] }
                for (listener in mListeners) {
                    listener.onItemSelected(this@LabeledDiscreteSeekBar, item, index, fromUser)
                }
            }
        })

        mThumb = ShapeDrawable(RoundRectShape(FloatArray(8) { thumbRadius }, null, null))
        mThumb.paint.color = thumbColor
        mThumb.intrinsicHeight = (textHeight + mPaddingThumb[1] + mPaddingThumb[3]).roundToInt()
        thumb = mThumb
        mBackground = ShapeDrawable(RoundRectShape(FloatArray(8) { thumbRadius }, null, null))
        mBackground.intrinsicHeight = trackHeight.roundToInt()
        mBackground.paint.color = trackColor
        background = null

        recalculateThumbInternal()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        recalculateThumbInternal()
        mBackground.intrinsicWidth = w
        mBackground.invalidateSelf()
        super.onSizeChanged(w, h, oldw, oldh)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        recalculateThumb()
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        setMeasuredDimension(measuredWidth, measuredHeight.coerceAtLeast(mThumb.intrinsicHeight))
    }

    override fun onDraw(canvas: Canvas) {
        canvas.save()
        canvas.translate(0f, 0.5f * (height - mBackground.intrinsicHeight))
        mBackground.setBounds(0, 0, mBackground.intrinsicWidth, mBackground.intrinsicHeight)
        mBackground.draw(canvas)
        canvas.restore()

        canvas.save()
        canvas.translate(mPaddingExternal[0].toFloat(), mPaddingExternal[1].toFloat())
        thumb.draw(canvas)

        val mProgress = progress.coerceIn(0, mItems.size)
        var text = "Item ${mProgress+1}"
        if (mProgress < mItems.size) {
            text = mItems[mProgress]
        }

        val leftText = thumb.bounds.left + mPaddingThumb[0] + 0.5f * (thumb.intrinsicWidth - paintText.measureText(text) - mPaddingThumb[0] - mPaddingThumb[2])
        val topText = thumb.bounds.top + mPaddingThumb[1] - paintText.fontMetrics.ascent
        canvas.drawText(text, leftText, topText, paintText)
        canvas.restore()
    }

    @Deprecated("Please use the setOnChangeListener instead", ReplaceWith("addOnChangeListener", "com.maddin.echtzeyt.components.ToolTippedDiscreteSeekBar"))
    override fun setOnSeekBarChangeListener(l: OnSeekBarChangeListener?) {
        throw IllegalStateException("Sorry, this function can no longer be used. Please use the addOnChangeListener function instead.")
    }


    interface OnSelectBarSelectListener {
        fun onItemSelected(seekBar: LabeledDiscreteSeekBar, item: String, index: Int, fromUser: Boolean)
    }
    interface OnSelectBarChangeListener : OnSelectBarSelectListener {
        fun onStopTrackingTouch(seekBar: LabeledDiscreteSeekBar)
        fun onStartTrackingTouch(seekBar: LabeledDiscreteSeekBar)
    }
    fun addOnChangeListener(listener: OnSelectBarSelectListener) {
        mListeners.add(listener)
    }
    fun addOnChangeListener(listener: (seekBar: LabeledDiscreteSeekBar, item: String, index: Int, fromUser: Boolean) -> Unit) {
        addOnChangeListener(object : OnSelectBarSelectListener {
            override fun onItemSelected(seekBar: LabeledDiscreteSeekBar, item: String, index: Int, fromUser: Boolean) {
                listener(seekBar, item, index, fromUser)
            }
        })
    }

    fun setItems(items: Array<String>) {
        mItems.clear()
        for (item in items) {
            mItems.add(item)
        }
        max = mItems.size-1
        recalculateThumb()
    }

    private fun recalculateThumbInternal() {
        val paddingLR = mPaddingThumb[0] + mPaddingThumb[2]
        var width = mThumb.intrinsicHeight.toFloat()
        val items = if (mItems.size > 0) { mItems } else { List(max.coerceAtLeast(1)) { i -> "Item $i" } }
        for (item in items) {
            val nWidth = paintText.measureText(item) + paddingLR
            if (nWidth < width) { continue }
            width = nWidth
        }

        mThumb.intrinsicWidth = width.roundToInt()
        thumb = mThumb

        // Set the padding such that the thumb can never be hidden behind the edges of the device
        val paddingThumb = (width/2).toInt()
        setPaddingInternal(paddingThumb, 0, paddingThumb, 0, true)
        invalidateDrawable(thumb)
    }

    private fun recalculateThumb() {
        recalculateThumbInternal()
        invalidate()
    }

    private fun setPaddingInternal(left: Int, top: Int, right: Int, bottom: Int, internal: Boolean) {
        var mPd = mPaddingExternal
        if (internal) { mPd = mPaddingInternal }
        mPd[0] = left
        mPd[1] = top
        mPd[2] = right
        mPd[3] = bottom

        val pd = IntArray(4) { i: Int -> mPaddingInternal[i] + mPaddingExternal[i] }
        super.setPadding(pd[0], pd[1], pd[2], pd[3])
    }

    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        setPaddingInternal(left, top, right, bottom, false)
    }
}