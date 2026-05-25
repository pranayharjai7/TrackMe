package com.trackme.wearable.ui.screens

import androidx.compose.foundation.background
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
import androidx.wear.compose.material3.Text
import com.trackme.wearable.designsystem.WearTokens
import com.trackme.wearable.designsystem.WearTypography

@Composable
fun WorkoutControlsOverlay(
    onPause: () -> Unit,
    onEndWorkout: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(WearTokens.Elevated.copy(alpha = 0.92f))
            .clickable(onClick = onDismiss)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Text("CONTROLS", style = WearTypography.Label)
        Spacer(Modifier.height(10.dp))
        Text(
            "END WORKOUT",
            style = WearTypography.LabelAction.copy(color = WearTokens.Signal),
            modifier = Modifier.clickable(onClick = onEndWorkout),
        )
        Spacer(Modifier.height(8.dp))
        Text("tap away to close", style = WearTypography.LabelHint)
    }
}

@Composable
fun UndoConfirmOverlay(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(WearTokens.Elevated.copy(alpha = 0.94f))
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Text("UNDO LAST SET?", style = WearTypography.Label)
        Spacer(Modifier.height(10.dp))
        Text("CONFIRM", style = WearTypography.LabelAction, modifier = Modifier.clickable(onClick = onConfirm))
        Spacer(Modifier.height(6.dp))
        Text("cancel", style = WearTypography.LabelHint, modifier = Modifier.clickable(onClick = onDismiss))
    }
}
