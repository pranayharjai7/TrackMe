package com.trackme.ui.progress

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackme.domain.analytics.ProgressAnalyticsEngine
import com.trackme.domain.analytics.models.*
import com.trackme.domain.model.HealthSnapshot
import com.trackme.domain.model.PersonalRecord
import com.trackme.domain.model.SessionSet
import com.trackme.domain.repository.HealthRepository
import com.trackme.domain.usecase.GetHealthSnapshotsUseCase
import com.trackme.domain.usecase.GetPersonalRecordsUseCase
import com.trackme.domain.repository.WorkoutRepository
import com.trackme.utils.millisDaysAgo
import com.trackme.utils.startOfLocalDayMillis
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProgressUiState(
    val isLoading: Boolean = true,
    val readinessScore: ReadinessScore? = null,
    val oneRmProjections: List<OneRMProjection> = emptyList(),
    val muscleFatigueMap: Map<String, MuscleFatigue> = emptyMap(),
    val plateauAlerts: List<PlateauAlert> = emptyList(),
    val matrixPosition: ConsistencyMatrixPosition? = null,
    val personalRecords: List<PersonalRecord> = emptyList(),
    val weightHistory: List<HealthSnapshot> = emptyList(),
    val exerciseOptions: List<String> = emptyList(),
    val selectedExerciseId: String? = null,
    val fullAnalytics: FullProgressAnalytics? = null,
)

@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val getPersonalRecords: GetPersonalRecordsUseCase,
    private val getHealthSnapshots: GetHealthSnapshotsUseCase,
    private val workoutRepository: WorkoutRepository,
    private val progressAnalyticsEngine: ProgressAnalyticsEngine,
    private val supabase: SupabaseClient,
    private val healthRepository: HealthRepository,
    private val dataStore: DataStore<Preferences>,
) : ViewModel() {

    private val _userId = MutableStateFlow(supabase.auth.currentSessionOrNull()?.user?.id ?: "")
    private val _selectedExerciseId = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            _userId.collect { uid ->
                if (uid.isNotEmpty()) {
                    val isBootstrapped = dataStore.data.map { it[booleanPreferencesKey("health_connect_bootstrapped")] ?: false }.first()
                    if (!isBootstrapped) {
                        try {
                            healthRepository.syncFromHealthConnectBootstrap(uid)
                            dataStore.edit { it[booleanPreferencesKey("health_connect_bootstrapped")] = true }
                        } catch (e: Exception) {
                            // Suppress bootstrap errors
                        }
                    } else {
                        try {
                            healthRepository.syncFromHealthConnect(uid)
                        } catch (e: Exception) {
                            // Suppress sync errors
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ProgressUiState> = _userId
        .filter { it.isNotEmpty() }
        .distinctUntilChanged()
        .flatMapLatest { uid ->
            // Pull raw data
            val ninetyDaysAgo = millisDaysAgo(90)
            combine(
                getPersonalRecords(uid),
                getHealthSnapshots(uid, 90),
                workoutRepository.getSessionsSince(uid, ninetyDaysAgo),
                workoutRepository.getSetsSince(uid, ninetyDaysAgo),
                _selectedExerciseId,
            ) { prs, snapshots, sessions, sets, selectedId ->
                
                // Map to pure models
                val healthMetricsData = snapshots.map {
                    HealthMetricsData(
                        dateMillis = it.date,
                        hrvRmssd = it.hrvRmssd,
                        restingHeartRate = it.restingHeartRate ?: it.heartRateAvg, // Fallback to avg if RHR is missing
                        sleepDurationMinutes = it.sleepDurationMinutes,
                        deepSleepMinutes = it.deepSleepMinutes,
                        weightKg = it.weightKg,
                        heightCm = it.heightCm,
                        steps = it.steps,
                        activeCaloriesBurned = it.activeCaloriesBurned
                    )
                }

                val setsBySession = sets.filter { it.completed }.groupBy { it.sessionId }
                
                val workoutSessionAnalyticsData = sessions.map { session ->
                    val sessionSets = setsBySession[session.id] ?: emptyList()
                    val totalVolume = sessionSets.sumOf { (it.weightKg * it.reps).toDouble() }.toFloat()
                    
                    val exercises = sessionSets.map { set ->
                        ExerciseAnalyticsData(
                            setId = set.id,
                            exerciseId = set.exerciseId,
                            name = set.exerciseId.replaceFirstChar { c -> c.uppercase() }, // Simple placeholder name format
                            dateMillis = set.updatedAt,
                            targetMuscles = emptyList(), // In reality we'd look up the Exercise dictionary here
                            weightKg = set.weightKg,
                            reps = set.reps,
                            durationSeconds = set.durationSeconds
                        )
                    }

                    WorkoutSessionAnalyticsData(
                        sessionId = session.id,
                        dateMillis = session.date,
                        durationMinutes = session.durationMinutes,
                        totalVolumeKg = totalVolume,
                        exercises = exercises
                    )
                }

                val maxSnapshotsUpdatedAt = snapshots.maxOfOrNull { it.updatedAt } ?: 0L
                val maxSessionsUpdatedAt = sessions.maxOfOrNull { it.updatedAt } ?: 0L
                val maxSetsUpdatedAt = sets.maxOfOrNull { it.updatedAt } ?: 0L
                val latestDataUpdateTime = maxOf(maxSnapshotsUpdatedAt, maxSessionsUpdatedAt, maxSetsUpdatedAt)

                // Delegate heavy computing to the Engine (automatically runs on Dispatchers.Default)
                val engineResult = progressAnalyticsEngine.computeAnalytics(
                    userId = uid,
                    workoutHistory = workoutSessionAnalyticsData,
                    healthHistory = healthMetricsData,
                    latestDataUpdateTime = latestDataUpdateTime
                )

                val options = prs.map { it.exerciseId }.distinct().sorted()
                val effectiveSelectedId = selectedId ?: prs.maxByOrNull { it.maxWeightKg }?.exerciseId

                ProgressUiState(
                    isLoading = false,
                    readinessScore = engineResult.readinessScore,
                    oneRmProjections = engineResult.oneRmProjections,
                    muscleFatigueMap = engineResult.muscleFatigueMap,
                    plateauAlerts = engineResult.plateauAlerts,
                    matrixPosition = engineResult.matrixPosition,
                    personalRecords = prs,
                    weightHistory = snapshots,
                    exerciseOptions = options,
                    selectedExerciseId = effectiveSelectedId,
                    fullAnalytics = engineResult.fullAnalytics
                )
            }
        }
        // Isolate UI from heavy downstream transformations
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProgressUiState())

    @OptIn(ExperimentalCoroutinesApi::class)
    val strengthHistory: StateFlow<List<SessionSet>> = combine(
        _userId.filter { it.isNotEmpty() },
        _selectedExerciseId,
    ) { uid, selId -> uid to selId }
        .flatMapLatest { (uid, selId) ->
            if (selId != null) {
                workoutRepository.getHistoryForExercise(uid, selId)
            } else {
                uiState.mapNotNull { it.selectedExerciseId }
                    .distinctUntilChanged()
                    .flatMapLatest { exerciseId -> workoutRepository.getHistoryForExercise(uid, exerciseId) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectExercise(exerciseId: String) {
        _selectedExerciseId.value = exerciseId
    }
}
