package com.example.data.repository

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.media.ExifInterface
import com.example.BuildConfig
import com.example.cv.tryon.ComputedHand
import com.example.cv.tryon.DetectionAttempt
import com.example.cv.tryon.DetectedHand
import com.example.cv.tryon.HandDetector
import com.example.cv.tryon.ManualAdjustments
import com.example.cv.tryon.Params
import com.example.cv.tryon.RenderOptions
import com.example.cv.tryon.RenderResult
import com.example.cv.tryon.StaticRenderer
import com.example.cv.tryon.computeHand
import com.example.data.model.Product
import com.example.data.model.Variant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max

data class SavedTryOn(
    val id: String,
    val productId: String,
    val productName: String,
    val variantId: String,
    val variantName: String,
    val imagePath: String,
    val jsonPath: String,
    val timestamp: Long
)

data class TryOnSessionState(
    val originalBitmap: Bitmap? = null,
    val productId: String = "",
    val variantId: String = "",
    val detectedHands: List<DetectedHand> = emptyList(),
    val computedHands: List<ComputedHand> = emptyList(),
    val detectionAttempt: DetectionAttempt? = null,
    val isDetecting: Boolean = false,
    val renderResult: RenderResult? = null,
    val adjustments: ManualAdjustments = ManualAdjustments(),
    val statusHint: String = "",
    val error: String? = null
)

class TryOnSessionRepository(private val context: Context) {

    private val _sessionState = MutableStateFlow(TryOnSessionState())
    val sessionState: StateFlow<TryOnSessionState> = _sessionState.asStateFlow()

    private val spriteCache = mutableMapOf<String, Bitmap>()

    fun resetSession() {
        _sessionState.value = TryOnSessionState()
    }

    suspend fun setPhoto(
        inputStream: InputStream,
        productId: String,
        variantId: String
    ) = withContext(Dispatchers.IO) {
        val tempFile = File.createTempFile("photo_input", ".tmp", context.cacheDir)
        FileOutputStream(tempFile).use { out ->
            inputStream.copyTo(out)
        }

        val exif = try { ExifInterface(tempFile.absolutePath) } catch (e: Exception) { null }
        val orientation = exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            ?: ExifInterface.ORIENTATION_NORMAL

        val rotationAngle = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }

        // Decode bounds first to cap long edge at 1600 px
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(tempFile.absolutePath, options)
        val maxEdge = max(options.outWidth, options.outHeight)
        var sampleSize = 1
        while (maxEdge / sampleSize > 1600) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(tempFile.absolutePath, decodeOptions)
        tempFile.delete()

        val correctedBitmap = if (rotationAngle != 0f && decoded != null) {
            val matrix = Matrix().apply { postRotate(rotationAngle) }
            val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            if (rotated != decoded) decoded.recycle()
            rotated
        } else {
            decoded
        }

