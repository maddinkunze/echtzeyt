package com.maddin.echtzeyt.activities

import android.app.ActivityManager
import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.net.Uri
import android.os.*
import android.text.Html
import android.text.Spanned
import android.text.SpannedString
import android.view.View
import android.widget.*
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import com.maddin.echtzeyt.BuildConfig
import com.maddin.echtzeyt.ECHTZEYT_CONFIGURATION
import com.maddin.echtzeyt.R
import com.maddin.echtzeyt.components.FloatingInfoButton
import com.maddin.echtzeyt.components.MenuTabLayout
import com.maddin.echtzeyt.fragments.MenuViewPagerAdapter
import com.maddin.echtzeyt.fragments.reduceDragSensitivity
import com.maddin.echtzeyt.randomcode.ActivityViewpagerScrollable
import com.maddin.echtzeyt.randomcode.ClassifiedException
import com.maddin.echtzeyt.randomcode.LazyView
import org.json.JSONObject
import java.net.URL
import java.util.*
import kotlin.concurrent.thread


// val EXAMPLE_API = com.maddin.transportapi.impl.EmptyAPI() // this api should not be used anywhere
// val EXAMPLE_API = com.maddin.transportapi.impl.ExampleAPI() // uncomment this to test the app with mock data

abstract class EchtzeytForegroundActivity: AppCompatActivity() {
    private var nextCheckForeground = 0L
    protected var checkIfForeground = ConditionVariable()
    protected val preferences: SharedPreferences by lazy { ECHTZEYT_CONFIGURATION.preferences(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ECHTZEYT_CONFIGURATION.check() && BuildConfig.DEBUG) {
            throw IllegalStateException("EchteytConfiguration not loaded/filled! Please make sure you are calling ECHTZEYT_CONFIGURATION.load() before any activity or similar is created. One way to make ensure wanted behaviour is overriding tha default Application class, overriding the onCreate() method and calling ECHTZEYT_CONFIGURATION.load() from there.")
        }
        checkIfForeground.open()

        enableFullscreen()
    }

    override fun onResume() {
        super.onResume()
        enableFullscreen()
        checkIfForeground.open()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        checkIfForeground.open()
    }

    override fun onStop() {
        super.onStop()
        checkIfForeground.close()
    }

    protected fun ntCheckIfInForeground() {
        val timeNow = System.currentTimeMillis()
        if (timeNow < nextCheckForeground) { return }

        val isInForeground = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            val appProcessInfo = ActivityManager.RunningAppProcessInfo()
            ActivityManager.getMyMemoryState(appProcessInfo)
            (appProcessInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) || (appProcessInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE)
        } else {
            hasWindowFocus()
        }

        if (isInForeground) {
            checkIfForeground.open()
        } else {
            checkIfForeground.close()
        }

