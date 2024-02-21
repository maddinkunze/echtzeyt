package com.maddin.echtzeyt.fragments.settings

import android.content.Context
import android.os.Bundle
import android.view.View
import com.maddin.echtzeyt.ECHTZEYT_CONFIGURATION
import com.maddin.echtzeyt.R
import com.maddin.echtzeyt.components.DescriptiveSeekbar
import com.maddin.echtzeyt.components.DescriptiveSwitch
import com.maddin.echtzeyt.components.LabeledDiscreteSeekBar

class RealtimeSettingsFragment : SettingsFragment(R.layout.fragment_settings_realtime) {
    override lateinit var settings: Array<SettingsProperty>
    private lateinit var propUseIcons: BoolProperty
    private lateinit var propIconsSameWidth: BoolProperty

    override fun onLoadSettings(view: View, context: Context, savedInstanceState: Bundle?) {
        val seekbarUpdate = view.findViewById<DescriptiveSeekbar>(R.id.settingsUpdateEverySelect)
        val valuesUpdate = context.resources.getIntArray(R.array.settingsUpdateIntervalOptionValues)
        val namesUpdate = context.resources.getStringArray(R.array.settingsUpdateIntervalOptionNames)
        seekbarUpdate.seekbar.setItems(namesUpdate)
        val settingUpdateEvery = DiscreteSliderProperty(ECHTZEYT_CONFIGURATION.prefRealtimeUpdateEvery, seekbarUpdate, valuesUpdate, 5000)

        val settingSlowerUpdates = BoolProperty(ECHTZEYT_CONFIGURATION.prefRealtimeSlowerUpdates, view.findViewById<DescriptiveSwitch>(R.id.settingsRealtimeSlowerUpdates), true)
        if (!ECHTZEYT_CONFIGURATION.supportsSlowedDownRealtimeUpdates()) { settingSlowerUpdates.hide() }

        val settingSaveContent = BoolProperty(ECHTZEYT_CONFIGURATION.prefRealtimeSaveStation, view.findViewById<DescriptiveSwitch>(R.id.settingsSaveContentSwitch), true)

        propUseIcons = BoolProperty(ECHTZEYT_CONFIGURATION.prefRealtimeUseIcons, view.findViewById<DescriptiveSwitch>(R.id.settingsRealtimeUseIconsSwitch), true)
        propIconsSameWidth = BoolProperty(ECHTZEYT_CONFIGURATION.prefRealtimeIconsSameWidth, view.findViewById<DescriptiveSwitch>(R.id.settingsRealtimeIconsSameWidthSwitch), false)

        val seekbarTime = view.findViewById<DescriptiveSeekbar>(R.id.settingsRealtimeTimeAfterSelect)
        val valuesTime = context.resources.getIntArray(R.array.settingsRealtimeTimeAfterOptionValues)
        val namesTime = context.resources.getStringArray(R.array.settingsRealtimeTimeAfterOptionNames)
        seekbarTime.seekbar.setItems(namesTime)
        val settingTimeAfter = DiscreteSliderProperty(ECHTZEYT_CONFIGURATION.prefRealtimeTimeAfter, seekbarTime, valuesTime, 300)

        val seekbarTimePast = view.findViewById<DescriptiveSeekbar>(R.id.settingsRealtimeTimeAfterPastSelect)
        val valuesTimePast = context.resources.getIntArray(R.array.settingsRealtimeTimeAfterPastOptionValues)
        val namesTimePast = context.resources.getStringArray(R.array.settingsRealtimeTimeAfterPastOptionNames)
        seekbarTimePast.seekbar.setItems(namesTimePast)
        val settingTimeAfterPast = DiscreteSliderProperty(ECHTZEYT_CONFIGURATION.prefRealtimeTimeAfterPast, seekbarTimePast, valuesTimePast, 300)

        val settingHideCancelled = BoolProperty(ECHTZEYT_CONFIGURATION.prefRealtimeHideCancelled, view.findViewById<DescriptiveSwitch>(R.id.settingsRealtimeHideCancelledSwitch), false)
        val settingNegativeTimes = BoolProperty(ECHTZEYT_CONFIGURATION.prefRealtimeNegativeTimes, view.findViewById<DescriptiveSwitch>(R.id.settingsRealtimeNegativeTimesSwitch), false)

        settings = arrayOf(
            settingUpdateEvery,
            settingSlowerUpdates,
            settingSaveContent,
            propUseIcons,
            propIconsSameWidth,
            settingTimeAfter,
            settingTimeAfterPast,
            settingHideCancelled,
            settingNegativeTimes
        )

        propUseIcons.addOnChangeListener { updateConstraints() }
    }

    override fun onSettingsLoaded() {
        super.onSettingsLoaded()
        updateConstraints()
    }

    private fun updateConstraints() {
        propIconsSameWidth.setEnabled(propUseIcons.value)
    }

    override fun onSaveInstanceState(outState: Bundle) {}
}