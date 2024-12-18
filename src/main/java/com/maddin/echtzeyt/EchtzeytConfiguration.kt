package com.maddin.echtzeyt

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.os.ConfigurationCompat
import androidx.fragment.app.Fragment
import com.maddin.echtzeyt.activities.EchtzeytActivity
import com.maddin.echtzeyt.activities.EchtzeytWidget
import com.maddin.echtzeyt.activities.MapActivity
import com.maddin.echtzeyt.activities.SettingsActivity
import com.maddin.echtzeyt.components.DefaultMOTTypeResolver
import com.maddin.echtzeyt.components.MOTTypeResolver
import com.maddin.echtzeyt.fragments.NamedFragment
import com.maddin.echtzeyt.fragments.echtzeyt.RealtimeFragment
import com.maddin.echtzeyt.fragments.echtzeyt.TripsFragment
import com.maddin.echtzeyt.fragments.settings.AboutSettingsFragment
import com.maddin.echtzeyt.fragments.settings.GeneralSettingsFragment
import com.maddin.echtzeyt.fragments.settings.MapSettingsFragment
import com.maddin.echtzeyt.fragments.settings.RealtimeSettingsFragment
import com.maddin.echtzeyt.fragments.settings.TripSettingsFragment
import com.maddin.echtzeyt.randomcode.LazyMutable
import com.maddin.echtzeyt.randomcode.SafePOI
import com.maddin.transportapi.components.POI
import com.maddin.transportapi.endpoints.RealtimeAPI
import com.maddin.transportapi.endpoints.LocatePOIAPI
import com.maddin.transportapi.endpoints.POIAPI
import com.maddin.transportapi.endpoints.SearchPOIAPI
import com.maddin.transportapi.endpoints.TripSearchAPI
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

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

    var realtimeStationAPI: SearchPOIAPI? = null
    var realtimeRealtimeAPI: RealtimeAPI? = null
    var realtimeSupport = false
    var realtimeFragment by LazyMutable { if (realtimeSupport) RealtimeFragment::class.java else null }
    var realtimeSettingsFragment by LazyMutable { if (realtimeSupport) RealtimeSettingsFragment::class.java else null }

    var widgetRealtimeStationAPI: SearchPOIAPI? = null
    var widgetRealtimeRealtimeAPI: RealtimeAPI? = null
    var widgetRealtimeSupport = false
    var widgetRealtimeClass by LazyMutable { if (!widgetRealtimeSupport) { return@LazyMutable null }; EchtzeytWidget::class.java }

    var mapsStationAPI: LocatePOIAPI? = null
    var mapsSupport = true
    var mapsSupportLocateStations = false
    var mapsSettingsFragment by LazyMutable { if (mapsSupport) MapSettingsFragment::class.java else null }

    var tripsStationAPI: SearchPOIAPI? = null
    var tripsTripSearchAPI: TripSearchAPI? = null
    var tripSupport = false
    var tripFragment by LazyMutable { if (tripSupport) TripsFragment::class.java else null }
    var tripSettingsFragment by LazyMutable { if (tripSupport) TripSettingsFragment::class.java else null }

    var pullupStationAPI: POIAPI? = null

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

    var motTypeResolver: MOTTypeResolver = DefaultMOTTypeResolver()
    val onFavoriteStationsChangedListeners = mutableListOf<() -> Unit>()

    var osmdroidUserAgent = getUserAgent(null)
    lateinit var application: Application  // it is bad practice to access this variable directly, but it may be needed in some very special cases -> public getter
        private set

    var prefRealtimeSaveStation = "saveContent"
    var prefRealtimeLastStation = "station"
    var prefFavoriteStations = "stations"
    var prefRealtimeUseIcons = "realtimeIcons"
    var prefRealtimeIconsSameWidth = "realtimeIconsSameWidth"
    var prefRealtimeUpdateEvery = "updateEvery"
    var prefRealtimeTimeAfter = "realtimeTimeAfter"
    var prefRealtimeTimeAfterPast = "realtimeTimeAfterPast"
    var prefRealtimeSlowerUpdates = "realtimeSlowerUpdates"
    var prefRealtimeHideCancelled = "realtimeHideCancelled"
    var prefRealtimeNegativeTimes = "realtimeNegativeTimes"

    var confAllowSlowerUpdated = true

    var locale: Locale? = null

    var LOG_TAG = "Echtzeyt.LOG"

    var isLoaded = false
        private set

    fun load(api: Any?) {
        isLoaded = true

        realtimeStationAPI = api as? SearchPOIAPI?
        realtimeRealtimeAPI = api as? RealtimeAPI?
        realtimeSupport = (realtimeStationAPI != null) && (realtimeRealtimeAPI != null)

        tripsStationAPI = api as? SearchPOIAPI?
        tripsTripSearchAPI = api as? TripSearchAPI?
        tripSupport = (tripsStationAPI != null) && (tripsTripSearchAPI != null)

        widgetRealtimeStationAPI = api as? SearchPOIAPI?
        widgetRealtimeRealtimeAPI = api as? RealtimeAPI?
        widgetRealtimeSupport = (widgetRealtimeStationAPI != null) && (widgetRealtimeRealtimeAPI != null)

        mapsStationAPI = api as? LocatePOIAPI?
        mapsSupportLocateStations = (mapsStationAPI != null)

        pullupStationAPI = api as? POIAPI?
    }

    fun initApplication(application: Application) {
        this.application = application
        osmdroidUserAgent = getUserAgent(application)
        locale = ConfigurationCompat.getLocales(application.resources.configuration).get(0)
    }

    fun check() : Boolean {
        return isLoaded
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

    fun getFavoritePOIs(): Set<String> {
        return mPreferences.getStringSet(prefFavoriteStations, emptySet()) ?: emptySet()
    }

    fun addFavoritePOI(poi: POI) {
        val stations = getFavoritePOIs().toMutableSet()
        if (!stations.add(poi.name)) { return }
        mPreferences.edit {
            remove(prefFavoriteStations)
            apply()
            putStringSet(prefFavoriteStations, stations)
            apply()
        }
        for (listener in onFavoriteStationsChangedListeners) { listener() }
    }

    fun removeFavoritePOI(poi: POI) {
        val stations = getFavoritePOIs().toMutableSet()
        if (!stations.remove(poi.name)) { return }
        mPreferences.edit {
            remove(prefFavoriteStations)
            apply()
            putStringSet(prefFavoriteStations, stations)
            apply()
        }
        for (listener in onFavoriteStationsChangedListeners) { listener() }
    }

    fun toggleFavoritePOI(poi: POI) {
        if (isFavoritePOI(poi)) {
            removeFavoritePOI(poi)
        } else {
            addFavoritePOI(poi)
        }
    }

    fun isFavoritePOI(poi: POI): Boolean {
        return poi.name in getFavoritePOIs()
    }

    fun shouldSaveLastRealtimeStation(): Boolean {
        return mPreferences.getBoolean(prefRealtimeSaveStation, true)
    }

    fun setSaveLastRealtimePOI(shouldSave: Boolean) {
        mPreferences.edit { putBoolean(prefRealtimeSaveStation, shouldSave) }
    }

    fun getLastRealtimePOI(): SafePOI {
        if (!shouldSaveLastRealtimeStation()) { return SafePOI(null, null) }
        val search = mPreferences.getString(prefRealtimeLastStation, null)
        val poi = null
        return SafePOI(search, poi)
    }

    fun setCurrentRealtimePOI(safePOI: SafePOI) {
        if (!shouldSaveLastRealtimeStation()) { return }
        mPreferences.edit { putString(prefRealtimeLastStation, safePOI.poi?.name ?: "") }
    }

    fun useIconsInRealtimeView(): Boolean {
        return mPreferences.getBoolean(prefRealtimeUseIcons, true)
    }

    fun iconsRealtimeViewSameWidth(): Boolean {
        return mPreferences.getBoolean(prefRealtimeIconsSameWidth, false)
    }

    fun getRealtimeShowTimeAfter(): Int {
        return mPreferences.getInt(prefRealtimeTimeAfter, 300)
    }

    fun getRealtimeShowTimeAfterPast(): Int {
        return mPreferences.getInt(prefRealtimeTimeAfterPast, 300)
    }

    fun getRealtimeUpdateInterval(): Long {
        return mPreferences.getInt(prefRealtimeUpdateEvery, 5000).toLong()
    }

    fun shouldSlowDownRealtimeUpdates(): Boolean {
        return supportsSlowedDownRealtimeUpdates() && mPreferences.getBoolean(prefRealtimeSlowerUpdates, true)
    }

    fun supportsSlowedDownRealtimeUpdates(): Boolean {
        // slow down on future requests can only be enabled if the api supports past/future requests
        return confAllowSlowerUpdated && (realtimeRealtimeAPI?.supportsRealtimeFeature(RealtimeAPI.FEATURE_REALTIME_FUTURE or RealtimeAPI.FEATURE_REALTIME_FUTURE) == true)
    }

    fun shouldHideCancelledRealtimeConnections(): Boolean {
        return mPreferences.getBoolean(prefRealtimeHideCancelled, false)
    }

    fun shouldShowNegativeRealtimeWaitingTimes(): Boolean {
        return mPreferences.getBoolean(prefRealtimeNegativeTimes, false)
    }



    var strDBYesterday by LazyMutable { application.resources.getString(R.string.dddepDBYesterday) }
    var strYesterday by LazyMutable { application.resources.getString(R.string.dddepYesterday) }
    var strNow by LazyMutable { application.resources.getString(R.string.dddepNow) }
    var strTomorrow by LazyMutable { application.resources.getString(R.string.dddepTomorrow) }
    var strDATomorrow by LazyMutable { application.resources.getString(R.string.dddepDATomorrow) }

    var formatterTimeShort: DateTimeFormatter by LazyMutable { DateTimeFormatter.ofPattern("HH:mm") }
    var formatterTimeLong: DateTimeFormatter by LazyMutable { DateTimeFormatter.ofPattern("HH:mm:ss") }
    var formatterDateShort: DateTimeFormatter by LazyMutable { DateTimeFormatter.ofPattern("EEE dd.MM") }
    var formatterDateLong: DateTimeFormatter by LazyMutable { DateTimeFormatter.ofPattern("EEE dd.MM.yyyy") }

    fun formatDateTime(dateTime: LocalDateTime?, showSeconds: Boolean=false): String {
        if (dateTime == null) { return strNow }
        var dateF = formatDate(dateTime.toLocalDate())
        if (dateF.isNotBlank()) { dateF = "$dateF, " }
        val timeF = formatTime(dateTime.toLocalTime(), showSeconds)
        return dateF + timeF
    }

    fun formatDate(date: LocalDate): String {
        val dateNow = LocalDate.now()
        when (ChronoUnit.DAYS.between(dateNow, date)) {
            -2L -> if (strDBYesterday.isNotBlank()) return strDBYesterday
            -1L -> return strYesterday
            0L -> return ""
            1L -> return strTomorrow
            2L -> if (strDATomorrow.isNotBlank()) return strDATomorrow
        }
        if (date.year == dateNow.year) {
            return formatterDateShort.format(date)
        }
        return formatterDateLong.format(date)
    }

    fun formatTime(time: LocalTime, showSeconds: Boolean=true): String {
        return (if (showSeconds) formatterTimeLong else formatterTimeShort).format(time)
    }

    fun handleExceptions(context: Context, exceptions: List<Exception>) {

    }

    fun handleExceptions(context: Context) {

    }
}