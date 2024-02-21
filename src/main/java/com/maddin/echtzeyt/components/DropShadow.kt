package com.maddin.echtzeyt.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.content.res.Resources.NotFoundException
import android.content.res.TypedArray
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ArrayRes
import androidx.annotation.RequiresApi
import androidx.annotation.StyleableRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.res.getFloatOrThrow
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.withClip
import androidx.core.view.children
import androidx.core.view.marginBottom
import androidx.core.view.marginLeft
import androidx.core.view.marginRight
import androidx.core.view.marginTop
import com.maddin.echtzeyt.BuildConfig
import com.maddin.echtzeyt.R
import com.maddin.echtzeyt.randomcode.applyRandomId


typealias Gradient = Map<Float, Int>

private val paintRemove by lazy { val p = Paint(); p.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR); p }

fun createShadowBitmap(widthOrig: Int, heightOrig: Int, radiusOrig: Int, shadowSize: Int, stops: Gradient) : Bitmap? {
    val widthNew = widthOrig + 2 * shadowSize
    val heightNew = heightOrig + 2 * shadowSize
    if (widthNew <= 0 || heightNew <= 0) { return null }

    val bitmap = Bitmap.createBitmap(widthNew, heightNew, Bitmap.Config.ARGB_8888)

    val radiusShadow = (radiusOrig + shadowSize).toFloat()
    val stopsStart = radiusOrig / radiusShadow

    val positionsAdj = stops.keys.map { stopsStart + it * (1 - stopsStart) }

    val colors = IntArray(stops.size+2) { i -> if (i < 2) { Color.TRANSPARENT } else { stops.values.elementAt(i-2) } }
    val positions = FloatArray(stops.size+2) { i -> if (i < 2) { i * stopsStart } else { positionsAdj[i-2] } }

    val shaderLinearGradient = LinearGradient(0f, radiusShadow, 0f, 0f, colors, positions, Shader.TileMode.CLAMP)
    val shaderRadialGradient = RadialGradient(0f, 0f, radiusShadow, colors, positions, Shader.TileMode.CLAMP)

    val paintLinearGradient = Paint()
    paintLinearGradient.shader = shaderLinearGradient

    val paintRadialGradient = Paint()
    paintRadialGradient.shader = shaderRadialGradient

    bitmap.applyCanvas {
        drawRect(0f, 0f, width.toFloat(), height.toFloat(), paintRemove)
        for (i in 0..3) {
            /*val left = radiusShadow + (i and 1) * (width - 2 * radiusShadow)
            val top = radiusShadow + ((i shr 1) and 1) * (height - 2 * radiusShadow)
            save(); translate(left, top); clipRect(0f, 0f, radiusShadow, radiusShadow)*/
            val px = when (i) { 2 -> width; 3 -> height; else -> 0f }.toFloat()
            val py = when (i) { 1 -> width; 2 -> height; else -> 0f }.toFloat()
            save(); rotate(i*90f); translate(-px+radiusShadow, -py+radiusShadow)
            withClip(-radiusShadow, -radiusShadow, 0f, 0f) { drawCircle(0f, 0f, radiusShadow, paintRadialGradient) }
            restore()
        }

        drawRect(0f, radiusShadow, width.toFloat(), height-radiusShadow, paintRemove)
        drawRect(radiusShadow, 0f, width-radiusShadow, height.toFloat(), paintRemove)

        for (i in 0..3) {
            val px = when (i) { 2 -> width; 3 -> height; else -> 0f }.toFloat()
            val py = when (i) { 1 -> width; 2 -> height; else -> 0f }.toFloat()
            save(); rotate(i*90f); translate(-px, -py)
            val widthR = (1-i%2) * width + (i%2) * height - radiusShadow
            drawRect(radiusShadow, 0f, widthR, radiusShadow, paintLinearGradient)
            restore()
        }
    }

    return bitmap
}

