package com.maddin.echtzeyt

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.*
import android.text.Html
import android.text.Spanned
import android.text.SpannedString
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.children
import com.maddin.echtzeyt.components.InstantAutoCompleteTextView
import com.maddin.transportapi.RealtimeConnection
import com.maddin.transportapi.Station
import org.json.JSONObject
import java.lang.Integer.max
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread

@Suppress("deprecation", "unused")
fun setAppLocale(context: Context, language: String) {
    val resources = context.resources
    val config = resources.configuration
    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N) {
        val locale = Locale(language)
        Locale.setDefault(locale)
        config.setLocale(locale)
        context.createConfigurationContext(config)
    } else {
        val locale = Locale(language)
        Locale.setDefault(locale)
        config.locale = locale
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) { config.setLayoutDirection(locale) }
    }
    resources.updateConfiguration(config, resources.displayMetrics)
}

var PREFERENCES_NAME = ""
val EXAMPLE_API = com.maddin.transportapi.impl.EmptyAPI()
// val EXAMPLE_API = com.maddin.transportapi.impl.ExampleAPI() // uncomment this to test the app with mock data

open class EchtzeytActivity : AppCompatActivity() {
    private var shouldUpdateSearch = false
    private var nextUpdateConnections = 0L
    private var nextUpdateNotifications = 0L
    private var currentStationSearch = ""

    private var menuOpened = false
    private lateinit var menuIds: IntArray
    private lateinit var menuVisible: BooleanArray
    private var menuItems: MutableList<View> = mutableListOf()
    private var bookmarksOpened = false

    private lateinit var preferences: SharedPreferences
    private var currentStation = ""
    private var savedStations: MutableSet<String> = mutableSetOf()
    private lateinit var adapterSearch: ArrayAdapter<String>
    private lateinit var transportStationAPI: com.maddin.transportapi.StationAPI
    private lateinit var transportRealtimeAPI: com.maddin.transportapi.RealtimeAPI

    private var currentNotification: JSONObject? = null
    private var lastClosedNotification = ""
    private var exceptions: MutableList<ClassifiedException> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //setAppLocale(this, "de")
        setContentView(R.layout.activity_main)

