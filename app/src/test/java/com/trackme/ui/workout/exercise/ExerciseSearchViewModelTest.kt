package com.trackme.ui.workout.exercise

import app.cash.turbine.test
import com.trackme.domain.model.Exercise
import com.trackme.domain.repository.WorkoutRepository
import com.trackme.domain.usecase.AddExerciseToDayUseCase
import com.trackme.domain.usecase.SearchExercisesUseCase
import io.github.jan.supabase.SupabaseClient
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseSearchViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val searchUseCase = mockk<SearchExercisesUseCase>()
    private val addExerciseToDay = mockk<AddExerciseToDayUseCase>(relaxed = true)
    private val workoutRepository = mockk<WorkoutRepository>(relaxed = true)
    private val supabase = mockk<SupabaseClient>(relaxed = true)
    private lateinit var viewModel: ExerciseSearchViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val fakeExercise = Exercise(
            id = "bench-press",
            name = "Bench Press",
            category = "Strength",
            primaryMuscles = listOf("chest"),
            secondaryMuscles = listOf("triceps"),
            equipment = "barbell",
            instructions = listOf("Step 1"),
            gifUrl = "",
            youtubeQuery = "bench press tutorial",
        )
        every { searchUseCase(any()) } returns flowOf(listOf(fakeExercise))
        viewModel = ExerciseSearchViewModel(
            searchUseCase,
            addExerciseToDay,
            workoutRepository,
            supabase,
            androidx.lifecycle.SavedStateHandle(mapOf("dayId" to "")),
        )
    }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `initial state is empty`() = runTest {
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(emptyList<Exercise>(), state.results)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updating query emits new state`() = runTest {
        viewModel.uiState.test {
            awaitItem() // initial
            viewModel.onQueryChange("bench")
            advanceTimeBy(400)
            val state = awaitItem()
            assertEquals("Bench Press", state.results.first().name)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
