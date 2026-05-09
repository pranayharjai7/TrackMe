package com.trackme.ui.workout.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.trackme.domain.model.Exercise
import com.trackme.domain.model.PlannedExercise
import com.trackme.domain.model.SessionSet
import com.trackme.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveSessionScreen(
    dayId: String,
    onSessionFinished: () -> Unit,
    viewModel: ActiveSessionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Active Session") },
                actions = {
                    TextButton(
                        onClick = { viewModel.finishSession(onSessionFinished) },
                        enabled = !state.isFinishing,
                    ) {
                        Text("Finish", color = Teal, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
        containerColor = Background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.restTimerRunning) {
                RestTimerBanner(
                    secondsRemaining = state.restSecondsRemaining,
                    totalSeconds = state.restSeconds,
                    onSkip = viewModel::stopRestTimer,
                )
            }

            val totalExercises = state.exercises.size
            val completedExercises = state.exercises.count { (pe, _) ->
                state.loggedSets.any { it.exerciseId == pe.exerciseId }
            }
            if (totalExercises > 0) {
                val progress = completedExercises.toFloat() / totalExercises.toFloat()
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "$completedExercises / $totalExercises exercises",
                            style = MaterialTheme.typography.labelSmall,
                            color = OnSurfaceMuted,
                        )
                        Text(
                            "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = Teal,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = Teal,
                        trackColor = OnSurfaceMuted.copy(alpha = 0.2f),
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
            ) {
                items(state.exercises, key = { it.first.id }) { (pe, exercise) ->
                    val exerciseSets = state.loggedSets.filter { it.exerciseId == pe.exerciseId }
                    ExerciseSessionCard(
                        plannedExercise = pe,
                        exercise = exercise,
                        loggedSets = exerciseSets,
                        onLogSet = { weight, reps ->
                            viewModel.logSet(pe.exerciseId, exerciseSets.size + 1, weight, reps)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RestTimerBanner(secondsRemaining: Int, totalSeconds: Int, onSkip: () -> Unit) {
    val progress = secondsRemaining.toFloat() / totalSeconds.toFloat()
    val minutes = secondsRemaining / 60
    val seconds = secondsRemaining % 60
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Violet.copy(alpha = 0.15f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.size(32.dp),
            color = Violet,
            strokeWidth = 3.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Rest", style = MaterialTheme.typography.labelSmall, color = Violet)
            Text(
                "%d:%02d".format(minutes, seconds),
                style = MaterialTheme.typography.titleMedium,
                color = OnBackground,
                fontWeight = FontWeight.Bold,
            )
        }
        TextButton(onClick = onSkip) { Text("Skip", color = OnSurfaceMuted) }
    }
}

@Composable
private fun ExerciseSessionCard(
    plannedExercise: PlannedExercise,
    exercise: Exercise?,
    loggedSets: List<SessionSet>,
    onLogSet: (weightKg: Float, reps: Int) -> Unit,
) {
    var weightInput by remember { mutableStateOf("") }
    var repsInput by remember { mutableStateOf("") }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        elevation = CardDefaults.cardElevation(4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                exercise?.name ?: plannedExercise.exerciseId,
                style = MaterialTheme.typography.titleMedium,
                color = OnSurface,
                fontWeight = FontWeight.SemiBold,
            )
            exercise?.let {
                Text(it.primaryMuscles.joinToString(", "), style = MaterialTheme.typography.labelSmall, color = Violet)
            }

            Spacer(Modifier.height(12.dp))

            loggedSets.forEachIndexed { i, set ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Set ${i + 1}", style = MaterialTheme.typography.bodySmall, color = OnSurfaceMuted)
                    Text(
                        "${set.weightKg}kg × ${set.reps}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Teal,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            if (loggedSets.isNotEmpty()) Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = weightInput,
                    onValueChange = { weightInput = it },
                    label = { Text("kg") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(12.dp),
                )
                OutlinedTextField(
                    value = repsInput,
                    onValueChange = { repsInput = it },
                    label = { Text("reps") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                )
                Button(
                    onClick = {
                        val weight = weightInput.toFloatOrNull() ?: return@Button
                        val reps = repsInput.toIntOrNull() ?: return@Button
                        onLogSet(weight, reps)
                        weightInput = ""
                        repsInput = ""
                    },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Log", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
