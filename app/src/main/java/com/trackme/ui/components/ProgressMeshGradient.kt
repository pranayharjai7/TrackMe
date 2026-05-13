package com.trackme.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.trackme.ui.progress.ProgressState
import com.trackme.ui.theme.*

@Composable
fun ProgressMeshGradient(
    state: ProgressState,
    modifier: Modifier = Modifier
) {
    val tiltState = rememberDeviceTilt()

    // Determine colors based on state
    // Aurora (Momentum), Twilight (Maintenance), Ember (Recovery), Dawn (Uncharted)
    val colors = when (state) {
        ProgressState.MOMENTUM -> listOf(
            Teal.copy(alpha = 0.5f), Color(0xFF00C853).copy(alpha = 0.4f), // Emerald-ish
            Blue.copy(alpha = 0.4f), Background
        )
        ProgressState.MAINTENANCE -> listOf(
            Violet.copy(alpha = 0.5f), Blue.copy(alpha = 0.4f),
            Color(0xFF607D8B).copy(alpha = 0.3f), Background // Slate-ish
        )
        ProgressState.RECOVERY -> listOf(
            Coral.copy(alpha = 0.6f), Color(0xFFFF9800).copy(alpha = 0.4f), // Orange
            Violet.copy(alpha = 0.3f), Background
        )
        ProgressState.UNCHARTED -> listOf(
            Color(0xFFF48FB1).copy(alpha = 0.4f), Color(0xFF81D4FA).copy(alpha = 0.4f), // Pink & Powder Blue
            Color.White.copy(alpha = 0.1f), Background
        )
    }

    val anim1Color by animateColorAsState(colors[0], tween(2500), label = "c1")
    val anim2Color by animateColorAsState(colors[1], tween(2500), label = "c2")
    val anim3Color by animateColorAsState(colors[2], tween(2500), label = "c3")
    val bgColor by animateColorAsState(colors[3], tween(2500), label = "bg")

    val infiniteTransition = rememberInfiniteTransition(label = "mesh")
    val flowOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "flow"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val tilt = tiltState.value
        val width = size.width
        val height = size.height

        // Parallax effect from device tilt
        val tiltOffsetX = (tilt.roll * width / 3f)
        val tiltOffsetY = (tilt.pitch * height / 3f)

        // Orbital paths for the color blobs
        val c1X = width * 0.3f + Math.cos(flowOffset.toDouble()).toFloat() * 250f + tiltOffsetX * 0.6f
        val c1Y = height * 0.3f + Math.sin(flowOffset.toDouble()).toFloat() * 250f + tiltOffsetY * 0.6f

        val c2X = width * 0.7f + Math.sin((flowOffset * 1.2f).toDouble()).toFloat() * 350f - tiltOffsetX * 0.4f
        val c2Y = height * 0.8f + Math.cos((flowOffset * 1.2f).toDouble()).toFloat() * 350f - tiltOffsetY * 0.4f

        val c3X = width * 0.6f + Math.cos((flowOffset * 0.8f + 1f).toDouble()).toFloat() * 300f + tiltOffsetX * 0.9f
        val c3Y = height * 0.4f + Math.sin((flowOffset * 0.8f + 1f).toDouble()).toFloat() * 300f - tiltOffsetY * 0.9f

        drawRect(color = bgColor)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(anim1Color, Color.Transparent),
                center = Offset(c1X, c1Y),
                radius = width * 0.85f
            ),
            center = Offset(c1X, c1Y),
            radius = width * 0.85f
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(anim2Color, Color.Transparent),
                center = Offset(c2X, c2Y),
                radius = width * 0.95f
            ),
            center = Offset(c2X, c2Y),
            radius = width * 0.95f
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(anim3Color, Color.Transparent),
                center = Offset(c3X, c3Y),
                radius = width * 0.75f
            ),
            center = Offset(c3X, c3Y),
            radius = width * 0.75f
        )
    }
}
