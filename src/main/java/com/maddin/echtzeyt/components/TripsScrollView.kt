package com.maddin.echtzeyt.components

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.GestureDetector.OnGestureListener
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.alpha
import androidx.core.util.TypedValueCompat
import androidx.core.view.children
import androidx.core.view.doOnPreDraw
import androidx.core.view.updateLayoutParams
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FlingAnimation
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import com.maddin.echtzeyt.ECHTZEYT_CONFIGURATION
import com.maddin.echtzeyt.R
import com.maddin.echtzeyt.randomcode.DisablingParentScrollChild
import com.maddin.echtzeyt.randomcode.LazyMutable
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.math.absoluteValue
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.roundToLong


// this scale gesture detector is kind of inspired by androids default ScaleGestureDetector (see https://android.googlesource.com/platform/frameworks/base/+/68c65e58b4f4daf79c9ffab518a826a506799db2/core/java/android/view/ScaleGestureDetector.java)
// however due to the minimum span being gigantic (intended behaviour, obviously) and no way of changing it (see https://issuetracker.google.com/issues/37131665), i was forced to reimplement it
// i used the reimplementation to add some other features (i.e. independent scaleX and scaleY factors)
@Suppress("MemberVisibilityCanBePrivate")
class BetterScaleGestureDetector(private val mContext: Context, private val mListener: OnBetterScaleGestureListener) {
    interface OnBetterScaleGestureListener {
        fun onScaleStart(detector: BetterScaleGestureDetector, event: MotionEvent)
        fun onScale(detector: BetterScaleGestureDetector, event: MotionEvent)
        fun onScaleEnd(detector: BetterScaleGestureDetector, event: MotionEvent)
    }
    var focusX = 0f; private set
    var focusY = 0f; private set
    var currentSpan = 0f; private set
    var previousSpan = 0f; private set
    var initialSpan = 0f; private set
    var currentSpanX = 0f; private set
    var currentSpanY = 0f; private set
    var previousSpanX = 0f; private set
    var previousSpanY = 0f; private set
    var eventTime = 0L; private set
    var prevTime = 0L; private set
    var isInProgress = false; private set

    private val mViewConfig by lazy { ViewConfiguration.get(mContext) }
    private val minScaleSpanDef by lazy {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) { return@lazy 4 * mSpanSlop }
        return@lazy mViewConfig.scaledMinimumScalingSpan.coerceAtLeast(2 * mSpanSlop)
    }
    var minScaleSpanX by LazyMutable { minScaleSpanDef.toFloat() }
    var minScaleSpanY by LazyMutable { minScaleSpanDef.toFloat() }
    var maxDownscaleFactor = 20f // maximum amount of downscaling that can happen in one event
    var maxUpscaleFactor = 20f // maximum amount of upscaling that can happen in one event

    private val mSpanSlop by lazy { mViewConfig.scaledTouchSlop * 2 }

    fun onTouchEvent(event: MotionEvent) {
        eventTime = event.eventTime
        val action = event.actionMasked

        val streamComplete = (action == MotionEvent.ACTION_UP) || (action == MotionEvent.ACTION_CANCEL)
        if ((action == MotionEvent.ACTION_DOWN || streamComplete) && isInProgress) {
            isInProgress = false
            initialSpan = 0f
            mListener.onScaleEnd(this, event)
        }
        if (streamComplete) { return }

        val configChanged = (action == MotionEvent.ACTION_DOWN) || (action == MotionEvent.ACTION_POINTER_UP) || (action == MotionEvent.ACTION_POINTER_DOWN)
        val pointerUp = action == MotionEvent.ACTION_POINTER_UP
        val skipIndex = if (pointerUp) event.actionIndex else -1
        val count = event.pointerCount


        // Determine focal point
        var sumX = 0f
        var sumY = 0f
        val div = if (pointerUp) count - 1 else count
        for (i in 0 until count) {
            if (skipIndex == i) continue
            sumX += event.getX(i)
            sumY += event.getY(i)
        }
        focusX = sumX / div
        focusY = sumY / div

        // Determine average deviation from focal point
        var devSumX = 0f
        var devSumY = 0f
        for (i in 0 until count) {
            if (skipIndex == i) continue
            // Convert the resulting diameter into a radius.
            devSumX += (event.getX(i) - focusX).absoluteValue
            devSumY += (event.getY(i) - focusY).absoluteValue
        }
        val devX = devSumX / div
        val devY = devSumY / div

        // Span is the average distance between touch points through the focal point;
        // i.e. the diameter of the circle with a radius of the average deviation from
        // the focal point.
        val spanX = (devX * 2)
        val spanY = (devY * 2)
        val span = hypot(spanX.toDouble(), spanY.toDouble()).toFloat()
        // Dispatch begin/end events as needed.
        // If the configuration changes, notify the app to reset its current state by beginning
        // a fresh scale event stream.
        val wasInProgress = isInProgress
        if (configChanged) {
            isInProgress = false
            currentSpanX = spanX
            previousSpanX = currentSpanX
            currentSpanY = spanY
            previousSpanY = currentSpanY
            currentSpan = span
            previousSpan = currentSpan
            initialSpan = previousSpan
            mListener.onScaleEnd(this, event)
        }
        if (!isInProgress && (wasInProgress || (span - initialSpan).absoluteValue > mSpanSlop)) {
            currentSpanX = spanX
            previousSpanX = currentSpanX
            currentSpanY = spanY
            previousSpanY = currentSpanY
            currentSpan = span
            previousSpan = currentSpan
            prevTime = eventTime
            isInProgress = true
            mListener.onScaleStart(this, event)
        }

        // Handle motion; focal point and span/scale factor are changing.
        if (action == MotionEvent.ACTION_MOVE) {
            currentSpanX = spanX
            currentSpanY = spanY
            currentSpan = span
            if (isInProgress) { mListener.onScale(this, event) }
            previousSpanX = currentSpanX
            previousSpanY = currentSpanY
            previousSpan = currentSpan
            prevTime = eventTime
        }
    }

    private fun getScaleFactor(prevSpan: Float, curSpan: Float, minSpan: Float): Float {
        val add = (minSpan - prevSpan).coerceAtLeast(0f)
        val prev = prevSpan + add
        val curr = curSpan + add
        return (curr/prev).coerceIn(1f/maxDownscaleFactor, maxUpscaleFactor)
    }

    val scaleFactor; get() = getScaleFactor(previousSpan, currentSpan, 0f)
    val scaleFactorX; get() = getScaleFactor(previousSpanX, currentSpanX, minScaleSpanX)
    val scaleFactorY; get() = getScaleFactor(previousSpanY, currentSpanY, minScaleSpanY)
}

