package com.trackme.wearable.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.trackme.wearable.designsystem.AmoledScreenBackground
import com.trackme.wearable.designsystem.wearBezelInput
import com.trackme.wearable.interaction.WorkoutHapticEvent
import com.trackme.wearable.interaction.rememberHapticFeedbackManager
import com.trackme.wearable.interaction.routeWorkoutTap
import com.trackme.wearable.ui.motion.screenPhaseTransition
import com.trackme.wearable.ui.screens.ActiveSetScreen
import com.trackme.wearable.ui.screens.ExerciseSummaryScreen
import com.trackme.wearable.ui.screens.HrZonesPage
import com.trackme.wearable.ui.screens.LogConfirmOverlay
import com.trackme.wearable.ui.screens.MediaPage
import com.trackme.wearable.ui.screens.MuscleMapPage
import com.trackme.wearable.ui.screens.RestTimerScreen
import com.trackme.wearable.ui.screens.WatchFaceScreen
import com.trackme.wearable.ui.screens.WorkoutHubScreen
import com.trackme.wearable.ui.screens.UndoConfirmOverlay
import com.trackme.wearable.ui.screens.WorkoutControlsOverlay
import com.trackme.wearable.ui.screens.WorkoutSummaryScreen
import com.trackme.wearable.viewmodel.WearSessionViewModel
import com.trackme.wearable.viewmodel.WearUiState
import com.trackme.wearable.workout.DerivedWorkoutUi
import com.trackme.wearable.workout.HorizontalPage
import com.trackme.wearable.workout.WorkoutAppPhase
import com.trackme.wearable.workout.WorkoutInteractionEngine
import com.trackme.wearable.workout.WorkoutOverlay
import com.trackme.wearable.workout.WorkoutScreenPhase
import com.trackme.wearable.workout.WearWorkoutStateMachine
import com.trackme.wearable.workout.computeReadiness
import com.trackme.wearable.workout.hubWorkouts
import com.trackme.wearable.workout.toLayoutProfile
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WorkoutExperience(
    state: WearUiState,
    viewModel: WearSessionViewModel,
    engine: WorkoutInteractionEngine,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHapticFeedbackManager()
    val derived = remember(state) { WearWorkoutStateMachine.derive(state, state.workoutFlow) }
    val focusRequester = remember { FocusRequester() }
    val readiness = computeReadiness(state)
    val hubItems = hubWorkouts(state)
    val hubIndex = state.workoutFlow.hubWorkoutIndex.coerceIn(0, hubItems.lastIndex.coerceAtLeast(0))

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val transition = updateTransition(targetState = derived.screenPhase, label = "workoutPhase")

    AmoledScreenBackground(phase = derived.screenPhase, modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .wearBezelInput(engine) { delta ->
                    engine.handleRotaryScroll(state, derived, delta)
                },
        ) {
            when (derived.appPhase) {
                WorkoutAppPhase.WatchFace -> WatchFaceScreen(
                    readiness = readiness,
                    heartRate = state.health.heartRateBpm?.toInt(),
                    calories = state.health.activeCalories?.toInt() ?: 0,
                    onOpenHub = {
                        if (state.session?.sessionId?.isNotBlank() == true) {
                            viewModel.navigateToHub()
                        } else {
                            viewModel.requestSnapshot()
                        }
                    },
                )
                WorkoutAppPhase.WorkoutHub -> WorkoutHubScreen(
                    workout = hubItems[hubIndex],
                    readiness = readiness,
                    onStart = { engine.onStartWorkout() },
                )
                WorkoutAppPhase.WorkoutActive -> AnimatedContent(
                    targetState = derived.screenPhase,
                    transitionSpec = { screenPhaseTransition(initialState, targetState) },
                    label = "screenPhase",
                ) { phase ->
                    when (phase) {
                        WorkoutScreenPhase.LogConfirm -> LogConfirmOverlay(
                            onConfirm = { engine.onConfirmLog() },
                            onCancel = { engine.onCancelLog() },
                        )
                        WorkoutScreenPhase.RestTimer -> {
                            val session = state.session
                            var initialRest by remember(session?.sessionId) {
                                mutableIntStateOf(
                                    (session?.restRemaining ?: 60) + state.workoutFlow.restAdjustSeconds,
                                )
                            }
                            val restSeconds = engine.restSecondsDisplayed(session, state.workoutFlow.restAdjustSeconds)
                            LaunchedEffect(restSeconds) {
                                if (restSeconds <= 10 && restSeconds > 0) {
                                    haptics.play(WorkoutHapticEvent.RestWarning10s)
                                }
                                if (restSeconds == 0 && session?.restActive == true) {
                                    haptics.play(WorkoutHapticEvent.RestComplete)
                                    viewModel.skipRest()
                                }
                            }
                            RestTimerScreen(
                                session = session,
                                restSeconds = restSeconds,
                                initialRestSeconds = initialRest,
                                onEndEarly = { engine.onEndRestEarly() },
                            )
                        }
                        WorkoutScreenPhase.ExerciseSummary -> {
                            val session = state.session
                            val exercise = session?.exercises?.getOrNull(session.exerciseIndex)
                            LaunchedEffect(Unit) {
                                state.workoutFlow.pendingPrDeltaKg?.let {
                                    haptics.play(WorkoutHapticEvent.PersonalRecord)
                                }
                            }
                            ExerciseSummaryScreen(
                                exerciseName = exercise?.exerciseName ?: session?.exerciseName.orEmpty(),
                                volumeKg = session?.totalVolumeKg ?: 0f,
                                prDeltaKg = state.workoutFlow.pendingPrDeltaKg,
                                onAdvance = { engine.onAdvanceExerciseSummary() },
                            )
                        }
                        WorkoutScreenPhase.WorkoutSummary -> {
                            val session = state.session
                            val detailIndex = state.workoutFlow.summaryDetailIndex
                            val detail = session?.exercises?.getOrNull(detailIndex)
                            LaunchedEffect(Unit) {
                                haptics.play(com.trackme.wearable.interaction.WorkoutHapticEvent.WorkoutComplete)
                            }
                            WorkoutSummaryScreen(
                                durationSeconds = state.health.durationSeconds,
                                exerciseCount = session?.exercises?.size ?: 0,
                                totalVolumeKg = session?.totalVolumeKg ?: 0f,
                                prCount = if (state.workoutFlow.pendingPrDeltaKg != null) 1 else 0,
                                detailExercise = detail,
                                syncing = state.queuedCount > 0,
                                onDismiss = { engine.onDismissWorkoutSummary() },
                            )
                        }
                        WorkoutScreenPhase.SetReady -> WorkoutPagerContent(
                            state = state,
                            derived = derived,
                            viewModel = viewModel,
                            engine = engine,
                        )
                    }
                }
            }

            if (state.workoutFlow.undoConfirmVisible) {
                UndoConfirmOverlay(
                    onConfirm = { viewModel.confirmUndo() },
                    onDismiss = { viewModel.dismissUndo() },
                )
            }
            if (state.workoutFlow.workoutControlsVisible) {
                WorkoutControlsOverlay(
                    onPause = { viewModel.hideWorkoutControls() },
                    onEndWorkout = {
                        viewModel.hideWorkoutControls()
                        viewModel.finishWorkout()
                    },
                    onDismiss = { viewModel.hideWorkoutControls() },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WorkoutPagerContent(
    state: WearUiState,
    derived: DerivedWorkoutUi,
    viewModel: WearSessionViewModel,
    engine: WorkoutInteractionEngine,
) {
    val pagerState = rememberPagerState(
        initialPage = HorizontalPage.ActiveSet.index,
        pageCount = { HorizontalPage.entries.size },
    )

    LaunchedEffect(derived.horizontalPage) {
        val target = derived.horizontalPage.index
        if (pagerState.currentPage != target) {
            pagerState.animateScrollToPage(target)
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                viewModel.setHorizontalPage(HorizontalPage.fromIndex(page))
            }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        beyondViewportPageCount = 1,
    ) { page ->
        when (HorizontalPage.fromIndex(page)) {
            HorizontalPage.HrZones -> HrZonesPage(bpm = state.health.heartRateBpm?.toInt())
            HorizontalPage.ActiveSet -> {
                val session = state.session
                if (session != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .routeWorkoutTap(
                                onTap = { engine.onActiveSetTap(state, derived) },
                                onDoubleTap = { engine.onActiveSetDoubleTap(state) },
                                onLongPress = { viewModel.showWorkoutControls() },
                            ),
                    ) {
                        ActiveSetScreen(
                            session = session,
                            input = state.loggerInput,
                            profile = session.loggingType.toLayoutProfile(),
                            heartRate = state.health.heartRateBpm?.toInt(),
                            volumeKg = session.totalVolumeKg,
                            offline = state.offline,
                        )
                    }
                }
            }
            HorizontalPage.MuscleMap -> {
                val session = state.session
                val muscle = session?.muscle.orEmpty()
                MuscleMapPage(
                    primaryMuscle = muscle,
                    secondaryMuscles = session?.exercises?.map { it.muscle }.orEmpty().distinct(),
                    scrollOffset = state.workoutFlow.muscleScrollOffset,
                )
            }
            HorizontalPage.Media -> MediaPage(
                trackTitle = "Workout Mix",
                artist = "On-device",
                volume = state.workoutFlow.mediaVolume,
                onPrevious = {},
                onTogglePlay = {},
                onNext = {},
            )
        }
    }
}
