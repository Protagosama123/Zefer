package com.example.cv.tryon

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class RenderOptions(
    val rightSet: String = "A",
    val leftSet: String = "B",
    val mirrorLeft: Boolean = false,
    val shadow: Float = 0.28f,
    val lightStrength: Float = 0.5f,
    val minAlpha: Float = 0.05f,
    val lengthMult: Float = 1.0f
)

data class RenderResult(
    val resultBitmap: Bitmap,
    val debugBitmap: Bitmap,
    val renderedFingers: List<List<String>>
)

object StaticRenderer {

    /**
     * Compute median luminance of the bounding box surrounding hand landmarks.
     */
    fun computeLocalLuminance(bitmap: Bitmap, px: List<Point3D>): Float {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        for (pt in px) {
            if (pt.x < minX) minX = pt.x
            if (pt.y < minY) minY = pt.y
            if (pt.x > maxX) maxX = pt.x
            if (pt.y > maxY) maxY = pt.y
        }

        val left = minX.toInt().coerceIn(0, bitmap.width - 1)
        val top = minY.toInt().coerceIn(0, bitmap.height - 1)
        val right = (maxX.toInt() + 1).coerceIn(left + 1, bitmap.width)
        val bottom = (maxY.toInt() + 1).coerceIn(top + 1, bitmap.height)

        val w = right - left
        val h = bottom - top
        if (w <= 0 || h <= 0) return 150f

        // Sample up to 100 pixels across the ROI for fast luminance estimation
        val stepX = max(1, w / 10)
        val stepY = max(1, h / 10)
        var totalLuma = 0.0
        var count = 0

        for (y in top until bottom step stepY) {
            for (x in left until right step stepX) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                // ITU-R BT.601 luma
                val luma = 0.299 * r + 0.587 * g + 0.114 * b
                totalLuma += luma
                count++
            }
        }

