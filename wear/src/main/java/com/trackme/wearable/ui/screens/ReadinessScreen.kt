package com.trackme.wearable.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.trackme.wearable.designsystem.WearColors
import com.trackme.wearable.haptics.WearHaptics
import com.trackme.wearable.viewmodel.WearUiState

@Composable
fun ReadinessScreen(
    uiState: WearUiState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val score = uiState.dayState?.readinessScore ?: 0
    val ringProgress = score / 100f

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WearColors.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onRotaryScrollEvent {
                WearHaptics.bezelStep(context)
                true
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "READINESS",
                color = WearColors.TextMuted,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
            )
            Box(contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(120.dp)) {
                    val stroke = 6.dp.toPx()
                    drawArc(
                        color = WearColors.Elevated,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    if (ringProgress > 0f) {
                        drawArc(
                            color = WearColors.Active,
                            startAngle = -90f,
                            sweepAngle = 360f * ringProgress,
                            useCenter = false,
                            style = Stroke(width = stroke, cap = StrokeCap.Round),
                        )
                    }
                }
                Text(
                    text = if (score > 0) score.toString() else "—",
                    color = WearColors.Active,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
            if (score > 0) {
                Text(
                    text = readinessLabel(score),
                    color = WearColors.TextSecondary,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                )
            }
            val hr = uiState.health.heartRateBpm?.toInt()
            if (hr != null) {
                Text(
                    text = "♥ $hr bpm",
                    color = WearColors.Signal,
                    fontSize = 8.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
