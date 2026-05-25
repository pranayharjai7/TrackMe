package com.trackme.wearable.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.trackme.wearable.designsystem.WearArcProgressRing
import com.trackme.wearable.designsystem.WearTokens
import com.trackme.wearable.designsystem.WearTypography
import com.trackme.wearable.workout.formatDuration
import com.trackme.wearbridge.SessionExercisePayload

@Composable
fun WorkoutSummaryScreen(
    durationSeconds: Long,
    exerciseCount: Int,
    totalVolumeKg: Float,
    prCount: Int,
    detailExercise: SessionExercisePayload?,
    syncing: Boolean,
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .clickable(onClick = onDismiss)
            .padding(horizontal = 14.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("COMPLETE", style = WearTypography.Label.copy(color = WearTokens.Warning))
        Text(
            text = formatDuration(durationSeconds.toInt()),
            style = WearTypography.Body.copy(fontSize = 14.sp),
        )
        Text("$exerciseCount exercises", style = WearTypography.LabelHint)
        Text(
            text = "${totalVolumeKg.toInt()} kg",
            style = WearTypography.Label.copy(color = WearTokens.Warning),
        )
        if (prCount > 0) {
            Text("${prCount}× PR", style = WearTypography.LabelHint.copy(color = WearTokens.Warning))
        }
        Spacer(Modifier.height(8.dp))
        detailExercise?.let { ex ->
            Text(
                text = ex.exerciseName,
                style = WearTypography.LabelHint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${ex.completedSets}/${ex.targetSets} sets",
                style = WearTypography.Ambient,
            )
        }
        Text("bezel = details", style = WearTypography.LabelHint)
        if (syncing) {
            Spacer(Modifier.height(8.dp))
            WearArcProgressRing(
                progress = 0.65f,
                modifier = Modifier.size(32.dp),
                strokeWidth = 3.dp,
                progressColor = WearTokens.Warning,
                centerContent = {},
            )
        }
    }
}
