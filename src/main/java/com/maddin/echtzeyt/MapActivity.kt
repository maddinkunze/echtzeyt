package com.maddin.echtzeyt

import android.graphics.ColorFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import com.maddin.echtzeyt.components.DynamicDrawable
import com.maddin.echtzeyt.components.StopMarker
import com.maddin.transportapi.DefaultCoordinate
import com.maddin.transportapi.LocatableStation
import com.maddin.transportapi.LocationAreaRect
import com.maddin.transportapi.LocationStationAPI
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File
import kotlin.concurrent.thread
import kotlin.math.absoluteValue
import kotlin.math.pow

@Suppress("MemberVisibilityCanBePrivate")
open class MapActivity : AppCompatActivity() {
    private var nextLocateUpdate = -1L
    private var lastLocateUpdate = -1L
    private lateinit var currentLocationArea : BoundingBox
    private var transportLocateStationAPI : LocationStationAPI = com.maddin.transportapi.impl.germany.VMS("Chemnitz") // TODO: generalize

    private lateinit var map : MapView
    protected lateinit var drawableMarker : DynamicDrawable

    protected val zoomMin = 10.0
    protected val zoomMax = 19.5
    protected val zoomStart = 17.0
    protected val zoomDefault = zoomStart
    private var zoomLastUpdateMarkers = Double.POSITIVE_INFINITY
    protected var zoomDeltaNoticeable = 0.01
    protected val zoomTile = 0.95f

    protected val scaleMin = 0.25
    protected val scaleMax = 0.9
    protected val scaleZoomFactor = 1.0
    private var scaleLastUpdateMarkers = zoomStart
    protected val scaleDeltaNoticeable = 0.007

    private var mScaleFactorY = 0.0
    private var mScaleOffsetY = 0.0

    protected val colorFilterLight: ColorFilter = com.maddin.echtzeyt.randomcode.getLightColorMatrixFilter()
    protected val colorFilterDark: ColorFilter = com.maddin.echtzeyt.randomcode.getDarkColorMatrixFilter()

    init {
        updateMarkerZoomConstants()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initOSM()

        //inflate and create the map
        setContentView(R.layout.activity_map)

        initVariables()
        initHandlers()
        initThreads()
        initMap()
    }

    private fun initOSM() {
        val osmconfig = Configuration.getInstance()
        osmconfig.osmdroidTileCache = File(cacheDir.absoluteFile, "osmdroid")
        osmconfig.userAgentValue = "echtzeyt/${application.packageName}"
    }

    private fun initVariables() {
        map = findViewById(R.id.mapView)

        // this is sketchy, we are using the same drawable for all markers
        // it significantly improves performance but i am not sure if this is supposed to work or just works by accident
        drawableMarker = DynamicDrawable(AppCompatResources.getDrawable(this, R.drawable.stationmark)!!)
        drawableMarker.anchorH = Marker.ANCHOR_CENTER
        drawableMarker.anchorV = Marker.ANCHOR_BOTTOM
        drawableMarker.scale = 1.0
    }

    private fun initMap() {
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.minZoomLevel = zoomMin
        map.maxZoomLevel = zoomMax

        val mapController = map.controller
        mapController.setZoom(zoomStart)
        val startPoint = GeoPoint(50.832115, 12.923990) // TODO: generalize
        mapController.setCenter(startPoint)

        map.overlayManager.tilesOverlay.setColorFilter(colorFilterLight)

        //map.isTilesScaledToDpi = true // TODO: check if this looks good on other devices
        map.tilesScaleFactor = zoomTile

        map.requestLayout()
    }

    private fun initHandlers() {
        map.addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean { updateLocationSearch(200); return false }
            override fun onZoom(event: ZoomEvent?): Boolean { updateLocationSearch(200); updateMarkerZoom(); return false }
        })
        map.addOnFirstLayoutListener { _, _, _, _, _ -> updateLocationSearch() }
    }

    private fun initThreads() {
        thread(start=true, isDaemon=false) {
            while (true) {
                val timeNow = System.currentTimeMillis()
                val shouldUpdate = (nextLocateUpdate < 0) || (timeNow < nextLocateUpdate)
                if (shouldUpdate) {
                    Thread.sleep(200)
                    continue
                }

                ntUpdateLocationSearch()
            }
        }
    }

    private fun updateMarkerZoom() {
        val zoomLevel = map.zoomLevelDouble
        if ((zoomLevel - zoomLastUpdateMarkers).absoluteValue < zoomDeltaNoticeable) { return }
        zoomLastUpdateMarkers = zoomLevel

        val scale = scaleMin + (getMarkerZoomValue(zoomLevel) - mScaleOffsetY) * mScaleFactorY
        if ((1-scale/scaleLastUpdateMarkers).absoluteValue < scaleDeltaNoticeable) { return }
        scaleLastUpdateMarkers = scale
        drawableMarker.scale = scale
    }

    private fun getMarkerZoomValue(x: Double) : Double {
        return Math.E.pow(x * scaleZoomFactor)
    }

    private fun updateMarkerZoomConstants() {
        val actualScaleMin = getMarkerZoomValue(zoomMin)
        val actualScaleMax = getMarkerZoomValue(zoomMax)

        mScaleOffsetY = actualScaleMin
        mScaleFactorY = (scaleMax - scaleMin) / (actualScaleMax - actualScaleMin)
    }

    private fun updateLocationArea() {
        currentLocationArea = map.boundingBox
    }

    private fun updateLocationSearch(after: Long) {
        updateLocationArea()
        val delay = (after-10).coerceAtMost(0)
        Handler(Looper.getMainLooper()).postDelayed({ updateLocationArea() }, delay)
        nextLocateUpdate = System.currentTimeMillis() + after
    }

    private fun updateLocationSearch() {
        updateLocationSearch(0)
    }

    private fun ntUpdateLocationSearch() {
        val timeNow = System.currentTimeMillis()

        val lastNextLocateUpdate = nextLocateUpdate

        val areaCenter = currentLocationArea.centerWithDateLine
        val center = DefaultCoordinate(areaCenter.latitude, areaCenter.longitude)
        val width = 2 * currentLocationArea.longitudeSpanWithDateLine
        val height = 2 * currentLocationArea.latitudeSpan
        val area = LocationAreaRect(center, width, height)
        val stations = transportLocateStationAPI.locateStations(area)

        if (timeNow < lastLocateUpdate) { return }
        lastLocateUpdate = timeNow

        Handler(Looper.getMainLooper()).post {
            map.overlays.clear()
            for (station in stations) {
                if (station !is LocatableStation) { continue }
                val marker = StopMarker(map, station)
                marker.icon = drawableMarker
                marker.setOnMarkerClickListener { _, _ -> selectStation(station); true }
                map.overlays.add(marker)
            }

            map.invalidate()
        }

        if (nextLocateUpdate == lastNextLocateUpdate) { nextLocateUpdate = -1L }
    }

    fun selectStation(station: LocatableStation) {
        println("MADDIN101: ${station.name}")
    }
}