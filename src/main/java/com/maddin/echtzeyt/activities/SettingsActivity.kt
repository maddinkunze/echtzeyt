package com.maddin.echtzeyt.activities

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import androidx.appcompat.widget.Toolbar
import androidx.core.view.updateLayoutParams
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.maddin.echtzeyt.BuildConfig
import com.maddin.echtzeyt.ECHTZEYT_CONFIGURATION
import com.maddin.echtzeyt.R
import com.maddin.echtzeyt.fragments.MenuViewPagerAdapter
import com.maddin.echtzeyt.fragments.settings.SettingsFragment
import com.maddin.echtzeyt.randomcode.ActivityViewpagerScrollable
import com.maddin.echtzeyt.randomcode.LazyView

open class SettingsActivity : EchtzeytForegroundActivity(), ActivityViewpagerScrollable {
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

    private val spaceNavbar: View by LazyView(R.id.fillerNavbar)
    override fun updateWindowNavigationInsets() {
        val navHeight = getNavigationHeight()
        val gestHeight = getGesturesHeight()
        val heights = listOf(navHeight, gestHeight).filter { it != 0 }
        if (heights.isEmpty()) { return }
        spaceNavbar.updateLayoutParams { height = heights.min() }
        BuildConfig.LIBRARY_PACKAGE_NAME
    }

    private val spaceStatus: View by LazyView(R.id.fillerStatus)
    override fun updateWindowStatusInsets() {
        val statusHeight = getStatusBarHeight()
        if (statusHeight <= 0) { return }
        spaceStatus.updateLayoutParams { height = statusHeight }
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