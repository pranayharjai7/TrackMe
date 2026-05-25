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
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.trackme.wearable.designsystem.WearTokens
import com.trackme.wearable.designsystem.WearTypography
import com.trackme.wearable.workout.formatWeight

@Composable
fun ExerciseSummaryScreen(
    exerciseName: String,
    volumeKg: Float,
    prDeltaKg: Float?,
    onAdvance: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .clickable(onClick = onAdvance)
            .padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Text(
            text = exerciseName,
            style = WearTypography.Body.copy(color = WearTokens.Summary, fontSize = 12.sp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "${volumeKg.toInt()} kg",
            style = WearTypography.Display.copy(fontSize = 13.sp),
        )
        Text("total volume", style = WearTypography.LabelHint)
        if (prDeltaKg != null && prDeltaKg > 0f) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "▲ +${formatWeight(prDeltaKg)}kg PR",
                style = WearTypography.Label.copy(color = WearTokens.Warning),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(WearTokens.Warning.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text("tap to continue", style = WearTypography.LabelHint)
    }
}
