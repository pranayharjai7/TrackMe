package com.trackme.data.repository

import com.trackme.data.local.dao.*
import com.trackme.data.local.entity.*
import com.trackme.data.remote.supabase.WorkoutRemoteSource
import com.trackme.domain.model.*
import com.trackme.domain.repository.WorkoutRepository
import com.trackme.sync.SyncManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository implementation for workout planning and session logging.
 *
 * Architecture Layer: Data repository
 *
 * Responsibilities:
 * - Translate Room entities to domain models and back.
 * - Mark local writes as unsynced and enqueue background Supabase sync.
 * - Preserve soft-delete semantics so offline deletes are eventually reconciled.
 */
@Singleton
class WorkoutRepositoryImpl @Inject constructor(
    private val workoutPlanDao: WorkoutPlanDao,
    private val workoutDayDao: WorkoutDayDao,
    private val plannedExerciseDao: PlannedExerciseDao,
    private val workoutSessionDao: WorkoutSessionDao,
    private val sessionSetDao: SessionSetDao,
    private val personalRecordDao: PersonalRecordDao,
    private val pendingDeletionDao: PendingDeletionDao,
    private val remoteSource: WorkoutRemoteSource,
    private val syncManager: SyncManager,
) : WorkoutRepository {

    override fun getActivePlan(userId: String): Flow<WorkoutPlan?> =
        workoutPlanDao.getActivePlan(userId).map { it?.toDomain() }

    override fun getAllPlans(userId: String): Flow<List<WorkoutPlan>> =
        workoutPlanDao.getAllForUser(userId).map { it.map { e -> e.toDomain() } }

    override suspend fun savePlan(plan: WorkoutPlan) {
        workoutPlanDao.insert(plan.toEntity(isSynced = false))
        syncManager.enqueueImmediateSync()
    }

    override suspend fun setActivePlan(planId: String, userId: String) {
        workoutPlanDao.clearActivePlan(userId)
        workoutPlanDao.setActivePlan(planId)
        syncManager.enqueueImmediateSync()
    }

    override fun getDaysForPlan(planId: String): Flow<List<WorkoutDay>> =
        workoutDayDao.getDaysForPlan(planId).map { it.map { e -> e.toDomain() } }

    override suspend fun saveDay(day: WorkoutDay) {
        workoutDayDao.insert(day.toEntity(isSynced = false))
        syncManager.enqueueImmediateSync()
    }

    override suspend fun deleteDay(day: WorkoutDay) {
        pushSoftDeletion(
            entityId = day.id,
            userId = day.userId,
            tableName = "workout_days",
            softDeleteLocal = { id, timestamp -> workoutDayDao.softDelete(id, timestamp) },
            deletedEntity = { timestamp ->
                day.toEntity(isSynced = false).copy(deletedAt = timestamp, updatedAt = timestamp)
            },
            upsertRemote = { remoteSource.upsertDay(it) },
            markSynced = { workoutDayDao.markSynced(it) },
        )
    }

    override fun getPlannedExercisesForDay(dayId: String): Flow<List<PlannedExercise>> =
        plannedExerciseDao.getForDay(dayId).map { it.map { e -> e.toDomain() } }

    override suspend fun addPlannedExercise(pe: PlannedExercise) {
        plannedExerciseDao.insert(pe.toEntity(isSynced = false))
        syncManager.enqueueImmediateSync()
    }

    override suspend fun updatePlannedExercise(pe: PlannedExercise) {
        val entity = pe.toEntity(isSynced = false).copy(updatedAt = System.currentTimeMillis())
        plannedExerciseDao.insert(entity)
        val pushed = runCatching { remoteSource.upsertPlannedExercise(entity) }.isSuccess
        if (pushed) {
            plannedExerciseDao.markSynced(pe.id)
        } else {
            syncManager.enqueueImmediateSync()
        }
    }

    override suspend fun removePlannedExercise(pe: PlannedExercise) {
        pushSoftDeletion(
            entityId = pe.id,
            userId = pe.userId,
            tableName = "planned_exercises",
            softDeleteLocal = { id, timestamp -> plannedExerciseDao.softDelete(id, timestamp) },
            deletedEntity = { timestamp ->
                pe.toEntity(isSynced = false).copy(deletedAt = timestamp, updatedAt = timestamp)
            },
            upsertRemote = { remoteSource.upsertPlannedExercise(it) },
            markSynced = { plannedExerciseDao.markSynced(it) },
        )
    }

    override suspend fun reorderExercises(exercises: List<PlannedExercise>) {
        plannedExerciseDao.insertAll(exercises.map { it.toEntity(isSynced = false) })
        syncManager.enqueueImmediateSync()
    }

    override suspend fun startSession(session: WorkoutSession) {
        workoutSessionDao.insert(session.toEntity(isSynced = false))
        syncManager.enqueueImmediateSync()
    }

    override suspend fun finishSession(sessionId: String, durationMinutes: Int) {
        workoutSessionDao.updateDuration(sessionId, durationMinutes)
        syncManager.enqueueImmediateSync()
    }

    override fun getSessionSets(sessionId: String): Flow<List<SessionSet>> =
        sessionSetDao.getSetsForSession(sessionId).map { it.map { e -> e.toDomain() } }

    override suspend fun logSet(set: SessionSet) {
        sessionSetDao.insert(set.toEntity(isSynced = false))
        syncManager.enqueueImmediateSync()
    }

    override suspend fun deleteSet(set: SessionSet) {
        pushSoftDeletion(
            entityId = set.id,
            userId = set.userId,
            tableName = "session_sets",
            softDeleteLocal = { id, timestamp -> sessionSetDao.softDelete(id, timestamp) },
            deletedEntity = { timestamp ->
                set.toEntity(isSynced = false).copy(deletedAt = timestamp, updatedAt = timestamp)
            },
            upsertRemote = { remoteSource.upsertSet(it) },
            markSynced = { sessionSetDao.markSynced(it) },
        )
    }

    override suspend fun updatePersonalRecord(userId: String, exerciseId: String, weightKg: Float, reps: Int, date: Long) {
        val existing = personalRecordDao.getForExercise(userId, exerciseId)
        if (existing == null || weightKg > existing.maxWeightKg) {
            personalRecordDao.insert(
                PersonalRecordEntity(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    userId = userId,
                    exerciseId = exerciseId,
                    maxWeightKg = weightKg,
                    maxReps = reps,
                    achievedAt = date,
                )
            )
        }
    }

    override fun getPersonalRecords(userId: String): Flow<List<PersonalRecord>> =
        personalRecordDao.getAllForUser(userId).map { it.map { e -> e.toDomain() } }

    override fun getHistoryForExercise(userId: String, exerciseId: String): Flow<List<SessionSet>> =
        sessionSetDao.getHistoryForExercise(userId, exerciseId).map { it.map { e -> e.toDomain() } }

    override fun getSessionsSince(userId: String, fromDate: Long): Flow<List<WorkoutSession>> =
        workoutSessionDao.getForUserSince(userId, fromDate).map { it.map { e -> e.toDomain() } }

    override fun getSetsSince(userId: String, fromDate: Long): Flow<List<SessionSet>> =
        sessionSetDao.getSetsSince(userId, fromDate).map { it.map { e -> e.toDomain() } }

    override suspend fun getInProgressSession(userId: String, todayStart: Long): WorkoutSession? =
        workoutSessionDao.getInProgressSession(userId, todayStart)?.toDomain()

    override suspend fun getInProgressSessionForDay(userId: String, dayId: String, todayStart: Long): WorkoutSession? =
        workoutSessionDao.getInProgressSessionForDay(userId, dayId, todayStart)?.toDomain()

    /**
     * Applies a local soft delete and tries to immediately push the tombstone.
     *
     * Side effects:
     * - Inserts PendingDeletionEntity when the remote write fails.
     * - Enqueues sync so the deletion is retried later.
     */
    private suspend fun <T> pushSoftDeletion(
        entityId: String,
        userId: String,
        tableName: String,
        softDeleteLocal: suspend (String, Long) -> Unit,
        deletedEntity: (Long) -> T,
        upsertRemote: suspend (T) -> Unit,
        markSynced: suspend (String) -> Unit,
    ) {
        val timestamp = System.currentTimeMillis()
        softDeleteLocal(entityId, timestamp)

        val pushed = runCatching { upsertRemote(deletedEntity(timestamp)) }.isSuccess
        if (pushed) {
            markSynced(entityId)
        } else {
            pendingDeletionDao.insert(PendingDeletionEntity(entityId, userId, tableName, timestamp))
            syncManager.enqueueImmediateSync()
        }
    }
}
