package com.trackme.domain.repository

import com.trackme.domain.model.*
import kotlinx.coroutines.flow.Flow

interface WorkoutRepository {
    fun getActivePlan(userId: String): Flow<WorkoutPlan?>
    fun getAllPlans(userId: String): Flow<List<WorkoutPlan>>
    suspend fun savePlan(plan: WorkoutPlan)
    suspend fun setActivePlan(planId: String, userId: String)

    fun getDaysForPlan(planId: String): Flow<List<WorkoutDay>>
    suspend fun saveDay(day: WorkoutDay)
    suspend fun deleteDay(day: WorkoutDay)

    fun getPlannedExercisesForDay(dayId: String): Flow<List<PlannedExercise>>
    suspend fun addPlannedExercise(pe: PlannedExercise)
    suspend fun removePlannedExercise(pe: PlannedExercise)
    suspend fun reorderExercises(exercises: List<PlannedExercise>)

    suspend fun startSession(session: WorkoutSession)
    suspend fun finishSession(sessionId: String, durationMinutes: Int)
    fun getSessionSets(sessionId: String): Flow<List<SessionSet>>

    suspend fun logSet(set: SessionSet)
    suspend fun deleteSet(set: SessionSet)

    suspend fun updatePersonalRecord(userId: String, exerciseId: String, weightKg: Float, reps: Int, date: Long)
    fun getPersonalRecords(userId: String): Flow<List<PersonalRecord>>

    fun getHistoryForExercise(userId: String, exerciseId: String): Flow<List<SessionSet>>
    fun getSessionsSince(userId: String, fromDate: Long): Flow<List<WorkoutSession>>
    fun getSetsSince(userId: String, fromDate: Long): Flow<List<SessionSet>>
    suspend fun getInProgressSession(userId: String, todayStart: Long): WorkoutSession?
}
