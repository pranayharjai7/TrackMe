package com.trackme.wear.comm

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.trackme.ui.workout.session.WorkoutSessionManager
import com.trackme.wear.health.WearMetricIngestor
import com.trackme.wear.session.SessionStatePublisher
import com.trackme.wearbridge.HealthMetricBatchPayload
import com.trackme.wearbridge.SwitchDirection
import com.trackme.wearbridge.WatchCommandPayload
import com.trackme.wearbridge.WatchCommandType
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

@Singleton
class WearCommandHandler @Inject constructor(
    private val sessionManager: WorkoutSessionManager,
    private val sessionStatePublisher: SessionStatePublisher,
    private val metricIngestor: WearMetricIngestor,
    private val supabase: SupabaseClient,
    private val dataStore: DataStore<Preferences>,
) {
    private val mutex = Mutex()
    private val appliedCommandsKey = stringSetPreferencesKey("wear_applied_command_ids")
    private val userId: String
        get() = runCatching { supabase.auth.currentSessionOrNull()?.user?.id }.getOrNull().orEmpty()

    suspend fun handleCommand(command: WatchCommandPayload) = mutex.withLock {
        if (command.type == WatchCommandType.REQUEST_SNAPSHOT) {
            sessionStatePublisher.publishNow()
            return@withLock
        }

        if (hasApplied(command.commandId)) {
            sessionStatePublisher.publishNow()
            return@withLock
        }

        ensureSessionLoaded(command)
        val currentState = sessionManager.uiState.value
        if (command.sessionId.isNotBlank() &&
            currentState.sessionId.isNotBlank() &&
            command.sessionId != currentState.sessionId &&
            command.type != WatchCommandType.START_SESSION
        ) {
            Log.w("WearCommandHandler", "Rejected stale Wear command ${command.type} for ${command.sessionId}")
            markApplied(command.commandId)
            sessionStatePublisher.publishNow()
            return@withLock
        }

        when (command.type) {
            WatchCommandType.START_SESSION -> handleStartSession(command)
            WatchCommandType.LOG_SET -> handleLogSet(command)
            WatchCommandType.DELETE_SET -> handleDeleteSet(command)
            WatchCommandType.EDIT_SET -> handleEditSet(command)
            WatchCommandType.SKIP_REST -> handleSkipRest(command)
            WatchCommandType.SWITCH_EXERCISE -> handleSwitchExercise(command)
            WatchCommandType.FINISH_WORKOUT -> sessionManager.finishSession {}
            WatchCommandType.PAUSE_WORKOUT -> sessionManager.pauseWorkout()
            WatchCommandType.RESUME_WORKOUT -> sessionManager.resumeWorkout()
            WatchCommandType.REQUEST_SNAPSHOT -> Unit
        }

        markApplied(command.commandId)
        delay(150)
        sessionStatePublisher.publishNow()
    }

    suspend fun handleHealthBatch(batch: HealthMetricBatchPayload) {
        metricIngestor.ingest(userId, batch)
    }

    private suspend fun handleStartSession(command: WatchCommandPayload) {
        val dayId = command.dayId ?: sessionManager.uiState.value.dayId
        if (dayId.isBlank()) return
        sessionManager.startOrResumeSession(
            userId = userId,
            targetDayId = dayId,
            sessionDateMillis = command.sessionDateMillis ?: System.currentTimeMillis(),
        )
        waitForSession()
    }

    private fun handleLogSet(command: WatchCommandPayload) {
        val exerciseId = command.exerciseId ?: currentExerciseId() ?: return
        val log = command.log ?: return
        sessionManager.completeSet(
            exerciseId = exerciseId,
            weightKg = log.weightKg,
            reps = log.reps,
            durationSeconds = log.durationSeconds,
            distanceKm = log.distanceKm,
            speedKmh = log.speedKmh,
            inclinePercent = log.inclinePercent,
        )
    }

    private fun handleEditSet(command: WatchCommandPayload) {
        val setId = command.setId ?: return
        val exerciseId = command.exerciseId ?: sessionManager.uiState.value.loggedSets.firstOrNull { it.id == setId }?.exerciseId ?: return
        val existing = sessionManager.uiState.value.loggedSets.firstOrNull { it.id == setId }
        val log = command.log ?: return
        sessionManager.editSet(
            setId = setId,
            exerciseId = exerciseId,
            setNumber = command.setNumber ?: existing?.setNumber ?: 1,
            weightKg = log.weightKg,
            reps = log.reps,
            durationSeconds = log.durationSeconds,
            distanceKm = log.distanceKm,
            speedKmh = log.speedKmh,
            inclinePercent = log.inclinePercent,
        )
    }

    private fun handleDeleteSet(command: WatchCommandPayload) {
        val state = sessionManager.uiState.value
        val set = command.setId?.let { id -> state.loggedSets.firstOrNull { it.id == id } }
            ?: state.loggedSets.firstOrNull { set ->
                command.exerciseId == set.exerciseId && command.setNumber == set.setNumber
            }
            ?: return
        sessionManager.deleteSet(set)
    }

    private fun handleSkipRest(command: WatchCommandPayload) {
        val restingId = command.exerciseId ?: sessionManager.uiState.value.restingExerciseId ?: return
        sessionManager.skipRest(restingId)
    }

    private fun handleSwitchExercise(command: WatchCommandPayload) {
        val state = sessionManager.uiState.value
        val ordered = state.exercises.sortedBy { it.first.orderIndex }
        val currentId = command.exerciseId ?: currentExerciseId()
        when (command.switchDirection) {
            SwitchDirection.NEXT -> currentId?.let { sessionManager.skipExercise(it) }
                ?: ordered.firstOrNull()?.first?.exerciseId?.let(sessionManager::startExercise)
            SwitchDirection.PREVIOUS -> currentId?.let { sessionManager.previousExercise(it) }
            SwitchDirection.INDEX -> command.exerciseIndex
                ?.let { ordered.getOrNull(it) }
                ?.first
                ?.exerciseId
                ?.let(sessionManager::startExercise)
            null -> command.exerciseId?.let(sessionManager::startExercise)
        }
    }

    private suspend fun ensureSessionLoaded(command: WatchCommandPayload) {
        if (sessionManager.uiState.value.sessionId.isNotBlank()) return
        val dayId = command.dayId ?: return
        if (userId.isBlank()) return
        sessionManager.startOrResumeSession(userId, dayId, command.sessionDateMillis ?: System.currentTimeMillis())
        waitForSession()
    }

    private suspend fun waitForSession() {
        withTimeoutOrNull(2_500) {
            sessionManager.uiState.map { it.sessionId }.first { it.isNotBlank() }
        }
    }

    private fun currentExerciseId(): String? {
        val state = sessionManager.uiState.value
        return state.activeExerciseId
            ?: state.restingExerciseId
            ?: state.exercises.sortedBy { it.first.orderIndex }
                .firstOrNull { (planned, _) ->
                    (state.loggedSetsByExercise[planned.exerciseId]?.size ?: 0) < planned.targetSets
                }
                ?.first
                ?.exerciseId
    }

    private suspend fun hasApplied(commandId: String): Boolean =
        dataStore.data.map { prefs -> prefs[appliedCommandsKey].orEmpty().contains(commandId) }.first()

    private suspend fun markApplied(commandId: String) {
        dataStore.edit { prefs ->
            val applied = prefs[appliedCommandsKey].orEmpty()
            val trimmed = (applied + commandId).toList().takeLast(300).toSet()
            prefs[appliedCommandsKey] = trimmed
        }
    }
}
