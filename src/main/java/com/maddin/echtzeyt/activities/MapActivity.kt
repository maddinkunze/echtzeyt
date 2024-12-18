package com.maddin.echtzeyt.activities

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.drawable.BitmapDrawable
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
import android.os.ConditionVariable
import android.provider.Settings
import android.text.method.LinkMovementMethod
import android.util.Log
import android.util.TypedValue
import android.view.View
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
import androidx.core.net.ConnectivityManagerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import com.maddin.echtzeyt.ECHTZEYT_CONFIGURATION
import com.maddin.echtzeyt.R
import com.maddin.echtzeyt.components.FloatingButton
import com.maddin.echtzeyt.components.PositionMarker
import com.maddin.echtzeyt.components.StationPullup
import com.maddin.echtzeyt.components.StopMarker
import com.maddin.echtzeyt.components.createShadowBitmap
import com.maddin.echtzeyt.components.getShadowColors
import com.maddin.echtzeyt.randomcode.ActivityResultSerializable
import com.maddin.echtzeyt.randomcode.DynamicDrawable
import com.maddin.echtzeyt.randomcode.LazyMutable
import com.maddin.echtzeyt.randomcode.LazyView
import com.maddin.transportapi.components.LocationAreaRect
import com.maddin.transportapi.components.LocationLatLon
import com.maddin.transportapi.components.LocationLatLonImpl
import com.maddin.transportapi.components.POI
import com.maddin.transportapi.endpoints.LocatePOIRequestImpl
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import java.io.File
import java.lang.IllegalArgumentException
import kotlin.concurrent.thread
import kotlin.math.absoluteValue
import kotlin.math.pow
import kotlin.math.roundToInt


fun Resources.getFloatValue(@DimenRes floatRes: Int):Float{
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { return getFloat(floatRes) }

    val out = TypedValue()
    getValue(floatRes, out, true)
    return out.float
}

class MapResultContractSelectPOI : ActivityResultSerializable<POI, POI>(ECHTZEYT_CONFIGURATION.activityMap) {
    companion object {
        const val ACTION_SELECT_STATION = "select_station"
        fun appliesToIntent(intent: Intent?): Boolean {
            return intent?.getStringExtra(ACTION) == ACTION_SELECT_STATION
        }
        fun createResult(station: POI): Intent {
            return ActivityResultSerializable.createResult(station)
        }
        fun parseIntent(intent: Intent?): POI? {
            return ActivityResultSerializable.parseIntent(intent)
        }
    }
    override fun createIntent(context: Context, input: POI?): Intent {
        return super.createIntent(context, input).putExtra(ACTION, ACTION_SELECT_STATION)
    }
}

@Suppress("MemberVisibilityCanBePrivate")
open class MapActivity : EchtzeytForegroundActivity(), LocationListener {
    private val map: MapView by LazyView(R.id.mapView)
    private val txtCopyright: TextView by LazyView(R.id.txtMapCopyright)
    private val btnBack: FloatingButton by LazyView(R.id.btnMapBack)
    private val btnLocate: FloatingButton by LazyView(R.id.btnMapLocate)

    // Stuff for locating/searching stations
    private val shouldSearchForStations by lazy { MapResultContractSelectPOI.appliesToIntent(intent) }
    private var nextLocateUpdate = -1L
    private val stationsFound = mutableSetOf<String>()
    @Volatile private lateinit var currentLocationArea : BoundingBox
    protected val transportLocateStationAPI by lazy { ECHTZEYT_CONFIGURATION.mapsStationAPI!! }
    protected var poiSelected: POI? by LazyMutable { MapResultContractSelectPOI.parseIntent(intent) }

    private val pullupStation: StationPullup by LazyView(R.id.pullupStationInfo)
    private val btnHideMarkers: FloatingButton by LazyView(R.id.btnMapHideMarkers)

