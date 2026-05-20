package com.trackme.ui.workout.session

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Size
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trackme.domain.model.Exercise
import com.trackme.domain.model.LoggingType
import com.trackme.domain.model.PlannedExercise
import com.trackme.domain.model.SessionSet
import com.trackme.domain.model.loggingType
import com.trackme.ui.components.GlassmorphicCard
import com.trackme.ui.components.HorizontalWheelPicker
import com.trackme.ui.components.MeshGradientPalette
import com.trackme.ui.components.ReactiveMeshGradient
import com.trackme.ui.components.WheelPicker
import com.trackme.ui.theme.Background
import com.trackme.ui.theme.Blue
import com.trackme.ui.theme.Coral
import com.trackme.ui.theme.Teal
import com.trackme.ui.theme.Violet
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

enum class ActivitySessionState {
    STARTING,
    WARMING_UP,
    IN_FLOW,
    RESTING,
    PEAK_PUSH,
    FINAL_STRETCH,
    COMPLETE,
}

private data class SessionProgress(
    val completedSets: Int,
    val totalSets: Int,
    val completedExercises: Int,
    val totalExercises: Int,
    val totalVolumeKg: Float,
) {
    val fraction: Float = if (totalSets == 0) 0f else completedSets / totalSets.toFloat()
    val percent: Int = (fraction * 100).roundToInt().coerceIn(0, 100)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ActiveSessionScreen(
    dayId: String,
    onSessionFinished: () -> Unit,
    onBack: () -> Unit,
    onExerciseClick: (exerciseId: String) -> Unit,
    onAddExercise: () -> Unit,
    viewModel: ActiveSessionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val progress = remember(state.exercises, state.loggedSets, state.loggedSetsByExercise) {
        state.toProgress()
    }
    val sessionState = remember(state.restTimerRunning, progress) {
        state.visualState(progress)
    }

    androidx.activity.compose.BackHandler(enabled = true) {
        if (state.isHistoricalSession) {
            viewModel.finishSession(onBack)
        } else {
            onBack()
        }
    }


    val handleBack = {
        if (state.isHistoricalSession) {
            viewModel.finishSession(onBack)
        } else {
            onBack()
        }
    }

    Box(Modifier.fillMaxSize()) {
        ReactiveMeshGradient(
            palette = sessionState.palette(),
            modifier = Modifier
                .matchParentSize()
                .zIndex(0f),
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .zIndex(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(start = 18.dp, top = 16.dp, end = 18.dp, bottom = 28.dp),
        ) {
            item {
                SessionHeader(
                    progress = progress,
                    sessionState = sessionState,
                    onBack = handleBack,
                )
            }

            if (state.restTimerRunning) {
                stickyHeader(key = "rest_timer_header") {
                    RestTimerCard(
                        secondsRemaining = state.restSecondsRemaining,
                        totalSeconds = state.restSeconds,
                        onSkip = viewModel::stopRestTimer,
                    )
                }
            }

            itemsIndexed(state.exercises, key = { _, item -> item.first.id }) { index, (planned, exercise) ->
                val loggedSets = state.loggedSetsByExercise[planned.exerciseId].orEmpty()
                val executionState = state.executionStates[planned.exerciseId] ?: ExerciseExecutionState.IDLE
                
                var showSwitchConfirmation by remember { mutableStateOf(false) }
                var showSkipRestConfirmation by remember { mutableStateOf(false) }
                var setTargetToEdit by remember { mutableStateOf<SessionSet?>(null) }
                val haptic = LocalHapticFeedback.current

                if (showSwitchConfirmation) {
                    SwitchExerciseConfirmationDialog(
                        onConfirm = {
                            showSwitchConfirmation = false
                            viewModel.cancelActiveSet(state.activeExerciseId ?: "")
                            viewModel.startExercise(planned.exerciseId)
                        },
                        onDismiss = { showSwitchConfirmation = false }
                    )
                }

                if (showSkipRestConfirmation) {
                    SkipRestConfirmationDialog(
                        onConfirm = {
                            showSkipRestConfirmation = false
                            viewModel.stopRestTimer()
                            viewModel.startExercise(planned.exerciseId)
                        },
                        onDismiss = { showSkipRestConfirmation = false }
                    )
                }

                if (setTargetToEdit != null) {
                    val set = setTargetToEdit!!
                    EditSetDialog(
                        set = set,
                        plannedExercise = planned,
                        exercise = exercise,
                        inputStyle = state.inputStyle,
                        onDismiss = { setTargetToEdit = null },
                        onSave = { weight, reps, duration, distance, speed, incline ->
                            viewModel.editSet(
                                setId = set.id,
                                exerciseId = planned.exerciseId,
                                setNumber = set.setNumber,
                                weightKg = weight,
                                reps = reps,
                                durationSeconds = duration,
                                distanceKm = distance,
                                speedKmh = speed,
                                inclinePercent = incline
                            )
                            setTargetToEdit = null
                        },
                        onDelete = {
                            viewModel.deleteSet(set)
                            setTargetToEdit = null
                        }
                    )
                }

                SessionExerciseCard(
                    index = index,
                    plannedExercise = planned,
                    exercise = exercise,
                    loggedSets = loggedSets,
                    executionState = executionState,
                    inputStyle = state.inputStyle,
                    isCompleted = state.isCompleted,
                    onStartExercise = {
                        if (state.activeExerciseId != null && state.activeExerciseId != planned.exerciseId) {
                            showSwitchConfirmation = true
                        } else if (state.restTimerRunning) {
                            showSkipRestConfirmation = true
                        } else {
                            viewModel.startExercise(planned.exerciseId)
                        }
                    },
                    onCompleteSet = { weight, reps, duration, distance, speed, incline ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.completeSet(
                            planned.exerciseId,
                            weight,
                            reps,
                            duration,
                            distance,
                            speed,
                            incline,
                        )
                    },
                    onDeleteSet = { viewModel.deleteSet(it) },
                    onEditSet = { set -> setTargetToEdit = set },
                    onSkipRest = { viewModel.skipRest(planned.exerciseId) },
                    onCancelActive = { viewModel.cancelActiveSet(planned.exerciseId) },
                    onExerciseClick = { exercise?.let { onExerciseClick(it.id) } },
                    onTargetSetsChanged = { viewModel.updateTargetSets(planned.exerciseId, it) },
                )
            }

            item {
                SessionActionsCard(
                    progress = progress,
                    isFinishing = state.isFinishing,
                    isCompleted = state.isCompleted,
                    isHistoricalSession = state.isHistoricalSession,
                    onAddExercise = onAddExercise,
                    onFinish = { viewModel.finishSession(onSessionFinished) },
                    onBack = handleBack,
                )
            }
        }
    }
}

@Composable
private fun SessionHeader(
    progress: SessionProgress,
    sessionState: ActivitySessionState,
    onBack: () -> Unit,
) {
    GlassmorphicCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f)),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        sessionState.title(),
                        style = MaterialTheme.typography.labelMedium,
                        color = sessionState.accent(),
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Activity Session",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(54.dp)) {
                    CircularProgressIndicator(
                        progress = { progress.fraction },
                        modifier = Modifier.matchParentSize(),
                        color = sessionState.accent(),
                        trackColor = Color.White.copy(alpha = 0.14f),
                        strokeWidth = 5.dp,
                    )
                    Text("${progress.percent}%", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }

            LinearProgressIndicator(
                progress = { progress.fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(CircleShape),
                color = sessionState.accent(),
                trackColor = Color.White.copy(alpha = 0.12f),
            )

            Text(
                "${progress.completedSets}/${progress.totalSets} sets | ${progress.completedExercises}/${progress.totalExercises} exercises | ${progress.totalVolumeKg.roundToInt()} kg volume",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.62f),
            )
        }
    }
}

