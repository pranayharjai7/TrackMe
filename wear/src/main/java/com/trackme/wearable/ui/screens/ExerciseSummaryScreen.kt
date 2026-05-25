package com.trackme.wearable.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.trackme.wearable.designsystem.WearColors
import com.trackme.wearable.haptics.WearHaptics
import com.trackme.wearable.ui.components.WearPrBadge
import com.trackme.wearable.viewmodel.WearUiState
import kotlinx.coroutines.delay

// ---------------------------------------------------------------------------
// Pure formatting helpers (tested in ExerciseSummaryScreenTest)
// ---------------------------------------------------------------------------

/**
 * Returns "${kg.toInt()} kg total" when [kg] > 0f, or null when <= 0f.
 */
internal fun formatVolume(kg: Float): String? =
    if (kg > 0f) "${kg.toInt()} kg total" else null

/**
 * Returns "+${deltaKg.toInt()} kg PR" for the PR badge label.
 */
internal fun formatPrBadge(deltaKg: Float): String = "+${deltaKg.toInt()} kg PR"

// ---------------------------------------------------------------------------
// ExerciseSummaryScreen composable
// ---------------------------------------------------------------------------

@Composable
fun ExerciseSummaryScreen(
    uiState: WearUiState,
    onAdvance: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Fire haptic then auto-advance after 2 seconds
    LaunchedEffect(Unit) {
        WearHaptics.exerciseSummary(context)
        delay(2_000)
        onAdvance()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable { onAdvance() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            // "DONE" violet label
            Text(
                text = "DONE",
                color = WearColors.Summary,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(6.dp))

            // Exercise name
            Text(
                text = uiState.lastExerciseName,
                color = WearColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            // Volume (only when > 0)
            val volumeText = formatVolume(uiState.lastExerciseVolume)
            if (volumeText != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = volumeText,
                    color = WearColors.TextSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
            }

            // PR badge
            if (uiState.isPr) {
                Spacer(Modifier.height(6.dp))
                WearPrBadge(text = formatPrBadge(uiState.prDeltaKg))
            }

            // Next exercise preview
            val nextName = uiState.session?.nextExerciseName
            if (nextName != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Next: $nextName",
                    color = WearColors.TextMuted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
