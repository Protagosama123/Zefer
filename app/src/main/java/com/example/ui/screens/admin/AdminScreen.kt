package com.example.ui.screens.admin

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.cv.pipeline.ExtractedNail
import com.example.cv.pipeline.ExtractionResult
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.ZeferBg
import com.example.ui.theme.ZeferCard
import com.example.ui.theme.ZeferPrimary
import com.example.ui.theme.ZeferSurface
import com.example.ui.theme.ZeferText
import com.example.ui.theme.ZeferTextDim
import com.example.ui.theme.ZeferWhite

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    viewModel: AdminViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    uiState.message?.let { msg ->
        LaunchedEffect(msg) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    // Zero-permission Android Photo Picker for flat-lay image
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val bmp = android.graphics.BitmapFactory.decodeStream(input)
                    if (bmp != null) {
                        viewModel.setFlatLayImage(bmp)
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.admin_dashboard_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = ZeferText
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("admin_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = ZeferText
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = ZeferBg)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = ZeferBg,
        modifier = modifier
            .fillMaxSize()
            .testTag("admin_screen_root")
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Section 1: Add New Product & Extract Flat-Lay Sprites
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = ZeferCard),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Add New Product & Flat-Lay Sprites",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ZeferText
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Product Name
                        OutlinedTextField(
                            value = uiState.pendingProductName,
                            onValueChange = viewModel::onNameChanged,
                            placeholder = { Text("Product Name (e.g. Sapphire Chrome)", color = ZeferTextDim) },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = ZeferSurface,
                                unfocusedContainerColor = ZeferSurface,
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_product_name_input")
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Description
                        OutlinedTextField(
                            value = uiState.pendingDescription,
                            onValueChange = viewModel::onDescriptionChanged,
                            placeholder = { Text("Product Description", color = ZeferTextDim) },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = ZeferSurface,
                                unfocusedContainerColor = ZeferSurface,
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Flat-Lay Photo Selector
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ZeferSurface),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("admin_pick_photo_button")
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = ZeferText)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Pick Flat-Lay Photo", color = ZeferText, fontSize = 13.sp)
                            }

                            Button(
                                onClick = {
                                    // Synthesize sample flat-lay with 10 nails (2 rows of 5)
                                    val sampleBmp = createSyntheticFlatLayBitmap()
                                    viewModel.setFlatLayImage(sampleBmp)
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ZeferSurface),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("admin_sample_flatlay_button")
                            ) {
                                Text("Sample Flat-Lay", color = ZeferText, fontSize = 13.sp)
                            }
                        }

                        // Preview Flat-Lay
                        if (uiState.pendingFlatLayBitmap != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(ZeferSurface),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = uiState.pendingFlatLayBitmap!!.asImageBitmap(),
                                    contentDescription = "Selected Flat-Lay",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Run Extractor button
                            Button(
                                onClick = { viewModel.runExtraction() },
                                enabled = !uiState.isExtracting,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ZeferPrimary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("admin_run_extractor_button")
                            ) {
                                if (uiState.isExtracting) {
                                    CircularProgressIndicator(color = ZeferWhite, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Extracting Sprites...", color = ZeferWhite)
                                } else {
                                    Text("Run Sprite Extractor", color = ZeferWhite, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Section 2: 10-Sprite Preview & Acceptance Gates
            uiState.extractionResult?.let { result ->
                item {
                    ExtractionPreviewCard(
                        result = result,
                        onConfirm = {
                            viewModel.confirmAndSaveProduct(context) {
                                // Cleared
                            }
                        }
                    )
                }
            }

            // Section 3: Existing Products List
            item {
                Text(
                    text = "Catalog Inventory (${uiState.products.size} Products)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = ZeferText,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(uiState.products, key = { it.id }) { product ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = ZeferCard),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_item_${product.id}")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = product.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = ZeferText
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${product.variants.size} Variants • ID: ${product.id}",
                                style = MaterialTheme.typography.labelSmall,
                                color = ZeferTextDim
                            )
                        }

                        if (product.id.startsWith("admin_")) {
                            IconButton(
                                onClick = { viewModel.deleteProduct(product.id) },
                                modifier = Modifier.testTag("admin_delete_${product.id}")
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExtractionPreviewCard(
    result: ExtractionResult,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = ZeferCard),
        modifier = modifier
            .fillMaxWidth()
            .testTag("admin_extraction_preview_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "10-Sprite Preview & Quality Gates",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = ZeferText
                )

                Icon(
                    imageVector = if (result.isOverallPassed) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = if (result.isOverallPassed) Color(0xFF00C853) else Color(0xFFFF9800)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = result.diagnosticMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = ZeferTextDim
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Row 1 (Thumb -> Pinky)
            Text("Row 1 (Variant A)", style = MaterialTheme.typography.labelSmall, color = ZeferTextDim)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                result.row1Nails.forEach { nail ->
                    NailSpriteThumbnail(nail = nail, modifier = Modifier.weight(1f))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Row 2 (Thumb -> Pinky)
            Text("Row 2 (Variant B)", style = MaterialTheme.typography.labelSmall, color = ZeferTextDim)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                result.row2Nails.forEach { nail ->
                    NailSpriteThumbnail(nail = nail, modifier = Modifier.weight(1f))
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ZeferPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("admin_confirm_save_button")
            ) {
                Text("Confirm & Save to Store", color = ZeferWhite, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun NailSpriteThumbnail(nail: ExtractedNail, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.6f)
                .clip(RoundedCornerShape(6.dp))
                .background(ZeferSurface)
                .border(
                    width = 1.dp,
                    color = if (nail.isPassed) Color(0xFF00C853) else Color.Red,
                    shape = RoundedCornerShape(6.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = nail.bitmap.asImageBitmap(),
                contentDescription = nail.fingerName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(2.dp)
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = nail.fingerName.take(3),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            color = ZeferText
        )
    }
}

// Generates a synthetic flat-lay bitmap for module 0 testing: 10 press-on nails in 2 rows of 5 on light background
fun createSyntheticFlatLayBitmap(): Bitmap {
    val w = 600
    val h = 400
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    canvas.drawColor(AndroidColor.rgb(240, 240, 242))

    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = AndroidColor.rgb(105, 18, 38)
    }

    // 2 rows of 5 nails
    val cols = 5
    for (row in 0 until 2) {
        val cy = 110 + row * 180
        for (col in 0 until cols) {
            val cx = 60 + col * 115
            val nw = 36f
            val nh = 78f
            canvas.drawRoundRect(
                cx - nw / 2, cy - nh / 2, cx + nw / 2, cy + nh / 2,
                14f, 14f, paint
            )
        }
    }
    return bmp
}
