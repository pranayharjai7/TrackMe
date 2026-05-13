package com.trackme.ui.workout.session

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
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

    val defaultActiveId = remember(state.exercises, state.loggedSetsByExercise) {
        state.exercises.firstOrNull { (planned, _) ->
            state.loggedSetsByExercise[planned.exerciseId].orEmpty().size < planned.targetSets
        }?.first?.exerciseId ?: state.exercises.lastOrNull()?.first?.exerciseId
    }
    var activeExerciseId by rememberSaveable(dayId) { mutableStateOf<String?>(null) }

    LaunchedEffect(defaultActiveId, state.loggedSetsByExercise) {
        val activePlanned = state.exercises.firstOrNull { it.first.exerciseId == activeExerciseId }?.first
        val activeIsDone = activePlanned != null &&
            state.loggedSetsByExercise[activePlanned.exerciseId].orEmpty().size >= activePlanned.targetSets
        if (activeExerciseId == null ||
            state.exercises.none { it.first.exerciseId == activeExerciseId } ||
            (activeIsDone && defaultActiveId != activeExerciseId)
        ) {
            activeExerciseId = defaultActiveId
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
                    onBack = onBack,
                )
            }

            if (state.restTimerRunning) {
                item {
                    RestTimerCard(
                        secondsRemaining = state.restSecondsRemaining,
                        totalSeconds = state.restSeconds,
                        onSkip = viewModel::stopRestTimer,
                    )
                }
            }

            itemsIndexed(state.exercises, key = { _, item -> item.first.id }) { index, (planned, exercise) ->
                val loggedSets = state.loggedSetsByExercise[planned.exerciseId].orEmpty()
                val isDone = loggedSets.size >= planned.targetSets
                val isCurrent = planned.exerciseId == activeExerciseId && !isDone

                SessionExerciseCard(
                    index = index,
                    plannedExercise = planned,
                    exercise = exercise,
                    loggedSets = loggedSets,
                    isCurrent = isCurrent,
                    isDone = isDone,
                    inputStyle = state.inputStyle,
                    onSelect = { activeExerciseId = planned.exerciseId },
                    onExerciseClick = { exercise?.let { onExerciseClick(it.id) } },
                    onTargetSetsChanged = { viewModel.updateTargetSets(planned.exerciseId, it) },
                    onLogSet = { weight, reps, duration, distance, speed, incline ->
                        viewModel.logSet(
                            planned.exerciseId,
                            loggedSets.size + 1,
                            weight,
                            reps,
                            duration,
                            distance,
                            speed,
                            incline,
                        )
                    },
                )
            }

            item {
                SessionActionsCard(
                    progress = progress,
                    isFinishing = state.isFinishing,
                    onAddExercise = onAddExercise,
                    onFinish = { viewModel.finishSession(onSessionFinished) },
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
    isCurrent: Boolean,
    isDone: Boolean,
    inputStyle: String,
    onSelect: () -> Unit,
    onExerciseClick: () -> Unit,
    onTargetSetsChanged: (Int) -> Unit,
    onLogSet: (weightKg: Float, reps: Int, durationSeconds: Int?, distanceKm: Float?, speedKmh: Float?, inclinePercent: Float?) -> Unit,
) {
    val loggingType = exercise?.loggingType() ?: LoggingType.WEIGHTED_REPS
    val targetColor by animateColorAsState(
        targetValue = when {
            isDone -> Teal
            isCurrent -> Coral
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
            .clickable(onClick = onSelect),
        containerColor = when {
            isDone -> Teal.copy(alpha = 0.13f)
            isCurrent -> Color.White.copy(alpha = 0.11f)
            else -> Color.White.copy(alpha = 0.045f)
        },
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            Modifier
                .border(
                    width = if (isCurrent) 1.5.dp else 1.dp,
                    color = if (isCurrent || isDone) targetColor.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.08f),
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
                        .background(targetColor.copy(alpha = if (isDone || isCurrent) 0.92f else 0.55f)),
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
                        StatusPill(isCurrent = isCurrent, isDone = isDone)
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
                color = if (isDone) Teal else Coral,
                trackColor = Color.White.copy(alpha = 0.12f),
            )

            SetHistory(
                loggedSets = loggedSets,
                loggingType = loggingType,
                targetSets = plannedExercise.targetSets,
                onTargetSetsChanged = onTargetSetsChanged,
            )

            AnimatedVisibility(visible = isCurrent && !isDone) {
                IntegratedSetLogger(
                    plannedExercise = plannedExercise,
                    exercise = exercise,
                    loggedSets = loggedSets,
                    inputStyle = inputStyle,
                    onLogSet = onLogSet,
                )
            }
        }
    }
}

