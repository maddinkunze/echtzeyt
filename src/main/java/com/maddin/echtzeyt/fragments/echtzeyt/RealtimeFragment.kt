package com.maddin.echtzeyt.fragments.echtzeyt

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.maddin.echtzeyt.ECHTZEYT_CONFIGURATION
import com.maddin.echtzeyt.R
import com.maddin.echtzeyt.activities.MapResultContractSelectPOI
import com.maddin.echtzeyt.components.DepartureDropdown
import com.maddin.echtzeyt.components.DropdownButton
import com.maddin.echtzeyt.components.LazyPullup
import com.maddin.echtzeyt.components.PullupManager
import com.maddin.echtzeyt.components.RealtimeInfo
import com.maddin.echtzeyt.components.StationPullup
import com.maddin.echtzeyt.components.POISearchbar
import com.maddin.echtzeyt.fragments.EchtzeytForegroundFragment
import com.maddin.echtzeyt.fragments.EchtzeytPullupFragment
import com.maddin.echtzeyt.randomcode.ClassifiedException
import com.maddin.echtzeyt.randomcode.LazyView
import com.maddin.transportapi.components.POI
import com.maddin.transportapi.endpoints.RealtimeAPI
import com.maddin.transportapi.components.RealtimeConnection
import com.maddin.transportapi.endpoints.RealtimeRequestImpl
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.concurrent.thread
import kotlin.math.roundToLong

class RealtimeFragment : EchtzeytPullupFragment(R.layout.fragment_realtime) {
    private var shouldUpdateStationPullup = false
    private var nextUpdateConnections = 0L

    // Elements/Views
    private val edtSearch: POISearchbar by LazyView(R.id.edtSearch)
    private val btnMap: ImageButton by LazyView(R.id.btnMap)
    private val btnInfo: ImageButton by LazyView(R.id.btnInfo)
    private val btnBookmarks: ImageButton by LazyView(R.id.btnBookmarks)
    private val layoutBookmarks: View by LazyView(R.id.layoutBookmarks)
    private val btnDeparture: DropdownButton by LazyView(R.id.btnDeparture)
    private val layoutDeparture: DepartureDropdown by LazyView(R.id.layoutFilterDeparture)
    private val layoutConnections: LinearLayout by LazyView(R.id.realtime_layoutConnections)
    private val btnShowConnectionsNow: Button by LazyView(R.id.realtime_btnShowConnectionsNow)
    private val btnShowConnectionsTime: Button by LazyView(R.id.realtime_btnShowConnectionsFrom)
    private val pullupStation: StationPullup by LazyPullup(R.id.pullupStationInfo, this)

    private val layoutsFilter by lazy { listOf(layoutDeparture) }

    private var mKeyboardVisible = false

    // Bookmark variables
    private var bookmarksOpened = false
    private var pullupStationOpened = false

    protected val transportRealtimeAPI by lazy { ECHTZEYT_CONFIGURATION.realtimeRealtimeAPI!! }

    // Everything related to other activities (such as the settings or a station selection map)
    private val activityMapLauncher by lazy { registerForActivityResult(MapResultContractSelectPOI()) { if (it == null) { return@registerForActivityResult }; commitToStation(it) } }