@Composable
private fun RestTimerCard(secondsRemaining: Int, totalSeconds: Int, onSkip: () -> Unit) {
    val progress = if (totalSeconds <= 0) 0f else secondsRemaining / totalSeconds.toFloat()
    val minutes = secondsRemaining / 60
    val seconds = secondsRemaining % 60

    GlassmorphicCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = Color(0xFF08111F).copy(alpha = 0.42f),
        shape = RoundedCornerShape(22.dp),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(62.dp)) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.matchParentSize(),
                    color = Blue,
                    trackColor = Color.White.copy(alpha = 0.12f),
                    strokeWidth = 5.dp,
                )
                Text("%d:%02d".format(minutes, seconds), color = Color.White, fontWeight = FontWeight.ExtraBold)
            }
            Column(Modifier.weight(1f)) {
                Text("Rest", style = MaterialTheme.typography.labelMedium, color = Blue, fontWeight = FontWeight.Bold)
                Text("Recover, breathe, then hit the next set.", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.72f))
            }
            TextButton(onClick = onSkip) {
                Text("Skip", color = Color.White.copy(alpha = 0.78f), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SessionExerciseCard(
    index: Int,
    plannedExercise: PlannedExercise,
    exercise: Exercise?,
    loggedSets: List<SessionSet>,
    executionState: ExerciseExecutionState,
    inputStyle: String,
    isCompleted: Boolean,
    onStartExercise: () -> Unit,
    onCompleteSet: (weightKg: Float, reps: Int, durationSeconds: Int?, distanceKm: Float?, speedKmh: Float?, inclinePercent: Float?) -> Unit,
    onDeleteSet: (SessionSet) -> Unit,
    onEditSet: (SessionSet) -> Unit,
    onSkipRest: () -> Unit,
    onCancelActive: () -> Unit,
    onExerciseClick: () -> Unit,
    onTargetSetsChanged: (Int) -> Unit,
) {
    val loggingType = exercise?.loggingType() ?: LoggingType.WEIGHTED_REPS
    val isDone = executionState == ExerciseExecutionState.COMPLETED
    val isActive = executionState == ExerciseExecutionState.ACTIVE_SET
    
    val targetColor by animateColorAsState(
        targetValue = when (executionState) {
            ExerciseExecutionState.COMPLETED -> Teal
            ExerciseExecutionState.ACTIVE_SET -> Coral
            ExerciseExecutionState.RESTING -> Blue
            else -> Color.White.copy(alpha = 0.08f)
        },
        label = "cardAccent",
    )
    
    val progress by animateFloatAsState(
        targetValue = (loggedSets.size / plannedExercise.targetSets.toFloat()).coerceIn(0f, 1f),
        label = "exerciseProgress",
    )

    GlassmorphicCard(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ),
        containerColor = when (executionState) {
            ExerciseExecutionState.COMPLETED -> Teal.copy(alpha = 0.13f)
            ExerciseExecutionState.ACTIVE_SET -> Color.White.copy(alpha = 0.11f)
            ExerciseExecutionState.RESTING -> Blue.copy(alpha = 0.1f)
            else -> Color.White.copy(alpha = 0.045f)
        },
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            Modifier
                .border(
                    width = if (isActive) 1.5.dp else 1.dp,
                    color = if (isActive || isDone) targetColor.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(24.dp),
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(targetColor.copy(alpha = if (isDone || isActive) 0.92f else 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isDone) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                    } else {
                        Text("${index + 1}", color = Color.White, fontWeight = FontWeight.ExtraBold)
                    }
                }

                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            exercise?.name ?: plannedExercise.exerciseId,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        ExecutionStatusPill(executionState)
                    }
                    Text(
                        exercise?.let { "${it.primaryMuscles.joinToString(", ")} | ${it.equipment}" }
                            ?: loggingType.displayName.cleanText(),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                IconButton(onClick = onExerciseClick, enabled = exercise != null) {
                    Icon(Icons.Default.Info, contentDescription = "Exercise info", tint = Color.White.copy(alpha = 0.62f))
                }
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = when (executionState) {
                    ExerciseExecutionState.COMPLETED -> Teal
                    ExerciseExecutionState.RESTING -> Blue
                    else -> Coral
                },
                trackColor = Color.White.copy(alpha = 0.12f),
            )

            when (executionState) {
                ExerciseExecutionState.IDLE -> {
                    IdleExerciseUI(
                        loggedSets = loggedSets,
                        targetSets = plannedExercise.targetSets,
                        loggingType = loggingType,
                        onStart = onStartExercise,
                        onTargetSetsChanged = onTargetSetsChanged,
                        onEditSet = onEditSet,
                    )
                }
                ExerciseExecutionState.ACTIVE_SET -> {
                    ActiveExerciseUI(
                        plannedExercise = plannedExercise,
                        exercise = exercise,
                        loggedSets = loggedSets,
                        inputStyle = inputStyle,
                        onCompleteSet = onCompleteSet,
                        onCancel = onCancelActive
                    )
                }
                ExerciseExecutionState.RESTING -> {
                    RestingExerciseUI(
                        loggedSets = loggedSets,
                        targetSets = plannedExercise.targetSets,
                        onSkipRest = onSkipRest
                    )
                }
                ExerciseExecutionState.COMPLETED -> {
                    CompletedExerciseUI(
                        loggedSets = loggedSets, 
                        loggingType = loggingType,
                        targetSets = plannedExercise.targetSets,
                        onTargetSetsChanged = onTargetSetsChanged,
                        onEditSet = onEditSet,
                        isCompleted = isCompleted,
                    )
                }
            }
        }
    }
}

