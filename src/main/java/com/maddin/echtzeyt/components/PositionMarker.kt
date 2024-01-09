package com.maddin.echtzeyt.components

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.util.Consumer
import com.maddin.transportapi.LocatableStation
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

    init {
        position = GeoPoint(50.832115, 12.923990)
        setAnchor(ANCHOR_CENTER, ANCHOR_CENTER)
        isDraggable = false
        //mLocationManager.requestLocationUpdates(mLocationProvider)
    }

    override fun showInfoWindow() {}
}