    // All sorts of markers and whatnot
    private val willMarkersBeVisible by lazy { MapResultContractSelectPOI.appliesToIntent(intent) }
    private val drawableMarker by lazy { DynamicDrawable(VectorDrawableCompat.create(resources, R.drawable.stationmark, null)!!) }
    private val drawableMarkerSelected by lazy { DynamicDrawable(VectorDrawableCompat.create(resources, R.drawable.stationmark_selected, null)!!) }
    protected val drawableMarkerShadow by lazy {
        val w = resources.getDimensionPixelSize(R.dimen.stationmarker_width)
        val ha = resources.getDimensionPixelSize(R.dimen.stationmarker_arrow_size)
        val h = resources.getDimensionPixelSize(R.dimen.stationmarker_height) - ha
        val r = resources.getDimensionPixelSize(R.dimen.stationmarker_radius)
        val ss = resources.getDimensionPixelSize(R.dimen.stationmarker_shadow_size)
        val bitmapShadow = createShadowBitmap(w, h, r, ss, resources.getShadowColors(R.array.shadowColors, R.array.shadowStops)) ?: return@lazy null
        val bitmapShadowS = Bitmap.createBitmap(bitmapShadow.width, bitmapShadow.height+ha-ss, Bitmap.Config.ARGB_8888)
        bitmapShadowS.applyCanvas { drawBitmap(bitmapShadow, 0f, 0f, null) }// currently, the shadow will be aligned on the bottom, and therefore it will may clipped at the bottom
        // if someone wants a shadow that is larger than sm_arrow_size, it may look weird
        // TODO: to fix this, the anchor could be recalculated if the drawable exceeds the "lower limit"
        DynamicDrawable(BitmapDrawable(resources, bitmapShadowS), optimize=true)
    }
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
    private var zoomLastUpdateMarkerVisibility = Double.POSITIVE_INFINITY
    protected val zoomDeltaNoticeable = 0.01
    protected val zoomDeltaVisibilityNoticeable = 0.25
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
    protected val overscrollX by lazy { 0 }//resources.displayMetrics.widthPixels.coerceAtLeast(resources.getDimensionPixelOffset(R.dimen.stationmarker_size)) }
    protected val overscrollY by lazy { 0 }//resources.displayMetrics.heightPixels.coerceAtLeast(resources.getDimensionPixelOffset(R.dimen.stationmarker_size)) }

    protected val colorFilterLight: ColorFilter by lazy { com.maddin.echtzeyt.randomcode.FILTER_OSM_LIGHT }
    protected val colorFilterDark: ColorFilter by lazy { com.maddin.echtzeyt.randomcode.FILTER_OSM_DARK }

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
    private val locationMarkerDrawable by lazy { DynamicDrawable(VectorDrawableCompat.create(resources, R.drawable.locationmark, null)!!) }
    private val locationMarkerDrawableDirected by lazy { DynamicDrawable(VectorDrawableCompat.create(resources, R.drawable.locationmark_directed, null)!!) }
    private val locationMarker by lazy { PositionMarker(map) }
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

                locationMarker.rotation = -mAzimutDegMovAvg.toFloat()

                // only force a redraw of the map, when the change would be noticeable
                if ((mAzimutDegMovAvg - mLastAzimutDeg).absoluteValue < mDeltaAzimutDegNoticeable) {
                    return
                }
                mLastAzimutDeg = mAzimutDegMovAvg
                if (!locationMarker.isVisible()) { return }
                map.invalidate()
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

        initOSM()
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

    private fun initOSM() {
        val osmconfig = Configuration.getInstance()
        osmconfig.isDebugMapTileDownloader = true
        osmconfig.osmdroidTileCache = File(cacheDir.absoluteFile, "osmdroid")
        osmconfig.userAgentValue = ECHTZEYT_CONFIGURATION.osmdroidUserAgent
    }

    @Suppress("DEPRECATION")
    private fun initWindow() {
        if (Build.VERSION.SDK_INT in 19..20) {
            setWindowFlag(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS, true)
        }
        if (Build.VERSION.SDK_INT >= 19) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }
        if (Build.VERSION.SDK_INT >= 21) {
            setWindowFlag(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS, false)
            window.statusBarColor = Color.TRANSPARENT
        }

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

        locationMarker.icon = locationMarkerDrawable

        if (shouldSearchForStations) {
            poiSelected?.let { selectPOI(it) }
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
        map.setUseDataConnection(preferences.getBoolean("mapUseMobileData", false))
        checkMobileDataUsage()
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
        map.overlayManager.tilesOverlay.setColorFilter(if (isInNightMode()) { colorFilterDark } else { colorFilterLight })

        map.overlays.add(locationMarker)

        //map.isTilesScaledToDpi = true // TODO: check if this looks good on other devices
        map.tilesScaleFactor = zoomTile

        map.requestLayout()
    }

