package com.maddin.echtzeyt

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.switchmaterial.SwitchMaterial
import com.maddin.echtzeyt.components.FloatingInfoButton
import com.maddin.echtzeyt.components.LabeledDiscreteSeekBar

open class SettingsActivity : AppCompatActivity() {
    private val optionsUpdateEvery = mutableMapOf<Int, String>()
    private val preferences by lazy { getSharedPreferences(PREFERENCES_NAME(this), MODE_PRIVATE) }

    private val switchSaveContent by lazy { findViewById<SwitchMaterial>(R.id.settingsSaveContentSwitch) }
    private val switchAutoDark by lazy { findViewById<SwitchMaterial>(R.id.settingsAutoDarkSwitch) }
    private val switchDarkMode by lazy { findViewById<SwitchMaterial>(R.id.settingsDarkModeSwitch) }
    private val selectUpdateEvery by lazy { findViewById<LabeledDiscreteSeekBar>(R.id.settingsUpdateEverySelect) }
    private val switchMapUseMobileData by lazy { findViewById<SwitchMaterial>(R.id.settingsMapUseMobileDataSwitch) }

    protected val activityLicenses = LicensesActivity::class.java

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        switchSaveContent.isChecked = preferences.getBoolean("saveStation", true)
        switchAutoDark.isChecked = preferences.getBoolean("autoDark", true)
        switchDarkMode.isChecked = preferences.getBoolean("darkMode", false)

        val namesUE = resources.getStringArray(R.array.widgetConfigureRunEveryOptionNames)
        val valuesUE = resources.getIntArray(R.array.widgetConfigureRunEveryOptionValues)
        for (i in valuesUE.indices) {
            optionsUpdateEvery[valuesUE[i]] = namesUE[i]
        }
        selectUpdateEvery.setItems(namesUE)
        selectUpdateEvery.progress = valuesUE.indexOf(preferences.getInt("updateEvery", 5000)).coerceAtLeast(0)

        switchMapUseMobileData.isChecked = preferences.getBoolean("mapUseMobileData", false)

        findViewById<FloatingInfoButton>(R.id.btnLicenses).button.setOnClickListener { openLicenses() }
        findViewById<ImageButton>(R.id.btnSettingsSave).setOnClickListener{ saveSettings(); finish() }

        switchSaveContent.setOnClickListener { saveSettings() }
        switchAutoDark.setOnClickListener { saveSettings() }
        switchDarkMode.setOnClickListener { saveSettings() }
        selectUpdateEvery.addOnChangeListener { _,_,_,_ -> saveSettings() }
        switchMapUseMobileData.setOnClickListener { saveSettings() }
        updateApp()

        val settingsTitle = "${resources.getString(R.string.appName)} - ${resources.getString(R.string.appNameSettings)}"
        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbarSettings).title = settingsTitle
    }

    private fun updateApp() {
        switchDarkMode.isEnabled = true
        switchDarkMode.alpha = 1f

        when {
            switchAutoDark.isChecked -> {
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
        edit.putBoolean("saveStation", switchSaveContent.isChecked)
        edit.putBoolean("autoDark", switchAutoDark.isChecked)
        edit.putBoolean("darkMode", switchDarkMode.isChecked)

        if (selectUpdateEvery.max > 0) {
            edit.putInt("updateEvery", optionsUpdateEvery.keys.toList()[selectUpdateEvery.progress.coerceIn(0, optionsUpdateEvery.size-1)])
        }

        edit.putBoolean("mapUseMobileData", switchMapUseMobileData.isChecked)

        edit.apply()
        updateApp()
    }

    private fun openLicenses() {
        startActivity(Intent().setClass(this, activityLicenses))
    }
}