package com.trackme.wearable.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Text
import com.trackme.wearable.designsystem.WearTokens
import com.trackme.wearable.designsystem.WearTypography
import com.trackme.wearable.workout.HubWorkoutItem
import com.trackme.wearable.workout.ReadinessUi

@Composable
fun WorkoutHubScreen(
    workout: HubWorkoutItem,
    readiness: ReadinessUi,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .clickable(onClick = onStart)
            .padding(horizontal = 14.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("TODAY", style = WearTypography.Label)
        Spacer(Modifier.height(4.dp))
        Text(
            text = workout.name,
            style = WearTypography.Body,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${workout.exerciseCount} exercises",
            style = WearTypography.LabelHint,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "${readiness.score} · ${readiness.label}",
            style = WearTypography.Label.copy(color = WearTokens.Active),
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(WearTokens.Active.copy(alpha = 0.15f))
                .padding(horizontal = 10.dp, vertical = 3.dp),
        )
        Spacer(Modifier.height(10.dp))
        Text("TAP = START", style = WearTypography.LabelAction)
        Text("bezel = browse", style = WearTypography.LabelHint)
    }
}