        val delayNextUpdate = preferences.getInt("updateEvery", 5000)
        nextCheckForeground = timeNow + 2 * delayNextUpdate.coerceAtLeast(5_000)
    }

    protected fun isInNightMode() : Boolean {
        return (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    protected open val useDarkStatusBarInLightMode by lazy { resources.getBoolean(R.bool.window_use_dark_status_bar_in_light_mode) }
    protected open fun enableFullscreen() {
        // use the dark status bar (i.e. white text) on some screens, especially where the status bar
        // has the theme color as a background (usually dark enough so white has a better contrast)
        if (useDarkStatusBarInLightMode) {
            enableEdgeToEdge(statusBarStyle=SystemBarStyle.dark(Color.TRANSPARENT))
        } else {
            enableEdgeToEdge()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateWindowInsets()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) { return }
        updateWindowInsets()
    }

    protected open fun updateWindowInsets() {
        updateWindowNavigationInsets()
        updateWindowStatusInsets()
    }

    protected abstract fun updateWindowNavigationInsets()
    protected abstract fun updateWindowStatusInsets()

    protected val notchHeight get() = ViewCompat.getRootWindowInsets(window.decorView)?.displayCutout?.safeInsetTop ?: 0
    protected val statusBarHeight get() = ViewCompat.getRootWindowInsets(window.decorView)?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0
    protected val navigationHeight get() = ViewCompat.getRootWindowInsets(window.decorView)?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
    protected val gesturesHeight get() = ViewCompat.getRootWindowInsets(window.decorView)?.getInsets(WindowInsetsCompat.Type.systemGestures())?.bottom ?: 0
    protected val usesGestures get() = ViewCompat.getRootWindowInsets(window.decorView)?.getInsets(WindowInsetsCompat.Type.systemGestures())?.let { it.left > 0 || it.right > 0 } ?: false
}


@Suppress("MemberVisibilityCanBePrivate")
open class EchtzeytActivity : EchtzeytForegroundActivity(), ActivityViewpagerScrollable {
    private var nextUpdateNotifications = 0L

    // Elements/Views
    private val btnMenu: ImageButton by LazyView(R.id.btnMenu)
    private val btnSettings: FloatingInfoButton by LazyView(R.id.btnSettings)
    private val btnDonate: FloatingInfoButton by LazyView(R.id.btnDonate)
    private val btnMessage: FloatingInfoButton by LazyView(R.id.btnMessage)
    private val btnNotification: FloatingInfoButton by LazyView(R.id.btnAnnouncement)
    private val btnNotificationClose: ImageButton by LazyView(R.id.notificationButtonClose)

    private val menu: MenuTabLayout by LazyView(R.id.menutabsMain)
    override val viewpager: ViewPager2 by LazyView(R.id.fragmentsMain)
    private val viewpagerAdapter by lazy { MenuViewPagerAdapter(supportFragmentManager, lifecycle, ECHTZEYT_CONFIGURATION.fragmentsView) }

    // Menu variables
    private var menuOpened = false
    private val menuItems by lazy { arrayOf<View>(
        btnSettings,
        btnDonate,
        btnMessage,
        btnNotification
    ) }
    private val menuVisible by lazy { booleanArrayOf(
        true, // always show the settings icon
        resources.getString(R.string.urlSupportMe).isNotBlank(), // show the support button if a link is provided
        resources.getString(R.string.contactEmail).isNotBlank(), // show the contact/feedback button if an e-mail is provided
        false // always hide the notification button at first
    ) }
    protected val menuOpeningDuration = 160L
    protected val menuOpeningDelayStep = menuOpeningDuration / 2
    private var menuNextOpenAllowed = 0L

    // Everything related to updating widgets when the app is opened
    // TODO: maybe move this to the android Application class?
    //  then widgets could be updated as soon as the application gets some sort of attention, not only when the main activity starts

    private val classesWidgets: List<Class<*>> by lazy { arrayOf(
        ECHTZEYT_CONFIGURATION.widgetRealtimeClass
    ).filterNotNull() }

    // Everything related to other activities (such as the settings or a station selection map)
    protected val activitySettings by lazy { ECHTZEYT_CONFIGURATION.activitySettings }

    // Notifications and exceptions
    private var currentNotification: JSONObject? = null
    private var lastClosedNotification = ""
    private var exceptions: MutableList<ClassifiedException> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
        updateWidgets()
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

    private fun initVariables() {
        for (menuItem in menuItems) {
            menuItem.alpha = 0f
        }

        viewpager.reduceDragSensitivity()
        viewpager.adapter = viewpagerAdapter
        if (viewpagerAdapter.itemCount < 2) { viewpager.isUserInputEnabled = false; menu.visibility = View.GONE }
        TabLayoutMediator(menu, viewpager) { tab, position -> tab.setText(viewpagerAdapter.getFragmentNameResource(position)) }.attach()
    }
    private fun initSettings() {
        // Dark mode
        if (!preferences.contains("darkMode")) { preferences.edit().putBoolean("darkMode", isInNightMode()).apply() }
        if (!preferences.contains("autoDark")) { preferences.edit().putBoolean("autoDark", true).apply() }

        // Notifications (last closed notification -> do not show a notification that is already closed)
        if (!preferences.contains("lastClosedNotification")) { preferences.edit().putString("lastClosedNotification", lastClosedNotification).apply() }
        lastClosedNotification = preferences.getString("lastClosedNotification", lastClosedNotification).toString()
    }
    private fun initHandlers() {
        // Toggle the (hamburger?) menu when clicking the menu button
        btnMenu.setOnClickListener { toggleMenu() }

        // Open settings when clicking the settings button
        btnSettings.setOnClickListener { toggleMenu(true); openSettings() }

        // Open the support/donation link when clicking the donation button
        btnDonate.setOnClickListener { toggleMenu(true); startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(resources.getString(
            R.string.urlSupportMe
        )))) }

        // Start the feedback process when clicking the feedback button
        btnMessage.setOnClickListener { toggleMenu(true); sendFeedback() }

        // Open/close the current notification (if there is one) when clicking the associated buttons are clicked
        btnNotification.setOnClickListener { toggleMenu(true); showNotification() }
        btnNotificationClose.setOnClickListener { closeNotification() }
    }
    private fun initThreads() {
        // Theoretically these threads could be combined into one, however this can be laggy, especially on older hardware

        // Notifications thread
        thread(start=true, isDaemon=true) {
            while (true) {
                ntCheckIfInForeground()
                if (!checkIfForeground.block(15000)) { continue }

                val time = System.currentTimeMillis()
                if (time > nextUpdateNotifications) { ntUpdateNotifications() }
                Thread.sleep(500)
            }
        }
    }
    private fun initApp() {
        viewNotification.alpha = 0f
    }

    /*
      Update all the connections for the currently selected station
     */

    private fun ntUpdateNotifications() {
        var delayNextCheck = 10 * 60 * 1000
        when (ntUpdateNotificationText()) {
            1 -> { menuVisible[3] = true }  // Notification
            0 -> {                          // No error but also no notification
                menuVisible[3] = false
                runOnUiThread { btnNotification.visibility = View.GONE }
            }
           -1 -> {                          // Error
               menuVisible[3] = false
               runOnUiThread { btnNotification.visibility = View.GONE }
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

    private val viewNotification: View by LazyView(R.id.notificationWindow)
    private val txtNotificationTitle: TextView by LazyView(R.id.notificationTitleText)
    private val txtNotificationText: TextView by LazyView(R.id.notificationText)

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

        runOnUiThread {
            txtNotificationTitle.text = nTitle
            txtNotificationText.text = nTextFormatted

            viewNotification.visibility = View.VISIBLE
            viewNotification.animate().alpha(1f).setDuration(100).start()
        }

        return 1
    }
    private fun showNotification() {
        if (currentNotification == null) { Toast.makeText(this,
            R.string.notificationToastInvalid, Toast.LENGTH_SHORT).show(); return }
        if (!currentNotification!!.has("id")) { Toast.makeText(this,
            R.string.notificationToastInvalid, Toast.LENGTH_SHORT).show(); return }
        if (ntShowNotificationInternal() != 1) { Toast.makeText(this,
            R.string.notificationToastOtherError, Toast.LENGTH_SHORT).show(); return }
    }
    private fun closeNotification() {
        runOnUiThread {
            ViewCompat.animate(viewNotification).alpha(0f).setDuration(100).withEndAction {
                viewNotification.visibility = View.GONE
            }.start()
        }

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
        AlertDialog.Builder(this, R.style.Theme_Echtzeyt_Dialog_Alert)
            .setTitle(R.string.sendLogsTitle)
            .setMessage(R.string.sendLogsText)
            .setIcon(R.drawable.ic_error)
            .setPositiveButton(R.string.sendLogsYes) { _, _ -> sendFeedback(true) }
            .setNegativeButton(R.string.sendLogsNo) { _, _ -> sendFeedback(false) }
            .show()
    }

    private fun toggleMenu(forceClose: Boolean = false) {
        var delay = 0L; val now = System.currentTimeMillis()

        if (menuOpened || forceClose) { // If open or forceClose -> close menu
            for (menuItem in menuItems.reversed()) {
                ViewCompat.animate(menuItem)
                    .alpha(0f)
                    .setDuration(menuOpeningDuration)
                    .withEndAction {
                        if (menuOpened) { return@withEndAction }
                        menuItem.visibility = View.GONE
                    }.setStartDelay(delay).setUpdateListener { it.invalidate() }
                    .start()
                delay += menuOpeningDelayStep
            }
            menuOpened = false
            menuNextOpenAllowed = now + delay - menuOpeningDelayStep + menuOpeningDuration
        } else if (now >= menuNextOpenAllowed) { // If menu is closed and not forceClose -> open menu
            for (i in menuItems.indices) {
                if (!menuVisible[i]) { continue }
                val menuItem = menuItems[i]
                menuItem.visibility = View.VISIBLE
                ViewCompat.animate(menuItem)
                    .alpha(1f)
                    .setDuration(menuOpeningDuration)
                    .setStartDelay(delay)
                    .setUpdateListener { it.invalidate() }
                    .start()
                delay += menuOpeningDelayStep
            }
            menuOpened = true
        }
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

    private val spaceNavbar: View by LazyView(R.id.fillerNavbar)
    override fun updateWindowNavigationInsets() {
        val navHeight = navigationHeight
        if (navHeight <= 0) { return }
        if (usesGestures) { return }
        spaceNavbar.updateLayoutParams { height = navHeight }
    }

    private val spaceStatus: View by LazyView(R.id.fillerStatus)
    override fun updateWindowStatusInsets() {
        val statusHeight = statusBarHeight
        if (statusHeight <= 0) { return }
        spaceStatus.updateLayoutParams { height = statusHeight }
    }
}