package com.example.ui.screens.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.model.Manifest
import com.example.data.model.Product
import com.example.data.model.Variant
import com.example.ui.theme.BrushScriptFontFamily
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.ZeferBg
import com.example.ui.theme.ZeferCard
import com.example.ui.theme.ZeferPrimary
import com.example.ui.theme.ZeferSurface
import com.example.ui.theme.ZeferText
import com.example.ui.theme.ZeferTextDim
import com.example.ui.theme.ZeferTheme

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToDetail: (productId: String) -> Unit,
    onNavigateToAdmin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val products by viewModel.products.collectAsState()

    HomeScreenContent(
        searchQuery = uiState.searchQuery,
        isLoading = uiState.isLoading,
        products = products,
        onSearchQueryChange = viewModel::onSearchQueryChanged,
        onProductClick = onNavigateToDetail,
        onLogoLongClick = viewModel::showAdminPinDialog,
        modifier = modifier
    )

    if (uiState.showAdminPinDialog) {
        AdminPinDialog(
            hasError = uiState.adminPinError,
            onDismiss = viewModel::hideAdminPinDialog,
            onConfirm = { pin ->
                viewModel.verifyPin(pin) {
                    onNavigateToAdmin()
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreenContent(
    searchQuery: String,
    isLoading: Boolean,
    products: List<Product>,
    onSearchQueryChange: (String) -> Unit,
    onProductClick: (productId: String) -> Unit,
    onLogoLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        containerColor = ZeferBg,
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen_root")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Top Header: Small wordmark top-left with logo (long press triggers admin)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {},
                        onLongClick = onLogoLongClick
                    )
                    .testTag("home_header_row")
            ) {
                Image(
                    painter = painterResource(id = R.drawable.zefer_logo),
                    contentDescription = stringResource(id = R.string.app_name),
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("home_logo_mark")
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(id = R.string.wordmark),
                    fontFamily = BrushScriptFontFamily,
                    fontSize = 28.sp,
                    color = ZeferText,
                    modifier = Modifier.testTag("home_wordmark_text")
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Full-width search field (#D9D9D9, magnifier icon, placeholder "Search for nails extension...")
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = {
                    Text(
                        text = stringResource(id = R.string.search_placeholder),
                        color = ZeferTextDim,
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = ZeferTextDim
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = ZeferSurface,
                    unfocusedContainerColor = ZeferSurface,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = ZeferPrimary,
                    focusedTextColor = ZeferText,
                    unfocusedTextColor = ZeferText
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("home_search_input")
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 2-column LazyVerticalGrid of white rounded product cards (12dp radius) with skeleton shimmer loading states
            if (isLoading) {
                SkeletonShimmerGrid()
            } else if (products.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(id = R.string.no_products_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = ZeferTextDim,
                        modifier = Modifier.testTag("home_empty_text")
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("home_product_grid")
                ) {
                    items(products, key = { it.id }) { product ->
                        ProductGridCard(
                            product = product,
                            onClick = { onProductClick(product.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProductGridCard(
    product: Product,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val primaryVariant = product.variants.firstOrNull()
    val imagePath = primaryVariant?.heroImage ?: primaryVariant?.thumbImage ?: ""

    // Image source resolves either asset or file path
    val imageModel = rememberImageSource(context = context, rawPath = imagePath)

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = ZeferCard,
            contentColor = ZeferText
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("product_card_${product.id}")
    ) {
        Column(
            modifier = Modifier.padding(10.dp)
        ) {
            // Rounded hero image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.0f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ZeferSurface),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = imageModel,
                    contentDescription = product.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = product.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = InterFontFamily,
                maxLines = 1,
                color = ZeferText
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "${product.variants.size} Design Variants",
                style = MaterialTheme.typography.labelSmall,
                color = ZeferTextDim
            )
        }
    }
}

@Composable
fun rememberImageSource(context: android.content.Context, rawPath: String): Any {
    return if (rawPath.startsWith("/") || rawPath.startsWith("file://")) {
        rawPath
    } else {
        "file:///android_asset/$rawPath"
    }
}

@Composable
fun SkeletonShimmerGrid(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier.fillMaxSize()
    ) {
        items(6) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = ZeferCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.0f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(ZeferSurface.copy(alpha = alpha))
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(ZeferSurface.copy(alpha = alpha))
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.4f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(ZeferSurface.copy(alpha = alpha))
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    ZeferTheme {
        HomeScreenContent(
            searchQuery = "",
            isLoading = false,
            products = listOf(
                Product(
                    id = "p1",
                    name = "Wine Gold Royal",
                    description = "Press on nails",
                    variants = listOf(
                        Variant("v1", "Wine", "", sprites = emptyMap())
                    )
                )
            ),
            onSearchQueryChange = {},
            onProductClick = {},
            onLogoLongClick = {}
        )
    }
}
