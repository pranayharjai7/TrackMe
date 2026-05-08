package com.trackme.ui.workout.session

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun ActiveSessionScreen(dayId: String, onSessionFinished: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Active Session $dayId — coming in Plan 2")
    }
}
