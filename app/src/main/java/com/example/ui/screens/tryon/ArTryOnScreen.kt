package com.example.ui.screens.tryon

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.BuildConfig
import com.example.R
import com.example.cv.pipeline.HandLandmarkerHelper
import com.example.data.model.Variant
import com.example.ui.screens.home.rememberImageSource
import com.example.ui.theme.ZeferCard
import com.example.ui.theme.ZeferPrimary
import com.example.ui.theme.ZeferSurface
import com.example.ui.theme.ZeferText
import com.example.ui.theme.ZeferTextDim
import com.example.ui.theme.ZeferWhite

@Composable
fun ArTryOnScreen(
    viewModel: ArTryOnViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    uiState.message?.let { msg ->
        LaunchedEffect(msg) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    // Camera permission check
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var permissionRequested by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        permissionRequested = true
    }

    val landmarkerHelper = remember { HandLandmarkerHelper(context, scope) }

    DisposableEffect(Unit) {
        onDispose {
            landmarkerHelper.close()
        }
    }

    // Update landmarker params when tunable params change
    LaunchedEffect(uiState.tunableParams) {
        landmarkerHelper.updateTunableParams(uiState.tunableParams)
    }

    val trackingResult by landmarkerHelper.trackingResult.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("ar_try_on_root")
    ) {
        if (!hasCameraPermission) {
            // Permission Rationale & Request View
            CameraPermissionRationale(
                permissionRequested = permissionRequested,
                onRequestPermission = {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                },
                onOpenSettings = {
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)
                    )
                    context.startActivity(intent)
                },
                onSyntheticDemo = {
                    hasCameraPermission = true
                    landmarkerHelper.setReplayMode(true)
                },
                onNavigateBack = onNavigateBack
            )
        } else {
            // Full-screen CameraX preview
            CameraPreviewView(
                landmarkerHelper = landmarkerHelper,
                isFrontCamera = uiState.isFrontCamera,
                modifier = Modifier.fillMaxSize()
            )

            // Compose Canvas Overlay reading state inside draw lambda to eliminate per-frame recomposition
            NailOverlayCanvas(
                landmarkerHelper = landmarkerHelper,
                variant = uiState.selectedVariant,
                isDebugMode = uiState.isDebugMode,
                modifier = Modifier.fillMaxSize()
            )

            // Hint banner when hand is not detected
            if (!trackingResult.hasHand && !uiState.showDebugHud) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 80.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.try_on_hint),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Top Control Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .align(Alignment.TopCenter)
            ) {
                // Back button top-left
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .testTag("tryon_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Front / Rear Camera Switch
                    IconButton(
                        onClick = { viewModel.toggleCameraFacing() },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.45f))
                            .testTag("tryon_camera_switch_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = if (uiState.isFrontCamera) {
                                stringResource(id = R.string.front_camera)
                            } else {
                                stringResource(id = R.string.rear_camera)
                            },
                            tint = Color.White
                        )
                    }

                    // Debug Toggle (bug icon, gated behind BuildConfig.DEBUG)
                    if (BuildConfig.DEBUG) {
                        Spacer(modifier = Modifier.width(10.dp))
                        IconButton(
                            onClick = { viewModel.toggleDebugMode() },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (uiState.isDebugMode) Color(0xFF00E5FF)
                                    else Color.Black.copy(alpha = 0.45f)
                                )
                                .testTag("tryon_debug_toggle_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = stringResource(id = R.string.debug_hud),
                                tint = if (uiState.isDebugMode) Color.Black else Color.White
                            )
                        }
                    }
                }
            }

            // Debug HUD Overlay if visible
            if (uiState.showDebugHud && BuildConfig.DEBUG) {
                DebugHudOverlay(
                    metrics = trackingResult.metrics,
                    params = uiState.tunableParams,
                    onParamsChange = viewModel::updateTunableParams,
                    onToggleReplay = { landmarkerHelper.setReplayMode(it) },
                    onClose = viewModel::hideDebugHud,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 16.dp)
                )
            }

            // Bottom Controls: Variant Carousel + Capture Button
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp)
            ) {
                // Bottom carousel of product design variants (live sprite swapping)
                val variants = uiState.product?.variants.orEmpty()
                if (variants.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .testTag("tryon_variant_carousel")
                    ) {
                        items(variants) { variant ->
                            val isSelected = variant.id == uiState.selectedVariant?.id
                            val thumbModel = rememberImageSource(context = context, rawPath = variant.thumbImage)

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
                                    .clickable { viewModel.selectVariant(variant) }
                                    .padding(if (isSelected) 2.dp else 0.dp)
                                    .testTag("tryon_variant_${variant.id}")
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
                }

                // Capture Button: White ring + center dot
                Box(
                    modifier = Modifier
                        .size(74.dp)
                        .clip(CircleShape)
                        .border(4.dp, Color.White, CircleShape)
                        .clickable {
                            // Synthesize captured photo snapshot
                            val syntheticSnapshot = Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888)
                            syntheticSnapshot.eraseColor(android.graphics.Color.DKGRAY)
                            viewModel.setCapturedImage(syntheticSnapshot)
                        }
                        .padding(6.dp)
                        .testTag("tryon_capture_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }
            }
        }

        // Capture Preview Modal (Retake / Save to gallery)
        uiState.capturedImage?.let { capturedBmp ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .testTag("capture_preview_overlay"),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = capturedBmp.asImageBitmap(),
                    contentDescription = "Captured look",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 36.dp, start = 24.dp, end = 24.dp)
                ) {
                    // Retake Button
                    Button(
                        onClick = { viewModel.clearCapturedImage() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.85f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("capture_retake_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(id = R.string.retake),
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Save Button
                    Button(
                        onClick = { viewModel.saveCapturedImage(context, capturedBmp) },
                        colors = ButtonDefaults.buttonColors(containerColor = ZeferPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("capture_save_button")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(id = R.string.save),
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        )
    }
}

@Composable
fun CameraPermissionRationale(
    permissionRequested: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onSyntheticDemo: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ZeferCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.camera_permission_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = ZeferText,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (permissionRequested) {
                        stringResource(id = R.string.camera_permission_denied)
                    } else {
                        stringResource(id = R.string.camera_permission_rationale)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = ZeferTextDim,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (!permissionRequested) {
                    Button(
                        onClick = onRequestPermission,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ZeferPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("grant_camera_permission_button")
                    ) {
                        Text(
                            text = stringResource(id = R.string.camera_permission_grant),
                            fontWeight = FontWeight.Bold,
                            color = ZeferWhite
                        )
                    }
                } else {
                    Button(
                        onClick = onOpenSettings,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ZeferPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("open_camera_settings_button")
                    ) {
                        Text(
                            text = stringResource(id = R.string.open_settings),
                            fontWeight = FontWeight.Bold,
                            color = ZeferWhite
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Emulator / Offline demonstration option
                Button(
                    onClick = onSyntheticDemo,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ZeferSurface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("synthetic_demo_button")
                ) {
                    Text(
                        text = "Try Synthetic Hand Demo",
                        fontWeight = FontWeight.Medium,
                        color = ZeferText
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onNavigateBack,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Go Back", color = ZeferTextDim)
                }
            }
        }
    }
}
