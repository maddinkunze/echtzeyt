package com.maddin.echtzeyt.randomcode

import kotlin.math.pow

class BezierAnimator {
    private var x0 = 0.0
    private var x1 = 0.0
    private var x2 = 0.0
    private var x3 = 0.0

    private var timeStart = 0L
    private var duration = 0L

    fun setValue(x: Double) {
        synchronized(this) {
            x0 = x; x1 = x; x2 = x; x3 = x
            timeStart = 0L
            duration = 0L
        }
    }

    fun setTargetValue(x: Double, duration: Long) {
        synchronized(this) {
            val cx = this.value
            val vx = this.vx
            x0 = cx; x1 = (vx/3) + cx; x2 = x; x3 = x
            this.timeStart = now()
            this.duration = duration
        }
    }

    fun cancel() { setValue(this.value) }

    private fun now() = System.currentTimeMillis()

    private val t; get() = if (duration > 0) { ((now() - timeStart) / duration.toDouble()).coerceIn(0.0, 1.0) } else { 1.0 }
    val value; get() = synchronized(this) { t.let { t -> (1-t).pow(3) * x0 + 3 * (1-t).pow(2) * t * x1 + 3 * (1-t) * t.pow(2) * x2 + t.pow(3) * x3 } }
    private val vx; get() = synchronized(this) { t.let { 3 * (1-t).pow(2) * (x1-x0) + 6 * (1-t) * t * (x2-x1) + 3 * t.pow(2) * (x3-x2) } }
    val isRunning; get() = synchronized(this) { (duration > 0) && (now() - timeStart < duration) }
}

class BezierAnimator2D {
    private var x0 = 0.0
    private var x1 = 0.0
    private var x2 = 0.0
    private var x3 = 0.0

    private var y0 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0
    private var y3 = 0.0

    private var timeStart = 0L
    private var duration = 0L

    fun setPosition(x: Double, y: Double) {
        synchronized(this) {
            x0 = x; x1 = x; x2 = x; x3 = x
            y0 = y; y1 = y; y2 = y; y3 = y
            timeStart = 0L
            duration = 0L
        }
    }

    fun setTargetPosition(x: Double, y: Double, duration: Long) {
        synchronized(this) {
            val cx = this.x;  val cy = this.y
            val vx = this.vx; val vy = this.vy
            x0 = cx; x1 = (vx/3) + cx; x2 = x; x3 = x
            y0 = cy; y1 = (vy/3) + cy; y2 = y; y3 = y
            this.timeStart = now()
            this.duration = duration
        }
    }

    fun cancel() { setPosition(this.x, this.y) }

    private fun now() = System.currentTimeMillis()

    private val t; get() = if (duration > 0) { ((now() - timeStart) / duration.toDouble()).coerceIn(0.0, 1.0) } else { 1.0 }
    val x; get() = synchronized(this) { t.let { t -> (1-t).pow(3) * x0 + 3 * (1-t).pow(2) * t * x1 + 3 * (1-t) * t.pow(2) * x2 + t.pow(3) * x3 } }
    val y; get() = synchronized(this) { t.let { t -> (1-t).pow(3) * y0 + 3 * (1-t).pow(2) * t * y1 + 3 * (1-t) * t.pow(2) * y2 + t.pow(3) * y3 } }
    private val vx; get() = synchronized(this) { t.let { 3 * (1-t).pow(2) * (x1-x0) + 6 * (1-t) * t * (x2-x1) + 3 * t.pow(2) * (x3-x2) } }
    private val vy; get() = synchronized(this) { t.let { 3 * (1-t).pow(2) * (y1-y0) + 6 * (1-t) * t * (y2-y1) + 3 * t.pow(2) * (y3-y2) } }
    val isRunning; get() = synchronized(this) { (duration > 0) && (now() - timeStart < duration) }
}