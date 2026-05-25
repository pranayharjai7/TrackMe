package com.trackme.wearable.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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

@Composable
fun MediaPage(
    trackTitle: String,
    artist: String,
    volume: Int,
    onPrevious: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(WearTokens.Surface),
            contentAlignment = Alignment.Center,
        ) {
            Text("♪", style = WearTypography.Body)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = trackTitle,
            style = WearTypography.Body.copy(fontSize = 10.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(artist, style = WearTypography.LabelHint, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MediaControl("⏮", onPrevious)
            MediaControl("⏸", onTogglePlay)
            MediaControl("⏭", onNext)
        }
        Spacer(Modifier.height(4.dp))
        Text("vol $volume · bezel", style = WearTypography.LabelHint)
    }
}

@Composable
private fun MediaControl(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(50))
            .background(WearTokens.Elevated)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = WearTypography.Body.copy(fontSize = 14.sp))
    }
}
