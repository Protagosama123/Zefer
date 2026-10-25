package com.example.cv.pipeline

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import kotlin.math.cos
import kotlin.math.sin

class HandLandmarkerHelper(
    private val context: Context,
    private val scope: CoroutineScope
) : ImageAnalysis.Analyzer {

    private val executor = Executors.newSingleThreadExecutor()
    private var handLandmarker: HandLandmarker? = null

    private val _trackingResult = MutableStateFlow(HandTrackingResult())
    val trackingResult: StateFlow<HandTrackingResult> = _trackingResult.asStateFlow()

    private val nailCalculator = NailAnchorCalculator()
    private var isFrontCamera: Boolean = true
    private var isReplayMode: Boolean = false

    private var frameCount: Int = 0
    private var lastFpsTimestamp: Long = SystemClock.uptimeMillis()
    private var currentFps: Float = 0f
    private var trackingLostCount: Int = 0

    val handConnections = listOf(
        Pair(0, 1), Pair(1, 2), Pair(2, 3), Pair(3, 4), // Thumb
        Pair(0, 5), Pair(5, 6), Pair(6, 7), Pair(7, 8), // Index
        Pair(5, 9), Pair(9, 10), Pair(10, 11), Pair(11, 12), // Middle
        Pair(9, 13), Pair(13, 14), Pair(14, 15), Pair(15, 16), // Ring
        Pair(13, 17), Pair(17, 18), Pair(18, 19), Pair(19, 20), // Pinky
        Pair(0, 17) // Palm base
    )

    init {
        initLandmarker(useGpu = true)
    }

    fun setCameraFacing(isFront: Boolean) {
        this.isFrontCamera = isFront
    }

    fun setReplayMode(enabled: Boolean) {
        this.isReplayMode = enabled
        if (enabled) {
            startSyntheticReplay()
        }
    }

    fun updateTunableParams(params: TunableParams) {
        nailCalculator.updateParams(params)
    }

    private fun initLandmarker(useGpu: Boolean) {
        executor.execute {
            try {
                val baseOptionsBuilder = BaseOptions.builder()
                    .setModelAssetPath("hand_landmarker.task")
                
                if (useGpu) {
                    baseOptionsBuilder.setDelegate(Delegate.GPU)
                } else {
                    baseOptionsBuilder.setDelegate(Delegate.CPU)
                }

                val options = HandLandmarker.HandLandmarkerOptions.builder()
                    .setBaseOptions(baseOptionsBuilder.build())
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setNumHands(1)
                    .setMinHandDetectionConfidence(0.5f)
                    .setMinHandPresenceConfidence(0.5f)
                    .setMinTrackingConfidence(0.5f)
                    .setResultListener { result, inputImage ->
                        onLandmarksDetected(result, inputImage)
                    }
                    .setErrorListener { error ->
                        Log.e("HandLandmarkerHelper", "MediaPipe Error: ${error.message}")
                        if (useGpu) {
                            // Graceful retry with CPU fallback
                            initLandmarker(useGpu = false)
                        }
                    }
                    .build()

                handLandmarker = HandLandmarker.createFromOptions(context, options)
                Log.d("HandLandmarkerHelper", "MediaPipe HandLandmarker initialized (GPU=$useGpu)")
            } catch (e: Exception) {
                Log.e("HandLandmarkerHelper", "Failed to init MediaPipe (GPU=$useGpu): ${e.message}")
                if (useGpu) {
                    initLandmarker(useGpu = false)
                }
            }
        }
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (isReplayMode) {
            imageProxy.close()
            return
        }

        val frameTimeMs = SystemClock.uptimeMillis()
        frameCount++
        if (frameTimeMs - lastFpsTimestamp >= 1000L) {
            currentFps = frameCount * 1000.0f / (frameTimeMs - lastFpsTimestamp)
            frameCount = 0
            lastFpsTimestamp = frameTimeMs
        }

        val landmarker = handLandmarker
        if (landmarker == null) {
            imageProxy.close()
            return
        }

        val startTime = SystemClock.uptimeMillis()
        try {
            val bitmap = imageProxy.toBitmap()
            val mpImage = BitmapImageBuilder(bitmap).build()

            landmarker.detectAsync(mpImage, frameTimeMs)
        } catch (e: Exception) {
            Log.e("HandLandmarkerHelper", "Frame analysis failed: ${e.message}")
        } finally {
            imageProxy.close()
        }
    }

    private fun onLandmarksDetected(result: HandLandmarkerResult, inputImage: MPImage) {
        val now = SystemClock.uptimeMillis()
        val allLandmarks = result.landmarks()

        if (allLandmarks.isNullOrEmpty() || allLandmarks[0].isNullOrEmpty()) {
            trackingLostCount++
            val (fingers, isPalm) = nailCalculator.calculateFingers(
                emptyList(),
                1000f, 1000f, now, isFrontCamera
            )
            _trackingResult.value = HandTrackingResult(
                hasHand = false,
                fingers = fingers,
                skeleton = null,
                metrics = DebugMetrics(
                    fps = currentFps,
                    inferenceTimeMs = 0L,
                    handedness = "None",
                    confidenceScore = 0f,
                    isPalmFacingCamera = isPalm,
                    trackingLostCount = trackingLostCount,
                    isReplayMode = false
                ),
                lastPoseTimestamp = now
            )
            return
        }

        val rawList = allLandmarks[0]
        val handednessList = result.handedness()
        val handLabel = if (!handednessList.isNullOrEmpty() && handednessList[0].isNotEmpty()) {
            handednessList[0][0].displayName() ?: "Hand"
        } else "Hand"

        val score = if (!handednessList.isNullOrEmpty() && handednessList[0].isNotEmpty()) {
            handednessList[0][0].score()
        } else 0.9f

        // Map landmarks into screen coordinates with front-camera mirroring correction
        val points = rawList.map { lm ->
            val mappedX = if (isFrontCamera) {
                1.0f - lm.x() // Mirror horizontally for front camera preview match
            } else {
                lm.x()
            }
            LandmarkPoint(x = mappedX, y = lm.y(), z = lm.z())
        }

        val (fingers, isPalm) = nailCalculator.calculateFingers(
            points,
            1000f, 1000f, now, isFrontCamera
        )

        val skeleton = HandSkeleton(
            landmarks = points,
            connections = handConnections
        )

        _trackingResult.value = HandTrackingResult(
            hasHand = true,
            fingers = fingers,
            skeleton = skeleton,
            metrics = DebugMetrics(
                fps = currentFps,
                inferenceTimeMs = SystemClock.uptimeMillis() - now,
                handedness = handLabel,
                confidenceScore = score,
                isPalmFacingCamera = isPalm,
                trackingLostCount = trackingLostCount,
                isReplayMode = false
            ),
            lastPoseTimestamp = now
        )
    }

    /**
     * Replay mode for offline testing and emulator demonstration.
     * Generates a realistic hand pose with gentle movement.
     */
    fun startSyntheticReplay() {
        scope.launch(Dispatchers.Default) {
            var step = 0.0
            while (isReplayMode) {
                step += 0.04
                val now = SystemClock.uptimeMillis()

                val wave = sin(step).toFloat() * 0.05f
                val tilt = cos(step * 0.7).toFloat() * 0.03f

                // 21 base landmarks of an open upright hand facing away from camera
                val base = listOf(
                    // 0: Wrist
                    LandmarkPoint(0.50f + wave, 0.85f, 0.0f),
                    // 1-4: Thumb
                    LandmarkPoint(0.42f + wave, 0.78f, -0.02f),
                    LandmarkPoint(0.36f + wave, 0.70f, -0.03f),
                    LandmarkPoint(0.32f + wave, 0.63f, -0.04f),
                    LandmarkPoint(0.28f + wave, 0.57f, -0.05f),
                    // 5-8: Index
                    LandmarkPoint(0.44f + wave, 0.65f, 0.0f),
                    LandmarkPoint(0.43f + wave, 0.53f, -0.02f),
                    LandmarkPoint(0.42f + wave, 0.44f, -0.03f),
                    LandmarkPoint(0.41f + wave, 0.36f, -0.04f),
                    // 9-12: Middle
                    LandmarkPoint(0.50f + wave, 0.63f, 0.0f),
                    LandmarkPoint(0.50f + wave, 0.50f, -0.02f),
                    LandmarkPoint(0.50f + wave, 0.40f, -0.03f),
                    LandmarkPoint(0.50f + wave, 0.32f, -0.04f),
                    // 13-16: Ring
                    LandmarkPoint(0.56f + wave, 0.65f, 0.0f),
                    LandmarkPoint(0.57f + wave, 0.53f, -0.02f),
                    LandmarkPoint(0.58f + wave, 0.44f, -0.03f),
                    LandmarkPoint(0.59f + wave, 0.36f, -0.04f),
                    // 17-20: Pinky
                    LandmarkPoint(0.62f + wave, 0.70f, 0.0f),
                    LandmarkPoint(0.64f + wave, 0.61f, -0.02f),
                    LandmarkPoint(0.65f + wave, 0.53f, -0.03f),
                    LandmarkPoint(0.66f + wave, 0.47f, -0.04f)
                )

                val (fingers, isPalm) = nailCalculator.calculateFingers(
                    base, 1000f, 1000f, now, isFrontCamera
                )

                _trackingResult.value = HandTrackingResult(
                    hasHand = true,
                    fingers = fingers,
                    skeleton = HandSkeleton(base, handConnections),
                    metrics = DebugMetrics(
                        fps = 30.0f,
                        inferenceTimeMs = 18L,
                        handedness = "Right (Synthetic)",
                        confidenceScore = 0.98f,
                        isPalmFacingCamera = isPalm,
                        trackingLostCount = 0,
                        isReplayMode = true
                    ),
                    lastPoseTimestamp = now
                )

                kotlinx.coroutines.delay(33) // ~30 FPS
            }
        }
    }

    fun close() {
        executor.execute {
            handLandmarker?.close()
            handLandmarker = null
        }
        executor.shutdown()
    }
}
