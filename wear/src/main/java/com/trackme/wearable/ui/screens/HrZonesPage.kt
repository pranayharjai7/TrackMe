package com.trackme.wearable.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.trackme.wearable.designsystem.WearColors

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

internal fun Int.zoneName(): String = when (this) {
    1    -> "Zone 1 · Recovery"
    2    -> "Zone 2 · Aerobic"
    3    -> "Zone 3 · Tempo"
    4    -> "Zone 4 · Threshold"
    5    -> "Zone 5 · Max"
    else -> "Zone ?"
}

internal fun Int.zoneColor(): Color = when (this) {
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
fun HrZonesPage(
    heartRateBpm: Double?,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
) {
    val focusRequester = remember { FocusRequester() }
    val bpm   = heartRateBpm?.toInt()
    val zone  = bpm?.let { hrToZone(it) } ?: 0
    val color = zone.zoneColor()

    LaunchedEffect(isActive) {
        if (isActive) {
            focusRequester.requestFocus()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Large BPM number
        Text(
            text = bpm?.toString() ?: "--",
            color = color,
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
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
            text = if (zone > 0) zone.zoneName() else "No signal",
            color = WearColors.TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(12.dp))

        // HR Zone bars — horizontal 5-segment color bar
        HrZoneBarsHorizontal(activeZone = zone)
    }
}

@Composable
private fun HrZoneBarsHorizontal(activeZone: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        (1..5).forEach { zoneNumber ->
            val zoneColor = zoneNumber.zoneColor()
            val isActive = zoneNumber == activeZone
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(5.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        color = if (isActive) zoneColor else zoneColor.copy(alpha = 0.15f),
                    )
            )
        }
    }
}
