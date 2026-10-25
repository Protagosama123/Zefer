package com.example.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Product
import com.example.data.repository.ProductRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val showAdminPinDialog: Boolean = false,
    val adminPinError: Boolean = false
)

class HomeViewModel(
    private val productRepository: ProductRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        // Shimmer simulation for smooth loading experience
        viewModelScope.launch {
            delay(400)
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    val products: StateFlow<List<Product>> = _uiState.combine(productRepository.getProducts()) { state, allProducts ->
        if (state.searchQuery.isBlank()) {
            allProducts
        } else {
            val q = state.searchQuery.trim().lowercase()
            allProducts.filter { product ->
                product.name.lowercase().contains(q) ||
                product.description.lowercase().contains(q) ||
                product.variants.any { it.name.lowercase().contains(q) }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun showAdminPinDialog() {
        _uiState.value = _uiState.value.copy(showAdminPinDialog = true, adminPinError = false)
    }

    fun hideAdminPinDialog() {
        _uiState.value = _uiState.value.copy(showAdminPinDialog = false, adminPinError = false)
    }

    fun verifyPin(pin: String, onSuccess: () -> Unit) {
        if (pin == "1234") {
            hideAdminPinDialog()
            onSuccess()
        } else {
            _uiState.value = _uiState.value.copy(adminPinError = true)
        }
    }
}