fun Canvas.clear() {
    drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
}

@Suppress("MemberVisibilityCanBePrivate")
class TripsScrollView : FrameLayout, OnGestureListener, BetterScaleGestureDetector.OnBetterScaleGestureListener, DisablingParentScrollChild {
    constructor(context: Context) : super(context) { initialize() }
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) { initialize() }
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { initialize() }

    private val tripsLayout by lazy { LinearLayout(context).apply {
        orientation=LinearLayout.HORIZONTAL
        this@TripsScrollView.addView(this, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    } }

    // scroll limits, the safe variants ensure that the scroll is not jumping
    // (i.e. if the user is out of bounds, only let him scroll inside the bounds but not further away
    //  but dont force him back instantly which would create an annoying jump;
    //  he will slowly be forced back into bounds using the scrollXAnimation and scrollYAnimation
    //  from recalculateScrollLimits() though)
    private var minScrollX = 0f; private val minScrollXSafe; get() = minScrollX.coerceAtMost(scrollX.toFloat())
    private var minScrollY = 0f; private val minScrollYSafe; get() = minScrollY.coerceAtMost(scrollY.toFloat())
    private var maxScrollX = 0f; private val maxScrollXSafe; get() = maxScrollX.coerceAtLeast(scrollX.toFloat())
    private var maxScrollY = 0f; private val maxScrollYSafe; get() = maxScrollY.coerceAtLeast(scrollY.toFloat())

    var maxHeightPerSecond by LazyMutable { TypedValueCompat.dpToPx(5f, resources.displayMetrics) }
    var minWidthPerTrip by LazyMutable { TypedValueCompat.dpToPx(40f, resources.displayMetrics) }
    var maxWidthPerTrip by LazyMutable { TypedValueCompat.dpToPx(140f, resources.displayMetrics) }
    var relativeWidthTripMargin = 0.2f

    var fadingEdgeLength = TypedValueCompat.dpToPx(36f, resources.displayMetrics)
    var fadingEdgeColor = ContextCompat.getColor(context, R.color.background)
    private val fadingEdgeColors; get() = intArrayOf(
        Color.TRANSPARENT,
        Color.argb((0.12f * Color.alpha(fadingEdgeColor)).roundToInt(), Color.red(fadingEdgeColor), Color.green(fadingEdgeColor), Color.blue(fadingEdgeColor)),
        Color.argb((0.30f * Color.alpha(fadingEdgeColor)).roundToInt(), Color.red(fadingEdgeColor), Color.green(fadingEdgeColor), Color.blue(fadingEdgeColor)),
        Color.argb((0.55f * Color.alpha(fadingEdgeColor)).roundToInt(), Color.red(fadingEdgeColor), Color.green(fadingEdgeColor), Color.blue(fadingEdgeColor)),
        Color.argb((0.81f * Color.alpha(fadingEdgeColor)).roundToInt(), Color.red(fadingEdgeColor), Color.green(fadingEdgeColor), Color.blue(fadingEdgeColor)),
        Color.argb((0.93f * Color.alpha(fadingEdgeColor)).roundToInt(), Color.red(fadingEdgeColor), Color.green(fadingEdgeColor), Color.blue(fadingEdgeColor)),
        fadingEdgeColor
    )

    var paintFadingEdgeBottom = Paint().apply {
        isDither = true
        shader = LinearGradient(0f, 0f, 0f, fadingEdgeLength, fadingEdgeColors, null, Shader.TileMode.CLAMP)
    }
    var paintFadingEdgeLeft = Paint().apply {
        isDither = true
        shader = LinearGradient(0f, 0f, fadingEdgeLength, 0f, fadingEdgeColors.reversedArray(), null, Shader.TileMode.CLAMP)
    }
    var paintFadingEdgeRight = Paint().apply {
        isDither = true
        shader = LinearGradient(0f, 0f, fadingEdgeLength, 0f, fadingEdgeColors, null, Shader.TileMode.CLAMP)
    }
    var paintFadingEdgeCornerBR = Paint().apply {
        isDither = true
        shader = RadialGradient(0f, 0f, fadingEdgeLength, fadingEdgeColors, null, Shader.TileMode.CLAMP)
    }
    var paintFadingEdgeCornerBL = Paint().apply {
        isDither = true
        shader = RadialGradient(fadingEdgeLength, 0f, fadingEdgeLength, fadingEdgeColors, null, Shader.TileMode.CLAMP)
    }

    private var currentHeightPerSecond = maxHeightPerSecond
    private var currentWidthPerTrip = minWidthPerTrip

    var isValid = false; private set
    private var timeStart = LocalDateTime.MIN
    private var timeEnd = LocalDateTime.MIN

    override val changeParentScrollListeners = mutableListOf<(Boolean) -> Unit>()

    val gestures = GestureDetector(context, this)
    val gesturesScale = BetterScaleGestureDetector(context, this)

    private fun initialize() {
        doOnPreDraw {
            recalculateScrollLimits()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (::bitmapGridPrev.isInitialized) { bitmapGridPrev.recycle() }
        if (::bitmapTimesPrev.isInitialized) { bitmapGridPrev.recycle() }
        bitmapGridPrev = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); canvasGridPrev = Canvas(bitmapGridPrev)
        bitmapTimesPrev = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); canvasTimesPrev = Canvas(bitmapTimesPrev)
        transferCurrentGrid()
        transferCurrentTimes()
        if (::bitmapGrid.isInitialized) { bitmapGrid.recycle() }
        if (::bitmapTimes.isInitialized) { bitmapTimes.recycle() }
        bitmapGrid = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); canvasGrid = Canvas(bitmapGrid)
        bitmapTimes = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); canvasTimes = Canvas(bitmapTimes)

        super.onSizeChanged(w, h, oldw, oldh)
        recalculateScrollLimits()
        redrawInternal()
    }

    override fun onDown(e: MotionEvent): Boolean {
        return true
    }

    override fun onShowPress(e: MotionEvent) {}

    override fun onSingleTapUp(e: MotionEvent): Boolean { return false }

    override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
        flingXAnimation.cancel()
        flingYAnimation.cancel()

        setScrollX((scrollX + distanceX).coerceIn(minScrollXSafe, maxScrollXSafe).roundToInt(), false)
        setScrollY((scrollY + distanceY).coerceIn(minScrollYSafe, maxScrollYSafe).roundToInt(), false)
        redrawInternal()

        // if the user seems to be explicitly trying to escape the fragment -> enable scroll for the fragment viewpager
        // a user is considered as trying to escape if
        //  - they are actively scrolling (i.e. not scaling)
        //  - they are scrolling mainly in the horizontal direction (very lax rule -> allow some slack in the angle)
        //  - they are either scrolling into the left or the right wall
        val scrollingMainlyInXDirection = (distanceX.absoluteValue > 0.8f * distanceY.absoluteValue)
        val scrollingIntoLeftWall = (distanceX < 0) && (scrollX < minScrollXSafe+2)
        val scrollingIntoRightWall = (distanceX > 0) && (scrollX > maxScrollXSafe-2)
        if (!isScaling && scrollingMainlyInXDirection && (scrollingIntoLeftWall || scrollingIntoRightWall)) { enableParentScroll() } else { disableParentScroll() }
        return true
    }

    override fun setScrollX(value: Int) {
        setScrollX(value, true)
    }

    private fun setScrollX(value: Int, invalidate: Boolean) {
        super.setScrollX(value)
        if (invalidate) { redrawInternal() }
    }

    private var lastScrollY = 0
    private var skipScrollYInvalidations = 0
    override fun setScrollY(value: Int) {
        setScrollY(value, true)
    }

    private fun setScrollY(value: Int, invalidate: Boolean) {
        super.setScrollY(value)

        if (skipScrollYInvalidations > 0) { skipScrollYInvalidations--; return }
        if ((value - lastScrollY).absoluteValue < 2) { return }

        lastScrollY = value

        tripsLayout.children.forEach { (it as? TripInfo)?.invalidateChildren() }
        if (invalidate) { redrawInternal() }
    }

    override fun onLongPress(e: MotionEvent) {}

    private val flingXAnimation = FlingAnimation(this, DynamicAnimation.SCROLL_X)
    private val flingYAnimation = FlingAnimation(this, DynamicAnimation.SCROLL_Y)
    private var nextFlingAllowed = 0L
    override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
        enableParentScroll()
        if (System.currentTimeMillis() < nextFlingAllowed) { return false }

        // TODO: fix this
        skipScrollYInvalidations = 1  // after starting the fling animation, the text inside the connection infos jumps for a brief moment, so we simply dont update the first 3 times after a fling animation started to avoid this behaviour
        startFlingAnimation(flingXAnimation, minScrollXSafe, scrollX.toFloat(), maxScrollXSafe, -velocityX)
        startFlingAnimation(flingYAnimation, minScrollYSafe, scrollY.toFloat(), maxScrollYSafe, -velocityY)

        return true
    }

    private fun startFlingAnimation(anim: FlingAnimation, min: Float, start: Float, max: Float, velocity: Float) {
        anim.cancel()
        anim.setMinValue(min)
        anim.setMaxValue(max)
        anim.setStartValue(start.coerceIn(min, max))
        anim.setStartVelocity(velocity)
        anim.start()
    }

    private val scrollXAnimation = ValueAnimator().apply {
        duration = 200
        startDelay = 50 // be able to cancel the "previous" animation on fast consequent updates before it even starts
        interpolator = LinearOutSlowInInterpolator()
        addUpdateListener { scrollX = (it.animatedValue as Float).roundToInt() }
    }
    private val scrollYAnimation = ValueAnimator().apply {
        duration = 200
        startDelay = 50 // be able to cancel the "previous" animation on fast consequent updates before it even starts
        interpolator = LinearOutSlowInInterpolator()
        addUpdateListener { scrollY = (it.animatedValue as Float).roundToInt() }
    }

    private fun recalculateScrollLimits(animateIntoBounds: Boolean=true) {
        if (tripsLayout.childCount == 0) {
            minScrollX = 0f
            minScrollY = 0f
            maxScrollX = 0f
            maxScrollY = 0f
            scrollX = 0
            scrollY = 0
            return
        }

        updateTripsLayout()

        minScrollX = - (lastLargestTimeWidth + 0.1f*width).coerceAtLeast(0.25f*width)
        minScrollY = - height * 0.3f
        maxScrollX = (currentWidthTrips - 0.75f*width).coerceAtLeast(minScrollX)
        maxScrollY = (currentHeightTrips - 0.7f*height).coerceAtLeast(minScrollY)

        val scrollXF = scrollX.toFloat()
        if (scrollXF !in minScrollX..maxScrollX) {
            scrollXAnimation.cancel()
            val goalX = scrollXF.coerceIn(minScrollX, maxScrollX)
            if (animateIntoBounds) {
                scrollXAnimation.setFloatValues(scrollXF, goalX)
                scrollXAnimation.start()
            } else if (!scrollXAnimation.isRunning) {
                scrollX = goalX.roundToInt()
            }
        }
        val scrollYF = scrollY.toFloat()
        if (scrollYF !in minScrollY..maxScrollY) {
            scrollYAnimation.cancel()
            val goalY = scrollYF.coerceIn(minScrollY, maxScrollY)
            if (animateIntoBounds) {
                scrollYAnimation.setFloatValues(scrollYF, goalY)
                scrollYAnimation.start()
            } else if (!scrollYAnimation.isRunning) {
                scrollY = goalY.roundToInt()
            }
        }
    }

    private var currentWidthTrips = 0f
    private var currentHeightTrips = 0f
    private fun updateTripsLayout() {
        currentWidthTrips = (getTripWidth() + getTripMarginLeft()) * tripsLayout.childCount
        currentHeightTrips = ChronoUnit.SECONDS.between(timeStart, timeEnd) * currentHeightPerSecond
        tripsLayout.updateLayoutParams {
            this.width = currentWidthTrips.roundToInt()
            this.height = currentHeightTrips.roundToInt()
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        val ret = super.dispatchTouchEvent(ev)
        if (ev == null) { return ret }

        if (ev.action == MotionEvent.ACTION_UP) { enableParentScroll() }

        gesturesScale.onTouchEvent(ev)
        return gestures.onTouchEvent(ev)
    }

    private var lastRedraw = 0L
    private val minTimeBetweenRedraws = 10L // -> max 100 frames per second, seems reasonable
    private fun redrawInternalDelayed(duration: Long) {
        val lastRedrawOnPost = lastRedraw
        postDelayed({
            if (lastRedraw != lastRedrawOnPost) { return@postDelayed }
            redrawInternal()
        }, duration)
    }

    private fun redrawInternal() {
        if (!isValid) { return }

        val timeNow = System.currentTimeMillis()
        val deltaSinceLastRedraw = (timeNow - lastRedraw).coerceAtLeast(0)
        if (deltaSinceLastRedraw < minTimeBetweenRedraws) {
            redrawInternalDelayed(minTimeBetweenRedraws - deltaSinceLastRedraw)
            return
        }
        lastRedraw = timeNow

        val gridPositions = calculateGridPositions()

        canvasGrid.clear()
        canvasTimes.clear()

        drawGrid(canvasGrid, gridPositions)
        drawTimes(canvasTimes, gridPositions)
        drawCurrentTime(canvasTimes)

        postInvalidate()
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (!isValid) { return }

        val xC = scrollX.toFloat()
        val yC = scrollY.toFloat()

        canvas.drawBitmap(bitmapGridPrev, xC, yC, paintPrev)
        canvas.drawBitmap(bitmapGrid, xC, yC, paintCurrent)

        super.dispatchDraw(canvas)
        drawFadingEdges(canvas)

        canvas.drawBitmap(bitmapTimesPrev, xC, yC, paintPrev)
        canvas.drawBitmap(bitmapTimes, xC, yC, paintCurrent)
    }

    private fun drawFadingEdges(canvas: Canvas) {
        drawFadingEdge(canvas, 0f, 0f, fadingEdgeLength, height-fadingEdgeLength, paintFadingEdgeLeft)
        drawFadingEdge(canvas, width-fadingEdgeLength, 0f, fadingEdgeLength, height-fadingEdgeLength, paintFadingEdgeRight)
        drawFadingEdge(canvas, fadingEdgeLength, height-fadingEdgeLength, width-2*fadingEdgeLength, fadingEdgeLength, paintFadingEdgeBottom)
        drawFadingEdge(canvas, width-fadingEdgeLength, height-fadingEdgeLength, fadingEdgeLength, fadingEdgeLength, paintFadingEdgeCornerBR)
        drawFadingEdge(canvas, 0f, height-fadingEdgeLength, fadingEdgeLength, fadingEdgeLength, paintFadingEdgeCornerBL)
    }
    private fun drawFadingEdge(canvas: Canvas, x: Float, y: Float, width: Float, height: Float, paint: Paint) {
        val saveCount = canvas.save()
        val xR = x + scrollX
        val yR = y + scrollY
        canvas.translate(xR, yR)
        canvas.clipRect(0f, 0f, width, height)
        canvas.drawRect(0f, 0f, width, height, paint)
        canvas.restoreToCount(saveCount)
    }

    private fun getRealStartTimeAfter(y: Float, timeUnit: Long): LocalDateTime {
        val timeDelta = floor(y / currentHeightPerSecond).roundToLong()
        val earliestStart = timeStart.plusSeconds(timeDelta).toEpochSecond(ZoneOffset.UTC)
        return LocalDateTime.ofEpochSecond((earliestStart/timeUnit)*timeUnit, 0, ZoneOffset.UTC)
    }

    private fun getYPositionOfTime(dateTime: LocalDateTime): Float {
        val timeDelta = ChronoUnit.SECONDS.between(timeStart, dateTime) + dateTime.nano * 1e-9f
        return timeDelta * currentHeightPerSecond - scrollY
    }

    var minHeightForTimeUnit by LazyMutable { 2.6f * heightTimeIndicator }
    //var minHeightForTimeUnitFade by LazyMutable { 1.2f * minHeightForTimeUnit }
    private val timeUnits = longArrayOf(
        ChronoUnit.MINUTES.duration.seconds,
        2 * ChronoUnit.MINUTES.duration.seconds,
        5 * ChronoUnit.MINUTES.duration.seconds,
        10 * ChronoUnit.MINUTES.duration.seconds,
        30 * ChronoUnit.MINUTES.duration.seconds,
        ChronoUnit.HOURS.duration.seconds,
        2 * ChronoUnit.HOURS.duration.seconds,
        5 * ChronoUnit.HOURS.duration.seconds,
        12 * ChronoUnit.HOURS.duration.seconds,
        ChronoUnit.DAYS.duration.seconds,
        3 * ChronoUnit.DAYS.duration.seconds,
        ChronoUnit.WEEKS.duration.seconds,
        2 * ChronoUnit.WEEKS.duration.seconds,
        ChronoUnit.MONTHS.duration.seconds,
        3 * ChronoUnit.MONTHS.duration.seconds,
        ChronoUnit.YEARS.duration.seconds
    )
    private fun getTimeUnit(minHeight: Float = minHeightForTimeUnit): Long {
        val minTimeUnit = minHeight / currentHeightPerSecond
        timeUnits.find { minTimeUnit < it }?.let { return it }
        val maxTimeUnit = timeUnits.last()
        return ceil(minTimeUnit/maxTimeUnit).roundToLong() * maxTimeUnit
    }

    private lateinit var bitmapGrid: Bitmap
    private lateinit var canvasGrid: Canvas
    private lateinit var bitmapTimes: Bitmap
    private lateinit var canvasTimes: Canvas
    private val paintCurrent = Paint().apply { alpha = 255 }
    private val animationCurrent = ValueAnimator.ofInt(0, 320).apply {
        addUpdateListener {
            val alpha = it.animatedValue as Int
            paintPrev.alpha = (320 - alpha).coerceIn(0, 255)
            paintCurrent.alpha = alpha.coerceIn(0, 255)
            redrawInternal()
        }
        duration = 250
    }
    private lateinit var bitmapGridPrev: Bitmap
    private lateinit var canvasGridPrev: Canvas
    private lateinit var bitmapTimesPrev: Bitmap
    private lateinit var canvasTimesPrev: Canvas
    private var unitPrev = 0L
        @Synchronized set
        @Synchronized get
    private val paintPrev = Paint().apply { alpha = 0 }

    private data class GridItem(var text: String, var x: Float, var y: Float, var alpha: Float)
    private fun makeGridItem(dateTime: LocalDateTime, y: Float, alpha: Float): GridItem {
        val text = ECHTZEYT_CONFIGURATION.formatDateTime(dateTime)
        val width = paintText.measureText(text) + 2 * paddingHorizontal
        return GridItem(text, width, y, alpha)
    }
    private fun calculateGridPositions(): List<GridItem> {
        val alphas = mutableListOf<GridItem>()

        val yNow = getYPositionOfTime(LocalDateTime.now())

        val topSafe = heightTimeIndicator / 2
        val unitPrevT = unitPrev
        unitPrev = getTimeUnit()

        if ((unitPrevT != unitPrev) && (unitPrevT != 0L)) {
            transferCurrentGrid()
            transferCurrentTimes()
            animationCurrent.cancel()
            animationCurrent.start()
        }

        var timeFadeOut = getRealStartTimeAfter(scrollY - topSafe, unitPrev)

        while (true) {
            val y = getYPositionOfTime(timeFadeOut)
            if (y > height + topSafe) { break }
            val alphaNow = (((y - yNow).absoluteValue - heightTimeIndicator) / distanceFadeTimeIndicator).coerceIn(0.3f, 1f)
            alphas.add(makeGridItem(timeFadeOut, y, alphaNow))
            timeFadeOut = timeFadeOut.plusSeconds(unitPrev)
        }
        return alphas
    }

    private fun transferCurrentGrid() {
        if (!::bitmapGrid.isInitialized) { return }
        if (!::canvasGridPrev.isInitialized) { return }
        canvasGridPrev.clear()
        canvasGridPrev.drawBitmap(bitmapGrid, 0f, 0f, null)
    }

    private fun transferCurrentTimes() {
        if (!::bitmapTimes.isInitialized) { return }
        if (!::canvasTimesPrev.isInitialized) { return }
        canvasTimesPrev.clear()
        canvasTimesPrev.drawBitmap(bitmapTimes, 0f, 0f, null)
    }

    private val colorGrid = ContextCompat.getColor(context, R.color.backgroundDivider)
    private val paintGrid = Paint().apply {
        color = colorGrid
        strokeWidth = resources.getDimension(R.dimen.divider_height)
    }
    private fun drawGridLineAt(canvas: Canvas, x: Float, y: Float, alpha: Float) {
        val left = 0f
        val right = left + width

        paintGrid.alpha = (alpha * colorGrid.alpha).roundToInt()
        canvas.drawLine(left+x, y, right, y, paintGrid)
    }
    private fun drawGrid(canvas: Canvas, grid: List<GridItem>) {
        for (item in grid) {
            drawGridLineAt(canvas, item.x, item.y, item.alpha)
        }
    }

    var textSizeTime by LazyMutable { 32f }
    var paddingHorizontal by LazyMutable { 16f }
    var paddingVertical by LazyMutable { 10f }
    private var heightTimeIndicator by LazyMutable { textSizeTime + 2 * paddingVertical }
    private val distanceFadeTimeIndicator by LazyMutable { heightTimeIndicator / 2 }
    private val paintTime = Paint().apply {
        color = ContextCompat.getColor(context, R.color.primary)
    }
    private val paintText = Paint().apply {
        color = ContextCompat.getColor(context, R.color.secondary)
        textSize = textSizeTime
    }
    private val rectText = RectF()
    private var lastLargestTimeWidth = 0f
    @Suppress("UNUSED_PARAMETER", "SameParameterValue")
    private fun drawGridTimeAt(canvas: Canvas, text: String, x: Float, y: Float, width: Float?, alpha: Float): Float {
        val left = 0f
        val widthR = width ?: (paintText.measureText(text) + 2 * paddingHorizontal)

        paintTime.alpha = (255 * alpha).roundToInt()
        paintText.alpha = (255 * alpha).roundToInt()

        rectText.set(left, y-heightTimeIndicator/2, left+widthR, y+heightTimeIndicator/2)
        canvas.drawRoundRect(rectText, heightTimeIndicator/2, heightTimeIndicator/2, paintTime)

        canvas.drawText(text, 0, text.length, left+paddingHorizontal, y+textSizeTime*0.35f, paintText)

        return widthR
    }
    private fun drawTimes(canvas: Canvas, grid: List<GridItem>) {
        val lastLargestTimeWidthT = lastLargestTimeWidth
        lastLargestTimeWidth = 0f

        for (item in grid) {
            val width = item.x
            drawGridTimeAt(canvas, item.text, 0f, item.y, width, item.alpha)
            lastLargestTimeWidth = lastLargestTimeWidth.coerceAtLeast(width)
        }

        if ((lastLargestTimeWidthT - lastLargestTimeWidth).absoluteValue > 5f) post { recalculateScrollLimits() }
    }

    private fun drawCurrentTime(canvas: Canvas) {
        val time = LocalDateTime.now()
        val y = getYPositionOfTime(time)

        // dont draw if the time indicator is out of bounds
        if (y < heightTimeIndicator/2) { return }
        if (y > height + heightTimeIndicator/2) { return }

        val text = resources.getString(R.string.dddepNow)

        val width = drawGridTimeAt(canvas, text, 0f, y, null, 1f)
        drawGridLineAt(canvas, width, y, 1f)

        // schedule an update such that the time is "moving"
        redrawInternalDelayed(redrawInterval)
    }

    private fun updateTripLayouts() {
        val width = getTripWidth()
        val marginLeft = getTripMarginLeft()
        for (child in tripsLayout.children) {
            if (child !is TripInfo) { continue }
            val height = getTripHeight(child)
            val marginTop = getTripMarginTop(child)

            child.updateLayoutParams<LinearLayout.LayoutParams> {
                this.width = width.roundToInt()
                this.height = height.roundToInt()
                this.topMargin = marginTop.roundToInt()
                this.leftMargin = marginLeft.roundToInt()
            }
        }
    }

    private fun getTripMarginLeft() = relativeWidthTripMargin * currentWidthPerTrip
    private fun getTripWidth() = currentWidthPerTrip
    private fun getTripMarginTop(trip: TripInfo) = ChronoUnit.SECONDS.between(timeStart, trip.start) * currentHeightPerSecond
    private fun getTripHeight(trip: TripInfo) = trip.duration * currentHeightPerSecond

    fun addTrip(trip: TripInfo) {
        val firstTrip = (tripsLayout.childCount == 0)
        var shouldUpdateOtherTrips = false
        if (trip.start < timeStart || firstTrip) {
            timeStart = trip.start
            shouldUpdateOtherTrips = true
        }
        if (trip.end > timeEnd || firstTrip) {
            timeEnd = trip.end
        }

        if (shouldUpdateOtherTrips) { updateTripLayouts() }

        val params = LinearLayout.LayoutParams(
            getTripWidth().roundToInt(),
            getTripHeight(trip).roundToInt()
        ).apply {
            leftMargin = getTripMarginLeft().roundToInt()
            topMargin = getTripMarginTop(trip).roundToInt()
        }
        tripsLayout.addView(trip, params)
        recalculateScrollLimits()
        isValid = true
    }

    fun removeAllTrips() {
        tripsLayout.removeAllViews()
        recalculateScrollLimits()
        isValid = false
    }

    private var isScaling = false
    private var scaleBufferX = 0f
    private var scaleBufferY = 0f
    private var focusMovingAverageX = 0f
    private var focusMovingAverageY = 0f
    private var focusMovingAverageLast = 0L
    private val focusMovingAverageDuration = 500L
    override fun onScaleStart(detector: BetterScaleGestureDetector, event: MotionEvent) {
        isScaling = true

        scaleBufferX = 0f
        scaleBufferY = 0f

        focusMovingAverageX = detector.focusX
        focusMovingAverageY = detector.focusY
        focusMovingAverageLast = event.eventTime
    }

    override fun onScale(detector: BetterScaleGestureDetector, event: MotionEvent) {
        val widthT = currentWidthPerTrip
        val heightT = currentHeightPerSecond

        val movingAverageFactor = (0f + (event.eventTime - focusMovingAverageLast) / focusMovingAverageDuration).coerceIn(0f, 1f)
        focusMovingAverageX = (1-movingAverageFactor) * focusMovingAverageX + movingAverageFactor * detector.focusX
        focusMovingAverageY = (1-movingAverageFactor) * focusMovingAverageY + movingAverageFactor * detector.focusY

        val focusX = scrollX + focusMovingAverageX
        val focusY = scrollY + focusMovingAverageY

        val wantedScaleFactorX = detector.scaleFactorX
        val wantedScaleFactorY = detector.scaleFactorY

        val calculatedScaleFactorX = wantedScaleFactorX + scaleBufferX // use up the scale buffer before actually scaling
        val calculatedScaleFactorY = wantedScaleFactorY + scaleBufferY // this allows a little bit of movement before scaling when the user "overscaled" or "underscaled"

        scaleBy(calculatedScaleFactorX, calculatedScaleFactorY, invalidate=false)

        val widthN = currentWidthPerTrip
        val heightN = currentHeightPerSecond

        val actualScaleFactorX = if (widthT > 0) widthN/widthT else 1f
        val actualScaleFactorY = if (heightT > 0) heightN/heightT else 1f

        scaleBufferX = (scaleBufferX + (wantedScaleFactorX - actualScaleFactorX)).coerceIn(-0.5f, 0.2f)
        scaleBufferY = (scaleBufferY + (wantedScaleFactorY - actualScaleFactorY)).coerceIn(-0.5f, 0.2f)

        // adjust the scrolls such that we are scaling "around" the center of the two scaling fingers
        // -> this basically acts like the pivot variables (or similar to the transform-origin property in css)
        val scrollXDisp = (focusX * (actualScaleFactorX - 1))
        val scrollYDisp = (focusY * (actualScaleFactorY - 1))
        setScrollX((scrollX + scrollXDisp).coerceIn(minScrollXSafe, maxScrollXSafe).roundToInt(), false)
        setScrollY((scrollY + scrollYDisp).coerceIn(minScrollYSafe, maxScrollYSafe).roundToInt(), false)

        // prevent this scale action or any related actions to cause flings
        nextFlingAllowed = System.currentTimeMillis() + 50

        redrawInternalDelayed(5)
    }

    override fun onScaleEnd(detector: BetterScaleGestureDetector, event: MotionEvent) {
        isScaling = false
    }

    fun scaleToContents() {
        val currentWidth = maxScrollX - minScrollX + width
        val currentHeight = maxScrollY - minScrollY + height
        if (currentWidth <= 0) { return }
        if (currentHeight <= 0) { return }
        scaleBy(width / currentWidth, height / currentHeight)
    }

    private fun scaleBy(factorX: Float, factorY: Float, invalidate: Boolean=true) {
        updateUnitSizes(currentWidthPerTrip * factorX, currentHeightPerSecond * factorY, invalidate=invalidate)
    }

    private fun recalculateWidthPerUnit(w: Float?=null) {
        val minWidth = minWidthPerTrip
        val maxWidth = maxWidthPerTrip
        currentWidthPerTrip = (w ?: currentWidthPerTrip).coerceIn(minWidth, maxWidth)
    }

    private fun recalculateHeightPerSecond(h: Float?=null) {
        val maxHeight = maxHeightPerSecond
        // all connections should fit in the window with some space on the top and bottom
        val duration = ChronoUnit.SECONDS.between(timeStart, timeEnd)
        val minHeight = (height.toFloat()/(2*duration)).coerceAtMost(maxHeight)
        currentHeightPerSecond = (h ?: currentHeightPerSecond).coerceIn(minHeight, maxHeight)
    }

    private var deltaYNoticeable = 2
    private var redrawInterval = 1000L
    private fun recalculateRedrawIntervals() {
        // recalculate redraw interval to ensure smooth animation of the "now" indicator
        // the now indicator itself should only be able to at most request 20 redraws per second (so every 50ms)
        // but it should also force a redraw after a long period (30s)
        redrawInterval = (1000f * (deltaYNoticeable / currentHeightPerSecond)).roundToLong().coerceIn(50, 30000)
    }

    private fun updateUnitSizes(widthPerUnit: Float?=null, heightPerSecond: Float?=null, invalidate: Boolean=true) {
        recalculateWidthPerUnit(widthPerUnit)
        recalculateHeightPerSecond(heightPerSecond)
        updateTripLayouts()
        recalculateScrollLimits(false)
        recalculateRedrawIntervals()
        if (invalidate) { redrawInternal() }
    }
}