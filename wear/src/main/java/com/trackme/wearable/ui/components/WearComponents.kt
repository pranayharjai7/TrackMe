package com.trackme.wearable.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.trackme.wearable.designsystem.WearColors

@Composable
fun WearPageDots(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
    activeColor: Color = WearColors.Active,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (index in 0 until pageCount) {
            val isActive = index == currentPage
            Box(
                modifier = Modifier
                    .size(if (isActive) 7.dp else 5.dp)
                    .clip(CircleShape)
                    .background(if (isActive) activeColor else WearColors.Surface),
            )
        }
    }
}

@Composable
fun WearPrBadge(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(WearColors.Warning.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            color = WearColors.Warning,
        )
    }
}

@Composable
fun WearAmbientRow(
    heartRateBpm: Int?,
    volumeKg: Float?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (heartRateBpm != null) {
            Text(
                text = "♥ $heartRateBpm",
                fontSize = 8.sp,
                color = WearColors.Signal,
            )
        }
        if (volumeKg != null) {
            Text(
                text = "${volumeKg.toInt()} kg",
                fontSize = 8.sp,
                color = WearColors.Summary,
            )
        }
    }
}
