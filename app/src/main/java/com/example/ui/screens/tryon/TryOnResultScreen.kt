package com.example.ui.screens.tryon

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.BuildConfig
import com.example.R
import com.example.cv.tryon.HandDetector
import com.example.cv.tryon.ManualAdjustments
import com.example.data.model.Product
import com.example.data.model.Variant
import com.example.data.repository.ProductRepository
import com.example.data.repository.TryOnSessionRepository
import com.example.ui.screens.home.rememberImageSource
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.ZeferCard
import com.example.ui.theme.ZeferPrimary
import com.example.ui.theme.ZeferSurface
import com.example.ui.theme.ZeferText
import com.example.ui.theme.ZeferTextDim
import com.example.ui.theme.ZeferWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TryOnResultScreen(
    productId: String,
    variantId: String,
    productRepository: ProductRepository,
    sessionRepository: TryOnSessionRepository,
    handDetector: HandDetector,
    onNavigateBack: () -> Unit,
    onNavigateToMyTryOns: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val sessionState by sessionRepository.sessionState.collectAsState()

    var product by remember { mutableStateOf<Product?>(null) }
    var selectedVariant by remember { mutableStateOf<Variant?>(null) }
    var isHoldingToCompare by remember { mutableStateOf(false) }
    var showDebugOverlay by remember { mutableStateOf(false) }
    var showTuningSheet by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(productId, variantId) {
        val p = productRepository.getProductById(productId).firstOrNull()
        product = p
        val v = p?.variants?.firstOrNull { it.id == variantId } ?: p?.variants?.firstOrNull()
        selectedVariant = v

        // Run detection once on the static image
        if (sessionState.originalBitmap != null && sessionState.computedHands.isEmpty()) {
            sessionRepository.runDetection(handDetector, v)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Black,
        modifier = modifier
            .fillMaxSize()
            .testTag("tryon_result_root")
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val renderResult = sessionState.renderResult
            val originalBmp = sessionState.originalBitmap

            // 1. Main Photo Viewer with press-and-hold compare
            if (originalBmp != null) {
                val displayBitmap = when {
                    isHoldingToCompare -> originalBmp
                    showDebugOverlay && renderResult != null -> renderResult.debugBitmap
                    renderResult != null -> renderResult.resultBitmap
                    else -> originalBmp
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    isHoldingToCompare = true
                                    tryAwaitRelease()
                                    isHoldingToCompare = false
                                }
                            )
                        }
                        .testTag("result_image_container"),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = displayBitmap.asImageBitmap(),
                        contentDescription = "Try-On Result",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // 2. Loading Spinner Overlay
            if (sessionState.isDetecting) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = ZeferCard),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            CircularProgressIndicator(color = ZeferPrimary)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = sessionState.statusHint,
                                color = ZeferText,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // 3. Top Control Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .align(Alignment.TopCenter)
            ) {
                // Back Button
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .testTag("result_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Compare Indicator / Hint Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isHoldingToCompare) Color(0xFFFF5252) else Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (isHoldingToCompare) "ORIGINAL PHOTO" else "HOLD TO COMPARE",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // My Try-Ons gallery quick access
                    IconButton(
                        onClick = onNavigateToMyTryOns,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .testTag("result_my_tryons_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = "My Try-Ons",
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Debug Toggle
                    IconButton(
                        onClick = {
                            showDebugOverlay = !showDebugOverlay
                            if (showDebugOverlay) showTuningSheet = true
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (showDebugOverlay) Color(0xFF00E5FF) else Color.Black.copy(alpha = 0.55f))
                            .testTag("result_debug_toggle_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = "Debug",
                            tint = if (showDebugOverlay) Color.Black else Color.White
                        )
                    }
                }
            }

            // 4. Status / Failure Hint Banner (Nails hidden or palm facing)
            if (sessionState.computedHands.isNotEmpty() &&
                sessionState.computedHands.all { it.poses.values.none { p -> p.targetAlpha >= 0.05f } }) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF263238)),
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .align(Alignment.TopCenter)
                        .padding(top = 70.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "Nails hidden? Try the other side",
                            color = Color.White,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                val currentFacing = sessionState.adjustments.facingOverride
                                val next = if (currentFacing == "palm") "back" else "palm"
                                scope.launch {
                                    sessionRepository.updateAdjustments(
                                        sessionState.adjustments.copy(facingOverride = next),
                                        selectedVariant
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Flip Facing", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 5. Bottom Controls: Fast Variant Swapping + Action Buttons
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp)
            ) {
                // Live Variant Carousel (instantly swaps sprites without re-running MediaPipe!)
                val variants = product?.variants.orEmpty()
                if (variants.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .testTag("result_variant_carousel")
                    ) {
                        items(variants) { v ->
                            val isSelected = v.id == selectedVariant?.id
                            val thumbModel = rememberImageSource(context = context, rawPath = v.thumbImage)

                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ZeferCard)
                                    .border(
                                        width = if (isSelected) 3.dp else 0.dp,
                                        color = if (isSelected) Color.White else Color.Transparent,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        selectedVariant = v
                                        scope.launch {
                                            sessionRepository.renderCurrent(v)
                                        }
                                    }
                                    .padding(if (isSelected) 2.dp else 0.dp)
                                    .testTag("variant_item_${v.id}")
                            ) {
                                AsyncImage(
                                    model = thumbModel,
                                    contentDescription = v.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(10.dp))
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Action Buttons Row (Save, Share, Try Another Photo)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    // Try Another Photo
                    Button(
                        onClick = onNavigateBack,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.25f),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("try_another_photo_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Retake", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    // Share Button
                    Button(
                        onClick = {
                            val bmp = sessionState.renderResult?.resultBitmap ?: return@Button
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val cacheFile = File(context.cacheDir, "shared_look.jpg")
                                    FileOutputStream(cacheFile).use { out ->
                                        bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
                                    }
                                    val shareUri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        cacheFile
                                    )
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "image/jpeg"
                                        putExtra(Intent.EXTRA_STREAM, shareUri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    withContext(Dispatchers.Main) {
                                        context.startActivity(Intent.createChooser(intent, "Share your Zefer look"))
                                    }
                                } catch (e: Exception) {
                                    // Handle share error
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.25f),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("share_result_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    // Save to Gallery
                    Button(
                        onClick = {
                            val p = product ?: return@Button
                            val v = selectedVariant ?: return@Button
                            scope.launch {
                                isSaving = true
                                val uri = sessionRepository.saveResult(p, v)
                                isSaving = false
                                if (uri != null) {
                                    val action = snackbarHostState.showSnackbar(
                                        message = "Saved to Pictures/Zefer!",
                                        actionLabel = "Open",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (action == SnackbarResult.ActionPerformed) {
                                        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri, "image/jpeg")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(viewIntent)
                                    }
                                }
                            }
                        },
                        enabled = !isSaving,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ZeferWhite,
                            contentColor = ZeferPrimary
                        ),
                        modifier = Modifier
                            .weight(1.2f)
                            .height(48.dp)
                            .testTag("save_result_button")
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(color = ZeferPrimary, modifier = Modifier.size(20.dp))
                        } else {
                            Icon(Icons.Default.Download, contentDescription = null, tint = ZeferPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 6. Tuning Sheet (Debug Mode)
            if (showTuningSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showTuningSheet = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = Color(0xFF1E1E1E),
                    contentColor = Color.White
                ) {
                    TuningSheetContent(
                        adjustments = sessionState.adjustments,
                        onAdjustmentsChange = { adj ->
                            scope.launch {
                                sessionRepository.updateAdjustments(adj, selectedVariant)
                            }
                        },
                        onExportDebugZip = {
                            val p = product ?: return@TuningSheetContent
                            val v = selectedVariant ?: return@TuningSheetContent
                            scope.launch(Dispatchers.IO) {
                                val zipFile = sessionRepository.exportDebugZip(p, v)
                                val zipUri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    zipFile
                                )
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/zip"
                                    putExtra(Intent.EXTRA_STREAM, zipUri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                withContext(Dispatchers.Main) {
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Debug ZIP"))
                                }
                            }
                        },
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TuningSheetContent(
    adjustments: ManualAdjustments,
    onAdjustmentsChange: (ManualAdjustments) -> Unit,
    onExportDebugZip: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "ZEFER TRY-ON TUNING",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00E5FF),
                fontFamily = FontFamily.Monospace
            )
            Button(
                onClick = onExportDebugZip,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Share Debug ZIP", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Size slider (0.7 - 1.3)
        Text("Size: ${String.format("%.2f", adjustments.size)}x", color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        Slider(
            value = adjustments.size,
            onValueChange = { onAdjustmentsChange(adjustments.copy(size = it)) },
            valueRange = 0.7f..1.3f,
            colors = SliderDefaults.colors(thumbColor = Color(0xFF00E5FF), activeTrackColor = Color(0xFF00E5FF))
        )

        // Length slider (0.7 - 1.4)
        Text("Length: ${String.format("%.2f", adjustments.length)}x", color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        Slider(
            value = adjustments.length,
            onValueChange = { onAdjustmentsChange(adjustments.copy(length = it)) },
            valueRange = 0.7f..1.4f,
            colors = SliderDefaults.colors(thumbColor = Color(0xFFFFD600), activeTrackColor = Color(0xFFFFD600))
        )

        // Offset slider (-0.3 - 0.3)
        Text("Offset (+ toward tip): ${String.format("%+.2f", adjustments.offset)}", color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        Slider(
            value = adjustments.offset,
            onValueChange = { onAdjustmentsChange(adjustments.copy(offset = it)) },
            valueRange = -0.3f..0.3f,
            colors = SliderDefaults.colors(thumbColor = Color(0xFF00E676), activeTrackColor = Color(0xFF00E676))
        )

        // Rotate slider (-20 - 20 deg)
        Text("Rotate Angle: ${adjustments.rotDeg.toInt()}°", color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        Slider(
            value = adjustments.rotDeg,
            onValueChange = { onAdjustmentsChange(adjustments.copy(rotDeg = it)) },
            valueRange = -20f..20f,
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White)
        )

        // Shadow slider (0.0 - 0.5)
        Text("Shadow Strength: ${String.format("%.2f", adjustments.shadow)}", color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        Slider(
            value = adjustments.shadow,
            onValueChange = { onAdjustmentsChange(adjustments.copy(shadow = it)) },
            valueRange = 0.0f..0.5f
        )

        // Light Match slider (0.0 - 1.0)
        Text("Light Match Strength: ${String.format("%.2f", adjustments.lightMatch)}", color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        Slider(
            value = adjustments.lightMatch,
            onValueChange = { onAdjustmentsChange(adjustments.copy(lightMatch = it)) },
            valueRange = 0.0f..1.0f
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Facing Override (auto / back / palm)
        Text("Facing Override:", color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("auto", "back", "palm").forEach { opt ->
                val isSelected = adjustments.facingOverride == opt
                Button(
                    onClick = { onAdjustmentsChange(adjustments.copy(facingOverride = opt)) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) Color(0xFF00E5FF) else Color(0xFF333333)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(opt.uppercase(), color = if (isSelected) Color.Black else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
