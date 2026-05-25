package com.trackme.wearable.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.wear.compose.material3.MaterialTheme
import com.trackme.wearable.designsystem.WearGradientBackground
import com.trackme.wearable.interaction.rememberHapticFeedbackManager
import com.trackme.wearable.viewmodel.WearSessionViewModel
import com.trackme.wearable.workout.WorkoutInteractionEngine

@Composable
fun TrackMeWearApp(viewModel: WearSessionViewModel) {
    val state by viewModel.uiState.collectAsState()
    val haptics = rememberHapticFeedbackManager()
    val engine = remember(viewModel, haptics) {
        WorkoutInteractionEngine(viewModel = viewModel, haptics = haptics)
    }

    MaterialTheme {
        WearGradientBackground {
            WorkoutExperience(
                state = state,
                viewModel = viewModel,
                engine = engine,
            )
        }
    }
}