    private fun initHandlers() {
        map.addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean { updateLocationSearch(50); updateMarkerVisibilities(100); return false }
            override fun onZoom(event: ZoomEvent?): Boolean { updateLocationSearch(50); updateMarkerZoom(); return false }
        })
        map.addOnFirstLayoutListener { _, _, _, _, _ -> updateLocationSearch() }

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
            locationMarker.icon = locationMarkerDrawableDirected
        } else {
            locationMarker.icon = locationMarkerDrawable
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
                ntUpdateMarkerVisibilities()

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
        updateLocation(force=false, goto=false)
    }

    private fun updateMarkerZoom() {
        updateMarkerAlphas()

        val zoomLevel = map.zoomLevelDouble
        if ((zoomLevel - zoomLastUpdateMarkerVisibility).absoluteValue > zoomDeltaVisibilityNoticeable) { updateMarkerVisibilities(0) }
        if ((zoomLevel - zoomLastUpdateMarkers).absoluteValue < zoomDeltaNoticeable) { return }

        val scale = scaleMin + (getMarkerZoomValue(zoomLevel) - mScaleOffsetY) * mScaleFactorY
        if ((1-scale/scaleLastUpdateMarkers).absoluteValue < scaleDeltaNoticeable) { return }

        zoomLastUpdateMarkers = zoomLevel
        scaleLastUpdateMarkers = scale

        drawableMarker.scale = scale
        drawableMarkerSelected.scale = scale
        drawableMarkerShadow?.scale = scale
        locationMarkerDrawable.scale = scale
        locationMarkerDrawableDirected.scale = scale

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
        if (!shouldSearchForStations) { return }
        nextLocateUpdate = System.currentTimeMillis() + after
    }

    private fun updateLocationSearch() {
        updateLocationSearch(0)
    }

    private fun ntUpdateLocationSearch() {
        checkMobileDataUsage() // not-ui-thread-safe even though it is not nt-prefixed
        if (!shouldSearchForStations) { return }

        val condition = ConditionVariable()
        var markerBB: BoundingBox? = null
        var mapZoom = 0.0
        runOnUiThread {
            updateLocationArea()
            markerBB = getMarkerVisibilitiesBoundingBox()
            mapZoom = map.zoomLevelDouble
            condition.open()
        }
        condition.block()

        if (!checkIfForeground.block(0)) { return }

        val areaCenter = currentLocationArea.centerWithDateLine
        val center = LocationLatLonImpl(areaCenter.latitude, areaCenter.longitude)
        val width = 2 * currentLocationArea.longitudeSpanWithDateLine
        val height = 2 * currentLocationArea.latitudeSpan
        val area = LocationAreaRect(center, width, height)
        val pois: List<POI>
        try { pois = transportLocateStationAPI.locatePOIs(LocatePOIRequestImpl(location=area)).pois.filter { it.id?.let { id -> stationsFound.add(id.uuid) } ?: false } } catch(e: Exception) { return }

        nextLocateUpdate = -1L

        if (mapZoom > zoomMarkerMin) updateMarkerVisibilities(markerBB)
        if (!checkIfForeground.block(0)) { return }

        runOnUiThread {
            for (poi in pois) {
                poi.location as? LocationLatLon ?: continue

                val marker = StopMarker(map, poi, locationMarker)
                marker.icon = drawableMarker
                marker.setIcon(drawableMarkerSelected, StopMarker.FLAG_SELECTED)
                marker.setOnMarkerClickListener { _, _ -> selectPOI(poi); true }
                marker.setVisible(showMarkers)
                marker.updateVisibility(markerBB)
                poiSelected?.let { marker.selectAndDeselectOthers(it) }
                map.overlayManager.add(marker)

                if (drawableMarkerShadow == null) { continue }
                val shadowMarker = StopMarker(map, poi, null)
                shadowMarker.icon = drawableMarkerShadow
                shadowMarker.setOnMarkerClickListener { _, _ -> true }
                shadowMarker.setVisible(showMarkers)
                shadowMarker.updateVisibility(markerBB)
                map.overlayManager.add(0, shadowMarker)
            }

            map.invalidate()
        }
    }

    fun selectPOI(poi: POI) {
        poiSelected = poi
        pullupStation.setPOI(poi, true)

        for (marker in map.overlays) {
            if (marker !is StopMarker) { continue }
            marker.selectAndDeselectOthers(poi)
        }
        map.invalidate()
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

        val position = GeoPoint(location.latitude, location.longitude)
        locationMarker.animateToPosition(position)
        locationMarker.setVisible(true)
        updateMarkerAlphas()
        map.invalidate()
    }

    private fun onLocationChanged(location: Location?, goto: Boolean) {
        if (location == null) {
            locationLast = null
            locationMarker.setVisible(false)
            map.invalidate()
            return
        }
        onLocationChanged(location)
        if (goto) { gotoLocation(location) }
    }

    private fun gotoLocation(location: Location?) {
        if (location == null) { return }
        val position = GeoPoint(location.latitude, location.longitude)
        if (!map.boundingBox.contains(position)) {
            mCurrentToast?.cancel()
            mCurrentToast = Toast.makeText(this, R.string.mapOutsidePermittedArea, Toast.LENGTH_SHORT)
            mCurrentToast?.show()
            return
        }

        val zoomTo = map.zoomLevelDouble.coerceAtLeast(zoomDefault)
        map.controller.animateTo(position, zoomTo, 700L)
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
        val zoom = map.zoomLevelDouble
        drawableMarkerShadow?.setOptimizedAlpha(when {
            !showMarkers -> 0
            zoom > zoomShadowMax -> 255
            zoom > zoomShadowMin -> (255 * ((zoom - zoomShadowMin) / (zoomShadowMax - zoomShadowMin))).toInt()
            else -> 0
        })

        val markerSelectedAlpha = when {
            showMarkers -> 255
            else -> 0
        }
        drawableMarkerSelected.setOptimizedAlpha(markerSelectedAlpha)

        val markersAlphaT = drawableMarker.getOptimizedAlpha()
        val markersAlpha = when {
            !showMarkers -> 0
            zoom > zoomMarkerMax -> 255
            zoom > zoomMarkerMin -> (255 * ((zoom - zoomMarkerMin) / (zoomMarkerMax - zoomMarkerMin))).roundToInt()
            else -> 0
        }
        if (markersAlpha == markersAlphaT) { return }
        if ((markersAlpha - markersAlphaT).absoluteValue <= 5 && markersAlpha != 0 && markersAlpha != 255) { return }
        drawableMarker.setOptimizedAlpha(markersAlpha)
        if (markersAlpha < 5 || markersAlphaT < 5) { updateMarkerVisibilities(getMarkerVisibilitiesBoundingBox()) }
        if ((markersAlpha - markersAlphaT).absoluteValue > 15 || markersAlpha == 255 || markersAlpha == 0) {
            map.invalidate()
        }
    }

    private fun getMarkerVisibilitiesBoundingBox(): BoundingBox {
        val mapbb = map.boundingBox
        val lonOffset = 2 * drawableMarker.intrinsicWidth * (mapbb.longitudeSpanWithDateLine / map.width)
        val latOffset = 2 * drawableMarker.intrinsicHeight * (mapbb.latitudeSpan / map.height)
        return BoundingBox(mapbb.latNorth+latOffset, mapbb.lonEast+lonOffset, mapbb.latSouth-latOffset, mapbb.lonWest-lonOffset)
    }

    private fun updateMarkerVisibilities(within: BoundingBox?) {
        for (marker in map.overlays) {
            if (marker !is StopMarker) { continue }
            marker.updateVisibility(within)
        }

        updateMarkerVisibilities(10_000) // request another update after at most 10s
        runOnUiThread {
            zoomLastUpdateMarkerVisibility = map.zoomLevelDouble
        }
    }

    @Suppress("SameParameterValue")
    protected fun updateMarkerVisibilities(after: Long) {
        if (!willMarkersBeVisible) { return }
        var next = System.currentTimeMillis() + after
        if (nextUpdateMarkerVisibility >= 0) {
            next = nextUpdateMarkerVisibility.coerceAtMost(next)
        }
        nextUpdateMarkerVisibility = next
    }

    private fun ntUpdateMarkerVisibilities() {
        if (!willMarkersBeVisible) { return }
        if (nextUpdateMarkerVisibility < 0) { return }
        val now = System.currentTimeMillis()
        if (now < nextUpdateMarkerVisibility) { return }

        val condition = ConditionVariable()
        var boundingBox: BoundingBox? = null
        var zoom = 0.0
        runOnUiThread {
            // sometimes osmdroid throws an exception when the map is not loaded -> simply skip
            try { boundingBox = getMarkerVisibilitiesBoundingBox() } catch (_: IllegalArgumentException) { println("MADDIN101: wth") }
            zoom = map.zoomLevelDouble
            condition.open()
        }
        condition.block()

        if (boundingBox == null) { return }
        /* there is other code in updateMarkerAlphas to make sure the markers are hidden as soon as
           the zoom exceeds their max display zoom; at the max zoom level, most markers would be
           included in the check -> optimization by skipping these cases */
        if (zoom < zoomMarkerMin) {
            nextUpdateMarkerVisibility = -1 // temporarily disable these checks to avoid unnecessary runOnUiThread calls
            return
        }

        updateMarkerVisibilities(boundingBox)
    }

    private fun closePullup() {
        poiSelected = null
        map.overlays.forEach {
            if (it !is StopMarker) { return@forEach }
            it.deselect()
        }
        map.invalidate()
    }

    private fun toggleMarkerVisibility() {
        showMarkers = !showMarkers

        updateMarkerAlphas()
        map.invalidate()

        btnHideMarkers.setImageResource(if (showMarkers) R.drawable.ic_stationmark_visible else R.drawable.ic_stationmark_hidden)
    }

    @SuppressLint("MissingPermission")
    private fun checkMobileDataUsage() {
        if (!checkIfForeground.block(0)) { return }
        if (mAskForMobileDataCount <= 0) { return } // dont show the dialog if we have asked too many times
        if (map.useDataConnection()) { return } // dont show the dialog if mobile data usage is allowed, the tiles will load eventually
        if (mCurrentDialogType == DIALOG_MOBILE_DATA) { return } // dont show the dialog if we are already showing such a dialog
        if (mAskForMobileDataNext < 0) { return }
        val now = System.currentTimeMillis()
        if (now < mAskForMobileDataNext) { return } // dont show the dialog if we showed the last dialog just a second ago
        val tileStates = map.overlayManager.tilesOverlay.tileStates
        if (tileStates.notFound + tileStates.scaled < 0.3 * tileStates.total) { return } // dont show the dialog when enough tiles are loaded (i.e. they are in the cache)
        val connManager = ContextCompat.getSystemService(this, ConnectivityManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (connManager != null && connManager.activeNetwork == null) { return } // dont show if there is no active network
        }
        if (connManager != null && !ConnectivityManagerCompat.isActiveNetworkMetered(connManager)) { return } // dont show the dialog when we are not on a metered connection

        mAskForMobileDataCount--
        mAskForMobileDataNext = now + mAskForMobileDataAgainAfter
        mCurrentDialogType = DIALOG_MOBILE_DATA

        runOnUiThread {
            mCurrentDialog?.dismiss()
            mCurrentDialog = AlertDialog.Builder(this, R.style.Theme_Echtzeyt_Dialog_Alert)
                .setTitle(R.string.mapMobileAllowTitle)
                .setMessage(R.string.mapMobileAllowText)
                .setIcon(R.drawable.ic_cellular)
                .setPositiveButton(R.string.mapMobileAllowPermanent) { _, _ -> map.setUseDataConnection(true); preferences.edit { putBoolean("mapUseMobileData", true) } }
                .setNeutralButton(R.string.mapMobileAllowTemporary) { _, _ -> map.setUseDataConnection(true) }
                .setNegativeButton(R.string.mapMobileAllowNo) { _, _ -> preferences.edit { putBoolean("mapUseMobileData", false) } }
                .setCancelable(true)
                .setOnDismissListener { mCurrentDialogType = 0; mAskForMobileDataNext = System.currentTimeMillis() + mAskForMobileDataAgainAfter }
                .show()
        }
    }

    override fun onPause() {
        mCurrentDialog?.dismiss()
        super.onPause()
    }
}