@Composable
private fun IdleExerciseUI(
    loggedSets: List<SessionSet>,
    targetSets: Int,
    loggingType: LoggingType,
    onStart: () -> Unit,
    onTargetSetsChanged: (Int) -> Unit,
    onEditSet: (SessionSet) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SetHistory(
            loggedSets = loggedSets,
            loggingType = loggingType,
            targetSets = targetSets,
            onTargetSetsChanged = onTargetSetsChanged,
            onEditSet = onEditSet,
        )
        
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Coral),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Start Set ${loggedSets.size + 1}", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ActiveExerciseUI(
    plannedExercise: PlannedExercise,
    exercise: Exercise?,
    loggedSets: List<SessionSet>,
    inputStyle: String,
    onCompleteSet: (weightKg: Float, reps: Int, durationSeconds: Int?, distanceKm: Float?, speedKmh: Float?, inclinePercent: Float?) -> Unit,
    onCancel: () -> Unit
) {
    val loggingType = exercise?.loggingType() ?: LoggingType.WEIGHTED_REPS
    
    // Auto-logging for timed exercises
    if (loggingType == LoggingType.TIMED) {
        val targetSeconds = plannedExercise.targetDurationSeconds ?: 60
        var secondsLeft by remember(plannedExercise.exerciseId, loggedSets.size) { mutableIntStateOf(targetSeconds) }
        
        LaunchedEffect(secondsLeft) {
            if (secondsLeft > 0) {
                delay(1000)
                secondsLeft--
            } else {
                onCompleteSet(0f, 0, targetSeconds, null, null, null)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black.copy(alpha = 0.22f))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Set ${loggedSets.size + 1} - Countdown",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold
                )
                IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Block, contentDescription = "Cancel", tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                }
            }

            Box(contentAlignment = Alignment.Center) {
                ExerciseExecutionAnimation(loggingType = loggingType, showIcon = false)
                Text(
                    "%d:%02d".format(secondsLeft / 60, secondsLeft % 60),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Button(
                onClick = { onCompleteSet(0f, 0, targetSeconds - secondsLeft, null, null, null) },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Coral),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("Finish Early", fontWeight = FontWeight.ExtraBold)
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black.copy(alpha = 0.22f))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Set ${loggedSets.size + 1} in Progress",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold
                )
                IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Block, contentDescription = "Cancel", tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                }
            }

            ExerciseExecutionAnimation(loggingType = loggingType)

            IntegratedSetLogger(
                plannedExercise = plannedExercise,
                exercise = exercise,
                loggedSets = loggedSets,
                inputStyle = inputStyle,
                onLogSet = onCompleteSet,
                buttonLabel = "Complete Set ${loggedSets.size + 1}"
            )
        }
    }
}

