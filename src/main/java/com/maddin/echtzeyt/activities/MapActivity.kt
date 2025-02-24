package com.maddin.echtzeyt.activities

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.method.LinkMovementMethod
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.View.OnLayoutChangeListener
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DimenRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.applyCanvas
import androidx.core.location.LocationManagerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import com.maddin.echtzeyt.ECHTZEYT_CONFIGURATION
import com.maddin.echtzeyt.R
import com.maddin.echtzeyt.components.FloatingButton
import com.maddin.echtzeyt.components.StationPullup
import com.maddin.echtzeyt.components.createShadowBitmap
import com.maddin.echtzeyt.components.getShadowColors
import com.maddin.echtzeyt.randomcode.ActivityResultSerializable
import com.maddin.echtzeyt.randomcode.BaseInstanceData
import com.maddin.echtzeyt.randomcode.DrawableBitmap
import com.maddin.echtzeyt.randomcode.InstanceData
import com.maddin.echtzeyt.randomcode.LazyMutable
import com.maddin.echtzeyt.randomcode.LazyView
import com.maddin.echtzeyt.randomcode.MarkerModel
import com.maddin.echtzeyt.randomcode.ModelInstanceLayer
import com.maddin.echtzeyt.randomcode.ModelLayer
import com.maddin.echtzeyt.randomcode.ModelSetupLayer
import com.maddin.echtzeyt.randomcode.OnModelClickListener
import com.maddin.echtzeyt.randomcode.unpackResourceIntoCache
import com.maddin.transportapi.components.LocationAreaRect
import com.maddin.transportapi.components.LocationLatLon
import com.maddin.transportapi.components.LocationLatLonImpl
import com.maddin.transportapi.components.POI
import com.maddin.transportapi.components.Trip
import com.maddin.transportapi.endpoints.LocatePOIRequestImpl
import com.maddin.transportapi.utils.sameAs
import org.oscim.android.MapView
import org.oscim.backend.canvas.Paint
import org.oscim.core.BoundingBox
import org.oscim.core.GeoPoint
import org.oscim.core.MapPosition
import org.oscim.event.Event
import org.oscim.layers.GroupLayer
import org.oscim.layers.PathLayer
import org.oscim.layers.marker.ItemizedLayer
import org.oscim.layers.marker.MarkerItem
import org.oscim.layers.marker.MarkerSymbol
import org.oscim.layers.tile.buildings.BuildingLayer
import org.oscim.layers.tile.vector.labeling.LabelLayer
import org.oscim.map.Map.UpdateListener
import org.oscim.theme.StreamRenderTheme
import org.oscim.theme.styles.LineStyle
import org.oscim.tiling.source.mapfile.MapFileTileSource
import kotlin.concurrent.thread
import kotlin.math.absoluteValue
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.roundToInt


fun Resources.getFloatValue(@DimenRes floatRes: Int):Float{
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { return getFloat(floatRes) }

    val out = TypedValue()
    getValue(floatRes, out, true)
    return out.float
}

enum class MapContractActions {
    SELECT_STATION,
    SHOW_TRIP
}

class MapResultContractSelectPOI : ActivityResultSerializable<POI, POI>(ECHTZEYT_CONFIGURATION.activityMap) {
    companion object {
        fun appliesToIntent(intent: Intent?): Boolean {
            return intent?.getStringExtra(ACTION) == MapContractActions.SELECT_STATION.name
        }
        fun createResult(station: POI): Intent {
            return ActivityResultSerializable.createResult(station)
        }
        fun parseIntent(intent: Intent?): POI? {
            return ActivityResultSerializable.parseIntent(intent)
        }
    }
    override fun createIntent(context: Context, input: POI?): Intent {
        return super.createIntent(context, input).putExtra(ACTION, MapContractActions.SELECT_STATION.name)
    }
}

class MapContractShowTrip :  ActivityResultSerializable<Trip, Nothing>(ECHTZEYT_CONFIGURATION.activityMap) {
    companion object {
        fun appliesToIntent(intent: Intent?): Boolean {
            return intent?.getStringExtra(ACTION) == MapContractActions.SHOW_TRIP.name
        }
        fun parseIntent(intent: Intent?): Trip? {
            return ActivityResultSerializable.parseIntent(intent)
        }
    }
    override fun createIntent(context: Context, input: Trip?): Intent {
        return super.createIntent(context, input).putExtra(ACTION, MapContractActions.SHOW_TRIP.name)
    }
}

@Suppress("MemberVisibilityCanBePrivate")
open class MapActivity : EchtzeytForegroundActivity(), LocationListener, UpdateListener, OnModelClickListener {
    private val mapView: MapView by LazyView(R.id.mapView)
    private val txtCopyright: TextView by LazyView(R.id.txtMapCopyright)
    private val btnBack: FloatingButton by LazyView(R.id.btnMapBack)
    private val btnLocate: FloatingButton by LazyView(R.id.btnMapLocate)

