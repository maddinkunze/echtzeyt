package com.maddin.echtzeyt

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RemoteViews
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.edit
import androidx.core.content.res.ResourcesCompat
import com.maddin.echtzeyt.components.InstantAutoCompleteTextView
import com.maddin.echtzeyt.components.LabeledDiscreteSeekBar
import com.maddin.transportapi.RealtimeAPI
import com.maddin.transportapi.RealtimeInfo
import com.maddin.transportapi.Station
import com.maddin.transportapi.StationAPI
import java.time.LocalDateTime
import kotlin.concurrent.thread


private data class WidgetInformation(val widgetId: Int, val context: Context, val firstUpdate: Long, var nextUpdate: Long, val deltaUpdate: Long, val durationActive: Long, var nextAlive: Long, var stepsAlive: Int, val stationName: String, var station: Station?)


open class EchtzeytWidget : AppWidgetProvider() {
    companion object {
        @Volatile private var threadLastActive: Long = 0
        @Volatile private var threadCurrentId: Int = 0
        @Volatile private var threadLastDeniedReload: Long = 0
        private var transportStationAPI: StationAPI = EXAMPLE_API
        private var transportRealtimeAPI: RealtimeAPI = EXAMPLE_API
        @Volatile private var widgetInformation = mutableMapOf<Int, WidgetInformation>()
        const val intentWidgetId = "com.maddin.echtzeyt.widget.INTENT_WIDGET_ID"
        const val intentUpdateWidget = "com.maddin.echtzeyt.widget.UPDATE"
        const val intentStopWidget = "com.maddin.echtzeyt.widget.STOP"
    }

