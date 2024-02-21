package com.maddin.echtzeyt.activities

import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.maddin.echtzeyt.ECHTZEYT_CONFIGURATION
import com.maddin.echtzeyt.R
import com.maddin.echtzeyt.fragments.MenuViewPagerAdapter
import com.maddin.echtzeyt.fragments.settings.SettingsFragment
import com.maddin.echtzeyt.randomcode.ActivityViewpagerScrollable
import com.maddin.echtzeyt.randomcode.LazyView

open class SettingsActivity : AppCompatActivity(), ActivityViewpagerScrollable {
    private val preferences by lazy { ECHTZEYT_CONFIGURATION.preferences(this) }

    private val btnSave: ImageButton by LazyView(R.id.btnSettingsSave)
    private val toolbar: Toolbar by LazyView(R.id.toolbarSettings)
    override val viewpager: ViewPager2 by LazyView(R.id.viewpagerSettings)
    private val tablayout: TabLayout by LazyView(R.id.tabLayoutSettingsMenu)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        btnSave.setOnClickListener{ saveSettings(); finish() }

        val settingsTitle = "${resources.getString(R.string.appName)} - ${resources.getString(R.string.appNameSettings)}"
        toolbar.title = settingsTitle

        val adapter = MenuViewPagerAdapter(supportFragmentManager, lifecycle, ECHTZEYT_CONFIGURATION.fragmentsSettings)
        viewpager.adapter = adapter

        TabLayoutMediator(tablayout, viewpager) { tab, position ->
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