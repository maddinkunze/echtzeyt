package com.maddin.echtzeyt

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
import com.maddin.echtzeyt.components.FloatingInfoButton
import com.maddin.echtzeyt.components.InstantAutoCompleteTextView
import com.maddin.echtzeyt.randomcode.ActivityResultSerializable
import com.maddin.echtzeyt.randomcode.ClassifiedException
import com.maddin.transportapi.LocatableStation
import com.maddin.transportapi.RealtimeConnection
import com.maddin.transportapi.Station
import org.json.JSONObject
import java.lang.IndexOutOfBoundsException
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

private var mPreferencesName = ""
val EXAMPLE_API = com.maddin.transportapi.impl.EmptyAPI()
// val EXAMPLE_API = com.maddin.transportapi.impl.ExampleAPI() // uncomment this to test the app with mock data

@Suppress("FunctionName")
fun PREFERENCES_NAME(context: Context) : String {
    if (mPreferencesName.isEmpty()) { mPreferencesName = context.packageName }
    return mPreferencesName
}

abstract class EchtzeytActivity : AppCompatActivity() {
    // Internal variables about when the next search/update/... should happen
    private var isInForeground = false
    private var nextCheckForeground = 0L
    private var shouldUpdateSearch = false
    private var nextUpdateConnections = 0L
    private var nextUpdateNotifications = 0L
    private var currentStationSearch = ""

    // Elements/Views
    private val edtSearch by lazy { findViewById<InstantAutoCompleteTextView>(R.id.edtSearch) }

    // Menu variables
    private var menuOpened = false
    private val menuItems by lazy { intArrayOf(
        R.id.btnSettings,
        R.id.btnDonate,
        R.id.btnMessage,
        R.id.btnAnnouncement
    ).map { findViewById<View>(it) } }
    private val menuVisible by lazy { booleanArrayOf(
        true, // always show the settings icon
        resources.getString(R.string.urlSupportMe).isNotBlank(), // show the support button if a link is provided
        resources.getString(R.string.contactEmail).isNotBlank(), // show the contact/feedback button if an e-mail is provided
        false // always hide the notification button at first
    ) }
    protected val menuOpeningDuration = 160L
    protected val menuOpeningDelayStep = menuOpeningDuration / 2
    private var menuNextOpenAllowed = 0L

    // Bookmark variables
    private var bookmarksOpened = false

    private val preferences by lazy { getSharedPreferences(PREFERENCES_NAME(this), MODE_PRIVATE) }
    private var currentStationName = ""
    private var currentStation: Station? = null
    private var savedStations: MutableSet<String> = mutableSetOf()
    private val adapterSearch by lazy { ArrayAdapter<String>(this, R.layout.support_simple_spinner_dropdown_item) }
    protected abstract val transportStationAPI: com.maddin.transportapi.StationAPI
    protected abstract val transportRealtimeAPI: com.maddin.transportapi.RealtimeAPI

    // Everything related to updating widgets when the app is opened
    private var classesWidgets = mutableListOf<Class<*>>()

    // Everything related to other activities (such as the settings or a station selection map)
    protected abstract val activitySettings: Class<out SettingsActivity>
    protected open val activityMap: Class<out MapActivity>? = null
    private val activityMapLauncher by lazy { registerForActivityResult(ActivityResultSerializable<LocatableStation>(activityMap!!)) { commitToStation(it) } }

    // Notifications and exceptions
    private var currentNotification: JSONObject? = null
    private var lastClosedNotification = ""
    private var exceptions: MutableList<ClassifiedException> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //setAppLocale(this, "de")
        updateWidgets()
        setContentView(R.layout.activity_main)

