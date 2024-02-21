package com.maddin.echtzeyt.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.children
import androidx.core.view.marginTop
import com.maddin.echtzeyt.ECHTZEYT_CONFIGURATION
import com.maddin.echtzeyt.R
import com.maddin.echtzeyt.randomcode.IconLineDrawable
import com.maddin.echtzeyt.randomcode.LineDrawable
import com.maddin.transportapi.components.Trip
import com.maddin.transportapi.components.TripConnection
import com.maddin.transportapi.components.VehicleTypes
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

class TripInfo : LinearLayout {
    constructor(context: Context) : super(context) { init() }
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) { init(attrs) }
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { init(attrs, defStyleAttr) }

    private fun init(attrs: AttributeSet?=null, defStyleAttr: Int?=0) {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
    }

    private fun onTripUpdated() {
        start = trip?.startActual ?: LocalDateTime.MIN
        end = trip?.endActual ?: start
        duration = trip?.duration?.seconds ?: 0
        setOnClickListener { _ -> trip?.let { onClickListener?.invoke(it, null) } }
        updateConnections()
    }

    private var pointerDownX = Float.NaN
    private var pointerDownY = Float.NaN
    private val mSlop by lazy { ViewConfiguration.get(context).scaledTouchSlop }
    override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
        if (event?.action == MotionEvent.ACTION_DOWN) {
            pointerDownX = event.rawX
            pointerDownY = event.rawY
        }
        if (event?.action == MotionEvent.ACTION_UP) {
            val downX = pointerDownX
            val downY = pointerDownY
            pointerDownX = Float.NaN
            pointerDownY = Float.NaN
            if (downX.isNaN() || (event.rawX - downX).absoluteValue > mSlop) { return false }
            if (downY.isNaN() || (event.rawY - downY).absoluteValue > mSlop) { return false }
        }
        return super.onTouchEvent(event)
    }

    var trip: Trip? = null
        set(value) { field = value; onTripUpdated() }
    var start: LocalDateTime = LocalDateTime.MIN
    var end: LocalDateTime = LocalDateTime.MIN
    var duration = 0L

    private fun updateConnections() {
        removeAllViews()
        val trip = this.trip ?: return
        var i = true
        var connectionLast: TripConnection? = null
        for (connection in trip.connections) {
            val timeDeltaToPrevious = connectionLast?.let { ChronoUnit.SECONDS.between(it.endActual, connection.startActual) } ?: 0
            if (timeDeltaToPrevious > 0) {
                val view = View(context)
                view.setBackgroundResource(R.drawable.line_wait)
                addView(view, LayoutParams(view.background.intrinsicWidth, 0, timeDeltaToPrevious.toFloat()))
            }

            // TODO: what if the data is weird/invalid and the next connection starts before the last ends (i.e. timeDeltaPrevious < 0)
            // in this case the next connection is not displayed correctly as it is forced below the previous connection -> fix?

            val view = ConnectionInfo(context)
            view.setConnection(connection)
            view.setOnClickListener { onClickListener?.let { it(trip, connection) } }
            addView(view, LayoutParams(LayoutParams.MATCH_PARENT, 0, (connection.duration?.seconds?:0).toFloat()))

            i = !i
            connectionLast = connection
        }
    }

    fun invalidateChildren() {
        invalidate()
        children.forEach { it.invalidate() }
    }

    private var onClickListener: ((trip: Trip, connection: TripConnection?) -> Unit)? = null
    fun setOnClickListener(listener: (trip: Trip, connection: TripConnection?) -> Unit) {
        onClickListener = listener
    }
    fun removeOnClickListener() {
        onClickListener = null
    }
}

