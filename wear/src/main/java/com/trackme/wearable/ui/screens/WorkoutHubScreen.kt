package com.trackme.wearable.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.trackme.wearable.viewmodel.WearSessionViewModel
import com.trackme.wearable.viewmodel.WearUiState

@Composable
fun WorkoutHubScreen(
    uiState: WearUiState,
    onStartWorkout: () -> Unit,
    onRequestPermission: () -> Unit,
    viewModel: WearSessionViewModel,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize())
}
