package com.maddin.echtzeyt

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.Icon
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import com.maddin.echtzeyt.activities.EchtzeytActivity
import com.maddin.echtzeyt.activities.EchtzeytWidget
import com.maddin.echtzeyt.activities.MapActivity
import com.maddin.echtzeyt.activities.SettingsActivity
import com.maddin.echtzeyt.components.DefaultVehicleTypeResolver
import com.maddin.echtzeyt.components.VehicleTypeResolver
import com.maddin.echtzeyt.fragments.NamedFragment
import com.maddin.echtzeyt.fragments.echtzeyt.RealtimeFragment
import com.maddin.echtzeyt.fragments.echtzeyt.TripsFragment
import com.maddin.echtzeyt.fragments.settings.AboutSettingsFragment
import com.maddin.echtzeyt.fragments.settings.GeneralSettingsFragment
import com.maddin.echtzeyt.fragments.settings.MapSettingsFragment
import com.maddin.echtzeyt.fragments.settings.RealtimeSettingsFragment
import com.maddin.echtzeyt.fragments.settings.TripSettingsFragment
import com.maddin.echtzeyt.randomcode.IconLineDrawable
import com.maddin.echtzeyt.randomcode.LazyMutable
import com.maddin.echtzeyt.randomcode.LineDrawable
import com.maddin.transportapi.LocationStationAPI
import com.maddin.transportapi.RealtimeAPI
import com.maddin.transportapi.SearchStationAPI
import com.maddin.transportapi.Station
import com.maddin.transportapi.StationAPI
import com.maddin.transportapi.VehicleTypes

var ECHTZEYT_CONFIGURATION = EchtzeytConfiguration()

@Suppress("MemberVisibilityCanBePrivate", "PropertyName", "unused")
class EchtzeytConfiguration {
    constructor()
    constructor(api: Any?) {
        load(api)
    }
    constructor(application: Application, api: Any?) {
        initApplication(application)
        load(api)
    }

    var activityMain: Class<out EchtzeytActivity> by LazyMutable { EchtzeytActivity::class.java }
    var activitySettings: Class<out SettingsActivity> by LazyMutable { SettingsActivity::class.java }
    var activityMap: Class<out MapActivity> by LazyMutable { MapActivity::class.java }

    var generalSettingsFragment: Class<out Fragment> by LazyMutable { GeneralSettingsFragment::class.java }
    var aboutSettingsFragment: Class<out Fragment> by LazyMutable { AboutSettingsFragment::class.java }

    private lateinit var mPreferences: SharedPreferences

    var realtimeStationAPI: SearchStationAPI? = null
    var realtimeRealtimeAPI: RealtimeAPI? = null
    var realtimeSupport = false
    var realtimeFragment by LazyMutable { if (realtimeSupport) RealtimeFragment::class.java else null }
    var realtimeSettingsFragment by LazyMutable { if (realtimeSupport) RealtimeSettingsFragment::class.java else null }

    var widgetRealtimeStationAPI: SearchStationAPI? = null
    var widgetRealtimeRealtimeAPI: RealtimeAPI? = null
    var widgetRealtimeSupport = false
    var widgetRealtimeClass by LazyMutable { if (!widgetRealtimeSupport) { return@LazyMutable null }; EchtzeytWidget::class.java }

    var mapsStationAPI: LocationStationAPI? = null
    var mapsSupport = true
    var mapsSupportLocateStations = false
    var mapsSettingsFragment by LazyMutable { if (mapsSupport) MapSettingsFragment::class.java else null }

    var tripsStationAPI: SearchStationAPI? = null
    var tripSupport = false
    var tripFragment by LazyMutable { if (tripSupport) TripsFragment::class.java else null }
    var tripSettingsFragment by LazyMutable { if (tripSupport) TripSettingsFragment::class.java else null }

    var pullupStationAPI: StationAPI? = null

    var customFragmentsView = mutableListOf<NamedFragment>()
    var customFragmentsSettings = mutableListOf<NamedFragment>()

    val fragmentsView by lazy { listOfNotNull(
        realtimeFragment?.let { NamedFragment(R.string.menuSettingsRealtime, it) },
        tripFragment?.let { NamedFragment(R.string.menuSettingsTrips, it) }
    ).plus(customFragmentsView) }

