package com.maddin.echtzeyt

import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.maddin.echtzeyt.fragments.NamedFragment
import com.maddin.echtzeyt.fragments.settings.SettingsFragment

class ViewPagerAdapter(fragmentManager: FragmentManager, lifecycle: Lifecycle) : FragmentStateAdapter(fragmentManager, lifecycle) {
    private val fragments by lazy { arrayOf(
        ECHTZEYT_CONFIGURATION.generalSettingsFragment,
        ECHTZEYT_CONFIGURATION.realtimeSettingsFragment,
        ECHTZEYT_CONFIGURATION.tripSettingsFragment,
        ECHTZEYT_CONFIGURATION.mapsSettingsFragment,
        ECHTZEYT_CONFIGURATION.aboutSettingsFragment
    ).filterNotNull() }

    override fun getItemCount(): Int {
        return fragments.size
    }

    override fun createFragment(position: Int): Fragment {
        return fragments[position]
    }

    fun getFragmentNameResource(position: Int): Int {
        return (fragments[position] as? NamedFragment)?.titleResource ?: 0
    }
}

open class SettingsActivity : AppCompatActivity() {
    private val preferences by lazy { ECHTZEYT_CONFIGURATION.preferences(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<ImageButton>(R.id.btnSettingsSave).setOnClickListener{ saveSettings(); finish() }

        val settingsTitle = "${resources.getString(R.string.appName)} - ${resources.getString(R.string.appNameSettings)}"
        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbarSettings).title = settingsTitle


        // PLAYGROUND:
        val viewPager = findViewById<ViewPager2>(R.id.viewpagerSettings)
        val tabLayout = findViewById<TabLayout>(R.id.tabLayoutSettingsMenu)

        val adapter = ViewPagerAdapter(supportFragmentManager, lifecycle)
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