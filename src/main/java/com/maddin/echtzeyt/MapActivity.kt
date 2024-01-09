package com.maddin.echtzeyt

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
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.provider.Settings
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DimenRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.graphics.applyCanvas
import androidx.core.location.LocationManagerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.maddin.echtzeyt.components.FloatingButton
import com.maddin.echtzeyt.components.PositionMarker
import com.maddin.echtzeyt.components.PullupScrollView
import com.maddin.echtzeyt.components.StopMarker
import com.maddin.echtzeyt.components.createShadowBitmap
import com.maddin.echtzeyt.components.getShadowColors
import com.maddin.echtzeyt.randomcode.ActivityResultSerializable
import com.maddin.echtzeyt.randomcode.DynamicDrawable
import com.maddin.echtzeyt.randomcode.getSerializableExtraCompat
import com.maddin.transportapi.LocatableStation
import com.maddin.transportapi.LocationAreaRect
import com.maddin.transportapi.LocationLatLon
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


fun Resources.getFloatValue(@DimenRes floatRes: Int):Float{
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { return getFloat(floatRes) }

    val out = TypedValue()
    getValue(floatRes, out, true)
    return out.float
}

@Suppress("MemberVisibilityCanBePrivate")
open class MapActivity : AppCompatActivity() {
    private var nextLocateUpdate = -1L
    private var lastLocateUpdate = -1L
    private lateinit var currentLocationArea : BoundingBox
    private var transportLocateStationAPI : LocationStationAPI = com.maddin.transportapi.impl.germany.VMS("Chemnitz") // TODO: generalize

    //private val preferences by lazy { getSharedPreferences(PREFERENCES_NAME(this), MODE_PRIVATE) }

    private val map by lazy { findViewById<MapView>(R.id.mapView) }
    private val txtStation by lazy { findViewById<TextView>(R.id.txtStationName) }
    private val pullup : PullupScrollView by lazy { findViewById(R.id.scrollStationInfo) }
    protected val drawableMarker by lazy { DynamicDrawable(AppCompatResources.getDrawable(this, R.drawable.stationmark)!!) }
    protected val drawableMarkerSelected by lazy { DynamicDrawable(AppCompatResources.getDrawable(this, R.drawable.stationmark_selected)!!) }
    protected val drawableMarkerShadow by lazy {
        val w = resources.getDimensionPixelSize(R.dimen.stationmarker_width)
        val ha = resources.getDimensionPixelSize(R.dimen.stationmarker_arrow_size)
        val h = resources.getDimensionPixelSize(R.dimen.stationmarker_height) - ha
        val r = resources.getDimensionPixelSize(R.dimen.stationmarker_radius)
        val ss = resources.getDimensionPixelSize(R.dimen.stationmarker_shadow_size)
        val shadowBitmap = createShadowBitmap(w, h, r, ss, resources.getShadowColors(R.array.shadow_colors, R.array.shadow_stops)) ?: return@lazy null
        // currently, the shadow will be aligned on the bottom, and therefore it will may clipped at the bottom
        // if someone wants a shadow that is larger than sm_arrow_size, it may look weird
        // TODO: to fix this, the anchor could be recalculated if the drawable exceeds the "lower limit"
        val shadowBitmapS = Bitmap.createBitmap(shadowBitmap.width, shadowBitmap.height+ha-ss, Bitmap.Config.ARGB_8888)
        shadowBitmapS.applyCanvas { drawBitmap(shadowBitmap, 0f, 0f, null) }
        DynamicDrawable(BitmapDrawable(resources, shadowBitmapS))
    }

    protected val zoomMin by lazy { resources.getFloatValue(R.dimen.map_zoom_min).toDouble() }
    protected val zoomMax by lazy { resources.getFloatValue(R.dimen.map_zoom_max).toDouble() }
    protected val zoomStart by lazy { getStartZoom() }
    protected val zoomDefault by lazy { resources.getFloatValue(R.dimen.map_zoom_start).toDouble() }
    protected val zoomStation by lazy { resources.getFloatValue(R.dimen.map_zoom_station).toDouble() }
    private var zoomLastUpdateMarkers = Double.POSITIVE_INFINITY
    protected var zoomDeltaNoticeable = 0.01
    protected val zoomTile = 0.95f

    protected val scaleMin = 0.25
    protected val scaleMax = 0.9
    protected val scaleZoomFactor = 1.0
    private var scaleLastUpdateMarkers = Double.POSITIVE_INFINITY
    protected val scaleDeltaNoticeable = 0.007

    private var mScaleFactorY = 0.0
    private var mScaleOffsetY = 0.0

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

    protected var stationSelected: LocatableStation? = null

