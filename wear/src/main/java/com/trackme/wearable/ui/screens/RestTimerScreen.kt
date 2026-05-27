package com.trackme.wearable.ui.screens

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.trackme.wearable.designsystem.WearColors
import com.trackme.wearable.haptics.WearHaptics
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

private const val ROTARY_THRESHOLD_PX = 16f

// ---------------------------------------------------------------------------
// RestTimerScreen composable
// ---------------------------------------------------------------------------

@Composable
fun RestTimerScreen(
    remaining: Int,
    total: Int,
    nextExerciseName: String?,
    heartRateBpm: Double?,
    onEndRest: () -> Unit,
    onAdjustRestTime: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val rotaryAccumulator = remember { floatArrayOf(0f) }

    val warning = isWarning(remaining)
    val progress = restProgress(remaining, total)

    // Request focus for rotary input
    LaunchedEffect(isActive) {
        if (isActive) {
            focusRequester.requestFocus()
        }
    }

    // Fire rest start haptic once when rest screen is entered
    LaunchedEffect(Unit) {
        WearHaptics.restStart(context)
    }

    // Haptic feedback when timer hits exactly zero
    LaunchedEffect(remaining) {
        if (remaining == 0) {
            WearHaptics.restEnd(context)
        }
    }

    // Fire triple-pulse haptic once when rest timer enters the warning zone
    LaunchedEffect(remaining) {
        if (remaining == 10) {
            WearHaptics.restWarning(context)
        }
    }

    // Arc color: Rest → Warning transition
    val arcColor by animateColorAsState(
        targetValue = if (warning) WearColors.Warning else WearColors.Rest,
        animationSpec = tween(durationMillis = 500),
        label = "arcColor"
    )

    // Pulsing scale for center text when in warning zone — only animate when needed
    val pulseScale = if (warning) {
        val pulse = rememberInfiniteTransition(label = "pulse")
        pulse.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(500, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseScale"
        ).value
    } else {
        1f
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onRotaryScrollEvent { event ->
                rotaryAccumulator[0] += event.verticalScrollPixels
                while (abs(rotaryAccumulator[0]) >= ROTARY_THRESHOLD_PX) {
                    val delta = if (rotaryAccumulator[0] > 0) -1 else 1
                    onAdjustRestTime(delta)
                    WearHaptics.bezelStep(context)
                    rotaryAccumulator[0] += if (rotaryAccumulator[0] > 0) -ROTARY_THRESHOLD_PX else ROTARY_THRESHOLD_PX
                }
                true
            }
            .pointerInput(onEndRest) {
                detectTapGestures(onTap = { onEndRest() })
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
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.scale(pulseScale),
            )

            // Next exercise preview
            nextExerciseName?.let { nextName ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Next: $nextName",
                    color = WearColors.TextMuted,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
            }
            Text(
                text = "TAP = END REST",
                color = WearColors.TextMuted.copy(alpha = 0.5f),
                fontSize = 8.sp,
                letterSpacing = 0.5.sp,
                textAlign = TextAlign.Center,
            )
        }

        // ── Heart-rate chip — bottom-left ─────────────────────────────────────
        val bpm = heartRateBpm?.toInt()
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
    }
}
