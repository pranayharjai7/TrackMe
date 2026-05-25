package com.trackme.wearable.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.Text
import com.trackme.wearable.designsystem.WearColors
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
    val restSecondsRemaining by viewModel.restSecondsRemaining.collectAsStateWithLifecycle()
    val restTotalSeconds by viewModel.restTotalSeconds.collectAsStateWithLifecycle()

    // Derived slices — each page only recomposes when its own slice changes
    val heartRateBpm by remember { derivedStateOf { uiState.health.heartRateBpm } }
    val sessionMuscle by remember { derivedStateOf { uiState.session?.muscle } }
    val sessionExerciseName by remember { derivedStateOf { uiState.session?.exerciseName } }

    val onTap         = remember(viewModel) { { viewModel.confirmSet() } }
    val onAdjustField = remember(viewModel) { { d: Int -> viewModel.adjustActiveField(d) } }
    val onToggleField = remember(viewModel) { { viewModel.toggleActiveField() } }

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
                PAGE_HR     -> HrZonesPage(heartRateBpm = heartRateBpm)
                PAGE_ACTIVE -> ActiveSetScreen(
                    uiState       = uiState,
                    onTap         = onTap,
                    onAdjustField = onAdjustField,
                    onToggleField = onToggleField,
                )
                PAGE_MUSCLE -> MuscleMapPage(
                    muscle        = sessionMuscle,
                    exerciseName  = sessionExerciseName,
                )
                PAGE_MEDIA  -> MediaPage()
                else        -> Box(modifier = Modifier.fillMaxSize())
            }
        }

        // Layer 2: state machine overlays
        // GW4: AnimatedContent pre-warms naturally — InfiniteTransition in LogConfirmOverlay
        // starts on first composition which AnimatedContent handles correctly.
        AnimatedContent(
            targetState = uiState.workoutScreenState,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
            label = "workoutStateOverlay"
        ) { state ->
            when (state) {
                WorkoutScreenState.CONFIRM -> LogConfirmOverlay(
                    uiState   = uiState,
                    onConfirm = { viewModel.confirmAndLog() },
                    onCancel  = { viewModel.cancelConfirm() }
                )
                WorkoutScreenState.RESTING -> RestTimerScreen(
                    remaining        = restSecondsRemaining,
                    total            = restTotalSeconds,
                    nextExerciseName = uiState.session?.nextExerciseName,
                    heartRateBpm     = uiState.health.heartRateBpm,
                    onEndRest        = { viewModel.endRest() },
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

        // Layer 3: ambient overlay (dims everything when watch is in AOD mode)
        if (uiState.isAmbient) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(WearColors.Black),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = uiState.session?.exerciseName ?: "Workout",
                        color = WearColors.TextSecondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = formatSeconds(restSecondsRemaining).takeIf {
                            uiState.workoutScreenState == WorkoutScreenState.RESTING
                        } ?: "Set ${uiState.session?.setIndex ?: "--"}",
                        color = WearColors.TextPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
