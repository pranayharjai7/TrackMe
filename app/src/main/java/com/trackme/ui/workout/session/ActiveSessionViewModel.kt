package com.trackme.ui.workout.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackme.domain.model.Exercise
import com.trackme.domain.model.PlannedExercise
import com.trackme.domain.model.SessionSet
import com.trackme.domain.repository.WorkoutRepository
import com.trackme.domain.usecase.AddExerciseToDayUseCase
import com.trackme.domain.usecase.FinishSessionUseCase
import com.trackme.domain.usecase.LogSetUseCase
import com.trackme.domain.usecase.ObservePlannedExercisesWithDetailsUseCase
import com.trackme.domain.usecase.StartSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.trackme.ui.onboarding.DEFAULT_INPUT_STYLE
import com.trackme.ui.onboarding.PREF_INPUT_STYLE
import com.trackme.utils.startOfTodayMillis
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class ActiveSessionUiState(
    val sessionId: String = "",
    val exercises: List<Pair<PlannedExercise, Exercise?>> = emptyList(),
    val loggedSets: List<SessionSet> = emptyList(),
    val loggedSetsByExercise: Map<String, List<SessionSet>> = emptyMap(),
    val restTimerRunning: Boolean = false,
    val restSeconds: Int = 90,
    val restSecondsRemaining: Int = 90,
    val isFinishing: Boolean = false,
    val inputStyle: String = DEFAULT_INPUT_STYLE,
)

/**
 * ViewModel responsible for a live workout session.
 *
 * Architecture Layer: ViewModel (MVVM)
 *
 * Responsibilities:
 * - Start or resume today's in-progress session for the selected workout day.
 * - Expose planned exercises, exercise metadata, logged sets, and rest timer state.
 * - Delegate persistence and personal-record logic to domain use cases/repositories.
 */
@HiltViewModel
class ActiveSessionViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val startSession: StartSessionUseCase,
    private val logSetUseCase: LogSetUseCase,
    private val finishSessionUseCase: FinishSessionUseCase,
    private val addExerciseToDay: AddExerciseToDayUseCase,
    private val observePlannedExercisesWithDetails: ObservePlannedExercisesWithDetailsUseCase,
    private val supabase: SupabaseClient,
    private val dataStore: DataStore<Preferences>,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val dayId: String = checkNotNull(savedStateHandle["dayId"])
    private val userId get() = runCatching { supabase.auth.currentSessionOrNull()?.user?.id }.getOrNull() ?: ""

    private val _uiState = MutableStateFlow(ActiveSessionUiState())
    val uiState: StateFlow<ActiveSessionUiState> = _uiState.asStateFlow()

    private var sessionStartTime = System.currentTimeMillis()
    private var restTimerJob: Job? = null

    init {
        viewModelScope.launch {
            dataStore.data.collect { prefs ->
                _uiState.update { it.copy(inputStyle = prefs[PREF_INPUT_STYLE] ?: DEFAULT_INPUT_STYLE) }
            }
        }
        
        viewModelScope.launch {
            val session = try {
                workoutRepository.getInProgressSessionForDay(userId, dayId, startOfTodayMillis())
                    ?: startSession(userId, dayId)
            } catch (e: Exception) {
                return@launch
            }
            sessionStartTime = session.date
            _uiState.update { it.copy(sessionId = session.id) }

            launch {
                observePlannedExercisesWithDetails(dayId).collect { withDetails ->
                    _uiState.update { it.copy(exercises = withDetails) }
                }
            }
            launch {
                workoutRepository.getSessionSets(session.id).collect { sets ->
                    _uiState.update {
                        it.copy(
                            loggedSets = sets,
                            loggedSetsByExercise = sets.groupBy { set -> set.exerciseId },
                        )
                    }
                }
            }
        }
    }

    fun logSet(
        exerciseId: String,
        setNumber: Int,
        weightKg: Float,
        reps: Int,
        durationSeconds: Int? = null,
        distanceKm: Float? = null,
        speedKmh: Float? = null,
        inclinePercent: Float? = null,
    ) {
        // Logging delegates to LogSetUseCase so PR updates and persistence stay in domain/data layers.
        viewModelScope.launch {
            logSetUseCase(
                sessionId = _uiState.value.sessionId,
                userId = userId,
                exerciseId = exerciseId,
                setNumber = setNumber,
                weightKg = weightKg,
                reps = reps,
                durationSeconds = durationSeconds,
                distanceKm = distanceKm,
                speedKmh = speedKmh,
                inclinePercent = inclinePercent,
            )
            startRestTimer()
        }
    }

    private fun startRestTimer() {
        // A single Job owns the timer so repeated set logs reset the countdown cleanly.
        restTimerJob?.cancel()
        val seconds = _uiState.value.restSeconds
        _uiState.update { it.copy(restTimerRunning = true, restSecondsRemaining = seconds) }
        restTimerJob = viewModelScope.launch {
            for (remaining in seconds downTo 0) {
                _uiState.update { it.copy(restSecondsRemaining = remaining) }
                if (remaining > 0) delay(1_000)
            }
            _uiState.update { it.copy(restTimerRunning = false) }
        }
    }

    fun stopRestTimer() {
        restTimerJob?.cancel()
        _uiState.update { it.copy(restTimerRunning = false) }
    }

    fun setRestDuration(seconds: Int) = _uiState.update { it.copy(restSeconds = seconds) }

    fun finishSession(onDone: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isFinishing = true) }
            finishSessionUseCase(_uiState.value.sessionId, sessionStartTime)
            onDone()
        }
    }

    fun updateTargetSets(exerciseId: String, sets: Int) {
        viewModelScope.launch {
            val planned = _uiState.value.exercises.find { it.first.exerciseId == exerciseId }?.first
            if (planned != null) {
                val updated = planned.copy(targetSets = sets)
                workoutRepository.updatePlannedExercise(updated)
            }
        }
    }

    fun setsForExercise(exerciseId: String): List<SessionSet> =
        _uiState.value.loggedSetsByExercise[exerciseId].orEmpty()

    fun plannedFor(exerciseId: String): PlannedExercise? =
        _uiState.value.exercises.find { it.first.exerciseId == exerciseId }?.first

    fun addExercise(exerciseId: String) {
        viewModelScope.launch {
            val nextIndex = _uiState.value.exercises.size
            addExerciseToDay(dayId, userId, exerciseId, nextIndex)
        }
    }
}
