package com.trackme.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.trackme.data.local.dao.*
import com.trackme.data.remote.supabase.WorkoutRemoteSource
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

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
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = runCatching {
        workoutPlanDao.getUnsynced().forEach { entity ->
            remoteSource.upsertPlan(entity)
            workoutPlanDao.markSynced(entity.id)
        }
        workoutDayDao.getUnsynced().forEach { entity ->
            remoteSource.upsertDay(entity)
            workoutDayDao.markSynced(entity.id)
        }
        plannedExerciseDao.getUnsynced().forEach { entity ->
            remoteSource.upsertPlannedExercise(entity)
            plannedExerciseDao.markSynced(entity.id)
        }
        workoutSessionDao.getUnsynced().forEach { entity ->
            remoteSource.upsertSession(entity)
            workoutSessionDao.markSynced(entity.id)
        }
        sessionSetDao.getUnsynced().forEach { entity ->
            remoteSource.upsertSet(entity)
            sessionSetDao.markSynced(entity.id)
        }
    }.fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
}
