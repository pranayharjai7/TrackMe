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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.trackme.wearable.designsystem.WearColors
import com.trackme.wearable.viewmodel.WearUiState

// ---------------------------------------------------------------------------
// Pure helpers (internal for testability)
// ---------------------------------------------------------------------------

internal fun hrToZone(bpm: Int): Int = when {
    bpm < 95  -> 1
    bpm < 114 -> 2
    bpm < 133 -> 3
    bpm < 152 -> 4
    else      -> 5
}

internal fun zoneName(zone: Int): String = when (zone) {
    1    -> "Zone 1 · Recovery"
    2    -> "Zone 2 · Aerobic"
    3    -> "Zone 3 · Tempo"
    4    -> "Zone 4 · Threshold"
    5    -> "Zone 5 · Max"
    else -> "Zone ?"
}

internal fun zoneColor(zone: Int): Color = when (zone) {
    1    -> WearColors.Rest
    2    -> WearColors.Active
    3    -> WearColors.Summary
    4    -> WearColors.Warning
    5    -> WearColors.Signal
    else -> WearColors.TextSecondary
}

// ---------------------------------------------------------------------------
// Composable
// ---------------------------------------------------------------------------

@Composable
fun HrZonesPage(uiState: WearUiState, modifier: Modifier = Modifier) {
    val bpm   = uiState.health.heartRateBpm?.toInt()
    val zone  = bpm?.let { hrToZone(it) } ?: 0
    val color = zoneColor(zone)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Large BPM number
        Text(
            text = bpm?.toString() ?: "--",
            color = color,
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        Text(
            text = "BPM",
            color = WearColors.TextMuted,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(6.dp))

        // Zone name
        Text(
            text = if (zone > 0) zoneName(zone) else "No signal",
            color = WearColors.TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(12.dp))

        // HR Zone bars — 5 bars, active zone highlighted
        HrZoneBars(activeZone = zone)
    }
}

@Composable
private fun HrZoneBars(activeZone: Int) {
    val zoneColors = listOf(
        WearColors.Rest,
        WearColors.Active,
        WearColors.Summary,
        WearColors.Warning,
        WearColors.Signal,
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        zoneColors.forEachIndexed { index, zoneAccent ->
            val zoneNumber = index + 1
            val isActive = zoneNumber == activeZone
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "$zoneNumber",
                    color = if (isActive) zoneAccent else WearColors.TextMuted,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(end = 2.dp),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .background(
                            color = if (isActive) zoneAccent else zoneAccent.copy(alpha = 0.25f),
                        )
                )
            }
        }
    }
}
