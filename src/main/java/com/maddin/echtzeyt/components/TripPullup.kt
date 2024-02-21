package com.maddin.echtzeyt.components

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.maddin.echtzeyt.ECHTZEYT_CONFIGURATION
import com.maddin.echtzeyt.R
import com.maddin.echtzeyt.randomcode.LazyView
import com.maddin.transportapi.components.Connection
import com.maddin.transportapi.components.InstructedTripConnection
import com.maddin.transportapi.components.Platform
import com.maddin.transportapi.components.Trip
import com.maddin.transportapi.components.TripConnection
import com.maddin.transportapi.components.VehicleTypes
import java.time.LocalDateTime

class TripPullup : PullupScrollView {
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context) : super(context)

    init {
        inflate(context, R.layout.comp_pullup_trip, this)
    }

    private val txtStationFrom: TextView by LazyView(R.id.put_txtStationFrom)
    private val txtStationTo: TextView by LazyView(R.id.put_txtStationTo)
    private val layoutConnections: LinearLayout by LazyView(R.id.put_layoutConnections)

    fun setTrip(trip: Trip, connectionS: Connection?=null, showPullup: Boolean=true) {
        txtStationFrom.text = trip.from.poi.name
        txtStationTo.text = trip.to.poi.name

        layoutConnections.removeAllViews()
        var connView: View? = null
        for ((index, connection) in trip.connections.withIndex()) {
            val view = TripConnectionInfoPullup(context, connection)
            if (connection == connectionS) { connView = view }
            layoutConnections.addView(view)
            if (index > 0) view.updateLayoutParams<LinearLayout.LayoutParams> { topMargin = 20 }
        }

        if (showPullup) {
            updateScrollLimits(isVisible())
            if (!isVisible()) { showPullup() }
        }
        connView?.post {  }
    }
}

fun TextView.setTextOrHide(text: String?) {
    this.text = text
    this.visibility = if (text == null) { View.GONE } else { View.VISIBLE }
}

fun TextView.setPlatformOrHide(platform: Platform?) {
    setTextOrHide(platform?.name)
}

fun TextView.setTimeOrHide(time: LocalDateTime?) {
    setTextOrHide(time?.format(ECHTZEYT_CONFIGURATION.formatterTimeShort))
}

fun TextView.strikeIfDifferentThan(other: TextView) {
    if (!isVisible) { return }
    if (!other.isVisible) { return }
    if (text == other.text) {
        other.visibility = View.GONE
    } else {
        setStrikeThrough()
    }
}

@SuppressLint("ViewConstructor")
class TripConnectionInfoPullup(context: Context, val connection: TripConnection) : ConstraintLayout(context) {
    private val txtPOIFrom: TextView by LazyView(R.id.cip_txtStationStart)
    private val txtPOITo: TextView by LazyView(R.id.cip_txtStationEnd)

    private val txtTimeFromPlanned: TextView by LazyView(R.id.cip_txtStart)
    private val txtTimeFromActual: TextView by LazyView(R.id.cip_txtStartReal)
    private val txtTimeToPlanned: TextView by LazyView(R.id.cip_txtEnd)
    private val txtTimeToActual: TextView by LazyView(R.id.cip_txtEndReal)

    private val txtPlatformFromPlanned: TextView by LazyView(R.id.cip_txtPlatformStart)
    private val txtPlatformFromActual: TextView by LazyView(R.id.cip_txtPlatformStartReal)
    private val txtPlatformToPlanned: TextView by LazyView(R.id.cip_txtPlatformEnd)
    private val txtPlatformToActual: TextView by LazyView(R.id.cip_txtPlatformEndReal)

    private val txtVehicleType: VehicleTypeTextView by LazyView(R.id.cip_txtVehicleType)
    private val txtStopCount: TextView by LazyView(R.id.cip_txtStops)

    init {
        inflate(context, R.layout.comp_connection_info_pullup, this)
        txtPOIFrom.text = connection.from.poi.name
        txtPOITo.text = connection.to.poi.name

        txtTimeFromPlanned.setTimeOrHide(connection.estimateStartPlanned())
        txtTimeFromActual.setTimeOrHide(connection.startActual)
        txtTimeFromPlanned.strikeIfDifferentThan(txtTimeFromActual)
        txtTimeToPlanned.setTimeOrHide(connection.estimateEndPlanned())
        txtTimeToActual.setTimeOrHide(connection.endActual)
        txtTimeToPlanned.strikeIfDifferentThan(txtTimeToActual)

        txtPlatformFromPlanned.setPlatformOrHide(connection.from.platformPlannedOrActual)
        txtPlatformFromActual.setPlatformOrHide(connection.from.platformActualIfNotPlanned)
        txtPlatformFromPlanned.strikeIfDifferentThan(txtPlatformFromActual)
        txtPlatformToPlanned.setPlatformOrHide(connection.to.platformPlannedOrActual)
        txtPlatformToActual.setPlatformOrHide(connection.to.platformActualIfNotPlanned)
        txtPlatformToPlanned.strikeIfDifferentThan(txtPlatformToActual)

        val stopCount = connection.stopCountBetween
        val instructions = (connection as? InstructedTripConnection)?.instructions
        if (!instructions.isNullOrEmpty()) {
            txtStopCount.text = "Instructions"
        } else if (stopCount < 1 || connection.vehicle?.type?.isSubtypeOf(VehicleTypes.WALK) == true) {
            (txtStopCount.parent as View).visibility = View.GONE
        } else {
            txtStopCount.text = "$stopCount Stops"
        }

        val vehicle = connection.vehicle
        if (vehicle == null) {
            txtVehicleType.visibility = View.GONE
        } else {
            txtVehicleType.setVehicle(vehicle, false)
        }
    }
}