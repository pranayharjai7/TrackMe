package com.trackme.wearable.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.trackme.wearable.designsystem.WearColors
import com.trackme.wearable.haptics.WearHaptics
import com.trackme.wearable.viewmodel.WearUiState

// ---------------------------------------------------------------------------
// Pure formatting helpers (tested in WorkoutSummaryScreenTest)
// ---------------------------------------------------------------------------

/**
 * Returns "${kcal.toInt()} kcal" when non-null, or "? kcal" when null.
 */
internal fun formatCalories(kcal: Double?): String =
    if (kcal != null) "${kcal.toInt()} kcal" else "? kcal"

/**
 * Returns "H:MM:SS" when hours > 0, otherwise "M:SS".
 * Adds hours support required for workout-length durations.
 */
internal fun formatDurationLabel(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0L)
    val hours = safe / 3600L
    val minutes = (safe % 3600L) / 60L
    val secs = safe % 60L
    return if (hours > 0L) {
        "$hours:${minutes.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"
    } else {
        "$minutes:${secs.toString().padStart(2, '0')}"
    }
}

// ---------------------------------------------------------------------------
// WorkoutSummaryScreen composable
// ---------------------------------------------------------------------------

@Composable
fun WorkoutSummaryScreen(
    uiState: WearUiState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()
    val session = uiState.session
    val health = uiState.health

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        WearHaptics.workoutComplete(context)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(WearColors.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onRotaryScrollEvent { _ ->
                true
            }
            .verticalScroll(scrollState)
            .pointerInput(onDismiss) { detectTapGestures(onTap = { onDismiss() }) }
            .padding(horizontal = 12.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "✓ COMPLETE",
            color = WearColors.Warning,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            text = formatDurationLabel(health.durationSeconds),
            color = WearColors.TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
        )
        if (session != null) {
            val exerciseCount = session.exercises.size
            if (exerciseCount > 0) {
                Text(
                    text = "$exerciseCount exercises",
                    color = WearColors.TextMuted,
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                text = "${session.totalVolumeKg.toInt()} kg",
                color = WearColors.Warning,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "${session.completedSets} sets",
                color = WearColors.TextSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "tap to finish",
            color = WearColors.TextMuted.copy(alpha = 0.5f),
            fontSize = 7.sp,
            textAlign = TextAlign.Center,
        )
    }
}
