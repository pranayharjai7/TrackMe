package com.trackme.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.trackme.data.local.dao.*
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
    private val remoteSource: WorkoutRemoteSource,
    private val supabase: SupabaseClient,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = runCatching {
        val userId = supabase.auth.currentSessionOrNull()?.user?.id ?: return Result.success()
        mergePlans(userId)
        mergeDays(userId)
        mergePlannedExercises(userId)
        mergeSessions(userId)
        mergeSets(userId)
    }.fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })

    private suspend fun mergePlans(userId: String) {
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

    private suspend fun mergeDays(userId: String) {
        val remoteMap = remoteSource.fetchDays(userId).associateBy { it.id }
        val localMap = workoutDayDao.getAllForSync(userId).associateBy { it.id }
        (remoteMap.keys + localMap.keys).forEach { id ->
            val remote = remoteMap[id]
            val local = localMap[id]
            when {
                local == null -> workoutDayDao.insert(remote!!)
                remote == null -> { remoteSource.upsertDay(local); workoutDayDao.markSynced(local.id) }
                remote.updatedAt > local.updatedAt -> workoutDayDao.insert(remote)
                local.updatedAt > remote.updatedAt || !local.isSynced -> { remoteSource.upsertDay(local); workoutDayDao.markSynced(local.id) }
            }
        }
    }

    private suspend fun mergePlannedExercises(userId: String) {
        val remoteMap = remoteSource.fetchPlannedExercises(userId).associateBy { it.id }
        val localMap = plannedExerciseDao.getAllForSync(userId).associateBy { it.id }
        (remoteMap.keys + localMap.keys).forEach { id ->
            val remote = remoteMap[id]
            val local = localMap[id]
            when {
                local == null -> plannedExerciseDao.insert(remote!!)
                remote == null -> { remoteSource.upsertPlannedExercise(local); plannedExerciseDao.markSynced(local.id) }
                remote.updatedAt > local.updatedAt -> plannedExerciseDao.insert(remote)
                local.updatedAt > remote.updatedAt || !local.isSynced -> { remoteSource.upsertPlannedExercise(local); plannedExerciseDao.markSynced(local.id) }
            }
        }
    }

    private suspend fun mergeSessions(userId: String) {
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

    private suspend fun mergeSets(userId: String) {
        val remoteMap = remoteSource.fetchSets(userId).associateBy { it.id }
        val localMap = sessionSetDao.getAllForSync(userId).associateBy { it.id }
        (remoteMap.keys + localMap.keys).forEach { id ->
            val remote = remoteMap[id]
            val local = localMap[id]
            when {
                local == null -> sessionSetDao.insert(remote!!)
                remote == null -> { remoteSource.upsertSet(local); sessionSetDao.markSynced(local.id) }
                remote.updatedAt > local.updatedAt -> sessionSetDao.insert(remote)
                local.updatedAt > remote.updatedAt || !local.isSynced -> { remoteSource.upsertSet(local); sessionSetDao.markSynced(local.id) }
            }
        }
    }
}

