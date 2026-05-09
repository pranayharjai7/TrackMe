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
import com.trackme.domain.model.LoggingType
import com.trackme.domain.model.PlannedExercise
import com.trackme.domain.model.SessionSet
import com.trackme.domain.model.loggingType
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
                        onLogSet = { weight, reps, duration, distance, speed, incline ->
                            viewModel.logSet(
                                pe.exerciseId, exerciseSets.size + 1,
                                weight, reps, duration, distance, speed, incline,
                            )
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
    onLogSet: (weightKg: Float, reps: Int, durationSeconds: Int?, distanceKm: Float?, speedKmh: Float?, inclinePercent: Float?) -> Unit,
) {
    val loggingType = exercise?.loggingType() ?: LoggingType.WEIGHTED_REPS
    val targetSets = plannedExercise.targetSets

    var weightInput by remember {
        mutableStateOf(plannedExercise.targetWeightKg?.let { if (it > 0f) it.toString() else "" } ?: "")
    }
    var repsInput by remember {
        mutableStateOf(plannedExercise.targetReps?.toString() ?: "")
    }
    var durationInput by remember {
        mutableStateOf(
            when (loggingType) {
                LoggingType.CARDIO -> plannedExercise.targetDurationSeconds?.let { (it / 60).toString() } ?: ""
                LoggingType.TIMED -> plannedExercise.targetDurationSeconds?.toString() ?: ""
                else -> ""
            }
        )
    }
    var distanceInput by remember { mutableStateOf(plannedExercise.targetDistanceKm?.toString() ?: "") }
    var speedInput by remember { mutableStateOf(plannedExercise.targetSpeedKmh?.toString() ?: "") }
    var inclineInput by remember { mutableStateOf(plannedExercise.targetIncline?.toString() ?: "") }

    val currentSetNumber = loggedSets.size + 1

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        elevation = CardDefaults.cardElevation(4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        exercise?.name ?: plannedExercise.exerciseId,
                        style = MaterialTheme.typography.titleMedium,
                        color = OnSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    exercise?.let {
                        Text(it.primaryMuscles.joinToString(", "), style = MaterialTheme.typography.labelSmall, color = Violet)
                    }
                }
                Text(
                    "Set $currentSetNumber of $targetSets",
                    style = MaterialTheme.typography.labelSmall,
                    color = Teal,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(12.dp))

            loggedSets.forEachIndexed { i, set ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Set ${i + 1}", style = MaterialTheme.typography.bodySmall, color = OnSurfaceMuted)
                    Text(
                        setChipLabel(set, loggingType),
                        style = MaterialTheme.typography.bodySmall,
                        color = Teal,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            if (loggedSets.isNotEmpty()) Spacer(Modifier.height(8.dp))

            when (loggingType) {
                LoggingType.WEIGHTED_REPS -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SessionField("kg", weightInput, Modifier.weight(1f), KeyboardType.Decimal) { weightInput = it }
                        SessionField("reps", repsInput, Modifier.weight(1f), KeyboardType.Number) { repsInput = it }
                        LogButton {
                            val w = weightInput.toFloatOrNull() ?: return@LogButton
                            val r = repsInput.toIntOrNull() ?: return@LogButton
                            onLogSet(w, r, null, null, null, null)
                        }
                    }
                }
                LoggingType.BODYWEIGHT_REPS -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SessionField("reps", repsInput, Modifier.weight(1f), KeyboardType.Number) { repsInput = it }
                        LogButton {
                            val r = repsInput.toIntOrNull() ?: return@LogButton
                            onLogSet(0f, r, null, null, null, null)
                        }
                    }
                }
                LoggingType.TIMED -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SessionField("duration (s)", durationInput, Modifier.weight(1f), KeyboardType.Number) { durationInput = it }
                        LogButton {
                            val d = durationInput.toIntOrNull() ?: return@LogButton
                            onLogSet(0f, 0, d, null, null, null)
                        }
                    }
                }
                LoggingType.CARDIO -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SessionField("min", durationInput, Modifier.weight(1f), KeyboardType.Number) { durationInput = it }
                            SessionField("km/h", speedInput, Modifier.weight(1f), KeyboardType.Decimal) { speedInput = it }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SessionField("km", distanceInput, Modifier.weight(1f), KeyboardType.Decimal) { distanceInput = it }
                            SessionField("incline%", inclineInput, Modifier.weight(1f), KeyboardType.Decimal) { inclineInput = it }
                            LogButton {
                                val d = durationInput.toIntOrNull()?.times(60) ?: return@LogButton
                                onLogSet(0f, 0, d, distanceInput.toFloatOrNull(), speedInput.toFloatOrNull(), inclineInput.toFloatOrNull())
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun setChipLabel(set: SessionSet, loggingType: LoggingType): String = when (loggingType) {
    LoggingType.WEIGHTED_REPS -> "${set.weightKg}kg × ${set.reps}"
    LoggingType.BODYWEIGHT_REPS -> "${set.reps} reps"
    LoggingType.TIMED -> set.durationSeconds?.let { "${it}s" } ?: "${set.reps}s"
    LoggingType.CARDIO -> buildString {
        set.durationSeconds?.let { append("${it / 60}min") }
        set.speedKmh?.let { if (isNotEmpty()) append(" · "); append("${it}km/h") }
        set.distanceKm?.let { if (isNotEmpty()) append(" · "); append("${it}km") }
    }.ifEmpty { "done" }
}

@Composable
private fun SessionField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Number,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(12.dp),
    )
}

@Composable
private fun LogButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Icon(Icons.Default.Check, contentDescription = "Log", modifier = Modifier.size(18.dp))
    }
}
