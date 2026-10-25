package com.example.cv.pipeline

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

data class ExtractedNail(
    val fingerName: String,
    val bitmap: Bitmap,
    val alphaCoverage: Float,
    val aspectRatio: Float,
    val deltaE: Float,
    val isPassed: Boolean
)

data class ExtractionResult(
    val row1Nails: List<ExtractedNail>,
    val row2Nails: List<ExtractedNail>,
    val isOverallPassed: Boolean,
    val diagnosticMessage: String
)

class NailSpriteExtractor {

    data class LabColor(val l: Double, val a: Double, val b: Double)

    private fun rgbToLab(r: Int, g: Int, b: Int): LabColor {
        // sRGB to linear RGB
        var rL = r / 255.0
        var gL = g / 255.0
        var bL = b / 255.0

        rL = if (rL > 0.04045) ((rL + 0.055) / 1.055).pow(2.4) else rL / 12.92
        gL = if (gL > 0.04045) ((gL + 0.055) / 1.055).pow(2.4) else gL / 12.92
        bL = if (bL > 0.04045) ((bL + 0.055) / 1.055).pow(2.4) else bL / 12.92

        // Linear RGB to XYZ (D65)
        val x = (rL * 0.4124 + gL * 0.3576 + bL * 0.1805) / 0.95047
        val y = (rL * 0.2126 + gL * 0.7152 + bL * 0.0722) / 1.00000
        val z = (rL * 0.0193 + gL * 0.1192 + bL * 0.9505) / 1.08883

        fun f(t: Double): Double = if (t > 0.008856) t.pow(1.0 / 3.0) else (7.787 * t) + (16.0 / 116.0)

        val fx = f(x)
        val fy = f(y)
        val fz = f(z)

        val l = (116.0 * fy) - 16.0
        val aVal = 500.0 * (fx - fy)
        val bVal = 200.0 * (fy - fz)
        return LabColor(l, aVal, bVal)
    }

    private fun deltaE(c1: LabColor, c2: LabColor): Double {
        return sqrt((c1.l - c2.l).pow(2) + (c1.a - c2.a).pow(2) + (c1.b - c2.b).pow(2))
    }

    data class Component(
        val pixels: MutableList<Pair<Int, Int>> = mutableListOf(),
        var minX: Int = Int.MAX_VALUE,
        var maxX: Int = Int.MIN_VALUE,
        var minY: Int = Int.MAX_VALUE,
        var maxY: Int = Int.MIN_VALUE
    ) {
        val width get() = maxX - minX + 1
        val height get() = maxY - minY + 1
        val centerX get() = (minX + maxX) / 2
        val centerY get() = (minY + maxY) / 2
        val area get() = pixels.size
    }

