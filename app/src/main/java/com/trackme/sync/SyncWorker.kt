package com.trackme.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.trackme.data.local.dao.*
import com.trackme.data.local.entity.PendingDeletionEntity
import com.trackme.data.remote.supabase.WorkoutRemoteSource
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val workoutPlanDao: WorkoutPlanDao,
    private val workoutDayDao: WorkoutDayDao,
    private val plannedExerciseDao: PlannedExerciseDao,
    private val workoutSessionDao: WorkoutSessionDao,
    private val sessionSetDao: SessionSetDao,
    private val pendingDeletionDao: PendingDeletionDao,
    private val remoteSource: WorkoutRemoteSource,
    private val supabase: SupabaseClient,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = runCatching {
        val userId = supabase.auth.currentSessionOrNull()?.user?.id ?: return Result.success()

        // Step 1: Push pending offline deletions to remote before the LWW merge runs.
        // This prevents the merge from re-inserting items the user deleted while offline.
        pendingDeletionDao.getAllForUser(userId).forEach { op ->
            runCatching { remoteSource.markDeleted(op.tableName, op.entityId, op.userId, op.deletedAt) }
                .onSuccess { pendingDeletionDao.deleteById(op.entityId) }
        }

        // Step 2: LWW merge. Reload pending — only failures from step 1 remain.
        val failedPending = pendingDeletionDao.getAllForUser(userId).associateBy { it.entityId }

        mergePlans(userId, failedPending)
        mergeDays(userId, failedPending)
        mergePlannedExercises(userId, failedPending)
        mergeSessions(userId, failedPending)
        mergeSets(userId, failedPending)
    }.fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })

    private suspend fun mergePlans(userId: String, failedPending: Map<String, PendingDeletionEntity>) {
        val remoteMap = remoteSource.fetchPlans(userId).associateBy { it.id }
        val localMap = workoutPlanDao.getAllForSync(userId).associateBy { it.id }
        (remoteMap.keys + localMap.keys).forEach { id ->
            val remote = remoteMap[id]
            val local = localMap[id]
            when {
                local == null -> workoutPlanDao.insert(remote!!)
                remote == null -> { remoteSource.upsertPlan(local); workoutPlanDao.markSynced(local.id) }
                remote.updatedAt > local.updatedAt -> workoutPlanDao.insert(remote)
                local.updatedAt > remote.updatedAt || !local.isSynced -> { remoteSource.upsertPlan(local); workoutPlanDao.markSynced(local.id) }
            }
        }
    }

    private suspend fun mergeDays(userId: String, failedPending: Map<String, PendingDeletionEntity>) {
        val remoteMap = remoteSource.fetchDays(userId).associateBy { it.id }
        val localMap = workoutDayDao.getAllForSync(userId).associateBy { it.id }
        val pendingForTable = failedPending.filter { it.value.tableName == "workout_days" }
        (remoteMap.keys + localMap.keys + pendingForTable.keys).forEach { id ->
            val remote = remoteMap[id]
            val local = localMap[id]
            val pending = pendingForTable[id]
            when {
                // Offline-deleted item: local was wiped on sign-out, remote push failed.
                // Insert locally as soft-deleted so UI never shows it.
                // Keep isSynced=false so the next LWW run will push deletedAt to remote.
                pending != null && local == null -> {
                    if (remote != null) {
                        val ts = maxOf(remote.updatedAt, pending.deletedAt)
                        workoutDayDao.insert(remote.copy(deletedAt = ts, updatedAt = ts, isSynced = false))
                    } else {
                        pendingDeletionDao.deleteById(id)
                    }
                }
                // Recovery: local was re-inserted as active by a previous bad sync despite a pending deletion.
                pending != null && local != null && local.deletedAt == null -> {
                    workoutDayDao.softDelete(local.id, pending.deletedAt)
                }
                local == null -> workoutDayDao.insert(remote!!)
                remote == null -> { remoteSource.upsertDay(local); workoutDayDao.markSynced(local.id); pendingDeletionDao.deleteById(local.id) }
                remote.updatedAt > local.updatedAt -> workoutDayDao.insert(remote)
                local.updatedAt > remote.updatedAt || !local.isSynced -> { remoteSource.upsertDay(local); workoutDayDao.markSynced(local.id); pendingDeletionDao.deleteById(local.id) }
                else -> pendingDeletionDao.deleteById(id)
            }
        }
    }

    private suspend fun mergePlannedExercises(userId: String, failedPending: Map<String, PendingDeletionEntity>) {
        val remoteMap = remoteSource.fetchPlannedExercises(userId).associateBy { it.id }
        val localMap = plannedExerciseDao.getAllForSync(userId).associateBy { it.id }
        val pendingForTable = failedPending.filter { it.value.tableName == "planned_exercises" }
        (remoteMap.keys + localMap.keys + pendingForTable.keys).forEach { id ->
            val remote = remoteMap[id]
            val local = localMap[id]
            val pending = pendingForTable[id]
            when {
                pending != null && local == null -> {
                    if (remote != null) {
                        val ts = maxOf(remote.updatedAt, pending.deletedAt)
                        plannedExerciseDao.insert(remote.copy(deletedAt = ts, updatedAt = ts, isSynced = false))
                    } else {
                        pendingDeletionDao.deleteById(id)
                    }
                }
                pending != null && local != null && local.deletedAt == null -> {
                    plannedExerciseDao.softDelete(local.id, pending.deletedAt)
                }
                local == null -> plannedExerciseDao.insert(remote!!)
                remote == null -> { remoteSource.upsertPlannedExercise(local); plannedExerciseDao.markSynced(local.id); pendingDeletionDao.deleteById(local.id) }
                remote.updatedAt > local.updatedAt -> plannedExerciseDao.insert(remote)
                local.updatedAt > remote.updatedAt || !local.isSynced -> { remoteSource.upsertPlannedExercise(local); plannedExerciseDao.markSynced(local.id); pendingDeletionDao.deleteById(local.id) }
                else -> pendingDeletionDao.deleteById(id)
            }
        }
    }

    private suspend fun mergeSessions(userId: String, failedPending: Map<String, PendingDeletionEntity>) {
        val remoteMap = remoteSource.fetchSessions(userId).associateBy { it.id }
        val localMap = workoutSessionDao.getAllForSync(userId).associateBy { it.id }
        (remoteMap.keys + localMap.keys).forEach { id ->
            val remote = remoteMap[id]
            val local = localMap[id]
            when {
                local == null -> workoutSessionDao.insert(remote!!)
                remote == null -> { remoteSource.upsertSession(local); workoutSessionDao.markSynced(local.id) }
                remote.updatedAt > local.updatedAt -> workoutSessionDao.insert(remote)
                local.updatedAt > remote.updatedAt || !local.isSynced -> { remoteSource.upsertSession(local); workoutSessionDao.markSynced(local.id) }
            }
        }
    }

    private suspend fun mergeSets(userId: String, failedPending: Map<String, PendingDeletionEntity>) {
        val remoteMap = remoteSource.fetchSets(userId).associateBy { it.id }
        val localMap = sessionSetDao.getAllForSync(userId).associateBy { it.id }
        val pendingForTable = failedPending.filter { it.value.tableName == "session_sets" }
        (remoteMap.keys + localMap.keys + pendingForTable.keys).forEach { id ->
            val remote = remoteMap[id]
            val local = localMap[id]
            val pending = pendingForTable[id]
            when {
                pending != null && local == null -> {
                    if (remote != null) {
                        val ts = maxOf(remote.updatedAt, pending.deletedAt)
                        sessionSetDao.insert(remote.copy(deletedAt = ts, updatedAt = ts, isSynced = false))
                    } else {
                        pendingDeletionDao.deleteById(id)
                    }
                }
                pending != null && local != null && local.deletedAt == null -> {
                    sessionSetDao.softDelete(local.id, pending.deletedAt)
                }
                local == null -> sessionSetDao.insert(remote!!)
                remote == null -> { remoteSource.upsertSet(local); sessionSetDao.markSynced(local.id); pendingDeletionDao.deleteById(local.id) }
                remote.updatedAt > local.updatedAt -> sessionSetDao.insert(remote)
                local.updatedAt > remote.updatedAt || !local.isSynced -> { remoteSource.upsertSet(local); sessionSetDao.markSynced(local.id); pendingDeletionDao.deleteById(local.id) }
                else -> pendingDeletionDao.deleteById(id)
            }
        }
    }
}
