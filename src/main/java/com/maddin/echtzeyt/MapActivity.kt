package com.maddin.echtzeyt

import android.graphics.ColorFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import com.maddin.echtzeyt.components.DynamicDrawable
import com.maddin.echtzeyt.components.PullupScrollView
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
import org.osmdroid.views.CustomZoomButtonsController
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

    private val map by lazy { findViewById<MapView>(R.id.mapView) }
    private val txtStation by lazy { findViewById<TextView>(R.id.txtStationName) }
    private val pullup : PullupScrollView by lazy { findViewById(R.id.scrollStationInfo) }
    protected lateinit var drawableMarker : DynamicDrawable

    protected val zoomMin = 12.0
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

    protected val latStart by lazy { resources.getFloat(R.dimen.map_lat_start).toDouble() }
    protected val latMin by lazy { resources.getFloat(R.dimen.map_lat_min).toDouble() }
    protected val latMax by lazy { resources.getFloat(R.dimen.map_lat_max).toDouble() }
    protected val lonStart by lazy { resources.getFloat(R.dimen.map_lon_start).toDouble() }
    protected val lonMin by lazy { resources.getFloat(R.dimen.map_lon_min).toDouble() }
    protected val lonMax by lazy { resources.getFloat(R.dimen.map_lon_max).toDouble() }
    protected val overscrollX by lazy { 0 }//resources.displayMetrics.widthPixels.coerceAtLeast(resources.getDimensionPixelOffset(R.dimen.stationmarker_size)) }
    protected val overscrollY by lazy { 0 }//resources.displayMetrics.heightPixels.coerceAtLeast(resources.getDimensionPixelOffset(R.dimen.stationmarker_size)) }

    protected val colorFilterLight: ColorFilter by lazy { com.maddin.echtzeyt.randomcode.FILTER_OSM_LIGHT }
    protected val colorFilterDark: ColorFilter by lazy { com.maddin.echtzeyt.randomcode.FILTER_OSM_DARK }

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
        // this is sketchy, we are using the same drawable for all markers
        // it significantly improves performance but i am not sure if this is supposed to work or just works by accident
        // TODO: check if this is supposed to work
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
        mapController.setCenter(GeoPoint(latStart, lonStart))

        map.isHorizontalMapRepetitionEnabled = false
        map.isVerticalMapRepetitionEnabled = false
        val mLonMin = lonMin.coerceAtMost(lonStart).coerceAtLeast(MapView.getTileSystem().minLongitude)
        val mLatMin = latMin.coerceAtMost(latStart).coerceAtLeast(MapView.getTileSystem().minLatitude)
        val mLonMax = lonMax.coerceAtLeast(lonStart).coerceAtMost(MapView.getTileSystem().maxLongitude)
        val mLatMax = latMax.coerceAtLeast(latStart).coerceAtMost(MapView.getTileSystem().maxLatitude)
        map.setScrollableAreaLimitLongitude(mLonMin, mLonMax, overscrollX)
        map.setScrollableAreaLimitLatitude(mLatMax, mLatMin, overscrollY)
        map.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
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

        findViewById<ImageButton>(R.id.btnClosePullup).setOnClickListener { pullup.hidePullup() }
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
        txtStation.text = station.name
        if (!pullup.isVisible()) { pullup.showPullup() }
    }
}