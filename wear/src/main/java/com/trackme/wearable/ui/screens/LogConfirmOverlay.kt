package com.trackme.wearable.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
import com.trackme.wearable.designsystem.WearTokens
import com.trackme.wearable.designsystem.WearTypography
import com.trackme.wearable.interaction.routeSwipeDownCancel

@Composable
fun LogConfirmOverlay(
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .clickable(onClick = onConfirm)
            .routeSwipeDownCancel(onCancel = onCancel)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Text("✓", style = WearTypography.Display.copy(fontSize = 36.sp, color = WearTokens.Active))
        Spacer(Modifier.height(6.dp))
        Text("LOG SET", style = WearTypography.Label.copy(color = WearTokens.Active, letterSpacing = 2.sp))
        Spacer(Modifier.height(8.dp))
        Text("tap anywhere", style = WearTypography.LabelHint)
        Text("↓ swipe = cancel", style = WearTypography.LabelHint.copy(color = WearTokens.TextMuted.copy(alpha = 0.6f)))
    }
}
