package com.trackme.wearable.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material3.Text
import com.trackme.wearable.designsystem.WearColors
import com.trackme.wearable.designsystem.WearGlassCard
import com.trackme.wearable.designsystem.WearPillButton
import com.trackme.wearable.designsystem.WearStatusChip
import com.trackme.wearable.viewmodel.WearSessionViewModel
import com.trackme.wearable.viewmodel.WearUiState
import com.trackme.wearbridge.SessionStatePayload

// ---------------------------------------------------------------------------
// Pure helper (tested in WorkoutHubScreenTest)
// ---------------------------------------------------------------------------

/**
 * Returns the targetSets for the exercise currently at [session.exerciseIndex],
 * falling back to [session.totalSets] if the index is out of bounds.
 */
internal fun currentTargetSets(session: SessionStatePayload): Int =
    session.exercises.getOrNull(session.exerciseIndex)?.targetSets ?: session.totalSets

// ---------------------------------------------------------------------------
// WorkoutHubScreen composable
// ---------------------------------------------------------------------------

@Composable
fun WorkoutHubScreen(
    uiState: WearUiState,
    onStartWorkout: () -> Unit,
    onRequestPermission: () -> Unit,
    viewModel: WearSessionViewModel,
    modifier: Modifier = Modifier
) {
    val session = uiState.session

    ScalingLazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── Connectivity status chip ──────────────────────────────────────────
        item {
            WearStatusChip(
                offline = uiState.offline,
                queuedCount = uiState.queuedCount,
            )
        }

        item { Spacer(Modifier.height(8.dp)) }

        if (session != null) {
            // ── Active session info ───────────────────────────────────────────
            item {
                WearGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = session.exerciseName,
                        color = WearColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Set ${session.setIndex}/${currentTargetSets(session)}",
                        color = WearColors.TextSecondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val bpm = uiState.health.heartRateBpm?.toInt()
                    if (bpm != null) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = "♥ $bpm bpm",
                            color = WearColors.Signal,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }

            item {
                WearPillButton(
                    text = "Continue Workout",
                    onClick = onStartWorkout,
                    accent = WearColors.Active,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            // ── No active session ─────────────────────────────────────────────
            item {
                Text(
                    text = "Start a workout on your phone",
                    color = WearColors.TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                )
            }

            val bpm = uiState.health.heartRateBpm?.toInt()
            if (bpm != null) {
                item {
                    Text(
                        text = "♥ $bpm bpm",
                        color = WearColors.Signal,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }

        // ── Refresh button ────────────────────────────────────────────────────
        item {
            WearPillButton(
                text = "Refresh",
                onClick = { viewModel.requestSnapshot() },
                accent = WearColors.Rest,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
