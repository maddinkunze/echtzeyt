package com.maddin.echtzeyt.fragments.settings

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import com.maddin.echtzeyt.R
import com.maddin.echtzeyt.components.DescriptiveSwitch
import com.maddin.echtzeyt.randomcode.LazyView

class GeneralSettingsFragment : SettingsFragment(R.layout.fragment_settings_general) {
    override val spaceNavbar: View? by LazyView(R.id.fillerNavbar)

    private lateinit var propAutoDark: BoolProperty
    private lateinit var propDarkMode: BoolProperty
    override lateinit var settings: Array<SettingsProperty>

    override fun onLoadSettings(view: View, context: Context, savedInstanceState: Bundle?) {
        propAutoDark = BoolProperty("autoDark", view.findViewById<DescriptiveSwitch>(R.id.settingsAutoDarkSwitch), true)
        propDarkMode = BoolProperty("darkMode", view.findViewById<DescriptiveSwitch>(R.id.settingsDarkModeSwitch), false)

        settings = arrayOf(
            propAutoDark,
            propDarkMode
        )

        propAutoDark.addOnChangeListener { updateConstraints() }
        propDarkMode.addOnChangeListener { updateConstraints() }
    }

    override fun onSettingsLoaded() {
        super.onSettingsLoaded()
        updateConstraints()
    }

    private fun updateConstraints() {
        propDarkMode.setEnabled(!propAutoDark.value)

        AppCompatDelegate.setDefaultNightMode(when {
            propAutoDark.value -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            propDarkMode.value -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_NO
        })
    }
}