fun TypedArray.getTypedArray(@StyleableRes index: Int, resources: Resources) : TypedArray? {
    val resId = getResourceId(index, 0)
    if (resId == 0) { return null }
    val resName = resources.getResourceName(resId)
    if (resName.isNullOrEmpty()) { return null }
    return try { resources.obtainTypedArray(resId) } catch(_: NotFoundException) { null }
}

fun TypedArray.getColorArray(@StyleableRes index: Int, resources: Resources) : IntArray? {
    val arr = getTypedArray(index, resources) ?: return null
    return IntArray(arr.length()) { i -> arr.getColor(i, Color.TRANSPARENT) }.apply { arr.recycle() }
}

fun TypedArray.getFloatArray(@StyleableRes index: Int, resources: Resources) : FloatArray? {
    val arr = getTypedArray(index, resources) ?: return null
    return FloatArray(arr.length()) { i -> arr.getFloat(i, 0f) }.apply { arr.recycle() }
}

private fun zipStopsAndColors(colors: IntArray, stops: FloatArray?) : Gradient {
    var stopsF = { i: Int -> i.toFloat() / (colors.size-1) }
    if (stops != null && stops.size == colors.size) {
        stopsF = { i: Int -> stops[i] }
    }
    return buildMap(colors.size) { colors.forEachIndexed { i, c -> put(stopsF(i), c) } }
}

fun TypedArray.getShadowColors(@StyleableRes colorsRes: Int, @StyleableRes stopsRes: Int, resources: Resources) : Gradient? {
    val colors = getColorArray(colorsRes, resources) ?: return null
    val stops = getFloatArray(stopsRes, resources)
    return zipStopsAndColors(colors, stops)
}

fun Resources.getColorArray(@ArrayRes index: Int) : IntArray {
    return getIntArray(index)
}

fun Resources.getFloatArray(@ArrayRes index: Int) : FloatArray {
    val arrT = obtainTypedArray(index)
    val arrF = FloatArray(arrT.length()) { i -> arrT.getFloatOrThrow(i) }
    arrT.recycle()
    return arrF
}

fun Resources.getShadowColors(@ArrayRes colorsRes: Int, @ArrayRes stopsRes: Int) : Gradient {
    val colors = getColorArray(colorsRes)
    val stops = if (stopsRes == 0) { null } else { getFloatArray(stopsRes) }
    return zipStopsAndColors(colors, stops)
}

// workaround for weird bug where the app would crash when started with dev setting "view attributes" on
// on creating the DropShadow, the view constructor would be called which in term would call
// retrieveExplicitStyle() which would call theme.getExplicitStyle(), resulting in a NPE
// by not passing the attributeset onto the view (but instead null) we can circumvent this behaviour
// https://medium.com/@debuggingisfun/retrieveexplicitstyle-android-10-crash-cef9bced1d01
private val workaroundSetAttrsNull = BuildConfig.DEBUG && Build.VERSION.SDK_INT >= 21

