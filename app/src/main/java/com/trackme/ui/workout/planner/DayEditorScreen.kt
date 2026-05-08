package com.trackme.ui.workout.planner

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun DayEditorScreen(dayId: String, onAddExercise: () -> Unit, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Day Editor $dayId — coming in Plan 2")
    }
}