    private val locationPermissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { updateLocation(force=true, goto=true) }
    private val locationClient by lazy { ContextCompat.getSystemService(this, LocationManager::class.java)!! } // TODO: make null safety checks
    private val locationProvider = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { LocationManager.FUSED_PROVIDER } else { LocationManager.GPS_PROVIDER }
    private val locationMarkerDrawable by lazy { DynamicDrawable(AppCompatResources.getDrawable(this, R.drawable.locationmark)!!) }
    private val locationMarkerDrawableDirected by lazy { DynamicDrawable(AppCompatResources.getDrawable(this, R.drawable.locationmark_directed)!!) }
    private val locationMarker by lazy { PositionMarker(map) }
    private val locationHandler by lazy { LocationListener { onLocationReceived(it, false) } }

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
                map.invalidate()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
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
        osmconfig.osmdroidTileCache = File(cacheDir.absoluteFile, "osmdroid")
        osmconfig.userAgentValue = "echtzeyt/${application.packageName}"
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
    }

    @Suppress("SameParameterValue")
    private fun setWindowFlag(bits: Int, on: Boolean) {
        val params = window.attributes
        params.flags = if (on) { params.flags or bits } else { params.flags and bits.inv() }
        window.attributes = params
    }

    private fun initVariables() {
        updateMarkerZoomConstants()

        // this is sketchy, we are using the same drawable for all markers
        // it significantly improves performance but i am not sure if this is supposed to work or just works by accident
        // TODO: check if this is supposed to work
        drawableMarker.anchorH = Marker.ANCHOR_CENTER
        drawableMarker.anchorV = Marker.ANCHOR_BOTTOM
        drawableMarker.scale = 1.0

        drawableMarkerSelected.anchorH = Marker.ANCHOR_CENTER
        drawableMarkerSelected.anchorV = Marker.ANCHOR_BOTTOM
        drawableMarkerSelected.scale = 1.0

        drawableMarkerShadow?.anchorH = Marker.ANCHOR_CENTER
        drawableMarkerShadow?.anchorV = Marker.ANCHOR_BOTTOM
        drawableMarkerShadow?.scale = 1.0

        locationMarker.icon = locationMarkerDrawable
        locationMarkerDrawable.anchorH = Marker.ANCHOR_CENTER
        locationMarkerDrawable.anchorV = Marker.ANCHOR_CENTER
        locationMarkerDrawable.scale = 1.0

        locationMarkerDrawableDirected.anchorH = Marker.ANCHOR_CENTER
        locationMarkerDrawableDirected.anchorV = Marker.ANCHOR_CENTER
        locationMarkerDrawableDirected.scale = 1.0

        val stationStart = intent.getSerializableExtraCompat<LocatableStation>(ActivityResultSerializable.INPUT_DATA)
        if (stationStart != null) { selectStation(stationStart) }
    }

    private fun initSettings() {

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
        val nightMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        map.overlayManager.tilesOverlay.setColorFilter(if (nightMode) { colorFilterDark } else { colorFilterLight })

        map.overlays.add(locationMarker)

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

        findViewById<ImageButton>(R.id.btnMapBack).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btnMapLocate).setOnClickListener { gotoCurrentLocation() }
        findViewById<ImageButton>(R.id.btnClosePullup).setOnClickListener { pullup.hidePullup() }
        findViewById<ImageButton>(R.id.btnPullupStationConfirm).setOnClickListener { finishResult() }
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

    private fun updateWindowInsets() {
        val safeTop = ViewCompat.getRootWindowInsets(window.decorView)?.displayCutout?.safeInsetTop ?: 0
        println("MADDIN101: inset $safeTop")
        if (safeTop <= 0) { return }
        findViewById<View>(R.id.fillerCutout).updateLayoutParams{ height = safeTop }
    }

    @SuppressLint("MissingPermission")
    private fun initResourceIntensiveHandlers() {
        locationClient.requestLocationUpdates(locationProvider, 500L, 5.0f, locationHandler)

        if (sensorAcc != null && sensorMag != null) {
            sensorManager?.registerListener(orientationHandler, sensorAcc, sensorSampling)
            sensorManager?.registerListener(orientationHandler, sensorMag, sensorSampling)
            locationMarker.icon = locationMarkerDrawableDirected
        } else {
            locationMarker.icon = locationMarkerDrawable
        }
    }

    @SuppressLint("MissingPermission")
    private fun removeResourceIntensiveHandlers() {
        locationClient.removeUpdates(locationHandler)
        if (sensorAcc != null) { sensorManager?.unregisterListener(orientationHandler, sensorAcc) }
        if (sensorMag != null) { sensorManager?.unregisterListener(orientationHandler, sensorMag) }
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

    override fun onStop() {
        removeResourceIntensiveHandlers()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        updateLocation(force=false, goto=false)
    }

    private fun updateMarkerZoom() {
        val zoomLevel = map.zoomLevelDouble
        if ((zoomLevel - zoomLastUpdateMarkers).absoluteValue < zoomDeltaNoticeable) { return }
        zoomLastUpdateMarkers = zoomLevel

        val scale = scaleMin + (getMarkerZoomValue(zoomLevel) - mScaleOffsetY) * mScaleFactorY
        if ((1-scale/scaleLastUpdateMarkers).absoluteValue < scaleDeltaNoticeable) { return }
        scaleLastUpdateMarkers = scale

        drawableMarker.scale = scale
        drawableMarkerSelected.scale = scale
        drawableMarkerShadow?.scale = scale
        locationMarkerDrawable.scale = scale
        locationMarkerDrawableDirected.scale = scale

        updateMarkerAlphas()
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
        val center = LocationLatLon(areaCenter.latitude, areaCenter.longitude)
        val width = 2 * currentLocationArea.longitudeSpanWithDateLine
        val height = 2 * currentLocationArea.latitudeSpan
        val area = LocationAreaRect(center, width, height)
        val stations = transportLocateStationAPI.locateStations(area)

        if (timeNow < lastLocateUpdate) { return }
        lastLocateUpdate = timeNow

        Handler(Looper.getMainLooper()).post {
            map.overlays.removeAll { overlay -> overlay is StopMarker }
            for (station in stations) {
                if (station !is LocatableStation) { continue }

                val marker = StopMarker(map, station)
                marker.icon = drawableMarker
                marker.setIcon(drawableMarkerSelected, StopMarker.FLAG_SELECTED)
                marker.setOnMarkerClickListener { _, _ -> selectStation(station); true }
                stationSelected?.let { marker.selectAndDeselectOthers(it) }
                map.overlays.add(marker)

                if (stations.size > 50) { continue }
                if (drawableMarkerShadow == null) { continue }
                val shadowMarker = StopMarker(map, station)
                shadowMarker.icon = drawableMarkerShadow
                shadowMarker.setOnMarkerClickListener { _, _ -> true }
                map.overlays.add(0, shadowMarker)
            }

            map.invalidate()
        }

        if (nextLocateUpdate == lastNextLocateUpdate) { nextLocateUpdate = -1L }
    }

    fun selectStation(station: LocatableStation) {
        stationSelected = station
        txtStation.text = station.name
        if (!pullup.isVisible()) { pullup.showPullup() }

        for (marker in map.overlays) {
            if (marker !is StopMarker) { continue }
            marker.selectAndDeselectOthers(station)
        }
        map.invalidate()
    }

    private fun getStartLatitude() : Double {
        return stationSelected?.location?.lat ?: resources.getFloatValue(R.dimen.map_lat_start).toDouble()
    }

    private fun getStartLongitude() : Double {
        return stationSelected?.location?.lon ?: resources.getFloatValue(R.dimen.map_lon_start).toDouble()
    }

    private fun getStartZoom() : Double {
        var zoom = zoomDefault
        if (stationSelected != null) { zoom = zoomStation }
        return zoom
    }

    private fun finishResult() {
        val result = Intent()
        result.putExtra(ActivityResultSerializable.OUTPUT_DATA, stationSelected)
        setResult(RESULT_OK, result)
        finish()
    }

    private fun askForLocationPermissions() {
        locationPermissionRequest.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    private fun gotoCurrentLocation() {
        val locCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val locFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (locCoarse || locFine) { updateLocation(force=true, goto=true); return }
        askForLocationPermissions()
    }

    @SuppressLint("MissingPermission")
    private fun updateLocation(force: Boolean, goto: Boolean) {
        if (!LocationManagerCompat.isLocationEnabled(locationClient)) {
            onLocationReceived(null, false)
            if (force) { askForLocationTurnedOn() }
            return
        }

        if (force) {
            LocationManagerCompat.getCurrentLocation(locationClient, locationProvider, null, ContextCompat.getMainExecutor(this)) { onLocationReceived(it, goto) }
            return
        }

        onLocationReceived(locationClient.getLastKnownLocation(locationProvider), goto)
    }

    private fun onLocationReceived(location: Location?, goto: Boolean) {
        if (location == null) {
            locationMarker.setVisible(false)
            return
        }

        val position = GeoPoint(location.latitude, location.longitude)
        locationMarker.position = position
        locationMarker.setVisible(true)
        updateMarkerAlphas()
        map.invalidate()

        if (goto) {
            if (map.boundingBox.contains(position)) {
                map.controller.animateTo(position, zoomDefault, 700L)
            } else {
                Toast.makeText(this, "Location outside permitted area", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun askForLocationTurnedOn() {
        // thanks to https://stackoverflow.com/questions/43138788/ask-user-to-turn-on-location for parts of this code
        AlertDialog.Builder(this, R.style.AlertDialogTheme)
            .setTitle(R.string.mapLocationEnableTitle)
            .setMessage(R.string.mapLocationEnableText)
            .setPositiveButton(R.string.mapLocationEnableDone) { _, _ -> updateLocation(force=false, goto=true) }
            .setNeutralButton(R.string.mapLocationEnableSettings) { _, _ -> startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
            .setNegativeButton(R.string.mapLocationEnableCancel) { dialog, _ -> dialog.cancel() }
            .show()
    }

    private fun updateMarkerAlphas() {
        // this will be called when the map may be different, to make sure that markers will get transparent
        // if the location marker is behind them -> stop markers will still be in front but slightly transparent
        /*for (marker in map.overlays) {
            if (marker !is StopMarker) { continue }
            marker.alpha = 1f
            if (!marker.bounds.contains(locationMarker.bounds.centerLatitude, locationMarker.bounds.centerLongitude)) { continue }
            marker.alpha = 0.3f
        }*/
        // TODO: implement this
    }
}