    override fun onAppWidgetOptionsChanged(context: Context?, appWidgetManager: AppWidgetManager?, widgetId: Int, newOptions: Bundle?) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, widgetId, newOptions)
        loadWidget(context, widgetId)
        updateThreads()
    }

    override fun onDeleted(context: Context?, widgetIds: IntArray?) {
        super.onDeleted(context, widgetIds)
        if (widgetIds == null) { return }
        for (widgetId in widgetIds) {
            unloadWidget(widgetId)
        }
    }

    override fun onDisabled(context: Context?) {
        super.onDisabled(context)
        threadCurrentId++
        widgetInformation.clear()
    }

    override fun onUpdate(context: Context?, appWidgetManager: AppWidgetManager?, widgetIds: IntArray?) {
        super.onUpdate(context, appWidgetManager, widgetIds)
        for (widgetId in widgetIds ?: intArrayOf()) {
            loadWidget(context, widgetId)
        }
        updateThreads(true)
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        super.onReceive(context, intent)
        if (intent == null) { return }

        if (intent.action == intentUpdateWidget) {
            val widgetId = intent.getIntExtra(intentWidgetId, AppWidgetManager.INVALID_APPWIDGET_ID)
            var force = widgetInformation.isEmpty()
            loadWidget(context, widgetId, LOAD_FLAG_LAZY or LOAD_FLAG_START_RUNNING)
            updateWidget(widgetId)
            val now = System.currentTimeMillis()
            if (now - threadLastDeniedReload < 5_000) { force = true }
            updateThreads(force)
        }

        if (intent.action == intentStopWidget) {
            val widgetId = intent.getIntExtra(intentWidgetId, AppWidgetManager.INVALID_APPWIDGET_ID)
            unloadWidget(widgetId)
        }
    }

    override fun onRestored(context: Context?, oldWidgetIds: IntArray?, newWidgetIds: IntArray?) {
        super.onRestored(context, oldWidgetIds, newWidgetIds)
        if (context == null) { return }
        if (oldWidgetIds == null) { return }
        if (newWidgetIds == null) { return }
        for (i in oldWidgetIds.indices) {
            migrateConfiguration(context, oldWidgetIds[i], newWidgetIds[i])
            unloadWidget(oldWidgetIds[i])
            loadWidget(context, newWidgetIds[i])
        }
    }

    private val LOAD_FLAG_LAZY = 1
    private val LOAD_FLAG_START_RUNNING = 2
    private fun loadWidget(context: Context?, widgetId: Int, flags: Int = 0) {
        if (context == null) { return }
        if (widgetInformation.contains(widgetId) && (flags.and(LOAD_FLAG_LAZY) > 0)) {
            return
        }

        val lastWidgetInfo = widgetInformation[widgetId]
        val preferences = openConfiguration(context, widgetId)
        val stationName = preferences.getString("station", lastWidgetInfo?.stationName ?: "")
        if (stationName.isNullOrBlank()) {
            unloadWidget(widgetId)
            loadWidgetContent(context, widgetId, null)
            return
        }
        val durationActive = preferences.getInt("runUntil", 120).toLong() * 1000L
        val updateEvery = preferences.getInt("runEvery", (lastWidgetInfo?.deltaUpdate ?: 5000).toInt()).toLong()
        var lastStation = lastWidgetInfo?.station
        if (lastStation?.name != stationName) { lastStation = null }
        val stepsAlive = lastWidgetInfo?.stepsAlive ?: 0
        val widgetInfo = WidgetInformation(widgetId, context, System.currentTimeMillis(), 0, updateEvery, durationActive, 0, stepsAlive, stationName, lastStation)

        if ((lastWidgetInfo != null) || (flags.and(LOAD_FLAG_START_RUNNING) > 0)) {
            widgetInformation[widgetId] = widgetInfo
        } else {
            widgetInformation.remove(widgetId)
        }
        loadWidgetContent(context, widgetId, widgetInfo)
    }

    private fun loadWidgetContent(contextOpt: Context?, widgetId: Int, widgetInfo: WidgetInformation?) {
        val context = contextOpt ?: widgetInfo?.context ?: return
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val remoteViews = RemoteViews(context.packageName, R.layout.widget_echtzeyt)

        val updateIntent = Intent(context, javaClass)
        updateIntent.action = intentUpdateWidget
        updateIntent.putExtra(intentWidgetId, widgetId)
        remoteViews.setOnClickPendingIntent(R.id.btnWidgetReload, PendingIntent.getBroadcast(context, widgetId, updateIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))

        val stopIntent = Intent(context, javaClass)
        stopIntent.action = intentStopWidget
        stopIntent.putExtra(intentWidgetId, widgetId)
        remoteViews.setOnClickPendingIntent(R.id.btnWidgetStop, PendingIntent.getBroadcast(context, widgetId, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))

        remoteViews.setViewVisibility(R.id.layoutButtonWidgetStop, View.GONE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            remoteViews.setViewOutlinePreferredRadiusDimen(R.id.layoutWidget, R.dimen.widget_corner_radius)
            remoteViews.setBoolean(R.id.layoutWidget, "setClipToOutline", true)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // val test = RemoteViewOutlineProvider.BACKGROUND
            // TODO: get this to actually work (clip everything inside the widget to the outline of the widget)
            remoteViews.setBoolean(R.id.layoutWidget, "setClipToOutline", true)
        }

        if (widgetInfo == null) {
            remoteViews.setViewVisibility(R.id.layoutButtonWidgetReload, View.GONE)
            remoteViews.setTextViewText(R.id.txtWidgetStationName, context.resources.getText(R.string.widgetNoStation))
            appWidgetManager.partiallyUpdateAppWidget(widgetId, remoteViews)
            return
        }

        remoteViews.setViewVisibility(R.id.layoutButtonWidgetReload, View.VISIBLE)
        if (widgetInformation.contains(widgetId)) {
            remoteViews.setViewVisibility(R.id.layoutButtonWidgetStop, View.VISIBLE)
            setWidgetStillAliveInternal(remoteViews, widgetInfo)
        }
        remoteViews.setTextViewText(R.id.txtWidgetStationName, widgetInfo.stationName)

        appWidgetManager.partiallyUpdateAppWidget(widgetId, remoteViews)
    }

    private fun updateWidget(widgetId: Int) {
        if (!widgetInformation.contains(widgetId)) { return }
        val widgetInfo = widgetInformation[widgetId] ?: return
        widgetInfo.nextUpdate = 0
    }
    private fun ntUpdateWidget(widgetId: Int) {
        if (!widgetInformation.contains(widgetId)) { return }
        val widgetInfo = widgetInformation[widgetId] ?: return
        val now = System.currentTimeMillis()
        if (widgetInfo.nextUpdate > now) {
            setWidgetStillAlive(widgetInfo)
            return
        }
        if ((widgetInfo.durationActive > 0) && (now - widgetInfo.firstUpdate > widgetInfo.durationActive)) {
            unloadWidget(widgetId)
            return
        }

        if ((widgetInfo.station == null) || (widgetInfo.station!!.name != widgetInfo.stationName)) {
            var stations = emptyList<Station>()
            try {
                stations = transportStationAPI.getStations(widgetInfo.stationName)
            } catch (_: Exception) {}

            if (stations.isEmpty()) {
                widgetInfo.station = null
                setWidgetInfoNextUpdate(widgetInfo, 1000) // something went wrong, try to update in a second again
                setWidgetLastUpdate(widgetInfo, false)
                return
            }
            widgetInfo.station = stations[0]
        }

        var connections: RealtimeInfo? = null
        try {
            connections = transportRealtimeAPI.getRealtimeInformation(widgetInfo.station!!)
        } catch(_: Exception) {}

        if (connections == null) {
            setWidgetInfoNextUpdate(widgetInfo, 1000) // something went wrong, try to update in a second again
            setWidgetLastUpdate(widgetInfo, false)
            return
        }

        var txtLineNumbers = ""
        var txtLineNames = ""
        var txtTimeHours = ""
        var txtTimeMinutes = ""
        var txtTimeSeconds = ""
        for (connection in connections.connections) {
            txtLineNumbers += "${connection.vehicle.name}\n"
            txtLineNames += "${connection.vehicle.directionName}\n"
            val timeDepart = connection.departsIn()
            val departHour = timeDepart.div(3600)
            val departMinute = timeDepart.div(60).mod(60)
            var departMinutePad = 0
            val departSecond = timeDepart.mod(60)
            if (departHour > 0) { txtTimeHours += "$departHour"; txtTimeMinutes += ":"; departMinutePad = 2 }
            txtTimeHours += "\n"
            txtTimeMinutes += "${departMinute.toString().padStart(departMinutePad, '0')}\n"
            txtTimeSeconds += ":${departSecond.toString().padStart(2, '0')}\n"
        }

        val appWidgetManager = AppWidgetManager.getInstance(widgetInfo.context)
        val remoteViews = RemoteViews(widgetInfo.context.packageName, R.layout.widget_echtzeyt)
        remoteViews.setTextViewText(R.id.txtWidgetLineNumbers, txtLineNumbers)
        remoteViews.setTextViewText(R.id.txtWidgetLineNames, txtLineNames)
        remoteViews.setTextViewText(R.id.txtWidgetTimesHours, txtTimeHours)
        remoteViews.setTextViewText(R.id.txtWidgetTimesMinutes, txtTimeMinutes)
        remoteViews.setTextViewText(R.id.txtWidgetTimesSeconds, txtTimeSeconds)
        setWidgetLastUpdateInternal(remoteViews, widgetInfo.context, true)
        setWidgetStillAliveInternal(remoteViews, widgetInfo)
        appWidgetManager.partiallyUpdateAppWidget(widgetInfo.widgetId, remoteViews)
        setWidgetInfoNextUpdate(widgetInfo)
    }

    private fun setWidgetInfoNextUpdate(widgetInfo: WidgetInformation, deltaUpdate: Long) {
        val now = System.currentTimeMillis()
        widgetInfo.nextUpdate = (widgetInfo.nextUpdate + deltaUpdate).coerceAtLeast(now)
    }

    private fun setWidgetInfoNextUpdate(widgetInfo: WidgetInformation) {
        setWidgetInfoNextUpdate(widgetInfo, widgetInfo.deltaUpdate)
    }

    private fun setWidgetStillAliveInternal(remoteViews: RemoteViews, widgetInfo: WidgetInformation) {
        val stepAngle = 30
        val currentAngle = (widgetInfo.stepsAlive++ * stepAngle).mod(360)

        val drawable = ResourcesCompat.getDrawable(widgetInfo.context.resources, R.drawable.ic_reload, null) ?: return
        val sizeBitmap = (TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10f, widgetInfo.context.resources.displayMetrics) +
                widgetInfo.context.resources.getDimension(R.dimen.widget_toolbar_text_size) +
                widgetInfo.context.resources.getDimension(R.dimen.widget_toolbar_title_size)).toInt()
        val bmpRotated = Bitmap.createBitmap(sizeBitmap, sizeBitmap, Bitmap.Config.ARGB_8888)
        val cvsRotated = Canvas(bmpRotated)
        cvsRotated.rotate(currentAngle.toFloat(), sizeBitmap / 2.toFloat(), sizeBitmap / 2.toFloat())
        drawable.setBounds(0, 0, sizeBitmap, sizeBitmap)
        drawable.draw(cvsRotated)
        remoteViews.setImageViewBitmap(R.id.btnWidgetReload, bmpRotated)

        widgetInfo.nextAlive = System.currentTimeMillis() + 1000
    }

    private fun setWidgetStillAlive(widgetInfo: WidgetInformation) {
        if (System.currentTimeMillis() < widgetInfo.nextAlive) { return }
        val appWidgetManager = AppWidgetManager.getInstance(widgetInfo.context)
        val remoteViews = RemoteViews(widgetInfo.context.packageName, R.layout.widget_echtzeyt)
        setWidgetStillAliveInternal(remoteViews, widgetInfo)
        appWidgetManager.partiallyUpdateAppWidget(widgetInfo.widgetId, remoteViews)
    }

    private fun setWidgetLastUpdateInternal(remoteViews: RemoteViews, context: Context, success: Boolean) {
        val timeNow = LocalDateTime.now()
        var updateColor = AppCompatResources.getColorStateList(context, R.color.errorWidget).defaultColor
        if (success) {
            //val updateLast = context.resources.getString(R.string.updateLast)
            val updateText = "${timeNow.hour.toString().padStart(2, '0')}:${timeNow.minute.toString().padStart(2, '0')}:${timeNow.second.toString().padStart(2, '0')}"
            remoteViews.setTextViewText(R.id.txtWidgetUpdate, updateText)
            updateColor = AppCompatResources.getColorStateList(context, R.color.successWidget).defaultColor
        }
        remoteViews.setTextColor(R.id.txtWidgetUpdate, updateColor)
    }

    @Suppress("SameParameterValue")
    private fun setWidgetLastUpdate(widgetInfo: WidgetInformation, success: Boolean = true) {
        val appWidgetManager = AppWidgetManager.getInstance(widgetInfo.context)
        val remoteViews = RemoteViews(widgetInfo.context.packageName, R.layout.widget_echtzeyt)
        setWidgetLastUpdateInternal(remoteViews, widgetInfo.context, success)
        setWidgetStillAliveInternal(remoteViews, widgetInfo)
        appWidgetManager.partiallyUpdateAppWidget(widgetInfo.widgetId, remoteViews)
    }

    private fun unloadWidget(widgetId: Int) {
        if (!widgetInformation.contains(widgetId)) { return }
        val widgetInfo = widgetInformation[widgetId] ?: return
        widgetInformation.remove(widgetId)

        val appWidgetManager = AppWidgetManager.getInstance(widgetInfo.context)
        val remoteViews = RemoteViews(widgetInfo.context.packageName, R.layout.widget_echtzeyt)
        remoteViews.setImageViewResource(R.id.btnWidgetReload, R.drawable.ic_play)
        remoteViews.setViewVisibility(R.id.layoutButtonWidgetStop, View.GONE)
        setWidgetLastUpdateInternal(remoteViews, widgetInfo.context, false)
        appWidgetManager.partiallyUpdateAppWidget(widgetId, remoteViews)
    }

    private fun openConfiguration(context: Context, widgetId: Int) : SharedPreferences {
        val configName = "${context.packageName}.widget.${widgetId}"
        return context.getSharedPreferences(configName, AppCompatActivity.MODE_PRIVATE)
    }

    private fun migrateConfiguration(context: Context, oldId: Int, newId: Int) {
        val oldPrefs = openConfiguration(context, oldId)
        val newPrefs = openConfiguration(context, newId)
        newPrefs.edit {
            for ((key, value) in oldPrefs.all) {
                if (value is Int) putInt(key, value)
                if (value is Long) putLong(key, value)
                if (value is Float) putFloat(key, value)
                if (value is String) putString(key, value)
                /*if (value is MutableSet<*>) { TODO: migrate string sets (although not really needed at the time)
                    val listValue = value.toList()
                    if (listValue[0] is String) putStringSet(key, listValue as MutableSet<String>)
                }*/
            }
            apply()
        }
    }

    private fun updateThreads(force: Boolean = false) {
        val timeNow = System.currentTimeMillis()
        if ((timeNow - threadLastActive < 30_000) && !force) {
            threadLastDeniedReload = timeNow
            return
        }

        val asyncDelay = 1000L
        if (threadCurrentId < 0) { threadCurrentId = 0 }
        val threadThisId = ++threadCurrentId
        thread(start = true, isDaemon = false) {
            while (true) {
                if (widgetInformation.isEmpty() || (threadCurrentId < 0) || (threadThisId < threadCurrentId)) { threadLastActive = 0; return@thread }
                val start = System.currentTimeMillis()
                for (widgetId in widgetInformation.keys) {
                    ntUpdateWidget(widgetId)
                }

                val end = System.currentTimeMillis()
                threadLastActive = end
                val nextDelay = (asyncDelay - (end-start)).coerceIn(0, asyncDelay) // if all updates took 200ms, wait another 800ms
                Thread.sleep(nextDelay)
            }
        }
    }

    protected fun <API> setTransportAPI(transportAPI: API) where API:StationAPI, API: RealtimeAPI {
        transportStationAPI = transportAPI
        transportRealtimeAPI = transportAPI
    }
}

