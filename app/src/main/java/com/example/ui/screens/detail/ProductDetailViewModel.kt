package com.example.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Product
import com.example.data.model.Variant
import com.example.data.repository.ProductRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProductDetailUiState(
    val product: Product? = null,
    val selectedVariantIndex: Int = 0,
    val isLoading: Boolean = true,
    val snackbarMessage: String? = null
)

class ProductDetailViewModel(
    private val productRepository: ProductRepository,
    private val productId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProductDetailUiState())
    val uiState: StateFlow<ProductDetailUiState> = _uiState.asStateFlow()

    init {
        loadProduct()
    }

    private fun loadProduct() {
        viewModelScope.launch {
            productRepository.getProductById(productId).collect { p ->
                _uiState.value = _uiState.value.copy(
                    product = p,
                    isLoading = false
                )
            }
        }
    }

    fun selectVariant(index: Int) {
        _uiState.value = _uiState.value.copy(selectedVariantIndex = index)
    }

    fun onBuyNowClicked() {
        _uiState.value = _uiState.value.copy(snackbarMessage = "Added to bag! (v1 stub)")
    }

    fun dismissSnackbar() {
        _uiState.value = _uiState.value.copy(snackbarMessage = null)
    }

    fun getSelectedVariant(): Variant? {
        val p = _uiState.value.product ?: return null
        val idx = _uiState.value.selectedVariantIndex
        return p.variants.getOrNull(idx) ?: p.variants.firstOrNull()
    }
}
