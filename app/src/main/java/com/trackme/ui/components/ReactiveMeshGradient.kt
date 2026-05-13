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
import com.trackme.ui.home.HomeDashboardState
import com.trackme.ui.theme.*

data class MeshGradientPalette(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val background: Color = Background,
)

@Composable
fun ReactiveMeshGradient(
    state: HomeDashboardState,
    modifier: Modifier = Modifier
) {
    val palette = when (state) {
        HomeDashboardState.REST_RECOVERY -> listOf(
            Teal.copy(alpha = 0.5f), Blue.copy(alpha = 0.6f),
            Violet.copy(alpha = 0.2f), Background
        )
        HomeDashboardState.PRE_WORKOUT -> listOf(
            Violet.copy(alpha = 0.6f), Coral.copy(alpha = 0.5f),
            Blue.copy(alpha = 0.3f), Background
        )
        HomeDashboardState.ACTIVE_SESSION -> listOf(
            Coral.copy(alpha = 0.7f), Color(0xFFFF5722).copy(alpha = 0.5f),
            Violet.copy(alpha = 0.4f), Background
        )
        HomeDashboardState.TRIUMPH -> listOf(
            Teal.copy(alpha = 0.6f), Color(0xFFFFD700).copy(alpha = 0.4f),
            Blue.copy(alpha = 0.4f), Background
        )
    }

    ReactiveMeshGradient(
        palette = MeshGradientPalette(
            primary = palette[0],
            secondary = palette[1],
            tertiary = palette[2],
            background = palette[3],
        ),
        modifier = modifier,
    )
}

@Composable
fun ReactiveMeshGradient(
    palette: MeshGradientPalette,
    modifier: Modifier = Modifier
) {
    val tiltState = rememberDeviceTilt()

    val anim1Color by animateColorAsState(palette.primary, tween(2000), label = "c1")
    val anim2Color by animateColorAsState(palette.secondary, tween(2000), label = "c2")
    val anim3Color by animateColorAsState(palette.tertiary, tween(2000), label = "c3")
    val bgColor by animateColorAsState(palette.background, tween(2000), label = "bg")

    val infiniteTransition = rememberInfiniteTransition(label = "mesh")
    val flowOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "flow"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val tilt = tiltState.value
        val width = size.width
        val height = size.height

        val tiltOffsetX = (tilt.roll * width / 2f)
        val tiltOffsetY = (tilt.pitch * height / 2f)

        val c1X = width * 0.2f + Math.cos(flowOffset.toDouble()).toFloat() * 200f + tiltOffsetX * 0.5f
        val c1Y = height * 0.2f + Math.sin(flowOffset.toDouble()).toFloat() * 200f + tiltOffsetY * 0.5f

        val c2X = width * 0.8f + Math.sin((flowOffset + 1f).toDouble()).toFloat() * 300f - tiltOffsetX * 0.3f
        val c2Y = height * 0.7f + Math.cos((flowOffset + 1f).toDouble()).toFloat() * 300f - tiltOffsetY * 0.3f

        val c3X = width * 0.5f + Math.cos((flowOffset + 2f).toDouble()).toFloat() * 250f + tiltOffsetX * 0.8f
        val c3Y = height * 0.5f + Math.sin((flowOffset + 2f).toDouble()).toFloat() * 250f - tiltOffsetY * 0.8f

        drawRect(color = bgColor)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(anim1Color, Color.Transparent),
                center = Offset(c1X, c1Y),
                radius = width * 0.8f
            ),
            center = Offset(c1X, c1Y),
            radius = width * 0.8f
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(anim2Color, Color.Transparent),
                center = Offset(c2X, c2Y),
                radius = width * 0.9f
            ),
            center = Offset(c2X, c2Y),
            radius = width * 0.9f
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(anim3Color, Color.Transparent),
                center = Offset(c3X, c3Y),
                radius = width * 0.7f
            ),
            center = Offset(c3X, c3Y),
            radius = width * 0.7f
        )
    }
}
