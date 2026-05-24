package com.trackme.ui.workout.session

import android.content.ComponentName
import android.content.Context
import app.cash.turbine.test
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.SavedStateHandle
import com.trackme.domain.model.Exercise
import com.trackme.domain.model.PlannedExercise
import com.trackme.domain.model.SessionSet
import com.trackme.domain.model.WorkoutSession
import com.trackme.domain.repository.WorkoutRepository
import com.trackme.domain.usecase.AddExerciseToDayUseCase
import com.trackme.domain.usecase.DeleteSetUseCase
import com.trackme.domain.usecase.FinishSessionUseCase
import com.trackme.domain.usecase.LogSetUseCase
import com.trackme.domain.usecase.ObservePlannedExercisesWithDetailsUseCase
import com.trackme.domain.usecase.StartSessionUseCase
import io.github.jan.supabase.SupabaseClient
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActiveSessionViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `rest timer initial state is 90 seconds and not running`() = runTest {
        val harness = createHarness()

        harness.viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(90, state.restSeconds)
            assertFalse(state.restTimerRunning)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `exercise state transitions dynamically based on completed and target sets`() = runTest {
        val exercisesFlow = MutableStateFlow<List<Pair<PlannedExercise, Exercise?>>>(emptyList())
        val setsFlow = MutableStateFlow<List<SessionSet>>(emptyList())
        val session = fakeSession()
        val harness = createHarness(
            session = session,
            exercisesFlow = exercisesFlow,
            setsFlow = setsFlow,
        )

        val plannedExercise = PlannedExercise(
            id = "planned1",
            dayId = "day1",
            userId = "user1",
            exerciseId = "ex1",
            orderIndex = 0,
            updatedAt = 1000L,
            targetSets = 3,
            targetReps = 10,
            targetWeightKg = 60f,
        )
        val exercise = Exercise(
            id = "ex1",
            name = "Squat",
            category = "Strength",
            primaryMuscles = listOf("Quads"),
            secondaryMuscles = emptyList(),
            equipment = "Barbell",
            instructions = emptyList(),
            gifUrl = "",
            youtubeQuery = "",
        )

        exercisesFlow.value = listOf(plannedExercise to exercise)

        harness.viewModel.uiState.test {
            var state = awaitItem()
            while (state.exercises.isEmpty()) {
                state = awaitItem()
            }
            assertEquals(ExerciseExecutionState.IDLE, state.executionStates["ex1"])

            harness.viewModel.startExercise("ex1")
            state = awaitItem()
            assertEquals(ExerciseExecutionState.ACTIVE_SET, state.executionStates["ex1"])
            assertEquals("ex1", state.activeExerciseId)

            val set1 = sessionSet("s1", session.id, setNumber = 1)
            harness.viewModel.completeSet("ex1", 60f, 10)
            setsFlow.value = listOf(set1)

            state = awaitItem()
            while (state.loggedSets.size < 1) {
                state = awaitItem()
            }
            assertEquals(ExerciseExecutionState.RESTING, state.executionStates["ex1"])
            assertEquals("ex1", state.restingExerciseId)
            assertNull(state.activeExerciseId)

            val set2 = sessionSet("s2", session.id, setNumber = 2)
            harness.viewModel.completeSet("ex1", 60f, 10)
            setsFlow.value = listOf(set1, set2)
            state = awaitItem()
            while (state.loggedSets.size < 2) {
                state = awaitItem()
            }
            assertEquals(ExerciseExecutionState.RESTING, state.executionStates["ex1"])

            val set3 = sessionSet("s3", session.id, setNumber = 3)
            harness.viewModel.completeSet("ex1", 60f, 10)
            setsFlow.value = listOf(set1, set2, set3)
            state = awaitItem()
            while (state.loggedSets.size < 3) {
                state = awaitItem()
            }
            assertEquals(ExerciseExecutionState.COMPLETED, state.executionStates["ex1"])
            assertNull(state.activeExerciseId)
            assertNull(state.restingExerciseId)

            exercisesFlow.value = listOf(plannedExercise.copy(targetSets = 4) to exercise)
            state = awaitItem()
            while (state.exercises.first().first.targetSets != 4) {
                state = awaitItem()
            }
            assertEquals(ExerciseExecutionState.IDLE, state.executionStates["ex1"])

            exercisesFlow.value = listOf(plannedExercise.copy(targetSets = 3) to exercise)
            state = awaitItem()
            while (state.exercises.first().first.targetSets != 3) {
                state = awaitItem()
            }
            assertEquals(ExerciseExecutionState.COMPLETED, state.executionStates["ex1"])

            harness.viewModel.deleteSet(set3)
            state = awaitItem()
            assertEquals(2, state.loggedSets.size)
            assertEquals(ExerciseExecutionState.IDLE, state.executionStates["ex1"])

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `completed session retrieved from repository marks all exercises completed and sets isCompleted true`() = runTest {
        val date = System.currentTimeMillis()
        val completedSession = fakeSession(date = date).copy(
            id = "completed_session_id",
            durationMinutes = 45,
        )
        val planned = PlannedExercise(
            id = "planned1",
            dayId = "day1",
            userId = "user1",
            exerciseId = "ex1",
            orderIndex = 0,
            updatedAt = 1000L,
            targetSets = 3,
            targetReps = 10,
            targetWeightKg = 60f,
        )
        val exercise = Exercise(
            id = "ex1",
            name = "Squat",
            category = "Strength",
            primaryMuscles = listOf("Quads"),
            secondaryMuscles = emptyList(),
            equipment = "Barbell",
            instructions = emptyList(),
            gifUrl = "",
            youtubeQuery = "",
        )
        val harness = createHarness(
            session = completedSession,
            existingSession = completedSession,
            exercisesFlow = MutableStateFlow(listOf(planned to exercise)),
            savedState = SavedStateHandle(mapOf("dayId" to "day1", "dateMillis" to date.toString())),
        )

        harness.viewModel.uiState.test {
            var state = awaitItem()
            while (state.exercises.isEmpty()) {
                state = awaitItem()
            }
            assertTrue(state.isCompleted)
            assertEquals(ExerciseExecutionState.COMPLETED, state.executionStates["ex1"])
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun createHarness(
        session: WorkoutSession = fakeSession(),
        existingSession: WorkoutSession? = null,
        exercisesFlow: Flow<List<Pair<PlannedExercise, Exercise?>>> = flowOf(emptyList()),
        setsFlow: Flow<List<SessionSet>> = flowOf(emptyList()),
        savedState: SavedStateHandle = SavedStateHandle(mapOf("dayId" to "day1")),
    ): Harness {
        val workoutRepository = mockk<WorkoutRepository>(relaxed = true)
        val startSession = mockk<StartSessionUseCase>()
        val logSet = mockk<LogSetUseCase>(relaxed = true)
        val deleteSet = mockk<DeleteSetUseCase>(relaxed = true)
        val finishSession = mockk<FinishSessionUseCase>(relaxed = true)
        val addExerciseToDay = mockk<AddExerciseToDayUseCase>(relaxed = true)
        val observePlannedExercises = mockk<ObservePlannedExercisesWithDetailsUseCase>()
        val supabase = mockk<SupabaseClient>(relaxed = true)
        val dataStore = FakePreferencesDataStore()

        coEvery { workoutRepository.getLatestSessionForDay(any(), any(), any()) } returns existingSession
        coEvery { startSession(any(), any(), any()) } returns session
        every { observePlannedExercises(any()) } returns exercisesFlow
        every { workoutRepository.getSessionSets(any()) } returns setsFlow

        val manager = WorkoutSessionManager(
            workoutRepository = workoutRepository,
            startSession = startSession,
            logSetUseCase = logSet,
            deleteSetUseCase = deleteSet,
            finishSessionUseCase = finishSession,
            addExerciseToDay = addExerciseToDay,
            observePlannedExercisesWithDetails = observePlannedExercises,
            supabase = supabase,
            dataStore = dataStore,
        )
        val viewModel = ActiveSessionViewModel(
            sessionManager = manager,
            savedStateHandle = savedState,
            context = fakeContext(),
        )

        return Harness(viewModel)
    }

    private fun fakeContext(): Context {
        val component = ComponentName("com.trackme", "WorkoutSessionService")
        return mockk(relaxed = true) {
            every { packageName } returns "com.trackme"
            every { startForegroundService(any()) } returns component
            every { startService(any()) } returns component
        }
    }

    private fun fakeSession(date: Long = System.currentTimeMillis()) = WorkoutSession(
        id = UUID.randomUUID().toString(),
        userId = "user1",
        dayId = "day1",
        date = date,
        durationMinutes = 0,
        notes = "",
        updatedAt = date,
    )

    private fun sessionSet(id: String, sessionId: String, setNumber: Int) = SessionSet(
        id = id,
        sessionId = sessionId,
        userId = "user1",
        exerciseId = "ex1",
        setNumber = setNumber,
        weightKg = 60f,
        reps = 10,
        completed = true,
        updatedAt = 1000L,
    )

    private data class Harness(
        val viewModel: ActiveSessionViewModel,
    )

    private class FakePreferencesDataStore(
        initial: Preferences = emptyPreferences(),
    ) : DataStore<Preferences> {
        private val state = MutableStateFlow(initial)

        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val next = transform(state.value)
            state.value = next
            return next
        }
    }
}
