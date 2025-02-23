package com.maddin.echtzeyt.fragments.echtzeyt

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import com.maddin.echtzeyt.ECHTZEYT_CONFIGURATION
import com.maddin.echtzeyt.R
import com.maddin.echtzeyt.activities.MapContractShowTrip
import com.maddin.echtzeyt.activities.MapResultContractSelectPOI
import com.maddin.echtzeyt.components.AnimatableImageButton
import com.maddin.echtzeyt.components.DepartureDropdown
import com.maddin.echtzeyt.components.DropdownButton
import com.maddin.echtzeyt.components.LazyPullup
import com.maddin.echtzeyt.components.StationPullup
import com.maddin.echtzeyt.components.POISearchbar
import com.maddin.echtzeyt.components.TripInfo
import com.maddin.echtzeyt.components.TripPullup
import com.maddin.echtzeyt.components.TripsScrollView
import com.maddin.echtzeyt.fragments.EchtzeytPullupFragment
import com.maddin.echtzeyt.randomcode.ActivityScrollable
import com.maddin.echtzeyt.randomcode.ContextShowConnectionPullup
import com.maddin.echtzeyt.randomcode.ContextShowStationPullup
import com.maddin.echtzeyt.randomcode.ContextShowTripPullup
import com.maddin.echtzeyt.randomcode.LazyView
import com.maddin.echtzeyt.randomcode.applyRandomId
import com.maddin.transportapi.components.Connection
import com.maddin.transportapi.components.POI
import com.maddin.transportapi.components.Station
import com.maddin.transportapi.components.Trip
import com.maddin.transportapi.endpoints.TripSearchAPI
import com.maddin.transportapi.endpoints.TripSearchRequestImpl
import kotlin.concurrent.thread

