package com.example.ui.screens.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.local.UserPreferences
import com.example.ui.theme.BrushScriptFontFamily
import com.example.ui.theme.ZeferBg
import com.example.ui.theme.ZeferPrimary
import com.example.ui.theme.ZeferSurface
import com.example.ui.theme.ZeferTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SplashViewModel(
    private val userPreferences: UserPreferences
) : ViewModel() {

    fun checkSession(onResult: (isLoggedIn: Boolean) -> Unit) {
        viewModelScope.launch {
            val loggedIn = userPreferences.isLoggedIn.first()
            onResult(loggedIn)
        }
    }
}

@Composable
fun SplashScreen(
    viewModel: SplashViewModel,
    onNavigateToHome: () -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Smooth horizontal progress bar animation while session loads
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1400, easing = LinearEasing)
        )
        viewModel.checkSession { isLoggedIn ->
            if (isLoggedIn) {
                onNavigateToHome()
            } else {
                onNavigateToLogin()
            }
        }
    }

    SplashScreenContent(
        progress = progress.value,
        modifier = modifier
    )
}

@Composable
fun SplashScreenContent(
    progress: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ZeferBg)
            .testTag("splash_screen_root"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(top = 40.dp) // centered slightly below vertical center
        ) {
            // Provided logo PNG used as-is directly via painterResource
            Image(
                painter = painterResource(id = R.drawable.zefer_logo),
                contentDescription = stringResource(id = R.string.app_name),
                modifier = Modifier
                    .size(90.dp)
                    .testTag("splash_logo_image")
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Wordmark in matching brush-script typeface
            Text(
                text = stringResource(id = R.string.wordmark),
                fontFamily = BrushScriptFontFamily,
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.testTag("splash_wordmark_text")
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Thin horizontal progress bar beneath: black (#1A1A1A) fill on #D9D9D9 track
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.48f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(ZeferSurface)
                    .testTag("splash_progress_track")
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(3.dp)
                        .background(ZeferPrimary)
                        .testTag("splash_progress_bar")
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SplashScreenPreview() {
    ZeferTheme {
        SplashScreenContent(progress = 0.65f)
    }
}
