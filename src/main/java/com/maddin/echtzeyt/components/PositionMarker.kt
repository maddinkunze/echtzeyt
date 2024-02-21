package com.maddin.echtzeyt.components

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Point
import android.view.MotionEvent
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class PositionMarker : Marker {
    constructor(mapView: MapView) : super(mapView) {
        mMap = mapView
    }
    constructor(mapView: MapView, context: Context) : super(mapView, context) {
        mMap = mapView
    }

    private val mMap: MapView
    private var animPos: ValueAnimator? = null
    private var animPosNextUpdate = 0L
    private var isInitialPositionSet = false

    var lastDrawnPosition = Point()
    private var visible = false
    private var shouldUpdateVisibility = false

    init {
        setAnchor(ANCHOR_CENTER, ANCHOR_CENTER)
        isDraggable = false
    }

    override fun showInfoWindow() {}

    override fun setPosition(position: GeoPoint?) {
        super.setPosition(position)
        if (position != null) { isInitialPositionSet = true }
    }

    fun animateToPosition(position: GeoPoint) {
        if (!isInitialPositionSet) {
            // set initial position to avoid a weird jump when the "initial" position is at 0,0
            setPosition(position)
            return
        }

        val animPosNew = ValueAnimator.ofObject({ f, s, e ->
            if (s !is GeoPoint || e !is GeoPoint) { return@ofObject null }
            GeoPoint(f*e.latitude+(1-f)*s.latitude, f*e.longitude+(1-f)*s.longitude)
        }, this.position, position)
        animPosNew.duration = 1000L
        animPosNew.addUpdateListener {
            val v = it.animatedValue; if (v !is GeoPoint) { return@addUpdateListener }
            setPosition(v)

            val now = System.currentTimeMillis()
            if (it.animatedFraction > 0.95 || now > animPosNextUpdate) {
                mMap.invalidate()
                animPosNextUpdate = now + 25 // limit the amount of map updates this single marker can produce
            }
        }

        animPos?.cancel()
        animPos = animPosNew
        animPosNew.start()
    }

    override fun drawAt(pCanvas: Canvas?, pX: Int, pY: Int, pOrientation: Float) {
        lastDrawnPosition.x = pX
        lastDrawnPosition.y = pY
        shouldUpdateVisibility = true

        super.drawAt(pCanvas, pX, pY, pOrientation)
    }

    private fun updateVisibility() {
        visible = (lastDrawnPosition.x > -icon.intrinsicWidth/2) &&
                (lastDrawnPosition.y > -icon.intrinsicHeight/2) &&
                (lastDrawnPosition.x < mMap.width+icon.intrinsicWidth/2) &&
                (lastDrawnPosition.y < mMap.height+icon.intrinsicHeight/2) &&
                alpha > 0.01
        shouldUpdateVisibility = false
    }

    override fun setVisible(visible: Boolean) {
        super.setVisible(visible)
        if (!visible) { isInitialPositionSet = false }
    }

    override fun setAlpha(alpha: Float) {
        super.setAlpha(alpha)
        shouldUpdateVisibility = true
    }

    fun isVisible() : Boolean {
        if (shouldUpdateVisibility) { updateVisibility() }
        return visible
    }

    override fun onTouchEvent(event: MotionEvent?, mapView: MapView?): Boolean {
        return false
    }
}