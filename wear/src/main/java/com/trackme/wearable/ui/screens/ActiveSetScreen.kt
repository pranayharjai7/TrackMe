package com.trackme.wearable.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
) {
    val focusRequester = remember { FocusRequester() }
    val haptic = LocalHapticFeedback.current

    // Accumulate fractional rotary scroll so we fire integer steps
    val rotaryAccumulator = remember { floatArrayOf(0f) }

    // Dynamic focus manager based on active page state
    LaunchedEffect(isActive) {
        if (isActive) {
            focusRequester.requestFocus()
        }
    }

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
            }
            .pointerInput(onTap, onToggleField) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { onToggleField() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        // ── Set progress ring (drawn behind everything) ─────────────────────
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (setProgress > 0f) {
                val strokeWidth = 4.dp.toPx()
                val inset = strokeWidth / 2f
                drawArc(
                    color = WearColors.Active.copy(alpha = 0.15f),
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
                .padding(horizontal = 16.dp),
        ) {
            // Exercise name (8sp, muted, uppercase, letter-spaced)
            Text(
                text = session?.exerciseName?.uppercase() ?: "READY",
                color = WearColors.TextMuted,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(3.dp))

            // ── Primary display metric ──────────────────────────────────────
            if (loggingType == LoggingTypePayload.WEIGHTED_REPS) {
                val isWeightActive = activeField == LoggerField.WEIGHT
                
                // Weight row
                val weightAnimState = remember(loggerInput.weightKg) {
                    FieldAnimState(
                        display = String.format(java.util.Locale.US, "%.1f", loggerInput.weightKg),
                        sortKey = loggerInput.weightKg
                    )
                }
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Center
                ) {
                    AnimatedContent(
                        targetState = weightAnimState,
                        transitionSpec = {
                            val goUp = targetState.sortKey > initialState.sortKey
                            val enterSlide = if (goUp) -1 else 1
                            val exitSlide = if (goUp) 1 else -1
                            (slideInVertically(tween(120)) { h -> enterSlide * h } + fadeIn(tween(120))) togetherWith
                            (slideOutVertically(tween(80)) { h -> exitSlide * h } + fadeOut(tween(80)))
                        },
                        label = "weightRoll"
                    ) { state ->
                        Text(
                            text = state.display,
                            color = if (isWeightActive) WearColors.TextPrimary else WearColors.TextSecondary.copy(alpha = 0.6f),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = "kg",
                        color = WearColors.TextMuted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }

                Spacer(Modifier.height(1.dp))

                // Reps row
                val repsAnimState = remember(loggerInput.reps) {
                    FieldAnimState(
                        display = loggerInput.reps.toString(),
                        sortKey = loggerInput.reps.toFloat()
                    )
                }
                AnimatedContent(
                    targetState = repsAnimState,
                    transitionSpec = {
                        val goUp = targetState.sortKey > initialState.sortKey
                        val enterSlide = if (goUp) -1 else 1
                        val exitSlide = if (goUp) 1 else -1
                        (slideInVertically(tween(120)) { h -> enterSlide * h } + fadeIn(tween(120))) togetherWith
                        (slideOutVertically(tween(80)) { h -> exitSlide * h } + fadeOut(tween(80)))
                    },
                    label = "repsRoll"
                ) { state ->
                    Text(
                        text = "× ${state.display}",
                        color = if (!isWeightActive) WearColors.Active else WearColors.TextSecondary.copy(alpha = 0.6f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                // For other logging types: show single active field large
                val activeAnimState = remember(activeField, loggerInput) {
                    FieldAnimState(
                        display = formatFieldValue(activeField, loggerInput),
                        sortKey = activeFieldSortKey(loggerInput)
                    )
                }
                AnimatedContent(
                    targetState = activeAnimState,
                    transitionSpec = {
                        val goUp = targetState.sortKey > initialState.sortKey
                        val enterSlide = if (goUp) -1 else 1
                        val exitSlide = if (goUp) 1 else -1
                        (slideInVertically(tween(120)) { h -> enterSlide * h } + fadeIn(tween(120))) togetherWith
                        (slideOutVertically(tween(80)) { h -> exitSlide * h } + fadeOut(tween(80)))
                    },
                    label = "activeRoll"
                ) { state ->
                    Text(
                        text = state.display,
                        color = WearColors.TextPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(Modifier.height(3.dp))

            // Set indicator (SET N / TOTAL — 9sp, muted, N in green)
            val currentEx = session?.exercises?.getOrNull(session.exerciseIndex)
            if (session != null && currentEx != null && session.setIndex <= currentEx.targetSets) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SET ",
                        color = WearColors.TextMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${session.setIndex}",
                        color = WearColors.Active,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = " / ${currentEx.targetSets}",
                        color = WearColors.TextMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // Ambient separator line
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.08f))
            )
            Spacer(Modifier.height(4.dp))

            // Ambient Row: heart rate (coral) and volume (violet)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val bpm = uiState.health.heartRateBpm?.toInt()
                Text(
                    text = if (bpm != null) "♥ $bpm" else "♥ --",
                    color = if (bpm != null) WearColors.Signal else WearColors.TextMuted,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = String.format(java.util.Locale.US, "vol %.1fk", (session?.totalVolumeKg ?: 0f) / 1000f),
                    color = WearColors.Summary,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            // TAP Hint
            if (session != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "TAP →",
                    color = WearColors.Active,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
