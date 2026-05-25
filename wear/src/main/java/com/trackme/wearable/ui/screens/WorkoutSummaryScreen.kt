package com.trackme.wearable.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material3.Text
import com.trackme.wearable.designsystem.WearColors
import com.trackme.wearable.designsystem.WearGlassCard
import com.trackme.wearable.designsystem.WearPillButton
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
    val session = uiState.session
    val health = uiState.health

    // Fire haptic once when the summary is shown
    LaunchedEffect(Unit) {
        WearHaptics.workoutComplete(context)
    }

    ScalingLazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── Headline ──────────────────────────────────────────────────────────
        item(key = "headline") {
            Text(
                text = "✓ WORKOUT COMPLETE",
                color = WearColors.Warning,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
            )
        }

        // ── Stats card ────────────────────────────────────────────────────────
        item(key = "stats_card") {
            WearGlassCard(modifier = Modifier.fillMaxWidth()) {
                StatRow(
                    label = "Duration",
                    value = formatDurationLabel(health.durationSeconds),
                )
                Spacer(Modifier.height(6.dp))
                StatRow(
                    label = "Volume",
                    value = "${session?.totalVolumeKg?.toInt() ?: 0} kg",
                )
                Spacer(Modifier.height(6.dp))
                StatRow(
                    label = "Sets",
                    value = "${session?.completedSets ?: 0} sets",
                )
                Spacer(Modifier.height(6.dp))
                StatRow(
                    label = "Calories",
                    value = formatCalories(health.activeCalories),
                )
            }
        }

        item(key = "gap") { Spacer(Modifier.height(8.dp)) }

        // ── Dismiss button ────────────────────────────────────────────────────
        item(key = "dismiss") {
            WearPillButton(
                text = "Dismiss",
                onClick = onDismiss,
                accent = WearColors.Warning,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            )
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = WearColors.TextSecondary,
            fontSize = 12.sp,
        )
        Text(
            text = value,
            color = WearColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
