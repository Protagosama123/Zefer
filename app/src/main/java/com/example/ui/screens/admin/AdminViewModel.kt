package com.example.ui.screens.admin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cv.pipeline.ExtractionResult
import com.example.cv.pipeline.NailSpriteExtractor
import com.example.data.model.Manifest
import com.example.data.model.Product
import com.example.data.model.Variant
import com.example.data.repository.ProductRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class AdminUiState(
    val products: List<Product> = emptyMap<String, String>().let { emptyList() },
    val isExtracting: Boolean = false,
    val extractionResult: ExtractionResult? = null,
    val pendingProductName: String = "",
    val pendingDescription: String = "",
    val pendingFlatLayBitmap: Bitmap? = null,
    val message: String? = null
)

class AdminViewModel(
    private val productRepository: ProductRepository,
    private val spriteExtractor: NailSpriteExtractor
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    init {
        loadProducts()
    }

    private fun loadProducts() {
        viewModelScope.launch {
            productRepository.getProducts().collect { list ->
                _uiState.value = _uiState.value.copy(products = list)
            }
        }
    }

    fun onNameChanged(name: String) {
        _uiState.value = _uiState.value.copy(pendingProductName = name)
    }

    fun onDescriptionChanged(desc: String) {
        _uiState.value = _uiState.value.copy(pendingDescription = desc)
    }

    fun setFlatLayImage(bitmap: Bitmap) {
        _uiState.value = _uiState.value.copy(
            pendingFlatLayBitmap = bitmap,
            extractionResult = null
        )
    }

    fun runExtraction() {
        val bitmap = _uiState.value.pendingFlatLayBitmap ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExtracting = true)
            val result = withContext(Dispatchers.Default) {
                spriteExtractor.extractSpritesFromFlatLay(bitmap)
            }
            _uiState.value = _uiState.value.copy(
                isExtracting = false,
                extractionResult = result
            )
        }
    }

    fun confirmAndSaveProduct(context: Context, onSuccess: () -> Unit) {
        val name = _uiState.value.pendingProductName.trim().ifEmpty { "Custom Nail Set" }
        val desc = _uiState.value.pendingDescription.trim().ifEmpty { "Admin created custom press-on nail extension set." }
        val result = _uiState.value.extractionResult ?: return

        viewModelScope.launch {
            val appStorageDir = File(context.filesDir, "custom_sprites").apply { mkdirs() }
            val variantId = "var_${System.currentTimeMillis()}"

            val allNails = result.row1Nails + result.row2Nails
            val paths = withContext(Dispatchers.IO) {
                spriteExtractor.saveSpritesToStorage(appStorageDir, variantId, allNails)
            }

            // Save first nail sprite as thumbnail / hero
            val heroFile = File(appStorageDir, "$variantId/hero.png")
            allNails.firstOrNull()?.bitmap?.let { bmp ->
                withContext(Dispatchers.IO) {
                    FileOutputStream(heroFile).use { out ->
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                }
            }

            val variant = Variant(
                id = variantId,
                name = "$name Variant 1",
                thumbImage = heroFile.absolutePath,
                heroImage = heroFile.absolutePath,
                sprites = paths,
                manifest = Manifest(aspectRatio = 2.0f, anchor = "base-center", tipDirection = "up")
            )

            productRepository.saveAdminProduct(name, desc, listOf(variant))
            _uiState.value = _uiState.value.copy(
                extractionResult = null,
                pendingFlatLayBitmap = null,
                pendingProductName = "",
                pendingDescription = "",
                message = "Product saved to local database!"
            )
            onSuccess()
        }
    }

    fun deleteProduct(productId: String) {
        viewModelScope.launch {
            productRepository.deleteAdminProduct(productId)
            _uiState.value = _uiState.value.copy(message = "Product removed")
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
