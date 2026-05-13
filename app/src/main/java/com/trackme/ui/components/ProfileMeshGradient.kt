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
import com.trackme.ui.profile.ProfileDashboardState
import com.trackme.ui.theme.*

@Composable
fun ProfileMeshGradient(
    state: ProfileDashboardState,
    modifier: Modifier = Modifier
) {
    val tiltState by rememberDeviceTilt()
    val tilt = tiltState

    // Determine colors based on state
    // Titan (Build Muscle), Breeze (Lose Weight), Velocity (Endurance), Cosmos (Default)
    val colors = when (state) {
        ProfileDashboardState.TITAN -> listOf(
            Color(0xFF311B92).copy(alpha = 0.5f), // Deep Indigo
            Color(0xFFB71C1C).copy(alpha = 0.4f), // Burnt Crimson
            Color(0xFF212121).copy(alpha = 0.4f), // Charcoal
            Background
        )
        ProfileDashboardState.BREEZE -> listOf(
            Color(0xFF00BCD4).copy(alpha = 0.5f), // Cyan
            Color(0xFFE1BEE7).copy(alpha = 0.4f), // Soft Lavender
            Color(0xFFB2DFDB).copy(alpha = 0.3f), // Mint
            Background
        )
        ProfileDashboardState.VELOCITY -> listOf(
            Color(0xFF64DD17).copy(alpha = 0.5f), // Neon Green
            Color(0xFFFFEA00).copy(alpha = 0.4f), // Electric Yellow
            Color(0xFF455A64).copy(alpha = 0.3f), // Slate
            Background
        )
        ProfileDashboardState.COSMOS -> listOf(
            Color(0xFF1A237E).copy(alpha = 0.5f), // Midnight Blue
            Color(0xFF4A148C).copy(alpha = 0.4f), // Deep Purple
            Color(0xFFEEEEEE).copy(alpha = 0.1f), // Silver
            Background
        )
    }

    val anim1Color by animateColorAsState(colors[0], tween(2500), label = "c1")
    val anim2Color by animateColorAsState(colors[1], tween(2500), label = "c2")
    val anim3Color by animateColorAsState(colors[2], tween(2500), label = "c3")
    val bgColor by animateColorAsState(colors[3], tween(2500), label = "bg")

    val infiniteTransition = rememberInfiniteTransition(label = "profile_mesh")
    val flowOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(25000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "flow"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // Parallax effect from device tilt
        val tiltOffsetX = (tilt.roll * width / 4f)
        val tiltOffsetY = (tilt.pitch * height / 4f)

        // Orbital paths for the color blobs
        val c1X = width * 0.2f + Math.cos(flowOffset.toDouble()).toFloat() * 200f + tiltOffsetX * 0.5f
        val c1Y = height * 0.2f + Math.sin(flowOffset.toDouble()).toFloat() * 200f + tiltOffsetY * 0.5f

        val c2X = width * 0.8f + Math.sin((flowOffset * 0.8f).toDouble()).toFloat() * 300f - tiltOffsetX * 0.3f
        val c2Y = height * 0.7f + Math.cos((flowOffset * 0.8f).toDouble()).toFloat() * 300f - tiltOffsetY * 0.3f

        val c3X = width * 0.5f + Math.cos((flowOffset * 1.5f + 2f).toDouble()).toFloat() * 250f + tiltOffsetX * 0.7f
        val c3Y = height * 0.4f + Math.sin((flowOffset * 1.5f + 2f).toDouble()).toFloat() * 250f - tiltOffsetY * 0.7f

        drawRect(color = bgColor)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(anim1Color, Color.Transparent),
                center = Offset(c1X, c1Y),
                radius = width * 0.9f
            ),
            center = Offset(c1X, c1Y),
            radius = width * 0.9f
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(anim2Color, Color.Transparent),
                center = Offset(c2X, c2Y),
                radius = width * 1.1f
            ),
            center = Offset(c2X, c2Y),
            radius = width * 1.1f
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(anim3Color, Color.Transparent),
                center = Offset(c3X, c3Y),
                radius = width * 0.8f
            ),
            center = Offset(c3X, c3Y),
            radius = width * 0.8f
        )
    }
}
