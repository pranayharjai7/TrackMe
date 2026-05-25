package com.trackme.wearable.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Text

@Composable
fun WearArcProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    strokeWidth: Dp = 6.dp,
    trackColor: Color = WearTokens.Surface,
    progressColor: Color = WearTokens.Active,
    warningBlend: Float = 0f,
    centerContent: @Composable () -> Unit = {},
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(120),
        label = "ringProgress",
    )
    val blendedColor = androidx.compose.ui.graphics.lerp(
        progressColor,
        WearTokens.Warning,
        warningBlend.coerceIn(0f, 1f),
    )
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            drawArc(
                color = trackColor.copy(alpha = 0.35f),
                startAngle = -220f,
                sweepAngle = 260f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = blendedColor,
                startAngle = -220f,
                sweepAngle = 260f * animatedProgress,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        centerContent()
    }
}

@Composable
fun WearReadinessRing(
    progress: Float,
    score: Int,
    modifier: Modifier = Modifier,
) {
    WearArcProgressRing(
        progress = progress,
        modifier = modifier,
        size = 108.dp,
        progressColor = WearTokens.Active,
        centerContent = {
            Text(
                text = score.toString(),
                style = WearTypography.Display.copy(color = WearTokens.Active),
            )
        },
    )
}