    val fragmentsSettings by lazy { listOfNotNull(
        NamedFragment(R.string.menuSettingsGeneral, generalSettingsFragment),
        realtimeSettingsFragment?.let { NamedFragment(R.string.menuSettingsRealtime, it) },
        tripSettingsFragment?.let { NamedFragment(R.string.menuSettingsTrips, it) },
        mapsSettingsFragment?.let { NamedFragment(R.string.menuSettingsMap, it) }
    ).plus(customFragmentsSettings)
     .plus(NamedFragment(R.string.menuSettingsAbout, aboutSettingsFragment)) } // the about settings fragment should always come last

    var vehicleTypeResolver: VehicleTypeResolver = DefaultVehicleTypeResolver()
    val onFavoriteStationsChangedListeners = mutableListOf<() -> Unit>()

    var osmdroidUserAgent = getUserAgent(null)
    lateinit var application: Application  // it is bad practice to access this variable, but it may be needed in some very special cases
        private set

    var LOG_TAG = "Echtzeyt.LOG"

    var isLoaded = false
        private set

    fun load(api: Any?) {
        isLoaded = true

        realtimeStationAPI = api as? SearchStationAPI?
        realtimeRealtimeAPI = api as? RealtimeAPI?
        realtimeSupport = (realtimeStationAPI != null) && (realtimeRealtimeAPI != null)

        tripsStationAPI = api as? SearchStationAPI?
        tripSupport = (tripsStationAPI != null)

        widgetRealtimeStationAPI = api as? SearchStationAPI?
        widgetRealtimeRealtimeAPI = api as? RealtimeAPI?
        widgetRealtimeSupport = (widgetRealtimeStationAPI != null) && (widgetRealtimeRealtimeAPI != null)

        mapsStationAPI = api as? LocationStationAPI?
        mapsSupportLocateStations = (mapsStationAPI != null)

        pullupStationAPI = api as? StationAPI?
    }

    fun initApplication(application: Application) {
        this.application = application
        osmdroidUserAgent = getUserAgent(application)
        addDefaultVehicleTypeBadges()
    }

    fun addDefaultVehicleTypeBadges() {
        (vehicleTypeResolver as? DefaultVehicleTypeResolver)?.let {
            it.defaultDrawable = LineDrawable(application, R.color.lineBackgroundDefault, R.color.lineForegroundDefault)
            it.add(VehicleTypes.BUS, drawable=IconLineDrawable(application, R.color.lineBackgroundBus, R.color.lineForegroundBus, R.drawable.ic_bus))
            it.add(VehicleTypes.TRAM, drawable=IconLineDrawable(application, R.color.lineBackgroundTram, R.color.lineForegroundTram, R.drawable.ic_tram).apply { iconSize = 0.8 })
            it.add(VehicleTypes.SUBWAY, drawable=LineDrawable(application, R.color.lineBackgroundSubway, R.color.lineForegroundSubway).apply { radius = 0.4 })
            it.add(VehicleTypes.TRAIN_SUBURBAN, drawable=IconLineDrawable(application, R.color.lineBackgroundSuburban, R.color.lineForegroundSuburban, R.drawable.ic_suburban).apply { radius = 0.5 })
            it.add(VehicleTypes.CABLECAR, drawable=IconLineDrawable(application, R.color.lineBackgroundCablecar, R.color.lineForegroundCablecar, R.drawable.ic_cablecar))
            it.add(VehicleTypes.TRAIN, drawable=IconLineDrawable(application, R.color.lineBackgroundTrain, R.color.lineForegroundTrain, R.drawable.ic_train))
            it.add(VehicleTypes.BUS_NIGHT, drawable=IconLineDrawable(application, R.color.lineBackgroundBusNight, R.color.lineForegroundBusNight, R.drawable.ic_bus))
            it.add(VehicleTypes.PLANE, drawable=IconLineDrawable(application, R.color.lineBackgroundPlane, R.color.lineForegroundPlane, R.drawable.ic_plane))
            it.add(VehicleTypes.HELICOPTER, drawable=IconLineDrawable(application, R.color.lineBackgroundHelicopter, R.color.lineForegroundHelicopter, R.drawable.ic_helicopter))
            it.add(VehicleTypes.BIKE, drawable=IconLineDrawable(application, R.color.lineBackgroundBike, R.color.lineForegroundBike, R.drawable.ic_bike))
            it.add(VehicleTypes.FERRY, drawable=IconLineDrawable(application, R.color.lineBackgroundFerry, R.color.lineForegroundFerry, R.drawable.ic_boat))
            it.add(VehicleTypes.ESCALATOR, drawable=IconLineDrawable(application, R.color.lineBackgroundEscalator, R.color.lineForegroundEscalator, R.drawable.ic_escalator))
            it.add(VehicleTypes.SCOOTER, drawable=IconLineDrawable(application, R.color.lineBackgroundScooter, R.color.lineForegroundScooter, R.drawable.ic_scooter))
            it.add(VehicleTypes.SEGWAY, drawable=IconLineDrawable(application, R.color.lineBackgroundSegway, R.color.lineForegroundSegway, R.drawable.ic_segway))
            it.add(VehicleTypes.WALK, drawable=IconLineDrawable(application, R.color.lineBackgroundWalk, R.color.lineForegroundWalk, R.drawable.ic_walk))
            it.add(VehicleTypes.WALK_RUN, drawable=IconLineDrawable(application, R.color.lineBackgroundWalkRun, R.color.lineForegroundWalkRun, R.drawable.ic_run))
            it.add(VehicleTypes.WALK_LONG, drawable=IconLineDrawable(application, R.color.lineBackgroundWalkLong, R.color.lineForegroundWalkLong, R.drawable.ic_hike))
            it.add(VehicleTypes.STAIRS, drawable=IconLineDrawable(application, R.color.lineBackgroundStairs, R.color.lineForegroundStairs, R.drawable.ic_stairs).apply { iconSize = 0.8 })
        }
    }

