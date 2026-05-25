package com.trackme.wearable.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.trackme.wearable.designsystem.WearColors
import com.trackme.wearable.designsystem.WearPillButton
import com.trackme.wearable.haptics.WearHaptics
import com.trackme.wearable.viewmodel.WearUiState
import kotlin.math.abs

// ---------------------------------------------------------------------------
// Pure helpers (tested in RestTimerScreenTest)
// ---------------------------------------------------------------------------

/**
 * Returns the progress fraction (0f..1f) of remaining rest time over the total.
 * Handles division-by-zero: when [total] == 0, returns 1f.
 */
internal fun restProgress(remaining: Int, total: Int): Float {
    if (total == 0) return 1f
    return (remaining.toFloat() / total.toFloat()).coerceIn(0f, 1f)
}

/**
 * Returns true when the timer is in the warning zone (1..10 seconds remaining).
 */
internal fun isWarning(seconds: Int): Boolean = seconds in 1..10

// ---------------------------------------------------------------------------
// RestTimerScreen composable
// ---------------------------------------------------------------------------

@Composable
fun RestTimerScreen(
    uiState: WearUiState,
    onEndRest: () -> Unit,
    onAdjustRestTime: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val rotaryAccumulator = remember { floatArrayOf(0f) }

    val remaining = uiState.restSecondsRemaining
    val total = uiState.restTotalSeconds
    val warning = isWarning(remaining)
    val progress = restProgress(remaining, total)

    // Request focus for rotary input
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // Haptic feedback when timer hits exactly zero
    LaunchedEffect(remaining) {
        if (remaining == 0) {
            WearHaptics.restEnd(context)
        }
    }

    // Arc color: Rest → Warning transition
    val arcColor by animateColorAsState(
        targetValue = if (warning) WearColors.Warning else WearColors.Rest,
        animationSpec = tween(durationMillis = 500),
        label = "arcColor"
    )

    // Pulsing scale for center text when in warning zone
    val pulseTransition: InfiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val centerScale = if (warning) pulseScale else 1f

    Box(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onRotaryScrollEvent { event ->
                rotaryAccumulator[0] += event.verticalScrollPixels
                val threshold = 16f
                while (abs(rotaryAccumulator[0]) >= threshold) {
                    val delta = if (rotaryAccumulator[0] > 0) -1 else 1
                    onAdjustRestTime(delta)
                    WearHaptics.bezelStep(context)
                    rotaryAccumulator[0] += if (rotaryAccumulator[0] > 0) -threshold else threshold
                }
                true
            },
        contentAlignment = Alignment.Center
    ) {
        // ── Full-screen depleting arc ─────────────────────────────────────────
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 12.dp.toPx()
            val inset = strokeWidth / 2f
            val arcLeft = inset
            val arcTop = inset
            val arcRight = size.width - inset
            val arcBottom = size.height - inset

            // Track (background full circle)
            drawArc(
                color = WearColors.Elevated,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                topLeft = androidx.compose.ui.geometry.Offset(arcLeft, arcTop),
                size = androidx.compose.ui.geometry.Size(
                    width = arcRight - arcLeft,
                    height = arcBottom - arcTop
                )
            )

            // Progress arc (depletes as time passes)
            if (progress > 0f) {
                drawArc(
                    color = arcColor,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    topLeft = androidx.compose.ui.geometry.Offset(arcLeft, arcTop),
                    size = androidx.compose.ui.geometry.Size(
                        width = arcRight - arcLeft,
                        height = arcBottom - arcTop
                    )
                )
            }
        }

        // ── Center content ────────────────────────────────────────────────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            // "REST" label
            Text(
                text = "REST",
                color = arcColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(2.dp))

            // Large countdown
            Text(
                text = formatSeconds(remaining),
                color = WearColors.TextPrimary,
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.scale(centerScale),
            )

            // Next exercise preview
            val nextName = uiState.session?.nextExerciseName
            if (nextName != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Next: $nextName",
                    color = WearColors.TextMuted,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // ── Heart-rate chip — bottom-left ─────────────────────────────────────
        val bpm = uiState.health.heartRateBpm?.toInt()
        if (bpm != null) {
            Text(
                text = "♥ $bpm",
                color = WearColors.Signal,
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 10.dp, bottom = 44.dp),
            )
        }

        // ── Skip Rest pill ────────────────────────────────────────────────────
        WearPillButton(
            text = "Skip Rest",
            onClick = onEndRest,
            accent = WearColors.Warning,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp, vertical = 8.dp),
        )
    }
}
