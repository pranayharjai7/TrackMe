package com.trackme.ui.workout.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackme.domain.model.Exercise
import com.trackme.domain.model.PlannedExercise
import com.trackme.domain.model.SessionSet
import com.trackme.domain.repository.ExerciseRepository
import com.trackme.domain.repository.WorkoutRepository
import com.trackme.domain.usecase.FinishSessionUseCase
import com.trackme.domain.usecase.LogSetUseCase
import com.trackme.domain.usecase.StartSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class ActiveSessionUiState(
    val sessionId: String = "",
    val exercises: List<Pair<PlannedExercise, Exercise?>> = emptyList(),
    val loggedSets: List<SessionSet> = emptyList(),
    val restTimerRunning: Boolean = false,
    val restSeconds: Int = 90,
    val restSecondsRemaining: Int = 90,
    val isFinishing: Boolean = false,
)

@HiltViewModel
class ActiveSessionViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    private val startSession: StartSessionUseCase,
    private val logSetUseCase: LogSetUseCase,
    private val finishSessionUseCase: FinishSessionUseCase,
    private val supabase: SupabaseClient,
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
            val todayStart = run {
                val cal = java.util.Calendar.getInstance()
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                cal.set(java.util.Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            val session = try {
                workoutRepository.getInProgressSessionForDay(userId, dayId, todayStart)
                    ?: startSession(userId, dayId)
            } catch (e: Exception) {
                return@launch
            }
            sessionStartTime = session.date
            _uiState.update { it.copy(sessionId = session.id) }

            launch {
                workoutRepository.getPlannedExercisesForDay(dayId).collect { planned ->
                    val withDetails = planned.map { pe -> pe to exerciseRepository.getById(pe.exerciseId) }
                    _uiState.update { it.copy(exercises = withDetails) }
                }
            }
            launch {
                workoutRepository.getSessionSets(session.id).collect { sets ->
                    _uiState.update { it.copy(loggedSets = sets) }
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

    fun setsForExercise(exerciseId: String): List<SessionSet> =
        _uiState.value.loggedSets.filter { it.exerciseId == exerciseId }

    fun plannedFor(exerciseId: String): PlannedExercise? =
        _uiState.value.exercises.find { it.first.exerciseId == exerciseId }?.first
}