    fun check() : Boolean {
        if (!isLoaded) { return false }
        return true
    }

    fun preferences(context: Context): SharedPreferences {
        if (!::mPreferences.isInitialized) {
            mPreferences = context.getSharedPreferences(context.packageName, AppCompatActivity.MODE_PRIVATE)
        }
        return mPreferences
    }

    fun getUserAgent(context: Context?): String {
        val packageName = context?.packageName?:"unknown"
        var versionName = "unknown"
        try { versionName = context?.packageManager?.getPackageInfo(packageName, 0)?.versionName?:versionName } catch (_: Throwable) {}
        return "$packageName/$versionName (application) ${getLibraryUserAgent()}"
    }

    fun getLibraryUserAgent(): String {
        return "${BuildConfig.LIBRARY_PACKAGE_NAME}/${BuildConfig.LIBRARY_VERSION_NAME} (library)"
    }

    fun getFavoriteStations(): Set<String> {
        return mPreferences.getStringSet("savedStations", emptySet()) ?: emptySet()
    }

    fun addFavoriteStation(station: Station) {
        val stations = getFavoriteStations().toMutableSet()
        if (!stations.add(station.name)) { return }
        mPreferences.edit {
            remove("savedStations")
            apply()
            putStringSet("savedStations", stations)
            apply()
        }
        for (listener in onFavoriteStationsChangedListeners) { listener() }
    }

    fun removeFavoriteStation(station: Station) {
        val stations = getFavoriteStations().toMutableSet()
        if (!stations.remove(station.name)) { return }
        mPreferences.edit {
            remove("savedStations")
            apply()
            putStringSet("savedStations", stations)
            apply()
        }
        for (listener in onFavoriteStationsChangedListeners) { listener() }
    }

    fun toggleFavoriteStation(station: Station) {
        if (isFavoriteStation(station)) {
            removeFavoriteStation(station)
        } else {
            addFavoriteStation(station)
        }
    }

    fun isFavoriteStation(station: Station): Boolean {
        return station.name in getFavoriteStations()
    }

    fun shouldSaveLastRealtimeStation(): Boolean {
        return mPreferences.getBoolean("saveStation", true)
    }

    fun getLastRealtimeStationName(): String {
        if (!shouldSaveLastRealtimeStation()) { return "" }
        return mPreferences.getString("station", "") ?: ""
    }

    fun setCurrentRealtimeStation(station: Station?) {
        if (!shouldSaveLastRealtimeStation()) { return }
        mPreferences.edit { putString("station", station?.name ?: "") }
    }
}