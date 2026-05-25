package com.trackme.wearable.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.animateColorAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import com.trackme.wearable.designsystem.stateBackgroundColor
import com.trackme.wearable.viewmodel.WearSessionViewModel
import com.trackme.wearable.viewmodel.WorkoutScreenState

private const val PAGE_HR     = 0
private const val PAGE_ACTIVE = 1
private const val PAGE_MUSCLE = 2
private const val PAGE_MEDIA  = 3
private const val PAGE_COUNT  = 4

@Composable
fun WorkoutScreen(
    viewModel: WearSessionViewModel,
    onWorkoutFinished: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(initialPage = PAGE_ACTIVE) { PAGE_COUNT }
    val bgColor by animateColorAsState(
        targetValue = stateBackgroundColor(uiState.workoutScreenState),
        animationSpec = tween(durationMillis = 300),
        label = "stateBackground"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        // Layer 1: horizontal context pages
        HorizontalPager(state = pagerState) { page ->
            when (page) {
                PAGE_HR     -> HrZonesPage(uiState = uiState)
                PAGE_ACTIVE -> ActiveSetScreen(
                    uiState = uiState,
                    onTap = { viewModel.confirmSet() },
                    onAdjustField = { delta -> viewModel.adjustActiveField(delta) },
                    onToggleField = {
                        viewModel.selectField(
                            if (uiState.loggerInput.activeField == com.trackme.wearable.viewmodel.LoggerField.WEIGHT)
                                com.trackme.wearable.viewmodel.LoggerField.REPS
                            else
                                com.trackme.wearable.viewmodel.LoggerField.WEIGHT
                        )
                    }
                )
                PAGE_MUSCLE -> MuscleMapPage(uiState = uiState)
                PAGE_MEDIA  -> MediaPage()
                else        -> Box(modifier = Modifier.fillMaxSize())
            }
        }

        // Layer 2: state machine overlays
        AnimatedContent(
            targetState = uiState.workoutScreenState,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
            label = "workoutStateOverlay"
        ) { state ->
            when (state) {
                WorkoutScreenState.CONFIRM -> LogConfirmOverlay(
                    onConfirm = { viewModel.confirmAndLog() },
                    onCancel  = { viewModel.cancelConfirm() }
                )
                WorkoutScreenState.RESTING -> RestTimerScreen(
                    uiState = uiState,
                    onEndRest = { viewModel.endRest() },
                    onAdjustRestTime = { delta -> viewModel.adjustRestTime(delta * 15) }
                )
                WorkoutScreenState.EXERCISE_SUMMARY -> ExerciseSummaryScreen(
                    uiState = uiState,
                    onAdvance = { viewModel.nextExercise() }
                )
                WorkoutScreenState.WORKOUT_COMPLETE -> WorkoutSummaryScreen(
                    uiState = uiState,
                    onDismiss = onWorkoutFinished
                )
                else -> Box(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