        initVariables()
        initSettings()
        initHandlers()
        initApp()
        initThreads()
    }

    override fun onResume() {
        super.onResume()
        isInForeground = true
        nextUpdateConnections = 0
        updateWidgets()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        isInForeground = true
    }

    override fun onStop() {
        super.onStop()
        isInForeground = false
    }

    private fun updateWidgets() {
        for (widgetClass in classesWidgets) {
            val intent = Intent(this, widgetClass)
            intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            val widgetIds = AppWidgetManager.getInstance(application).getAppWidgetIds(ComponentName(application, widgetClass))
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)
            sendBroadcast(intent)
        }

    }

    protected fun addWidgetClass(cls: Class<*>) {
        classesWidgets.add(cls)
    }

    private fun initVariables() {
        for (menuItem in menuItems) {
            menuItem.alpha = 0f
        }

        // the launcher has to be registered before the activity has been started
        // by explicitly stating activityMapLauncher, we force lazy to initialize activityMapLauncher
        if (activityMap != null) { activityMapLauncher }

        isInForeground = true
    }
    private fun initSettings() {
        // Save last station?
        if (!preferences.contains("saveStation")) { preferences.edit().putBoolean("saveStation", true).apply() }
        if (preferences.getBoolean("saveStation", true)) { currentStationName = preferences.getString("station", "")!! }

        // Dark mode
        if (!preferences.contains("darkMode")) { preferences.edit().putBoolean("darkMode", AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES).apply() }
        if (!preferences.contains("autoDark")) { preferences.edit().putBoolean("autoDark", true).apply() }
        when {
            preferences.getBoolean("autoDark", true) -> { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) }
            preferences.getBoolean("darkMode", false) -> { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES) }
            else -> { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO) }
        }

        // Update interval (legacy code from when the user could choose between normal mode -> every 5s and fast mode -> every 1s)
        if (preferences.contains("fastMode")) { preferences.edit().putInt("updateEvery", if (preferences.getBoolean("fastMode", false)) { 1000 } else { 5000 }).remove("fastMode").apply() }
        if (!preferences.contains("updateEvery")) { preferences.edit().putInt("updateEvery", 5000).apply() }

        // Saved stations
        if (!preferences.contains("savedStations")) { preferences.edit().putStringSet("savedStations", savedStations).apply() }
        val savedStationsTemp = preferences.getStringSet("savedStations", savedStations)
        if (savedStationsTemp != null) { savedStations = savedStationsTemp }

        // Notifications (last closed notification -> do not show a notification that is already closed)
        if (!preferences.contains("lastClosedNotification")) { preferences.edit().putString("lastClosedNotification", lastClosedNotification).apply() }
        lastClosedNotification = preferences.getString("lastClosedNotification", lastClosedNotification).toString()
    }
    private fun initHandlers() {
        val btnSearch = findViewById<ImageButton>(R.id.btnSearch)
        val btnMap = findViewById<ImageButton>(R.id.btnMap)
        val btnLike = findViewById<ImageButton>(R.id.btnLike)
        val btnBookmarks = findViewById<ImageButton>(R.id.btnBookmarks)
        val btnMenu = findViewById<ImageButton>(R.id.btnMenu)
        val btnSettings = findViewById<FloatingInfoButton>(R.id.btnSettings).button
        val btnDonate = findViewById<FloatingInfoButton>(R.id.btnDonate).button
        val btnMessage = findViewById<FloatingInfoButton>(R.id.btnMessage).button
        val btnNotification = findViewById<FloatingInfoButton>(R.id.btnAnnouncement).button
        val btnNotificationClose = findViewById<ImageButton>(R.id.notificationButtonClose)

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

        // Toggle the (hamburger?) menu when clicking the menu button
        btnMenu.setOnClickListener { toggleMenu() }

        // Open settings when clicking the settings button
        btnSettings.setOnClickListener { toggleMenu(true); openSettings() }

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
                ntCheckIfInForeground()
                if (!isInForeground) {
                    Thread.sleep(500)
                    continue
                }

                val time = System.currentTimeMillis()
                if (time > nextUpdateConnections) { ntUpdateConnections() }
                Thread.sleep(50)
            }
        }

        // Notifications thread
        thread(start = true, isDaemon = true) {
            while (true) {
                ntCheckIfInForeground()
                if (!isInForeground) {
                    Thread.sleep(10000)
                    continue
                }
                val time = System.currentTimeMillis()
                if (time > nextUpdateNotifications) { ntUpdateNotifications() }
                Thread.sleep(50)
            }
        }
    }
    private fun initApp() {
        val edtSearch = findViewById<AutoCompleteTextView>(R.id.edtSearch)
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
        // Select all necessary views
        val curUpdateTime = nextUpdateConnections
        val edtSearch = findViewById<AutoCompleteTextView>(R.id.edtSearch)
        val edtNumbers = findViewById<TextView>(R.id.txtLineNumbers)
        val edtNames = findViewById<TextView>(R.id.txtLineNames)
        val edtTimesHour = findViewById<TextView>(R.id.txtLineTimesHour)
        val edtTimesMin = findViewById<TextView>(R.id.txtLineTimesMin)
        val edtTimesSec = findViewById<TextView>(R.id.txtLineTimesSec)
        val txtLastUpdated = findViewById<TextView>(R.id.txtLastUpdated)
        currentStationSearch = edtSearch.text.toString()

        val stops: List<RealtimeConnection>
        try {
            if ((currentStation == null) || (currentStation!!.name != currentStationSearch)) {
                val stations = transportStationAPI.searchStations(currentStationSearch)
                if (stations.isEmpty()) { return }
                currentStation = stations[0]
            }
            stops = transportRealtimeAPI.getRealtimeInformation(currentStation!!).connections
        } catch (e: Exception) {
            Handler(Looper.getMainLooper()).post {
                txtLastUpdated.setTextColor(resources.getColor(R.color.error))
                txtLastUpdated.alpha = 1f
                val oa = ObjectAnimator.ofFloat(txtLastUpdated, "alpha", 0.4f).setDuration(300)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) { oa.setAutoCancel(true) }
                oa.startDelay = 300; oa.start()
            }

            val classification = classifyExceptionDefault(e)
            exceptions.add(ClassifiedException(e, classification))

            // Error -> next connection update in 2 seconds
            updateConnectionsIn(1000, curUpdateTime == nextUpdateConnections)
            return
        }

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

        for (stop in stops) {
            departure = stop.departsIn().coerceAtLeast(0)
            depHours = departure.div(3600)
            depMin = departure.div(60).rem(60)
            depSec = departure.rem(60)
            textNumbers += "${stop.vehicle.line?.name}\n"
            textNames += "${stop.vehicle.direction?.name}\n"
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) { oa.setAutoCancel(true) }
            oa.startDelay = 300; oa.start()
        }

        // Usually the next update would be in 5 seconds
        val delayNextUpdate = preferences.getInt("updateEvery", 5000).toLong()
        // only force the update when nothing else has requested an update in the meantime (-> curUpdateTime would not be equal to nextUpdateConnections anymore)
        updateConnectionsIn(delayNextUpdate, curUpdateTime == nextUpdateConnections)
    }
    private fun ntUpdateSearch() {
        val edtSearch = findViewById<InstantAutoCompleteTextView>(R.id.edtSearch)
        try {
            currentStationSearch = edtSearch.text.toString()
        } catch (_: IndexOutOfBoundsException) { } // quite normal due to threading

        try {
            var stations = emptyList<Station>()
            if (currentStationSearch.isNotEmpty()) {
                stations = transportStationAPI.searchStations(currentStationSearch)
            }

            Handler(Looper.getMainLooper()).post {
                adapterSearch.clear()
                if (stations.isEmpty()) {
                    adapterSearch.notifyDataSetChanged()
                    edtSearch.dismissDropDown()
                    return@post
                }

                for (station in stations) {
                    val stationName = station.name
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

    private fun ntCheckIfInForeground() {
        val timeNow = System.currentTimeMillis()
        if (timeNow < nextCheckForeground) { return }

        val appProcessInfo = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(appProcessInfo)
        isInForeground = (appProcessInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) || (appProcessInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE)

        val delayNextUpdate = preferences.getInt("updateEvery", 5000)
        nextCheckForeground = timeNow + 2 * delayNextUpdate.coerceAtLeast(5_000)
    }

    private fun ntUpdateNotifications() {
        var delayNextCheck = 10 * 60 * 1000
        when (ntUpdateNotificationText()) {
            1 -> { menuVisible[3] = true }  // Notification
            0 -> {                          // No error but also no notification
                menuVisible[3] = false
                Handler(Looper.getMainLooper()).post { findViewById<View>(R.id.btnAnnouncement).visibility = View.GONE }
            }
           -1 -> {                          // Error
               menuVisible[3] = false
               Handler(Looper.getMainLooper()).post { findViewById<View>(R.id.btnAnnouncement).visibility = View.GONE }
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

            val oa = ObjectAnimator.ofFloat(notificationWindow, View.ALPHA, 1f).setDuration(100)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) { oa.setAutoCancel(true) }
            oa.start()
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
            val oa = ObjectAnimator.ofFloat(notificationWindow, View.ALPHA, 0f).setDuration(100)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) { oa.setAutoCancel(true) }
            oa.start()
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
        var delay = 0L; val now = System.currentTimeMillis()

        if (menuOpened || forceClose) { // If open or forceClose -> close menu
            for (menuItem in menuItems.reversed()) {
                val oa = ObjectAnimator.ofFloat(menuItem, "alpha", 0f).setDuration(menuOpeningDuration)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) { oa.setAutoCancel(true) }
                oa.addUpdateListener { (menuItem.parent as View).invalidate() }
                oa.startDelay = delay; oa.start()
                menuItem.postDelayed({
                    if (menuOpened) { return@postDelayed } // if the menu got opened again don't hide the item
                    menuItem.visibility = View.GONE }, menuOpeningDuration+delay)
                delay += menuOpeningDelayStep
            }
            menuOpened = false
            menuNextOpenAllowed = now + delay - menuOpeningDelayStep + menuOpeningDuration
        } else if (now >= menuNextOpenAllowed) { // If menu is closed and not forceClose -> open menu
            for (i in menuItems.indices) {
                if (!menuVisible[i]) { continue }
                val menuItem = menuItems[i]
                menuItem.visibility = View.VISIBLE
                val oa = ObjectAnimator.ofFloat(menuItem, "alpha", 1f).setDuration(menuOpeningDuration)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) { oa.setAutoCancel(true) }
                oa.addUpdateListener { (menuItem.parent as View).invalidate() }
                oa.startDelay = delay; oa.start()
                delay += menuOpeningDelayStep
            }
            menuOpened = true
        }
    }

    private fun toggleLike() {
        val btnLike = findViewById<ImageButton>(R.id.btnLike)
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
        val duration = 100L
        val bookmarksLayout = findViewById<View>(R.id.layoutBookmarks)
        if (forceClose || bookmarksOpened) {
            val oa = ObjectAnimator.ofFloat(bookmarksLayout, View.ALPHA, 0f).setDuration(duration)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) { oa.setAutoCancel(true) }
            oa.start()

            bookmarksLayout.postDelayed({
                if (bookmarksOpened) { return@postDelayed }
                bookmarksLayout.visibility = View.GONE }, duration)

            bookmarksOpened = false
        } else {
            bookmarksLayout.visibility = View.VISIBLE

            val oa = ObjectAnimator.ofFloat(bookmarksLayout, View.ALPHA, 1f).setDuration(duration)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) { oa.setAutoCancel(true) }
            oa.start()

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
        currentStationName = edtSearch.text.toString()
        preferences.edit().putString("station", currentStationName).apply()
        findViewById<ImageButton>(R.id.btnLike).setImageResource(if (savedStations.contains(currentStationName)) { R.drawable.ic_star_filled } else { R.drawable.ic_star })

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

    private fun updateConnectionsIn(deltaTime: Long, force: Boolean = false) {
        val now = System.currentTimeMillis()
        val next = (nextUpdateConnections + deltaTime).coerceIn(now, now + deltaTime)
        scheduleNextConnectionsUpdate(next, force)
    }

    private fun updateConnections() {
        // schedule the next connection update to be now
        scheduleNextConnectionsUpdate(0, true)
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

    protected fun openSettings() {
        startActivity(Intent().setClass(this, activitySettings))
    }

    protected fun openStationMap() {
        if (activityMap == null) { return }

        var station: LocatableStation? = null
        if (currentStation is LocatableStation) { station = currentStation as LocatableStation }

        activityMapLauncher.launch(station)
    }
}