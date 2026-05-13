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
import com.trackme.ui.theme.*

@Composable
fun PlanningMeshGradient(modifier: Modifier = Modifier) {
    val tilt by rememberDeviceTilt()
    
    val color1 = Violet.copy(alpha = 0.35f)
    val color2 = Color(0xFF312E81).copy(alpha = 0.6f) // Deep Indigo
    val color3 = Color(0xFF1E1E2F).copy(alpha = 0.8f) // Charcoal
    val bgColor = Background

    val infiniteTransition = rememberInfiniteTransition(label = "planningMesh")
    val flowOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(40000, easing = LinearEasing), // Slower, deeper movement
            repeatMode = RepeatMode.Restart
        ),
        label = "planningFlow"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // Calculate parallax based on tilt
        val tiltOffsetX = (tilt.roll * width / 4f) // Subtler than Home menu
        val tiltOffsetY = (tilt.pitch * height / 4f)

        val c1X = width * 0.3f + Math.cos(flowOffset.toDouble()).toFloat() * 100f + tiltOffsetX * 0.4f
        val c1Y = height * 0.3f + Math.sin(flowOffset.toDouble()).toFloat() * 100f + tiltOffsetY * 0.4f

        val c2X = width * 0.7f + Math.sin((flowOffset + 1f).toDouble()).toFloat() * 150f - tiltOffsetX * 0.3f
        val c2Y = height * 0.8f + Math.cos((flowOffset + 1f).toDouble()).toFloat() * 150f - tiltOffsetY * 0.3f

        val c3X = width * 0.5f + Math.cos((flowOffset + 2f).toDouble()).toFloat() * 200f + tiltOffsetX * 0.6f
        val c3Y = height * 0.5f + Math.sin((flowOffset + 2f).toDouble()).toFloat() * 200f - tiltOffsetY * 0.6f

        drawRect(color = bgColor)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color1, Color.Transparent),
                center = Offset(c1X, c1Y),
                radius = width * 0.8f
            ),
            center = Offset(c1X, c1Y),
            radius = width * 0.8f
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color2, Color.Transparent),
                center = Offset(c2X, c2Y),
                radius = width * 1.0f
            ),
            center = Offset(c2X, c2Y),
            radius = width * 1.0f
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color3, Color.Transparent),
                center = Offset(c3X, c3Y),
                radius = width * 0.9f
            ),
            center = Offset(c3X, c3Y),
            radius = width * 0.9f
        )
    }
}
