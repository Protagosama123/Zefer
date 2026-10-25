package com.example

import com.example.cv.pipeline.CircularAngleFilter
import com.example.cv.pipeline.FingerType
import com.example.cv.pipeline.LandmarkPoint
import com.example.cv.pipeline.NailAnchorCalculator
import com.example.cv.pipeline.OneEuroFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

class NailTrackingMathUnitTest {

    @Test
    fun testOneEuroFilterConvergence() {
        val filter = OneEuroFilter(minCutoff = 1.0f, beta = 0.01f)
        var filtered = filter.filter(100f, 0L)
        assertEquals(100f, filtered, 0.01f)

        // Jittery series around 100
        for (i in 1..20) {
            val noisyInput = 100f + (if (i % 2 == 0) 5f else -5f)
            filtered = filter.filter(noisyInput, i * 33L)
        }
        // Filter should smooth out the high-frequency oscillation close to 100
        assertTrue("Filtered value should stay near 100 but was $filtered", abs(filtered - 100f) < 2.5f)
    }

    @Test
    fun testCircularAngleFilterShortestPathWrapping() {
        val angleFilter = CircularAngleFilter()
        // Start near +PI (~3.10 rad)
        val initialAngle = (PI - 0.04).toFloat()
        var current = angleFilter.filter(initialAngle, 0L)
        assertEquals(initialAngle, current, 0.01f)

        // Wrap across boundary to -PI (~ -3.10 rad)
        val wrappedAngle = (-PI + 0.04).toFloat()
        current = angleFilter.filter(wrappedAngle, 33L)

        // The angle delta should be ~0.08 rad, NOT ~6.20 rad!
        val angularDifference = abs(current - initialAngle)
        assertTrue("Angle should take shortest path across PI boundary (diff=$angularDifference)", angularDifference < 1.0f)
    }

    @Test
    fun testNailAnchorCalculationUprightFinger() {
        val calculator = NailAnchorCalculator()
        // Create an upright finger in screen coordinates (DIP at y=0.5, TIP at y=0.3)
        // With tip directly above dip (dy < 0, dx = 0), theta = atan2(-0.2, 0) + PI/2 = -PI/2 + PI/2 = 0
        val landmarks = MutableList(21) { LandmarkPoint(0.5f, 0.5f, 0f) }
        landmarks[FingerType.INDEX.dipIndex] = LandmarkPoint(0.5f, 0.5f, 0f)
        landmarks[FingerType.INDEX.tipIndex] = LandmarkPoint(0.5f, 0.3f, 0f)

        val (fingers, isPalm) = calculator.calculateFingers(
            landmarks = landmarks,
            viewWidth = 1000f,
            viewHeight = 1000f,
            timestampMs = 100L,
            isFrontCamera = true
        )

        val indexPose = fingers[FingerType.INDEX]
        assertTrue("Index finger should be detected", indexPose != null)
        assertEquals("Angle for upward finger should be ~0 radians", 0f, indexPose!!.rawAngleRad, 0.05f)
        assertTrue("Height should be greater than 0", indexPose.rawHeight > 0f)
    }

    @Test
    fun testPalmFacingDetection() {
        val calculator = NailAnchorCalculator()
        val landmarks = MutableList(21) { LandmarkPoint(0.5f, 0.5f, 0f) }
        landmarks[0] = LandmarkPoint(0.5f, 0.8f, 0f) // Wrist
        landmarks[5] = LandmarkPoint(0.4f, 0.5f, 0f) // Index MCP (to left of wrist)
        landmarks[17] = LandmarkPoint(0.6f, 0.5f, 0f) // Pinky MCP (to right of wrist)

        // For front camera, this represents the dorsal/back of hand
        val isPalm = calculator.isPalmFacingCamera(landmarks, isFrontCamera = true)
        assertFalse("Should be dorsal back of hand, not palm", isPalm)
    }
}
