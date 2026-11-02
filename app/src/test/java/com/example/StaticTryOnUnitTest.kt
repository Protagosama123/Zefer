package com.example.cv.tryon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class StaticTryOnUnitTest {

    @Test
    fun testSmoothstep() {
        assertEquals(0.0f, smoothstep(0f, 1f, -0.5f), 1e-4f)
        assertEquals(0.0f, smoothstep(0f, 1f, 0f), 1e-4f)
        assertEquals(0.5f, smoothstep(0f, 1f, 0.5f), 1e-4f)
        assertEquals(1.0f, smoothstep(0f, 1f, 1.0f), 1e-4f)
        assertEquals(1.0f, smoothstep(0f, 1f, 1.5f), 1e-4f)
        // Degenerate bounds
        assertEquals(1.0f, smoothstep(1f, 1f, 1f), 1e-4f)
        assertEquals(0.0f, smoothstep(1f, 1f, 0.5f), 1e-4f)
    }

    @Test
    fun testAngleConvention() {
        // Finger pointing straight up: d.x = 0, d.y = -1
        // theta = atan2(-1, 0) + PI/2 = -PI/2 + PI/2 = 0
        val pDipUp = Point3D(100f, 200f)
        val pTipUp = Point3D(100f, 150f)
        val dUp = pTipUp - pDipUp
        val thetaUp = Math.atan2(dUp.y.toDouble(), dUp.x.toDouble()) + Math.PI / 2.0
        assertEquals(0.0, thetaUp, 1e-4)

        // Finger pointing right: d.x = 1, d.y = 0
        // theta = atan2(0, 1) + PI/2 = 0 + PI/2 = +PI/2 (+90 deg clockwise)
        val pDipRight = Point3D(100f, 200f)
        val pTipRight = Point3D(150f, 200f)
        val dRight = pTipRight - pDipRight
        val thetaRight = Math.atan2(dRight.y.toDouble(), dRight.x.toDouble()) + Math.PI / 2.0
        assertEquals(Math.PI / 2.0, thetaRight, 1e-4)
    }

    @Test
    fun testHandGeometryAndBackConfidence() {
        // Construct canonical 21 landmarks for a right hand (back-of-hand view)
        // Wrist at (500, 800)
        // Index MCP (5) at (450, 500)
        // Middle MCP (9) at (500, 480)
        // Pinky MCP (17) at (580, 550)
        val lms = MutableList(21) { Point3D(500f, 500f) }
        lms[0] = Point3D(500f, 800f)
        lms[5] = Point3D(450f, 500f)
        lms[9] = Point3D(500f, 480f)
        lms[13] = Point3D(540f, 510f)
        lms[17] = Point3D(580f, 550f)

        val (unit, yawCos) = handGeometry(lms)
        assertTrue("Unit size should be positive and realistic", unit > 200f)
        assertTrue("yawCos should be in [0, 1]", yawCos in 0.0f..1.0f)

        // Test back confidence: right hand, back view -> backConf > 0
        val (backConf, appearsRight) = backConfidenceFromImage(lms, null, "Right", labelMirrored = false)
        assertTrue("Right hand back view should yield positive backConf", backConf > 0f)
        assertTrue("Right hand should be detected as appearsRight", appearsRight)

        // Mirror / selfie condition
        val (backConfMirrored, appearsRightMirrored) = backConfidenceFromImage(lms, null, "Right", labelMirrored = true)
        assertFalse("Mirrored right label should resolve to false", appearsRightMirrored)
    }

    @Test
    fun testRotationInverseMappingExact() {
        val w = 1200
        val h = 1600
        val angles = listOf(0, 90, 180, 270, 45, 315, 135, 225)
        val upscales = listOf(1.0f, 1.5f)

        val testPts = listOf(
            Point3D(100f, 200f, 10f),
            Point3D(600f, 800f, 0f),
            Point3D(1100f, 1500f, -20f)
        )
        val testWorld = listOf(
            Point3D(0.05f, 0.1f, -0.02f),
            Point3D(-0.04f, 0.08f, 0.01f)
        )

        for (angle in angles) {
            for (up in upscales) {
                val (forwardM, dims) = RotationUtils.getForwardAffine(w, h, angle, up)

                // Forward transform test points
                val prPts = testPts.map { pt ->
                    val (tx, ty) = forwardM.transform(pt.x.toDouble(), pt.y.toDouble())
                    Point3D(tx.toFloat(), ty.toFloat(), pt.z * up)
                }

                val a00 = forwardM.m00 / up
                val a01 = forwardM.m01 / up
                val a10 = forwardM.m10 / up
                val a11 = forwardM.m11 / up
                val rotM = AffineMatrix2D(a00, a01, 0.0, a10, a11, 0.0)
                val prWorld = testWorld.map { wt ->
                    val (wx, wy) = rotM.transform(wt.x.toDouble(), wt.y.toDouble())
                    Point3D(wx.toFloat(), wy.toFloat(), wt.z)
                }

                // Now unmap
                val (unmappedPx, unmappedWorld) = RotationUtils.unmapLandmarks(prPts, prWorld, forwardM, up)

                for (i in testPts.indices) {
                    assertEquals("X mismatch at angle=$angle, up=$up", testPts[i].x, unmappedPx[i].x, 0.01f)
                    assertEquals("Y mismatch at angle=$angle, up=$up", testPts[i].y, unmappedPx[i].y, 0.01f)
                    assertEquals("Z mismatch at angle=$angle, up=$up", testPts[i].z, unmappedPx[i].z, 0.01f)
                }

                for (i in testWorld.indices) {
                    assertEquals("World X mismatch at angle=$angle, up=$up", testWorld[i].x, unmappedWorld[i].x, 0.001f)
                    assertEquals("World Y mismatch at angle=$angle, up=$up", testWorld[i].y, unmappedWorld[i].y, 0.001f)
                    assertEquals("World Z mismatch at angle=$angle, up=$up", testWorld[i].z, unmappedWorld[i].z, 0.001f)
                }
            }
        }
    }

    @Test
    fun testSyntheticHandGoldenPose() {
        // Construct synthetic open hand facing back of hand
        val px = MutableList(21) { Point3D(0f, 0f) }
        px[0] = Point3D(500f, 750f)
        // Thumb (1, 2, 3, 4)
        px[1] = Point3D(420f, 680f); px[2] = Point3D(360f, 600f); px[3] = Point3D(320f, 530f); px[4] = Point3D(280f, 470f)
        // Index (5, 6, 7, 8)
        px[5] = Point3D(440f, 550f); px[6] = Point3D(430f, 440f); px[7] = Point3D(420f, 350f); px[8] = Point3D(410f, 270f)
        // Middle (9, 10, 11, 12)
        px[9] = Point3D(500f, 530f); px[10] = Point3D(500f, 410f); px[11] = Point3D(500f, 320f); px[12] = Point3D(500f, 230f)
        // Ring (13, 14, 15, 16)
        px[13] = Point3D(560f, 550f); px[14] = Point3D(570f, 440f); px[15] = Point3D(580f, 350f); px[16] = Point3D(590f, 270f)
        // Pinky (17, 18, 19, 20)
        px[17] = Point3D(620f, 600f); px[18] = Point3D(640f, 520f); px[19] = Point3D(650f, 440f); px[20] = Point3D(660f, 370f)

        val params = Params()
        val detected = DetectedHand(px = px, world = px.map { Point3D(it.x / 1000f, it.y / 1000f) }, label = "Right", score = 0.95f)
        val computed = computeHand(detected, params, facing = "auto")

        assertEquals("Right", computed.label)
        assertTrue("backConf should be positive for back view", computed.backConf > 0.3f)
        assertTrue("All 5 fingers must be computed", computed.poses.size == 5)

        for (finger in listOf("thumb", "index", "middle", "ring", "pinky")) {
            val pose = computed.poses[finger]!!
            assertTrue("$finger alpha should be high for straight back-facing hand", pose.targetAlpha > 0.8f)
            assertTrue("$finger rawW must be reasonable positive number", pose.rawW > 15f && pose.rawW < 120f)
            assertTrue("$finger base X should be near fingertip", abs(pose.rawX - px[FINGERS[finger]!![3]].x) < 80f)
            assertTrue("$finger base Y should be near fingertip", abs(pose.rawY - px[FINGERS[finger]!![3]].y) < 80f)
        }
    }
}
