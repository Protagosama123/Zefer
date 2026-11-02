package com.example.cv.tryon

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

data class DetectedHand(
    val px: List<Point3D>,
    val world: List<Point3D>,
    val label: String,
    val score: Float
)

data class DetectionAttempt(
    val angle: Int = 0,
    val up: Float = 1.0f
)

data class ComputedHand(
    val hand: DetectedHand,
    val poses: Map<String, RawFingerPose>,
    val backConf: Float,
    val appearsRight: Boolean,
    val unitPx: Float,
    val yawCos: Float,
    val label: String,
    val score: Float
)

data class AffineMatrix2D(
    val m00: Double, val m01: Double, val m02: Double,
    val m10: Double, val m11: Double, val m12: Double
) {
    fun inverse(): AffineMatrix2D {
        val det = m00 * m11 - m01 * m10
        require(abs(det) > 1e-12) { "Matrix is singular and cannot be inverted" }
        val invDet = 1.0 / det
        val i00 = m11 * invDet
        val i01 = -m01 * invDet
        val i10 = -m10 * invDet
        val i11 = m00 * invDet
        val i02 = -(i00 * m02 + i01 * m12)
        val i12 = -(i10 * m02 + i11 * m12)
        return AffineMatrix2D(i00, i01, i02, i10, i11, i12)
    }

    fun transform(x: Double, y: Double): Pair<Double, Double> {
        val tx = m00 * x + m01 * y + m02
        val ty = m10 * x + m11 * y + m12
        return Pair(tx, ty)
    }
}

object RotationUtils {
    /**
     * Compute 2x3 forward affine matrix for rotating an image (W x H) counter-clockwise
     * by [angleDeg], expanding bounding canvas if necessary, matching OpenCV rotate_bound / _forward.
     */
    fun getForwardAffine(w: Int, h: Int, angleDeg: Int, up: Float = 1.0f): Pair<AffineMatrix2D, Pair<Int, Int>> {
        val rad = Math.toRadians(angleDeg.toDouble())
        val cosA = cos(rad)
        val sinA = sin(rad)

        val (baseM, newDims) = if (angleDeg % 360 == 0) {
            Pair(AffineMatrix2D(1.0, 0.0, 0.0, 0.0, 1.0, 0.0), Pair(w, h))
        } else if (angleDeg % 90 == 0) {
            val k = ((angleDeg / 90) % 4 + 4) % 4
            when (k) {
                1 -> Pair(AffineMatrix2D(0.0, 1.0, 0.0, -1.0, 0.0, w.toDouble()), Pair(h, w))
                2 -> Pair(AffineMatrix2D(-1.0, 0.0, w.toDouble(), 0.0, -1.0, h.toDouble()), Pair(w, h))
                3 -> Pair(AffineMatrix2D(0.0, -1.0, h.toDouble(), 1.0, 0.0, 0.0), Pair(h, w))
                else -> Pair(AffineMatrix2D(1.0, 0.0, 0.0, 0.0, 1.0, 0.0), Pair(w, h))
            }
        } else {
            // General angle with bounding box expansion
            val cx = w / 2.0
            val cy = h / 2.0
            val absCos = abs(cosA)
            val absSin = abs(sinA)
            val nw = (h * absSin + w * absCos).roundToInt()
            val nh = (h * absCos + w * absSin).roundToInt()
            val tx = nw / 2.0 - (cosA * cx - sinA * cy)
            val ty = nh / 2.0 - (sinA * cx + cosA * cy)
            Pair(AffineMatrix2D(cosA, -sinA, tx, sinA, cosA, ty), Pair(nw, nh))
        }

        val scaledM = if (up != 1.0f) {
            AffineMatrix2D(
                baseM.m00 * up, baseM.m01 * up, baseM.m02 * up,
                baseM.m10 * up, baseM.m11 * up, baseM.m12 * up
            )
        } else baseM

        val targetDims = Pair((newDims.first * up).roundToInt(), (newDims.second * up).roundToInt())
        return Pair(scaledM, targetDims)
    }

    /**
     * Map detected landmarks from rotated/upscaled image coordinates back to original image pixels.
     */
    fun unmapLandmarks(
        prLandmarks: List<Point3D>,
        worldLandmarks: List<Point3D>,
        forwardM: AffineMatrix2D,
        up: Float
    ): Pair<List<Point3D>, List<Point3D>> {
        val invM = forwardM.inverse()

        // For world landmarks: rotation only (scale removed)
        val a00 = forwardM.m00 / up
        val a01 = forwardM.m01 / up
        val a10 = forwardM.m10 / up
        val a11 = forwardM.m11 / up
        val rotM = AffineMatrix2D(a00, a01, 0.0, a10, a11, 0.0)
        val invRotM = rotM.inverse()

        val unmappedPx = prLandmarks.map { pt ->
            val (origX, origY) = invM.transform(pt.x.toDouble(), pt.y.toDouble())
            Point3D(origX.toFloat(), origY.toFloat(), pt.z / up)
        }

        val unmappedWorld = worldLandmarks.map { wt ->
            val (wx, wy) = invRotM.transform(wt.x.toDouble(), wt.y.toDouble())
            Point3D(wx.toFloat(), wy.toFloat(), wt.z)
        }

        return Pair(unmappedPx, unmappedWorld)
    }
}

fun computeHand(
    hand: DetectedHand,
    params: Params,
    facing: String = "auto",
    adj: ManualAdjustments? = null
): ComputedHand {
    val (initialBackConf, appearsRight) = backConfidenceFromImage(
        hand.px, hand.world, hand.label, params.labelMirrored
    )
    var backConf = initialBackConf
    if (facing == "back") {
        backConf = if (abs(backConf) > 0.3f) abs(backConf) else 0.9f
    } else if (facing == "palm") {
        backConf = if (abs(backConf) > 0.3f) -abs(backConf) else -0.9f
    }

    val (unit, yawCos) = handGeometry(hand.px)
    val poses = rawFingerPoses(hand.px, hand.world, backConf, unit, params, adj)
    return ComputedHand(
        hand = hand,
        poses = poses,
        backConf = backConf,
        appearsRight = appearsRight,
        unitPx = unit,
        yawCos = yawCos,
        label = hand.label,
        score = hand.score
    )
}
