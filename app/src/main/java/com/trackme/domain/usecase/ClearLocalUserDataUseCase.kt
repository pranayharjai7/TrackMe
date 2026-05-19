package com.trackme.domain.usecase

import com.trackme.data.local.dao.*
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
    private val muscleWeeklyAnalyticsDao: MuscleWeeklyAnalyticsDao,
    private val exerciseProgressSnapshotDao: ExerciseProgressSnapshotDao,
    private val dailyHealthAnalyticsDao: DailyHealthAnalyticsDao,
    private val bodyStateSnapshotDao: BodyStateSnapshotDao,
    private val syncManager: SyncManager,
) {
    suspend operator fun invoke() {
        // Run initial sync to flush any pending offline local writes to remote db before wiping the database.
        syncManager.runInitialSync()

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
        muscleWeeklyAnalyticsDao.deleteAll()
        exerciseProgressSnapshotDao.deleteAll()
        dailyHealthAnalyticsDao.deleteAll()
        bodyStateSnapshotDao.deleteAll()
    }
}
