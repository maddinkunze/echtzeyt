package com.maddin.echtzeyt.fragments.echtzeyt

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import androidx.activity.OnBackPressedCallback
import androidx.core.view.WindowInsetsCompat
import com.maddin.echtzeyt.ECHTZEYT_CONFIGURATION
import com.maddin.echtzeyt.R
import com.maddin.echtzeyt.activities.MapResultContractSelectStation
import com.maddin.echtzeyt.components.AnimatableImageButton
import com.maddin.echtzeyt.components.StationSearchTextView
import com.maddin.echtzeyt.components.StationSearchbar
import com.maddin.echtzeyt.fragments.EchtzeytForegroundFragment
import com.maddin.transportapi.LocatableStation
import kotlin.concurrent.thread

class TripsFragment : EchtzeytForegroundFragment(R.layout.fragment_trips) {
    private val mEdtSearchFrom: StationSearchbar by lazy { safeView.findViewById(R.id.edtSearchFrom) }
    private val mBtnMapFrom: ImageButton by lazy { safeView.findViewById(R.id.btnMapFrom) }
    private val mEdtSearchTo: StationSearchbar by lazy { safeView.findViewById(R.id.edtSearchTo) }
    private val mBtnMapTo: ImageButton by lazy { safeView.findViewById(R.id.btnMapTo) }
    private var mEdtInvokedMap: StationSearchbar? = null
    private var mKeyboardVisible = false

    // Everything related to other activities (such as the settings or a station selection map)
    private val activityMapLauncher by lazy { registerForActivityResult(MapResultContractSelectStation()) { if (it == null) { return@registerForActivityResult }; mEdtInvokedMap?.currentStation = it } }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initVariables()
        initListeners()
        initThreads()
    }

    private fun initVariables() {
        if (ECHTZEYT_CONFIGURATION.mapsSupportLocateStations) { activityMapLauncher }
        mEdtSearchFrom.searchStationAPI = ECHTZEYT_CONFIGURATION.tripsStationAPI!!
        mEdtSearchTo.searchStationAPI = ECHTZEYT_CONFIGURATION.tripsStationAPI!!
    }

    private fun initListeners() {
        // Listener for whether the ime (i.e. keyboard inset) is visible / has been hidden
        // -> clear focus from text inputs on keyboard hide
        safeView.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) { return@addOnLayoutChangeListener }
            if (!v.isAttachedToWindow) { return@addOnLayoutChangeListener }
            val keyboardVisible = WindowInsetsCompat.toWindowInsetsCompat(v.rootWindowInsets, v).isVisible(
                WindowInsetsCompat.Type.ime())
            if (mKeyboardVisible && !keyboardVisible) { clearFocus() }
            mKeyboardVisible = keyboardVisible
        }

        mBtnMapFrom.setOnClickListener { mEdtInvokedMap = it.parent as? StationSearchbar; activityMapLauncher.launch(mEdtInvokedMap?.currentStation as? LocatableStation) }
        mBtnMapTo.setOnClickListener { mEdtInvokedMap = it.parent as? StationSearchbar; activityMapLauncher.launch(mEdtInvokedMap?.currentStation as? LocatableStation) }
    }

    private fun initThreads() {
        thread(start=true, isDaemon=true) {
            while (true) {
                if (!isInForeground.block(15000)) { continue }

                mEdtSearchFrom.ntUpdateSearch()
                mEdtSearchTo.ntUpdateSearch()
                Thread.sleep(50)
            }
        }
    }

    private fun clearFocus() {
        mEdtSearchFrom.clearFocus()
        mEdtSearchTo.clearFocus()
    }
}