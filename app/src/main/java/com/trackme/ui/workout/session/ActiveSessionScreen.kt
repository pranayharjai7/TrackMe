package com.trackme.ui.workout.session

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*import androidx.compose.material3.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    onBack: () -> Unit,
    onExerciseClick: (exerciseId: String) -> Unit,
    viewModel: ActiveSessionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Active Session") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
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
                        onExerciseClick = { exercise?.let { onExerciseClick(it.id) } },
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
    onExerciseClick: () -> Unit,
    onLogSet: (weightKg: Float, reps: Int, durationSeconds: Int?, distanceKm: Float?, speedKmh: Float?, inclinePercent: Float?) -> Unit,
) {
    val loggingType = exercise?.loggingType() ?: LoggingType.WEIGHTED_REPS
    var targetSets by rememberSaveable(plannedExercise.id) { mutableIntStateOf(plannedExercise.targetSets) }
    val currentSetNumber = loggedSets.size + 1
    val allSetsLogged = loggedSets.size >= targetSets

    var weightInput by remember {
        mutableStateOf(plannedExercise.targetWeightKg?.let { if (it > 0f) it.toString() else "" } ?: "")
    }
    var repsInput by remember { mutableStateOf(plannedExercise.targetReps?.toString() ?: "") }
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

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        elevation = CardDefaults.cardElevation(4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            // Header
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
                IconButton(
                    onClick = onExerciseClick,
                    enabled = exercise != null,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "Exercise info",
                        tint = OnSurfaceMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // Set progress chips with +/- controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(
                    onClick = { if (targetSets > maxOf(loggedSets.size, 1)) targetSets-- },
                    modifier = Modifier.size(28.dp),
                    enabled = targetSets > maxOf(loggedSets.size, 1),
                ) {
                    Icon(
                        Icons.Default.Remove,
                        contentDescription = "Remove set",
                        tint = if (targetSets > maxOf(loggedSets.size, 1)) OnSurface else OnSurfaceMuted.copy(alpha = 0.3f),
                        modifier = Modifier.size(16.dp),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (i in 1..targetSets) {
                        val isCompleted = i <= loggedSets.size
                        val isCurrent = i == currentSetNumber && !allSetsLogged
                        SetChip(index = i, isCompleted = isCompleted, isCurrent = isCurrent)
                    }
                }
                IconButton(
                    onClick = { targetSets++ },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add set",
                        tint = OnSurface,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                if (allSetsLogged) {
                    Surface(shape = RoundedCornerShape(20.dp), color = Teal.copy(alpha = 0.15f)) {
                        Text(
                            "Done",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Teal,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                } else {
                    Text(
                        "Set $currentSetNumber of $targetSets",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceMuted,
                    )
                }
            }

            // Logged set values
            if (loggedSets.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    loggedSets.forEachIndexed { i, set ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "Set ${i + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = OnSurfaceMuted,
                            )
                            Text(
                                setChipLabel(set, loggingType),
                                style = MaterialTheme.typography.labelSmall,
                                color = Teal,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }

            // Input fields (only when sets remain)
            if (!allSetsLogged) {
                HorizontalDivider(color = OnSurfaceMuted.copy(alpha = 0.15f))
                when (loggingType) {
                    LoggingType.WEIGHTED_REPS -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SessionField("Weight (kg)", weightInput, Modifier.weight(1f), KeyboardType.Decimal) { weightInput = it }
                            SessionField("Reps", repsInput, Modifier.weight(1f), KeyboardType.Number) { repsInput = it }
                        }
                        Button(
                            onClick = {
                                val w = weightInput.toFloatOrNull() ?: return@Button
                                val r = repsInput.toIntOrNull() ?: return@Button
                                onLogSet(w, r, null, null, null, null)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text("Log Set $currentSetNumber", fontWeight = FontWeight.SemiBold)
                        }
                    }
                    LoggingType.BODYWEIGHT_REPS -> {
                        SessionField("Reps", repsInput, Modifier.fillMaxWidth(), KeyboardType.Number) { repsInput = it }
                        Button(
                            onClick = {
                                val r = repsInput.toIntOrNull() ?: return@Button
                                onLogSet(0f, r, null, null, null, null)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text("Log Set $currentSetNumber", fontWeight = FontWeight.SemiBold)
                        }
                    }
                    LoggingType.TIMED -> {
                        SessionField("Duration (seconds)", durationInput, Modifier.fillMaxWidth(), KeyboardType.Number) { durationInput = it }
                        Button(
                            onClick = {
                                val d = durationInput.toIntOrNull() ?: return@Button
                                onLogSet(0f, 0, d, null, null, null)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text("Log Set $currentSetNumber", fontWeight = FontWeight.SemiBold)
                        }
                    }
                    LoggingType.CARDIO -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SessionField("Duration (min)", durationInput, Modifier.weight(1f), KeyboardType.Number) { durationInput = it }
                            SessionField("Speed (km/h)", speedInput, Modifier.weight(1f), KeyboardType.Decimal) { speedInput = it }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SessionField("Distance (km)", distanceInput, Modifier.weight(1f), KeyboardType.Decimal) { distanceInput = it }
                            SessionField("Incline (%)", inclineInput, Modifier.weight(1f), KeyboardType.Decimal) { inclineInput = it }
                        }
                        Button(
                            onClick = {
                                val d = durationInput.toIntOrNull()?.times(60) ?: return@Button
                                onLogSet(0f, 0, d, distanceInput.toFloatOrNull(), speedInput.toFloatOrNull(), inclineInput.toFloatOrNull())
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text("Log Cardio", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SetChip(index: Int, isCompleted: Boolean, isCurrent: Boolean) {
    val bgColor = when {
        isCompleted -> Teal
        isCurrent -> Violet
        else -> OnSurfaceMuted.copy(alpha = 0.12f)
    }
    val border = if (isCurrent) BorderStroke(1.5.dp, Violet) else null
    val contentColor = when {
        isCompleted || isCurrent -> Color.White
        else -> OnSurfaceMuted
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = bgColor,
        border = border,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (isCompleted) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Set $index done",
                    tint = contentColor,
                    modifier = Modifier.size(13.dp),
                )
            } else {
                Text(
                    index.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                )
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
