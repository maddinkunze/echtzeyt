package com.maddin.echtzeyt.fragments.echtzeyt

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.ConditionVariable
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.maddin.echtzeyt.ECHTZEYT_CONFIGURATION
import com.maddin.echtzeyt.R
import com.maddin.echtzeyt.activities.MapResultContractSelectStation
import com.maddin.echtzeyt.components.InstantAutoCompleteTextView
import com.maddin.echtzeyt.components.RealtimeInfo
import com.maddin.echtzeyt.components.StationPullup
import com.maddin.echtzeyt.components.StationSearchTextView
import com.maddin.echtzeyt.components.StationSearchbar
import com.maddin.echtzeyt.fragments.EchtzeytForegroundFragment
import com.maddin.echtzeyt.randomcode.ClassifiedException
import com.maddin.transportapi.LocatableStation
import com.maddin.transportapi.RealtimeConnection
import com.maddin.transportapi.Station
import org.json.JSONObject
import java.lang.IndexOutOfBoundsException
import java.text.SimpleDateFormat
import java.util.Calendar
import kotlin.concurrent.thread

class RealtimeFragment : EchtzeytForegroundFragment(R.layout.fragment_realtime) {
    private var shouldUpdateStationPullup = false
    private var nextUpdateConnections = 0L

    // Elements/Views
    private val edtSearch by lazy { safeView.findViewById<StationSearchbar>(R.id.edtSearch) }
    private val btnMap by lazy { safeView.findViewById<ImageButton>(R.id.btnMap) }
    private val btnInfo by lazy { safeView.findViewById<ImageButton>(R.id.btnInfo) }
    private val btnBookmarks by lazy { safeView.findViewById<ImageButton>(R.id.btnBookmarks) }
    private val layoutConnections by lazy { safeView.findViewById<LinearLayout>(R.id.layoutScroll) }
    private val pullupStation by lazy { safeView.findViewById<StationPullup>(R.id.pullupStationInfo) }

    private var mKeyboardVisible = false

    // Bookmark variables
    private var bookmarksOpened = false
    private var pullupStationOpened = false

    protected val transportRealtimeAPI by lazy { ECHTZEYT_CONFIGURATION.realtimeRealtimeAPI!! }

    // Everything related to other activities (such as the settings or a station selection map)
    private val activityMapLauncher by lazy { registerForActivityResult(MapResultContractSelectStation()) { if (it == null) { return@registerForActivityResult }; commitToStation(it) } }

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