@Suppress("unused")
class DropShadow : View {
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, if (workaroundSetAttrsNull) null else attrs, defStyleAttr, defStyleRes) {
        getAttributes(attrs, defStyleAttr, defStyleRes)
    }
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, if (workaroundSetAttrsNull) null else attrs, defStyleAttr) {
        getAttributes(attrs, defStyleAttr)
    }
    constructor(context: Context, attrs: AttributeSet?) : super(context, if (workaroundSetAttrsNull) null else attrs) {
        getAttributes(attrs)
    }
    constructor(context: Context) : super(context) {
        getAttributes()
    }

    companion object {
        private val colorStopsDefault = mapOf(Pair(0f, Color.argb(24, 0, 0, 0)), Pair(1f, Color.TRANSPARENT))
    }

    private var mBitmapShadow: Bitmap? = null

    private var mMarginLeft = 0
    private var mMarginTop = 0
    private var mMarginRight = 0
    private var mMarginBottom = 0
    private var mShadowSize = 5
    private var mRadiusOriginal = 0
    private var mExtraOffsetBottom = 0

    private var mColorStops = colorStopsDefault

    private var mInitialized = false

    private fun getAttributes(attrs: AttributeSet?=null, defStyleAttr: Int=0, defStyleRes: Int=0) {
        val styledAttr = context.theme.obtainStyledAttributes(attrs, R.styleable.DropShadow, defStyleAttr, defStyleRes)
        try {
            mShadowSize = styledAttr.getDimensionPixelSize(R.styleable.DropShadow_shadowSize, mShadowSize)
            mRadiusOriginal = styledAttr.getDimensionPixelSize(R.styleable.DropShadow_radiusInner, mRadiusOriginal)
            mColorStops = styledAttr.getShadowColors(R.styleable.DropShadow_shadowColors, R.styleable.DropShadow_shadowStops, resources) ?: mColorStops
            mExtraOffsetBottom = styledAttr.getDimensionPixelSize(R.styleable.DropShadow_extraOffsetBottom, mExtraOffsetBottom)
        } finally {
            styledAttr.recycle()
        }
        mBitmapShadow = null
    }

    private fun initIfNeeded() {
        if (mInitialized) { return }
        try { mMarginLeft } catch (_: NullPointerException) { return }

        mInitialized = true

        mMarginLeft = marginLeft
        mMarginTop = marginTop
        mMarginRight = marginRight
        mMarginBottom = marginBottom


        if (layoutParams is ConstraintLayout.LayoutParams) {
            val lParams = layoutParams as ConstraintLayout.LayoutParams
            lParams.width = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
            lParams.height = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
            layoutParams = lParams
        }

        updateMargins()
    }

    override fun onDraw(canvas: Canvas) {
        initIfNeeded()
        drawShadow(canvas)
        super.onDraw(canvas)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        initIfNeeded()
        updateMargins()
    }

    // TODO: check if these are the right handler for the job (when are the layoutparams provided)
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        initIfNeeded()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        initIfNeeded()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        initIfNeeded()
    }

    private fun drawShadow(canvas: Canvas) {
        recalculateBitmapIfNeeded()
        if (mBitmapShadow == null) { return }

        canvas.drawBitmap(mBitmapShadow!!, 0f, 0f, Paint())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        invalidate()
    }

    private fun recalculateBitmapIfNeeded() {
        if (!shouldRecalculateBitmap()) { return }

        mBitmapShadow = createShadowBitmap(width-2*mShadowSize, height-2*mShadowSize, mRadiusOriginal, mShadowSize, mColorStops)
    }

    private fun shouldRecalculateBitmap() : Boolean {
        return mBitmapShadow == null
    }

    fun setMarginLeft(left: Int) {
        mMarginLeft = left
        updateMargins()
    }

    fun setMarginTop(top: Int) {
        mMarginTop = top
        updateMargins()
    }

    fun setMarginRight(right: Int) {
        mMarginRight = right
        updateMargins()
    }

    fun setMarginBottom(bottom: Int) {
        mMarginBottom = bottom
        updateMargins()
    }

    fun setMargins(left: Int, top: Int, right: Int, bottom: Int) {
        mMarginLeft = left
        mMarginTop = top
        mMarginRight = right
        mMarginBottom = bottom
        updateMargins()
    }

    fun setMargins(margin: Int) {
        setMargins(margin, margin, margin, margin)
    }

    private fun updateMargins() {
        if (layoutParams !is ConstraintLayout.LayoutParams) { return }
        val lParams = layoutParams as ConstraintLayout.LayoutParams
        lParams.leftMargin = mMarginLeft - mShadowSize
        lParams.topMargin = mMarginTop - mShadowSize
        lParams.rightMargin = mMarginRight - mShadowSize
        lParams.bottomMargin = mMarginBottom - mShadowSize - mExtraOffsetBottom
        layoutParams = lParams
    }

    fun setShadowSize(size: Int) {
        mShadowSize = size
        updateMargins()
        invalidate()
    }

    fun getShadowSize() : Int {
        return mShadowSize
    }

    fun setInnerRadius(radius: Int) {
        mRadiusOriginal = radius
        invalidate()
    }

    fun getInnerRadius() : Int {
        return mRadiusOriginal
    }

    fun setColorStops(stops: Gradient) {
        mColorStops = stops
    }

    fun getColorStops() : Gradient {
        return mColorStops
    }

    override fun invalidate() {
        super.invalidate()

        val bm = mBitmapShadow
        mBitmapShadow = null

        if (bm == null) { return }
        bm.recycle()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        return false
    }

    override fun getExplicitStyle(): Int {

        return super.getExplicitStyle()
    }
}


