package com.trackme.wearable.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Text
import com.trackme.wearable.designsystem.WearRollingMetric
import com.trackme.wearable.designsystem.WearTokens
import com.trackme.wearable.designsystem.WearTypography
import com.trackme.wearable.workout.hrZone

private val zoneColors = listOf(
    WearTokens.Rest,
    WearTokens.Active,
    WearTokens.Warning,
    androidx.compose.ui.graphics.Color(0xFFFB923C),
    WearTokens.Signal,
)

@Composable
fun HrZonesPage(
    bpm: Int?,
    modifier: Modifier = Modifier,
) {
    val safeBpm = bpm ?: 0
    val (zone, zoneLabel) = if (bpm != null) hrZone(safeBpm) else 0 to "—"

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        WearRollingMetric(
            value = bpm?.toString() ?: "—",
            style = WearTypography.DisplayHr,
        )
        Text(zoneLabel, style = WearTypography.Label.copy(color = WearTokens.Signal))
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            zoneColors.forEachIndexed { index, color ->
                val active = index + 1 == zone
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(color.copy(alpha = if (active) 1f else 0.25f)),
                )
            }
        }
    }
}
