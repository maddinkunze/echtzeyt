package com.maddin.echtzeyt.components

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.util.TypedValueCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.maddin.echtzeyt.ECHTZEYT_CONFIGURATION
import com.maddin.echtzeyt.R
import com.maddin.echtzeyt.randomcode.LazyView
import com.maddin.transportapi.components.Connection
import com.maddin.transportapi.components.InstructedTripConnection
import com.maddin.transportapi.components.Platform
import com.maddin.transportapi.components.Stop
import com.maddin.transportapi.components.Trip
import com.maddin.transportapi.components.TripConnection
import com.maddin.transportapi.components.TripInstruction
import com.maddin.transportapi.components.TurnDirection
import com.maddin.transportapi.components.Walk
import com.maddin.transportapi.components.WalkInstructionEnter
import com.maddin.transportapi.components.WalkInstructionGo
import com.maddin.transportapi.components.WalkInstructionLeave
import com.maddin.transportapi.components.WalkInstructionTurn
import java.time.LocalDateTime
import kotlin.math.roundToInt

class TripPullup : PullupScrollView {
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
            val view = TripConnectionInfoPullup(context, connection, this)
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
class TripConnectionInfoPullup(context: Context, val connection: TripConnection, val pullup: PullupScrollView?) : ConstraintLayout(context) {
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
    private val btnStopCount: DropdownButton by LazyView(R.id.cip_btnStopsShow)
    private val layoutStops: LinearLayout by LazyView(R.id.cip_layoutStops)

    init {
        inflate(context, R.layout.comp_trip_connection_info, this)
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
            btnStopCount.text = resources.getString(R.string.tripInstructions)

            val marginTop = TypedValueCompat.dpToPx(4f, resources.displayMetrics).roundToInt()
            var skipTopMargin = 1
            for (instruction in instructions) {
                val view = TripConnectionInstructionInfoPullup(context, instruction)
                layoutStops.addView(view)
                if (skipTopMargin-- > 0) { continue }
                view.updateLayoutParams<LinearLayout.LayoutParams> { topMargin = marginTop }
            }
        } else if (stopCount < 1 || connection.modeOfTransport?.motType is Walk) {
            btnStopCount.visibility = View.GONE
        } else {
            btnStopCount.text = resources.getString(R.string.tripNoOfStations).format(stopCount)
        }

        for (stop in connection.stopsBetween) {
            layoutStops.addView(TripConnectionStopInfoPullup(context, stop))
        }

        btnStopCount.setOnClickListener {
            if (layoutStops.isVisible) {
                layoutStops.visibility = View.GONE
                btnStopCount.close()
            } else {
                layoutStops.visibility = View.VISIBLE
                btnStopCount.open()
            }
            pullup?.updateScrollLimits()
         }

        val vehicle = connection.modeOfTransport
        if (vehicle == null) {
            txtVehicleType.visibility = View.GONE
        } else {
            txtVehicleType.setVehicle(vehicle, false)
        }
    }
}

@SuppressLint("ViewConstructor")
open class TripConnectionStopInfoPullup(context: Context, val stop: Stop) : ConstraintLayout(context) {

    private val txtTimeDeparture: TextView by LazyView(R.id.cip_txtStop)
    private val txtPlatformDeparture: TextView by LazyView(R.id.cip_txtPlatformStop)
    private val txtStation: TextView by LazyView(R.id.cip_txtStationStop)

    init {
        inflate(context, R.layout.comp_trip_connection_stop_info, this)

        txtTimeDeparture.setTimeOrHide(stop.estimateDepartureActual())
        txtPlatformDeparture.setPlatformOrHide(stop.platformActualOrPlanned)
        txtStation.text = stop.poi.name
    }
}

fun ImageView.setDrawableOrHide(resId: Int?) {
    val drawable = resId?.let { AppCompatResources.getDrawable(context, it) }
    setImageDrawable(drawable)
    this.visibility = if (this.drawable == null) View.GONE else View.VISIBLE
}


@SuppressLint("ViewConstructor")
class TripConnectionInstructionInfoPullup(context: Context, instruction: TripInstruction) : ConstraintLayout(context) {
    private val imgInstruction: ImageView by LazyView(R.id.cip_imgInstruction)
    private val txtInstruction: TextView by LazyView(R.id.cip_txtInstruction)

    init {
        inflate(context, R.layout.comp_trip_connection_instruction_info, this)

        txtInstruction.text = instruction.translate(ECHTZEYT_CONFIGURATION.locale)

        imgInstruction.setDrawableOrHide(when (instruction) {
            is WalkInstructionGo -> R.drawable.ic_inst_straight
            is WalkInstructionTurn -> when (instruction.direction) {
                TurnDirection.AROUND -> R.drawable.ic_inst_turn_around
                TurnDirection.CLOCKWISE -> R.drawable.ic_inst_turn_clockwise
                TurnDirection.COUNTERCLOCKWISE -> R.drawable.ic_inst_turn_counterclockwise
                TurnDirection.SLIGHT_LEFT -> R.drawable.ic_inst_turn_slight_left
                TurnDirection.LEFT -> R.drawable.ic_inst_turn_left
                TurnDirection.SHARP_LEFT -> R.drawable.ic_inst_turn_sharp_left
                TurnDirection.SLIGHT_RIGHT -> R.drawable.ic_inst_turn_slight_right
                TurnDirection.RIGHT -> R.drawable.ic_inst_turn_right
                TurnDirection.SHARP_RIGHT -> R.drawable.ic_inst_turn_sharp_right
                else -> R.drawable.ic_inst_turn_unknown
            }
            is WalkInstructionEnter -> R.drawable.ic_inst_enter
            is WalkInstructionLeave -> R.drawable.ic_inst_leave
            else -> null
        })
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (height != measuredHeight) {
            updateLayoutParams<LinearLayout.LayoutParams> { height = measuredHeight }
        }
    }
}