package com.maddin.echtzeyt.fragments.settings

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.forEach
import androidx.core.view.get
import androidx.fragment.app.Fragment
import com.maddin.echtzeyt.R
import com.maddin.echtzeyt.fragments.NamedFragment

class AboutSettingsFragment : Fragment(R.layout.fragment_settings_about) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val linkMovement = LinkMovementMethod.getInstance()
        (view.findViewById<ScrollView>(R.id.scrollLicenses)[0] as ViewGroup).forEach {
            if (it !is TextView) { return@forEach }
            it.movementMethod = linkMovement
        }
    }
}