class ConnectionInfo : View {
    constructor(context: Context) : super(context) { init() }
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) { init(attrs) }
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { init(attrs, defStyleAttr) }

    private fun init(attrs: AttributeSet?=null, defStyleAttr: Int?=0) {
        //inflate(context, R.layout.comp_connection_info, this)
    }

    private lateinit var drawable: LineDrawable
    private lateinit var connection: TripConnection
    fun setConnection(connection: TripConnection) {
        this.connection = connection

        val isDefaultWalkType = (connection.vehicle?.type is VehicleTypes) && (connection.vehicle?.type?.isSubtypeOf(
            VehicleTypes.WALK) == true)

        drawable = ECHTZEYT_CONFIGURATION.vehicleTypeResolver.getDrawable(connection.vehicle?.type)
        iconLine = (drawable as? IconLineDrawable)?.icon?.mutate()
        iconLine?.let { DrawableCompat.setTint(it, drawable.textColor) }
        paintBackground.color = drawable.backColor
        paintTextLineNumber.color = drawable.textColor
        paintTextLineName.color = drawable.textColor
        paintTextInfo.color = drawable.hintColor

        primaryTextElements.clear()
        secondaryTextElements.clear()

        if (!isDefaultWalkType) {
            connection.vehicle?.line?.let {
                val lineShort = ECHTZEYT_CONFIGURATION.vehicleTypeResolver.getLineNumber(connection.vehicle?.type, it)
                val lineLong = it.name ?: lineShort
                if (lineLong.isBlank()) { return@let }
                primaryTextElements.add(ResizeableTextElement(lineShort, lineLong, paintTextLineNumber))
            }
            connection.vehicle?.direction?.name?.let { primaryTextElements.add(TextElement(it, paintTextLineName)) }

            secondaryTextElements.add(TextElement("${connection.stops.size} stops", paintTextInfo))
        }
        connection.duration?.let { d -> secondaryTextElements.add(TextElement(formatDuration(d.seconds), paintTextInfo, iconTime)) }

        fadeOutBottom = !isDefaultWalkType
        updateGradient()
    }

    private fun formatDuration(duration: Long): String {
        if (duration <= 0) { return "" }

        val seconds = duration % 60
        if (duration < 60) { return "${seconds}s" }

        val minutes = (duration / 60) % 60
        val hours = duration / 3600
        return if (hours > 0) { "${hours}h ${minutes}m" } else { "${minutes}m" }
    }

    // get safe y position to draw text that will be visible as long as possible (center of an item of given height)
    // try to position the text in the center of the visible portion, otherwise snap it to the top or bottom (depending on which way the user is scrolling)
    private fun getSafeYPosition(itemHeight: Float): Float {
        val scrollLayout = (parent.parent.parent as View)
        val parentScroll = scrollLayout.scrollY
        val parentHeight = scrollLayout.height
        val posYRelativeToScrollableParent = top + (parent as View).marginTop

        val topEdgeY = (parentScroll - posYRelativeToScrollableParent).coerceAtLeast(0)
        val bottomEdgeYIfCutOff = (parentScroll + parentHeight) - posYRelativeToScrollableParent
        val bottomEdgeY = (topEdgeY + parentHeight).coerceAtMost(bottomEdgeYIfCutOff).coerceAtMost(height)

        return ((topEdgeY + bottomEdgeY)/2f).coerceAtLeast(topEdgeY + itemHeight/2)
    }

    private val paintBackground = Paint()
    private val paintBackgroundFade = Paint().apply { isDither = true }
    private val paintTextLineNumber = TextPaint().apply { textSize = 32f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    private val paintTextLineName = TextPaint().apply { textSize = 28f }
    private val paintTextInfo = TextPaint().apply { textSize = 24f }
    private var iconLine: Drawable? = null
    private val iconTime by lazy { ContextCompat.getDrawable(context, R.drawable.ic_time_duration) }
    private val iconStationCount by lazy { ContextCompat.getDrawable(context, R.drawable.ic_stations_count) }
    private var fadeOutBottom = true

    private val radius = 25f
    private val rectDraw = RectF(0f, 0f, 0f, 0f)
    private open class TextElement(val text: String, val paint: TextPaint, val icon: Drawable?=null) {
        val height = paint.textSize
        val width = paint.measureText(text)
    }
    private open class ResizeableTextElement(val textShort: String, val textLong: String, paint: TextPaint, icon: Drawable?=null) : TextElement(textLong, paint, icon) {
        val widthShort = paint.measureText(textShort)
        val widthLong = paint.measureText(textLong)
    }
    private val primaryTextElements = mutableListOf<TextElement>()
    private val secondaryTextElements = mutableListOf<TextElement>()
    override fun onDraw(canvas: Canvas) {
        val saveCount = canvas.save()
        canvas.clipPath(clipPath)

        canvas.drawRect(rectDraw, paintBackground)

        var preferredHeight = 2 * paddingVertical
        val availableWidth = width - 2 * paddingHorizontal

        val icon = iconLine
        var iconAboveText = true
        val iconSizeFactor = ((drawable as? IconLineDrawable)?.iconSize?.toFloat() ?: 1f)
        var iconWidth = (50f * iconSizeFactor).coerceAtMost(availableWidth)
        var iconHeight = 0f

        icon?.let {
            val firstPrimaryText = primaryTextElements.getOrNull(0) ?: return@let
            if (firstPrimaryText.icon != null) { return@let }

            val widthT = (firstPrimaryText as? ResizeableTextElement)?.widthShort ?: firstPrimaryText.width
            val iconWidthT = firstPrimaryText.height * (it.intrinsicWidth / it.intrinsicHeight) * iconSizeFactor
            val widthRequired = widthT + iconWidthT + paddingHorizontalIcon
            if (widthRequired > availableWidth) { return@let }

            iconAboveText = false
            iconWidth = iconWidthT
        }
        icon?.let {
            iconHeight = iconWidth * (it.intrinsicHeight / it.intrinsicHeight)
        }
        if (iconAboveText) {
            preferredHeight += iconHeight
        }

        for (item in primaryTextElements) {
            preferredHeight += item.height
        }
        for (item in secondaryTextElements) {
            preferredHeight += item.height
        }
        preferredHeight += (secondaryTextElements.size - 1).coerceAtLeast(0) * paddingVerticalBetween
        if (primaryTextElements.isNotEmpty() && secondaryTextElements.isNotEmpty()) {
            preferredHeight += paddingVerticalBetweenPS
        }

        val safeY = getSafeYPosition(preferredHeight)
        var y = safeY - preferredHeight / 2 + paddingVertical

        val primaryTextsIterator = primaryTextElements.iterator()
        icon?.let {
            var xI = ((width - iconWidth) / 2).roundToInt()
            val yI = y.roundToInt()
            if (!iconAboveText) {
                val item = primaryTextsIterator.next()
                val widthT = (item as? ResizeableTextElement)?.widthShort ?: item.width
                val widthRequired = widthT + iconWidth + paddingHorizontalIcon
                xI = ((width - widthRequired) / 2).roundToInt()
                val textT = (item as? ResizeableTextElement)?.textShort ?: item.text
                val paddingY = (iconHeight - item.height) / 2
                canvas.drawText(textT, xI + iconWidth + paddingHorizontalIcon, yI+paddingY+item.height*0.87f, item.paint)
            }
            icon.setBounds(xI, yI, xI+iconWidth.roundToInt(), yI+iconHeight.roundToInt())
            icon.draw(canvas)
            y += iconHeight
        }

        for (textItem in primaryTextsIterator) {
            y += drawTextItem(canvas, y, textItem)
        }
        y += paddingVerticalBetweenPS
        for (textItem in secondaryTextElements) {
            y += drawTextItem(canvas, y, textItem)
            y += paddingVerticalBetween
        }

        if (fadeOutBottom) canvas.drawRect(rectDraw, paintBackgroundFade)

        canvas.restoreToCount(saveCount)
    }

    private val fadingEdgeLength = 50f
    private val paddingVertical = 8f
    private val paddingVerticalBetween = 5f
    private val paddingVerticalBetweenPS = 20f // space between primary and secondary texts
    private val paddingHorizontal = 10f
    private val paddingHorizontalIcon = 5f
    private fun drawTextItem(canvas: Canvas, y: Float, item: TextElement, center: Boolean=true): Float {
        var currentX = paddingHorizontal
        if (center) { currentX = ((width - item.width) / 2).coerceAtLeast(paddingHorizontal) }

        item.icon?.let {
            val heightI = item.height
            val widthI = heightI * (it.intrinsicWidth / it.intrinsicHeight)
            if (center) {
                val requiredWidth = item.width + widthI + paddingHorizontalIcon
                currentX = ((width - requiredWidth) / 2).coerceAtLeast(paddingHorizontal)
            }
            val xI = currentX.roundToInt()
            val yI = y.roundToInt()
            it.setBounds(xI, yI, xI+widthI.roundToInt(), yI+heightI.roundToInt())
            DrawableCompat.setTint(it, item.paint.color)
            it.draw(canvas)
            currentX += widthI + paddingHorizontalIcon
        }
        val avail = width - paddingHorizontal - currentX
        val text = TextUtils.ellipsize(item.text, item.paint, avail, TextUtils.TruncateAt.END).toString()
        canvas.drawText(text, currentX, y+0.87f*item.height, item.paint)
        return item.height
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        rectDraw.right = w.toFloat()
        rectDraw.bottom = h.toFloat()

        updateGradient()
        updateClipPath()
    }

    private fun updateGradient() {
        if (!fadeOutBottom) { return }
        if (!::drawable.isInitialized) { return }
        val h = height.toFloat()
        val f = fadingEdgeLength.coerceAtMost(h/2)
        paintBackgroundFade.shader = LinearGradient(0f, h-f, 0f, h, Color.TRANSPARENT, drawable.backColor, Shader.TileMode.CLAMP)
    }

    private val clipPath = Path()
    private fun updateClipPath() {
        clipPath.reset()
        clipPath.addRoundRect(rectDraw, radius, radius, Path.Direction.CCW)
        clipPath.close()
    }
}