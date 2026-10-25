package com.example.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.Manifest
import com.example.data.model.Product
import com.example.data.model.Variant
import com.example.ui.screens.home.rememberImageSource
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.ZeferBg
import com.example.ui.theme.ZeferCard
import com.example.ui.theme.ZeferPrimary
import com.example.ui.theme.ZeferSurface
import com.example.ui.theme.ZeferText
import com.example.ui.theme.ZeferTextDim
import com.example.ui.theme.ZeferTheme
import com.example.ui.theme.ZeferWhite

@Composable
fun ProductDetailScreen(
    viewModel: ProductDetailViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToTryOn: (productId: String, variantId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    uiState.snackbarMessage?.let { msg ->
        LaunchedEffect(msg) {
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissSnackbar()
        }
    }

    ProductDetailContent(
        product = uiState.product,
        selectedVariantIndex = uiState.selectedVariantIndex,
        onSelectVariant = viewModel::selectVariant,
        onBuyNowClick = viewModel::onBuyNowClicked,
        onTryOnClick = {
            val product = uiState.product
            val variant = viewModel.getSelectedVariant()
            if (product != null && variant != null) {
                onNavigateToTryOn(product.id, variant.id)
            }
        },
        onNavigateBack = onNavigateBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailContent(
    product: Product?,
    selectedVariantIndex: Int,
    onSelectVariant: (Int) -> Unit,
    onBuyNowClick: () -> Unit,
    onTryOnClick: () -> Unit,
    onNavigateBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.title_nails),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = ZeferText,
                        modifier = Modifier.testTag("detail_title_text")
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = ZeferText
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = ZeferBg
                )
            )
        },
        bottomBar = {
            // Bottom bar: wide black Buy Now + square black camera button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ZeferBg)
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .testTag("detail_bottom_bar")
            ) {
                // Wide black Buy Now button (bag icon, white text)
                Button(
                    onClick = onBuyNowClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ZeferPrimary,
                        contentColor = ZeferWhite
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("detail_buy_now_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShoppingBag,
                            contentDescription = null,
                            tint = ZeferWhite,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(id = R.string.buy_now),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            fontFamily = InterFontFamily
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Square black camera/aperture icon button at bottom-right -> launches AR Try-On
                Button(
                    onClick = onTryOnClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ZeferPrimary,
                        contentColor = ZeferWhite
                    ),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier
                        .size(52.dp)
                        .testTag("detail_try_on_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Virtual Try-On",
                        tint = ZeferWhite,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = ZeferBg,
        modifier = modifier
            .fillMaxSize()
            .testTag("product_detail_root")
    ) { innerPadding ->
        if (product == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Loading product...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = ZeferTextDim
                )
            }
        } else {
            val variants = product.variants
            val currentVariant = variants.getOrNull(selectedVariantIndex) ?: variants.firstOrNull()
            val heroPath = currentVariant?.heroImage ?: currentVariant?.thumbImage ?: ""
            val heroModel = rememberImageSource(context = context, rawPath = heroPath)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // Large rounded hero image (24dp radius)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.0f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(ZeferCard)
                        .testTag("detail_hero_image_container"),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = heroModel,
                        contentDescription = product.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Row of 5 square thumbnails (12dp radius) representing product design variants — tapping swaps hero
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("detail_variant_thumbnail_row")
                ) {
                    itemsIndexed(variants) { index, variant ->
                        val isSelected = index == selectedVariantIndex
                        val thumbModel = rememberImageSource(context = context, rawPath = variant.thumbImage)

                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(ZeferCard)
                                .border(
                                    width = if (isSelected) 2.dp else 0.dp,
                                    color = if (isSelected) ZeferPrimary else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { onSelectVariant(index) }
                                .padding(if (isSelected) 2.dp else 0.dp)
                                .testTag("variant_thumb_$index")
                        ) {
                            AsyncImage(
                                model = thumbModel,
                                contentDescription = variant.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(10.dp))
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Product name (large, bold)
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = InterFontFamily,
                    color = ZeferText,
                    modifier = Modifier.testTag("detail_product_name")
                )

                // Current variant label
                if (currentVariant != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Variant: ${currentVariant.name}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = ZeferTextDim
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Description
                Text(
                    text = product.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = ZeferText,
                    lineHeight = 22.sp,
                    modifier = Modifier.testTag("detail_product_description")
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ProductDetailScreenPreview() {
    ZeferTheme {
        ProductDetailContent(
            product = Product(
                id = "sample",
                name = "Wine Gold Royal",
                description = "Handcrafted burgundy press-on nails with 24K gold foil flakes.",
                variants = listOf(
                    Variant(id = "v1", name = "Foil", thumbImage = "", sprites = emptyMap())
                )
            ),
            selectedVariantIndex = 0,
            onSelectVariant = {},
            onBuyNowClick = {},
            onTryOnClick = {},
            onNavigateBack = {},
            snackbarHostState = remember { SnackbarHostState() }
        )
    }
}