@Composable
private fun RestingExerciseUI(
    loggedSets: List<SessionSet>,
    targetSets: Int,
    onSkipRest: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Blue.copy(alpha = 0.15f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Timer, contentDescription = null, tint = Blue, modifier = Modifier.size(32.dp))
        Text("Resting...", color = Color.White, fontWeight = FontWeight.Bold)
        Text(
            "Nice work on Set ${loggedSets.size}! Get ready for Set ${loggedSets.size + 1}.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        OutlinedButton(
            onClick = onSkipRest,
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, Blue.copy(alpha = 0.5f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Blue)
        ) {
            Text("Skip Rest", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CompletedExerciseUI(
    loggedSets: List<SessionSet>, 
    loggingType: LoggingType,
    targetSets: Int,
    onTargetSetsChanged: (Int) -> Unit,
    onEditSet: (SessionSet) -> Unit,
    isCompleted: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Teal.copy(alpha = 0.1f))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Teal, modifier = Modifier.size(32.dp))
        Text("Exercise Completed!", color = Teal, fontWeight = FontWeight.ExtraBold)
        Text(
            "Total Volume: ${loggedSets.sumOf { (it.weightKg * it.reps).toDouble() }.toInt()} kg",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.6f)
        )
        
        Spacer(Modifier.height(4.dp))
        
        SetHistory(
            loggedSets = loggedSets,
            loggingType = loggingType,
            targetSets = targetSets,
            onTargetSetsChanged = onTargetSetsChanged,
            onEditSet = onEditSet,
            isCompleted = isCompleted,
        )
    }
}

@Composable
private fun ExerciseExecutionAnimation(loggingType: LoggingType, showIcon: Boolean = true) {
    val infiniteTransition = rememberInfiniteTransition(label = "exerciseAnim")
    
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val rotate by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing)
        ),
        label = "rotate"
    )

    Box(
        modifier = Modifier.size(120.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension / 2.2f
            
            // Background ring
            drawCircle(
                color = Color.White.copy(alpha = 0.05f),
                radius = radius,
                style = Stroke(width = 8.dp.toPx())
            )
            
            // Accent ring
            drawArc(
                color = Coral,
                startAngle = rotate,
                sweepAngle = 90f,
                useCenter = false,
                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round),
                topLeft = Offset(center.x - radius, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
            )
        }

        if (showIcon) {
            Icon(
                imageVector = when (loggingType) {
                    LoggingType.WEIGHTED_REPS, LoggingType.BODYWEIGHT_REPS -> Icons.Default.FitnessCenter
                    LoggingType.TIMED -> Icons.Default.Timer
                    else -> Icons.Default.PlayArrow
                },
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .graphicsLayer(scaleX = pulse, scaleY = pulse),
                tint = Color.White
            )
        }
    }
}

@Composable
private fun ExecutionStatusPill(state: ExerciseExecutionState) {
    val (text, color) = when (state) {
        ExerciseExecutionState.COMPLETED -> "Done" to Teal
        ExerciseExecutionState.ACTIVE_SET -> "Active" to Coral
        ExerciseExecutionState.RESTING -> "Rest" to Blue
        else -> "Next" to Color.White.copy(alpha = 0.28f)
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = if (state != ExerciseExecutionState.IDLE) 0.22f else 0.16f),
        border = BorderStroke(1.dp, color.copy(alpha = if (state != ExerciseExecutionState.IDLE) 0.55f else 0.2f)),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (state != ExerciseExecutionState.IDLE) color else Color.White.copy(alpha = 0.58f),
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SwitchExerciseConfirmationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Switch Exercise?") },
        text = { Text("You have an active set in progress. Starting a new exercise will cancel the current active set. Continue?") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Confirm", color = Coral, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White.copy(alpha = 0.6f))
            }
        },
        containerColor = Color(0xFF1A1C1E),
        titleContentColor = Color.White,
        textContentColor = Color.White.copy(alpha = 0.7f)
    )
}

@Composable
private fun SkipRestConfirmationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rest in Progress") },
        text = { Text("You are currently resting. Starting this exercise will skip the rest timer. Continue?") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Skip & Start", color = Coral, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Keep Resting", color = Color.White.copy(alpha = 0.6f))
            }
        },
        containerColor = Color(0xFF1A1C1E),
        titleContentColor = Color.White,
        textContentColor = Color.White.copy(alpha = 0.7f)
    )
}


@Composable
private fun SetHistory(
    loggedSets: List<SessionSet>,
    loggingType: LoggingType,
    targetSets: Int,
    onTargetSetsChanged: (Int) -> Unit,
    onEditSet: (SessionSet) -> Unit,
    isCompleted: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (loggedSets.isEmpty()) {
            Text("No sets logged yet", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.48f))
        } else {
            if (!isCompleted) {
                Text(
                    "Tap a set to edit or delete",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
            loggedSets.forEachIndexed { index, set ->
                val rowModifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.15f))
                
                val finalModifier = if (isCompleted) {
                    rowModifier
                } else {
                    rowModifier.clickable { onEditSet(set) }
                }
                
                Row(
                    modifier = finalModifier.padding(vertical = 8.dp, horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Set ${index + 1}", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.56f))
                    Text(setLabel(set, loggingType), style = MaterialTheme.typography.labelMedium, color = Teal, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (isCompleted) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${loggedSets.size} sets completed",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${loggedSets.size}/$targetSets sets",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.weight(1f),
                )
                AssistChip(
                    onClick = { if (targetSets > maxOf(loggedSets.size, 1)) onTargetSetsChanged(targetSets - 1) },
                    enabled = targetSets > maxOf(loggedSets.size, 1),
                    label = { Text("- Set") },
                )
                AssistChip(
                    onClick = { onTargetSetsChanged(targetSets + 1) },
                    label = { Text("+ Set") },
                )
            }
        }
    }
}

