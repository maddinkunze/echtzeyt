package com.maddin.echtzeyt.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.ViewCompat
import com.maddin.echtzeyt.ECHTZEYT_CONFIGURATION
import com.maddin.echtzeyt.randomcode.LineDrawable
import com.maddin.transportapi.components.Line
import com.maddin.transportapi.components.LineMOT
import com.maddin.transportapi.components.LineVariant
import com.maddin.transportapi.components.MOTType
import com.maddin.transportapi.components.ModeOfTransport
import kotlin.math.roundToInt

interface MOTTypeResolver {
    fun getDrawable(type: MOTType?): LineDrawable
    fun getLineNumber(type: MOTType?, line: Line, variant: LineVariant?) : String {
        return variant?.name ?: line.name ?: ""
    }
    fun getLineName(type: MOTType?, variant: LineVariant, line: Line?) : String {
        return variant.direction?.name ?: ""
    }
}

typealias MOTPredicate = (MOTType?) -> Boolean
typealias MOTPair<T> = Pair<MOTPredicate, T>
open class DefaultMOTTypeResolver : MOTTypeResolver {
    private val mDrawables = mutableListOf<MOTPair<LineDrawable>>()
    private val mLineNumberResolvers = mutableListOf<MOTPair<(MOTType?, Line, LineVariant?) -> String?>>()
    private val mLineNameResolvers = mutableListOf<MOTPair<(MOTType?, LineVariant, Line?) -> String?>>()
    lateinit var defaultDrawable: LineDrawable

    fun add(predicate: MOTPredicate, drawable: LineDrawable? = null,
            numberResolver: ((MOTType?, Line, LineVariant?) -> String?)? = null,
            nameResolver: ((MOTType?, LineVariant, Line?) -> String?)? = null
    ) {
        drawable?.let { mDrawables.add(MOTPair(predicate, it)) }
        numberResolver?.let { mLineNumberResolvers.add(MOTPair(predicate, it)) }
        nameResolver?.let { mLineNameResolvers.add(MOTPair(predicate, it)) }
    }

    private fun <T> List<MOTPair<T>>.find(type: MOTType?) : T? = find{ it.first(type) }?.second

    override fun getDrawable(type: MOTType?): LineDrawable {
        return mDrawables.find(type) ?: defaultDrawable
    }

    override fun getLineNumber(type: MOTType?, line: Line, variant: LineVariant?): String {
        return mLineNumberResolvers.find(type)?.invoke(type, line, variant) ?: super.getLineNumber(type, line, variant)
    }

    override fun getLineName(type: MOTType?, variant: LineVariant, line: Line?): String {
        return mLineNameResolvers.find(type)?.invoke(type, variant, line) ?: super.getLineName(type, variant, line)
    }
}

class VehicleTypeTextView : AppCompatTextView {
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context) : super(context)

    private fun setBackground(type: MOTType?) {
        val bg = ECHTZEYT_CONFIGURATION.motTypeResolver.getDrawable(type).copy()
        ViewCompat.setBackground(this, bg)
        setTextColor(bg.textColor)
        setPadding((bg.paddingLeft*textSize).roundToInt(), (bg.paddingTop*textSize).roundToInt(),
            (bg.paddingRight*textSize).roundToInt(), (bg.paddingBottom*textSize).roundToInt())
    }

    @SuppressLint("SetTextI18n")
    private fun setText(type: MOTType?, line: Line?, variant: LineVariant?, onlyNumber: Boolean = false) {
        val lineNumber = line?.let { ECHTZEYT_CONFIGURATION.motTypeResolver.getLineNumber(type, it, variant) } ?: ""
        val lineName = if (onlyNumber) "" else variant?.let { ECHTZEYT_CONFIGURATION.motTypeResolver.getLineName(type, it, line) } ?: ""
        val delimiter = if (lineNumber.isEmpty() || lineName.isEmpty()) "" else " "
        text = "$lineNumber$delimiter$lineName"
        setTypeface(null, Typeface.BOLD)
    }
    fun setLine(line: Line) {
        setBackground(line.defaultMOTType)
        setText(line.defaultMOTType, line, null, true)
    }
    fun setVariant(line: Line, variant: LineVariant) {
        setBackground(line.defaultMOTType)
        setText(line.defaultMOTType, line, variant)
    }
    fun setVehicle(mot: ModeOfTransport, onlyNumber: Boolean) {
        val type = mot.motType ?: (mot as? LineMOT)?.line?.defaultMOTType
        setBackground(type)
        setText(type, (mot as? LineMOT)?.line, (mot as? LineMOT)?.variant, onlyNumber)
    }
}