package com.maddin.echtzeyt.fragments.settings

import android.content.Context
import android.os.Bundle
import android.view.View
import com.maddin.echtzeyt.R
import com.maddin.echtzeyt.components.LabeledDiscreteSeekBar

class RealtimeSettingsFragment : SettingsFragment(R.layout.fragment_settings_realtime) {
    override val titleResource = R.string.menuSettingsRealtime
    override lateinit var settings: Array<SettingsProperty>

    override fun onLoadSettings(view: View, context: Context, savedInstanceState: Bundle?) {
        val seekbar = view.findViewById<LabeledDiscreteSeekBar>(R.id.settingsUpdateEverySelect)
        val values = context.resources.getIntArray(R.array.settingsUpdateIntervalOptionValues)
        val names = context.resources.getStringArray(R.array.settingsUpdateIntervalOptionNames)
        seekbar.setItems(names)
        val settingUpdateEvery = DiscreteSliderProperty("updateEvery", seekbar, values, 5000)

        val settingSaveContent = BoolProperty("saveContent", view.findViewById(R.id.settingsSaveContentSwitch), true)

        settings = arrayOf(
            settingUpdateEvery,
            settingSaveContent
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {}
}