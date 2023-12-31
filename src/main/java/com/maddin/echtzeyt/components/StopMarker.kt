package com.maddin.echtzeyt.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.view.MotionEvent
import com.maddin.transportapi.LocatableStation
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import kotlin.math.roundToInt

class DynamicDrawable(val drawable: Drawable) : LayerDrawable(arrayOf(drawable)) {
    private var mBitmap: Bitmap? = null
    private val mRect = Rect(bounds)
    private val mRectDraw = Rect()

    private var paint = Paint(Paint.ANTI_ALIAS_FLAG)

    var anchorH = Marker.ANCHOR_CENTER
        set(x) { field = x; updateDrawRect() }
    var anchorV = Marker.ANCHOR_TOP
        set(x) { field = x; updateDrawRect() }
    var scale = 1.0
        set(x) { field = x; updateDrawRect() }

    init {
        updateDrawRect()
    }

    override fun draw(canvas: Canvas) {
        if (mBitmap == null || mBitmap?.isRecycled == true) {
            mBitmap?.recycle()
            mBitmap = Bitmap.createBitmap(intrinsicWidth, intrinsicHeight, Bitmap.Config.ARGB_8888)
            val bCanvas = Canvas(mBitmap!!)
            super.draw(bCanvas)
        }
        canvas.drawBitmap(mBitmap!!, null, mRectDraw, paint)
    }

    override fun invalidateSelf() {
        super.invalidateSelf()
        mBitmap?.recycle()
        updateDrawRect()
    }

    override fun setBounds(left: Int, top: Int, right: Int, bottom: Int) {
        super.setBounds(0, 0, right-left, bottom-top)
        mRect.set(left, top, right, bottom)
        updateDrawRect()
    }

    override fun setBounds(bounds: Rect) {
        super.setBounds(Rect(0, 0, bounds.width(), bounds.height()))
        mRect.set(bounds)
        updateDrawRect()
    }

    private fun updateDrawRect() {
        val realWidth = mRect.width() * scale
        val realHeight = mRect.height() * scale

        val left = mRect.left + (mRect.width() - realWidth) * anchorH

        val top = mRect.top + (mRect.height() - realHeight) * anchorV

        mRectDraw.set(left.roundToInt(), top.roundToInt(), (left+realWidth).roundToInt(), (top+realHeight).roundToInt())
    }
}

class StopMarker : Marker {
    constructor(mapView: MapView, station: LocatableStation) : super(mapView) {
        mStation = station
        mMap = mapView
        initialize()
    }
    constructor(mapView: MapView, station: LocatableStation, context: Context) : super(mapView, context) {
        mStation = station
        mMap = mapView
        initialize()
    }

    private val mStation: LocatableStation
    private val mMap: MapView

    init {
        isDraggable = false
    }

    private fun initialize() {
        position = GeoPoint(mStation.location.lat, mStation.location.lon)
    }

    override fun showInfoWindow() {}
}