package com.trackme.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

@Composable
fun GlassmorphicCard(
    modifier: Modifier = Modifier,
    containerColor: Color = Color.White.copy(alpha = 0.05f),
    shape: Shape = RoundedCornerShape(24.dp),
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(containerColor)
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)), shape),
        content = content
    )
}
