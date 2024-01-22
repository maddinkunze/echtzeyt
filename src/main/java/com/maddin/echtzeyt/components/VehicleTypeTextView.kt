package com.maddin.echtzeyt.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Parcelable
import android.util.AttributeSet
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.updatePadding
import com.maddin.echtzeyt.ECHTZEYT_CONFIGURATION
import com.maddin.echtzeyt.R
import com.maddin.echtzeyt.randomcode.LineDrawable
import com.maddin.transportapi.Direction
import com.maddin.transportapi.Line
import com.maddin.transportapi.Vehicle
import com.maddin.transportapi.VehicleType
import kotlin.math.roundToInt

interface VehicleTypeResolver {
    fun getDrawable(type: VehicleType?): LineDrawable
    fun getLineNumber(type: VehicleType?, line: Line) : String {
        return line.name
    }
    fun getLineName(type: VehicleType?, direction: Direction) : String {
        return direction.name
    }
}

open class DefaultVehicleTypeResolver : VehicleTypeResolver {
    protected val mDrawables = mutableMapOf<VehicleType, LineDrawable>()
    protected val mLineNumberResolvers = mutableMapOf<VehicleType, (VehicleType, Line) -> String?>()
    protected val mLineNameResolvers = mutableMapOf<VehicleType, (VehicleType, Direction) -> String?>()
    lateinit var defaultDrawable: LineDrawable

    // breadth first search
    protected fun <T> resolveVehicleType(types: List<VehicleType>, map: Map<VehicleType, T>) : T? {
        if (types.isEmpty()) { return null }
        val typesN = mutableListOf<VehicleType>()
        for (type in types) {
            if (type in map) {
                return map[type]
            }
            typesN.addAll(type.supertypes)
        }
        return resolveVehicleType(typesN, map)
    }
    override fun getDrawable(type: VehicleType?): LineDrawable {
        return resolveVehicleType(listOfNotNull(type), mDrawables) ?: defaultDrawable
    }

    fun add(type: VehicleType, drawable: LineDrawable? = null, numberResolver: ((VehicleType, Line) -> String?)? = null, nameResolver: ((VehicleType, Direction) -> String?)? = null) {
        drawable?.let { mDrawables[type] = it }
        numberResolver?.let { mLineNumberResolvers[type] = it }
        nameResolver?.let { mLineNameResolvers[type] = it }
    }

    override fun getLineNumber(type: VehicleType?, line: Line): String {
        return resolveVehicleType(listOfNotNull(type), mLineNumberResolvers)?.let { it(type!!, line) }
            ?: super.getLineNumber(type, line)
    }

    override fun getLineName(type: VehicleType?, direction: Direction): String {
        return resolveVehicleType(listOfNotNull(type), mLineNameResolvers)?.let { it(type!!, direction) }
            ?: super.getLineName(type, direction)
    }
}

class VehicleTypeTextView : AppCompatTextView {
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context) : super(context)

    private fun setBackground(type: VehicleType?) {
        val bg = ECHTZEYT_CONFIGURATION.vehicleTypeResolver.getDrawable(type).copy()
        ViewCompat.setBackground(this, bg)
        setTextColor(bg.textColor)
        setPadding((bg.paddingLeft*textSize).roundToInt(), (bg.paddingTop*textSize).roundToInt(),
            (bg.paddingRight*textSize).roundToInt(), (bg.paddingBottom*textSize).roundToInt())
    }

    @SuppressLint("SetTextI18n")
    private fun setText(type: VehicleType?, line: Line?, direction: Direction?, onlyNumber: Boolean = false) {
        val lineNumber = line?.let { ECHTZEYT_CONFIGURATION.vehicleTypeResolver.getLineNumber(type, it) } ?: ""
        val lineName = if (onlyNumber) "" else direction?.let { ECHTZEYT_CONFIGURATION.vehicleTypeResolver.getLineName(type, it) }
        val delimiter = if (lineNumber.isEmpty() || lineName.isNullOrEmpty()) "" else " "
        text = "$lineNumber$delimiter$lineName"
        setTypeface(null, Typeface.BOLD)
    }
    fun setLine(line: Line) {
        setBackground(line.defaultVehicleType)
        setText(line.defaultVehicleType, line, null, true)
    }
    fun setDirection(line: Line, direction: Direction) {
        setBackground(line.defaultVehicleType)
        setText(line.defaultVehicleType, line, direction)
    }
    fun setVehicle(vehicle: Vehicle, onlyNumber: Boolean) {
        val type = vehicle.type ?: vehicle.line?.defaultVehicleType
        setBackground(type)
        setText(type, vehicle.line, vehicle.direction, onlyNumber)
    }
}