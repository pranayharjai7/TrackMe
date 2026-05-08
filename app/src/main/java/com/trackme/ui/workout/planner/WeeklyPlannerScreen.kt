package com.trackme.ui.workout.planner

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun WeeklyPlannerScreen(onEditDay: (dayId: String) -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Weekly Planner — coming in Plan 2", style = MaterialTheme.typography.titleLarge)
    }
}
