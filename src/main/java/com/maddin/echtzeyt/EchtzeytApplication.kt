package com.maddin.echtzeyt

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

abstract class EchtzeytApplication : Application() {
    abstract fun configure()
    override fun onCreate() {
        super.onCreate()

        configure()
        setCorrectNightMode()
    }

    private fun setCorrectNightMode() {
        val preferences = ECHTZEYT_CONFIGURATION.preferences(this)
        val nightMode = when {
            preferences.getBoolean("autoDark", true) -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            preferences.getBoolean("darkMode", false) -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_NO
        }
        if (AppCompatDelegate.getDefaultNightMode() == nightMode) { return }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }
}