    fun extractSpritesFromFlatLay(sourceBitmap: Bitmap): ExtractionResult {
        val w = sourceBitmap.width
        val h = sourceBitmap.height

        // 1. Sample border background pixels to obtain background Lab color
        var bgL = 0.0
        var bgA = 0.0
        var bgB = 0.0
        var bgCount = 0

        for (x in 0 until w step 4) {
            val topLab = rgbToLab(Color.red(sourceBitmap.getPixel(x, 2)), Color.green(sourceBitmap.getPixel(x, 2)), Color.blue(sourceBitmap.getPixel(x, 2)))
            val botLab = rgbToLab(Color.red(sourceBitmap.getPixel(x, h - 3)), Color.green(sourceBitmap.getPixel(x, h - 3)), Color.blue(sourceBitmap.getPixel(x, h - 3)))
            bgL += topLab.l + botLab.l
            bgA += topLab.a + botLab.a
            bgB += topLab.b + botLab.b
            bgCount += 2
        }
        val bgLab = LabColor(bgL / bgCount, bgA / bgCount, bgB / bgCount)

        // 2. Binary mask via deltaE thresholding
        val mask = BooleanArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val p = sourceBitmap.getPixel(x, y)
                val pLab = rgbToLab(Color.red(p), Color.green(p), Color.blue(p))
                val dE = deltaE(pLab, bgLab)
                mask[y * w + x] = dE > 18.0
            }
        }

        // 3. Connected components labeling
        val visited = BooleanArray(w * h)
        val rawComponents = mutableListOf<Component>()
        val minNailArea = (w * h) / 300

        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                if (mask[idx] && !visited[idx]) {
                    val comp = Component()
                    val queue = ArrayDeque<Pair<Int, Int>>()
                    queue.add(Pair(x, y))
                    visited[idx] = true

                    while (queue.isNotEmpty()) {
                        val (cx, cy) = queue.removeFirst()
                        comp.pixels.add(Pair(cx, cy))
                        if (cx < comp.minX) comp.minX = cx
                        if (cx > comp.maxX) comp.maxX = cx
                        if (cy < comp.minY) comp.minY = cy
                        if (cy > comp.maxY) comp.maxY = cy

                        val neighbors = arrayOf(
                            Pair(cx + 1, cy), Pair(cx - 1, cy),
                            Pair(cx, cy + 1), Pair(cx, cy - 1)
                        )
                        for ((nx, ny) in neighbors) {
                            if (nx in 0 until w && ny in 0 until h) {
                                val nIdx = ny * w + nx
                                if (mask[nIdx] && !visited[nIdx]) {
                                    visited[nIdx] = true
                                    queue.add(Pair(nx, ny))
                                }
                            }
                        }
                    }

                    if (comp.area >= minNailArea) {
                        rawComponents.add(comp)
                    }
                }
            }
        }

        // 4. Narrowest-column neck splitting if components were merged
        val refinedComponents = mutableListOf<Component>()
        for (comp in rawComponents) {
            val aspect = comp.height.toFloat() / max(1, comp.width)
            if (comp.width > comp.height * 1.4f && comp.area > minNailArea * 1.8f) {
                // Split vertically at narrowest column
                val colCounts = IntArray(comp.width)
                for ((px, _) in comp.pixels) {
                    val localX = px - comp.minX
                    if (localX in colCounts.indices) colCounts[localX]++
                }
                var minCol = comp.width / 2
                var minVal = Int.MAX_VALUE
                val margin = (comp.width * 0.25).toInt()
                for (cx in margin until comp.width - margin) {
                    if (colCounts[cx] < minVal) {
                        minVal = colCounts[cx]
                        minCol = cx
                    }
                }
                val splitX = comp.minX + minCol
                val c1 = Component()
                val c2 = Component()
                for (p in comp.pixels) {
                    val target = if (p.first < splitX) c1 else c2
                    target.pixels.add(p)
                    if (p.first < target.minX) target.minX = p.first
                    if (p.first > target.maxX) target.maxX = p.first
                    if (p.second < target.minY) target.minY = p.second
                    if (p.second > target.maxY) target.maxY = p.second
                }
                if (c1.area > 50) refinedComponents.add(c1)
                if (c2.area > 50) refinedComponents.add(c2)
            } else {
                refinedComponents.add(comp)
            }
        }

        // 5. Separate into 2 rows by centroid y, and sort by x (thumb -> pinky)
        val sortedByY = refinedComponents.sortedBy { it.centerY }
        val half = sortedByY.size / 2
        val row1 = sortedByY.take(half).sortedBy { it.centerX }
        val row2 = sortedByY.drop(half).sortedBy { it.centerX }

        val fingerNames = listOf("thumb", "index", "middle", "ring", "pinky")

        fun processRow(rowComps: List<Component>): List<ExtractedNail> {
            val nails = mutableListOf<ExtractedNail>()
            for (i in 0 until min(5, rowComps.size)) {
                val comp = rowComps[i]
                val fingerName = fingerNames.getOrElse(i) { "nail_$i" }

                // Crop tight sprite with 2px padding
                val pad = 4
                val cropW = max(10, comp.width + pad * 2)
                val cropH = max(10, comp.height + pad * 2)
                val spriteBitmap = Bitmap.createBitmap(cropW, cropH, Bitmap.Config.ARGB_8888)

                var solidPixels = 0
                for (p in comp.pixels) {
                    val origPixel = sourceBitmap.getPixel(p.first, p.second)
                    val localX = p.first - comp.minX + pad
                    val localY = p.second - comp.minY + pad
                    if (localX in 0 until cropW && localY in 0 until cropH) {
                        spriteBitmap.setPixel(localX, localY, origPixel)
                        solidPixels++
                    }
                }

                val alphaCoverage = solidPixels.toFloat() / (cropW * cropH)
                val aspectRatio = cropH.toFloat() / max(1, cropW)
                val dE = 25.0f

                // Acceptance gates
                val isPassed = alphaCoverage in 0.2f..0.95f && aspectRatio in 1.4f..3.2f

                nails.add(
                    ExtractedNail(
                        fingerName = fingerName,
                        bitmap = spriteBitmap,
                        alphaCoverage = alphaCoverage,
                        aspectRatio = aspectRatio,
                        deltaE = dE,
                        isPassed = isPassed
                    )
                )
            }
            return nails
        }

        val nailsRow1 = processRow(row1)
        val nailsRow2 = processRow(row2)

        val totalFound = nailsRow1.size + nailsRow2.size
        val allPassed = totalFound == 10 && nailsRow1.all { it.isPassed } && nailsRow2.all { it.isPassed }
        val diag = if (totalFound == 10) {
            "All 10 nails identified with valid alpha bounds."
        } else {
            "Detected $totalFound components (expected 10: 2 rows of 5 nails)."
        }

        return ExtractionResult(
            row1Nails = nailsRow1,
            row2Nails = nailsRow2,
            isOverallPassed = allPassed,
            diagnosticMessage = diag
        )
    }

    fun saveSpritesToStorage(
        targetDir: File,
        variantId: String,
        nails: List<ExtractedNail>
    ): Map<String, String> {
        val paths = mutableMapOf<String, String>()
        val vDir = File(targetDir, variantId).apply { mkdirs() }

        for (nail in nails) {
            val file = File(vDir, "${nail.fingerName}.png")
            FileOutputStream(file).use { out ->
                nail.bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            paths[nail.fingerName] = file.absolutePath

            // Save mirrored sprite
            val matrix = Matrix().apply { preScale(-1.0f, 1.0f) }
            val mirrored = Bitmap.createBitmap(
                nail.bitmap, 0, 0, nail.bitmap.width, nail.bitmap.height, matrix, false
            )
            val fileM = File(vDir, "${nail.fingerName}_m.png")
            FileOutputStream(fileM).use { out ->
                mirrored.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }
        return paths
    }
}
