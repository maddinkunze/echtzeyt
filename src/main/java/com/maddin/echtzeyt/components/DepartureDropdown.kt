package com.maddin.echtzeyt.components

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.app.DatePickerDialog.OnDateSetListener
import android.app.TimePickerDialog
import android.app.TimePickerDialog.BUTTON_NEUTRAL
import android.app.TimePickerDialog.OnTimeSetListener
import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.text.format.DateFormat
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.DatePicker
import android.widget.TimePicker
import androidx.annotation.ColorRes
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.view.children
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import com.google.android.material.tabs.TabLayout.Tab
import com.maddin.echtzeyt.ECHTZEYT_CONFIGURATION
import com.maddin.echtzeyt.R
import com.maddin.echtzeyt.randomcode.LazyMutable
import com.maddin.echtzeyt.randomcode.LazyView
import java.lang.IllegalArgumentException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

fun View.setBackgroundTransparentRecursively() {
    setBackgroundColor(Color.TRANSPARENT)
    if (this !is ViewGroup) { return }
    for (child in children) {
        child.setBackgroundTransparentRecursively()
    }
}

@SuppressLint("DiscouragedApi")
fun TimePickerDialog.fixHeaderBackgrounds() {
    try {
        val header = findViewById<ViewGroup>(Resources.getSystem().getIdentifier("time_header", "id", "android"))
        header.children.forEach { it.setBackgroundTransparentRecursively() }
    } catch (e: Throwable) {
        Log.e(ECHTZEYT_CONFIGURATION.LOG_TAG, "Could not patch backgrounds on time picker", e)
    }
}

@SuppressLint("DiscouragedApi")
fun DatePickerDialog.fixHeaderBackgrounds(@ColorRes colorRes: Int) {
    try {
        val header = datePicker.findViewById<ViewGroup>(Resources.getSystem().getIdentifier("date_picker_header", "id", "android"))
        header.children.forEach { it.setBackgroundTransparentRecursively() }
        header.setBackgroundResource(colorRes)
    } catch (e: Throwable) {
        Log.e(ECHTZEYT_CONFIGURATION.LOG_TAG, "Could not patch backgrounds on date picker", e)
    }
}

@SuppressLint("DiscouragedApi")
fun DatePickerDialog.hideSecondTitle() {
    try {
        val title = findViewById<View>(Resources.getSystem().getIdentifier("title_template", "id", "android"))
        title.visibility = View.GONE
        (title.parent as? View)?.apply { post { requestLayout() } }

    } catch (e: Throwable) {
        Log.d(ECHTZEYT_CONFIGURATION.LOG_TAG, "Did not remove a secondary title from date picker", e)
    }
}

