package com.example.ui.screens.tryon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cv.pipeline.DebugMetrics
import com.example.cv.pipeline.TunableParams

@Composable
fun DebugHudOverlay(
    metrics: DebugMetrics,
    params: TunableParams,
    onParamsChange: (TunableParams) -> Unit,
    onToggleReplay: (Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth(0.92f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.85f))
            .padding(14.dp)
            .verticalScroll(rememberScrollState())
            .testTag("debug_hud_overlay")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "ZEFER AR DEBUG HUD",
                color = Color(0xFF00E5FF),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
            )
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Hide", color = Color.White, fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Metrics Grid
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            HudMetric("FPS", "%.1f".format(metrics.fps))
            HudMetric("Inference", "${metrics.inferenceTimeMs}ms")
            HudMetric("Hand", metrics.handedness)
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            HudMetric("Confidence", "%.0f%%".format(metrics.confidenceScore * 100))
            HudMetric("Palm State", if (metrics.isPalmFacingCamera) "Palm (Hidden)" else "Back (Visible)")
            HudMetric("Lost Count", "${metrics.trackingLostCount}")
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Replay Mode Toggle
        Button(
            onClick = { onToggleReplay(!metrics.isReplayMode) },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (metrics.isReplayMode) Color(0xFF00C853) else Color(0xFF424242)
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .testTag("debug_replay_toggle_button")
        ) {
            Text(
                text = if (metrics.isReplayMode) "Active: Synthetic Hand Replay" else "Enable Synthetic Hand Demo",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "LIVE TUNABLE PARAMETERS",
            color = Color(0xFFFFD600),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Anchor fraction slider
        Text(
            text = "Anchor DIP->TIP Fraction: %.2f".format(params.dipTipAnchorFraction),
            color = Color.White,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Slider(
            value = params.dipTipAnchorFraction,
            onValueChange = { onParamsChange(params.copy(dipTipAnchorFraction = it)) },
            valueRange = 0.05f..0.50f,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF00E5FF),
                activeTrackColor = Color(0xFF00E5FF)
            )
        )

        // Length extension slider
        Text(
            text = "Extension Length Ratio: %.2f".format(params.lengthExtensionRatio),
            color = Color.White,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Slider(
            value = params.lengthExtensionRatio,
            onValueChange = { onParamsChange(params.copy(lengthExtensionRatio = it)) },
            valueRange = 0.8f..2.0f,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFFFD600),
                activeTrackColor = Color(0xFFFFD600)
            )
        )

        // One Euro Filter Min Cutoff
        Text(
            text = "1€ Filter Min Cutoff: %.2f".format(params.oneEuroMinCutoff),
            color = Color.White,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Slider(
            value = params.oneEuroMinCutoff,
            onValueChange = { onParamsChange(params.copy(oneEuroMinCutoff = it)) },
            valueRange = 0.2f..3.0f,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White
            )
        )
    }
}

@Composable
private fun HudMetric(label: String, value: String) {
    Column {
        Text(
            text = label,
            color = Color.Gray,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
