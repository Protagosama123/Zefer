package com.example.cv.pipeline

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class FingerSmoother(minCutoff: Float = 1.2f, beta: Float = 0.008f) {
    val xFilter = OneEuroFilter(minCutoff, beta)
    val yFilter = OneEuroFilter(minCutoff, beta)
    val widthFilter = OneEuroFilter(minCutoff, beta)
    val heightFilter = OneEuroFilter(minCutoff, beta)
    val angleFilter = CircularAngleFilter(minCutoff * 0.8f, beta * 0.7f)

    fun reset() {
        xFilter.reset()
        yFilter.reset()
        widthFilter.reset()
        heightFilter.reset()
        angleFilter.reset()
    }
}

class NailAnchorCalculator(
    private var params: TunableParams = TunableParams()
) {
    private val smoothers = mapOf(
        FingerType.THUMB to FingerSmoother(params.oneEuroMinCutoff * 0.7f, params.oneEuroBeta * 1.5f), // stronger smoothing for thumb
        FingerType.INDEX to FingerSmoother(params.oneEuroMinCutoff, params.oneEuroBeta),
        FingerType.MIDDLE to FingerSmoother(params.oneEuroMinCutoff, params.oneEuroBeta),
        FingerType.RING to FingerSmoother(params.oneEuroMinCutoff, params.oneEuroBeta),
        FingerType.PINKY to FingerSmoother(params.oneEuroMinCutoff, params.oneEuroBeta)
    )

    private var lastValidPoseTime: Long = 0L
    private var lastFingers: Map<FingerType, TrackedFingerPose> = emptyMap()

    fun updateParams(newParams: TunableParams) {
        params = newParams
    }

    /**
     * Determines whether the palm is facing towards the camera or away from it.
     * Uses the cross product of the palm plane (Wrist -> Index MCP) x (Wrist -> Pinky MCP).
     */
    fun isPalmFacingCamera(landmarks: List<LandmarkPoint>, isFrontCamera: Boolean): Boolean {
        if (landmarks.size < 21) return false
        val wrist = landmarks[0]
        val indexMcp = landmarks[5]
        val pinkyMcp = landmarks[17]

        val v1x = indexMcp.x - wrist.x
        val v1y = indexMcp.y - wrist.y
        val v2x = pinkyMcp.x - wrist.x
        val v2y = pinkyMcp.y - wrist.y

        // Cross product 2D pseudo-z
        val crossZ = v1x * v2y - v1y * v2x
        return if (isFrontCamera) crossZ > params.palmFacingAngleThreshold else crossZ < -params.palmFacingAngleThreshold
    }

    fun calculateFingers(
        landmarks: List<LandmarkPoint>,
        viewWidth: Float,
        viewHeight: Float,
        timestampMs: Long,
        isFrontCamera: Boolean
    ): Pair<Map<FingerType, TrackedFingerPose>, Boolean> {
        if (landmarks.size < 21) {
            // Tracking lost handling: hold last pose ~150ms before gracefully fading out
            val elapsedSinceLast = timestampMs - lastValidPoseTime
            return if (elapsedSinceLast in 1..150 && lastFingers.isNotEmpty()) {
                val fade = 1.0f - (elapsedSinceLast / 150.0f)
                val faded = lastFingers.mapValues { (_, pose) ->
                    pose.copy(alpha = fade)
                }
                Pair(faded, false)
            } else {
                Pair(emptyMap(), false)
            }
        }

        val palmFacing = isPalmFacingCamera(landmarks, isFrontCamera)
        lastValidPoseTime = timestampMs

        val result = mutableMapOf<FingerType, TrackedFingerPose>()

        for (finger in FingerType.values()) {
            val dip = landmarks[finger.dipIndex]
            val tip = landmarks[finger.tipIndex]

            // Screen space coordinates
            val dipScreenX = dip.x * viewWidth
            val dipScreenY = dip.y * viewHeight
            val tipScreenX = tip.x * viewWidth
            val tipScreenY = tip.y * viewHeight

            val dx = tipScreenX - dipScreenX
            val dy = tipScreenY - dipScreenY
            val boneLength = max(1.0f, hypot(dx, dy))

            // Nail anchor derived from DIP -> TIP vector:
            // Base anchor = DIP + tunable fraction toward TIP
            val rawAnchorX = dipScreenX + dx * params.dipTipAnchorFraction
            val rawAnchorY = dipScreenY + dy * params.dipTipAnchorFraction

            // Angle: theta = atan2(tipY - dipY, tipX - dipX) + PI/2 (image space y-down)
            val rawAngleRad = (atan2(dy, dx) + (PI / 2.0)).toFloat()

            // Extension length grows past the fingertip
            val rawHeight = boneLength * params.lengthExtensionRatio
            val rawWidth = boneLength * params.baseWidthRatio

            // Foreshortening depth adjustment
            val zDiff = abs(tip.z - dip.z)
            val foreshortenFactor = max(0.4f, min(1.1f, 1.0f - zDiff * 1.5f))
            val adjustedWidth = rawWidth * foreshortenFactor

            // Apply One Euro Filter to reduce jitter
            val smoother = smoothers[finger] ?: FingerSmoother()
            val smoothedX = smoother.xFilter.filter(rawAnchorX, timestampMs)
            val smoothedY = smoother.yFilter.filter(rawAnchorY, timestampMs)
            val smoothedW = smoother.widthFilter.filter(adjustedWidth, timestampMs)
            val smoothedH = smoother.heightFilter.filter(rawHeight, timestampMs)
            val smoothedAngle = smoother.angleFilter.filter(rawAngleRad, timestampMs)

            // Hide when palm faces camera or finger is curled inward
            val isCurled = tip.y > dip.y && finger != FingerType.THUMB && boneLength < 15.0f
            val isVisible = !palmFacing && !isCurled

            result[finger] = TrackedFingerPose(
                finger = finger,
                rawAnchor = Offset(rawAnchorX, rawAnchorY),
                smoothedAnchor = Offset(smoothedX, smoothedY),
                rawAngleRad = rawAngleRad,
                smoothedAngleRad = smoothedAngle,
                rawWidth = adjustedWidth,
                smoothedWidth = smoothedW,
                rawHeight = rawHeight,
                smoothedHeight = smoothedH,
                isVisible = isVisible,
                alpha = if (isVisible) 1.0f else 0.0f
            )
        }

        lastFingers = result
        return Pair(result, palmFacing)
    }

    fun reset() {
        smoothers.values.forEach { it.reset() }
        lastFingers = emptyMap()
        lastValidPoseTime = 0L
    }
}
