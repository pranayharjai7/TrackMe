package com.trackme.data.repository

import com.trackme.data.local.dao.*
import com.trackme.data.local.entity.PersonalRecordEntity
import com.trackme.data.remote.supabase.WorkoutRemoteSource
import com.trackme.sync.SyncManager
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Test

class WorkoutRepositoryImplTest {

    private val workoutPlanDao = mockk<WorkoutPlanDao>(relaxed = true)
    private val workoutDayDao = mockk<WorkoutDayDao>(relaxed = true)
    private val plannedExerciseDao = mockk<PlannedExerciseDao>(relaxed = true)
    private val workoutSessionDao = mockk<WorkoutSessionDao>(relaxed = true)
    private val sessionSetDao = mockk<SessionSetDao>(relaxed = true)
    private val personalRecordDao = mockk<PersonalRecordDao>(relaxed = true)
    private val pendingDeletionDao = mockk<PendingDeletionDao>(relaxed = true)
    private val remoteSource = mockk<WorkoutRemoteSource>(relaxed = true)
    private val syncManager = mockk<SyncManager>(relaxed = true)

    private val repo = WorkoutRepositoryImpl(
        workoutPlanDao, workoutDayDao, plannedExerciseDao,
        workoutSessionDao, sessionSetDao, personalRecordDao,
        pendingDeletionDao, remoteSource, syncManager,
    )

    @Test
    fun `updatePersonalRecord inserts new record when none exists`() = runTest {
        coEvery { personalRecordDao.getForExercise("user1", "bench-press") } returns null

        repo.updatePersonalRecord("user1", "bench-press", 100f, 8, 1000L)

        coVerify(exactly = 1) { personalRecordDao.insert(any()) }
    }

    @Test
    fun `updatePersonalRecord updates when new weight is higher`() = runTest {
        val existing = PersonalRecordEntity("pr1", "user1", "bench-press", 80f, 8, 900L)
        coEvery { personalRecordDao.getForExercise("user1", "bench-press") } returns existing

        repo.updatePersonalRecord("user1", "bench-press", 100f, 8, 1000L)

        coVerify(exactly = 1) { personalRecordDao.insert(match { it.maxWeightKg == 100f }) }
    }

    @Test
    fun `updatePersonalRecord skips when new weight is lower`() = runTest {
        val existing = PersonalRecordEntity("pr1", "user1", "bench-press", 120f, 8, 900L)
        coEvery { personalRecordDao.getForExercise("user1", "bench-press") } returns existing

        repo.updatePersonalRecord("user1", "bench-press", 100f, 8, 1000L)

        coVerify(exactly = 0) { personalRecordDao.insert(any()) }
    }
}