open class EchtzeytWidgetConfigureActivity : AppCompatActivity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var adapterSearch: ArrayAdapter<String>
    private var transportStationAPI: StationAPI = EXAMPLE_API
    private var shouldUpdateSearch = false
    private lateinit var preferences: SharedPreferences
    private var runUntilOptions = mutableMapOf<Int, String>()
    private var runEveryOptions = mutableMapOf<Int, String>()
    protected var widgetClass: Class<*> = EchtzeytWidget::class.java // TODO: change to Class<*>? and default to null since the EchtzeytWidget class will usually not be exposed directly

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_widget_echtzeyt_configure)

        initVariables()
        initHandlers()
        initConfiguration()
        initThreads()
    }

    private fun initVariables() {
        setResult(RESULT_CANCELED)

        val settingsTitle = "${resources.getString(R.string.widgetName)} - ${resources.getString(R.string.widgetNameSettings)}"
        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbarWidgetConfiguration).title = settingsTitle

        val namesRU = resources.getStringArray(R.array.widgetConfigureRunUntilOptionNames)
        val valuesRU = resources.getIntArray(R.array.widgetConfigureRunUntilOptionValues)
        for (i in valuesRU.indices) { runUntilOptions[valuesRU[i]] = namesRU[i] }
        findViewById<LabeledDiscreteSeekBar>(R.id.selectRunUntilWidgetConfiguration).setItems(namesRU)

        val namesRE = resources.getStringArray(R.array.widgetConfigureRunEveryOptionNames)
        val valuesRE = resources.getIntArray(R.array.widgetConfigureRunEveryOptionValues)
        for (i in valuesRU.indices) { runEveryOptions[valuesRE[i]] = namesRE[i] }
        findViewById<LabeledDiscreteSeekBar>(R.id.selectRunEveryWidgetConfiguration).setItems(namesRE)

        adapterSearch = ArrayAdapter(this, R.layout.support_simple_spinner_dropdown_item)
        val edtSearch = findViewById<InstantAutoCompleteTextView>(R.id.edtWidgetConfigurationSearch)
        edtSearch.setAdapter(adapterSearch)

        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // If this activity was started with an intent without an app widget ID, finish with an error.
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val configName = "${packageName}.widget.${widgetId}"
        preferences = getSharedPreferences(configName, MODE_PRIVATE)
    }

    private fun sendWidgetUpdateBroadcast() {
        val intent = Intent(this, widgetClass)
        intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        val ids = intArrayOf(widgetId)
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        sendBroadcast(intent)
    }

    private fun initHandlers() {
        findViewById<ImageButton>(R.id.btnWidgetConfigurationSave).setOnClickListener {
            saveConfiguration()
            val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            setResult(RESULT_OK, resultValue)
            finish()
        }

        val edtSearch = findViewById<InstantAutoCompleteTextView>(R.id.edtWidgetConfigurationSearch)
        edtSearch.addOnTextChangedListener { shouldUpdateSearch = true }
        edtSearch.addOnItemSelectedListener { clearFocus() }
    }

    private fun initThreads() {
        thread(start = true, isDaemon = true) {
            while (true) {
                if (shouldUpdateSearch) {
                    ntUpdateSearch()
                }

                Thread.sleep(20)
            }
        }
    }

    private fun initConfiguration() {
        val stationName = preferences.getString("station", "")
        val runUntil = preferences.getInt("runUntil", 120)
        val runEvery = preferences.getInt("runEvery", 5000)

        findViewById<InstantAutoCompleteTextView>(R.id.edtWidgetConfigurationSearch).setText(stationName)
        val runUntilSelect = findViewById<LabeledDiscreteSeekBar>(R.id.selectRunUntilWidgetConfiguration)
        val runUntilIndex = runUntilOptions.keys.indexOf(runUntil)
        runUntilSelect.progress = if (runUntilIndex < 0) { 0 } else { runUntilIndex }
        val runEverySelect = findViewById<LabeledDiscreteSeekBar>(R.id.selectRunEveryWidgetConfiguration)
        val runEveryIndex = runEveryOptions.keys.indexOf(runEvery)
        runEverySelect.progress = if (runEveryIndex < 0) { 0 } else { runEveryIndex }
    }

    private fun saveConfiguration() {
        val stationName = findViewById<EditText>(R.id.edtWidgetConfigurationSearch).text.toString()
        val runUntilIndex = findViewById<LabeledDiscreteSeekBar>(R.id.selectRunUntilWidgetConfiguration).progress.coerceIn(0, runUntilOptions.size-1)
        val runUntil = runUntilOptions.keys.toList()[runUntilIndex]
        val runEveryIndex = findViewById<LabeledDiscreteSeekBar>(R.id.selectRunEveryWidgetConfiguration).progress.coerceIn(0, runEveryOptions.size-1)
        val runEvery = runEveryOptions.keys.toList()[runEveryIndex]
        preferences.edit {
            putString("station", stationName)
            putInt("runUntil", runUntil)
            putInt("runEvery", runEvery)
            apply()
        }
        sendWidgetUpdateBroadcast()
    }

    protected fun setStationAPI(stationAPI: StationAPI) {
        transportStationAPI = stationAPI
    }

    private fun ntUpdateSearch() {
        val edtSearch = findViewById<InstantAutoCompleteTextView>(R.id.edtWidgetConfigurationSearch)
        val stationSearch = edtSearch.text.toString()

        try {
            var stations = emptyList<Station>()
            if (stationSearch.isNotEmpty()) {
                stations = transportStationAPI.getStations(stationSearch)
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
                    if (stationName == stationSearch) { clearFocus(); return@post }
                    adapterSearch.add(stationName)
                }

                adapterSearch.notifyDataSetChanged()
                edtSearch.post {
                    edtSearch.showSuggestions()
                }
            }
        } catch (_: Exception) {}

        shouldUpdateSearch = false
    }

    private fun clearFocus() {
        val edtSearch = findViewById<AutoCompleteTextView>(R.id.edtWidgetConfigurationSearch)
        val focusLayout = findViewById<LinearLayout>(R.id.focusableLayoutWidgetConfiguration)
        edtSearch.dismissDropDown()
        focusLayout.requestFocus()
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(focusLayout.windowToken, 0)
    }
}