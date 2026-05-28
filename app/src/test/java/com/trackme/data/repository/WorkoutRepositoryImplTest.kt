package com.trackme.data.repository

import com.trackme.data.local.dao.*
import com.trackme.data.local.entity.PersonalRecordEntity
import com.trackme.data.remote.supabase.WorkoutRemoteSource
import com.trackme.domain.model.DayOfWeek
import com.trackme.domain.model.PlannedExercise
import com.trackme.domain.model.SessionSet
import com.trackme.domain.model.WorkoutDay
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

    @Test
    fun `deleteDay soft deletes locally and marks synced when remote tombstone succeeds`() = runTest {
        val day = WorkoutDay(
            id = "day-1",
            planId = "plan-1",
            userId = "user-1",
            dayOfWeek = DayOfWeek.MON,
            name = "Push",
            updatedAt = 1L,
        )
        coEvery { remoteSource.upsertDay(any()) } returns mockk(relaxed = true)

        repo.deleteDay(day)

        coVerify(exactly = 1) { workoutDayDao.softDelete("day-1", any()) }
        coVerify(exactly = 1) {
            remoteSource.upsertDay(match {
                it.id == "day-1" &&
                    it.userId == "user-1" &&
                    it.deletedAt != null &&
                    !it.isSynced
            })
        }
        coVerify(exactly = 1) { workoutDayDao.markSynced("day-1") }
        coVerify(exactly = 0) { pendingDeletionDao.insert(any()) }
        coVerify(exactly = 0) { syncManager.enqueueImmediateSync() }
    }

    @Test
    fun `removePlannedExercise records pending deletion and enqueues sync when remote tombstone fails`() = runTest {
        val planned = PlannedExercise(
            id = "planned-1",
            dayId = "day-1",
            userId = "user-1",
            exerciseId = "bench",
            orderIndex = 0,
            updatedAt = 1L,
            targetSets = 3,
        )
        coEvery { remoteSource.upsertPlannedExercise(any()) } throws RuntimeException("network down")

        repo.removePlannedExercise(planned)

        coVerify(exactly = 1) { plannedExerciseDao.softDelete("planned-1", any()) }
        coVerify(exactly = 1) {
            pendingDeletionDao.insert(match {
                it.entityId == "planned-1" &&
                    it.userId == "user-1" &&
                    it.tableName == "planned_exercises"
            })
        }
        coVerify(exactly = 0) { plannedExerciseDao.markSynced("planned-1") }
        coVerify(exactly = 1) { syncManager.enqueueImmediateSync() }
    }

    @Test
    fun `deleteSet records typed pending deletion when remote tombstone fails`() = runTest {
        val set = SessionSet(
            id = "set-1",
            sessionId = "session-1",
            userId = "user-1",
            exerciseId = "bench",
            setNumber = 1,
            weightKg = 100f,
            reps = 5,
            completed = true,
            updatedAt = 1L,
        )
        coEvery { remoteSource.upsertSet(any()) } throws RuntimeException("postgrest unavailable")

        repo.deleteSet(set)

        coVerify(exactly = 1) { sessionSetDao.softDelete("set-1", any()) }
        coVerify(exactly = 1) {
            remoteSource.upsertSet(match {
                it.id == "set-1" &&
                    it.deletedAt != null &&
                    it.weightKg == 100f &&
                    it.reps == 5
            })
        }
        coVerify(exactly = 1) {
            pendingDeletionDao.insert(match {
                it.entityId == "set-1" &&
                    it.tableName == "session_sets"
            })
        }
        coVerify(exactly = 1) { syncManager.enqueueImmediateSync() }
    }
}
