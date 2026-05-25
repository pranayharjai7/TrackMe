package com.trackme.wearable.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Text
import com.trackme.wearable.designsystem.WearArcProgressRing
import com.trackme.wearable.designsystem.WearRollingMetric
import com.trackme.wearable.designsystem.WearTokens
import com.trackme.wearable.designsystem.WearTypography
import com.trackme.wearable.workout.formatDuration
import com.trackme.wearable.workout.formatWeight
import com.trackme.wearbridge.SessionStatePayload

@Composable
fun RestTimerScreen(
    session: SessionStatePayload?,
    restSeconds: Int,
    initialRestSeconds: Int,
    onEndEarly: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val total = remember(initialRestSeconds) { initialRestSeconds.coerceAtLeast(1) }
    val progress = restSeconds.toFloat() / total
    val warningBlend = if (restSeconds <= 10) 1f - (restSeconds / 10f).coerceIn(0f, 1f) else 0f

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(onClick = onEndEarly),
    ) {
        WearArcProgressRing(
            progress = progress,
            modifier = Modifier.fillMaxSize(),
            progressColor = WearTokens.Rest,
            warningBlend = warningBlend,
            centerContent = {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    WearRollingMetric(
                        value = formatDuration(restSeconds),
                        style = WearTypography.DisplayRest,
                    )
                    Spacer(Modifier.height(6.dp))
                    val nextName = session?.nextExerciseName ?: session?.exerciseName.orEmpty()
                    val weight = session?.targetWeight
                    val reps = session?.targetReps
                    val nextLine = buildString {
                        append("NEXT: $nextName")
                        if (weight != null && reps != null) {
                            append(" ${formatWeight(weight)}×$reps")
                        }
                    }
                    Text(
                        text = nextLine,
                        style = WearTypography.LabelHint,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(2.dp)
                            .padding(top = 2.dp),
                    ) {
                        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                            drawRect(WearTokens.Rest.copy(alpha = 0.2f))
                            drawRect(
                                color = WearTokens.Rest,
                                size = size.copy(width = size.width * progress.coerceIn(0f, 1f)),
                            )
                        }
                    }
                    Text("tap = end rest", style = WearTypography.LabelHint)
                }
            },
        )
    }
}
