package com.example.cv.pipeline

import androidx.compose.ui.geometry.Offset

enum class FingerType(val key: String, val dipIndex: Int, val tipIndex: Int) {
    THUMB("thumb", 3, 4),
    INDEX("index", 7, 8),
    MIDDLE("middle", 11, 12),
    RING("ring", 15, 16),
    PINKY("pinky", 19, 20)
}

data class TrackedFingerPose(
    val finger: FingerType,
    val rawAnchor: Offset,
    val smoothedAnchor: Offset,
    val rawAngleRad: Float,
    val smoothedAngleRad: Float,
    val rawWidth: Float,
    val smoothedWidth: Float,
    val rawHeight: Float,
    val smoothedHeight: Float,
    val isVisible: Boolean,
    val alpha: Float = 1.0f
)

data class LandmarkPoint(
    val x: Float, // Normalized [0, 1] mapped to preview coordinates
    val y: Float,
    val z: Float
)

data class HandSkeleton(
    val landmarks: List<LandmarkPoint>,
    val connections: List<Pair<Int, Int>>
)

data class DebugMetrics(
    val fps: Float = 0f,
    val inferenceTimeMs: Long = 0L,
    val handedness: String = "Right",
    val confidenceScore: Float = 0f,
    val isPalmFacingCamera: Boolean = false,
    val trackingLostCount: Int = 0,
    val isReplayMode: Boolean = false
)

data class TunableParams(
    val dipTipAnchorFraction: Float = 0.22f, // Anchor position = DIP + fraction * (TIP - DIP)
    val lengthExtensionRatio: Float = 1.35f, // Nail length relative to DIP-TIP distance
    val baseWidthRatio: Float = 0.72f,
    val oneEuroMinCutoff: Float = 1.2f,
    val oneEuroBeta: Float = 0.008f,
    val palmFacingAngleThreshold: Float = 0.15f
)

data class HandTrackingResult(
    val hasHand: Boolean = false,
    val fingers: Map<FingerType, TrackedFingerPose> = emptyMap(),
    val skeleton: HandSkeleton? = null,
    val metrics: DebugMetrics = DebugMetrics(),
    val lastPoseTimestamp: Long = 0L
)
