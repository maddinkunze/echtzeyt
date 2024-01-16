package com.maddin.echtzeyt

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.maddin.echtzeyt.fragments.NamedFragment
import com.maddin.echtzeyt.fragments.echtzeyt.RealtimeFragment
import com.maddin.echtzeyt.fragments.echtzeyt.TripsFragment
import com.maddin.echtzeyt.fragments.settings.AboutSettingsFragment
import com.maddin.echtzeyt.fragments.settings.GeneralSettingsFragment
import com.maddin.echtzeyt.fragments.settings.MapSettingsFragment
import com.maddin.echtzeyt.fragments.settings.RealtimeSettingsFragment
import com.maddin.echtzeyt.fragments.settings.TripSettingsFragment
import com.maddin.echtzeyt.randomcode.LazyMutable
import com.maddin.transportapi.LocationStationAPI
import com.maddin.transportapi.RealtimeAPI
import com.maddin.transportapi.SearchStationAPI
import com.maddin.transportapi.StationAPI

var ECHTZEYT_CONFIGURATION = EchtzeytConfiguration()
const val ECHTZEYT_LOG_TAG = "Echtzeyt.LOG"

@Suppress("MemberVisibilityCanBePrivate")
class EchtzeytConfiguration {
    constructor()
    constructor(api: Any?) {
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

    var tripsStationAPI: StationAPI? = null
    var tripSupport = false
    var tripFragment by LazyMutable { if (tripSupport || true) TripsFragment::class.java else null }
    var tripSettingsFragment by LazyMutable { if (tripSupport) TripSettingsFragment::class.java else null }

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

    var isLoaded = false
        private set

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