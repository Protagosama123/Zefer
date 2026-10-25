package com.example.cv.pipeline

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Low-pass filter for 1€ Filter algorithm
 */
class LowPassFilter(private var alpha: Float = 0f, private var initValue: Float = 0f) {
    private var s: Float = initValue
    private var initialized: Boolean = false

    fun filter(value: Float, alpha: Float): Float {
        this.alpha = alpha
        s = if (initialized) {
            alpha * value + (1.0f - alpha) * s
        } else {
            initialized = true
            value
        }
        return s
    }

    fun last(): Float = s

    fun reset() {
        initialized = false
        s = initValue
    }
}

/**
 * Precise One Euro Filter implementation designed to reduce high-frequency jitter
 * while maintaining low-latency responsive tracking during rapid finger motion.
 */
class OneEuroFilter(
    private val minCutoff: Float = 1.0f,
    private val beta: Float = 0.007f,
    private val dCutoff: Float = 1.0f
) {
    private var xFilter = LowPassFilter()
    private var dxFilter = LowPassFilter()
    private var lastTime: Long = -1L

    private fun alpha(cutoff: Float, dt: Float): Float {
        val tau = 1.0f / (2.0f * PI.toFloat() * cutoff)
        return 1.0f / (1.0f + tau / dt)
    }

    fun filter(value: Float, timestampMs: Long): Float {
        if (lastTime < 0L) {
            lastTime = timestampMs
            xFilter.reset()
            dxFilter.reset()
            return xFilter.filter(value, 1.0f)
        }

        val dt = (timestampMs - lastTime) / 1000.0f
        lastTime = timestampMs
        if (dt <= 0.0f) return xFilter.last()

        val prevX = xFilter.last()
        val dx = (value - prevX) / dt
        val edx = dxFilter.filter(dx, alpha(dCutoff, dt))

        val cutoff = minCutoff + beta * abs(edx)
        return xFilter.filter(value, alpha(cutoff, dt))
    }

    fun reset() {
        lastTime = -1L
        xFilter.reset()
        dxFilter.reset()
    }
}

/**
 * Filter for angle values using shortest-path circular wrapping
 * delta = atan2(sin(t - o), cos(t - o))
 */
class CircularAngleFilter(
    minCutoff: Float = 0.8f,
    beta: Float = 0.005f
) {
    private val filter = OneEuroFilter(minCutoff, beta)
    private var currentAngle: Float = 0f
    private var initialized: Boolean = false

    fun filter(targetAngleRad: Float, timestampMs: Long): Float {
        if (!initialized) {
            currentAngle = targetAngleRad
            initialized = true
            filter.filter(currentAngle, timestampMs)
            return currentAngle
        }

        // Shortest-path angular difference
        val diff = atan2(sin(targetAngleRad - currentAngle), cos(targetAngleRad - currentAngle))
        val continuousTarget = currentAngle + diff
        currentAngle = filter.filter(continuousTarget, timestampMs)
        return currentAngle
    }

    fun reset() {
        initialized = false
        filter.reset()
    }
}
