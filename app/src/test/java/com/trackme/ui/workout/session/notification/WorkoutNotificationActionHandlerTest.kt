package com.trackme.ui.workout.session.notification

import com.trackme.domain.model.PlannedExercise
import com.trackme.ui.workout.session.ActiveSessionUiState
import com.trackme.ui.workout.session.WorkoutSessionManager
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WorkoutNotificationActionHandlerTest {

    private val sessionManager = mockk<WorkoutSessionManager>(relaxed = true)
    private val stateFlow = MutableStateFlow(ActiveSessionUiState(sessionId = "s1", activeExerciseId = "ex-1"))
    private lateinit var handler: WorkoutNotificationActionHandler

    @Before
    fun setUp() {
        every { sessionManager.uiState } returns stateFlow
        every { sessionManager.skipRest(any()) } just runs
        every { sessionManager.pauseWorkout() } just runs
        handler = WorkoutNotificationActionHandler(sessionManager)
    }

    @Test
    fun `dispatches skip rest to manager`() {
        stateFlow.value = stateFlow.value.copy(restingExerciseId = "ex-1")
        assertTrue(handler.handle(WorkoutNotificationActions.ACTION_SKIP_REST))
        verify { sessionManager.skipRest("ex-1") }
    }

    @Test
    fun `ignores actions without session`() {
        stateFlow.value = ActiveSessionUiState()
        assertFalse(handler.handle(WorkoutNotificationActions.ACTION_COMPLETE_SET))
    }

    @Test
    fun `debounces duplicate rapid actions`() {
        stateFlow.value = stateFlow.value.copy(restingExerciseId = "ex-1")
        assertTrue(handler.handle(WorkoutNotificationActions.ACTION_SKIP_REST))
        assertFalse(handler.handle(WorkoutNotificationActions.ACTION_SKIP_REST))
        verify(exactly = 1) { sessionManager.skipRest("ex-1") }
    }

    @Test
    fun `add set increases target sets`() {
        val planned = mockk<PlannedExercise>()
        every { planned.targetSets } returns 3
        every { sessionManager.plannedFor("ex-1") } returns planned
        every { sessionManager.updateTargetSets("ex-1", 4) } just runs

        assertTrue(handler.handle(WorkoutNotificationActions.ACTION_ADD_SET))
        verify { sessionManager.updateTargetSets("ex-1", 4) }
    }
}
