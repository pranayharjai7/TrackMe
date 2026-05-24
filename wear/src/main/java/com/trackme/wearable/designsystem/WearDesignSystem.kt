package com.trackme.wearable.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text

object WearColors {
    val Background = Color(0xFF111118)
    val Surface = Color(0xFF1A1A24)
    val SurfaceElevated = Color(0xFF22223A)
    val Accent = Color(0xFF34D399)
    val Violet = Color(0xFFA78BFA)
    val Blue = Color(0xFF60A5FA)
    val Coral = Color(0xFFFB7185)
    val TextPrimary = Color(0xFFEEEEF5)
    val TextSecondary = Color(0xFFAAAABC)
    val TextMuted = Color(0xFF6E6E82)
}

@Composable
fun WearGradientBackground(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(WearColors.SurfaceElevated, WearColors.Background),
                    center = Offset(120f, 80f),
                    radius = 420f,
                ),
            ),
    ) {
        content()
    }
}

@Composable
fun WearGlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(WearColors.Surface.copy(alpha = 0.92f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        content = content,
    )
}

@Composable
fun WearProgressRing(
    progress: Float,
    centerText: String,
    modifier: Modifier = Modifier,
    accent: Color = WearColors.Accent,
) {
    Box(modifier = modifier.size(108.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 10.dp.toPx()
            drawArc(
                color = WearColors.SurfaceElevated,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Text(
            text = centerText,
            color = WearColors.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun WearPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = WearColors.Accent,
    enabled: Boolean = true,
) {
    val scale by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.96f,
        animationSpec = spring(),
        label = "pillScale",
    )
    Text(
        text = text,
        color = if (enabled) WearColors.Background else WearColors.TextMuted,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = modifier
            .scale(scale)
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(if (enabled) accent else WearColors.SurfaceElevated)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 16.dp),
    )
}

@Composable
fun WearIconButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = label,
        color = WearColors.TextPrimary,
        fontSize = 11.sp,
        textAlign = TextAlign.Center,
        modifier = modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(WearColors.SurfaceElevated)
            .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
            .clickable(onClick = onClick)
            .padding(6.dp),
    )
}

@Composable
fun WearStatusChip(offline: Boolean, queuedCount: Int) {
    val label = when {
        offline && queuedCount > 0 -> "Offline · $queuedCount queued"
        offline -> "Offline mode"
        queuedCount > 0 -> "Syncing $queuedCount"
        else -> "Connected"
    }
    val color = when {
        offline -> WearColors.Coral
        queuedCount > 0 -> WearColors.Blue
        else -> WearColors.Accent
    }
    Text(
        text = label,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

@Composable
fun WearHeadline(
    text: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = text,
            color = WearColors.TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                color = WearColors.TextSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
