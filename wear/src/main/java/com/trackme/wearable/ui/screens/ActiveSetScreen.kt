package com.trackme.wearable.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Text
import com.trackme.wearable.designsystem.WearArcProgressRing
import com.trackme.wearable.designsystem.WearRollingMetric
import com.trackme.wearable.designsystem.WearTokens
import com.trackme.wearable.designsystem.WearTypography
import com.trackme.wearable.viewmodel.LoggerField
import com.trackme.wearable.viewmodel.LoggerInputState
import com.trackme.wearable.workout.ExerciseLayoutProfile
import com.trackme.wearable.workout.primaryDisplayValue
import com.trackme.wearable.workout.secondaryLine
import com.trackme.wearable.workout.setProgressWithinExercise
import com.trackme.wearbridge.SessionStatePayload

@Composable
fun ActiveSetScreen(
    session: SessionStatePayload,
    input: LoggerInputState,
    profile: ExerciseLayoutProfile,
    heartRate: Int?,
    volumeKg: Float,
    offline: Boolean,
    modifier: Modifier = Modifier,
) {
    val exercise = session.exercises.getOrNull(session.exerciseIndex)
    val totalSets = exercise?.targetSets ?: session.totalSets
    val setNumber = (exercise?.completedSets ?: session.setIndex).coerceAtLeast(1)
    val weightActive = input.activeField == LoggerField.WEIGHT

    WearArcProgressRing(
        progress = setProgressWithinExercise(session),
        modifier = modifier.fillMaxSize(),
        progressColor = WearTokens.Active,
        centerContent = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = session.exerciseName.uppercase(),
                    style = WearTypography.ExerciseName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    WearRollingMetric(
                        value = input.primaryDisplayValue(profile, input.activeField),
                        style = WearTypography.Display.copy(
                            color = if (weightActive || profile != ExerciseLayoutProfile.Strength) {
                                WearTokens.TextPrimary
                            } else {
                                WearTokens.TextMuted
                            },
                        ),
                        increaseHint = null,
                    )
                    if (profile == ExerciseLayoutProfile.Strength && input.activeField == LoggerField.WEIGHT) {
                        Text(" kg", style = WearTypography.Unit)
                    }
                }
                Text(
                    text = input.secondaryLine(profile, input.activeField),
                    style = WearTypography.Body.copy(
                        color = if (!weightActive && profile == ExerciseLayoutProfile.Strength) {
                            WearTokens.TextPrimary
                        } else {
                            WearTokens.TextSecondary
                        },
                    ),
                )
                Text(
                    text = "SET $setNumber OF $totalSets",
                    style = WearTypography.Label.copy(color = WearTokens.TextMuted),
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    Text("♥ ${heartRate ?: "—"}", style = WearTypography.Ambient.copy(color = WearTokens.Signal))
                    Text("${volumeKg.toInt()} kg", style = WearTypography.Ambient.copy(color = WearTokens.Summary))
                }
                if (offline) {
                    Text("offline · queued", style = WearTypography.LabelHint.copy(color = WearTokens.Warning))
                }
                Text("TAP →", style = WearTypography.LabelAction)
            }
        },
    )
}
