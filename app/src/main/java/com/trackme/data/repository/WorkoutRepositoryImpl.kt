package com.trackme.data.repository

import com.trackme.data.local.dao.*
import com.trackme.data.local.entity.*
import com.trackme.domain.model.*
import com.trackme.domain.repository.WorkoutRepository
import com.trackme.sync.SyncManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkoutRepositoryImpl @Inject constructor(
    private val workoutPlanDao: WorkoutPlanDao,
    private val workoutDayDao: WorkoutDayDao,
    private val plannedExerciseDao: PlannedExerciseDao,
    private val workoutSessionDao: WorkoutSessionDao,
    private val sessionSetDao: SessionSetDao,
    private val personalRecordDao: PersonalRecordDao,
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
        workoutDayDao.delete(day.toEntity())
    }

    override fun getPlannedExercisesForDay(dayId: String): Flow<List<PlannedExercise>> =
        plannedExerciseDao.getForDay(dayId).map { it.map { e -> e.toDomain() } }

    override suspend fun addPlannedExercise(pe: PlannedExercise) {
        plannedExerciseDao.insert(pe.toEntity(isSynced = false))
        syncManager.enqueueImmediateSync()
    }

    override suspend fun removePlannedExercise(pe: PlannedExercise) {
        plannedExerciseDao.delete(pe.toEntity())
    }

    override suspend fun reorderExercises(exercises: List<PlannedExercise>) {
        plannedExerciseDao.insertAll(exercises.map { it.toEntity(isSynced = false) })
        syncManager.enqueueImmediateSync()
    }

    override suspend fun startSession(session: WorkoutSession) {
        workoutSessionDao.insert(session.toEntity(isSynced = false))
    }

    override suspend fun finishSession(sessionId: String, durationMinutes: Int) {
        syncManager.enqueueImmediateSync()
    }

    override fun getSessionSets(sessionId: String): Flow<List<SessionSet>> =
        sessionSetDao.getSetsForSession(sessionId).map { it.map { e -> e.toDomain() } }

    override suspend fun logSet(set: SessionSet) {
        sessionSetDao.insert(set.toEntity(isSynced = false))
        syncManager.enqueueImmediateSync()
    }

    override suspend fun deleteSet(set: SessionSet) {
        sessionSetDao.delete(set.toEntity())
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
}
