package com.trackme.ui.workout.session

import app.cash.turbine.test
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
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
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class ActiveSessionViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun fakeSession() = WorkoutSession(
        id = UUID.randomUUID().toString(),
        userId = "user1",
        dayId = "day1",
        date = 1000L,
        durationMinutes = 0,
        notes = "",
        updatedAt = 1000L,
    )

    @Test
    fun `rest timer initial state is 90 seconds and not running`() = runTest {
        val workoutRepository = mockk<WorkoutRepository>(relaxed = true)
        val exerciseRepository = mockk<ExerciseRepository>(relaxed = true)
        val startSession = mockk<StartSessionUseCase>()
        val logSet = mockk<LogSetUseCase>(relaxed = true)
        val finishSession = mockk<FinishSessionUseCase>(relaxed = true)
        val addExerciseToDay = mockk<AddExerciseToDayUseCase>(relaxed = true)
        val supabase = mockk<SupabaseClient>(relaxed = true)
        val dataStore = mockk<DataStore<Preferences>>(relaxed = true)

        val session = fakeSession()
        coEvery { startSession(any(), any()) } returns session
        every { workoutRepository.getPlannedExercisesForDay(any()) } returns flowOf(emptyList())
        every { workoutRepository.getSessionSets(any()) } returns flowOf(emptyList())
        every { dataStore.data } returns flowOf(emptyPreferences())

        val savedState = androidx.lifecycle.SavedStateHandle(mapOf("dayId" to "day1"))
        val vm = ActiveSessionViewModel(
            workoutRepository, exerciseRepository, startSession, logSet, finishSession,
            addExerciseToDay, supabase, dataStore, savedState,
        )

        vm.uiState.test {
            val state = awaitItem()
            assertEquals(90, state.restSeconds)
            assertFalse(state.restTimerRunning)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
