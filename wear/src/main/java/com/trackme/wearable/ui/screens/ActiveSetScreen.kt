package com.trackme.wearable.ui.screens

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.trackme.wearbridge.LoggingTypePayload
import com.trackme.wearable.designsystem.WearColors
import com.trackme.wearable.viewmodel.LoggerField
import com.trackme.wearable.viewmodel.LoggerInputState
import com.trackme.wearable.viewmodel.WearUiState
import kotlin.math.abs

// ---------------------------------------------------------------------------
// Pure formatting helpers (also used by ActiveSetScreenTest)
// ---------------------------------------------------------------------------

/**
 * Formats a duration given in seconds as "m:ss".
 * e.g. 90 → "1:30", 5 → "0:05"
 */
fun formatSeconds(seconds: Int): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return "$minutes:${secs.toString().padStart(2, '0')}"
}

/**
 * Returns a human-readable string for the current value of [field] in [input].
 */
fun formatFieldValue(field: LoggerField, input: LoggerInputState): String = when (field) {
    LoggerField.WEIGHT   -> "${input.weightKg} kg"
    LoggerField.REPS     -> "${input.reps}"
    LoggerField.DURATION -> formatSeconds(input.durationSeconds)
    LoggerField.DISTANCE -> "${input.distanceKm} km"
    LoggerField.SPEED    -> "${input.speedKmh} km/h"
    LoggerField.INCLINE  -> "${input.inclinePercent}%"
}

/** Short label shown above the big value. */
private fun fieldLabel(field: LoggerField): String = when (field) {
    LoggerField.WEIGHT   -> "WEIGHT"
    LoggerField.REPS     -> "REPS"
    LoggerField.DURATION -> "DURATION"
    LoggerField.DISTANCE -> "DISTANCE"
    LoggerField.SPEED    -> "SPEED"
    LoggerField.INCLINE  -> "INCLINE"
}

// ---------------------------------------------------------------------------
// ActiveSetScreen composable
// ---------------------------------------------------------------------------

@Composable
fun ActiveSetScreen(
    uiState: WearUiState,
    onTap: () -> Unit,
    onAdjustField: (Int) -> Unit,
    onToggleField: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val haptic = LocalHapticFeedback.current

    // Accumulate fractional rotary scroll so we fire integer steps
    var rotaryAccumulator = remember { 0f }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val session      = uiState.session
    val loggerInput  = uiState.loggerInput
    val activeField  = loggerInput.activeField
    val loggingType  = session?.loggingType

    Box(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .onRotaryScrollEvent { event ->
                rotaryAccumulator += event.verticalScrollPixels
                // Use a threshold to debounce tiny nudges
                val threshold = 16f
                while (abs(rotaryAccumulator) >= threshold) {
                    val delta = if (rotaryAccumulator > 0) -1 else 1
                    onAdjustField(delta)
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    rotaryAccumulator += if (rotaryAccumulator > 0) -threshold else threshold
                }
                true
            },
        contentAlignment = Alignment.Center,
    ) {
        // ── Central content column ──────────────────────────────────────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
        ) {
            // Exercise name
            Text(
                text = session?.exerciseName ?: "Ready",
                color = WearColors.TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(2.dp))

            // Set indicator
            val setLabel = session?.let { "Set ${it.setIndex}" } ?: ""
            if (setLabel.isNotEmpty()) {
                Text(
                    text = setLabel,
                    color = WearColors.TextMuted,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
            }

            // Active field label
            Text(
                text = fieldLabel(activeField),
                color = WearColors.Active,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(2.dp))

            // ── Hero value — double-tap toggles field, single tap confirms ──
            Text(
                text = formatFieldValue(activeField, loggerInput),
                color = WearColors.TextPrimary,
                fontSize = 54.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onTap() },
                            onDoubleTap = { onToggleField() },
                        )
                    },
            )

            // ── Secondary field row (WEIGHTED_REPS only) ───────────────────
            if (loggingType == LoggingTypePayload.WEIGHTED_REPS) {
                Spacer(Modifier.height(6.dp))
                val secondaryField = if (activeField == LoggerField.WEIGHT)
                    LoggerField.REPS
                else
                    LoggerField.WEIGHT
                val secondaryLabel = fieldLabel(secondaryField).lowercase().replaceFirstChar { it.uppercaseChar() }
                val secondaryValue = formatFieldValue(secondaryField, loggerInput)
                Text(
                    text = "$secondaryLabel  $secondaryValue",
                    color = WearColors.TextMuted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // ── Heart-rate chip — bottom-left ───────────────────────────────────
        val bpm = uiState.health.heartRateBpm?.toInt()
        if (bpm != null) {
            Text(
                text = "♥ $bpm",
                color = WearColors.Signal,
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 10.dp, bottom = 8.dp),
            )
        }
    }
}
