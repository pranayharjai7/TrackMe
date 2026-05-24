package com.trackme.wearable.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import com.trackme.wearable.designsystem.WearColors
import com.trackme.wearable.designsystem.WearGlassCard
import com.trackme.wearable.designsystem.WearHeadline
import com.trackme.wearable.designsystem.WearIconButton
import com.trackme.wearable.designsystem.WearPillButton
import com.trackme.wearable.designsystem.WearProgressRing
import com.trackme.wearable.designsystem.WearStatusChip
import com.trackme.wearable.viewmodel.WearUiState
import androidx.wear.compose.material3.Text

@Composable
fun WearHomeScreen(
    state: WearUiState,
    onOpenWorkout: () -> Unit,
    onSync: () -> Unit,
) {
    val session = state.session
    val hasWorkout = session?.sessionId?.isNotBlank() == true

    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item { WearStatusChip(offline = state.offline, queuedCount = state.queuedCount) }
        item {
            WearProgressRing(
                progress = (session?.sessionProgressPercent ?: 0) / 100f,
                centerText = if (hasWorkout) "${session?.sessionProgressPercent ?: 0}%" else "—",
                accent = if (hasWorkout) WearColors.Accent else WearColors.Violet,
            )
        }
        item {
            WearGlassCard {
                WearHeadline(
                    text = if (hasWorkout) "Workout live" else "TrackMe",
                    subtitle = if (hasWorkout) {
                        session?.exerciseName ?: "Active session"
                    } else {
                        "Start on phone to begin"
                    },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "HR ${state.health.heartRateBpm?.toInt() ?: "—"} · ${state.health.activeCalories?.toInt() ?: 0} kcal",
                    color = WearColors.TextMuted,
                )
            }
        }
        item {
            WearPillButton(
                text = if (hasWorkout) "Open workout" else "Waiting for session",
                onClick = onOpenWorkout,
                enabled = hasWorkout,
            )
        }
        item {
            WearPillButton(
                text = "Sync with phone",
                onClick = onSync,
                accent = WearColors.Violet,
            )
        }
        item {
            androidx.compose.foundation.layout.Row {
                WearIconButton("Sync", onSync)
            }
        }
    }
}
