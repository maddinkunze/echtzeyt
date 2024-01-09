package com.maddin.echtzeyt.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import com.maddin.transportapi.LocatableStation
import com.maddin.transportapi.Station
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

open class StopMarker : Marker {
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

    companion object {
        val FLAG_SELECTED = 1
    }

    private val mStation: LocatableStation
    private val mMap: MapView
    private var mSelected = false

    private var mIconDefault: Drawable? = mIcon
    private var mIconSelected: Drawable? = null

    init {
        isDraggable = false
    }

    private fun initialize() {
        position = GeoPoint(mStation.location.lat, mStation.location.lon)
    }

    fun select() {
        mSelected = true
        setIconAccordingToState()
    }

    fun selectAndDeselectOthers(station: Station) {
        mSelected = (station.id == mStation.id)
        setIconAccordingToState()
    }

    fun deselect() {
        mSelected = false
        setIconAccordingToState()
    }

    private fun setIconAccordingToState() {
        if (mSelected && mIconSelected != null) {
            super.setIcon(mIconSelected)
            return
        }
        super.setIcon(mIconDefault)
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
}