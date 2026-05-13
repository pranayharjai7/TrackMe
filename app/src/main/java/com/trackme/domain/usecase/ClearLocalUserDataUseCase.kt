package com.trackme.domain.usecase

import com.trackme.data.local.dao.HealthMetricDao
import com.trackme.data.local.dao.HealthSnapshotDao
import com.trackme.data.local.dao.PersonalRecordDao
import com.trackme.data.local.dao.PlannedExerciseDao
import com.trackme.data.local.dao.SessionSetDao
import com.trackme.data.local.dao.WorkoutDayDao
import com.trackme.data.local.dao.WorkoutPlanDao
import com.trackme.data.local.dao.WorkoutSessionDao
import com.trackme.sync.SyncManager
import javax.inject.Inject

/**
 * Use case that clears user-scoped local data during sign-out.
 *
 * Architecture Layer: Domain use case
 *
 * Responsibilities:
 * - Stop sync jobs before local tables are cleared.
 * - Delete cached workout, health, and progress data for the previous session.
 * - Keep sign-out cleanup out of ProfileViewModel.
 */
class ClearLocalUserDataUseCase @Inject constructor(
    private val workoutPlanDao: WorkoutPlanDao,
    private val workoutDayDao: WorkoutDayDao,
    private val plannedExerciseDao: PlannedExerciseDao,
    private val workoutSessionDao: WorkoutSessionDao,
    private val sessionSetDao: SessionSetDao,
    private val personalRecordDao: PersonalRecordDao,
    private val healthSnapshotDao: HealthSnapshotDao,
    private val healthMetricDao: HealthMetricDao,
    private val syncManager: SyncManager,
) {
    suspend operator fun invoke() {
        // Cancel sync first so a queued worker cannot restore the previous user's data.
        syncManager.cancelAllSync()

        workoutPlanDao.deleteAll()
        workoutDayDao.deleteAll()
        plannedExerciseDao.deleteAll()
        workoutSessionDao.deleteAll()
        sessionSetDao.deleteAll()
        personalRecordDao.deleteAll()
        healthSnapshotDao.deleteAll()
        healthMetricDao.deleteAll()
    }
}
