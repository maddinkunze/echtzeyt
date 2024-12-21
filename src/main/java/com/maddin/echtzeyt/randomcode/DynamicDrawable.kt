package com.maddin.echtzeyt.randomcode

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.graphics.applyCanvas
//import org.osmdroid.views.overlay.Marker
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

open class DelegatedDrawable(val drawable: Drawable) : Drawable() {
    override fun draw(canvas: Canvas) {
        drawable.draw(canvas)
    }

    override fun setAlpha(alpha: Int) {
        drawable.alpha = alpha
    }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java", ReplaceWith("drawable.opacity"))
    override fun getOpacity(): Int {
        return drawable.opacity
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        drawable.colorFilter = colorFilter
    }

    override fun getConstantState(): ConstantState? {
        return drawable.constantState
    }

    override fun setBounds(bounds: Rect) {
        super.setBounds(bounds)
        drawable.bounds = bounds
    }

    override fun setBounds(left: Int, top: Int, right: Int, bottom: Int) {
        super.setBounds(left, top, right, bottom)
        drawable.setBounds(left, top, right, bottom)
    }

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    override fun getAlpha(): Int {
        return drawable.alpha
    }

    override fun getCurrent(): Drawable {
        return drawable.current
    }

    override fun getIntrinsicHeight(): Int {
        return drawable.intrinsicHeight
    }

    override fun getIntrinsicWidth(): Int {
        return drawable.intrinsicWidth
    }

    override fun getMinimumHeight(): Int {
        return drawable.minimumHeight
    }

    override fun getMinimumWidth(): Int {
        return drawable.minimumWidth
    }
}

// the optimize variable can be set if the drawable is used for multiple views that all share the same attributes (i.e. alpha)
// when it is set, the normal setAlpha will have no effect (this is the optimization), instead use setAlphaOptimized
// this allows the drawable to be used in a component that explicitly sets alpha before drawing, with would force an expensive invalidation
class DynamicDrawable(drawable: Drawable, optimize: Boolean) : DelegatedDrawable(drawable) {
    constructor(drawable: Drawable) : this(drawable, false)

    private val mBitmapOrig by lazy {
        val b = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        val boundsT = drawable.bounds
        drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
        b.applyCanvas { drawable.draw(this) }
        drawable.bounds = boundsT
        b
    }
    private var mBitmap : Bitmap? = null
    private val mRect = Rect(bounds)

    private val paintPre = Paint(Paint.ANTI_ALIAS_FLAG)
    private var lastAlpha = paintPre.alpha
    private val deltaAlphaNoticeable = 5
    private val paintReal = if (optimize) null else { Paint(Paint.ANTI_ALIAS_FLAG) }

    var scale = 1.0
        set(x) { field = x; invalidateSelf() }

    override fun draw(canvas: Canvas) {
        if (mBitmap == null || mBitmap?.isRecycled != false) {
            mBitmap = Bitmap.createBitmap(intrinsicWidth, intrinsicHeight, Bitmap.Config.ARGB_8888)
            mBitmap?.applyCanvas {
                drawBitmap(mBitmapOrig, null, Rect(0, 0, width, height), paintPre)
            }
            lastAlpha = paintPre.alpha
        }
        mBitmap?.let { canvas.drawBitmap(it, mRect.left.toFloat(), mRect.top.toFloat(), paintReal) }
    }

    override fun invalidateSelf() {
        super.invalidateSelf()
        mBitmap?.recycle()
    }

    override fun setBounds(left: Int, top: Int, right: Int, bottom: Int) {
        super.setBounds(0, 0, intrinsicWidth, intrinsicHeight)
        //drawable.setBounds(0, 0, super.getIntrinsicWidth(), super.getIntrinsicHeight())
        mRect.set(left, top, right, bottom)
    }

    override fun setBounds(bounds: Rect) {
        super.setBounds(Rect(0, 0, intrinsicWidth, intrinsicHeight))
        mRect.set(bounds)
    }

    override fun setAlpha(alpha: Int) {
        paintReal?.alpha = alpha
    }

    fun setOptimizedAlpha(alpha: Int) {
        paintPre.alpha = alpha
        if ((alpha-lastAlpha).absoluteValue < deltaAlphaNoticeable) { return }
        invalidateSelf()
    }

    override fun getAlpha(): Int {
        return (paintReal?:paintPre).alpha
    }

    fun getOptimizedAlpha() : Int {
        return paintPre.alpha
    }

    override fun getIntrinsicWidth(): Int {
        return (super.getIntrinsicWidth() * scale).roundToInt()
    }

    override fun getIntrinsicHeight(): Int {
        return (super.getIntrinsicHeight() * scale).roundToInt()
    }
}