@Composable
private fun EditSetDialog(
    set: SessionSet,
    plannedExercise: PlannedExercise,
    exercise: Exercise?,
    inputStyle: String,
    onDismiss: () -> Unit,
    onSave: (weightKg: Float, reps: Int, durationSeconds: Int?, distanceKm: Float?, speedKmh: Float?, inclinePercent: Float?) -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Background,
        title = { Text("Edit Set ${set.setNumber}", color = Color.White) },
        text = {
            IntegratedSetLogger(
                plannedExercise = plannedExercise,
                exercise = exercise,
                loggedSets = emptyList(), // Passing empty list so currentSetNumber logic doesn't matter (we use initialSetToEdit)
                inputStyle = inputStyle,
                buttonLabel = "Save Changes",
                initialSetToEdit = set,
                onLogSet = onSave
            )
        },
        confirmButton = {},
        dismissButton = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Coral.copy(alpha = 0.8f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Coral),
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Set",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Delete",
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f)),
                ) {
                    Text(
                        "Cancel",
                        color = Color.White.copy(alpha = 0.88f),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    )
}

@Composable
private fun IntegratedSetLogger(
    plannedExercise: PlannedExercise,
    exercise: Exercise?,
    loggedSets: List<SessionSet>,
    inputStyle: String,
    buttonLabel: String = "Log Set",
    initialSetToEdit: SessionSet? = null,
    onLogSet: (weightKg: Float, reps: Int, durationSeconds: Int?, distanceKm: Float?, speedKmh: Float?, inclinePercent: Float?) -> Unit,
) {
    val loggingType = exercise?.loggingType() ?: LoggingType.WEIGHTED_REPS
    val currentSetNumber = initialSetToEdit?.setNumber ?: (loggedSets.size + 1)

    val wholeNumbers = remember { (0..300).toList() }
    val decimalWeightOptions = remember { listOf(0.0f, 0.25f, 0.5f, 0.75f) }
    val decimalTenths = remember { (0..9).map { it / 10f } }
    val repsList = remember { (0..100).toList() }
    val minsList = remember { (0..120).toList() }
    val secsList = remember { (0..59).toList() }

    val initialWeight = initialSetToEdit?.weightKg ?: plannedExercise.targetWeightKg ?: loggedSets.lastOrNull()?.weightKg ?: 0f
    var weightWhole by remember(plannedExercise.exerciseId, initialSetToEdit) { mutableIntStateOf(initialWeight.toInt()) }
    var weightDecimal by remember(plannedExercise.exerciseId, initialSetToEdit) {
        mutableFloatStateOf(decimalWeightOptions.minByOrNull { abs(it - (initialWeight - initialWeight.toInt())) } ?: 0f)
    }
    var repsInput by remember(plannedExercise.exerciseId, initialSetToEdit) {
        mutableIntStateOf(initialSetToEdit?.reps ?: plannedExercise.targetReps ?: loggedSets.lastOrNull()?.reps ?: 0)
    }

    val initialDuration = initialSetToEdit?.durationSeconds ?: plannedExercise.targetDurationSeconds ?: loggedSets.lastOrNull()?.durationSeconds ?: 0
    var durationMin by remember(plannedExercise.exerciseId, initialSetToEdit) { mutableIntStateOf(initialDuration / 60) }
    var durationSec by remember(plannedExercise.exerciseId, initialSetToEdit) { mutableIntStateOf(initialDuration % 60) }

    val initialDistance = initialSetToEdit?.distanceKm ?: plannedExercise.targetDistanceKm ?: loggedSets.lastOrNull()?.distanceKm ?: 0f
    var distanceWhole by remember(plannedExercise.exerciseId, initialSetToEdit) { mutableIntStateOf(initialDistance.toInt()) }
    var distanceDecimal by remember(plannedExercise.exerciseId, initialSetToEdit) {
        mutableFloatStateOf(decimalTenths.minByOrNull { abs(it - (initialDistance - initialDistance.toInt())) } ?: 0f)
    }

    val initialSpeed = initialSetToEdit?.speedKmh ?: plannedExercise.targetSpeedKmh ?: loggedSets.lastOrNull()?.speedKmh ?: 0f
    var speedWhole by remember(plannedExercise.exerciseId, initialSetToEdit) { mutableIntStateOf(initialSpeed.toInt()) }
    var speedDecimal by remember(plannedExercise.exerciseId, initialSetToEdit) {
        mutableFloatStateOf(decimalTenths.minByOrNull { abs(it - (initialSpeed - initialSpeed.toInt())) } ?: 0f)
    }

    val initialIncline = initialSetToEdit?.inclinePercent ?: plannedExercise.targetIncline ?: loggedSets.lastOrNull()?.inclinePercent ?: 0f
    var inclineWhole by remember(plannedExercise.exerciseId, initialSetToEdit) { mutableIntStateOf(initialIncline.toInt()) }
    var inclineDecimal by remember(plannedExercise.exerciseId, initialSetToEdit) {
        mutableFloatStateOf(decimalTenths.minByOrNull { abs(it - (initialIncline - initialIncline.toInt())) } ?: 0f)
    }

    val isTapToExpand = inputStyle == "TAP_EXPAND"
    val isHorizontal = inputStyle == "HORIZONTAL"
    var isExpanded by remember(plannedExercise.exerciseId, initialSetToEdit) { mutableStateOf(!isTapToExpand || initialSetToEdit != null) }
    LaunchedEffect(inputStyle, plannedExercise.exerciseId, initialSetToEdit) {
        isExpanded = inputStyle != "TAP_EXPAND" || initialSetToEdit != null
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.Black.copy(alpha = 0.18f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Log ${loggingType.actionLabel()} $currentSetNumber",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
            )

            if (!isExpanded) {
                OutlinedButton(
                    onClick = { isExpanded = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Violet.copy(alpha = 0.42f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            loggerSummary(
                                loggingType = loggingType,
                                weightKg = weightWhole + weightDecimal,
                                reps = repsInput,
                                durationSeconds = durationMin * 60 + durationSec,
                                distanceKm = distanceWhole + distanceDecimal,
                                speedKmh = speedWhole + speedDecimal,
                                inclinePercent = inclineWhole + inclineDecimal,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.86f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Default.Edit, contentDescription = "Edit input", tint = Violet, modifier = Modifier.size(18.dp))
                    }
                }
            } else {
                when (loggingType) {
                    LoggingType.WEIGHTED_REPS -> {
                        if (isHorizontal) {
                            LoggerRow {
                                LoggerPicker(label = "Weight", value = "${weightWhole + weightDecimal} kg", modifier = Modifier.weight(1.2f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        HorizontalWheelPicker(items = wholeNumbers, initialIndex = weightWhole, modifier = Modifier.weight(1f), onItemSelected = { weightWhole = it })
                                        Text(".", color = Color.White, style = MaterialTheme.typography.titleLarge)
                                        HorizontalWheelPicker(
                                            items = decimalWeightOptions,
                                            initialIndex = decimalWeightOptions.indexOf(weightDecimal).takeIf { it >= 0 } ?: 0,
                                            modifier = Modifier.weight(1f),
                                            itemToString = { it.toString().substringAfter('.') },
                                            onItemSelected = { weightDecimal = it },
                                        )
                                    }
                                }
                                LoggerPicker(label = "Reps", value = repsInput.toString(), modifier = Modifier.weight(1f)) {
                                    HorizontalWheelPicker(items = repsList, initialIndex = repsInput, modifier = Modifier.fillMaxWidth(), onItemSelected = { repsInput = it })
                                }
                            }
                        } else {
                            LoggerRow {
                                LoggerPicker(label = "Weight", value = "${weightWhole + weightDecimal} kg", modifier = Modifier.weight(1.2f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        WheelPicker(items = wholeNumbers, initialIndex = weightWhole, modifier = Modifier.width(62.dp), onItemSelected = { weightWhole = it })
                                        Text(".", color = Color.White, style = MaterialTheme.typography.titleLarge)
                                        WheelPicker(
                                            items = decimalWeightOptions,
                                            initialIndex = decimalWeightOptions.indexOf(weightDecimal).takeIf { it >= 0 } ?: 0,
                                            modifier = Modifier.width(62.dp),
                                            itemToString = { it.toString().substringAfter('.') },
                                            onItemSelected = { weightDecimal = it },
                                        )
                                    }
                                }
                                LoggerPicker(label = "Reps", value = repsInput.toString(), modifier = Modifier.weight(1f)) {
                                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                        WheelPicker(items = repsList, initialIndex = repsInput, modifier = Modifier.width(82.dp), onItemSelected = { repsInput = it })
                                    }
                                }
                            }
                        }
                    }
                    LoggingType.BODYWEIGHT_REPS -> {
                        LoggerPicker(label = "Reps", value = repsInput.toString()) {
                            if (isHorizontal) {
                                HorizontalWheelPicker(items = repsList, initialIndex = repsInput, modifier = Modifier.fillMaxWidth(), onItemSelected = { repsInput = it })
                            } else {
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    WheelPicker(items = repsList, initialIndex = repsInput, modifier = Modifier.width(82.dp), onItemSelected = { repsInput = it })
                                }
                            }
                        }
                    }
                    LoggingType.TIMED -> {
                        LoggerPicker(label = "Duration", value = "%d:%02d".format(durationMin, durationSec)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (isHorizontal) {
                                    HorizontalWheelPicker(items = minsList, initialIndex = durationMin, modifier = Modifier.weight(1f), itemToString = { "$it m" }, onItemSelected = { durationMin = it })
                                } else {
                                    WheelPicker(items = minsList, initialIndex = durationMin, modifier = Modifier.width(70.dp), itemToString = { "$it m" }, onItemSelected = { durationMin = it })
                                }
                                Text(":", color = Color.White, style = MaterialTheme.typography.titleLarge)
                                if (isHorizontal) {
                                    HorizontalWheelPicker(items = secsList, initialIndex = durationSec, modifier = Modifier.weight(1f), itemToString = { "%02d s".format(it) }, onItemSelected = { durationSec = it })
                                } else {
                                    WheelPicker(items = secsList, initialIndex = durationSec, modifier = Modifier.width(78.dp), itemToString = { "%02d s".format(it) }, onItemSelected = { durationSec = it })
                                }
                            }
                        }
                    }
                    LoggingType.CARDIO -> {
                        LoggerRow {
                            LoggerPicker(label = "Duration", value = "$durationMin min", modifier = Modifier.weight(1f)) {
                                if (isHorizontal) {
                                    HorizontalWheelPicker(items = minsList, initialIndex = durationMin, modifier = Modifier.fillMaxWidth(), onItemSelected = { durationMin = it })
                                } else {
                                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                        WheelPicker(items = minsList, initialIndex = durationMin, modifier = Modifier.width(80.dp), onItemSelected = { durationMin = it })
                                    }
                                }
                            }
                            LoggerPicker(label = "Speed", value = "${speedWhole + speedDecimal}", modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (isHorizontal) {
                                        HorizontalWheelPicker(items = wholeNumbers, initialIndex = speedWhole, modifier = Modifier.weight(1f), onItemSelected = { speedWhole = it })
                                    } else {
                                        WheelPicker(items = wholeNumbers, initialIndex = speedWhole, modifier = Modifier.width(56.dp), onItemSelected = { speedWhole = it })
                                    }
                                    Text(".", color = Color.White, style = MaterialTheme.typography.titleLarge)
                                    if (isHorizontal) {
                                        HorizontalWheelPicker(
                                            items = decimalTenths,
                                            initialIndex = decimalTenths.indexOf(speedDecimal).takeIf { it >= 0 } ?: 0,
                                            modifier = Modifier.weight(1f),
                                            itemToString = { it.toString().substringAfter('.') },
                                            onItemSelected = { speedDecimal = it },
                                        )
                                    } else {
                                        WheelPicker(
                                            items = decimalTenths,
                                            initialIndex = decimalTenths.indexOf(speedDecimal).takeIf { it >= 0 } ?: 0,
                                            modifier = Modifier.width(56.dp),
                                            itemToString = { it.toString().substringAfter('.') },
                                            onItemSelected = { speedDecimal = it },
                                        )
                                    }
                                }
                            }
                        }
                        LoggerRow {
                            LoggerPicker(label = "Distance", value = "${distanceWhole + distanceDecimal} km", modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (isHorizontal) {
                                        HorizontalWheelPicker(items = wholeNumbers, initialIndex = distanceWhole, modifier = Modifier.weight(1f), onItemSelected = { distanceWhole = it })
                                    } else {
                                        WheelPicker(items = wholeNumbers, initialIndex = distanceWhole, modifier = Modifier.width(56.dp), onItemSelected = { distanceWhole = it })
                                    }
                                    Text(".", color = Color.White, style = MaterialTheme.typography.titleLarge)
                                    if (isHorizontal) {
                                        HorizontalWheelPicker(
                                            items = decimalTenths,
                                            initialIndex = decimalTenths.indexOf(distanceDecimal).takeIf { it >= 0 } ?: 0,
                                            modifier = Modifier.weight(1f),
                                            itemToString = { it.toString().substringAfter('.') },
                                            onItemSelected = { distanceDecimal = it },
                                        )
                                    } else {
                                        WheelPicker(
                                            items = decimalTenths,
                                            initialIndex = decimalTenths.indexOf(distanceDecimal).takeIf { it >= 0 } ?: 0,
                                            modifier = Modifier.width(56.dp),
                                            itemToString = { it.toString().substringAfter('.') },
                                            onItemSelected = { distanceDecimal = it },
                                        )
                                    }
                                }
                            }
                            LoggerPicker(label = "Incline", value = "${inclineWhole + inclineDecimal}%", modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (isHorizontal) {
                                        HorizontalWheelPicker(items = wholeNumbers, initialIndex = inclineWhole, modifier = Modifier.weight(1f), onItemSelected = { inclineWhole = it })
                                    } else {
                                        WheelPicker(items = wholeNumbers, initialIndex = inclineWhole, modifier = Modifier.width(56.dp), onItemSelected = { inclineWhole = it })
                                    }
                                    Text(".", color = Color.White, style = MaterialTheme.typography.titleLarge)
                                    if (isHorizontal) {
                                        HorizontalWheelPicker(
                                            items = decimalTenths,
                                            initialIndex = decimalTenths.indexOf(inclineDecimal).takeIf { it >= 0 } ?: 0,
                                            modifier = Modifier.weight(1f),
                                            itemToString = { it.toString().substringAfter('.') },
                                            onItemSelected = { inclineDecimal = it },
                                        )
                                    } else {
                                        WheelPicker(
                                            items = decimalTenths,
                                            initialIndex = decimalTenths.indexOf(inclineDecimal).takeIf { it >= 0 } ?: 0,
                                            modifier = Modifier.width(56.dp),
                                            itemToString = { it.toString().substringAfter('.') },
                                            onItemSelected = { inclineDecimal = it },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    onLogSet(
                        weightWhole + weightDecimal,
                        repsInput,
                        durationMin * 60 + durationSec,
                        distanceWhole + distanceDecimal,
                        speedWhole + speedDecimal,
                        inclineWhole + inclineDecimal,
                    )
                    if (isTapToExpand) isExpanded = false
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Coral, contentColor = Color.White),
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(buttonLabel, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun LoggerRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

@Composable
private fun LoggerPicker(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.52f))
            Text(value, style = MaterialTheme.typography.labelSmall, color = Teal, fontWeight = FontWeight.Bold)
        }
        content()
    }
}

