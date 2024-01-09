package com.maddin.echtzeyt

import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.switchmaterial.SwitchMaterial
import com.maddin.echtzeyt.components.LabeledDiscreteSeekBar

open class SettingsActivity : AppCompatActivity() {
    private val optionsUpdateEvery = mutableMapOf<Int, String>()
    private val preferences by lazy { getSharedPreferences(PREFERENCES_NAME(this), MODE_PRIVATE) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<SwitchMaterial>(R.id.settingsSaveContentSwitch).isChecked = preferences.getBoolean("saveStation", true)
        findViewById<SwitchMaterial>(R.id.settingsAutoDarkSwitch).isChecked = preferences.getBoolean("autoDark", true)
        findViewById<SwitchMaterial>(R.id.settingsDarkModeSwitch).isChecked = preferences.getBoolean("darkMode", false)

        val namesUE = resources.getStringArray(R.array.widgetConfigureRunEveryOptionNames)
        val valuesUE = resources.getIntArray(R.array.widgetConfigureRunEveryOptionValues)
        for (i in valuesUE.indices) {
            optionsUpdateEvery[valuesUE[i]] = namesUE[i]
        }
        val settingsUpdateEverySelect = findViewById<LabeledDiscreteSeekBar>(R.id.settingsUpdateEverySelect)
        settingsUpdateEverySelect.setItems(namesUE)
        settingsUpdateEverySelect.progress = valuesUE.indexOf(preferences.getInt("updateEvery", 5000)).coerceAtLeast(0)

        findViewById<ImageButton>(R.id.btnSettingsSave).setOnClickListener{ saveSettings(); finish() }
        findViewById<SwitchMaterial>(R.id.settingsSaveContentSwitch).setOnClickListener { saveSettings() }
        findViewById<SwitchMaterial>(R.id.settingsAutoDarkSwitch).setOnClickListener { saveSettings() }
        findViewById<SwitchMaterial>(R.id.settingsDarkModeSwitch).setOnClickListener { saveSettings() }
        settingsUpdateEverySelect.addOnChangeListener { _,_,_,_ -> saveSettings() }
        updateApp()

        val settingsTitle = "${resources.getString(R.string.appName)} - ${resources.getString(R.string.appNameSettings)}"
        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbarSettings).title = settingsTitle
    }

    private fun updateApp() {
        val switchDarkMode = findViewById<SwitchMaterial>(R.id.settingsDarkModeSwitch)
        switchDarkMode.isEnabled = true
        switchDarkMode.alpha = 1f

        when {
            findViewById<SwitchMaterial>(R.id.settingsAutoDarkSwitch).isChecked -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                switchDarkMode.isEnabled = false
                switchDarkMode.alpha = 0.5f
            }
            switchDarkMode.isChecked -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
            else -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }
    }

    private fun saveSettings() {
        val edit = preferences.edit()
        edit.putBoolean("saveStation", findViewById<SwitchMaterial>(R.id.settingsSaveContentSwitch).isChecked)
        edit.putBoolean("autoDark", findViewById<SwitchMaterial>(R.id.settingsAutoDarkSwitch).isChecked)
        edit.putBoolean("darkMode", findViewById<SwitchMaterial>(R.id.settingsDarkModeSwitch).isChecked)

        val settingsUpdateEverySelect = findViewById<LabeledDiscreteSeekBar>(R.id.settingsUpdateEverySelect)
        if (settingsUpdateEverySelect.max > 0) {
            edit.putInt("updateEvery", optionsUpdateEvery.keys.toList()[settingsUpdateEverySelect.progress.coerceIn(0, optionsUpdateEvery.size-1)])
        }
        edit.apply()
        updateApp()
    }
}