    protected val map: org.oscim.map.Map by lazy { mapView.map() }
    protected lateinit var tileLayer: org.oscim.layers.tile.vector.VectorTileLayer

    // Stuff for locating/searching stations
    private val shouldSearchForStations by lazy { MapResultContractSelectPOI.appliesToIntent(intent) }
    private var nextLocateUpdate = -1L
    private val stationsFound = mutableSetOf<String>()
    protected val transportLocateStationAPI by lazy { ECHTZEYT_CONFIGURATION.mapsStationAPI!! }
    protected var poiSelected: POI? by LazyMutable { if (shouldSearchForStations) MapResultContractSelectPOI.parseIntent(intent) else null }

    private val pullupStation: StationPullup by LazyView(R.id.pullupStationInfo)
    private val btnHideMarkers: FloatingButton by LazyView(R.id.btnMapHideMarkers)

    // All sorts of markers and whatnot
    private val willMarkersBeVisible by lazy { MapResultContractSelectPOI.appliesToIntent(intent) }
    private val stationMarkerWidth by lazy { resources.getDimensionPixelSize(R.dimen.stationmarker_width) }
    private val stationMarkerHeight by lazy { resources.getDimensionPixelSize(R.dimen.stationmarker_height) }
    private val drawableMarker by lazy { VectorDrawableCompat.create(resources, R.drawable.stationmark, theme)!! }
    private val drawableMarkerSelected by lazy { VectorDrawableCompat.create(resources, R.drawable.stationmark_selected, theme)!! }
    protected val drawableMarkerShadow by lazy {
        val w = stationMarkerWidth
        val ha = resources.getDimensionPixelSize(R.dimen.stationmarker_arrow_size)
        val h = stationMarkerHeight - ha
        val r = resources.getDimensionPixelSize(R.dimen.stationmarker_radius)
        val ss = resources.getDimensionPixelSize(R.dimen.stationmarker_shadow_size)
        val bitmapShadow = createShadowBitmap(w, h, r, ss, resources.getShadowColors(R.array.shadowColors, R.array.shadowStops))
        val bitmapShadowS = Bitmap.createBitmap(bitmapShadow.width, bitmapShadow.height+ha-ss, Bitmap.Config.ARGB_8888)
        bitmapShadowS.applyCanvas { drawBitmap(bitmapShadow, 0f, 0f, null) }// currently, the shadow will be aligned on the bottom, and therefore it will may clipped at the bottom
        // if someone wants a shadow that is larger than sm_arrow_size, it may look weird
        // TODO: to fix this, the anchor could be recalculated if the drawable exceeds the "lower limit"
        bitmapShadowS
    }

    protected val modelMarker by lazy { MarkerModel(drawableMarker) }
    protected val modelMarkerSelected by lazy { MarkerModel(drawableMarkerSelected) }
    protected val modelMarkerShadow by lazy { MarkerModel(drawableMarkerShadow) }
    protected val layerMarkerShadows by lazy { ModelInstanceLayer(map, modelMarkerShadow) }
    protected val layerMarkers by lazy { ModelInstanceLayer(map, modelMarker).also { it.onClickListener = this } }
    protected val layerModels by lazy { ModelLayer(map) }
    protected val layerMarkerSelected by lazy { ModelInstanceLayer(map, modelMarkerSelected) }

    protected var showMarkers = true
    protected val zoomMarkerMin by lazy { 13 }
    protected val zoomMarkerMax by lazy { 14.5 }
    protected val zoomShadowMin by lazy { 14.5 /*resources.getFloatValue(R.dimen.map_zoom_start)*/ }
    protected val zoomShadowMax by lazy { 15.5 /*resources.getFloatValue(R.dimen.map_zoom_start)*/ }

    protected val zoomMin by lazy { resources.getFloatValue(R.dimen.map_zoom_min).toDouble() }
    protected val zoomMax by lazy { resources.getFloatValue(R.dimen.map_zoom_max).toDouble() }
    protected val zoomStart by lazy { getStartZoom() }
    protected val zoomDefault by lazy { resources.getFloatValue(R.dimen.map_zoom_start).toDouble() }
    protected val zoomStation by lazy { resources.getFloatValue(R.dimen.map_zoom_station).toDouble() }
    private var zoomLastUpdateMarkers = Double.POSITIVE_INFINITY
    protected val zoomDeltaNoticeable = 0.01
    protected val zoomTile = 0.95f

    protected val scaleMin = 0.25
    protected val scaleMax = 0.9
    protected val scaleZoomFactor = 1.0
    private var scaleLastUpdateMarkers = Double.POSITIVE_INFINITY
    protected val scaleDeltaNoticeable = 0.007

    private var mScaleFactorY = 0.0
    private var mScaleOffsetY = 0.0