class TripsFragment : EchtzeytPullupFragment(R.layout.fragment_trips),
    ContextShowStationPullup, ContextShowConnectionPullup, ContextShowTripPullup {
    private val mBtnReverse: ImageButton by LazyView(R.id.btnReorder)
    private val mEdtSearchFrom: POISearchbar by LazyView(R.id.edtSearchFrom)
    private val mBtnMapFrom: ImageButton by LazyView(R.id.btnMapFrom)
    private val mEdtSearchTo: POISearchbar by LazyView(R.id.edtSearchTo)
    private val mBtnMapTo: ImageButton by LazyView(R.id.btnMapTo)
    private val mBtnDeparture: DropdownButton by LazyView(R.id.trip_btnDeparture)
    private val layoutDeparture: DepartureDropdown by LazyView(R.id.trip_layoutFilterDeparture)
    private val layoutSearches by lazy { mEdtSearchFrom.parent as ViewGroup }
    private val layoutTrips: TripsScrollView by LazyView(R.id.trip_trips)
    private var mEdtInvokedMap: POISearchbar? = null
    private var mKeyboardVisible = false

    private val layoutsFilter by lazy { listOf(layoutDeparture) }

    // Everything related to other activities (such as the settings or a station selection map)
    private val activityMapLauncherStation by lazy { registerForActivityResult(MapResultContractSelectPOI()) { if (it == null) { return@registerForActivityResult }; mEdtInvokedMap?.currentPOI = it; updateTrips() } }
    private val activityMapLauncherTrip by lazy { registerForActivityResult(MapContractShowTrip()) {} }

    val pullupTrip: TripPullup by LazyPullup(R.id.ft_pullupTrip, this)
    val pullupStation: StationPullup by LazyPullup(R.id.ft_pullupStation, this)

    protected val transportTripsAPI by lazy { ECHTZEYT_CONFIGURATION.tripsTripSearchAPI!! }

    private var shouldUpdateTrips = false
        @Synchronized set

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initVariables()
        initListeners()
        initThreads()
    }

    private fun initVariables() {
        if (ECHTZEYT_CONFIGURATION.mapsSupportLocateStations) { activityMapLauncherStation }
        if (ECHTZEYT_CONFIGURATION.mapsSupportShowTrip) { activityMapLauncherTrip }
        mEdtSearchFrom.searchStationAPI = ECHTZEYT_CONFIGURATION.tripsStationAPI!!
        mEdtSearchTo.searchStationAPI = ECHTZEYT_CONFIGURATION.tripsStationAPI!!

        addStationSearchIfNeeded(false)

        // TODO: remove after testing
        //mEdtSearchFrom.setText("Reichsstr")
        //mEdtSearchTo.setText("Hauptbahnhof")
    }

    private fun initListeners() {
        // Listener for whether the ime (i.e. keyboard inset) is visible / has been hidden
        // -> clear focus from text inputs on keyboard hide
        safeView.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) { return@addOnLayoutChangeListener }
            if (!v.isAttachedToWindow) { return@addOnLayoutChangeListener }
            val keyboardVisible = WindowInsetsCompat.toWindowInsetsCompat(v.rootWindowInsets, v).isVisible(WindowInsetsCompat.Type.ime())
            if (mKeyboardVisible && !keyboardVisible) { clearFocus() }
            mKeyboardVisible = keyboardVisible
        }

        // set listener to reverse the order of stations
        mBtnReverse.setOnClickListener { reverseStations() }

        // add station search listeners for the existing input fields "from" and "to"
        addStationSearchListeners(mEdtSearchFrom, mBtnMapFrom)
        addStationSearchListeners(mEdtSearchTo, mBtnMapTo)

        // Set listeners for departure filter
        layoutDeparture.setListeners(mBtnDeparture, layoutsFilter)
        layoutDeparture.onChangedListeners.add { updateTrips() }

        // disable viewpager scroll when someone is interacting with the trip scroll list
        registerParentScrollDisablingView(layoutTrips)
    }

    private fun initThreads() {
        thread(start=true, isDaemon=true) {
            while (true) {
                if (!isInForeground.block(15000)) { continue }

                for (child in layoutSearches.children) {
                    if (child !is POISearchbar) { continue }
                    child.ntUpdateSearch()
                }
                Thread.sleep(50)
            }
        }

        thread(start=true, isDaemon=true) {
            while (true) {
                if (!isInForeground.block(15000)) { continue }
                if (!shouldUpdateTrips) { continue }

                shouldUpdateTrips = false
                val stops = layoutSearches.children.mapNotNull { (it as? POISearchbar)?.currentPOI }.toList()
                if (stops.size < 2) {
                    activity?.runOnUiThread {
                        layoutTrips.removeAllTrips()
                    }
                    continue
                }
                val request = TripSearchRequestImpl(waypoints=stops)
                if (apiSupportsDifferentTimeThanNow()) {
                    val diffTime = !layoutDeparture.isNow()
                    val diffMode = layoutDeparture.mode != DepartureDropdown.DepartureMode.DEPARTURE
                    if (diffTime || diffMode) {
                        request.time = layoutDeparture.dateTime
                        request.timeSpec = layoutDeparture.mode.timeSpec
                    }
                }
                val response = transportTripsAPI.searchTrips(request)
                val trips = response.trips

                activity?.runOnUiThread {
                    layoutTrips.removeAllTrips()
                    for (item in trips) {
                        layoutTrips.addTrip(TripInfo(safeContext).apply {
                            trip = item
                            setOnClickListener { trip, connection -> showTripPullup(trip, connection) }
                        })
                    }
                    layoutTrips.scaleToContents()
                }
            }
        }
    }

    private fun clearFocus() {
        for (child in layoutSearches.children) {
            child.clearFocus()
        }
    }

    private fun addStationSearchListeners(edtSearch: POISearchbar, btnMap: ImageButton) {
        edtSearch.applyRandomId()
        edtSearch.onItemSelectedListeners.add {
            addStationSearchIfNeeded(true)
        }
        edtSearch.onFocusChangeListeners.add {focused ->
            if (focused) { return@add }
            deleteStationSearchIfNeeded()
        }
        if (ECHTZEYT_CONFIGURATION.mapsSupportLocateStations) {
            btnMap.setOnClickListener {
                mEdtInvokedMap = edtSearch
                activityMapLauncherStation.launch(edtSearch.currentPOI)
            }
        }
        edtSearch.onItemSelectedListeners.add { shouldUpdateTrips = true }
    }

    private fun countStationSearches(): Int {
        return layoutSearches.children.count { view -> view is POISearchbar }
    }

    private fun canAddStationSearch() : Boolean {
        val waypointCount = ECHTZEYT_CONFIGURATION.tripsTripSearchAPI!!.getSearchTripWaypointCount()
        if (waypointCount < 0) { return true }
        return waypointCount > countStationSearches()
    }

    private fun shouldAddStationSearch() : Boolean {
        for (child in layoutSearches.children) {
            if (child !is POISearchbar) { continue }
            if (child == mEdtSearchFrom) { continue }
            if (child == mEdtSearchTo) { continue }
            if (child.currentPOI == null) { return false }
        }
        return true
    }

    private fun canDeleteStationSearch(): Int {
        if (countStationSearches() < 4) { return 0 }
        val emptyVias = layoutSearches.children.count {
            if (it !is POISearchbar) { return@count false }
            if (it == mEdtSearchFrom) { return@count false }
            if (it == mEdtSearchTo) { return@count false }
            if (it.isDeleted) { return@count false }

            return@count it.currentPOI == null
        }
        return emptyVias - 1
    }

    private fun deleteStationSearchIfNeeded(): Boolean {
        val viewsToDelete = canDeleteStationSearch()
        if (viewsToDelete < 1) { return false }

        var viewsDeleted = 0
        for (child in layoutSearches.children) {
            if (child !is POISearchbar) { continue }
            if (child == mEdtSearchFrom) { continue }
            if (child == mEdtSearchTo) { continue }
            if (child.isDeleted) { continue }
            if (child.currentPOI != null) { continue }

            child.post { child.removeFromParent(250) }
            viewsDeleted++
            if (viewsDeleted >= viewsToDelete) { break }
        }
        return viewsDeleted > 0
    }

    private fun addStationSearchIfNeeded(animate: Boolean) {
        if (!canAddStationSearch()) { return }
        if (!shouldAddStationSearch()) { return }

        val edtSearch = POISearchbar(ContextThemeWrapper(safeContext, R.style.Theme_Echtzeyt_TextView_StationSearch_Trips), null, 0)
        edtSearch.searchStationAPI = ECHTZEYT_CONFIGURATION.tripsStationAPI!!
        edtSearch.setIcon(R.drawable.ic_route_via)
        edtSearch.setHint(R.string.tripStationVia)

        val btnMap = AnimatableImageButton(ContextThemeWrapper(safeContext, R.style.Theme_Echtzeyt_Button_Image_Animatable), null, 0)
        btnMap.setImageResource(R.drawable.ic_map)
        addStationSearchListeners(edtSearch, btnMap)

        val btnRemove = AnimatableImageButton(ContextThemeWrapper(safeContext, R.style.Theme_Echtzeyt_Button_Image_Animatable), null, 0)
        btnRemove.setImageResource(R.drawable.ic_close)
        btnRemove.setOnClickListener {
            if (edtSearch.currentPOI == null) {
                deleteStationSearchIfNeeded() // automatically remove all unnecessary empty input fields
            } else if (canDeleteStationSearch() >= 0 && countStationSearches() > 3) {
                edtSearch.removeFromParent(250) // remove this specific view if it has content -> better animation
            } else {
                edtSearch.currentPOI = null // simply clear the content if we are not allowed to remove views right now
            }
        }

        edtSearch.addView(btnMap)
        edtSearch.addView(btnRemove)
        if (animate) { edtSearch.animateIntoExistence(250) }
        layoutSearches.addView(edtSearch, layoutSearches.childCount-1)
    }

    private fun reverseStations() {
        val stations = getStations().asReversed().iterator()
        for (child in layoutSearches.children) {
            if (child !is POISearchbar) { continue }
            if (child.currentPOI == null) { continue }
            if (!stations.hasNext()) { return }
            child.currentPOI = stations.next()
        }
        updateTrips()
    }

    private fun getStations(): List<POI> {
        return layoutSearches.children.mapNotNull { (it as? POISearchbar)?.currentPOI }.toList()
    }

    private fun updateTrips() {
        shouldUpdateTrips = true
    }

    override fun onResume() {
        super.onResume()
        (activity as? ActivityScrollable)?.enableScroll()
    }

    override fun showStationPullup(station: Station) {

    }

    override fun showConnectionPullup(connection: Connection) {

    }

    override fun showTripPullup(trip: Trip, connection: Connection?) {
        pullupTrip.setMapLauncher(activityMapLauncherTrip)
        pullupTrip.setTrip(trip, connection, true)
    }

    protected fun apiSupportsDifferentTimeThanNow(): Boolean {
        return transportTripsAPI.supportsSearchTripFeature(TripSearchAPI.FEATURE_SEARCH_TRIP_CUSTOM_ARRIVAL or TripSearchAPI.FEATURE_SEARCH_TRIP_CUSTOM_DEPARTURE)
    }
}