package com.maddin.echtzeyt

import android.app.ActivityManager
import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.*
import android.text.Html
import android.text.Spanned
import android.text.SpannedString
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import com.maddin.echtzeyt.components.FloatingInfoButton
import com.maddin.echtzeyt.components.MenuTabLayout
import com.maddin.echtzeyt.fragments.MenuViewPagerAdapter
import com.maddin.echtzeyt.fragments.echtzeyt.RealtimeFragment
import com.maddin.echtzeyt.fragments.reduceDragSensitivity
import com.maddin.echtzeyt.randomcode.ClassifiedException
import org.json.JSONObject
import java.net.URL
import java.util.*
import kotlin.concurrent.thread


/*@Suppress("deprecation", "unused")
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
}*/

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
    }

    override fun onResume() {
        super.onResume()
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

}

class TestAdapterM(fragmentManager: FragmentManager, lifecycle: Lifecycle) : FragmentStateAdapter(fragmentManager, lifecycle) {
    private val fragments = arrayOf<Fragment>(
        RealtimeFragment()
    )

    override fun getItemCount(): Int {
        return fragments.size
    }

    override fun createFragment(position: Int): Fragment {
        return fragments[position]
    }

}

@Suppress("MemberVisibilityCanBePrivate")
open class EchtzeytActivity : EchtzeytForegroundActivity() {
    private var nextUpdateNotifications = 0L

    // Elements/Views
    private val btnMenu by lazy { findViewById<ImageButton>(R.id.btnMenu) }
    private val btnSettings by lazy { findViewById<FloatingInfoButton>(R.id.btnSettings) }
    private val btnDonate by lazy { findViewById<FloatingInfoButton>(R.id.btnDonate) }
    private val btnMessage by lazy { findViewById<FloatingInfoButton>(R.id.btnMessage) }
    private val btnNotification by lazy { findViewById<FloatingInfoButton>(R.id.btnAnnouncement) }
    private val btnNotificationClose by lazy { findViewById<ImageButton>(R.id.notificationButtonClose) }

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

        // PLAYGROUND:
        val pager = findViewById<ViewPager2>(R.id.fragmentsMain)
        pager.reduceDragSensitivity()
        val menu = findViewById<MenuTabLayout>(R.id.menutabsMain)
        val adapter = MenuViewPagerAdapter(supportFragmentManager, lifecycle, ECHTZEYT_CONFIGURATION.fragmentsView)
        pager.adapter = adapter

        if (adapter.itemCount < 2) {
            pager.isUserInputEnabled = false
            menu.visibility = View.GONE
        }

        TabLayoutMediator(menu, pager) { tab, position ->
            tab.setText(adapter.getFragmentNameResource(position))
        }.attach()
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
        btnDonate.setOnClickListener { toggleMenu(true); startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(resources.getString(R.string.urlSupportMe)))) }

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
                if (checkIfForeground.block(15000)) { continue }

                val time = System.currentTimeMillis()
                if (time > nextUpdateNotifications) { ntUpdateNotifications() }
                Thread.sleep(50)
            }
        }
    }
    private fun initApp() {
        findViewById<View>(R.id.notificationWindow).alpha = 0f
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
                runOnUiThread { findViewById<View>(R.id.btnAnnouncement).visibility = View.GONE }
            }
           -1 -> {                          // Error
               menuVisible[3] = false
               runOnUiThread { findViewById<View>(R.id.btnAnnouncement).visibility = View.GONE }
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

        runOnUiThread {
            findViewById<TextView>(R.id.notificationTitleText).text = nTitle
            findViewById<TextView>(R.id.notificationText).text = nTextFormatted

            val notificationWindow = findViewById<View>(R.id.notificationWindow)
            notificationWindow.visibility = View.VISIBLE

            notificationWindow.animate().alpha(1f).setDuration(100).start()
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
        runOnUiThread {
            ViewCompat.animate(notificationWindow).alpha(0f).setDuration(100).withEndAction {
                notificationWindow.visibility = View.GONE
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
        AlertDialog.Builder(this, R.style.Theme_Echtzeyt_AlertDialog)
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
}