    // Notifications and exceptions
    private var exceptions: MutableList<ClassifiedException> = mutableListOf()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initVariables()
        initSettings()
        initHandlers()
        initApp()
        initThreads()
    }

    override fun onResume() {
        updateConnectionsIn(100, true)
        super.onResume()
    }

    private fun initVariables() {
        // the launcher has to be registered before the activity has been started
        // by explicitly stating activityMapLauncher, we force lazy to initialize activityMapLauncher
        if (ECHTZEYT_CONFIGURATION.mapsSupportLocateStations) { activityMapLauncher }
        edtSearch.searchStationAPI = ECHTZEYT_CONFIGURATION.realtimeStationAPI!!

        pullupStation.manager = pullupManager

        toggleBookmarks(true)
    }
    private fun initSettings() {
        // Save last station?
        edtSearch.setText(ECHTZEYT_CONFIGURATION.getLastRealtimePOIName())
    }
    private fun initHandlers() {
        // When the search has selected a new station, update connections and pullup etc
        edtSearch.onItemSelectedListeners.add { commitToStation(edtSearch.currentPOI, updateSearch=false) }

        // Set listeners for departure filter
        if (apiSupportsDifferentTimeThanNow()) {
            layoutDeparture.setListeners(btnDeparture, layoutsFilter)
            layoutDeparture.onChangedListeners.add { updateConnections() }
        } else {
            layoutDeparture.visibility = View.GONE
            btnDeparture.visibility = View.GONE
        }

        // Set listeners for the "show connections from now" button
        btnShowConnectionsNow.setOnClickListener { layoutDeparture.resetDateTimeToNow() }

        // Open map (for selecting a station) when the map button is clicked
        btnMap.setOnClickListener { openStationMap() }

        // Toggle the bookmarks/favorites menu when clicking the bookmarks button
        btnBookmarks.setOnClickListener { toggleBookmarks() }

        // Toggle the station info pullup when the info button is pressed
        btnInfo.setOnClickListener { toggleStationPullup() }

        // When a new favorite station was added, update the bookmarks view
        ECHTZEYT_CONFIGURATION.onFavoriteStationsChangedListeners.add { updateBookmarks() }

        // When the pullup gets closed, update the info button
        pullupStation.addOnCloseListener {
            pullupStationOpened = false
            updateInfoButtonFill()
        }

        // Listener for whether the ime (i.e. keyboard inset) is visible / has been hidden
        // -> clear focus from text inputs on keyboard hide
        safeView.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) { return@addOnLayoutChangeListener }
            if (!v.isAttachedToWindow) { return@addOnLayoutChangeListener }
            val keyboardVisible = WindowInsetsCompat.toWindowInsetsCompat(v.rootWindowInsets, v).isVisible(
                WindowInsetsCompat.Type.ime())
            if (mKeyboardVisible && !keyboardVisible) { edtSearch.clearFocus() }
            mKeyboardVisible = keyboardVisible
        }
    }
    private fun initThreads() {
        // Theoretically these threads could be combined into one, however this can be laggy, especially on older hardware

        // Search thread
        thread(start=true, isDaemon=true) {
            while (true) {
                if (!isInForeground.block(15000)) { continue }
                if (isDestroyed) {
                    return@thread
                }

                edtSearch.ntUpdateSearch()
                Thread.sleep(50)
            }
        }

        // Connections thread
        thread(start=true, isDaemon=true) {
            while (true) {
                if (!isInForeground.block(15000)) { continue }
                if (isDestroyed) {
                    return@thread
                }

                val time = System.currentTimeMillis()
                if (time > nextUpdateConnections) { ntUpdateConnections() }
                Thread.sleep(50)
            }
        }
    }
    private fun initApp() {
        layoutBookmarks.alpha = 0f
        updateBookmarks()
    }

    private fun calculateUpdateFactor(dateTime: LocalDateTime): Float {
        val delta = ChronoUnit.MINUTES.between(LocalDateTime.now(), dateTime)
        if (delta < -120) { return 30f } // if you are looking at connections from more than two hours ago, the update rate can be slowed down significantly
        return (delta / 30f).coerceIn(1f, 10f) // after looking 30 minutes in the future -> start to slow down the lookup times (30 minutes = 1x; 300 minutes = 10x slowdown), with a max slowdown of 10
    }

    private val txtLastUpdated: TextView by LazyView(R.id.txtLastUpdated)
    private val txtEmpty: TextView by LazyView(R.id.txtEmpty)
    /*
      Update all the connections for the currently selected station
     */
    @Suppress("DEPRECATION")
    @SuppressLint("SetTextI18n", "SimpleDateFormat")
    private fun ntUpdateConnections() {
        // Schedule the next update
        // Usually the next update would be in 5 seconds
        val delayNextUpdate = ECHTZEYT_CONFIGURATION.getRealtimeUpdateInterval()

        // calculate an additional delay factor if we are looking far into the future -> no "unnecessary" updates
        var additionalUpdateDelay = 0L
        val dateTime = layoutDeparture.dateTime
        if (ECHTZEYT_CONFIGURATION.shouldSlowDownRealtimeUpdates()) {
            val delayUpdateFactor = calculateUpdateFactor(dateTime)
            // the additional delay should not exceed 3 minutes
            additionalUpdateDelay = ((delayUpdateFactor - 1) * delayNextUpdate).roundToLong().coerceAtMost(3 * 60 * 1000)
        }
        // only force the update when nothing else has requested an update in the meantime (-> curUpdateTime would not be equal to nextUpdateConnections anymore)
        val plannedNextUpdateTime = updateConnectionsIn(delayNextUpdate + additionalUpdateDelay, true)

        // Select all necessary views
        val currentPOI = edtSearch.currentPOI
        if (currentPOI == null) {
            activity?.runOnUiThread {
                layoutConnections.removeAllViews()
                txtEmpty.setText(R.string.updateStationEmpty)
                txtEmpty.visibility = View.VISIBLE
                btnShowConnectionsNow.visibility = View.GONE
                btnShowConnectionsTime.visibility = View.GONE
            }
            return
        }

        val connections: List<RealtimeConnection>
        try {
            if (shouldUpdateStationPullup) {
                activity?.runOnUiThread { pullupStation.setPOI(currentPOI) }
                shouldUpdateStationPullup = false
            }

            val request = RealtimeRequestImpl(poi=currentPOI)
            if (apiSupportsDifferentTimeThanNow()) { request.time = layoutDeparture.dateTime }
            connections = transportRealtimeAPI.getRealtimeInformation(request).connections
        } catch (e: Exception) {
            activity?.runOnUiThread {
                txtLastUpdated.setTextColor(resources.getColor(R.color.error))
                txtLastUpdated.alpha = 1f
                txtLastUpdated.animate().alpha(0.4f).setDuration(300).setStartDelay(300).start()
            }

            val classification = classifyExceptionDefault(e)
            exceptions.add(ClassifiedException(e, classification))

            // Error -> next connection update in 1 second instead of whatever was planned
            updateConnectionsIn(1000, plannedNextUpdateTime == nextUpdateConnections)
            return
        }

        // TODO: maybe optimize this by reusing already existing RealtimeInfo views
        // TODO: also, maybe use a recycler view?
        val hideCancelled = ECHTZEYT_CONFIGURATION.shouldHideCancelledRealtimeConnections()
        val views = connections.mapIndexedNotNull { i, it ->
            if (hideCancelled && it.isStopCancelled) { return@mapIndexedNotNull null }
            RealtimeInfo(safeContext, it, (i%2)>0)
        }

        val noFutureApi = !apiSupportsDifferentTimeThanNow()
        val timeLastConnection = views.lastOrNull()?.connection?.stop?.departureActual

        val lineNumberWidths = mutableMapOf<View, Int>()
        var maxMinWidth = 0
        var maxSecWidth = 0

        for (view in views) {
            view.measureForMaximumWidth(layoutConnections)
            lineNumberWidths[view] = view.getMaxLineNumberWidth()
            maxMinWidth = maxMinWidth.coerceAtLeast(view.getMaxMinutesWidth())
            maxSecWidth = maxSecWidth.coerceAtLeast(view.getMaxSecondsWidth())
        }

        val maxLineNumberWidth = lineNumberWidths.values.maxOrNull() ?: 0
        val setWidthInsteadOfMargin = ECHTZEYT_CONFIGURATION.iconsRealtimeViewSameWidth()
        for (view in views) {
            if (setWidthInsteadOfMargin) {
                view.setLineNumberMinWidth(maxLineNumberWidth)
            } else {
                view.setLineNumberMarginLeft(maxLineNumberWidth - lineNumberWidths.getOrDefault(view, maxLineNumberWidth))
            }
            view.setMinutesMinWidth(maxMinWidth)
            view.setSecondsMinWidth(maxSecWidth)
        }

        activity?.runOnUiThread {
            if (connections.isEmpty()) {
                txtEmpty.setText(if (layoutDeparture.isNow()) R.string.updateEmptyNow else R.string.updateEmptyTime)
                txtEmpty.visibility = View.VISIBLE
            } else {
                txtEmpty.visibility = View.GONE
            }

            btnShowConnectionsNow.visibility = if (noFutureApi || layoutDeparture.isNow()) View.GONE else View.VISIBLE
            if (noFutureApi || (timeLastConnection == null)) {
                btnShowConnectionsTime.visibility = View.GONE
            } else {
                btnShowConnectionsTime.text = resources.getString(R.string.realtimeShowFromTime, ECHTZEYT_CONFIGURATION.formatDateTime(timeLastConnection))
                btnShowConnectionsTime.setOnClickListener { layoutDeparture.dateTime = timeLastConnection }
                btnShowConnectionsTime.visibility = View.VISIBLE
            }

            layoutConnections.removeAllViews()

            for (view in views) {
                layoutConnections.addView(view)
            }

            txtLastUpdated.text = "${resources.getString(R.string.updateLast)} ${ECHTZEYT_CONFIGURATION.formatDateTime(LocalDateTime.now(), showSeconds=true)}"
            txtLastUpdated.setTextColor(resources.getColor(R.color.success))
            txtLastUpdated.alpha = 1f
            txtLastUpdated.animate().alpha(0.4f).setDuration(300).setStartDelay(300).start()
        }
    }

    private fun sendFeedback(sendLogs: Boolean) {
        val i = Intent(Intent.ACTION_SEND); i.type = "message/rfc822"
        i.putExtra(Intent.EXTRA_EMAIL, arrayOf(resources.getString(R.string.contactEmail)))
        val contactSubject = "${resources.getString(R.string.appName)} - ${resources.getString(R.string.contactSubject)}"
        i.putExtra(Intent.EXTRA_SUBJECT, contactSubject)
        var contactBody = resources.getString(R.string.contactBody)
        if (sendLogs) {
            contactBody += resources.getString(R.string.contactBodyError)
            for (exception in exceptions) {
                contactBody += exception.toString() + "\n\n"
            }
        }
        i.putExtra(Intent.EXTRA_TEXT, contactBody)
        try { startActivity(Intent.createChooser(i, resources.getString(R.string.contactTitle))) }
        catch (e: ActivityNotFoundException) { Toast.makeText(safeContext, resources.getString(R.string.contactError), Toast.LENGTH_SHORT).show() }
    }
    private fun sendFeedback() {
        if (exceptions.isEmpty()) {
            sendFeedback(false)
            return
        }

        // If errors occurred, ask the user whether or not he wants to report it
        AlertDialog.Builder(safeContext, R.style.Theme_Echtzeyt_Dialog_Alert)
            .setTitle(R.string.sendLogsTitle)
            .setMessage(R.string.sendLogsText)
            .setIcon(R.drawable.ic_error)
            .setPositiveButton(R.string.sendLogsYes) { _, _ -> sendFeedback(true) }
            .setNegativeButton(R.string.sendLogsNo) { _, _ -> sendFeedback(false) }
            .show()
    }

    private fun toggleBookmarks() { toggleBookmarks(false) }
    private fun toggleBookmarks(forceClose: Boolean) {
        val duration = 150L
        if (forceClose || bookmarksOpened) {
            ViewCompat.animate(layoutBookmarks).alpha(0f).translationY(20f).setDuration(duration).withEndAction {
                if (bookmarksOpened) { return@withEndAction }
                layoutBookmarks.visibility = View.GONE
            }.start()
            bookmarksOpened = false
        } else {
            layoutBookmarks.visibility = View.VISIBLE
            layoutBookmarks.animate().alpha(1f).translationY(0f).setInterpolator(FastOutSlowInInterpolator()).setDuration(duration).start()
            bookmarksOpened = true
        }
    }

    private val layoutBookmarksItems: LinearLayout by LazyView(R.id.bookmarksItems)
    private val txtBookmarksEmpty: TextView by LazyView(R.id.bookmarksEmpty)
    @SuppressLint("SetTextI18n")
    private fun updateBookmarks() {
        val itemsScroll = layoutBookmarksItems.parent as View
        val savedStations = ECHTZEYT_CONFIGURATION.getFavoritePOIs()

        layoutBookmarksItems.removeAllViews()
        if (savedStations.isEmpty()) {
            txtBookmarksEmpty.visibility = View.VISIBLE
            itemsScroll.visibility = View.GONE
            itemsScroll.invalidate()
            return
        }
        txtBookmarksEmpty.visibility = View.GONE
        itemsScroll.visibility = View.VISIBLE

        var odd = false
        for (savedStation in savedStations) {
            val itemButton = layoutInflater.inflate(R.layout.comp_button_bookmark, layoutBookmarksItems, false)
            (itemButton as? TextView) ?: continue

            itemButton.text = savedStation
            if (odd) { itemButton.setBackgroundResource(R.drawable.favorite_highlight) }
            itemButton.setOnClickListener { commitToStation(savedStation) }

            layoutBookmarksItems.addView(itemButton)

            odd = !odd
        }

        itemsScroll.invalidate()
    }

    private fun toggleStationPullup(forceClose: Boolean = false) {
        if (pullupStationOpened || forceClose) {
            pullupStation.hidePullup()
            pullupStationOpened = false
        } else {
            edtSearch.currentPOI?.let {
                pullupStation.setPOI(it, true)
                pullupStationOpened = true
            }
        }
        updateInfoButtonFill()
    }

    private fun updateInfoButtonFill() {
        btnInfo.setImageResource(if (pullupStationOpened) R.drawable.ic_info_filled else R.drawable.ic_info)
    }

    private fun commitToStation(poi: POI?, updateSearch: Boolean=true, updatePullup: Boolean=true, updateConnections: Boolean=true) {
        if (updateSearch) { edtSearch.currentPOI = poi }
        if (updatePullup) { poi?.let { pullupStation.setPOI(it) } }
        if (updateConnections) { updateConnections() }
        ECHTZEYT_CONFIGURATION.setCurrentRealtimePOI(poi)
    }

    private fun commitToStation(stationName: String) {
        edtSearch.setText(stationName)
        toggleBookmarks(true)
    }

    private fun scheduleNextConnectionsUpdate(next: Long, force: Boolean = false) {
        if ((next > nextUpdateConnections) && !force) { return }
        nextUpdateConnections = next
    }

    private fun updateConnectionsIn(deltaTime: Long, force: Boolean = false): Long {
        val now = System.currentTimeMillis()
        val next = (nextUpdateConnections + deltaTime).coerceIn(now, now + deltaTime)
        scheduleNextConnectionsUpdate(next, force)
        return next
    }

    private fun updateConnections() {
        // schedule the next connection update to be now
        scheduleNextConnectionsUpdate(0, true)
    }

    private fun classifyExceptionDefault(e: Exception) : String {
        if (e is java.net.UnknownHostException) {
            return "No internet connection"
        }
        return ""
    }

    protected fun openStationMap() {
        if (!ECHTZEYT_CONFIGURATION.mapsSupportLocateStations) { return }
        activityMapLauncher.launch(edtSearch.currentPOI)
    }

    protected fun apiSupportsDifferentTimeThanNow(): Boolean {
        return transportRealtimeAPI.supportsRealtimeFeature(RealtimeAPI.FEATURE_REALTIME_FUTURE or RealtimeAPI.FEATURE_REALTIME_PAST)
    }
}