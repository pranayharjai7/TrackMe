package com.trackme.ui.workout.session

import app.cash.turbine.test
import com.trackme.domain.model.*
import com.trackme.domain.repository.ExerciseRepository
import com.trackme.domain.repository.WorkoutRepository
import com.trackme.domain.usecase.*
import io.mockk.*
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActiveSessionViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `rest timer initial state is 90 seconds and not running`() = runTest {
        val workoutRepository = mockk<WorkoutRepository>(relaxed = true)
        val exerciseRepository = mockk<ExerciseRepository>(relaxed = true)
        val startSession = StartSessionUseCase(workoutRepository)
        val logSet = LogSetUseCase(workoutRepository)
        val finishSession = FinishSessionUseCase(workoutRepository)
        val supabase = mockk<SupabaseClient>(relaxed = true)

        every { workoutRepository.getPlannedExercisesForDay(any()) } returns flowOf(emptyList())
        every { workoutRepository.getSessionSets(any()) } returns flowOf(emptyList())

        val savedState = androidx.lifecycle.SavedStateHandle(mapOf("dayId" to "day1"))
        val vm = ActiveSessionViewModel(
            workoutRepository, exerciseRepository, startSession, logSet, finishSession, supabase, savedState
        )

        vm.uiState.test {
            val state = awaitItem()
            assertEquals(90, state.restSeconds)
            assertFalse(state.restTimerRunning)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
