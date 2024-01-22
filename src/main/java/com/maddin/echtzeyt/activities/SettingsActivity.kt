package com.maddin.echtzeyt.activities

import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.maddin.echtzeyt.ECHTZEYT_CONFIGURATION
import com.maddin.echtzeyt.R
import com.maddin.echtzeyt.fragments.MenuViewPagerAdapter
import com.maddin.echtzeyt.fragments.settings.SettingsFragment

open class SettingsActivity : AppCompatActivity() {
    private val preferences by lazy { ECHTZEYT_CONFIGURATION.preferences(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<ImageButton>(R.id.btnSettingsSave).setOnClickListener{ saveSettings(); finish() }

        val settingsTitle = "${resources.getString(R.string.appName)} - ${resources.getString(R.string.appNameSettings)}"
        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbarSettings).title = settingsTitle

        val viewPager = findViewById<ViewPager2>(R.id.viewpagerSettings)
        val tabLayout = findViewById<TabLayout>(R.id.tabLayoutSettingsMenu)

        val adapter = MenuViewPagerAdapter(supportFragmentManager, lifecycle, ECHTZEYT_CONFIGURATION.fragmentsSettings)
        viewPager.adapter = adapter

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.setText(adapter.getFragmentNameResource(position))
        }.attach()
    }

    private fun saveSettings() {
        val edit = preferences.edit()
        for (fragment in supportFragmentManager.fragments) {
            if (fragment !is SettingsFragment) { continue }
            fragment.save(edit)
        }
        edit.apply()
    }
}