@Composable
private fun SessionActionsCard(
    progress: SessionProgress,
    isFinishing: Boolean,
    isCompleted: Boolean,
    isHistoricalSession: Boolean,
    onAddExercise: () -> Unit,
    onFinish: () -> Unit,
    onBack: () -> Unit,
) {
    GlassmorphicCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (isCompleted || isHistoricalSession) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onAddExercise,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add")
                    }
                    Button(
                        onClick = onBack,
                        modifier = Modifier
                            .weight(1.35f)
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Teal, contentColor = Color.White),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Go Back", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onAddExercise,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add")
                    }
                    Button(
                        onClick = onFinish,
                        enabled = !isFinishing,
                        modifier = Modifier
                            .weight(1.35f)
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Teal, contentColor = Color.White),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Finish Workout", fontWeight = FontWeight.Bold)
                    }
                }
            }
            Text(
                if (isCompleted) "Workout session completed!" else if (isHistoricalSession) "Editing historical workout session" else "${progress.completedSets} sets logged so far",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.58f),
            )
        }
    }
}

private fun ActiveSessionUiState.toProgress(): SessionProgress {
    val totalSets = exercises.sumOf { it.first.targetSets }
    val completedSets = loggedSetsByExercise.values.sumOf { it.size }
    val completedExercises = exercises.count { (planned, _) ->
        loggedSetsByExercise[planned.exerciseId].orEmpty().size >= planned.targetSets
    }
    val totalVolume = loggedSets.sumOf { (it.weightKg * it.reps).toDouble() }.toFloat()
    return SessionProgress(
        completedSets = completedSets,
        totalSets = totalSets,
        completedExercises = completedExercises,
        totalExercises = exercises.size,
        totalVolumeKg = totalVolume,
    )
}

