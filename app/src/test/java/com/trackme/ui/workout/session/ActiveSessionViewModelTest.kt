package com.trackme.ui.workout.session

import app.cash.turbine.test
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.trackme.domain.model.*
import com.trackme.domain.repository.WorkoutRepository
import com.trackme.domain.usecase.*
import io.mockk.*
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
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
        val startSession = mockk<StartSessionUseCase>()
        val logSet = mockk<LogSetUseCase>(relaxed = true)
        val deleteSet = mockk<DeleteSetUseCase>(relaxed = true)
        val finishSession = mockk<FinishSessionUseCase>(relaxed = true)
        val addExerciseToDay = mockk<AddExerciseToDayUseCase>(relaxed = true)
        val observePlannedExercises = mockk<ObservePlannedExercisesWithDetailsUseCase>()
        val supabase = mockk<SupabaseClient>(relaxed = true)
        val dataStore = mockk<DataStore<Preferences>>(relaxed = true)

        val session = fakeSession()
        coEvery { startSession(any(), any()) } returns session
        every { observePlannedExercises(any()) } returns flowOf(emptyList())
        every { workoutRepository.getSessionSets(any()) } returns flowOf(emptyList())
        every { dataStore.data } returns flowOf(emptyPreferences())

        val savedState = androidx.lifecycle.SavedStateHandle(mapOf("dayId" to "day1"))
        val vm = ActiveSessionViewModel(
            workoutRepository = workoutRepository,
            startSession = startSession,
            logSetUseCase = logSet,
            deleteSetUseCase = deleteSet,
            finishSessionUseCase = finishSession,
            addExerciseToDay = addExerciseToDay,
            observePlannedExercisesWithDetails = observePlannedExercises,
            supabase = supabase,
            dataStore = dataStore,
            savedStateHandle = savedState,
        )

        vm.uiState.test {
            val state = awaitItem()
            assertEquals(90, state.restSeconds)
            assertFalse(state.restTimerRunning)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `exercise state transitions dynamically based on completed and target sets`() = runTest {
        val workoutRepository = mockk<WorkoutRepository>(relaxed = true)
        val startSession = mockk<StartSessionUseCase>()
        val logSet = mockk<LogSetUseCase>(relaxed = true)
        val deleteSet = mockk<DeleteSetUseCase>(relaxed = true)
        val finishSession = mockk<FinishSessionUseCase>(relaxed = true)
        val addExerciseToDay = mockk<AddExerciseToDayUseCase>(relaxed = true)
        val observePlannedExercises = mockk<ObservePlannedExercisesWithDetailsUseCase>()
        val supabase = mockk<SupabaseClient>(relaxed = true)
        val dataStore = mockk<DataStore<Preferences>>(relaxed = true)

        val session = fakeSession()
        coEvery { startSession(any(), any()) } returns session
        
        val exercisesFlow = MutableStateFlow<List<Pair<PlannedExercise, Exercise?>>>(emptyList())
        val setsFlow = MutableStateFlow<List<SessionSet>>(emptyList())
        every { observePlannedExercises(any()) } returns exercisesFlow
        every { workoutRepository.getSessionSets(any()) } returns setsFlow
        every { dataStore.data } returns flowOf(emptyPreferences())

        val savedState = androidx.lifecycle.SavedStateHandle(mapOf("dayId" to "day1"))
        val vm = ActiveSessionViewModel(
            workoutRepository = workoutRepository,
            startSession = startSession,
            logSetUseCase = logSet,
            deleteSetUseCase = deleteSet,
            finishSessionUseCase = finishSession,
            addExerciseToDay = addExerciseToDay,
            observePlannedExercisesWithDetails = observePlannedExercises,
            supabase = supabase,
            dataStore = dataStore,
            savedStateHandle = savedState,
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
            targetWeightKg = 60f
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
            youtubeQuery = ""
        )

        // 1. Initial State: Exercise is added, 0 sets logged. State should be IDLE.
        exercisesFlow.value = listOf(plannedExercise to exercise)

        vm.uiState.test {
            var state = awaitItem()
            // Make sure the flow collected the initial exercises
            while (state.exercises.isEmpty()) {
                state = awaitItem()
            }
            assertEquals(ExerciseExecutionState.IDLE, state.executionStates["ex1"])

            // 2. Start Exercise -> ACTIVE_SET
            vm.startExercise("ex1")
            state = awaitItem()
            assertEquals(ExerciseExecutionState.ACTIVE_SET, state.executionStates["ex1"])
            assertEquals("ex1", state.activeExerciseId)

            // 3. Log 1st set (1/3 sets complete) -> since it's completeSet, transitions to resting if not done
            // To simulate completed sets flow, we update setsFlow.
            val set1 = SessionSet(
                id = "s1",
                sessionId = session.id,
                userId = "user1",
                exerciseId = "ex1",
                setNumber = 1,
                weightKg = 60f,
                reps = 10,
                completed = true,
                updatedAt = 1000L
            )
            
            // We call completeSet which updates active/resting flags
            vm.completeSet("ex1", 60f, 10)
            // completeSet invokes logSetUseCase. The setsFlow collects updated sets.
            setsFlow.value = listOf(set1)
            
            state = awaitItem()
            while (state.loggedSets.size < 1) {
                state = awaitItem()
            }
            // State should be RESTING since 1/3 sets are complete
            assertEquals(ExerciseExecutionState.RESTING, state.executionStates["ex1"])
            assertEquals("ex1", state.restingExerciseId)
            assertNull(state.activeExerciseId)

            // 4. Log 2nd set (2/3 sets complete)
            val set2 = SessionSet(
                id = "s2",
                sessionId = session.id,
                userId = "user1",
                exerciseId = "ex1",
                setNumber = 2,
                weightKg = 60f,
                reps = 10,
                completed = true,
                updatedAt = 1000L
            )
            vm.completeSet("ex1", 60f, 10)
            setsFlow.value = listOf(set1, set2)
            state = awaitItem()
            while (state.loggedSets.size < 2) {
                state = awaitItem()
            }
            assertEquals(ExerciseExecutionState.RESTING, state.executionStates["ex1"])

            // 5. Log 3rd set (3/3 sets complete) -> transitions to COMPLETED, clears active/resting flags
            val set3 = SessionSet(
                id = "s3",
                sessionId = session.id,
                userId = "user1",
                exerciseId = "ex1",
                setNumber = 3,
                weightKg = 60f,
                reps = 10,
                completed = true,
                updatedAt = 1000L
            )
            vm.completeSet("ex1", 60f, 10)
            setsFlow.value = listOf(set1, set2, set3)
            state = awaitItem()
            while (state.loggedSets.size < 3) {
                state = awaitItem()
            }
            assertEquals(ExerciseExecutionState.COMPLETED, state.executionStates["ex1"])
            assertNull(state.activeExerciseId)
            assertNull(state.restingExerciseId)

            // 6. EDGE CASE 1: Increase sets from 3 to 4. Exercise should transition to IDLE!
            val updatedPlannedExercise = plannedExercise.copy(targetSets = 4)
            exercisesFlow.value = listOf(updatedPlannedExercise to exercise)
            state = awaitItem()
            while (state.exercises.first().first.targetSets != 4) {
                state = awaitItem()
            }
            assertEquals(ExerciseExecutionState.IDLE, state.executionStates["ex1"])

            // 7. EDGE CASE 2: Decrease sets back to 3. Exercise should transition to COMPLETED!
            val updatedPlannedExercise2 = plannedExercise.copy(targetSets = 3)
            exercisesFlow.value = listOf(updatedPlannedExercise2 to exercise)
            state = awaitItem()
            while (state.exercises.first().first.targetSets != 3) {
                state = awaitItem()
            }
            assertEquals(ExerciseExecutionState.COMPLETED, state.executionStates["ex1"])

            // 8. EDGE CASE 3: Delete a set while COMPLETED (3/3 -> 2/3). Should transition back to IDLE.
            // We use vm.deleteSet(set3)
            vm.deleteSet(set3)
            // Note: deleteSet updates state optimistically!
            state = awaitItem()
            assertEquals(2, state.loggedSets.size)
            assertEquals(ExerciseExecutionState.IDLE, state.executionStates["ex1"])

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `completed session retrieved from repository marks all exercises completed and sets isCompleted true`() = runTest {
        val workoutRepository = mockk<WorkoutRepository>(relaxed = true)
        val startSession = mockk<StartSessionUseCase>()
        val logSet = mockk<LogSetUseCase>(relaxed = true)
        val deleteSet = mockk<DeleteSetUseCase>(relaxed = true)
        val finishSession = mockk<FinishSessionUseCase>(relaxed = true)
        val addExerciseToDay = mockk<AddExerciseToDayUseCase>(relaxed = true)
        val observePlannedExercises = mockk<ObservePlannedExercisesWithDetailsUseCase>()
        val supabase = mockk<SupabaseClient>(relaxed = true)
        val dataStore = mockk<DataStore<Preferences>>(relaxed = true)

        val completedSession = WorkoutSession(
            id = "completed_session_id",
            userId = "user1",
            dayId = "day1",
            date = 1000L,
            durationMinutes = 45, // Completed!
            notes = "",
            updatedAt = 1000L,
        )

        coEvery { workoutRepository.getLatestSessionForDay(any(), any(), any()) } returns completedSession
        every { observePlannedExercises(any()) } returns flowOf(
            listOf(
                PlannedExercise(
                    id = "planned1",
                    dayId = "day1",
                    userId = "user1",
                    exerciseId = "ex1",
                    orderIndex = 0,
                    updatedAt = 1000L,
                    targetSets = 3,
                    targetReps = 10,
                    targetWeightKg = 60f
                ) to Exercise(
                    id = "ex1",
                    name = "Squat",
                    category = "Strength",
                    primaryMuscles = listOf("Quads"),
                    secondaryMuscles = emptyList(),
                    equipment = "Barbell",
                    instructions = emptyList(),
                    gifUrl = "",
                    youtubeQuery = ""
                )
            )
        )
        every { workoutRepository.getSessionSets(any()) } returns flowOf(emptyList())
        every { dataStore.data } returns flowOf(emptyPreferences())

        val savedState = androidx.lifecycle.SavedStateHandle(mapOf("dayId" to "day1"))
        val vm = ActiveSessionViewModel(
            workoutRepository = workoutRepository,
            startSession = startSession,
            logSetUseCase = logSet,
            deleteSetUseCase = deleteSet,
            finishSessionUseCase = finishSession,
            addExerciseToDay = addExerciseToDay,
            observePlannedExercisesWithDetails = observePlannedExercises,
            supabase = supabase,
            dataStore = dataStore,
            savedStateHandle = savedState,
        )

        vm.uiState.test {
            var state = awaitItem()
            while (state.exercises.isEmpty()) {
                state = awaitItem()
            }
            assertTrue(state.isCompleted)
            assertEquals(ExerciseExecutionState.COMPLETED, state.executionStates["ex1"])
            cancelAndIgnoreRemainingEvents()
        }
    }
}
