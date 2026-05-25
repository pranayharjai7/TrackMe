package com.trackme.wearable.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.trackme.wearable.designsystem.WearColors
import com.trackme.wearable.haptics.WearHaptics
import com.trackme.wearable.viewmodel.LoggerInputState
import com.trackme.wearable.viewmodel.WearUiState
import com.trackme.wearbridge.LoggingTypePayload

// ── Pure formatting helper (also used by LogConfirmOverlayTest) ────────────

/**
 * Returns the set-summary string for the confirm overlay.
 * Examples:
 *   WEIGHTED_REPS   → "80.0 kg × 12 reps"
 *   BODYWEIGHT_REPS → "12 reps"
 *   TIMED           → "1:30"
 *   CARDIO          → "1:30 · 2.50 km"
 */
fun formatLogSummary(loggingType: LoggingTypePayload?, input: LoggerInputState): String =
    when (loggingType) {
        LoggingTypePayload.WEIGHTED_REPS ->
            String.format(java.util.Locale.US, "%.1f kg × %d reps", input.weightKg, input.reps)
        LoggingTypePayload.BODYWEIGHT_REPS ->
            "${input.reps} reps"
        LoggingTypePayload.TIMED ->
            formatSeconds(input.durationSeconds)
        LoggingTypePayload.CARDIO ->
            String.format(
                java.util.Locale.US,
                "%s · %.2f km",
                formatSeconds(input.durationSeconds),
                input.distanceKm
            )
        null -> ""
    }

// ── LogConfirmOverlay composable ───────────────────────────────────────────

private const val SWIPE_DOWN_THRESHOLD_DP = 80f

@Composable
fun LogConfirmOverlay(
    uiState: WearUiState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current

    // Pulse animation: scale 1.0 → 1.06 → 1.0 looping
    val infiniteTransition = rememberInfiniteTransition(label = "confirmPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )

    // Track cumulative vertical drag for swipe-down detection
    var dragAccumY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WearColors.Black.copy(alpha = 0.85f))
            .pointerInput(onConfirm) {
                detectTapGestures {
                    WearHaptics.setLogged(context)
                    onConfirm()
                }
            }
            .pointerInput(onCancel) {
                detectDragGestures(
                    onDragStart = { dragAccumY = 0f },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragAccumY += dragAmount.y
                        // Convert dp threshold to pixels using density
                        val thresholdPx = SWIPE_DOWN_THRESHOLD_DP * density
                        if (dragAccumY > thresholdPx) {
                            dragAccumY = 0f
                            onCancel()
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        // ── Central content ───────────────────────────────────────────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            // Exercise name
            val exerciseName = uiState.session?.exerciseName
            if (!exerciseName.isNullOrBlank()) {
                Text(
                    text = exerciseName,
                    color = WearColors.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
            }

            // Set summary
            val summaryText = formatLogSummary(
                loggingType = uiState.session?.loggingType,
                input = uiState.loggerInput,
            )
            if (summaryText.isNotBlank()) {
                Text(
                    text = summaryText,
                    color = WearColors.Active,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
            }

            // TAP TO LOG — pulsing hero text
            Text(
                text = "TAP TO LOG",
                color = WearColors.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.scale(pulseScale),
            )
        }

        // ── Swipe-down cancel hint ────────────────────────────────────────
        Text(
            text = "↓ cancel",
            color = WearColors.TextMuted,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp),
        )
    }
}
