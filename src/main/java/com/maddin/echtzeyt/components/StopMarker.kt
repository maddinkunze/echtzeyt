package com.maddin.echtzeyt.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Point
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.MotionEvent
import com.maddin.echtzeyt.randomcode.DynamicDrawable
import com.maddin.transportapi.components.LocationLatLon
import com.maddin.transportapi.components.POI
import com.maddin.transportapi.components.Station
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Marker
import kotlin.math.roundToInt

open class StopMarker : Marker {
    constructor(mapView: MapView, poi: POI, positionMarker: PositionMarker?) : super(mapView) {
        mPoi = poi
        mMap = mapView
        mPositionMarker = positionMarker
        initialize()
    }
    constructor(mapView: MapView, poi: POI, positionMarker: PositionMarker?, context: Context) : super(mapView, context) {
        mPoi = poi
        mMap = mapView
        mPositionMarker = positionMarker
        initialize()
    }

    companion object {
        const val FLAG_SELECTED = 1
    }

    private val mPoi: POI
    private val mMap: MapView
    private var mSelected = false

    private var mIconDefault: Drawable? = mIcon
    private var mIconSelected: Drawable? = null

    private val mRectDrawn = Rect()
    private val mPositionMarker: PositionMarker?

    private var mVisible = true
    private var mShouldUpdateVisibility = false

    init {
        isDraggable = false
    }

    private fun initialize() {
        (mPoi.location as? LocationLatLon)?.let { position = GeoPoint(it.lat, it.lon) }
    }

    fun select() {
        mSelected = true
        setIconAccordingToState()
    }

    fun selectAndDeselectOthers(poi: POI) {
        mSelected = (poi.id == mPoi.id)
        setIconAccordingToState()
    }

    fun deselect() {
        mSelected = false
        setIconAccordingToState()
    }

    private fun setIconAccordingToState() {
        val iconC = when {
            (mSelected && (mIconSelected != null)) -> mIconSelected
            else -> mIconDefault
        }
        if (icon == iconC) { return }
        super.setIcon(iconC)
    }

    fun setIcon(icon: Drawable?, flags: Int) {
        if (flags and FLAG_SELECTED > 0) {
            mIconSelected = icon
            setIconAccordingToState()
            return
        }

        mIconDefault = icon
        setIconAccordingToState()
    }

    override fun setIcon(icon: Drawable?) {
        setIcon(icon, 0)
    }

    override fun draw(canvas: Canvas?, pj: Projection?) {
        if (!mVisible && !mShouldUpdateVisibility) { return }
        super.draw(canvas, pj)
    }

    override fun draw(pCanvas: Canvas?, pMapView: MapView?, pShadow: Boolean) {
        if (!mVisible && !mShouldUpdateVisibility) { return }
        super.draw(pCanvas, pMapView, pShadow)
    }

    override fun drawAt(pCanvas: Canvas?, pX: Int, pY: Int, pOrientation: Float) {
        mRectDrawn.left = (pX - icon.intrinsicWidth * mAnchorU).roundToInt()
        mRectDrawn.top = (pY - icon.intrinsicHeight * mAnchorV).roundToInt()
        mRectDrawn.right = mRectDrawn.left + icon.intrinsicWidth
        mRectDrawn.bottom = mRectDrawn.top + icon.intrinsicHeight

        val alphaT = alpha
        mPositionMarker?.let {
            if (!it.isVisible()) { return@let }
            if (!contains(it.lastDrawnPosition)) { return@let }
            alpha = 0.4f * alphaT
        }

        super.drawAt(pCanvas, pX, pY, pOrientation)

        alpha = alphaT
        if (mShouldUpdateVisibility) { mUpdateVisibility() }
    }

    fun updateVisibility(estimate: BoundingBox?) {
        if (!mVisible) {
            if (!mGetVisibilityPrimitive()) { return }
            if (estimate?.contains(position) == false) { return }
            mShouldUpdateVisibility = true
            isEnabled = true
            return
        }
        mUpdateVisibility()
    }

    private fun mGetVisibilityPrimitive() : Boolean {
        val iconT = icon
        return alpha > 0.01 &&
                iconT.isVisible &&
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT || iconT.alpha > 5) &&
                (iconT is DynamicDrawable && (iconT.getOptimizedAlpha()) > 5)
    }

    private fun mUpdateVisibility() {
        mVisible = mGetVisibilityPrimitive() &&
                mRectDrawn.left < 1.5 * mMap.width &&
                mRectDrawn.top < 1.5 * mMap.height &&
                mRectDrawn.right > -0.5 * mMap.width &&
                mRectDrawn.bottom > -0.5 * mMap.height
        isEnabled = mVisible
        mShouldUpdateVisibility = false
    }

    override fun onTouchEvent(event: MotionEvent?, mapView: MapView?): Boolean {
        if (!mVisible) { return false }
        return super.onTouchEvent(event, mapView)
    }

    private fun contains(point: Point) : Boolean {
        return mRectDrawn.contains(point.x, point.y)
    }
}