// all of those attributes should be protected, but kotlin currently does not (fully) support protected members on interfaces
interface DropShadowView {
    val mShadow: DropShadow
    var mShadowAttached: Boolean

    var mShadowSize: Int
    var mRadiusInner: Int
    var mColorStops: Map<Float, Int>?

    var mShadowBelow: Int
    var mShadowAboveIndex: Int

    fun readAttributes(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) {
        this as View  // assert that we are implemented on a view
        val styledAttr = context.theme.obtainStyledAttributes(attrs, R.styleable.DropShadowView, defStyleAttr, defStyleRes)
        try {
            mShadowSize = styledAttr.getDimensionPixelSize(R.styleable.DropShadowView_shadowSize, mShadowSize)
            mRadiusInner = styledAttr.getDimensionPixelSize(R.styleable.DropShadowView_radiusInner, mRadiusInner)
            mColorStops = styledAttr.getShadowColors(R.styleable.DropShadowView_shadowColors, R.styleable.DropShadowView_shadowStops, resources) ?: mColorStops

            mShadowBelow = styledAttr.getResourceId(R.styleable.DropShadowView_shadowBelow, mShadowBelow)
            mShadowAboveIndex = styledAttr.getInteger(R.styleable.DropShadowView_shadowAboveIndex, mShadowAboveIndex)
        } finally {
            styledAttr.recycle()
        }
    }

    // should be called in (overridden) onLayout in the view this is implemented on
    fun addShadow() {
        if (Build.VERSION.SDK_INT < 21) { return }
        this as View  // assert that we are implemented on a view
        if (isInEditMode) { return }
        if (mShadowAttached) { return }
        if (mShadowSize <= 0) { return }

        val layout = parent as ViewGroup
        if (layout.children.contains(mShadow)) { return }

        mShadow.applyRandomId()
        mShadow.setShadowSize(mShadowSize)
        mShadow.setInnerRadius(mRadiusInner)
        mColorStops?.let { mShadow.setColorStops(it) }

        var belowView = layout.children.find { it.id == mShadowBelow }
        if (belowView == null) { belowView = this }
        var index = layout.children.indexOf(belowView)
        index = (index+mShadowAboveIndex).coerceAtLeast(0).coerceAtMost(layout.children.count())

        post {
            layout.addView(mShadow, index)

            if (layout !is ConstraintLayout) { return@post }

            val constraints = ConstraintSet()
            constraints.clone(layout)
            constraints.connect(mShadow.id, ConstraintSet.LEFT, id, ConstraintSet.LEFT)
            constraints.connect(mShadow.id, ConstraintSet.TOP, id, ConstraintSet.TOP)
            constraints.connect(mShadow.id, ConstraintSet.RIGHT, id, ConstraintSet.RIGHT)
            constraints.connect(mShadow.id, ConstraintSet.BOTTOM, id, ConstraintSet.BOTTOM)
            constraints.applyTo(layout)
        }
        mShadowAttached = true
    }

    // should be called in (overridden) onSetAlpha in the view this is implemented on
    fun setShadowAlpha(alpha: Int) {
        mShadow.alpha = alpha / 255f
    }

    // should be called in (overridden) onVisibilityChanged in the view this is implemented on
    fun setShadowVisibility(visibility: Int) {
        try { mShadow.visibility = visibility } catch (_: Throwable) { (this as View).post { mShadow.visibility = visibility } }
    }
}