package com.trackme.wearable.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.trackme.wearable.designsystem.WearReadinessRing
import com.trackme.wearable.designsystem.WearTokens
import com.trackme.wearable.designsystem.WearTypography
import com.trackme.wearable.workout.ReadinessUi

@Composable
fun WatchFaceScreen(
    readiness: ReadinessUi,
    heartRate: Int?,
    calories: Int,
    onOpenHub: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .clickable(onClick = onOpenHub)
            .padding(horizontal = 12.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("READINESS", style = WearTypography.Label)
        Spacer(Modifier.height(4.dp))
        WearReadinessRing(progress = readiness.ringProgress, score = readiness.score)
        Spacer(Modifier.height(4.dp))
        Text(readiness.label, style = WearTypography.LabelHint)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("♥", style = WearTypography.Body.copy(color = WearTokens.Signal, fontSize = 9.sp))
                Text(
                    heartRate?.toString() ?: "—",
                    style = WearTypography.Ambient,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("CAL", style = WearTypography.Ambient)
                Text(calories.toString(), style = WearTypography.Ambient)
            }
        }
    }
}
