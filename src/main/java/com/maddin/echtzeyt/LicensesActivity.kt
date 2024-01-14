package com.maddin.echtzeyt

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.forEach
import androidx.core.view.get
import com.maddin.echtzeyt.components.FloatingButton

class LicensesActivity : AppCompatActivity(R.layout.activity_licenses) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appName = resources.getString(R.string.appName)
        val screenName = resources.getString(R.string.appNameLicenses)
        findViewById<Toolbar>(R.id.toolbarLicenses).title = "$appName - $screenName"

        val linkMovement = LinkMovementMethod.getInstance()
        (findViewById<ScrollView>(R.id.scrollLicenses)[0] as ViewGroup).forEach {
            if (it !is TextView) { return@forEach }
            it.movementMethod = linkMovement
        }

        findViewById<FloatingButton>(R.id.btnLicensesBack).setOnClickListener { finish() }
    }
}