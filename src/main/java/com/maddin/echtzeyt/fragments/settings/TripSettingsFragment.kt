package com.maddin.echtzeyt.fragments.settings

import android.content.Context
import android.os.Bundle
import android.view.View
import com.maddin.echtzeyt.R
import com.maddin.echtzeyt.randomcode.LazyView

class TripSettingsFragment : SettingsFragment(R.layout.fragment_settings_trips) {
    override val spaceNavbar: View? by LazyView(R.id.fillerNavbar)

    override lateinit var settings: Array<out SettingsProperty>
    override fun onLoadSettings(view: View, context: Context, savedInstanceState: Bundle?) {
        settings = arrayOf()
    }
}