package com.example.ui.screens.tryon

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cv.pipeline.DebugMetrics
import com.example.cv.pipeline.HandLandmarkerHelper
import com.example.cv.pipeline.TunableParams
import com.example.data.model.Product
import com.example.data.model.Variant
import com.example.data.repository.ProductRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream

data class ArTryOnUiState(
    val product: Product? = null,
    val selectedVariant: Variant? = null,
    val isFrontCamera: Boolean = true,
    val isDebugMode: Boolean = false,
    val showDebugHud: Boolean = false,
    val tunableParams: TunableParams = TunableParams(),
    val metrics: DebugMetrics = DebugMetrics(),
    val capturedImage: Bitmap? = null,
    val message: String? = null
)

class ArTryOnViewModel(
    private val productRepository: ProductRepository,
    private val productId: String,
    private val initialVariantId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArTryOnUiState())
    val uiState: StateFlow<ArTryOnUiState> = _uiState.asStateFlow()

    init {
        loadProduct()
    }

    private fun loadProduct() {
        viewModelScope.launch {
            productRepository.getProductById(productId).collect { p ->
                if (p != null) {
                    val variant = p.variants.firstOrNull { it.id == initialVariantId }
                        ?: p.variants.firstOrNull()
                    _uiState.value = _uiState.value.copy(
                        product = p,
                        selectedVariant = variant
                    )
                }
            }
        }
    }

    fun selectVariant(variant: Variant) {
        _uiState.value = _uiState.value.copy(selectedVariant = variant)
    }

    fun toggleCameraFacing() {
        val next = !_uiState.value.isFrontCamera
        _uiState.value = _uiState.value.copy(isFrontCamera = next)
    }

    fun toggleDebugMode() {
        val next = !_uiState.value.isDebugMode
        _uiState.value = _uiState.value.copy(isDebugMode = next, showDebugHud = next)
    }

    fun hideDebugHud() {
        _uiState.value = _uiState.value.copy(showDebugHud = false)
    }

    fun updateTunableParams(params: TunableParams) {
        _uiState.value = _uiState.value.copy(tunableParams = params)
    }

    fun setCapturedImage(bitmap: Bitmap?) {
        _uiState.value = _uiState.value.copy(capturedImage = bitmap)
    }

    fun clearCapturedImage() {
        _uiState.value = _uiState.value.copy(capturedImage = null)
    }

    fun saveCapturedImage(context: Context, bitmap: Bitmap) {
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                saveBitmapToGallery(context, bitmap)
            }
            if (success) {
                _uiState.value = _uiState.value.copy(
                    message = "Photo saved to gallery!",
                    capturedImage = null
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    message = "Could not save photo"
                )
            }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    private fun saveBitmapToGallery(context: Context, bitmap: Bitmap): Boolean {
        return try {
            val filename = "ZEFER_${System.currentTimeMillis()}.jpg"
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Zefer")
                }
            }
            val uri: Uri? = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { stream: OutputStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
                }
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }
}
