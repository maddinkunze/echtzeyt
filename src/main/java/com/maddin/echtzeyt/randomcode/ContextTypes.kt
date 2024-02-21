package com.maddin.echtzeyt.randomcode

import androidx.viewpager2.widget.ViewPager2
import com.maddin.transportapi.components.Connection
import com.maddin.transportapi.components.Station
import com.maddin.transportapi.components.Trip

interface ActivityScrollable {
    fun disableScroll()
    fun enableScroll()
}

interface ActivityViewpagerScrollable : ActivityScrollable {
    val viewpager: ViewPager2
    override fun disableScroll() {
        viewpager.isUserInputEnabled = false
    }

    override fun enableScroll() {
        viewpager.isUserInputEnabled = true
    }
}

interface ContextShowStationPullup {
    fun showStationPullup(station: Station)
}

interface ContextShowConnectionPullup {
    fun showConnectionPullup(connection: Connection)
}

interface ContextShowTripPullup {
    fun showTripPullup(trip: Trip, connection: Connection?=null)
}

interface ContextShowStationOnMap {

}

interface ContextSelectStationOnMap {

}

interface ContextShowTripOnMap {

}

interface ContextShowConnectionOnMap {

}