package com.maddin.echtzeyt.components

import android.annotation.SuppressLint
import android.content.Context
import android.text.Spannable
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.updatePadding
import com.maddin.echtzeyt.R
import com.maddin.transportapi.RealtimeConnection

private val STRIKE_THROUGH_SPAN = StrikethroughSpan()
fun TextView.setStrikeThrough() {
    setStrikeThrough(0, text.length)
}
fun TextView.setStrikeThrough(from: Int, length: Int) {
    setText(text, TextView.BufferType.SPANNABLE)
    (text as Spannable).setSpan(STRIKE_THROUGH_SPAN, from, from+length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
}

@SuppressLint("ViewConstructor", "SetTextI18n")
class RealtimeInfo(context: Context, connection: RealtimeConnection, odd: Boolean) : LinearLayout(context) {

    private val txtLineNumber by lazy { findViewById<TextView>(R.id.txtLineNumber) }
    private val txtLineName by lazy { findViewById<TextView>(R.id.txtLineName) }
    private val txtDepHours by lazy { findViewById<TextView>(R.id.txtLineTimeHour) }
    private val txtDepMins by lazy { findViewById<TextView>(R.id.txtLineTimeMin) }
    private val txtDepSecs by lazy { findViewById<TextView>(R.id.txtLineTimeSec) }

    private var shouldRespectMinSize: Boolean = false

    init {
        orientation = HORIZONTAL
        LayoutInflater.from(context).inflate(R.layout.comp_realtime_info, this)
        val padding = context.resources.getDimensionPixelSize(R.dimen.realtimeinfo_padding)
        updatePadding(top=padding, bottom=padding)


        if (odd) {
            setBackgroundResource(R.drawable.realtime_highlight)
        }

        updateConnection(connection)
    }

    fun measureForMaximumWidth(parent: ViewGroup) {
        measure(
            MeasureSpec.makeMeasureSpec(parent.width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(parent.height, MeasureSpec.EXACTLY)
        )
    }

    private fun getMaxWidthBeforeLayout(view: View, spec: Int): Int {
        view.measure(
            MeasureSpec.makeMeasureSpec(measuredWidth, spec),
            MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.UNSPECIFIED)
        )
        return view.measuredWidth
    }

    fun getMaxLineNumberWidth(spec: Int = MeasureSpec.AT_MOST): Int {
        return getMaxWidthBeforeLayout(txtLineNumber, spec)
    }

    fun getLineNumberWidth(): Int {
        return txtLineNumber.width
    }

    fun setLineNumberMinWidth(width: Int) {
        txtLineNumber.minWidth = width
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
        txtLineNumber.text = connection.vehicle.line?.name ?: ""
        txtLineName.text = connection.vehicle.direction?.name ?: ""

        val departsIn = connection.departsIn().coerceAtLeast(0)
        val depSecs = departsIn % 60
        val depMins = (departsIn / 60) % 60
        val depHours = departsIn / 3600
        var padMin = 0
        if (depHours > 0) {
            txtDepHours.visibility = VISIBLE
            padMin = 2
            shouldRespectMinSize = true
        }
        txtDepHours.text = "${depHours}h"
        txtDepMins.text = "${depMins.toString().padStart(padMin, '0')}m"
        txtDepSecs.text = "${depSecs.toString().padStart(2, '0')}s"

        if (connection.isStopCancelled()) {
            txtLineNumber.setStrikeThrough()
            txtLineName.setStrikeThrough()
            txtLineNumber.alpha = 0.3f
            txtLineName.alpha = 0.3f
            txtDepHours.alpha = 0.5f
            txtDepMins.alpha = 0.5f
            txtDepSecs.alpha = 0.5f
        }
    }
}