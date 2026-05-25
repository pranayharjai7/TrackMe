package com.trackme.wearable.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Text
import com.trackme.wearable.designsystem.WearTokens
import com.trackme.wearable.designsystem.WearTypography

@Composable
fun MuscleMapPage(
    primaryMuscle: String,
    secondaryMuscles: List<String>,
    scrollOffset: Int,
    modifier: Modifier = Modifier,
) {
    val muscles = buildList {
        if (primaryMuscle.isNotBlank()) add(primaryMuscle to 1f)
        secondaryMuscles.filter { it.isNotBlank() && it != primaryMuscle }
            .forEach { add(it to 0.4f) }
    }
    val visible = muscles.drop(scrollOffset).take(3)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
        ) {
            val w = size.width
            val h = size.height
            drawOval(
                color = WearTokens.Surface,
                topLeft = Offset(w * 0.3f, 0f),
                size = Size(w * 0.4f, h * 0.22f),
            )
            drawRoundRect(
                color = WearTokens.Surface,
                topLeft = Offset(w * 0.32f, h * 0.2f),
                size = Size(w * 0.36f, h * 0.55f),
            )
            visible.forEachIndexed { index, (_, alpha) ->
                val color = WearTokens.Summary.copy(alpha = alpha)
                when (index) {
                    0 -> drawRoundRect(
                        color = color,
                        topLeft = Offset(w * 0.34f, h * 0.28f),
                        size = Size(w * 0.32f, h * 0.18f),
                    )
                    else -> drawRoundRect(
                        color = color,
                        topLeft = Offset(w * 0.28f, h * 0.42f),
                        size = Size(w * 0.44f, h * 0.12f),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        visible.forEach { (name, alpha) ->
            Text(
                text = name,
                style = WearTypography.LabelHint.copy(
                    color = WearTokens.Summary.copy(alpha = if (alpha >= 1f) 1f else 0.55f),
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (muscles.size > 3) {
            Text("bezel = scroll", style = WearTypography.LabelHint)
        }
    }
}