private fun ActiveSessionUiState.visualState(progress: SessionProgress): ActivitySessionState = when {
    progress.totalSets > 0 && progress.completedSets >= progress.totalSets -> ActivitySessionState.COMPLETE
    restTimerRunning -> ActivitySessionState.RESTING
    progress.completedSets == 0 -> ActivitySessionState.STARTING
    progress.fraction < 0.22f -> ActivitySessionState.WARMING_UP
    progress.fraction > 0.82f -> ActivitySessionState.FINAL_STRETCH
    progress.fraction > 0.55f -> ActivitySessionState.PEAK_PUSH
    else -> ActivitySessionState.IN_FLOW
}

private fun ActivitySessionState.palette(): MeshGradientPalette = when (this) {
    ActivitySessionState.STARTING -> MeshGradientPalette(
        primary = Violet.copy(alpha = 0.45f),
        secondary = Blue.copy(alpha = 0.35f),
        tertiary = Teal.copy(alpha = 0.22f),
    )
    ActivitySessionState.WARMING_UP -> MeshGradientPalette(
        primary = Violet.copy(alpha = 0.58f),
        secondary = Blue.copy(alpha = 0.42f),
        tertiary = Coral.copy(alpha = 0.24f),
    )
    ActivitySessionState.IN_FLOW -> MeshGradientPalette(
        primary = Teal.copy(alpha = 0.48f),
        secondary = Blue.copy(alpha = 0.5f),
        tertiary = Violet.copy(alpha = 0.34f),
    )
    ActivitySessionState.RESTING -> MeshGradientPalette(
        primary = Blue.copy(alpha = 0.5f),
        secondary = Violet.copy(alpha = 0.34f),
        tertiary = Teal.copy(alpha = 0.18f),
        background = Color(0xFF09111D),
    )
    ActivitySessionState.PEAK_PUSH -> MeshGradientPalette(
        primary = Coral.copy(alpha = 0.7f),
        secondary = Color(0xFFFF5722).copy(alpha = 0.44f),
        tertiary = Violet.copy(alpha = 0.36f),
    )
    ActivitySessionState.FINAL_STRETCH -> MeshGradientPalette(
        primary = Teal.copy(alpha = 0.56f),
        secondary = Color(0xFFFFD54F).copy(alpha = 0.34f),
        tertiary = Coral.copy(alpha = 0.34f),
    )
    ActivitySessionState.COMPLETE -> MeshGradientPalette(
        primary = Teal.copy(alpha = 0.62f),
        secondary = Color(0xFFFFD700).copy(alpha = 0.4f),
        tertiary = Blue.copy(alpha = 0.34f),
    )
}