    private var nextUpdateMarkerVisibility = -1L

    protected val latStart by lazy { getStartLatitude() }
    protected val latMin by lazy { resources.getFloatValue(R.dimen.map_lat_min).toDouble() }
    protected val latMax by lazy { resources.getFloatValue(R.dimen.map_lat_max).toDouble() }
    protected val lonStart by lazy { getStartLongitude() }
    protected val lonMin by lazy { resources.getFloatValue(R.dimen.map_lon_min).toDouble() }
    protected val lonMax by lazy { resources.getFloatValue(R.dimen.map_lon_max).toDouble() }

    // Everything related to location permissions and gps updates and whatnot
    private val locationPermissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { updateLocation(force=true, goto=true) }
    private val locationClient by lazy { ContextCompat.getSystemService(this, LocationManager::class.java) }
    private val locationProviders by lazy {
        val fused = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { LocationManager.FUSED_PROVIDER } else { null }
        val gps = LocationManager.GPS_PROVIDER
        val network = LocationManager.NETWORK_PROVIDER
        arrayOf(fused, gps, network).filterNotNull()
    }
    private var locationProviderMain: String? = null
    private var locationLast: Location? = null
    private var locationLastUpdate: Location? = null
    protected val locationMinDeltaTime: Long = 700L
    protected val locationMinDeltaDistance: Float = 2f
    private val locationMarkerSize by lazy { resources.getDimensionPixelSize(R.dimen.positionmarker_size) }
    private val locationMarkerDrawable by lazy { DrawableBitmap(VectorDrawableCompat.create(resources, R.drawable.locationmark, null)!!, locationMarkerSize, locationMarkerSize) }
    private val locationMarkerDrawableDirected by lazy { DrawableBitmap(VectorDrawableCompat.create(resources, R.drawable.locationmark_directed, null)!!, locationMarkerSize, locationMarkerSize) }
    private val locationMarkerSymbol by lazy { MarkerSymbol(locationMarkerDrawable, MarkerSymbol.HotspotPlace.CENTER) }
    private val locationMarkerSymbolDirected by lazy { MarkerSymbol(locationMarkerDrawableDirected, MarkerSymbol.HotspotPlace.CENTER) }
    private val locationMarker by lazy { MarkerItem(null, null, GeoPoint(0.0, 0.0)) }
    protected val layerMarkerLocation by lazy { ItemizedLayer(map, listOf(locationMarker), locationMarkerSymbol, null) }

    private var gpsHandlersInitialized = false
    @Volatile private var gpsEnabled = false
    @Volatile private var gpsEnabledNextCheck = -1L
    protected val gpsEnabledNextCheckAfter = 5000

