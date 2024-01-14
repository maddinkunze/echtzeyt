package com.maddin.echtzeyt

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.maddin.echtzeyt.fragments.settings.GeneralSettingsFragment
import com.maddin.echtzeyt.fragments.settings.RealtimeSettingsFragment
import com.maddin.echtzeyt.randomcode.LazyMutable
import com.maddin.transportapi.LocationStationAPI
import com.maddin.transportapi.RealtimeAPI
import com.maddin.transportapi.SearchStationAPI
import com.maddin.transportapi.StationAPI

var ECHTZEYT_CONFIGURATION = EchtzeytConfiguration()
val ECHTZEYT_LOG_TAG = "Echtzeyt.LOG"

open class EchtzeytConfiguration {
    constructor()
    constructor(api: Any?) {
        load(api)
    }

    var activityMain: Class<out EchtzeytActivity> by LazyMutable { EchtzeytActivity::class.java }
    var activitySettings: Class<out SettingsActivity> by LazyMutable { SettingsActivity::class.java }
    var activityMap: Class<out MapActivity> by LazyMutable { MapActivity::class.java }

    var generalSettingsFragment: Fragment by LazyMutable { GeneralSettingsFragment() }
    private lateinit var mPreferences: SharedPreferences

    var realtimeStationAPI: SearchStationAPI? = null
    var realtimeRealtimeAPI: RealtimeAPI? = null
    var realtimeSupport = false
    var realtimeSettingsFragment: Fragment? by LazyMutable { if (!realtimeSupport) { return@LazyMutable null }; RealtimeSettingsFragment() }

    var widgetRealtimeStationAPI: SearchStationAPI? = null
    var widgetRealtimeRealtimeAPI: RealtimeAPI? = null
    var widgetRealtimeSupport = false
    var widgetRealtimeClass by LazyMutable { if (!widgetRealtimeSupport) { return@LazyMutable null }; EchtzeytWidget::class.java }

    var mapsStationAPI: LocationStationAPI? = null
    var mapsSupportLocateStations = false

    var tripsStationAPI: StationAPI? = null

    var isLoaded = false
        protected set

    fun load(api: Any?) {
        isLoaded = true

        realtimeStationAPI = api as? SearchStationAPI?
        realtimeRealtimeAPI = api as? RealtimeAPI?
        realtimeSupport = (realtimeStationAPI != null) && (realtimeRealtimeAPI != null)

        widgetRealtimeStationAPI = api as? SearchStationAPI?
        widgetRealtimeRealtimeAPI = api as? RealtimeAPI?
        widgetRealtimeSupport = (widgetRealtimeStationAPI != null) && (widgetRealtimeRealtimeAPI != null)

        mapsStationAPI = api as? LocationStationAPI?
        mapsSupportLocateStations = (mapsStationAPI != null)
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
}