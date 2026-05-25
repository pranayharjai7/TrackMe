package com.trackme.wearable.interaction

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

@Composable
fun Modifier.routeWorkoutTap(
    enabled: Boolean = true,
    onTap: () -> Unit,
    onDoubleTap: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
): Modifier = pointerInput(enabled, onTap, onDoubleTap, onLongPress) {
    if (!enabled) return@pointerInput
    detectTapGestures(
        onTap = { onTap() },
        onDoubleTap = { onDoubleTap?.invoke() },
        onLongPress = { onLongPress?.invoke() },
    )
}

@Composable
fun Modifier.routeSwipeDownCancel(
    enabled: Boolean = true,
    thresholdPx: Float = 48f,
    onCancel: () -> Unit,
): Modifier = pointerInput(enabled, onCancel) {
    if (!enabled) return@pointerInput
    var total = 0f
    detectVerticalDragGestures(
        onDragEnd = {
            if (total > thresholdPx) onCancel()
            total = 0f
        },
        onVerticalDrag = { _, dragAmount ->
            if (dragAmount > 0f) total += dragAmount
        },
    )
}