    private val sensorManager by lazy { ContextCompat.getSystemService(this, SensorManager::class.java) }
    private val sensorSampling = SensorManager.SENSOR_DELAY_NORMAL
    private val sensorAcc by lazy { sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }
    private val sensorMag by lazy { sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) }
    // thanks to https://stackoverflow.com/questions/8315913/how-to-get-direction-in-android-such-as-north-west for this code
    private val orientationHandler: SensorEventListener by lazy {
        object : SensorEventListener {
            private var mGravity: FloatArray? = null
            private var mGeomagnetic: FloatArray? = null
            private var mAzimutDegMovAvg = Double.NaN
            private val mAzimutDegMovAvgFactor = 0.9
            private var mLastAzimutDeg = Double.POSITIVE_INFINITY
            private val mDeltaAzimutDegNoticeable = 1.0

            @Suppress("LocalVariableName")
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) mGravity = event.values
                if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) mGeomagnetic = event.values
                if (mGravity == null || mGeomagnetic == null) {
                    return
                }

                val R = FloatArray(9)
                val I = FloatArray(9)
                if (!SensorManager.getRotationMatrix(R, I, mGravity, mGeomagnetic)) {
                    return
                }

                // orientation contains azimut, pitch and roll
                val orientation = FloatArray(3)
                SensorManager.getOrientation(R, orientation)
                val azimutInRad = orientation[0]
                val azimutInDeg = azimutInRad * (180 / Math.PI)

                if (mAzimutDegMovAvg.isNaN()) {
                    mAzimutDegMovAvg = azimutInDeg
                }
                mAzimutDegMovAvg *= mAzimutDegMovAvgFactor
                mAzimutDegMovAvg += (1 - mAzimutDegMovAvgFactor) * azimutInDeg

                locationMarker.setRotation(-mAzimutDegMovAvg.toFloat())
                layerMarkerLocation.populate()

                // only force a redraw of the map, when the change would be noticeable
                if ((mAzimutDegMovAvg - mLastAzimutDeg).absoluteValue < mDeltaAzimutDegNoticeable) {
                    return
                }
                mLastAzimutDeg = mAzimutDegMovAvg
                map.updateMap()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
    }

    private var mAskForMobileDataCount = 3
    private var mAskForMobileDataNext = -1L
    protected val mAskForMobileDataAgainAfter = 5000
    private var mCurrentDialogType = 0
    private var mCurrentDialog: AlertDialog? = null
    private var mCurrentToast: Toast? = null

    private companion object {
        const val DIALOG_LOCATION = 1
        const val DIALOG_MOBILE_DATA = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initWindow()

        //inflate and create the map
        setContentView(R.layout.activity_map)

        initVariables()
        initSettings()
        initHandlers()
        initThreads()
        initMap()
    }

    override fun onStart() {
        super.onStart()
        initResourceIntensiveHandlers()
    }

    @Suppress("DEPRECATION")
    private fun initWindow() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        setWindowFlag(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS, false)
        window.statusBarColor = Color.TRANSPARENT

        if (!isInNightMode()) {
            val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
            windowInsetsController.isAppearanceLightStatusBars = true
        }
    }

    @Suppress("SameParameterValue")
    private fun setWindowFlag(bits: Int, on: Boolean) {
        val params = window.attributes
        params.flags = if (on) { params.flags or bits } else { params.flags and bits.inv() }
        window.attributes = params
    }

    private fun initVariables() {
        updateMarkerZoomConstants()

        if (shouldSearchForStations) {
            poiSelected?.let { addPOIMarker(it); selectPOI(it) }
            pullupStation.visibility = View.VISIBLE
        }

        if (willMarkersBeVisible) {
            btnHideMarkers.visibility = View.VISIBLE
        }

        if (locationClient == null || locationProviders.isEmpty()) {
            btnLocate.visibility = View.GONE
        }

        txtCopyright.movementMethod = LinkMovementMethod.getInstance()

        val now = System.currentTimeMillis()
        gpsEnabledNextCheck = now + 2 * gpsEnabledNextCheckAfter
        mAskForMobileDataNext = now + mAskForMobileDataAgainAfter
    }

    private fun initSettings() {
        //map.setUseDataConnection(preferences.getBoolean("mapUseMobileData", false))
        checkMobileDataUsage()
    }

    open fun initMap() {
        // Set tile source
        val tileSource = MapFileTileSource()
        val mapFile = unpackResourceIntoCache(R.raw.fileMapRaw, getString(R.string.fileMapExtracted))
        tileSource.setMapFileInputStream(mapFile.inputStream())

        // Assign tile source to map (has to be done before adding the theme)
        tileLayer = map.setBaseMap(tileSource)

        // Set theme (has to be done before adding the layers)
        val theme = StreamRenderTheme(null, resources.openRawResource(R.raw.vtm_map_theme))
        map.setTheme(theme)

        // Add map layers
        val layers = map.layers()
        layers.add(ModelSetupLayer(map)) // Needed to reset the depth mask, so we can properly render the models at the correct position and blend them behind (obstructing) buildings of the 3d-buildings layer; if we remove this, the model will flicker because the VectorTileRenderer (i think?) randomly fills the entire depth buffer with 0.0, so nothing respecting the depth buffer will draw -> after tile layer, before all other (3d-)layers
        layers.add(BuildingLayer(map, tileLayer, 15, ceil(zoomMax).toInt(), false, false)) // 3D buildings
        layers.add(LabelLayer(map, tileLayer)) // Labels

        if (willMarkersBeVisible) { layers.add(layerMarkerShadows) } // Marker Shadows
        if (willMarkersBeVisible) { layers.add(layerMarkers) } // Markers

        layers.add(layerModels) // 3D Models and Markers
        initCustomModels(layerModels)

        if (willMarkersBeVisible) { layers.add(layerMarkerSelected) } // Selected Markers (should only be 1)

        if (MapContractShowTrip.appliesToIntent(intent)) {
            val paths = GroupLayer(map)
            MapContractShowTrip.parseIntent(intent)?.connections?.forEach {
                val path = it.estimatedPath
                if (path.isEmpty()) { return@forEach }
                val pathLayer = PathLayer(map, LineStyle(org.oscim.backend.canvas.Color.RED, 5f, Paint.Cap.ROUND))
                paths.layers.add(pathLayer)
                pathLayer.addPoints(path.map { p -> GeoPoint(p.lat, p.lon) })
            }
            if (paths.layers.isNotEmpty()) {
                layers.add(paths)
            }
        }

        // Set map limits and initial values
        map.mapPosition = MapPosition(latStart, lonStart, 1.0).setZoom(zoomStart)
        val controller = map.viewport()
        controller.mapLimit = BoundingBox(latMin, lonMin, latMax, lonMax)
        val posRef = MapPosition(latStart, lonStart, 1.0)
        posRef.zoom = zoomMin; controller.minScale = posRef.scale
        posRef.zoom = zoomMax; controller.maxScale = posRef.scale
        controller.maxTilt = 60f

        map.events.bind(this)
    }

    open fun initCustomModels(layer: ModelLayer) {

    }

    private fun initHandlers() {
        mapView.addOnLayoutChangeListener(object : OnLayoutChangeListener {
            override fun onLayoutChange(p0: View?, p1: Int, p2: Int, p3: Int, p4: Int, p5: Int, p6: Int, p7: Int, p8: Int) {
                updateLocationSearch()
                mapView.removeOnLayoutChangeListener(this)
            }
        })

        btnBack.setOnClickListener { finish() }
        btnLocate.setOnClickListener { gotoCurrentLocation() }
        btnHideMarkers.setOnClickListener { toggleMarkerVisibility() }

        pullupStation.addOnCloseListener { _ -> closePullup() }
        if (MapResultContractSelectPOI.appliesToIntent(intent)) {
            pullupStation.addOnConfirmListener { _, _ -> finishResult() }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateWindowInsets()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) { return }
        updateWindowInsets()
    }

    private val spaceCutout: View by LazyView(R.id.fillerCutout)
    private fun updateWindowInsets() {
        val statusBarTop = ViewCompat.getRootWindowInsets(window.decorView)?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0
        val cutoutTop = ViewCompat.getRootWindowInsets(window.decorView)?.displayCutout?.safeInsetTop ?: 0
        var safeTop = cutoutTop.coerceAtLeast(statusBarTop)

        if (safeTop <= 0) { return }

        val rFactor = resources.getFloatValue(R.dimen.map_buttons_top_up_factor)
        val rMax = resources.getDimensionPixelSize(R.dimen.map_buttons_top_up_max)
        safeTop -= (rFactor * safeTop).roundToInt().coerceAtMost(rMax)
        if (safeTop <= 0) { return }

        spaceCutout.updateLayoutParams{ height = safeTop }
    }

    private fun initResourceIntensiveHandlers() {
        removeResourceIntensiveHandlers()

        ntInitGpsHandlers()

        if (sensorAcc != null && sensorMag != null) {
            sensorManager?.registerListener(orientationHandler, sensorAcc, sensorSampling)
            sensorManager?.registerListener(orientationHandler, sensorMag, sensorSampling)
            locationMarker.marker = locationMarkerSymbolDirected
        } else {
            locationMarker.marker = locationMarkerSymbol
        }
    }

    private fun ntInitGpsHandlersIfNeeded() {
        if (gpsHandlersInitialized) { return }
        ntInitGpsHandlers()
    }

    @SuppressLint("MissingPermission")
    private fun ntInitGpsHandlers() {
        locationProviderMain = null

        val lc = locationClient ?: return
        if (!LocationManagerCompat.isLocationEnabled(lc)) { return }

        gpsHandlersInitialized = true

        val enabledProviders = lc.getProviders(true)
        if (enabledProviders.isEmpty()) {
            runOnUiThread {
                mCurrentToast?.cancel()
                mCurrentToast = Toast.makeText(this, R.string.mapNoGpsProviders, Toast.LENGTH_SHORT)
                mCurrentToast?.show()
            }
            return
        }

        lc.removeUpdates(this)
        for (provider in locationProviders) {
            if (!enabledProviders.contains(provider)) { continue }
            if (locationProviderMain == null) { locationProviderMain = provider }
            lc.requestLocationUpdates(provider, locationMinDeltaTime, locationMinDeltaDistance, this)
        }
    }

    @SuppressLint("MissingPermission")
    private fun removeResourceIntensiveHandlers() {
        try { locationClient?.removeUpdates(this) }
        catch (_: Throwable) { Log.w(ECHTZEYT_CONFIGURATION.LOG_TAG, "MapActivity: unable to remove location handler") }

        if (sensorAcc != null) { sensorManager?.unregisterListener(orientationHandler, sensorAcc) }
        if (sensorMag != null) { sensorManager?.unregisterListener(orientationHandler, sensorMag) }
    }

    private fun initThreads() {
        thread(start=true, isDaemon=true) {
            while (true) {
                if (!checkIfForeground.block(15000)) { continue }

                ntCheckIfGpsEnabledChanged()

                val timeNow = System.currentTimeMillis()

                val skipLocateUpdate = !shouldSearchForStations || (nextLocateUpdate < 0) || (timeNow < nextLocateUpdate) || !showMarkers
                if (!skipLocateUpdate) { ntUpdateLocationSearch() }

                Thread.sleep(200)
            }
        }
    }

    override fun onStop() {
        removeResourceIntensiveHandlers()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        updateLocation(force=false, goto=false)
    }

    private var updateMapScaleAddConstant = 0.0001
    private fun updateMapWorkaround() {
        // map.updateMap(true) does not work reliably, so we slightly change the position/scale of the map to force a redraw
        if (map.animator().isActive)  { return } // skip workaround during an animation as (1) -> it already updates due to the animation so no need to update manually and (2) -> it would interrupt the running animation
        map.mapPosition = MapPosition().also { it.copy(map.mapPosition) }.also { it.scale += updateMapScaleAddConstant }
        updateMapScaleAddConstant *= -1
    }

    private fun updateMarkerZoom() {
        updateMarkerAlphas()

        val zoomLevel = map.mapPosition.zoom
        if ((zoomLevel - zoomLastUpdateMarkers).absoluteValue < zoomDeltaNoticeable) { return }

        val scale = scaleMin + (getMarkerZoomValue(zoomLevel) - mScaleOffsetY) * mScaleFactorY
        if ((1-scale/scaleLastUpdateMarkers).absoluteValue < scaleDeltaNoticeable) { return }

        zoomLastUpdateMarkers = zoomLevel
        scaleLastUpdateMarkers = scale

        /*locationMarkerDrawable.setScale(scale)
        locationMarkerDrawableDirected.setScale(scale)*/
        modelMarker.scale = scale
        modelMarkerSelected.scale = scale
        modelMarkerShadow.scale = scale
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

    private fun updateLocationSearch(after: Long) {
        if (!shouldSearchForStations) { return }
        nextLocateUpdate = System.currentTimeMillis() + after
    }

    private fun updateLocationSearch() {
        updateLocationSearch(0)
    }

    private fun ntUpdateLocationSearch() {
        checkMobileDataUsage() // not-ui-thread-safe even though it is not nt-prefixed
        if (!shouldSearchForStations) { return }

        val locationArea = map.getBoundingBox(0)

        if (!checkIfForeground.block(0)) { return }

        val areaCenter = locationArea.centerPoint
        val center = LocationLatLonImpl(areaCenter.latitude, areaCenter.longitude)
        val width = 2 * locationArea.longitudeSpan
        val height = 2 * locationArea.latitudeSpan
        val area = LocationAreaRect(center, width, height)
        val pois: List<POI>
        try { pois = transportLocateStationAPI.locatePOIs(LocatePOIRequestImpl(location=area)).pois } catch (_: Exception) { return }

        nextLocateUpdate = -1L

        if (!checkIfForeground.block(0)) { return }

        pois.forEach(::addPOIMarker)
        map.updateMap()
    }

    fun selectPOI(poi: POI) {
        poiSelected = poi
        pullupStation.setPOI(poi, true)

        synchronized(layerMarkerSelected.instanceSync) {
            if (layerMarkerSelected.instances.size == 1 && layerMarkerSelected.instances.find { poi.sameAs(it.data.uid) } != null) { return }
            layerMarkerSelected.instances.forEach {
                it.moveToLayer(layerMarkers)
            }
        }

        synchronized(layerMarkers) {
            layerMarkers.instances.find { poi.sameAs(it.data.uid) }?.moveToLayer(layerMarkerSelected)
        }
        map.updateMap()
    }

    fun addPOIMarker(poi: POI) {
        val locMarker = (poi.location as? LocationLatLon)?.let { GeoPoint(it.lat, it.lon) } ?: return // has no location, cannot be displayed in map

        synchronized(layerMarkers.instanceSync) {
            if (layerMarkers.instances.find { (it.data.uid as? POI).sameAs(poi) } != null) { return }
        }
        synchronized(layerMarkerSelected.instanceSync) {
            if (layerMarkerSelected.instances.find { (it.data.uid as? POI).sameAs(poi) } != null) { return }
        }

        val instanceData = InstanceData(poi, locMarker)
        val instance = layerMarkers.createInstance(instanceData)
        if (poi.sameAs(poiSelected)) { instance.moveToLayer(layerMarkerSelected) }
        layerMarkerShadows.createInstance(instanceData.createShadowData())
    }

    private fun getStartLatitude() : Double {
        return (poiSelected?.location as? LocationLatLon)?.lat ?: resources.getFloatValue(R.dimen.map_lat_start).toDouble()
    }

    private fun getStartLongitude() : Double {
        return (poiSelected?.location as? LocationLatLon)?.lon ?: resources.getFloatValue(R.dimen.map_lon_start).toDouble()
    }

    private fun getStartZoom() : Double {
        var zoom = zoomDefault
        if (poiSelected != null) { zoom = zoomStation }
        return zoom
    }

    private fun finishResult() {
        val result = when {
            MapResultContractSelectPOI.appliesToIntent(intent) -> poiSelected?.let { MapResultContractSelectPOI.createResult(it) }
            else -> null
        }
        result?.let { setResult(RESULT_OK, it) }
        finish()
    }

    override fun finish() {
        mCurrentDialog?.dismiss()
        super.finish()
    }

    private fun askForLocationPermissions() {
        locationPermissionRequest.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    private fun gotoCurrentLocation() {
        val locCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val locFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!locCoarse && !locFine) { askForLocationPermissions() }
        if (locationProviderMain == null) { ntInitGpsHandlers() }
        updateLocation(force=true, goto=true)
    }

    private fun ntCheckIfGpsEnabledChanged() {
        val now = System.currentTimeMillis()
        if (gpsEnabledNextCheck < 0) { return }
        if (now < gpsEnabledNextCheck) { return }

        val lc = locationClient ?: return

        val gpsEnabledT = gpsEnabled
        gpsEnabled = LocationManagerCompat.isLocationEnabled(lc)

        if (gpsEnabled != gpsEnabledT) {
            Thread.sleep(200)
            runOnUiThread { updateLocation(force=false, goto=false) }
        }

        gpsEnabledNextCheck = now + gpsEnabledNextCheckAfter
    }

    @SuppressLint("MissingPermission")
    private fun updateLocation(force: Boolean, goto: Boolean) {
        // only really update when the last known location is older than our update time
        val now = System.currentTimeMillis()
        if (now - (locationLast?.time?:0) < locationMinDeltaTime) {
            if (goto) { gotoLocation(locationLast) }
            return
        }

        val lc = locationClient ?: return

        if (!LocationManagerCompat.isLocationEnabled(lc)) {
            onLocationChanged(null, false)
            gpsEnabled = false
            gpsEnabledNextCheck = now + 2000
            if (force) { askForLocationTurnedOn() }
            return
        }

        if (locationProviderMain == null) { ntInitGpsHandlersIfNeeded() }
        val lp = locationProviderMain ?: return

        if (force) {
            val cs: android.os.CancellationSignal? = null
            LocationManagerCompat.getCurrentLocation(lc, lp, cs, ContextCompat.getMainExecutor(this)) { onLocationChanged(it, goto) }
            return
        }

        onLocationChanged(lc.getLastKnownLocation(lp), goto)
    }

    override fun onLocationChanged(location: Location) {
        locationLast = location

        gpsEnabled = true
        gpsEnabledNextCheck = System.currentTimeMillis() + 5000

        // only update our animations if there is a significant difference between our last updated position and now
        locationLastUpdate?.let {
            // if the new latitude/longitude/... is different -> skip the return statement by returning from the let block
            if ((location.latitude - it.latitude).absoluteValue > Double.MIN_VALUE) { return@let }
            if ((location.longitude - it.longitude).absoluteValue > Double.MIN_VALUE) { return@let }
            // if no component was (sufficiently) different from the last update, return from the function
            return
        }
        locationLastUpdate = location

        locationMarker.geoPoint = GeoPoint(location.latitude, location.longitude) // TODO: animate
        layerMarkerLocation.isEnabled = true
        layerMarkerLocation.populate()
        updateMarkerAlphas()
        map.updateMap(true)
    }

    private fun onLocationChanged(location: Location?, goto: Boolean) {
        if (location == null) {
            locationLast = null
            //layerMarkerLocation.isEnabled = false
            map.updateMap()
            return
        }
        onLocationChanged(location)
        if (goto) { gotoLocation(location) }
    }

    private fun gotoLocation(location: Location?) {
        if (location == null) { return }
        val position = MapPosition(location.latitude, location.longitude, map.mapPosition.scale)
        if (!map.viewport().mapLimit.contains(position.geoPoint)) {
            mCurrentToast?.cancel()
            mCurrentToast = Toast.makeText(this, R.string.mapOutsidePermittedArea, Toast.LENGTH_SHORT)
            mCurrentToast?.show()
            return
        }

        position.zoom = position.zoom.coerceAtLeast(zoomDefault)
        map.animator().animateTo(700L, position)
    }

    private fun askForLocationTurnedOn() {
        // thanks to https://stackoverflow.com/questions/43138788/ask-user-to-turn-on-location for parts of this code
        if (mCurrentDialogType == DIALOG_LOCATION) { return } // dont show the dialog if we already have the same dialog open
        mCurrentDialog?.dismiss()

        mCurrentDialogType = DIALOG_LOCATION
        mCurrentDialog = AlertDialog.Builder(this, R.style.Theme_Echtzeyt_Dialog_Alert)
            .setTitle(R.string.mapLocationEnableTitle)
            .setIcon(R.drawable.ic_locate)
            .setMessage(R.string.mapLocationEnableText)
            .setPositiveButton(R.string.mapLocationEnableDone) { _, _ -> updateLocation(force=false, goto=true) }
            .setNeutralButton(R.string.mapLocationEnableSettings) { _, _ -> startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
            .setNegativeButton(R.string.mapLocationEnableCancel) { dialog, _ -> dialog.cancel() }
            .setOnDismissListener { mCurrentDialogType = 0 }
            .show()
    }

    private fun updateMarkerAlphas() {
        if (!willMarkersBeVisible) { return }
        val zoom = map.mapPosition.zoom
        modelMarkerShadow.alpha = when {
            !showMarkers -> 0.0
            zoom > zoomShadowMax -> 1.0
            zoom > zoomShadowMin -> (zoom - zoomShadowMin) / (zoomShadowMax - zoomShadowMin)
            else -> 0.0
        }

        val markerSelectedAlpha = when {
            showMarkers -> 1.0
            else -> 0.0
        }
        modelMarkerSelected.alpha = markerSelectedAlpha

        val markersAlphaT = modelMarker.alpha
        val markersAlpha = when {
            !showMarkers -> 0.0
            zoom > zoomMarkerMax -> 1.0
            zoom > zoomMarkerMin -> ((zoom - zoomMarkerMin) / (zoomMarkerMax - zoomMarkerMin))
            else -> 0.0
        }
        if (markersAlpha == markersAlphaT) { return }

        val significantChange = (markersAlpha - markersAlphaT).absoluteValue > 0.05
        val insignificantChange = (markersAlpha - markersAlphaT).absoluteValue <= 0.02
        val extremeAlpha = markersAlpha >= 0.999 || markersAlpha < 0.001
        if (insignificantChange && !extremeAlpha) { return }

        modelMarker.alpha = markersAlpha
        if (significantChange || extremeAlpha) {
            updateMapWorkaround()
        }
    }

    private fun closePullup() {
        poiSelected = null
        synchronized(layerMarkerSelected.instanceSync) {
            layerMarkerSelected.instances.forEach { it.moveToLayer(layerMarkers) }
        }
        map.updateMap()
    }

    private fun toggleMarkerVisibility() {
        showMarkers = !showMarkers
        updateMarkerAlphas()
        btnHideMarkers.setImageResource(if (showMarkers) R.drawable.ic_stationmark_visible else R.drawable.ic_stationmark_hidden)
    }

    @SuppressLint("MissingPermission")
    private fun checkMobileDataUsage() {
        if (!checkIfForeground.block(0)) { return }
        if (mAskForMobileDataCount <= 0) { return } // dont show the dialog if we have asked too many times
        //if (map.useDataConnection()) { return } // dont show the dialog if mobile data usage is allowed, the tiles will load eventually
        if (mCurrentDialogType == DIALOG_MOBILE_DATA) { return } // dont show the dialog if we are already showing such a dialog
        if (mAskForMobileDataNext < 0) { return }
        val now = System.currentTimeMillis()
        if (now < mAskForMobileDataNext) { return } // dont show the dialog if we showed the last dialog just a second ago
        //val tileStates = map.overlayManager.tilesOverlay.tileStates
        //if (tileStates.notFound + tileStates.scaled < 0.3 * tileStates.total) { return } // dont show the dialog when enough tiles are loaded (i.e. they are in the cache)
        val connManager = ContextCompat.getSystemService(this, ConnectivityManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (connManager != null && connManager.activeNetwork == null) { return } // dont show if there is no active network
        }
        if (connManager != null && !connManager.isActiveNetworkMetered) { return } // dont show the dialog when we are not on a metered connection

        if (true) { return } // TODO: do we need to ask for mobile data connection? maybe for downloading map updates?

        mAskForMobileDataCount--
        mAskForMobileDataNext = now + mAskForMobileDataAgainAfter
        mCurrentDialogType = DIALOG_MOBILE_DATA

        runOnUiThread {
            mCurrentDialog?.dismiss()
            mCurrentDialog = AlertDialog.Builder(this, R.style.Theme_Echtzeyt_Dialog_Alert)
                .setTitle(R.string.mapMobileAllowTitle)
                .setMessage(R.string.mapMobileAllowText)
                .setIcon(R.drawable.ic_cellular)
                .setPositiveButton(R.string.mapMobileAllowPermanent) { _, _ -> /*map.setUseDataConnection(true);*/ preferences.edit { putBoolean("mapUseMobileData", true) } }
                .setNeutralButton(R.string.mapMobileAllowTemporary) { _, _ -> /*map.setUseDataConnection(true)*/ }
                .setNegativeButton(R.string.mapMobileAllowNo) { _, _ -> preferences.edit { putBoolean("mapUseMobileData", false) } }
                .setCancelable(true)
                .setOnDismissListener { mCurrentDialogType = 0; mAskForMobileDataNext = System.currentTimeMillis() + mAskForMobileDataAgainAfter }
                .show()
        }
    }

    override fun onPause() {
        mCurrentDialog?.dismiss()
        mapView.onPause()
        super.onPause()
    }

    override fun onMapEvent(p0: Event?, p1: MapPosition?) {
        updateLocationSearch(50)
        updateMarkerZoom()
    }

    // On station marker pressed
    override fun onModelClicked(instance: BaseInstanceData): Boolean {
        (instance.uid as? POI)?.let { selectPOI(it) } ?: return false
        return true
    }
}