        toggleBookmarks(true)
    }
    private fun initSettings() {
        // Save last station?
        edtSearch.setText(ECHTZEYT_CONFIGURATION.getLastRealtimeStationName())
    }
    private fun initHandlers() {
        // When the search has selected a new station, update connections and pullup etc
        edtSearch.onItemSelectedListeners.add { commitToStation(edtSearch.currentStation, updateSearch=false) }

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
        safeView.findViewById<View>(R.id.layoutBookmarks).alpha = 0f
        updateBookmarks()
    }

    /*
      Update all the connections for the currently selected station
     */
    @Suppress("DEPRECATION")
    @SuppressLint("SetTextI18n", "SimpleDateFormat")
    private fun ntUpdateConnections() {
        // Schedule the next update
        // Usually the next update would be in 5 seconds
        val delayNextUpdate = preferences.getInt("updateEvery", 5000).toLong()
        // only force the update when nothing else has requested an update in the meantime (-> curUpdateTime would not be equal to nextUpdateConnections anymore)
        val plannedNextUpdateTime = updateConnectionsIn(delayNextUpdate, true)

        // Select all necessary views
        val txtLastUpdated = safeView.findViewById<TextView>(R.id.txtLastUpdated)
        val txtEmpty = safeView.findViewById<TextView>(R.id.txtEmpty)
        val currentStation = edtSearch.currentStation
        if (currentStation == null) {
            activity?.runOnUiThread {
                layoutConnections.removeAllViews()
                txtEmpty.setText(R.string.updateStationEmpty)
                txtEmpty.visibility = View.VISIBLE
            }
            return
        }

        val connections: List<RealtimeConnection>
        try {
            /*if ((currentStation == null) || (currentStation!!.name.lowercase() != currentStationSearch.lowercase())) {
                val stations = transportSearchStationAPI.searchStations(currentStationSearch)
                if (stations.isEmpty()) {
                    currentStation = null
                    activity?.runOnUiThread {
                        layoutConnections.removeAllViews()
                        txtEmpty.setText(R.string.updateStationInvalid)
                        txtEmpty.visibility = View.VISIBLE
                    }
                    return
                }
                currentStation = stations[0]
            }*/
            if (shouldUpdateStationPullup) {
                activity?.runOnUiThread { pullupStation.setStation(currentStation) }
                shouldUpdateStationPullup = false
            }
            connections = transportRealtimeAPI.getRealtimeInformation(currentStation).connections
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
        val views = connections.mapIndexed { i, it -> RealtimeInfo(safeContext, it, (i%2)>0) }

        var maxLineNumberWidth = 0
        var maxMinWidth = 0
        var maxSecWidth = 0

        for (view in views) {
            view.measureForMaximumWidth(layoutConnections)
            maxLineNumberWidth = maxLineNumberWidth.coerceAtLeast(view.getMaxLineNumberWidth())
            maxMinWidth = maxMinWidth.coerceAtLeast(view.getMaxMinutesWidth())
            maxSecWidth = maxSecWidth.coerceAtLeast(view.getMaxSecondsWidth())
        }

        for (view in views) {
            view.setLineNumberMinWidth(maxLineNumberWidth)
            view.setMinutesMinWidth(maxMinWidth)
            view.setSecondsMinWidth(maxSecWidth)
        }

        activity?.runOnUiThread {
            if (connections.isEmpty()) {
                txtEmpty.setText(R.string.updateEmpty)
                txtEmpty.visibility = View.VISIBLE
            } else {
                txtEmpty.visibility = View.GONE
            }

            layoutConnections.removeAllViews()

            for (view in views) {
                layoutConnections.addView(view)
            }

            txtLastUpdated.text = "${resources.getString(R.string.updateLast)} ${
                SimpleDateFormat("HH:mm:ss").format(
                    Calendar.getInstance().time
                )
            }"
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
        AlertDialog.Builder(safeContext, R.style.Theme_Echtzeyt_AlertDialog)
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
        val bookmarksLayout = safeView.findViewById<View>(R.id.layoutBookmarks)
        if (forceClose || bookmarksOpened) {
            ViewCompat.animate(bookmarksLayout).alpha(0f).translationY(20f).setDuration(duration).withEndAction {
                if (bookmarksOpened) { return@withEndAction }
                bookmarksLayout.visibility = View.GONE
            }.start()
            bookmarksOpened = false
        } else {
            bookmarksLayout.visibility = View.VISIBLE
            bookmarksLayout.animate().alpha(1f).translationY(0f).setInterpolator(FastOutSlowInInterpolator()).setDuration(duration).start()
            bookmarksOpened = true
        }
    }
    @SuppressLint("SetTextI18n")
    private fun updateBookmarks() {
        val items = safeView.findViewById<LinearLayout>(R.id.bookmarksItems)
        val itemsScroll = items.parent as View
        val savedStations = ECHTZEYT_CONFIGURATION.getFavoriteStations()

        items.removeAllViews()
        val txtEmpty = safeView.findViewById<TextView>(R.id.bookmarksEmpty)
        if (savedStations.isEmpty()) {
            txtEmpty.visibility = View.VISIBLE
            itemsScroll.visibility = View.GONE
            itemsScroll.invalidate()
            return
        }
        txtEmpty.visibility = View.GONE
        itemsScroll.visibility = View.VISIBLE

        val inflater = LayoutInflater.from(safeContext)
        var odd = false
        for (savedStation in savedStations) {
            val root = inflater.inflate(R.layout.comp_button_bookmark, items, false)
            val itemButton = root.findViewById<Button>(R.id.btnBookmarkItem)
            itemButton.text = savedStation
            if (odd) { itemButton.setBackgroundResource(R.drawable.favorite_highlight) }
            itemButton.setOnClickListener { commitToStation(savedStation) }
            items.addView(itemButton)

            odd = !odd
        }
        itemsScroll.invalidate()
    }

    private fun toggleStationPullup(forceClose: Boolean = false) {
        if (pullupStationOpened || forceClose) {
            pullupStation.hidePullup()
            pullupStationOpened = false
        } else {
            edtSearch.currentStation?.let {
                pullupStation.setStation(it, true)
                pullupStationOpened = true
            }
        }
        updateInfoButtonFill()
    }

    private fun updateInfoButtonFill() {
        btnInfo.setImageResource(if (pullupStationOpened) R.drawable.ic_info_filled else R.drawable.ic_info)
    }

    private fun commitToStation(station: Station?, updateSearch: Boolean=true, updatePullup: Boolean=true, updateConnections: Boolean=true) {
        if (updateSearch) { edtSearch.currentStation = station }
        if (updatePullup) { station?.let { pullupStation.setStation(it) } }
        if (updateConnections) { updateConnections() }
        ECHTZEYT_CONFIGURATION.setCurrentRealtimeStation(station)
    }

    private fun commitToStation(stationName: String) {
        edtSearch.setText(stationName)
        toggleBookmarks(true)
    }

    /*private fun commitToStation(stationName: String? = null) {
        val edtSearch = safeView.findViewById<TextView>(R.id.edtSearch)
        if (!stationName.isNullOrEmpty()) {
            edtSearch.text = stationName
        }
        currentStationName = edtSearch.text.toString()
        preferences.edit().putString("station", currentStationName).apply()
        shouldUpdateStationPullup = true

        toggleBookmarks(true)
        updateConnections()
    }*/

    /*private fun commitToStation(station: Station?) {
        if (station == null) { return }
        currentStation = station
        commitToStation(station.name)
    }*/

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
        activityMapLauncher.launch(edtSearch.currentStation as? LocatableStation)
    }
}