private fun ActivitySessionState.title(): String = when (this) {
    ActivitySessionState.STARTING -> "Lock in"
    ActivitySessionState.WARMING_UP -> "Warming up"
    ActivitySessionState.IN_FLOW -> "In flow"
    ActivitySessionState.RESTING -> "Recovering"
    ActivitySessionState.PEAK_PUSH -> "Peak push"
    ActivitySessionState.FINAL_STRETCH -> "Final stretch"
    ActivitySessionState.COMPLETE -> "Workout complete"
}

private fun ActivitySessionState.accent(): Color = when (this) {
    ActivitySessionState.RESTING -> Blue
    ActivitySessionState.FINAL_STRETCH,
    ActivitySessionState.COMPLETE -> Color(0xFFFFD54F)
    ActivitySessionState.PEAK_PUSH -> Coral
    else -> Teal
}

private fun setLabel(set: SessionSet, loggingType: LoggingType): String = when (loggingType) {
    LoggingType.WEIGHTED_REPS -> "${set.weightKg}kg x ${set.reps}"
    LoggingType.BODYWEIGHT_REPS -> "${set.reps} reps"
    LoggingType.TIMED -> set.durationSeconds?.let { "%d:%02d".format(it / 60, it % 60) } ?: "${set.reps}s"
    LoggingType.CARDIO -> buildString {
        set.durationSeconds?.let { append("${it / 60}min") }
        set.speedKmh?.let { if (isNotEmpty()) append(" | "); append("${it}km/h") }
        set.distanceKm?.let { if (isNotEmpty()) append(" | "); append("${it}km") }
    }.ifEmpty { "done" }
}

private fun loggerSummary(
    loggingType: LoggingType,
    weightKg: Float,
    reps: Int,
    durationSeconds: Int,
    distanceKm: Float,
    speedKmh: Float,
    inclinePercent: Float,
): String = when (loggingType) {
    LoggingType.WEIGHTED_REPS -> "Current: ${weightKg}kg x $reps reps"
    LoggingType.BODYWEIGHT_REPS -> "Current: $reps reps"
    LoggingType.TIMED -> "Current: %d:%02d".format(durationSeconds / 60, durationSeconds % 60)
    LoggingType.CARDIO -> "Current: ${durationSeconds / 60}min | ${distanceKm}km | ${speedKmh}km/h | ${inclinePercent}%"
}

private fun LoggingType.actionLabel(): String = when (this) {
    LoggingType.WEIGHTED_REPS,
    LoggingType.BODYWEIGHT_REPS -> "Set"
    LoggingType.TIMED -> "Interval"
    LoggingType.CARDIO -> "Cardio"
}

private fun String.cleanText(): String = replace("\u00C2\u00B7", "|").replace("\u00B7", "|")
