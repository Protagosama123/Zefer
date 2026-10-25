package com.example.ui.screens.tryon

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.example.cv.pipeline.FingerType
import com.example.cv.pipeline.HandLandmarkerHelper
import com.example.data.model.Variant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

@Composable
fun NailOverlayCanvas(
    landmarkerHelper: HandLandmarkerHelper,
    variant: Variant?,
    isDebugMode: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val spriteBitmaps = remember { mutableStateMapOf<String, Bitmap>() }

    // Load sprites into memory when active variant changes
    LaunchedEffect(variant) {
        if (variant == null) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val loaded = mutableMapOf<String, Bitmap>()
            for ((fingerKey, spritePath) in variant.sprites) {
                try {
                    val bitmap = if (spritePath.startsWith("/") || spritePath.startsWith("file://")) {
                        val cleanPath = spritePath.removePrefix("file://")
                        FileInputStream(File(cleanPath)).use { BitmapFactory.decodeStream(it) }
                    } else {
                        context.assets.open(spritePath).use { BitmapFactory.decodeStream(it) }
                    }
                    if (bitmap != null) {
                        loaded[fingerKey] = bitmap
                    }
                } catch (e: Exception) {
                    // Fallback
                }
            }
            withContext(Dispatchers.Main) {
                spriteBitmaps.clear()
                spriteBitmaps.putAll(loaded)
            }
        }
    }

    val paint = remember {
        Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
        }
    }

    val matrix = remember { Matrix() }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .testTag("nail_overlay_canvas")
    ) {
        // Read tracking state directly inside draw lambda to eliminate per-frame recomposition
        val result = landmarkerHelper.trackingResult.value
        val canvasWidth = size.width
        val canvasHeight = size.height

        // 1. Draw nail sprites
        drawIntoCanvas { composeCanvas ->
            val nativeCanvas = composeCanvas.nativeCanvas

            for ((fingerType, pose) in result.fingers) {
                if (!pose.isVisible || pose.alpha <= 0.02f) continue
                val bitmap = spriteBitmaps[fingerType.key] ?: continue

                matrix.reset()

                val anchorX = pose.smoothedAnchor.x * (canvasWidth / 1000f)
                val anchorY = pose.smoothedAnchor.y * (canvasHeight / 1000f)
                val nailW = pose.smoothedWidth * (canvasWidth / 1000f)
                val nailH = pose.smoothedHeight * (canvasHeight / 1000f)

                val scaleX = nailW / bitmap.width.toFloat()
                val scaleY = nailH / bitmap.height.toFloat()

                // Base-center pivot: nail base is at (bitmap.width / 2, bitmap.height)
                matrix.postTranslate(-bitmap.width / 2f, -bitmap.height.toFloat())
                matrix.postScale(scaleX, scaleY)
                val degrees = Math.toDegrees(pose.smoothedAngleRad.toDouble()).toFloat()
                matrix.postRotate(degrees)
                matrix.postTranslate(anchorX, anchorY)

                paint.alpha = (pose.alpha * 255).toInt().coerceIn(0, 255)
                nativeCanvas.drawBitmap(bitmap, matrix, paint)
            }
        }

        // 2. Draw Debug Skeleton if enabled
        if (isDebugMode && result.skeleton != null) {
            val landmarks = result.skeleton.landmarks

            // Draw bone connections
            for ((from, to) in result.skeleton.connections) {
                if (from < landmarks.size && to < landmarks.size) {
                    val p1 = landmarks[from]
                    val p2 = landmarks[to]
                    drawLine(
                        color = Color.White.copy(alpha = 0.85f),
                        start = Offset(p1.x * canvasWidth, p1.y * canvasHeight),
                        end = Offset(p2.x * canvasWidth, p2.y * canvasHeight),
                        strokeWidth = 3f
                    )
                }
            }

            // Draw landmark joints
            for (i in landmarks.indices) {
                val lm = landmarks[i]
                drawCircle(
                    color = Color.White,
                    radius = 4f,
                    center = Offset(lm.x * canvasWidth, lm.y * canvasHeight)
                )
            }

            // Highlight DIP and TIP points per finger with bone vectors
            for (finger in FingerType.values()) {
                val dip = landmarks[finger.dipIndex]
                val tip = landmarks[finger.tipIndex]

                val dipOffset = Offset(dip.x * canvasWidth, dip.y * canvasHeight)
                val tipOffset = Offset(tip.x * canvasWidth, tip.y * canvasHeight)

                // DIP in Cyan
                drawCircle(
                    color = Color(0xFF00E5FF),
                    radius = 7f,
                    center = dipOffset
                )

                // TIP in Yellow
                drawCircle(
                    color = Color(0xFFFFD600),
                    radius = 7f,
                    center = tipOffset
                )

                // Bone vector
                drawLine(
                    color = Color(0xFFFF5252),
                    start = dipOffset,
                    end = tipOffset,
                    strokeWidth = 4f
                )
            }
        }
    }
}
