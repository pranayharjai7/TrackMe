package com.trackme.wearable.health

import android.content.Context
import android.util.Log
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseLapSummary
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseUpdate
import com.trackme.wearbridge.HealthMetricSamplePayload
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WearHealthSnapshot(
    val heartRateBpm: Double? = null,
    val steps: Double? = null,
    val activeCalories: Double? = null,
    val distanceMeters: Double? = null,
    val durationSeconds: Long = 0L,
)

class WearHealthMetricCollector(context: Context) {
    private val exerciseClient = HealthServices.getClient(context.applicationContext).exerciseClient
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _snapshot = MutableStateFlow(WearHealthSnapshot())
    val snapshot: StateFlow<WearHealthSnapshot> = _snapshot.asStateFlow()

    private val _samples = MutableSharedFlow<List<HealthMetricSamplePayload>>(extraBufferCapacity = 8)
    val samples: SharedFlow<List<HealthMetricSamplePayload>> = _samples.asSharedFlow()

    private var samplingJob: Job? = null
    private var startedAt = 0L
    private var lastSampleAt = 0L
    private var estimatedCaloriesKcal = 0.0
    private var activeSessionId = ""

    private val callback = object : ExerciseUpdateCallback {
        override fun onRegistered() = Unit

        override fun onRegistrationFailed(throwable: Throwable) {
            Log.w("WearHealthCollector", "Health Services callback registration failed", throwable)
        }

        override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
            val metrics = update.latestMetrics
            val heartRate = metrics.getData(DataType.HEART_RATE_BPM).lastOrNull()?.value
            val steps = runCatching { metrics.getData(DataType.STEPS_TOTAL)?.total?.toDouble() }.getOrNull()
            val calories = runCatching { metrics.getData(DataType.CALORIES_TOTAL)?.total }.getOrNull()
            val distance = runCatching { metrics.getData(DataType.DISTANCE_TOTAL)?.total }.getOrNull()
            _snapshot.value = _snapshot.value.copy(
                heartRateBpm = heartRate ?: _snapshot.value.heartRateBpm,
                steps = steps ?: _snapshot.value.steps,
                activeCalories = calories ?: _snapshot.value.activeCalories,
                distanceMeters = distance ?: _snapshot.value.distanceMeters,
            )
        }

        override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) = Unit

        override fun onAvailabilityChanged(dataType: DataType<*, *>, availability: Availability) = Unit

    }

    fun start(sessionId: String, samplingIntervalMillis: () -> Long = { 5_000L }) {
        if (activeSessionId == sessionId && samplingJob?.isActive == true) return
        activeSessionId = sessionId
        startedAt = System.currentTimeMillis()
        lastSampleAt = startedAt
        estimatedCaloriesKcal = 0.0
        _snapshot.value = WearHealthSnapshot()

        val config = ExerciseConfig.builder(ExerciseType.WORKOUT)
            .setDataTypes(
                setOf(
                    DataType.HEART_RATE_BPM,
                    DataType.STEPS_TOTAL,
                    DataType.CALORIES_TOTAL,
                    DataType.DISTANCE_TOTAL,
                )
            )
            .setIsGpsEnabled(false)
            .setIsAutoPauseAndResumeEnabled(false)
            .build()

        runCatching {
            exerciseClient.setUpdateCallback(executor, callback)
            exerciseClient.startExerciseAsync(config)
        }.onFailure { Log.w("WearHealthCollector", "Unable to start Health Services exercise", it) }

        samplingJob?.cancel()
        samplingJob = scope.launch {
            while (true) {
                val interval = samplingIntervalMillis().coerceIn(5_000L, 15_000L)
                delay(interval)
                val now = System.currentTimeMillis()
                updateEstimatedCalories(now, interval)
                val durationSeconds = ((now - startedAt) / 1_000).coerceAtLeast(0L)
                val current = _snapshot.value.copy(
                    durationSeconds = durationSeconds,
                    activeCalories = _snapshot.value.activeCalories ?: estimatedCaloriesKcal,
                )
                _snapshot.value = current
                _samples.emit(current.toSamples(now))
            }
        }
    }

    fun stop() {
        samplingJob?.cancel()
        samplingJob = null
        activeSessionId = ""
        runCatching {
            exerciseClient.endExerciseAsync()
            exerciseClient.clearUpdateCallbackAsync(callback)
        }.onFailure { Log.w("WearHealthCollector", "Unable to stop Health Services exercise", it) }
    }

    private fun WearHealthSnapshot.toSamples(now: Long): List<HealthMetricSamplePayload> = buildList {
        heartRateBpm?.let {
            add(HealthMetricSamplePayload("HEART_RATE", it, "bpm", now, now))
        }
        steps?.let {
            add(HealthMetricSamplePayload("STEPS", it, "count", now, now))
        }
        activeCalories?.let {
            add(HealthMetricSamplePayload("ACTIVE_CALORIES", it, "kcal", now, now))
        }
        distanceMeters?.let {
            add(HealthMetricSamplePayload("DISTANCE", it, "m", now, now))
        }
        add(HealthMetricSamplePayload("DURATION", durationSeconds.toDouble(), "s", now, now))
    }

    private fun updateEstimatedCalories(now: Long, intervalMillis: Long) {
        val elapsedHours = ((now - lastSampleAt).coerceAtLeast(intervalMillis)).toDouble() / 3_600_000.0
        val met = if (intervalMillis >= 15_000L) 2.0 else 6.0
        val assumedWeightKg = 75.0
        estimatedCaloriesKcal += met * assumedWeightKg * elapsedHours
        lastSampleAt = now
    }
}