class DepartureDropdown : DropdownLayout, OnTimeSetListener, OnDateSetListener {
    constructor(context: Context) : super(context) { initialize(); getAttributes(context) }
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) { initialize(); getAttributes(context, attrs) }
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { initialize(); getAttributes(context, attrs, defStyleAttr) }

    private val mTabs: TabLayout by LazyView(R.id.departure_tabs)
    private val mTabDepart by lazy { mTabs.getTabAt(0)!! }
    private val mTabArrive by lazy { mTabs.getTabAt(1)!! }
    private val mBtnNow: Button by LazyView(R.id.departure_btnNow)
    private val mBtn15M: Button by LazyView(R.id.departure_btn15Min)
    private val mBtn1H: Button by LazyView(R.id.departure_btn1H)
    private val mBtnSelect: Button by LazyView(R.id.departure_btnSelectTime)

    private val strToday by lazy { resources.getString(R.string.dddepToday) }
    private val strArrive by lazy { resources.getString(R.string.dddepArrive) }
    private val strDepart by lazy { resources.getString(R.string.dddepDepart) }
    private val strChooseDate by lazy { resources.getString(R.string.dddepSelectDate) }

    companion object {
        const val MODE_DEPARTURE = 1
        const val MODE_ARRIVAL = 2
        val MODES_ALL = intArrayOf(MODE_DEPARTURE, MODE_ARRIVAL)
    }

    var mode: Int
        get() = mMode
        set(value) { mMode = value; onModeUpdated() }
    private var mMode: Int = MODE_DEPARTURE
    var dateTime: LocalDateTime
        get() { return mDateTime ?: LocalDateTime.now() }
        set(value) { mDateTime = value; onDateTimeUpdated() }
    private var mDateTime: LocalDateTime? = null  // null = now
    private lateinit var mTimeUncommitted: LocalTime
    private lateinit var mDateUncommitted: LocalDate
    private val mTimePickerDialog by lazy {
        TimePickerDialog(ContextThemeWrapper(context, R.style.Theme_Echtzeyt_Dialog_TimePicker), this, 0, 0, DateFormat.is24HourFormat(context)).apply {
            setButton(BUTTON_NEUTRAL, strChooseDate) { _, _ -> openDatePickerDialog() }
        }
    }
    private val mDatePickerDialog by lazy {
        DatePickerDialog(ContextThemeWrapper(context, R.style.Theme_Echtzeyt_Dialog_DatePicker), this, 1, 1, 1970).apply {
            setOnCancelListener { _ -> reopenTimePickerDialog() }
            setButton(BUTTON_NEUTRAL, strToday) { _, _ -> mDateUncommitted = LocalDate.now(); reopenTimePickerDialog() }
        }
    }

    private var mAllowedModes = MODES_ALL.fold(0, Int::or)

    var onModeChangedListeners = mutableListOf<() -> Unit>()
    var onDateTimeChangedListeners = mutableListOf<() -> Unit>()
    var onChangedListeners = mutableListOf<() -> Unit>()

    private fun initialize() {
        inflate(context, R.layout.comp_dropdown_departure, this)
    }

    private fun getAttributes(context: Context, attrs: AttributeSet?=null, defStyleAttr: Int=0, defStyleRes: Int=0) {
        val styledAttr = context.obtainStyledAttributes(attrs, R.styleable.DepartureDropdown, defStyleAttr, defStyleRes)
        try {
            mAllowedModes = styledAttr.getInt(R.styleable.DepartureDropdown_allowedModes, mAllowedModes)
        } catch (_: Throwable) {
            styledAttr.recycle()
        }

        // force all lazy tabs to load
        mTabDepart; mTabArrive
        setExclusivelyAllowedModes(mAllowedModes)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isInEditMode) { return }
        updateButton()
    }

    private fun getModeTab(mode: Int): Tab {
        return when (mode) {
            MODE_ARRIVAL -> mTabArrive
            else -> mTabDepart
        }
    }

    fun disallowModes(vararg modes: Int) {
        for (mode in modes) {
            val tab = getModeTab(mode)
            if (tab.parent == null) { continue }
            try { mTabs.removeTab(tab) } catch (_: IllegalArgumentException) {}
        }
        updateTabsVisibility()
    }

    private fun updateTabsVisibility() {
        mTabs.visibility = if (mTabs.tabCount > 1) VISIBLE else GONE
    }

    private fun setExclusivelyAllowedModes(flagsModes: Int) {
        val disallowedModes = mutableListOf<Int>()
        for (mode in MODES_ALL) {
            if (mode and flagsModes != 0) { continue }
            disallowedModes.add(mode)
        }
        disallowModes(*disallowedModes.toIntArray())
    }

    fun setExclusivelyAllowedModes(vararg modes: Int) {
        setExclusivelyAllowedModes(modes.fold(0, Int::or))
    }

    override fun setListeners(button: Button, others: List<DropdownLayout>) {
        super.setListeners(button, others)

        mBtnNow.setOnClickListener { mDateTime = null; onDateTimeUpdated() }
        mBtn15M.setOnClickListener { mDateTime = LocalDateTime.now().plusMinutes(15); onDateTimeUpdated() }
        mBtn1H.setOnClickListener { mDateTime = LocalDateTime.now().plusHours(1); onDateTimeUpdated() }
        mBtnSelect.setOnClickListener { openTimePickerDialog() }

        mTabs.addOnTabSelectedListener(object: OnTabSelectedListener {
            override fun onTabSelected(tab: Tab?) {
                mMode = when (tab) {
                    mTabDepart -> MODE_DEPARTURE
                    mTabArrive -> MODE_ARRIVAL
                    else -> return
                }
                onModeUpdated(updateTabs=false)
            }
            override fun onTabUnselected(tab: Tab?) {}
            override fun onTabReselected(tab: Tab?) { onTabSelected(tab) }
        })
    }

    fun setDateTimeWithoutListeners(dateTime: LocalDateTime) {
        mDateTime = dateTime
        onDateTimeUpdated(callListeners=false)
    }

    fun resetDateTimeToNow() {
        mDateTime = null
        onDateTimeUpdated()
    }

    private fun onDateTimeUpdated(closeDropdown: Boolean=true, callListeners: Boolean=true) {
        if (closeDropdown) { close() }
        updateButton()
        if (callListeners) { onDateTimeChanged() }
    }

    private fun onDateTimeChanged() {
        onDateTimeChangedListeners.forEach { it() }
        onChanged()
    }

    private fun onModeChanged() {
        onModeChangedListeners.forEach { it() }
        onChanged()
    }

    private fun onChanged() {
        onChangedListeners.forEach { it() }
    }

    private fun onModeUpdated(updateTabs: Boolean=true, callListeners: Boolean=true) {
        if (updateTabs) { mTabs.selectTab(getModeTab(mode)) }
        updateButton()
        if (callListeners) { onModeChanged() }
    }

    @SuppressLint("SetTextI18n")
    private fun updateButton() {
        val deparr = when (mMode) {
            MODE_ARRIVAL -> strArrive
            else -> strDepart
        }
        val time = ECHTZEYT_CONFIGURATION.formatDateTime(mDateTime)
        mButton?.text = "$deparr: $time"
    }

    private fun openTimePickerDialog() {
        mTimeUncommitted = LocalTime.from(dateTime)
        mDateUncommitted = LocalDate.from(dateTime)
        mTimePickerDialog.updateTime(mTimeUncommitted.hour, mTimeUncommitted.minute)
        reopenTimePickerDialog()
    }

    private fun reopenTimePickerDialog() {
        mTimePickerDialog.show()
        mTimePickerDialog.fixHeaderBackgrounds()
    }

    private fun openDatePickerDialog() {
        mDatePickerDialog.updateDate(mDateUncommitted.year, mDateUncommitted.monthValue-1, mDateUncommitted.dayOfMonth)
        mDatePickerDialog.show()
        mDatePickerDialog.fixHeaderBackgrounds(R.color.backgroundDialogHeader)
        mDatePickerDialog.hideSecondTitle()
        //(mDatePickerDialog.datePicker.parent.parent.parent as View).listRecursive("├")
    }

    private fun commitChanges() {
        mDateTime = LocalDateTime.of(mDateUncommitted.year, mDateUncommitted.month, mDateUncommitted.dayOfMonth, mTimeUncommitted.hour, mTimeUncommitted.minute)
        onDateTimeUpdated()
    }

    override fun onTimeSet(view: TimePicker?, hourOfDay: Int, minute: Int) {
        mTimeUncommitted = LocalTime.of(hourOfDay, minute)
        commitChanges()
    }

    override fun onDateSet(view: DatePicker?, year: Int, month: Int, dayOfMonth: Int) {
        mDateUncommitted = LocalDate.of(year, month+1, dayOfMonth)
        reopenTimePickerDialog()
    }

    fun isNow(): Boolean {
        return mDateTime == null
    }
}