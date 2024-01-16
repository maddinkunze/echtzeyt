package com.maddin.echtzeyt.fragments.echtzeyt

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.MeasureSpec
import android.view.animation.Interpolator
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
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.children
import androidx.core.view.doOnLayout
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.maddin.echtzeyt.ECHTZEYT_CONFIGURATION
import com.maddin.echtzeyt.R
import com.maddin.echtzeyt.components.InstantAutoCompleteTextView
import com.maddin.echtzeyt.components.RealtimeInfo
import com.maddin.echtzeyt.fragments.EchtzeytForegroundFragment
import com.maddin.echtzeyt.randomcode.ActivityResultSerializable
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
    private var shouldUpdateSearch = false
    private var nextUpdateConnections = 0L
    private var currentStationSearch = ""

    // Elements/Views
    private val edtSearch by lazy { safeView.findViewById<InstantAutoCompleteTextView>(R.id.edtSearch) }
    private val btnSearch by lazy { safeView.findViewById<ImageButton>(R.id.btnSearch) }
    private val btnMap by lazy { safeView.findViewById<ImageButton>(R.id.btnMap) }
    private val btnLike by lazy { safeView.findViewById<ImageButton>(R.id.btnLike) }
    private val btnBookmarks by lazy { safeView.findViewById<ImageButton>(R.id.btnBookmarks) }
    private val layoutConnections by lazy { safeView.findViewById<LinearLayout>(R.id.layoutScroll) }


    // Bookmark variables
    private var bookmarksOpened = false

    private var currentStationName = ""
    private var currentStation: Station? = null
    private var savedStations: MutableSet<String> = mutableSetOf()
    private val adapterSearch by lazy { ArrayAdapter<String>(safeContext, R.layout.support_simple_spinner_dropdown_item) }
    protected val transportSearchStationAPI by lazy { ECHTZEYT_CONFIGURATION.realtimeStationAPI!! }
    protected val transportRealtimeAPI by lazy { ECHTZEYT_CONFIGURATION.realtimeRealtimeAPI!! }

    // Everything related to other activities (such as the settings or a station selection map)
    protected val activitySettings by lazy { ECHTZEYT_CONFIGURATION.activitySettings }
    protected val activityMap by lazy { if (!ECHTZEYT_CONFIGURATION.mapsSupportLocateStations) { return@lazy null }; ECHTZEYT_CONFIGURATION.activityMap }
    private val activityMapLauncher by lazy { registerForActivityResult(ActivityResultSerializable<LocatableStation>(activityMap!!)) { commitToStation(it) } }

    // Notifications and exceptions
    private var currentNotification: JSONObject? = null
    private var lastClosedNotification = ""
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
        updateConnectionsIn(0, true)
        super.onResume()
    }

    private fun initVariables() {
        // the launcher has to be registered before the activity has been started
        // by explicitly stating activityMapLauncher, we force lazy to initialize activityMapLauncher
        if (activityMap != null) { activityMapLauncher }

        toggleBookmarks(true)
    }
    private fun initSettings() {
        // Save last station?
        if (!preferences.contains("saveStation")) { preferences.edit().putBoolean("saveStation", true).apply() }
        if (preferences.getBoolean("saveStation", true)) { currentStationName = preferences.getString("station", "")?:"" }

        // Saved stations
        if (!preferences.contains("savedStations")) { preferences.edit().putStringSet("savedStations", savedStations).apply() }
        val savedStationsTemp = preferences.getStringSet("savedStations", savedStations)
        if (savedStationsTemp != null) { savedStations = savedStationsTemp }

        // Notifications (last closed notification -> do not show a notification that is already closed)
        if (!preferences.contains("lastClosedNotification")) { preferences.edit().putString("lastClosedNotification", lastClosedNotification).apply() }
        lastClosedNotification = preferences.getString("lastClosedNotification", lastClosedNotification).toString()
    }
    private fun initHandlers() {
        // Set adapter (dropdown) for the station search -> autocomplete
        edtSearch.setAdapter(adapterSearch)
        edtSearch.threshold = 0  // Show dropdown after the first character entered
        edtSearch.setDropDownBackgroundResource(R.drawable.dropdown)  // Change background resource of the dropdown to match the rest

        // Listener when the main search input changes
        edtSearch.addOnTextChangedListener { text ->
            val search = text.toString()
            if (search == currentStationName) { edtSearch.clearFocus(); return@addOnTextChangedListener }

            currentStationSearch = search
            shouldUpdateSearch = true
        }

        // When selecting an item of the search dropdown
        edtSearch.addOnItemSelectedListener { clearFocus(); commitToStation() }

        // Update station times and close the dropdown when clicking on the search button
        btnSearch.setOnClickListener { clearFocus(); commitToStation() }

        // Open map (for selecting a station) when the map button is clicked
        btnMap.setOnClickListener { openStationMap() }

        // Toggle like when clicking the star/like button
        btnLike.setOnClickListener { toggleLike() }

        // Toggle the bookmarks/favorites menu when clicking the bookmarks button
        btnBookmarks.setOnClickListener { toggleBookmarks() }
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

                if (shouldUpdateSearch) { ntUpdateSearch() }
                Thread.sleep(20)
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

        edtSearch.setText(currentStationName)
        if (currentStationName.isNotEmpty()) { edtSearch.clearFocus() }

        commitToStation()
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
        currentStationSearch = edtSearch.text.toString()

        val connections: List<RealtimeConnection>
        try {
            if ((currentStation == null) || (currentStation!!.name != currentStationSearch)) {
                val stations = transportSearchStationAPI.searchStations(currentStationSearch)
                if (stations.isEmpty()) { return }
                currentStation = stations[0]
            }
            connections = transportRealtimeAPI.getRealtimeInformation(currentStation!!).connections
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
        val views = connections.mapIndexed { i, it -> RealtimeInfo(safeContext, it, (i%2)>0) }

        activity?.runOnUiThread {
            txtEmpty.visibility = if (connections.isEmpty()) { View.VISIBLE } else { View.GONE }

            var maxLineNumberWidth = 0
            var maxMinWidth = 0
            var maxSecWidth = 0

            layoutConnections.removeAllViews()

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
    private fun ntUpdateSearch() {
        try {
            currentStationSearch = edtSearch.text.toString()
        } catch (_: IndexOutOfBoundsException) { } // quite normal due to threading

        try {
            var stations = emptyList<Station>()
            if (currentStationSearch.isNotEmpty()) {
                stations = transportSearchStationAPI.searchStations(currentStationSearch)
            }

            activity?.runOnUiThread {
                adapterSearch.clear()
                if (stations.isEmpty()) {
                    adapterSearch.notifyDataSetChanged()
                    edtSearch.dismissDropDown()
                    return@runOnUiThread
                }

                for (station in stations) {
                    val stationName = station.name
                    if (stationName == currentStationSearch) { clearFocus(); return@runOnUiThread }
                    adapterSearch.add(stationName)
                }

                adapterSearch.notifyDataSetChanged()
                edtSearch.post {
                    edtSearch.showSuggestions()
                }
            }
        } catch (e: Exception) {
            val classification = classifyExceptionDefault(e)
            exceptions.add(ClassifiedException(e, classification))
        }

        shouldUpdateSearch = false
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

    private fun toggleLike() {
        val btnLike = safeView.findViewById<ImageButton>(R.id.btnLike)
        if (savedStations.contains(currentStationName)) {
            savedStations.remove(currentStationName)
            btnLike.setImageResource(R.drawable.ic_star)
        } else {
            savedStations.add(currentStationName)
            btnLike.setImageResource(R.drawable.ic_star_filled)
        }

        preferences.edit().remove("savedStations").apply()
        preferences.edit().putStringSet("savedStations", savedStations).apply()
        updateBookmarks()
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

        items.removeAllViews()
        val txtEmpty = safeView.findViewById<TextView>(R.id.bookmarksEmpty)
        if (savedStations.isEmpty()) {
            txtEmpty.visibility = View.VISIBLE
            itemsScroll.visibility = View.GONE
            return
        }
        txtEmpty.visibility = View.GONE
        itemsScroll.visibility = View.VISIBLE

        val inflater = LayoutInflater.from(safeContext)
        for (savedStation in savedStations) {
            val root = inflater.inflate(R.layout.comp_button_bookmark, items, false)
            val itemButton = root.findViewById<Button>(R.id.btnBookmarkItem)
            itemButton.text = " • $savedStation"
            itemButton.setOnClickListener { commitToStation(savedStation) }
            items.addView(itemButton)
        }
    }

    private fun commitToStation(stationName: String? = null) {
        val edtSearch = safeView.findViewById<TextView>(R.id.edtSearch)
        if (!stationName.isNullOrEmpty()) {
            edtSearch.text = stationName
        }
        currentStationName = edtSearch.text.toString()
        preferences.edit().putString("station", currentStationName).apply()
        safeView.findViewById<ImageButton>(R.id.btnLike).setImageResource(if (savedStations.contains(currentStationName)) { R.drawable.ic_star_filled } else { R.drawable.ic_star })

        toggleBookmarks(true)
        updateConnections()
    }

    private fun commitToStation(station: Station?) {
        if (station == null) { return }
        currentStation = station
        commitToStation(station.name)
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

    private fun clearFocus() {
        val edtSearch = safeView.findViewById<AutoCompleteTextView>(R.id.edtSearch)
        val focusLayout = safeView.findViewById<LinearLayout>(R.id.focusableLayout)
        edtSearch.dismissDropDown()
        focusLayout.requestFocus()
        (activity?.getSystemService(AppCompatActivity.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(safeView.findViewById<View>(R.id.dividerSearch).windowToken, 0)
    }

    private fun classifyExceptionDefault(e: Exception) : String {
        if (e is java.net.UnknownHostException) {
            return "No internet connection"
        }
        return ""
    }

    protected fun openStationMap() {
        if (activityMap == null) { return }

        var station: LocatableStation? = null
        if (currentStation is LocatableStation) { station = currentStation as LocatableStation }

        activityMapLauncher.launch(station)
    }
}