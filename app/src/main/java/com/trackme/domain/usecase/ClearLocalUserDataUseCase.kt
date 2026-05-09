package com.trackme.domain.usecase

import com.trackme.data.local.dao.*
import com.trackme.sync.SyncManager
import javax.inject.Inject

class ClearLocalUserDataUseCase @Inject constructor(
    private val workoutPlanDao: WorkoutPlanDao,
    private val workoutDayDao: WorkoutDayDao,
    private val plannedExerciseDao: PlannedExerciseDao,
    private val workoutSessionDao: WorkoutSessionDao,
    private val sessionSetDao: SessionSetDao,
    private val personalRecordDao: PersonalRecordDao,
    private val healthSnapshotDao: HealthSnapshotDao,
    private val syncManager: SyncManager,
) {
    suspend operator fun invoke() {
        // Cancel any in-flight or queued sync jobs before wiping — prevents a race where
        // a sync job restores data from the previous user after we clear it.
        syncManager.cancelAllSync()

        workoutPlanDao.deleteAll()
        workoutDayDao.deleteAll()
        plannedExerciseDao.deleteAll()
        workoutSessionDao.deleteAll()
        sessionSetDao.deleteAll()
        personalRecordDao.deleteAll()
        healthSnapshotDao.deleteAll()
    }
}
