package com.maddin.echtzeyt.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources.NotFoundException
import android.text.Spannable
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import com.maddin.echtzeyt.ECHTZEYT_CONFIGURATION
import com.maddin.echtzeyt.R
import com.maddin.echtzeyt.randomcode.LazyView
import com.maddin.transportapi.components.RealtimeConnection
import kotlin.math.absoluteValue

private val STRIKE_THROUGH_SPAN = StrikethroughSpan()
fun TextView.setStrikeThrough() {
    setStrikeThrough(0, text.length)
}
fun TextView.setStrikeThrough(from: Int, length: Int) {
    setText(text, TextView.BufferType.SPANNABLE)
    (text as Spannable).setSpan(STRIKE_THROUGH_SPAN, from, from+length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
}

@SuppressLint("ViewConstructor", "SetTextI18n")
class RealtimeInfo(context: Context, val connection: RealtimeConnection, odd: Boolean) : LinearLayout(context) {

    private val txtLineNumber: TextView by LazyView(R.id.txtLineNumber)
    private val txtLineNumberIcon: VehicleTypeTextView by LazyView(R.id.txtLineNumberIcon)
    private val txtLineName: TextView by LazyView(R.id.txtLineName)
    private val txtDepNeg: TextView by LazyView(R.id.txtLineTimeNeg)
    private val txtDepHours: TextView by LazyView(R.id.txtLineTimeHour)
    private val txtDepMins: TextView by LazyView(R.id.txtLineTimeMin)
    private val txtDepSecs: TextView by LazyView(R.id.txtLineTimeSec)
    private val txtDepTime: TextView by LazyView(R.id.txtLineTime)

    private var shouldRespectMinSize: Boolean = false

    init {
        orientation = HORIZONTAL
        LayoutInflater.from(context).inflate(R.layout.comp_realtime_info, this)
        val padding = context.resources.getDimensionPixelSize(R.dimen.realtimeinfo_padding)
        updatePadding(top=padding, bottom=padding)


        if (odd) {
            setBackgroundOdd()
        }

        updateConnection(connection)
    }

    fun setBackgroundOdd() {
        try {
            setBackgroundResource(R.drawable.realtime_highlight)
        } catch (_: NotFoundException) {
            ViewCompat.setBackground(this, VectorDrawableCompat.create(resources, R.drawable.realtime_highlight, null))
        }
    }

    fun measureForMaximumWidth(parent: ViewGroup) {
        measure(
            MeasureSpec.makeMeasureSpec(parent.width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(parent.height, MeasureSpec.EXACTLY)
        )
    }

    private fun getMaxWidthBeforeLayout(view: View, spec: Int): Int {
        if (!view.isVisible) { return 0 }
        view.measure(
            MeasureSpec.makeMeasureSpec(measuredWidth, spec),
            MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.UNSPECIFIED)
        )
        return view.measuredWidth
    }

    fun getMaxLineNumberWidth(spec: Int = MeasureSpec.AT_MOST): Int {
        val view = if (txtLineNumberIcon.isVisible) txtLineNumberIcon else txtLineNumber
        return getMaxWidthBeforeLayout(view, spec)
    }

    fun getLineNumberWidth(): Int {
        return txtLineNumber.width
    }

    fun setLineNumberMinWidth(width: Int) {
        val view = if (txtLineNumberIcon.isVisible) txtLineNumberIcon else txtLineNumber
        view.minWidth = width
    }

    fun setLineNumberMarginLeft(margin: Int) {
        txtLineNumber.updateLayoutParams<LayoutParams> { leftMargin = margin }
        txtLineNumberIcon.updateLayoutParams<LayoutParams> { leftMargin = margin }
    }

    fun getMaxSecondsWidth(spec: Int = MeasureSpec.AT_MOST): Int {
        return getMaxWidthBeforeLayout(txtDepSecs, spec)
    }

    fun getSecondsWidth(): Int {
        return txtDepSecs.width
    }

    fun setSecondsMinWidth(width: Int) {
        txtDepSecs.minWidth = width
    }

    fun getMaxMinutesWidth(spec: Int = MeasureSpec.AT_MOST): Int {
        return getMaxWidthBeforeLayout(txtDepMins, spec)
    }

    fun getMinutesWidth(): Int {
        return txtDepMins.width
    }

    fun setMinutesMinWidth(width: Int) {
        if (!shouldRespectMinSize) { return }
        txtDepMins.minWidth = width
    }

    fun updateConnection(connection: RealtimeConnection) {
        val useIcons = ECHTZEYT_CONFIGURATION.useIconsInRealtimeView()
        txtLineNumber.visibility = if (useIcons) GONE else VISIBLE
        txtLineNumberIcon.visibility = if (useIcons) VISIBLE else GONE
        val vehicle = connection.vehicle
        if (useIcons && vehicle != null) {
            txtLineNumberIcon.setVehicle(vehicle, onlyNumber=true)
        } else {
            txtLineNumber.text = vehicle?.line?.name ?: ""
        }
        txtLineName.text = vehicle?.direction?.name ?: ""

        var departsIn = connection.departsOrArrivesIn()?.seconds ?: 0
        val showAbsoluteTimeAfter = ECHTZEYT_CONFIGURATION.getRealtimeShowTimeAfter()
        val showAbsoluteTimeAfterPast = ECHTZEYT_CONFIGURATION.getRealtimeShowTimeAfterPast()
        val showAbsoluteTime = ((showAbsoluteTimeAfter >= 0) && (departsIn >= showAbsoluteTimeAfter * 60)) // the connection is too far in the future -> it does not make sense to display departure in 200h
                || ((showAbsoluteTimeAfterPast >= 0) && (-departsIn >= showAbsoluteTimeAfterPast)) // the connection is too far in the past

        val showNegativeTime = ECHTZEYT_CONFIGURATION.shouldShowNegativeRealtimeWaitingTimes()
        if (!showNegativeTime) { departsIn = departsIn.coerceAtLeast(0) }

        txtDepNeg.visibility = if (showAbsoluteTime || departsIn >= 0) GONE else  VISIBLE
        txtDepHours.visibility = GONE
        txtDepMins.visibility = if (showAbsoluteTime) GONE else VISIBLE
        txtDepSecs.visibility = if (showAbsoluteTime) GONE else VISIBLE
        txtDepTime.visibility = if (showAbsoluteTime) VISIBLE else GONE

        if (showAbsoluteTime) {
            txtDepTime.text = ECHTZEYT_CONFIGURATION.formatDateTime(connection.stop.departureActual)
        } else {
            departsIn = departsIn.absoluteValue
            val depSecs = departsIn % 60
            val depMins = (departsIn / 60) % 60
            val depHours = departsIn / 3600
            var padMin = 0

            txtDepSecs.visibility = VISIBLE
            txtDepMins.visibility = VISIBLE
            if (depHours > 0) {
                txtDepHours.visibility = VISIBLE
                padMin = 2
                shouldRespectMinSize = true
            }
            txtDepHours.text = "${depHours}h"
            txtDepMins.text = "${depMins.toString().padStart(padMin, '0')}m"
            txtDepSecs.text = "${depSecs.toString().padStart(2, '0')}s"
        }

        if (connection.isStopCancelled) {
            txtLineNumber.setStrikeThrough()
            txtLineNumberIcon.setStrikeThrough()
            txtLineName.setStrikeThrough()
            txtLineNumber.alpha = 0.3f
            txtLineNumberIcon.alpha = 0.3f
            txtLineName.alpha = 0.3f
            txtDepNeg.alpha = 0.5f
            txtDepHours.alpha = 0.5f
            txtDepMins.alpha = 0.5f
            txtDepSecs.alpha = 0.5f
            txtDepTime.alpha = 0.5f
        }
    }
}