        return if (count > 0) (totalLuma / count).toFloat() else 150f
    }

    /**
     * Render nail sprites onto a copy of the original bitmap.
     * [sprites]: map of fingerName -> Bitmap.
     * [mirroredSprites]: map of fingerName -> Bitmap (for other hand).
     */
    fun render(
        originalBitmap: Bitmap,
        computedHands: List<ComputedHand>,
        sprites: Map<String, Bitmap>,
        mirroredSprites: Map<String, Bitmap> = emptyMap(),
        options: RenderOptions = RenderOptions()
    ): RenderResult {
        val resultBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(resultBitmap)

        val debugBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val debugCanvas = Canvas(debugBitmap)

        val renderedFingers = mutableListOf<List<String>>()

        val nailPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        for (comp in computedHands) {
            val hand = comp.hand
            val doneForHand = mutableListOf<String>()

            // Hand-level lighting match
            val lum = computeLocalLuminance(originalBitmap, hand.px)
            val gain = (1.0f + options.lightStrength * (lum / 150.0f - 1.0f)).coerceIn(0.75f, 1.12f)

            // Setup color filter for lighting match
            if (abs(gain - 1.0f) > 0.01f) {
                val colorMatrix = ColorMatrix(floatArrayOf(
                    gain, 0f, 0f, 0f, 0f,
                    0f, gain, 0f, 0f, 0f,
                    0f, 0f, gain, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                ))
                nailPaint.colorFilter = ColorMatrixColorFilter(colorMatrix)
            } else {
                nailPaint.colorFilter = null
            }

            // Pick sprite set
            val useMirror = !comp.appearsRight
            val activeSpriteMap = if (useMirror && mirroredSprites.isNotEmpty()) mirroredSprites else sprites

            for ((name, pose) in comp.poses) {
                if (pose.targetAlpha < options.minAlpha) continue
                val spriteBmp = activeSpriteMap[name] ?: sprites[name] ?: continue

                val sw = spriteBmp.width.toFloat()
                val sh = spriteBmp.height.toFloat()
                val aspect = sw / sh

                val wPx = max(3.0f, pose.rawW)
                val hPx = (wPx / aspect) * max(0.45f, pose.proj) * options.lengthMult

                val scaleX = wPx / sw
                val scaleY = hPx / sh
                val deg = Math.toDegrees(pose.rawAng.toDouble()).toFloat()

                // Matrix: base-center pivot at (sw / 2, sh) -> rotate -> translate to (rawX, rawY)
                val matrix = Matrix().apply {
                    postTranslate(-sw / 2.0f, -sh)
                    postScale(scaleX, scaleY)
                    postRotate(deg)
                    postTranslate(pose.rawX, pose.rawY)
                }

                // 1. Draw contact shadow if enabled
                if (options.shadow > 0f) {
                    val shadowMatrix = Matrix(matrix).apply {
                        postTranslate(0.05f * wPx, 0.07f * wPx)
                    }
                    val shadowAlpha = (options.shadow * pose.targetAlpha * 0.45f * 255).toInt().coerceIn(0, 255)
                    shadowPaint.color = Color.BLACK
                    shadowPaint.alpha = shadowAlpha
                    val blurRadius = max(1.0f, 0.07f * wPx)
                    shadowPaint.maskFilter = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)

                    // Draw extracted alpha mask for shadow
                    val alphaMask = spriteBmp.extractAlpha()
                    canvas.drawBitmap(alphaMask, shadowMatrix, shadowPaint)
                    alphaMask.recycle()
                    shadowPaint.maskFilter = null
                }

                // 2. Draw nail sprite
                nailPaint.alpha = (pose.targetAlpha * 255).toInt().coerceIn(0, 255)
                canvas.drawBitmap(spriteBmp, matrix, nailPaint)

                doneForHand.add(name)
            }

            renderedFingers.add(doneForHand)
        }

        // Draw debug overlay on debugBitmap
        drawDebug(debugCanvas, debugBitmap.width, debugBitmap.height, computedHands, renderedFingers)

        return RenderResult(
            resultBitmap = resultBitmap,
            debugBitmap = debugBitmap,
            renderedFingers = renderedFingers
        )
    }

    private fun drawDebug(
        canvas: Canvas,
        w: Int,
        h: Int,
        computedHands: List<ComputedHand>,
        renderedFingers: List<List<String>>
    ) {
        val lw = max(2f, w / 500f)

        val bonePaint = Paint().apply {
            color = Color.WHITE
            strokeWidth = lw + 1f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        val jointPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val vectorPaint = Paint().apply {
            color = Color.rgb(255, 82, 82)
            strokeWidth = lw + 2f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        val dipPaint = Paint().apply {
            color = Color.rgb(0, 229, 255) // Cyan
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val tipPaint = Paint().apply {
            color = Color.rgb(255, 214, 0) // Yellow
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val basePaint = Paint().apply {
            color = Color.rgb(0, 230, 118) // Green
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = max(22f, w / 45f)
            isAntiAlias = true
            setShadowLayer(4f, 1f, 1f, Color.BLACK)
        }

        val hudPaint = Paint().apply {
            color = Color.rgb(0, 255, 255)
            textSize = max(24f, w / 40f)
            isAntiAlias = true
            setShadowLayer(5f, 2f, 2f, Color.BLACK)
        }

        for (hi in computedHands.indices) {
            val comp = computedHands[hi]
            val hand = comp.hand
            val px = hand.px

            // Skeleton connections
            for ((from, to) in HAND_CONNECTIONS) {
                if (from < px.size && to < px.size) {
                    val p1 = px[from]
                    val p2 = px[to]
                    canvas.drawLine(p1.x, p1.y, p2.x, p2.y, bonePaint)
                }
            }

            // Joints
            for (p in px) {
                canvas.drawCircle(p.x, p.y, lw + 2f, jointPaint)
            }

            // Finger vectors & nail bases
            for ((name, indices) in FINGERS) {
                val dip = px[indices[2]]
                val tip = px[indices[3]]

                canvas.drawLine(dip.x, dip.y, tip.x, tip.y, vectorPaint)
                canvas.drawCircle(dip.x, dip.y, lw + 5f, dipPaint)
                canvas.drawCircle(tip.x, tip.y, lw + 5f, tipPaint)

                val pose = comp.poses[name]
                if (pose != null) {
                    canvas.drawCircle(pose.rawX, pose.rawY, lw + 4f, basePaint)

                    val deg = Math.toDegrees(pose.rawAng.toDouble()).toInt()
                    val txt = "${name.take(2)} ${deg}° w${pose.rawW.toInt()} a${String.format("%.1f", pose.targetAlpha)}"
                    canvas.drawText(txt, tip.x + 10f, tip.y - 10f, textPaint)
                }
            }

            // HUD string
            val wrist = px[0]
            val face = if (comp.backConf > 0.1f) "BACK" else (if (comp.backConf < -0.1f) "PALM" else "EDGE")
            val hud = "Hand #$hi ${comp.label} (${(comp.score * 100).toInt()}%) $face (${String.format("%+.2f", comp.backConf)}) Unit=${comp.unitPx.toInt()}px"
            canvas.drawText(hud, max(16f, wrist.x - 140f), min(h - 20f, wrist.y + 40f), hudPaint)
        }
    }
}
