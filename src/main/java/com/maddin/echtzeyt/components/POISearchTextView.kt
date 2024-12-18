package com.maddin.echtzeyt.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.annotation.LayoutRes
import androidx.core.app.ActivityCompat
import com.maddin.echtzeyt.R
import com.maddin.transportapi.components.POI
import com.maddin.transportapi.endpoints.SearchPOIAPI
import com.maddin.transportapi.endpoints.SearchPOIRequestImpl

class POIAdapter(context: Context, @LayoutRes resId: Int) : ArrayAdapter<String>(context, resId) {
    private val mPOIs = mutableListOf<POI>()
    fun getStationAt(pos: Int) : POI? {
        return mPOIs.getOrNull(pos)
    }

    fun add(poi: POI) {
        mPOIs.add(poi)
        super.add(poi.name)
    }

    override fun clear() {
        super.clear()
        mPOIs.clear()
    }
}

class POISearchTextView : InstantAutoCompleteTextView {
    constructor(context: Context, attr: AttributeSet?, defStyleAttr: Int) : super(context, attr, defStyleAttr)
    constructor(context: Context, attr: AttributeSet?) : super(context, attr)
    constructor(context: Context) : super(context)

    lateinit var searchPOIAPI: SearchPOIAPI
    val onItemSelectedListeners = mutableListOf<() -> Unit>()
    private val adapterSearch by lazy { POIAdapter(context, R.layout.support_simple_spinner_dropdown_item) }
    private val activity by lazy {
        var c = context
        while (c is ContextWrapper) {
            if (c is Activity) { return@lazy c as Activity }
            c = (c as ContextWrapper).baseContext
        }
        null
    }

    private val inputMethodManager by lazy { ActivityCompat.getSystemService(activity?:context, InputMethodManager::class.java) }

    private var mCurrentPOI: POI? = null
        @Synchronized get
        @Synchronized set
    private var mLastSelectedStation: String = ""
        @Synchronized get
        @Synchronized set
    var currentPOI: POI?
        @Synchronized get() = mCurrentPOI
        @Synchronized set(value) {
            mCurrentPOI = value
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
            if (search == currentPOI?.name) { clearFocus(); return@addOnTextChangedListener }

            currentStationSearch = search
            shouldUpdateSearch = true
        }

        // When selecting an item of the search dropdown
        addOnItemSelectedListener { clearFocus(); onItemSelected() }
        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                mCurrentPOI = adapterSearch.getStationAt(position)
                clearFocus()
                onItemSelected()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        setOnItemClickListener { _: AdapterView<*>?, _: View?, position: Int, _: Long ->
            mCurrentPOI = adapterSearch.getStationAt(position)
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
            var pois = emptyList<POI>()
            if (currentStationSearch.isNotEmpty()) {
                pois = searchPOIAPI.searchPOIs(SearchPOIRequestImpl(currentStationSearch)).pois
            }

            activity?.runOnUiThread {
                adapterSearch.clear()
                if (pois.isEmpty()) {
                    mCurrentPOI = null
                    adapterSearch.notifyDataSetChanged()
                    dismissDropDown()
                    onItemSelected()
                    return@runOnUiThread
                }

                for (poi in pois) {
                    if (poi.name == currentStationSearch || (pois.size == 1 && !hasFocus())) {
                        mCurrentPOI = poi
                        clearFocus()
                        onItemSelected()
                        return@runOnUiThread
                    }
                    adapterSearch.add(poi)
                }

                adapterSearch.notifyDataSetChanged()
                post { if (!hasFocus()) { return@post }; showSuggestions() }
            }
        } catch (e: Exception) {
            //val classification = classifyExceptionDefault(e)
            //exceptions.add(ClassifiedException(e, classification))
        }

        shouldUpdateSearch = false
    }

    private fun onItemSelected() {
        if (mLastSelectedStation.isNotEmpty() && mLastSelectedStation == mCurrentPOI?.id?.uuid) { return }
        mLastSelectedStation = mCurrentPOI?.id?.uuid ?: ""
        for (listener in onItemSelectedListeners) { listener() }
    }

    fun setText(text: String) {
        super.setText(text)
        if (text.isNotEmpty()) { clearFocus() }
        shouldUpdateSearch = true
    }

    override fun requestFocus(direction: Int, previouslyFocusedRect: Rect?): Boolean {
        val gotFocus = super.requestFocus(direction, previouslyFocusedRect)
        if (gotFocus) { inputMethodManager?.showSoftInput(this, 0) }
        return gotFocus
    }

    override fun clearFocus() {
        super.clearFocus()
        dismissDropDown()
        inputMethodManager?.hideSoftInputFromWindow(windowToken, 0)
    }
}