package com.maddin.echtzeyt.fragments.settings

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.SwitchCompat
import com.maddin.echtzeyt.R
import com.maddin.echtzeyt.components.DescriptiveSwitch
import com.maddin.echtzeyt.randomcode.LazyView
import kotlin.math.roundToInt

class MapSettingsFragment : SettingsFragment(R.layout.fragment_settings_map) {
    override val spaceNavbar: View? by LazyView(R.id.fillerNavbar)

    override lateinit var settings: Array<out SettingsProperty>

    override fun onLoadSettings(view: View, context: Context, savedInstanceState: Bundle?) {
        settings = arrayOf(
            BoolProperty("mapUseMobileData", view.findViewById<DescriptiveSwitch>(R.id.settingsMapUseMobileDataSwitch), false)
        )
    }
}