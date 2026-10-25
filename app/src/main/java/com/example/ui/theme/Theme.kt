package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Global Dimensions and Radii Tokens
data class ZeferDimensions(
    val radiusSm: Dp = 12.dp,
    val radiusLg: Dp = 24.dp,
    val paddingXs: Dp = 4.dp,
    val paddingSm: Dp = 8.dp,
    val paddingMd: Dp = 16.dp,
    val paddingLg: Dp = 24.dp,
    val paddingXl: Dp = 32.dp,
    val touchTargetMin: Dp = 48.dp,
    val buttonHeight: Dp = 52.dp,
    val thumbSize: Dp = 64.dp
)

val LocalZeferDimensions = staticCompositionLocalOf { ZeferDimensions() }

private val ZeferLightColorScheme = lightColorScheme(
    primary = ZeferPrimary,
    onPrimary = ZeferWhite,
    primaryContainer = ZeferSurface,
    onPrimaryContainer = ZeferText,
    secondary = ZeferPrimary,
    onSecondary = ZeferWhite,
    background = ZeferBg,
    onBackground = ZeferText,
    surface = ZeferSurface,
    onSurface = ZeferText,
    surfaceVariant = ZeferCard,
    onSurfaceVariant = ZeferText,
    outline = ZeferTextDim,
    error = ZeferError,
    onError = ZeferWhite
)

val ZeferShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(24.dp)
)

@Composable
fun ZeferTheme(
    content: @Composable () -> Unit
) {
    // Zefer strictly enforces Light Theme with its custom design system tokens
    CompositionLocalProvider(
        LocalZeferDimensions provides ZeferDimensions()
    ) {
        MaterialTheme(
            colorScheme = ZeferLightColorScheme,
            typography = ZeferTypography,
            shapes = ZeferShapes,
            content = content
        )
    }
}
