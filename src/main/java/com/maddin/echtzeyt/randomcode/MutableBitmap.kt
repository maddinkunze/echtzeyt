package com.maddin.echtzeyt.randomcode

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.opengl.GLES20
import android.opengl.GLUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.scale
import org.oscim.backend.canvas.Bitmap as VTMBitmap
import org.oscim.utils.IOUtils
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

abstract class MutableBitmap(private val initialWidth: Int, private val initialHeight: Int) : VTMBitmap {
    private var mBitmap: Bitmap? = null
    private val mPaint = Paint()
    private val bitmap: Bitmap
        get() {
            var bitmap = mBitmap
            var forceRedraw = shouldRedraw
            if (bitmap == null || bitmap.isRecycled || sizeChanged) {
                recycle()
                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                mBitmap = bitmap
                forceRedraw = true
            }
            if (forceRedraw) {
                eraseColor(Color.TRANSPARENT)
                val canvas = Canvas(bitmap)
                canvas.drawBitmap(asResizedBitmap(), 0f, 0f, mPaint)
                shouldRedraw = false
            }
            return bitmap
        }
    private var sizeChanged = false
    private var shouldRedraw = false
    private var mWidth: Int = initialWidth
    private var mHeight: Int = initialHeight
    var alpha: Int = 255
        set(value) { if (value == field) { return }; field = value; mPaint.alpha = alpha; shouldRedraw = true }

    override fun isValid() = true

    override fun getWidth() = mWidth
    fun setWidth(width: Int) {
        if (width == mWidth) { return }
        mWidth = width
        sizeChanged = true
    }

    override fun getHeight() = mHeight
    fun setHeight(height: Int) {
        if (height == mHeight) { return }
        mHeight = height
        sizeChanged = true
    }

    override fun scaleTo(width: Int, height: Int) {
        this.width = width
        this.height = height
    }

    fun setScale(scale: Double) {
        scaleTo((initialWidth*scale).roundToInt(), (initialHeight*scale).roundToInt())
    }

    override fun getPixels(): IntArray {
        val width = width
        val height = height
        val colors = IntArray(width * height)
        bitmap.getPixels(colors, 0, width, 0, 0, width, height)
        return colors
    }

    override fun uploadToTexture(replace: Boolean) {
        val bitmap = bitmap
        if (bitmap.isRecycled) return

        val format: Int = GLUtils.getInternalFormat(bitmap)
        val type: Int = GLUtils.getType(bitmap)

        if (replace) {
            GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, bitmap, format, type)
        } else {
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, format, bitmap, type, 0)
        }
    }

    override fun getPngEncodedData(): ByteArray {
        var outputStream: ByteArrayOutputStream? = null
        try {
            outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            return outputStream.toByteArray()
        } finally {
            IOUtils.closeQuietly(outputStream)
        }
    }

    override fun eraseColor(color: Int) {
        mBitmap?.eraseColor(color)
    }

    override fun recycle() {
        mBitmap?.recycle()
    }

    protected abstract fun asResizedBitmap(): Bitmap
}

class DynamicBitmap(private val bitmap: Bitmap) : MutableBitmap(bitmap.width, bitmap.height) {
    override fun asResizedBitmap() = bitmap.scale(width, height)
}

class DrawableBitmap(private val drawable: Drawable, width: Int, height: Int) : MutableBitmap(width, height) {
    constructor(drawable: Drawable) : this(drawable, drawable.intrinsicWidth, drawable.intrinsicHeight)
    override fun asResizedBitmap(): Bitmap = drawable.toBitmap(width, height)
}