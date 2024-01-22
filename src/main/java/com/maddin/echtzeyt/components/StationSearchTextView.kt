package com.maddin.echtzeyt.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.text.InputType
import android.util.AttributeSet
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.annotation.LayoutRes
import androidx.core.app.ActivityCompat
import com.maddin.echtzeyt.R
import com.maddin.transportapi.SearchStationAPI
import com.maddin.transportapi.Station

class StationAdapter(context: Context, @LayoutRes resId: Int) : ArrayAdapter<String>(context, resId) {
    private val mStations = mutableListOf<Station>()
    fun getStationAt(pos: Int) : Station? {
        return mStations.getOrNull(pos)
    }

    fun add(station: Station) {
        mStations.add(station)
        super.add(station.name)
    }

    override fun clear() {
        super.clear()
        mStations.clear()
    }
}

class StationSearchTextView : InstantAutoCompleteTextView {
    constructor(context: Context, attr: AttributeSet?, defStyleAttr: Int) : super(context, attr, defStyleAttr)
    constructor(context: Context, attr: AttributeSet?) : super(context, attr)
    constructor(context: Context) : super(context)

    lateinit var searchStationAPI: SearchStationAPI
    val onItemSelectedListeners = mutableListOf<() -> Unit>()
    private val adapterSearch by lazy { StationAdapter(context, R.layout.support_simple_spinner_dropdown_item) }
    private val activity by lazy {
        var c = context
        while (c is ContextWrapper) {
            if (c is Activity) { return@lazy c as Activity }
            c = (c as ContextWrapper).baseContext
        }
        null
    }

    private var mCurrentStation: Station? = null
    var currentStation: Station?
        get() = mCurrentStation
        set(value) {
            mCurrentStation = value
            if (value?.name == currentStationSearch) { return }
            currentStationSearch = value?.name ?: ""
            post { super.setText(currentStationSearch) }
        }
    private var currentStationSearch: String = ""
    private var shouldUpdateSearch = false

    init {
        // Set adapter (dropdown) for the station search -> autocomplete
        setAdapter(adapterSearch)
        threshold = 0  // Show dropdown after the first character entered
        setDropDownBackgroundResource(R.drawable.dropdown)  // Change background resource of the dropdown to match the rest

        // Listener when the main search input changes
        addOnTextChangedListener { text ->
            val search = text.toString()
            if (search == currentStation?.name) { clearFocus(); return@addOnTextChangedListener }

            currentStationSearch = search
            shouldUpdateSearch = true
        }

        // When selecting an item of the search dropdown
        addOnItemSelectedListener { clearFocus(); onItemSelected() }
        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                mCurrentStation = adapterSearch.getStationAt(position)
                clearFocus()
                onItemSelected()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        setOnItemClickListener { _: AdapterView<*>?, _: View?, position: Int, _: Long ->
            mCurrentStation = adapterSearch.getStationAt(position)
            clearFocus()
            onItemSelected()
        }
    }

    fun updateSearch() {
        shouldUpdateSearch = true
    }

    fun ntUpdateSearch() {
        if (!shouldUpdateSearch) { return }
        try {
            var stations = emptyList<Station>()
            if (currentStationSearch.isNotEmpty()) {
                stations = searchStationAPI.searchStations(currentStationSearch)
            }

            activity?.runOnUiThread {
                adapterSearch.clear()
                if (stations.isEmpty()) {
                    mCurrentStation = null
                    adapterSearch.notifyDataSetChanged()
                    dismissDropDown()
                    onItemSelected()
                    return@runOnUiThread
                }

                for (station in stations) {
                    if (station.name == currentStationSearch) {
                        mCurrentStation = station
                        clearFocus()
                        onItemSelected()
                        return@runOnUiThread }
                    adapterSearch.add(station)
                }

                adapterSearch.notifyDataSetChanged()
                post { showSuggestions() }
            }
        } catch (e: Exception) {
            //val classification = classifyExceptionDefault(e)
            //exceptions.add(ClassifiedException(e, classification))
        }

        shouldUpdateSearch = false
    }

    private fun onItemSelected() {
        for (listener in onItemSelectedListeners) { listener() }
    }

    fun setText(text: String) {
        super.setText(text)
        if (text.isNotEmpty()) { clearFocus() }
        shouldUpdateSearch = true
    }

    override fun clearFocus() {
        super.clearFocus()
        dismissDropDown()
        ActivityCompat.getSystemService(activity?:context, InputMethodManager::class.java)?.hideSoftInputFromWindow(windowToken, 0)
    }
}