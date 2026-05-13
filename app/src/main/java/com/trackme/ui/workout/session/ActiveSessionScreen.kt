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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import com.trackme.ui.components.HorizontalWheelPicker
import com.trackme.ui.components.WheelPicker
import com.trackme.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ActiveSessionScreen(
    dayId: String,
    onSessionFinished: () -> Unit,
    onBack: () -> Unit,
    onExerciseClick: (exerciseId: String) -> Unit,
    onAddExercise: () -> Unit,
    viewModel: ActiveSessionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("Active Session", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
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
                contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp),
            ) {
                items(state.exercises, key = { it.first.id }) { (pe, exercise) ->
                    val exerciseSets = state.loggedSets.filter { it.exerciseId == pe.exerciseId }
                    ExerciseSessionCard(
                        plannedExercise = pe,
                        exercise = exercise,
                        loggedSets = exerciseSets,
                        inputStyle = state.inputStyle,
                        onExerciseClick = { exercise?.let { onExerciseClick(it.id) } },
                        onLogSet = { weight, reps, duration, distance, speed, incline ->
                            viewModel.logSet(
                                pe.exerciseId, exerciseSets.size + 1,
                                weight, reps, duration, distance, speed, incline,
                            )
                        },
                        onTargetSetsChanged = { newTarget ->
                            viewModel.updateTargetSets(pe.exerciseId, newTarget)
                        }
                    )
                }

                item {
                    OutlinedButton(
                        onClick = onAddExercise,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Violet.copy(alpha = 0.4f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add Exercise", fontWeight = FontWeight.SemiBold)
                    }
                }

                if (!state.isFinishing) {
                    item {
                        Button(
                            onClick = { viewModel.finishSession(onSessionFinished) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp)
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Teal, contentColor = Color.White),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                            Text("Finish Workout", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }
                    }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseSessionCard(
    plannedExercise: PlannedExercise,
    exercise: Exercise?,
    loggedSets: List<SessionSet>,
    inputStyle: String,
    onExerciseClick: () -> Unit,
    onLogSet: (weightKg: Float, reps: Int, durationSeconds: Int?, distanceKm: Float?, speedKmh: Float?, inclinePercent: Float?) -> Unit,
    onTargetSetsChanged: (Int) -> Unit = {},
) {
    val loggingType = exercise?.loggingType() ?: LoggingType.WEIGHTED_REPS
    val targetSets = plannedExercise.targetSets
    val currentSetNumber = loggedSets.size + 1
    val allSetsLogged = loggedSets.size >= targetSets

    val wholeNumbers = remember { (0..300).toList() }
    val decimalWeightOptions = remember { listOf(0.0f, 0.25f, 0.5f, 0.75f) }
    val decimalTenths = remember { (0..9).map { it / 10f } }
    val repsList = remember { (0..100).toList() }
    val minsList = remember { (0..120).toList() }
    val secsList = remember { (0..59).toList() }

    val initialWeight = plannedExercise.targetWeightKg ?: 0f
    var weightWhole by remember { mutableIntStateOf(initialWeight.toInt()) }
    var weightDecimal by remember { mutableFloatStateOf(decimalWeightOptions.minByOrNull { kotlin.math.abs(it - (initialWeight - initialWeight.toInt())) } ?: 0f) }
    
    var repsInput by remember { mutableIntStateOf(plannedExercise.targetReps ?: 0) }
    
    val initialDuration = plannedExercise.targetDurationSeconds ?: 0
    var durationMin by remember { mutableIntStateOf(initialDuration / 60) }
    var durationSec by remember { mutableIntStateOf(initialDuration % 60) }
    
    val initialDistance = plannedExercise.targetDistanceKm ?: 0f
    var distanceWhole by remember { mutableIntStateOf(initialDistance.toInt()) }
    var distanceDecimal by remember { mutableFloatStateOf(decimalTenths.minByOrNull { kotlin.math.abs(it - (initialDistance - initialDistance.toInt())) } ?: 0f) }
    
    val initialSpeed = plannedExercise.targetSpeedKmh ?: 0f
    var speedWhole by remember { mutableIntStateOf(initialSpeed.toInt()) }
    var speedDecimal by remember { mutableFloatStateOf(decimalTenths.minByOrNull { kotlin.math.abs(it - (initialSpeed - initialSpeed.toInt())) } ?: 0f) }
    
    val initialIncline = plannedExercise.targetIncline ?: 0f
    var inclineWhole by remember { mutableIntStateOf(initialIncline.toInt()) }
    var inclineDecimal by remember { mutableFloatStateOf(decimalTenths.minByOrNull { kotlin.math.abs(it - (initialIncline - initialIncline.toInt())) } ?: 0f) }

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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        if (allSetsLogged) "Done" else "Set $currentSetNumber of $targetSets",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (allSetsLogged) Teal else OnSurfaceMuted,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = { if (targetSets > maxOf(loggedSets.size, 1)) onTargetSetsChanged(targetSets - 1) },
                            modifier = Modifier.size(28.dp),
                            enabled = targetSets > maxOf(loggedSets.size, 1),
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Remove", tint = if (targetSets > maxOf(loggedSets.size, 1)) OnSurface else OnSurfaceMuted.copy(alpha = 0.3f), modifier = Modifier.size(16.dp))
                        }
                        IconButton(
                            onClick = { onTargetSetsChanged(targetSets + 1) },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add", tint = OnSurface, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (i in 1..targetSets) {
                        val isCompleted = i <= loggedSets.size
                        val isCurrent = i == currentSetNumber && !allSetsLogged
                        SetChip(index = i, isCompleted = isCompleted, isCurrent = isCurrent)
                    }
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
                
                var isExpanded by remember { mutableStateOf(inputStyle != "TAP_EXPAND") }
                LaunchedEffect(inputStyle) { isExpanded = inputStyle != "TAP_EXPAND" }
                
                if (!isExpanded) {
                    val summaryText = when (loggingType) {
                        LoggingType.WEIGHTED_REPS -> "${weightWhole + weightDecimal} kg × $repsInput reps"
                        LoggingType.BODYWEIGHT_REPS -> "$repsInput reps"
                        LoggingType.TIMED -> "${durationMin}m ${durationSec}s"
                        LoggingType.CARDIO -> "${durationMin}m | ${distanceWhole + distanceDecimal} km | ${speedWhole + speedDecimal} km/h | ${inclineWhole + inclineDecimal}%"
                    }
                    OutlinedButton(
                        onClick = { isExpanded = true },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Violet.copy(alpha = 0.3f))
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Adjust, contentDescription = null, tint = Violet, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(summaryText, color = OnSurface, style = MaterialTheme.typography.bodyMedium)
                            }
                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = OnSurfaceMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                if (isExpanded) {
                    val isHorizontal = inputStyle == "HORIZONTAL"
                    when (loggingType) {
                    LoggingType.WEIGHTED_REPS -> {
                        if (isHorizontal) {
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text("Weight (kg)", style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                        HorizontalWheelPicker(items = wholeNumbers, initialIndex = weightWhole, modifier = Modifier.weight(1f), onItemSelected = { weightWhole = it })
                                        Text(".", style = MaterialTheme.typography.titleLarge)
                                        HorizontalWheelPicker(items = decimalWeightOptions, initialIndex = decimalWeightOptions.indexOf(weightDecimal).takeIf { it >= 0 } ?: 0, modifier = Modifier.weight(1f), itemToString = { it.toString().substringAfter('.') }, onItemSelected = { weightDecimal = it })
                                    }
                                }
                                Column(Modifier.fillMaxWidth()) {
                                    Text("Reps", style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
                                    HorizontalWheelPicker(items = repsList, initialIndex = repsInput, modifier = Modifier.fillMaxWidth(), onItemSelected = { repsInput = it })
                                }
                            }
                        } else {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Weight (kg)", style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                        WheelPicker(items = wholeNumbers, initialIndex = weightWhole, modifier = Modifier.width(56.dp), onItemSelected = { weightWhole = it })
                                        Text(".", style = MaterialTheme.typography.titleLarge)
                                        WheelPicker(items = decimalWeightOptions, initialIndex = decimalWeightOptions.indexOf(weightDecimal).takeIf { it >= 0 } ?: 0, modifier = Modifier.width(56.dp), itemToString = { it.toString().substringAfter('.') }, onItemSelected = { weightDecimal = it })
                                    }
                                }
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Reps", style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
                                    WheelPicker(items = repsList, initialIndex = repsInput, modifier = Modifier.width(80.dp), onItemSelected = { repsInput = it })
                                }
                            }
                        }
                    }
                    LoggingType.BODYWEIGHT_REPS -> {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Reps", style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
                            if (isHorizontal) {
                                HorizontalWheelPicker(items = repsList, initialIndex = repsInput, modifier = Modifier.fillMaxWidth(), onItemSelected = { repsInput = it })
                            } else {
                                WheelPicker(items = repsList, initialIndex = repsInput, modifier = Modifier.width(80.dp), onItemSelected = { repsInput = it })
                            }
                        }
                    }
                    LoggingType.TIMED -> {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Duration", style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                if (isHorizontal) {
                                    HorizontalWheelPicker(items = minsList, initialIndex = durationMin, modifier = Modifier.weight(1f), itemToString = { "$it m" }, onItemSelected = { durationMin = it })
                                    Text(":", style = MaterialTheme.typography.titleLarge)
                                    HorizontalWheelPicker(items = secsList, initialIndex = durationSec, modifier = Modifier.weight(1f), itemToString = { "%02d s".format(it) }, onItemSelected = { durationSec = it })
                                } else {
                                    WheelPicker(items = minsList, initialIndex = durationMin, modifier = Modifier.width(60.dp), itemToString = { "$it m" }, onItemSelected = { durationMin = it })
                                    Text(":", style = MaterialTheme.typography.titleLarge)
                                    WheelPicker(items = secsList, initialIndex = durationSec, modifier = Modifier.width(60.dp), itemToString = { "%02d s".format(it) }, onItemSelected = { durationSec = it })
                                }
                            }
                        }
                    }
                    LoggingType.CARDIO -> {
                        if (isHorizontal) {
                            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text("Duration (min)", style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
                                    HorizontalWheelPicker(items = minsList, initialIndex = durationMin, modifier = Modifier.fillMaxWidth(), onItemSelected = { durationMin = it })
                                }
                                Column(Modifier.fillMaxWidth()) {
                                    Text("Speed (km/h)", style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                        HorizontalWheelPicker(items = wholeNumbers, initialIndex = speedWhole, modifier = Modifier.weight(1f), onItemSelected = { speedWhole = it })
                                        Text(".", style = MaterialTheme.typography.titleMedium)
                                        HorizontalWheelPicker(items = decimalTenths, initialIndex = decimalTenths.indexOf(speedDecimal).takeIf { it >= 0 } ?: 0, modifier = Modifier.weight(1f), itemToString = { it.toString().substringAfter('.') }, onItemSelected = { speedDecimal = it })
                                    }
                                }
                                Column(Modifier.fillMaxWidth()) {
                                    Text("Distance (km)", style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                        HorizontalWheelPicker(items = wholeNumbers, initialIndex = distanceWhole, modifier = Modifier.weight(1f), onItemSelected = { distanceWhole = it })
                                        Text(".", style = MaterialTheme.typography.titleMedium)
                                        HorizontalWheelPicker(items = decimalTenths, initialIndex = decimalTenths.indexOf(distanceDecimal).takeIf { it >= 0 } ?: 0, modifier = Modifier.weight(1f), itemToString = { it.toString().substringAfter('.') }, onItemSelected = { distanceDecimal = it })
                                    }
                                }
                                Column(Modifier.fillMaxWidth()) {
                                    Text("Incline (%)", style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                        HorizontalWheelPicker(items = wholeNumbers, initialIndex = inclineWhole, modifier = Modifier.weight(1f), onItemSelected = { inclineWhole = it })
                                        Text(".", style = MaterialTheme.typography.titleMedium)
                                        HorizontalWheelPicker(items = decimalTenths, initialIndex = decimalTenths.indexOf(inclineDecimal).takeIf { it >= 0 } ?: 0, modifier = Modifier.weight(1f), itemToString = { it.toString().substringAfter('.') }, onItemSelected = { inclineDecimal = it })
                                    }
                                }
                            }
                        } else {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Duration (min)", style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
                                    WheelPicker(items = minsList, initialIndex = durationMin, modifier = Modifier.width(80.dp), onItemSelected = { durationMin = it })
                                }
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Speed (km/h)", style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                        WheelPicker(items = wholeNumbers, initialIndex = speedWhole, modifier = Modifier.width(56.dp), onItemSelected = { speedWhole = it })
                                        Text(".", style = MaterialTheme.typography.titleMedium)
                                        WheelPicker(items = decimalTenths, initialIndex = decimalTenths.indexOf(speedDecimal).takeIf { it >= 0 } ?: 0, modifier = Modifier.width(56.dp), itemToString = { it.toString().substringAfter('.') }, onItemSelected = { speedDecimal = it })
                                    }
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Distance (km)", style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                        WheelPicker(items = wholeNumbers, initialIndex = distanceWhole, modifier = Modifier.width(56.dp), onItemSelected = { distanceWhole = it })
                                        Text(".", style = MaterialTheme.typography.titleMedium)
                                        WheelPicker(items = decimalTenths, initialIndex = decimalTenths.indexOf(distanceDecimal).takeIf { it >= 0 } ?: 0, modifier = Modifier.width(56.dp), itemToString = { it.toString().substringAfter('.') }, onItemSelected = { distanceDecimal = it })
                                    }
                                }
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Incline (%)", style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                        WheelPicker(items = wholeNumbers, initialIndex = inclineWhole, modifier = Modifier.width(56.dp), onItemSelected = { inclineWhole = it })
                                        Text(".", style = MaterialTheme.typography.titleMedium)
                                        WheelPicker(items = decimalTenths, initialIndex = decimalTenths.indexOf(inclineDecimal).takeIf { it >= 0 } ?: 0, modifier = Modifier.width(56.dp), itemToString = { it.toString().substringAfter('.') }, onItemSelected = { inclineDecimal = it })
                                    }
                                }
                            }
                        }
                    }
                    }
                }

                // Log Button is always visible
                val logButtonLabel = when (loggingType) {
                    LoggingType.WEIGHTED_REPS -> "Log Set $currentSetNumber"
                    LoggingType.BODYWEIGHT_REPS -> "Log Set $currentSetNumber"
                    LoggingType.TIMED -> "Log Interval $currentSetNumber"
                    LoggingType.CARDIO -> "Log Cardio Session"
                }

                Button(
                    onClick = {
                        onLogSet(
                            weightWhole + weightDecimal,
                            repsInput,
                            durationMin * 60 + durationSec,
                            distanceWhole + distanceDecimal,
                            speedWhole + speedDecimal,
                            inclineWhole + inclineDecimal
                        )
                        if (inputStyle == "TAP_EXPAND") isExpanded = false
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Teal, contentColor = Color.White)
                ) {
                    Text(logButtonLabel, fontWeight = FontWeight.Bold)
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
