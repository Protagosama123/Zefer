package com.example.cv.tryon

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HandDetector(
    private val context: Context
) : AutoCloseable {

    private var handLandmarker: HandLandmarker? = null
    private var isGpuEnabled: Boolean = true

    init {
        initLandmarker(useGpu = true)
    }

    private fun initLandmarker(useGpu: Boolean) {
        try {
            handLandmarker?.close()
            handLandmarker = null

            val baseOptionsBuilder = BaseOptions.builder()
                .setModelAssetPath("hand_landmarker.task")

            if (useGpu) {
                baseOptionsBuilder.setDelegate(Delegate.GPU)
            } else {
                baseOptionsBuilder.setDelegate(Delegate.CPU)
            }

            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptionsBuilder.build())
                .setRunningMode(RunningMode.IMAGE)
                .setNumHands(2)
                .setMinHandDetectionConfidence(0.3f)
                .setMinHandPresenceConfidence(0.3f)
                .setMinTrackingConfidence(0.3f)
                .build()

            handLandmarker = HandLandmarker.createFromOptions(context, options)
            isGpuEnabled = useGpu
            Log.d("HandDetector", "MediaPipe HandLandmarker initialized in IMAGE mode (GPU=$useGpu)")
        } catch (e: Exception) {
            Log.e("HandDetector", "Failed initializing HandLandmarker (GPU=$useGpu): ${e.message}")
            if (useGpu) {
                // Graceful fallback to CPU delegate
                initLandmarker(useGpu = false)
            }
        }
    }

    private val fallbackAngles = listOf(0, 90, 270, 180, 45, 315, 135, 225)

    suspend fun detectHands(
        bitmap: Bitmap,
        fallback: Boolean = true
    ): Pair<List<DetectedHand>, DetectionAttempt?> = withContext(Dispatchers.Default) {
        val attempts = if (fallback) {
            fallbackAngles.map { DetectionAttempt(angle = it, up = 1.0f) } + listOf(DetectionAttempt(angle = 0, up = 1.5f))
        } else {
            listOf(DetectionAttempt(angle = 0, up = 1.0f))
        }

        for (attempt in attempts) {
            val (processedBmp, affineM) = transformBitmap(bitmap, attempt.angle, attempt.up)
            val mpImage = BitmapImageBuilder(processedBmp).build()

            val landmarker = handLandmarker ?: run {
                initLandmarker(useGpu = false)
                handLandmarker
            }

            if (landmarker == null) {
                if (processedBmp != bitmap) processedBmp.recycle()
                continue
            }

            val result = try {
                landmarker.detect(mpImage)
            } catch (e: Exception) {
                Log.e("HandDetector", "detect failed: ${e.message}")
                if (isGpuEnabled) {
                    initLandmarker(useGpu = false)
                    handLandmarker?.detect(mpImage)
                } else null
            }

            val wR = processedBmp.width.toFloat()
            val hR = processedBmp.height.toFloat()
            if (processedBmp != bitmap) processedBmp.recycle()

            val allLandmarks = result?.landmarks()
            if (allLandmarks.isNullOrEmpty()) {
                continue
            }

            val allWorldLandmarks = result.worldLandmarks()
            val handednessList = result.handedness()
            val hands = mutableListOf<DetectedHand>()

            for (i in allLandmarks.indices) {
                val lms = allLandmarks[i]
                val wl = allWorldLandmarks?.getOrNull(i) ?: emptyList()
                val label = handednessList?.getOrNull(i)?.firstOrNull()?.displayName()
                    ?: handednessList?.getOrNull(i)?.firstOrNull()?.categoryName()
                    ?: "Right"
                val score = handednessList?.getOrNull(i)?.firstOrNull()?.score() ?: 0.9f

                val prPts = lms.map { lm ->
                    Point3D(lm.x() * wR, lm.y() * hR, lm.z() * wR)
                }
                val prWorld = wl.map { wm ->
                    Point3D(wm.x(), wm.y(), wm.z())
                }

                val (origPx, origWorld) = RotationUtils.unmapLandmarks(
                    prPts, prWorld, affineM, attempt.up
                )

                hands.add(
                    DetectedHand(
                        px = origPx,
                        world = origWorld,
                        label = label,
                        score = score
                    )
                )
            }

            return@withContext Pair(hands, attempt)
        }

        return@withContext Pair(emptyList(), null)
    }

    private fun transformBitmap(src: Bitmap, angleDeg: Int, up: Float): Pair<Bitmap, AffineMatrix2D> {
        val (affine, newDims) = RotationUtils.getForwardAffine(src.width, src.height, angleDeg, up)
        if (angleDeg % 360 == 0 && up == 1.0f) {
            return Pair(src, affine)
        }

        val targetW = newDims.first
        val targetH = newDims.second
        val destBmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(destBmp)
        val matrix = Matrix().apply {
            setValues(floatArrayOf(
                affine.m00.toFloat(), affine.m01.toFloat(), affine.m02.toFloat(),
                affine.m10.toFloat(), affine.m11.toFloat(), affine.m12.toFloat(),
                0f, 0f, 1f
            ))
        }
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(src, matrix, paint)
        return Pair(destBmp, affine)
    }

    override fun close() {
        handLandmarker?.close()
        handLandmarker = null
    }
}
