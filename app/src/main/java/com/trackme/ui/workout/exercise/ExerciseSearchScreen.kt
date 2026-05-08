package com.trackme.ui.workout.exercise

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun ExerciseSearchScreen(
    dayId: String,
    onExerciseClick: (exerciseId: String) -> Unit,
    onBack: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Exercise Search — coming in Task 13")
    }
}