@Composable
private fun StatusPill(isCurrent: Boolean, isDone: Boolean) {
    val (text, color) = when {
        isDone -> "Done" to Teal
        isCurrent -> "Now" to Coral
        else -> "Next" to Color.White.copy(alpha = 0.28f)
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = if (isCurrent || isDone) 0.22f else 0.16f),
        border = BorderStroke(1.dp, color.copy(alpha = if (isCurrent || isDone) 0.55f else 0.2f)),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (isCurrent || isDone) color else Color.White.copy(alpha = 0.58f),
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SetHistory(
    loggedSets: List<SessionSet>,
    loggingType: LoggingType,
    targetSets: Int,
    onTargetSetsChanged: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (loggedSets.isEmpty()) {
            Text("No sets logged yet", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.48f))
        } else {
            loggedSets.forEachIndexed { index, set ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Set ${index + 1}", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.56f))
                    Text(setLabel(set, loggingType), style = MaterialTheme.typography.labelMedium, color = Teal, fontWeight = FontWeight.Bold)
                }
            }
        }

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

@Composable
private fun IntegratedSetLogger(
    plannedExercise: PlannedExercise,
    exercise: Exercise?,
    loggedSets: List<SessionSet>,
    inputStyle: String,
    onLogSet: (weightKg: Float, reps: Int, durationSeconds: Int?, distanceKm: Float?, speedKmh: Float?, inclinePercent: Float?) -> Unit,
) {
    val loggingType = exercise?.loggingType() ?: LoggingType.WEIGHTED_REPS
    val currentSetNumber = loggedSets.size + 1

    val wholeNumbers = remember { (0..300).toList() }
    val decimalWeightOptions = remember { listOf(0.0f, 0.25f, 0.5f, 0.75f) }
    val decimalTenths = remember { (0..9).map { it / 10f } }
    val repsList = remember { (0..100).toList() }
    val minsList = remember { (0..120).toList() }
    val secsList = remember { (0..59).toList() }

    val initialWeight = plannedExercise.targetWeightKg ?: loggedSets.lastOrNull()?.weightKg ?: 0f
    var weightWhole by remember(plannedExercise.exerciseId) { mutableIntStateOf(initialWeight.toInt()) }
    var weightDecimal by remember(plannedExercise.exerciseId) {
        mutableFloatStateOf(decimalWeightOptions.minByOrNull { abs(it - (initialWeight - initialWeight.toInt())) } ?: 0f)
    }
    var repsInput by remember(plannedExercise.exerciseId) {
        mutableIntStateOf(plannedExercise.targetReps ?: loggedSets.lastOrNull()?.reps ?: 0)
    }

    val initialDuration = plannedExercise.targetDurationSeconds ?: loggedSets.lastOrNull()?.durationSeconds ?: 0
    var durationMin by remember(plannedExercise.exerciseId) { mutableIntStateOf(initialDuration / 60) }
    var durationSec by remember(plannedExercise.exerciseId) { mutableIntStateOf(initialDuration % 60) }

    val initialDistance = plannedExercise.targetDistanceKm ?: loggedSets.lastOrNull()?.distanceKm ?: 0f
    var distanceWhole by remember(plannedExercise.exerciseId) { mutableIntStateOf(initialDistance.toInt()) }
    var distanceDecimal by remember(plannedExercise.exerciseId) {
        mutableFloatStateOf(decimalTenths.minByOrNull { abs(it - (initialDistance - initialDistance.toInt())) } ?: 0f)
    }

    val initialSpeed = plannedExercise.targetSpeedKmh ?: loggedSets.lastOrNull()?.speedKmh ?: 0f
    var speedWhole by remember(plannedExercise.exerciseId) { mutableIntStateOf(initialSpeed.toInt()) }
    var speedDecimal by remember(plannedExercise.exerciseId) {
        mutableFloatStateOf(decimalTenths.minByOrNull { abs(it - (initialSpeed - initialSpeed.toInt())) } ?: 0f)
    }

    val initialIncline = plannedExercise.targetIncline ?: loggedSets.lastOrNull()?.inclinePercent ?: 0f
    var inclineWhole by remember(plannedExercise.exerciseId) { mutableIntStateOf(initialIncline.toInt()) }
    var inclineDecimal by remember(plannedExercise.exerciseId) {
        mutableFloatStateOf(decimalTenths.minByOrNull { abs(it - (initialIncline - initialIncline.toInt())) } ?: 0f)
    }

    val isTapToExpand = inputStyle == "TAP_EXPAND"
    val isHorizontal = inputStyle == "HORIZONTAL"
    var isExpanded by remember(plannedExercise.exerciseId) { mutableStateOf(!isTapToExpand) }
    LaunchedEffect(inputStyle, plannedExercise.exerciseId) {
        isExpanded = inputStyle != "TAP_EXPAND"
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
                Text("Log ${loggingType.actionLabel()} $currentSetNumber", fontWeight = FontWeight.ExtraBold)
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
    onAddExercise: () -> Unit,
    onFinish: () -> Unit,
) {
    GlassmorphicCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
            Text(
                "${progress.completedSets} sets logged so far",
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
