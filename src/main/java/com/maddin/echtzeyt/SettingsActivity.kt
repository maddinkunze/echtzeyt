package com.maddin.echtzeyt

import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.switchmaterial.SwitchMaterial

open class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
        setContentView(R.layout.activity_settings)

        findViewById<SwitchMaterial>(R.id.settingsSaveContentSwitch).isChecked = settings.getBoolean("saveStation", true)
        findViewById<SwitchMaterial>(R.id.settingsAutoDarkSwitch).isChecked = settings.getBoolean("autoDark", true)
        findViewById<SwitchMaterial>(R.id.settingsDarkModeSwitch).isChecked = settings.getBoolean("darkMode", false)
        findViewById<SwitchMaterial>(R.id.settingsFastModeSwitch).isChecked = settings.getBoolean("fastMode", false)

        findViewById<ImageButton>(R.id.btnSettingsSave).setOnClickListener{ saveSettings(); finish() }
        findViewById<SwitchMaterial>(R.id.settingsSaveContentSwitch).setOnClickListener { saveSettings() }
        findViewById<SwitchMaterial>(R.id.settingsAutoDarkSwitch).setOnClickListener { saveSettings() }
        findViewById<SwitchMaterial>(R.id.settingsDarkModeSwitch).setOnClickListener { saveSettings() }
        findViewById<SwitchMaterial>(R.id.settingsFastModeSwitch).setOnClickListener { saveSettings() }
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
        val preferences = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
        val edit = preferences.edit()
        edit.putBoolean("saveStation", findViewById<SwitchMaterial>(R.id.settingsSaveContentSwitch).isChecked)
        edit.putBoolean("autoDark", findViewById<SwitchMaterial>(R.id.settingsAutoDarkSwitch).isChecked)
        edit.putBoolean("darkMode", findViewById<SwitchMaterial>(R.id.settingsDarkModeSwitch).isChecked)
        edit.putBoolean("fastMode", findViewById<SwitchMaterial>(R.id.settingsFastModeSwitch).isChecked)
        edit.apply()
        updateApp()
    }
}