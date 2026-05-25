package com.trackme.wearable.designsystem

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.wear.compose.material3.Text

@Composable
fun WearRollingMetric(
    value: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    increaseHint: Boolean? = null,
) {
    AnimatedContent(
        targetState = value,
        modifier = modifier,
        transitionSpec = {
            val direction = if (increaseHint == true) -1 else if (increaseHint == false) 1 else 0
            (slideInVertically(tween(120)) { it * direction / 3 } + fadeIn(tween(120))) togetherWith
                (slideOutVertically(tween(120)) { -it * direction / 3 } + fadeOut(tween(100)))
        },
        label = "rollingMetric",
    ) { text ->
        Text(text = text, style = style)
    }
}