        _sessionState.value = TryOnSessionState(
            originalBitmap = correctedBitmap,
            productId = productId,
            variantId = variantId,
            statusHint = "Photo loaded. Ready to detect hands."
        )
    }

    suspend fun runDetection(
        detector: HandDetector,
        variant: Variant?
    ) = withContext(Dispatchers.Default) {
        val original = _sessionState.value.originalBitmap ?: return@withContext
        _sessionState.value = _sessionState.value.copy(isDetecting = true, statusHint = "Detecting hand landmarks...")

        val (hands, attempt) = detector.detectHands(original, fallback = true)

        val params = Params()
        val computed = hands.map { computeHand(it, params, _sessionState.value.adjustments.facingOverride, _sessionState.value.adjustments) }

        val hint = if (hands.isEmpty()) {
            "No hand found. Try a clear photo with the back of your hand in good light."
        } else if (computed.all { it.poses.values.none { p -> p.targetAlpha >= 0.05f } }) {
            "Hand detected but nails hidden (palm facing camera). Tap 'Nails missing? Try the other side'."
        } else {
            "Hand detected" + if (attempt != null && attempt.angle != 0) " (after ${attempt.angle}° rotation fallback)" else "!"
        }

        _sessionState.value = _sessionState.value.copy(
            detectedHands = hands,
            computedHands = computed,
            detectionAttempt = attempt,
            isDetecting = false,
            statusHint = hint
        )

        // Initial render
        if (variant != null && computed.isNotEmpty()) {
            renderCurrent(variant)
        }
    }

    suspend fun updateAdjustments(
        adjustments: ManualAdjustments,
        variant: Variant?
    ) = withContext(Dispatchers.Default) {
        val hands = _sessionState.value.detectedHands
        val params = Params()
        val computed = hands.map { computeHand(it, params, adjustments.facingOverride, adjustments) }

        _sessionState.value = _sessionState.value.copy(
            adjustments = adjustments,
            computedHands = computed
        )

        if (variant != null && computed.isNotEmpty()) {
            renderCurrent(variant)
        }
    }

    suspend fun renderCurrent(variant: Variant) = withContext(Dispatchers.Default) {
        val original = _sessionState.value.originalBitmap ?: return@withContext
        val computed = _sessionState.value.computedHands
        if (computed.isEmpty()) return@withContext

        val (sprites, mirroredSprites) = loadSpritesForVariant(variant)
        val opts = RenderOptions(
            shadow = _sessionState.value.adjustments.shadow,
            lightStrength = _sessionState.value.adjustments.lightMatch,
            lengthMult = _sessionState.value.adjustments.length
        )

        val result = StaticRenderer.render(original, computed, sprites, mirroredSprites, opts)
        _sessionState.value = _sessionState.value.copy(renderResult = result)
    }

    private suspend fun loadSpritesForVariant(variant: Variant): Pair<Map<String, Bitmap>, Map<String, Bitmap>> = withContext(Dispatchers.IO) {
        val normal = mutableMapOf<String, Bitmap>()
        val mirrored = mutableMapOf<String, Bitmap>()

        for ((finger, path) in variant.sprites) {
            val key = "${variant.id}_$finger"
            val bmp = spriteCache.getOrPut(key) {
                if (path.startsWith("/") || path.startsWith("file://")) {
                    val clean = path.removePrefix("file://")
                    BitmapFactory.decodeFile(clean)
                } else {
                    context.assets.open(path).use { BitmapFactory.decodeStream(it) }
                }
            }
            if (bmp != null) {
                normal[finger] = bmp

                val mirrorKey = "${variant.id}_${finger}_m"
                val mBmp = spriteCache.getOrPut(mirrorKey) {
                    val matrix = Matrix().apply { preScale(-1f, 1f) }
                    Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
                }
                mirrored[finger] = mBmp
            }
        }
        Pair(normal, mirrored)
    }

    suspend fun saveResult(
        product: Product,
        variant: Variant
    ): Uri? = withContext(Dispatchers.IO) {
        val renderResult = _sessionState.value.renderResult ?: return@withContext null
        val resultBmp = renderResult.resultBitmap
        val debugBmp = renderResult.debugBitmap

        val timestamp = System.currentTimeMillis()
        val timeString = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(timestamp))
        val filename = "ZEFER_$timeString.jpg"

        // 1. MediaStore save to Pictures/Zefer/
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Zefer")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
            resolver.openOutputStream(uri)?.use { out ->
                resultBmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
        }

        // 2. App-private storage save to filesDir/tryon_results/
        val tryonDir = File(context.filesDir, "tryon_results").apply { mkdirs() }
        val localImageFile = File(tryonDir, "ZEFER_$timeString.jpg")
        val localDebugFile = File(tryonDir, "ZEFER_${timeString}_debug.jpg")
        val localJsonFile = File(tryonDir, "ZEFER_$timeString.json")

        FileOutputStream(localImageFile).use { out ->
            resultBmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        FileOutputStream(localDebugFile).use { out ->
            debugBmp.compress(Bitmap.CompressFormat.JPEG, 88, out)
        }

        val json = buildDebugJson(product, variant, timestamp, _sessionState.value)
        localJsonFile.writeText(json.toString(2))

        uri
    }

    suspend fun exportDebugZip(
        product: Product,
        variant: Variant
    ): File = withContext(Dispatchers.IO) {
        val renderResult = _sessionState.value.renderResult ?: throw IllegalStateException("No render result")
        val timestamp = System.currentTimeMillis()
        val timeString = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(timestamp))

        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val zipFile = File(exportDir, "ZEFER_DEBUG_$timeString.zip")

        val json = buildDebugJson(product, variant, timestamp, _sessionState.value)

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            // 1. Result Image
            zos.putNextEntry(ZipEntry("ZEFER_${timeString}_result.jpg"))
            renderResult.resultBitmap.compress(Bitmap.CompressFormat.JPEG, 92, zos)
            zos.closeEntry()

            // 2. Debug Image
            zos.putNextEntry(ZipEntry("ZEFER_${timeString}_debug.jpg"))
            renderResult.debugBitmap.compress(Bitmap.CompressFormat.JPEG, 88, zos)
            zos.closeEntry()

            // 3. JSON
            zos.putNextEntry(ZipEntry("ZEFER_$timeString.json"))
            zos.write(json.toString(2).toByteArray())
            zos.closeEntry()
        }

        zipFile
    }

    private fun buildDebugJson(
        product: Product,
        variant: Variant,
        timestamp: Long,
        state: TryOnSessionState
    ): JSONObject {
        return JSONObject().apply {
            put("app_version", BuildConfig.VERSION_NAME)
            put("timestamp", timestamp)
            put("product_id", product.id)
            put("product_name", product.name)
            put("variant_id", variant.id)
            put("variant_name", variant.name)
            put("attempt", JSONObject().apply {
                put("angle", state.detectionAttempt?.angle ?: 0)
                put("up", state.detectionAttempt?.up ?: 1.0f)
            })
            put("adjustments", JSONObject().apply {
                put("size", state.adjustments.size)
                put("length", state.adjustments.length)
                put("offset", state.adjustments.offset)
                put("rotDeg", state.adjustments.rotDeg)
                put("shadow", state.adjustments.shadow)
                put("lightMatch", state.adjustments.lightMatch)
                put("facingOverride", state.adjustments.facingOverride)
            })

            val handsArray = JSONArray()
            for (comp in state.computedHands) {
                val hObj = JSONObject().apply {
                    put("label", comp.label)
                    put("score", comp.score)
                    put("appears_right", comp.appearsRight)
                    put("back_conf", comp.backConf)
                    put("unit_px", comp.unitPx)
                    put("yaw_cos", comp.yawCos)

                    val posesObj = JSONObject()
                    for ((f, p) in comp.poses) {
                        posesObj.put(f, JSONObject().apply {
                            put("raw_x", p.rawX)
                            put("raw_y", p.rawY)
                            put("raw_ang", p.rawAng)
                            put("raw_w", p.rawW)
                            put("raw_h", p.rawH)
                            put("flex_deg", p.flexDeg)
                            put("proj", p.proj)
                            put("target_alpha", p.targetAlpha)
                            put("width_rel", p.widthRel)
                        })
                    }
                    put("poses", posesObj)
                }
                handsArray.put(hObj)
            }
            put("hands", handsArray)
        }
    }

    suspend fun getSavedTryOns(): List<SavedTryOn> = withContext(Dispatchers.IO) {
        val tryonDir = File(context.filesDir, "tryon_results")
        if (!tryonDir.exists()) return@withContext emptyList()

        val jsonFiles = tryonDir.listFiles { _, name -> name.endsWith(".json") }.orEmpty()
        val list = mutableListOf<SavedTryOn>()

        for (file in jsonFiles.sortedByDescending { it.lastModified() }) {
            try {
                val json = JSONObject(file.readText())
                val id = file.nameWithoutExtension
                val pId = json.optString("product_id")
                val pName = json.optString("product_name")
                val vId = json.optString("variant_id")
                val vName = json.optString("variant_name")
                val ts = json.optLong("timestamp", file.lastModified())

                val imgFile = File(tryonDir, "$id.jpg")
                if (imgFile.exists()) {
                    list.add(
                        SavedTryOn(
                            id = id,
                            productId = pId,
                            productName = pName,
                            variantId = vId,
                            variantName = vName,
                            imagePath = imgFile.absolutePath,
                            jsonPath = file.absolutePath,
                            timestamp = ts
                        )
                    )
                }
            } catch (e: Exception) {
                // Ignore corrupt record
            }
        }
        list
    }

    suspend fun deleteSavedTryOn(id: String) = withContext(Dispatchers.IO) {
        val tryonDir = File(context.filesDir, "tryon_results")
        File(tryonDir, "$id.json").delete()
        File(tryonDir, "$id.jpg").delete()
        File(tryonDir, "${id}_debug.jpg").delete()
    }
}
