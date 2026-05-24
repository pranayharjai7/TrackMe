package com.trackme.wearable.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.trackme.wearable.designsystem.WearGradientBackground
import com.trackme.wearable.ui.screens.WearHomeScreen
import com.trackme.wearable.viewmodel.LoggerField
import com.trackme.wearable.viewmodel.WearSessionViewModel
import com.trackme.wearable.viewmodel.WearUiState
import com.trackme.wearbridge.LoggingTypePayload
import com.trackme.wearbridge.SessionExercisePayload
import com.trackme.wearbridge.SessionStatePayload

private object WearRoutes {
    const val Home = "home"
    const val Overview = "overview"
    const val Logger = "logger"
    const val Rest = "rest"
    const val Detail = "detail"
    const val Exercises = "exercises"
}

@Composable
fun TrackMeWearApp(viewModel: WearSessionViewModel) {
    val state by viewModel.uiState.collectAsState()
    val navController = rememberSwipeDismissableNavController()

    MaterialTheme {
        WearGradientBackground {
            SwipeDismissableNavHost(
                navController = navController,
                startDestination = WearRoutes.Home,
            ) {
                composable(WearRoutes.Home) {
                    WearHomeScreen(
                        state = state,
                        onOpenWorkout = {
                            navController.navigate(WearRoutes.Overview) {
                                popUpTo(WearRoutes.Home)
                            }
                        },
                        onSync = viewModel::requestSnapshot,
                    )
                }
                composable(WearRoutes.Overview) {
                    SessionOverviewScreen(
                        state = state,
                        onStartSet = {
                            viewModel.startSet()
                            viewModel.prepareLogger()
                            navController.navigate(WearRoutes.Logger)
                        },
                        onSkipRest = {
                            viewModel.skipRest()
                            navController.navigate(WearRoutes.Overview)
                        },
                        onNext = viewModel::nextExercise,
                        onPrevious = viewModel::previousExercise,
                        onFinish = viewModel::finishWorkout,
                        onRest = { navController.navigate(WearRoutes.Rest) },
                        onDetail = { navController.navigate(WearRoutes.Detail) },
                        onExercises = { navController.navigate(WearRoutes.Exercises) },
                        onRefresh = viewModel::requestSnapshot,
                    )
                }
                composable(WearRoutes.Logger) {
                    SetLoggerScreen(
                        state = state,
                        onSelectField = viewModel::selectField,
                        onAdjust = viewModel::adjustActiveField,
                        onSave = {
                            viewModel.submitLog()
                            navController.navigate(WearRoutes.Overview) {
                                popUpTo(WearRoutes.Overview)
                            }
                        },
                    )
                }
                composable(WearRoutes.Rest) {
                    RestTimerScreen(
                        session = state.session,
                        onSkipRest = {
                            viewModel.skipRest()
                            navController.navigate(WearRoutes.Overview) {
                                popUpTo(WearRoutes.Overview)
                            }
                        },
                    )
                }
                composable(WearRoutes.Detail) {
                    ExerciseDetailMiniView(session = state.session)
                }
                composable(WearRoutes.Exercises) {
                    ExerciseSwitcherScreen(
                        state = state,
                        onSwitch = {
                            viewModel.switchToExercise(it)
                            navController.navigate(WearRoutes.Overview) {
                                popUpTo(WearRoutes.Overview)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionOverviewScreen(
    state: WearUiState,
    onStartSet: () -> Unit,
    onSkipRest: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onFinish: () -> Unit,
    onRest: () -> Unit,
    onDetail: () -> Unit,
    onExercises: () -> Unit,
    onRefresh: () -> Unit,
) {
    val session = state.session
    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            StatusLine(offline = state.offline, queuedCount = state.queuedCount)
        }
        item {
            ProgressRing(
                progress = (session?.sessionProgressPercent ?: 0) / 100f,
                centerText = "${session?.sessionProgressPercent ?: 0}%",
            )
        }
        item {
            GlassCard {
                Text(
                    text = session?.exerciseName?.takeIf { it.isNotBlank() } ?: "Open active session",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (session == null || session.sessionId.isBlank()) {
                        "Start or resume a workout on the phone"
                    } else {
                        "Set ${session.setIndex}/${currentTargetSets(session)} | ${session.completedSets}/${session.totalSets} done"
                    },
                    color = Color(0xFFD7D8E6),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (session?.nextExerciseName != null) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "Next: ${session.nextExerciseName}",
                        color = Color(0xFF8EDBCE),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (session != null) {
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = "Volume ${session.totalVolumeKg.toInt()} kg | HR ${state.health.heartRateBpm?.toInt() ?: 0}",
                        color = Color(0xFFB9BBD0),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        if (session?.restActive == true) {
            item {
                ActionPill("Rest ${formatSeconds(session.restRemaining)}", onClick = onRest)
            }
            item {
                ActionPill("Skip Rest", onClick = onSkipRest, accent = Color(0xFFFFC06A))
            }
        } else {
            item {
                ActionPill("Start Set", onClick = onStartSet, enabled = session?.sessionId?.isNotBlank() == true)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.Center) {
                RoundAction("Prev", onPrevious)
                Spacer(Modifier.width(8.dp))
                RoundAction("Next", onNext)
                Spacer(Modifier.width(8.dp))
                RoundAction("Info", onDetail)
                Spacer(Modifier.width(8.dp))
                RoundAction("List", onExercises)
            }
        }
        item {
            ActionPill("Finish Workout", onClick = onFinish, accent = Color(0xFFFF7A90), enabled = session?.sessionId?.isNotBlank() == true)
        }
    }
}

@Composable
private fun SetLoggerScreen(
    state: WearUiState,
    onSelectField: (LoggerField) -> Unit,
    onAdjust: (Int) -> Unit,
    onSave: () -> Unit,
) {
    val session = state.session
    val input = state.loggerInput
    val focusRequester = remember { FocusRequester() }
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp)
            .onRotaryScrollEvent {
                val delta = if (it.verticalScrollPixels > 0) -1 else 1
                onAdjust(delta)
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                true
            }
            .focusRequester(focusRequester)
            .focusable(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Text(
                text = session?.exerciseName ?: "Log set",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        item {
            GlassCard {
                Text(
                    text = input.activeField.label(),
                    color = Color(0xFF8EDBCE),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = input.valueForActiveField(),
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoundAction("-", { onAdjust(-1) })
                    Spacer(Modifier.width(10.dp))
                    RoundAction("+", { onAdjust(1) })
                }
            }
        }
        loggerFields(session?.loggingType ?: LoggingTypePayload.WEIGHTED_REPS).forEach { field ->
            item {
                ActionPill(
                    text = field.label(),
                    onClick = { onSelectField(field) },
                    accent = if (field == input.activeField) Color(0xFF8EDBCE) else Color(0xFF7D82FF),
                )
            }
        }
        item {
            ActionPill("Log Set", onClick = onSave, accent = Color(0xFF64E3A1))
        }
    }
}

@Composable
private fun RestTimerScreen(session: SessionStatePayload?, onSkipRest: () -> Unit) {
    val seconds = session?.restRemaining ?: 0
    val scale by animateFloatAsState(
        targetValue = if (seconds <= 5 && seconds > 0) 1.08f else 1f,
        animationSpec = spring(),
        label = "restScale",
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("REST", color = Color(0xFF8EDBCE), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(
            text = formatSeconds(seconds),
            color = Color.White,
            fontSize = 42.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.scale(scale),
        )
        Spacer(Modifier.height(10.dp))
        ActionPill("Skip Rest", onClick = onSkipRest, accent = Color(0xFFFFC06A))
    }
}

@Composable
private fun ExerciseDetailMiniView(session: SessionStatePayload?) {
    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Text(
                text = session?.exerciseName ?: "Exercise",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        item {
            GlassCard {
                DetailRow("Muscle", session?.muscle.orEmpty())
                DetailRow("Equipment", session?.equipment.orEmpty())
                DetailRow("Type", session?.loggingType?.name.orEmpty())
            }
        }
        item {
            GlassCard {
                Text(
                    text = session?.instructionSummary?.takeIf { it.isNotBlank() } ?: "No instructions synced yet.",
                    color = Color(0xFFE8E9F4),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ExerciseSwitcherScreen(state: WearUiState, onSwitch: (Int) -> Unit) {
    val exercises = state.session?.exercises.orEmpty()
    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Text("Exercises", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        itemsIndexed(exercises) { index, exercise ->
            ExerciseRow(index = index, exercise = exercise, onClick = { onSwitch(index) })
        }
    }
}

@Composable
private fun ExerciseRow(index: Int, exercise: SessionExercisePayload, onClick: () -> Unit) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Text(
            text = "${index + 1}. ${exercise.exerciseName}",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = if (exercise.isActive || exercise.isResting) FontWeight.Bold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "${exercise.completedSets}/${exercise.targetSets} sets",
            color = if (exercise.isCompleted) Color(0xFF64E3A1) else Color(0xFFB9BBD0),
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun MeshBackground(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF12121B),
                        Color(0xFF1B2534),
                        Color(0xFF24192D),
                        Color(0xFF111118),
                    ),
                    start = Offset.Zero,
                    end = Offset(300f, 360f),
                )
            ),
    ) {
        content()
    }
}

@Composable
private fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@Composable
private fun ActionPill(
    text: String,
    onClick: () -> Unit,
    accent: Color = Color(0xFF7D82FF),
    enabled: Boolean = true,
) {
    val alpha = if (enabled) 1f else 0.38f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = 0.24f * alpha))
            .border(1.dp, accent.copy(alpha = 0.55f * alpha), RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White.copy(alpha = alpha),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RoundAction(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun ProgressRing(progress: Float, centerText: String) {
    Box(modifier = Modifier.size(96.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawArc(
                color = Color.White.copy(alpha = 0.13f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round),
            )
            drawArc(
                brush = Brush.sweepGradient(listOf(Color(0xFF64E3A1), Color(0xFF7D82FF), Color(0xFF64E3A1))),
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        Text(centerText, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatusLine(offline: Boolean, queuedCount: Int) {
    Text(
        text = when {
            offline && queuedCount > 0 -> "Offline | queued $queuedCount"
            offline -> "Offline"
            else -> "Connected"
        },
        color = if (offline) Color(0xFFFFC06A) else Color(0xFF64E3A1),
        fontSize = 11.sp,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Text(
        text = "$label: ${value.ifBlank { "-" }}",
        color = Color(0xFFE8E9F4),
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun currentTargetSets(session: SessionStatePayload): Int =
    session.exercises.getOrNull(session.exerciseIndex)?.targetSets ?: session.totalSets

private fun LoggerField.label(): String =
    when (this) {
        LoggerField.WEIGHT -> "Weight"
        LoggerField.REPS -> "Reps"
        LoggerField.DURATION -> "Duration"
        LoggerField.DISTANCE -> "Distance"
        LoggerField.SPEED -> "Speed"
        LoggerField.INCLINE -> "Incline"
    }

private fun com.trackme.wearable.viewmodel.LoggerInputState.valueForActiveField(): String =
    when (activeField) {
        LoggerField.WEIGHT -> "${weightKg.toOneDecimal()} kg"
        LoggerField.REPS -> reps.toString()
        LoggerField.DURATION -> formatSeconds(durationSeconds)
        LoggerField.DISTANCE -> "${distanceKm.toOneDecimal()} km"
        LoggerField.SPEED -> "${speedKmh.toOneDecimal()} km/h"
        LoggerField.INCLINE -> "${inclinePercent.toOneDecimal()}%"
    }

private fun loggerFields(loggingType: LoggingTypePayload): List<LoggerField> =
    when (loggingType) {
        LoggingTypePayload.WEIGHTED_REPS -> listOf(LoggerField.WEIGHT, LoggerField.REPS)
        LoggingTypePayload.BODYWEIGHT_REPS -> listOf(LoggerField.REPS)
        LoggingTypePayload.TIMED -> listOf(LoggerField.DURATION)
        LoggingTypePayload.CARDIO -> listOf(LoggerField.DURATION, LoggerField.DISTANCE, LoggerField.SPEED, LoggerField.INCLINE)
    }

private fun formatSeconds(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    return "${safe / 60}:${(safe % 60).toString().padStart(2, '0')}"
}

private fun Float.toOneDecimal(): String =
    if (this % 1f == 0f) toInt().toString() else String.format(java.util.Locale.US, "%.1f", this)
