package com.trackme.wearable.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
// Stable animation state
// ---------------------------------------------------------------------------

@Immutable
internal data class FieldAnimState(val display: String, val sortKey: Float)

// ---------------------------------------------------------------------------
// Pure formatting helpers (also used by ActiveSetScreenTest)
// ---------------------------------------------------------------------------

// formatSeconds is defined in WearFormatUtils.kt (same package)

/**
 * Returns a numeric sort key for the currently active field in [input].
 * Used to determine the direction of the number-roll animation.
 */
internal fun activeFieldSortKey(input: LoggerInputState): Float = when (input.activeField) {
    LoggerField.WEIGHT   -> input.weightKg
    LoggerField.REPS     -> input.reps.toFloat()
    LoggerField.DURATION -> input.durationSeconds.toFloat()
    LoggerField.DISTANCE -> input.distanceKm
    LoggerField.SPEED    -> input.speedKmh
    LoggerField.INCLINE  -> input.inclinePercent
}

/**
 * Returns a human-readable string for the current value of [field] in [input].
 */
fun formatFieldValue(field: LoggerField, input: LoggerInputState): String = when (field) {
    LoggerField.WEIGHT   -> String.format(java.util.Locale.US, "%.1f kg", input.weightKg)
    LoggerField.REPS     -> input.reps.toString()
    LoggerField.DURATION -> formatSeconds(input.durationSeconds)
    LoggerField.DISTANCE -> String.format(java.util.Locale.US, "%.2f km", input.distanceKm)
    LoggerField.SPEED    -> String.format(java.util.Locale.US, "%.1f km/h", input.speedKmh)
    LoggerField.INCLINE  -> String.format(java.util.Locale.US, "%.1f%%", input.inclinePercent)
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
    val rotaryAccumulator = remember { floatArrayOf(0f) }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val session      = uiState.session
    val loggerInput  = uiState.loggerInput
    val activeField  = loggerInput.activeField
    val loggingType  = session?.loggingType

    // Set progress for the ring arc
    val setProgress = remember(uiState.session?.exerciseIndex, uiState.session?.exercises) {
        val s = uiState.session ?: return@remember 0f
        val ex = s.exercises.getOrNull(s.exerciseIndex) ?: return@remember 0f
        if (ex.targetSets > 0) ex.completedSets.toFloat() / ex.targetSets.toFloat() else 0f
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onRotaryScrollEvent { event ->
                rotaryAccumulator[0] += event.verticalScrollPixels
                // Use a threshold to debounce tiny nudges
                val threshold = 16f
                while (abs(rotaryAccumulator[0]) >= threshold) {
                    val delta = if (rotaryAccumulator[0] > 0) -1 else 1
                    onAdjustField(delta)
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    rotaryAccumulator[0] += if (rotaryAccumulator[0] > 0) -threshold else threshold
                }
                true
            },
        contentAlignment = Alignment.Center,
    ) {
        // ── Set progress ring (drawn behind everything) ─────────────────────
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (setProgress > 0f) {
                val strokeWidth = 4.dp.toPx()
                val inset = strokeWidth / 2f
                drawArc(
                    color = WearColors.Active.copy(alpha = 0.25f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - strokeWidth, size.height - strokeWidth),
                )
                drawArc(
                    color = WearColors.Active,
                    startAngle = -90f,
                    sweepAngle = 360f * setProgress.coerceIn(0f, 1f),
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - strokeWidth, size.height - strokeWidth),
                )
            }
        }

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
            val heroAnimState = remember(activeField, loggerInput) {
                FieldAnimState(
                    display = formatFieldValue(activeField, loggerInput),
                    sortKey = activeFieldSortKey(loggerInput),
                )
            }
            AnimatedContent(
                targetState = heroAnimState,
                transitionSpec = {
                    val goUp = targetState.sortKey > initialState.sortKey
                    val enterSlide = if (goUp) -1 else 1
                    val exitSlide = if (goUp) 1 else -1
                    (slideInVertically(tween(120)) { height -> enterSlide * height } +
                        fadeIn(tween(120))) togetherWith
                    (slideOutVertically(tween(80)) { height -> exitSlide * height } +
                        fadeOut(tween(80)))
                },
                label = "valueRoll",
                modifier = Modifier
                    .pointerInput(onTap, onToggleField) {
                        detectTapGestures(
                            onTap = { onTap() },
                            onDoubleTap = { onToggleField() },
                        )
                    },
            ) { state ->
                Text(
                    text = state.display,
                    color = WearColors.TextPrimary,
                    fontSize = 54.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

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
        Text(
            text = if (bpm != null) "♥ $bpm bpm" else "♥ --",
            color = if (bpm != null) WearColors.Signal else WearColors.TextMuted,
            fontSize = 10.sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 10.dp, bottom = 8.dp),
        )
    }
}
