package com.maddin.echtzeyt.randomcode

import com.maddin.transportapi.Connection
import com.maddin.transportapi.Station

interface ActivityShowStation {
    fun showStationInfo(station: Station) {}
}

interface ActivityShowConnection {
    fun showConnectionInfo(connection: Connection) {

    }
}