        initVariables()
        initSettings()
        initHandlers()
        initApp()
        initThreads()
    }

    private fun initVariables() {
        menuIds = intArrayOf(
            R.id.layoutButtonSettings,
            R.id.layoutButtonDonate,
            R.id.layoutButtonMessage,
            R.id.layoutButtonAnnouncement
        )
        for (menuId in menuIds) {
            val menuItem = findViewById<View>(menuId)
            menuItem.alpha = 0f
            menuItems.add(menuItem)
        }
        menuVisible = booleanArrayOf(
            true, // always show the settings icon
            resources.getString(R.string.urlSupportMe).isNotBlank(),
            resources.getString(R.string.contactEmail).isNotBlank(),
            false // always hide the notification button at first
        )

        // Set mock/empty api as default
        if (!this::transportStationAPI.isInitialized) {
            this.transportStationAPI = EXAMPLE_API
        }
        if (!this::transportRealtimeAPI.isInitialized) {
            this.transportRealtimeAPI = EXAMPLE_API
        }

        PREFERENCES_NAME = packageName
    }
    private fun initSettings() {
        preferences = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)

        // Save last station?
        if (!preferences.contains("saveStation")) { preferences.edit().putBoolean("saveStation", true).apply() }
        if (preferences.getBoolean("saveStation", true)) { currentStation = preferences.getString("station", "")!! }

        // Dark mode
        if (!preferences.contains("darkMode")) { preferences.edit().putBoolean("darkMode", AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES).apply() }
        if (!preferences.contains("autoDark")) { preferences.edit().putBoolean("autoDark", true).apply() }
        when {
            preferences.getBoolean("autoDark", true) -> { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) }
            preferences.getBoolean("darkMode", false) -> { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES) }
            else -> { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO) }
        }

        // Saved stations
        if (!preferences.contains("savedStations")) { preferences.edit().putStringSet("savedStations", savedStations).apply() }
        val savedStationsTemp = preferences.getStringSet("savedStations", savedStations)
        if (savedStationsTemp != null) { savedStations = savedStationsTemp }

        // Notifications (last closed notification -> do not show a notification that is already closed)
        if (!preferences.contains("lastClosedNotification")) { preferences.edit().putString("lastClosedNotification", lastClosedNotification).apply() }
        lastClosedNotification = preferences.getString("lastClosedNotification", lastClosedNotification).toString()
    }
    private fun initHandlers() {
        val edtSearch = findViewById<InstantAutoCompleteTextView>(R.id.edtSearch)
        val btnSearch = findViewById<ImageButton>(R.id.btnSearch)
        val btnLike = findViewById<ImageButton>(R.id.btnLike)
        val btnBookmarks = findViewById<ImageButton>(R.id.btnBookmarks)
        val btnMenu = findViewById<ImageButton>(R.id.btnMenu)
        val btnSettings = findViewById<ImageButton>(R.id.btnSettings)
        val btnDonate = findViewById<ImageButton>(R.id.btnDonate)
        val btnMessage = findViewById<ImageButton>(R.id.btnMessage)
        val btnNotification = findViewById<ImageButton>(R.id.btnAnnouncement)
        val btnNotificationClose = findViewById<ImageButton>(R.id.notificationButtonClose)

        // Set adapter (dropdown) for the station search -> autocomplete
        adapterSearch = ArrayAdapter<String>(this, R.layout.support_simple_spinner_dropdown_item)
        edtSearch.setAdapter(adapterSearch)
        edtSearch.threshold = 0  // Show dropdown after the first character entered
        edtSearch.setDropDownBackgroundResource(R.drawable.dropdown)  // Change background resource of the dropdown to match the rest

        // Listener when the main search input changes
        edtSearch.addOnTextChangedListener { text ->
            val search = text.toString()
            if (search == currentStation) { edtSearch.clearFocus(); return@addOnTextChangedListener }

            currentStationSearch = search
            shouldUpdateSearch = true
        }

        // When selecting an item of the search dropdown
        edtSearch.addOnItemSelectedListener { clearFocus(); commitToStation() }

        // Update station times and close the dropdown when clicking on the search button
        btnSearch.setOnClickListener { clearFocus(); commitToStation() }

        // Toggle like when clicking the star/like button
        btnLike.setOnClickListener { toggleLike() }

        // Toggle the bookmarks/favorites menu when clicking the bookmarks button
        btnBookmarks.setOnClickListener { toggleBookmarks() }

        // Toggle the (hamburger?) menu when clicking the menu button
        btnMenu.setOnClickListener { toggleMenu() }

        // Open settings when clicking the settings button
        println("MADDIN101: $packageName")
        btnSettings.setOnClickListener { toggleMenu(true); startActivity(Intent().setComponent(ComponentName(this, "$packageName.SettingsActivity"))) }

        // Open the support/donation link when clicking the donation button
        btnDonate.setOnClickListener { toggleMenu(true); startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(resources.getString(R.string.urlSupportMe)))) }

        // Start the feedback process when clicking the feedback button
        btnMessage.setOnClickListener { toggleMenu(true); sendFeedback() }

        // Open/close the current notification (if there is one) when clicking the associated buttons are clicked
        btnNotification.setOnClickListener { toggleMenu(true); showNotification() }
        btnNotificationClose.setOnClickListener { closeNotification() }
    }
    private fun initThreads() {
        // Theoretically these threads could be combined into one, however this can be laggy, especially on older hardware

        // Search thread
        thread(start = true, isDaemon = true) {
            while (true) {
                if (shouldUpdateSearch) { ntUpdateSearch() }
                Thread.sleep(20)
            }
        }

        // Connections thread
        thread(start = true, isDaemon = true) {
            while (true) {
                val time = System.currentTimeMillis()
                if (time > nextUpdateConnections) { ntUpdateConnections() }
                Thread.sleep(50)
            }
        }

        // Notifications thread
        thread(start = true, isDaemon = true) {
            while (true) {
                val time = System.currentTimeMillis()
                if (time > nextUpdateNotifications) { ntUpdateNotifications() }
                Thread.sleep(50)
            }
        }
    }
    private fun initApp() {
        val edtSearch = findViewById<AutoCompleteTextView>(R.id.edtSearch)
        edtSearch.setText(currentStation)
        if (currentStation.isNotEmpty()) { edtSearch.clearFocus() }

        commitToStation()
        updateBookmarks()
    }

    protected fun <API> setTransportAPI(transportAPI: API) where API : com.maddin.transportapi.StationAPI, API : com.maddin.transportapi.RealtimeAPI {
        // Set all transport apis to the one specified
        transportStationAPI = transportAPI
        transportRealtimeAPI = transportAPI
    }

    /*
      Update all the connections for the currently selected station
     */
    @Suppress("DEPRECATION")
    @SuppressLint("SetTextI18n", "SimpleDateFormat")
    private fun ntUpdateConnections() {
        // Select all necessary views
        val curUpdateTime = nextUpdateConnections
        val edtSearch = findViewById<AutoCompleteTextView>(R.id.edtSearch)
        val edtNumbers = findViewById<TextView>(R.id.txtLineNumbers)
        val edtNames = findViewById<TextView>(R.id.txtLineNames)
        val edtTimesHour = findViewById<TextView>(R.id.txtLineTimesHour)
        val edtTimesMin = findViewById<TextView>(R.id.txtLineTimesMin)
        val edtTimesSec = findViewById<TextView>(R.id.txtLineTimesSec)
        val txtLastUpdated = findViewById<TextView>(R.id.txtLastUpdated)

        val stops: List<RealtimeConnection>
        try {
            val stations = transportStationAPI.getStations(edtSearch.text.toString())
            if (stations.isEmpty()) { return }
            stops = transportRealtimeAPI.getRealtimeInformation(stations[0]).connections
        } catch (e: Exception) {
            Handler(Looper.getMainLooper()).post {
                txtLastUpdated.setTextColor(resources.getColor(R.color.error))
                txtLastUpdated.alpha = 1f
                val oa = ObjectAnimator.ofFloat(txtLastUpdated, "alpha", 0.4f).setDuration(300)
                oa.startDelay = 300; oa.start()
            }

            val classification = classifyExceptionDefault(e)
            exceptions.add(ClassifiedException(e, classification))

            // Error -> next connection update in 2 seconds
            scheduleNextConnectionsUpdate(System.currentTimeMillis() + 2000, curUpdateTime == nextUpdateConnections)
            return
        }

        var stop: RealtimeConnection
        var departure: Long
        var depHours: Long
        var maxHours = 0
        var depMin: Long
        var padMin: Int
        var depSec: Long

        var textNumbers = ""
        var textNames = ""
        var textTimesHours = ""
        var textTimesMin = ""
        var textTimesSec = ""

        for (index in stops.indices) {
            stop = stops[index]
            departure = stop.departsIn().coerceAtLeast(0)
            depHours = departure.div(3600)
            depMin = departure.div(60).rem(60)
            depSec = departure.rem(60)
            textNumbers += "${stop.vehicle.name}\n"
            textNames += "${stop.vehicle.directionName}\n"
            padMin = 0
            if (depHours > 0) {
                maxHours = max(maxHours, depHours.toInt())
                padMin = 2
                textTimesHours += "${depHours}h\n"
            } else {
                textTimesHours += "\n"
            }
            textTimesMin += "${depMin.toString().padStart(padMin, '0')}m\n"
            textTimesSec += "${depSec.toString().padStart(2, '0')}s\n"
        }

        // Add some clearance at the bottom
        textNumbers += "\n"
        textNames += "\n"
        textTimesHours += "\n"
        textTimesMin += "\n"
        textTimesSec += "\n"

        if (stops.isEmpty()) {
            textNames = resources.getString(R.string.updateEmpty)
        }

        Handler(Looper.getMainLooper()).post {
            edtNumbers.text = textNumbers
            edtNames.text = textNames
            edtTimesHour.visibility = if (maxHours > 0) { View.VISIBLE } else { View.GONE } // Hide the hours column if there are no hours to be displayed
            edtTimesHour.text = textTimesHours
            edtTimesMin.text = textTimesMin
            edtTimesSec.text = textTimesSec
            txtLastUpdated.text = "${resources.getString(R.string.updateLast)} ${SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().time)}"
            txtLastUpdated.setTextColor(resources.getColor(R.color.success))
            txtLastUpdated.alpha = 1f
            val oa = ObjectAnimator.ofFloat(txtLastUpdated, "alpha", 0.4f).setDuration(300)
            oa.startDelay = 300; oa.start()
        }

        // Usually the next update would be in 5 seconds
        var delayNextUpdate = 5000
        if (preferences.getBoolean("fastMode", false)) {
            // but when fastMode is enabled, the next update will be in 0.5 seconds
            delayNextUpdate = 500
        }
        // only force the update when nothing else has requested an update in the meantime (-> curUpdateTime would not be equal to nextUpdateConnections anymore)
        scheduleNextConnectionsUpdate(System.currentTimeMillis() + delayNextUpdate, curUpdateTime == nextUpdateConnections)
    }
    private fun ntUpdateSearch() {
        val edtSearch = findViewById<InstantAutoCompleteTextView>(R.id.edtSearch)

        try {
            var stations = emptyList<Station>()
            if (currentStationSearch.isNotEmpty()) {
                stations = transportStationAPI.getStations(currentStationSearch)
            }

            Handler(Looper.getMainLooper()).post {
                adapterSearch.clear()
                if (stations.isEmpty()) {
                    adapterSearch.notifyDataSetChanged()
                    edtSearch.dismissDropDown()
                    return@post
                }

                for (index in stations.indices) {
                    val stationName = stations[index].name
                    if (stationName == currentStationSearch) { clearFocus(); return@post }
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

    private fun ntUpdateNotifications() {
        var delayNextCheck = 10 * 60 * 1000
        when (ntUpdateNotificationText()) {
            1 -> { menuVisible[3] = true }  // Notification
            0 -> {                          // No error but also no notification
                menuVisible[3] = false
                Handler(Looper.getMainLooper()).post { findViewById<View>(R.id.layoutButtonAnnouncement).visibility = View.GONE }
            }
           -1 -> {                          // Error
               menuVisible[3] = false
               Handler(Looper.getMainLooper()).post { findViewById<View>(R.id.layoutButtonAnnouncement).visibility = View.GONE }
               delayNextCheck = 10 * 1000 // Something went wrong, check again in 10 seconds
           }
        }
        nextUpdateNotifications = System.currentTimeMillis() + delayNextCheck
    }
    private fun ntUpdateNotificationText() : Int {
        try {
            val urlBase = getString(R.string.urlNotification)
            if (urlBase.isEmpty()) { return 0 }
            val urlQuery = getString(R.string.urlNotificationQuery)
            val notification = JSONObject(URL(urlBase + urlQuery).readText())
            if (!notification.has("id")) { return -1 }
            currentNotification = notification

            if (notification.getString("id") == lastClosedNotification) { return 1 }
            return ntShowNotificationInternal()
        } catch (e: Exception) {
            val classification = classifyExceptionDefault(e)
            exceptions.add(ClassifiedException(e, classification))
        }
        return -1
    }
    @Suppress("DEPRECATION")
    private fun ntShowNotificationInternal() : Int {
        if (!currentNotification!!.has("title")) { return -1 }
        if (!currentNotification!!.has("text")) { return -1 }
        val nTitle = currentNotification?.getString("title")
        val nText = currentNotification?.getString("text")
        if (nTitle.isNullOrEmpty()) { return 0 }
        if (nText.isNullOrEmpty()) { return 0 }

        // Allow formatted notifications (such as html formatted)
        var nTextFormatted: Spanned = SpannedString(nText)
        if (currentNotification!!.has("html")) {
            val nHtml = currentNotification?.getString("html") ?: nText
            nTextFormatted =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) Html.fromHtml(nHtml, Html.FROM_HTML_MODE_LEGACY)
                else Html.fromHtml(nHtml)
        }

        val showNotificationUnit = {
            findViewById<TextView>(R.id.notificationTitleText).text = nTitle
            findViewById<TextView>(R.id.notificationText).text = nTextFormatted

            val notificationWindow = findViewById<View>(R.id.notificationWindow)
            notificationWindow.visibility = View.VISIBLE
            ObjectAnimator.ofFloat(notificationWindow, View.ALPHA, 1f).setDuration(100).start()
        }

        val mainLooper = Looper.getMainLooper()
        if (Looper.myLooper() == mainLooper) {
            showNotificationUnit()
        } else {
            Handler(mainLooper).post(showNotificationUnit)
        }

        return 1
    }
    private fun showNotification() {
        if (currentNotification == null) { Toast.makeText(this, R.string.notificationToastInvalid, Toast.LENGTH_SHORT).show(); return }
        if (!currentNotification!!.has("id")) { Toast.makeText(this, R.string.notificationToastInvalid, Toast.LENGTH_SHORT).show(); return }
        if (ntShowNotificationInternal() != 1) { Toast.makeText(this, R.string.notificationToastOtherError, Toast.LENGTH_SHORT).show(); return }
    }
    private fun closeNotification() {
        val notificationWindow = findViewById<View>(R.id.notificationWindow)
        Handler(Looper.getMainLooper()).post {
            ObjectAnimator.ofFloat(notificationWindow, View.ALPHA, 0f).setDuration(100).start()
        }
        notificationWindow.postDelayed({notificationWindow.visibility = View.GONE}, 100)

        if (currentNotification == null) { return }
        if (!currentNotification!!.has("id")) { return }

        val closedNotification = currentNotification!!.getString("id")
        if (closedNotification == "") { return }
        lastClosedNotification = closedNotification
        preferences.edit().putString("lastClosedNotification", lastClosedNotification).apply()
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
        catch (e: ActivityNotFoundException) { Toast.makeText(this, resources.getString(R.string.contactError), Toast.LENGTH_SHORT).show() }
    }
    private fun sendFeedback() {
        if (exceptions.isEmpty()) {
            sendFeedback(false)
            return
        }

        // If errors occurred, ask the user whether or not he wants to report it
        AlertDialog.Builder(this)
            .setTitle(R.string.sendLogsTitle)
            .setMessage(R.string.sendLogsText)
            .setIcon(android.R.drawable.ic_dialog_info)
            .setPositiveButton(R.string.sendLogsYes) { _, _ -> sendFeedback(true) }
            .setNegativeButton(R.string.sendLogsNo) { _, _ -> sendFeedback(false) }
            .show()
    }

    private fun toggleMenu(forceClose: Boolean = false) {
        val duration = 200L
        var delay = 0L; val delayStep = duration / 2

        if (menuOpened || forceClose) { // If open or forceClose -> close menu
            for (menuItem in menuItems.reversed()) {
                val oa = ObjectAnimator.ofFloat(menuItem, "alpha", 0f).setDuration(duration)
                oa.startDelay = delay; oa.start()
                menuItem.postDelayed({
                    if (menuOpened) { return@postDelayed } // if the menu got opened again don't hide the item
                    menuItem.visibility = View.GONE }, duration+delay)
                delay += delayStep
            }
            menuOpened = false
        } else { // If menu is closed and not forceClose -> open menu
            for (i in menuItems.indices) {
                if (!menuVisible[i]) { continue }
                val menuItem = menuItems[i]
                print("Opening: ")
                println(((menuItem as LinearLayout).children.first() as TextView).text)
                menuItem.visibility = View.VISIBLE
                val oa = ObjectAnimator.ofFloat(menuItem, "alpha", 1f).setDuration(duration)
                oa.startDelay = delay; oa.start()
                delay += delayStep
            }
            menuOpened = true
        }
    }

    private fun toggleLike() {
        val btnLike = findViewById<ImageButton>(R.id.btnLike)
        if (savedStations.contains(currentStation)) {
            savedStations.remove(currentStation)
            btnLike.setImageResource(R.drawable.ic_star)
        } else {
            savedStations.add(currentStation)
            btnLike.setImageResource(R.drawable.ic_star_filled)
        }

        preferences.edit().remove("savedStations").apply()
        preferences.edit().putStringSet("savedStations", savedStations).apply()
        updateBookmarks()
    }

    private fun toggleBookmarks() { toggleBookmarks(false) }
    private fun toggleBookmarks(forceClose: Boolean) {
        val duration = 100L
        val bookmarksLayout = findViewById<View>(R.id.layoutBookmarks)
        if (forceClose || bookmarksOpened) {
            ObjectAnimator.ofFloat(bookmarksLayout, View.ALPHA, 0f).setDuration(duration).start()
            bookmarksLayout.postDelayed({bookmarksLayout.visibility = View.GONE}, duration)
            bookmarksOpened = false
        } else {
            bookmarksLayout.visibility = View.VISIBLE
            ObjectAnimator.ofFloat(bookmarksLayout, View.ALPHA, 1f).setDuration(duration).start()
            bookmarksOpened = true
        }
    }
    @SuppressLint("SetTextI18n")
    private fun updateBookmarks() {
        val items = findViewById<LinearLayout>(R.id.bookmarksItems)
        items.removeAllViews()
        val txtEmpty = findViewById<TextView>(R.id.bookmarksEmpty)
        if (savedStations.isEmpty()) {
            txtEmpty.visibility = View.VISIBLE
            return
        }
        txtEmpty.visibility = View.GONE
        val inflater = LayoutInflater.from(this)
        for (savedStation in savedStations) {
            val root = inflater.inflate(R.layout.button_bookmark, items, false)
            val itemButton = root.findViewById<Button>(R.id.btnBookmarkItem)
            itemButton.text = " • $savedStation"
            itemButton.setOnClickListener { commitToStation(savedStation) }
            items.addView(itemButton)
        }
    }

    private fun commitToStation(stationName: String? = null) {
        val edtSearch = findViewById<TextView>(R.id.edtSearch)
        if (!stationName.isNullOrEmpty()) {
            edtSearch.text = stationName
        }
        currentStation = edtSearch.text.toString()
        preferences.edit().putString("station", currentStation).apply()
        findViewById<ImageButton>(R.id.btnLike).setImageResource(if (savedStations.contains(currentStation)) { R.drawable.ic_star_filled } else { R.drawable.ic_star })

        toggleBookmarks(true)
        updateConnections()
    }
    private fun scheduleNextConnectionsUpdate(next: Long, force: Boolean = false) {
        if ((next > nextUpdateConnections) && !force) { return }
        nextUpdateConnections = next
    }
    private fun updateConnections() {
        // schedule the next connection update to be now
        scheduleNextConnectionsUpdate(System.currentTimeMillis())
    }

    private fun clearFocus() {
        val edtSearch = findViewById<AutoCompleteTextView>(R.id.edtSearch)
        val focusLayout = findViewById<LinearLayout>(R.id.focusableLayout)
        edtSearch.dismissDropDown()
        focusLayout.requestFocus()
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(findViewById<View>(R.id.dividerSearch).windowToken, 0)
    }

    private fun classifyExceptionDefault(e: Exception) : String {
        if (e is java.net.UnknownHostException) {
            return "No internet connection"
        }
        return ""
    }
}