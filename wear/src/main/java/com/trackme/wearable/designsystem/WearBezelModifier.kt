package com.trackme.wearable.designsystem

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import com.trackme.wearable.workout.WorkoutInteractionEngine

@Composable
fun Modifier.wearBezelInput(
    engine: WorkoutInteractionEngine,
    enabled: Boolean = true,
    onScroll: (deltaPx: Float) -> Boolean,
): Modifier {
    val focusRequester = remember { FocusRequester() }
    return this
        .focusRequester(focusRequester)
        .focusable(enabled)
        .onRotaryScrollEvent {
            if (!enabled) return@onRotaryScrollEvent false
            onScroll(it.verticalScrollPixels)
        }
}
