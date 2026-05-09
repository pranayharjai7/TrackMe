package com.trackme.data.remote.supabase

import com.trackme.data.local.entity.*
import com.trackme.data.remote.dto.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class DeletionPatch(
    @SerialName("deleted_at") val deletedAt: Long,
    @SerialName("updated_at") val updatedAt: Long,
)

@Singleton
class WorkoutRemoteSource @Inject constructor(
    private val supabase: SupabaseClient,
) {
    suspend fun upsertPlan(entity: WorkoutPlanEntity) {
        supabase.postgrest["workout_plans"].upsert(
            WorkoutPlanDto(entity.id, entity.userId, entity.name, entity.isActive, entity.createdAt, entity.updatedAt, entity.deletedAt)
        )
    }

    suspend fun upsertDay(entity: WorkoutDayEntity) {
        supabase.postgrest["workout_days"].upsert(
            WorkoutDayDto(entity.id, entity.planId, entity.userId, entity.dayOfWeek, entity.name, entity.updatedAt, entity.deletedAt)
        )
    }

    suspend fun upsertPlannedExercise(entity: PlannedExerciseEntity) {
        supabase.postgrest["planned_exercises"].upsert(
            PlannedExerciseDto(entity.id, entity.dayId, entity.userId, entity.exerciseId, entity.orderIndex, entity.updatedAt, entity.deletedAt)
        )
    }

    suspend fun upsertSession(entity: WorkoutSessionEntity) {
        supabase.postgrest["workout_sessions"].upsert(
            WorkoutSessionDto(entity.id, entity.userId, entity.dayId, entity.date, entity.durationMinutes, entity.notes, entity.updatedAt, entity.deletedAt)
        )
    }

    suspend fun upsertSet(entity: SessionSetEntity) {
        supabase.postgrest["session_sets"].upsert(
            SessionSetDto(entity.id, entity.sessionId, entity.userId, entity.exerciseId, entity.setNumber, entity.weightKg, entity.reps, entity.completed, entity.updatedAt, entity.deletedAt)
        )
    }

    suspend fun fetchPlans(userId: String): List<WorkoutPlanEntity> =
        supabase.postgrest["workout_plans"]
            .select { filter { eq("user_id", userId) } }
            .decodeList<WorkoutPlanDto>()
            .map { WorkoutPlanEntity(it.id, it.userId, it.name, it.isActive, it.createdAt, it.updatedAt, isSynced = true, deletedAt = it.deletedAt) }

    suspend fun fetchDays(userId: String): List<WorkoutDayEntity> =
        supabase.postgrest["workout_days"]
            .select { filter { eq("user_id", userId) } }
            .decodeList<WorkoutDayDto>()
            .map { WorkoutDayEntity(it.id, it.planId, it.userId, it.dayOfWeek, it.name, it.updatedAt, isSynced = true, deletedAt = it.deletedAt) }

    suspend fun fetchPlannedExercises(userId: String): List<PlannedExerciseEntity> =
        supabase.postgrest["planned_exercises"]
            .select { filter { eq("user_id", userId) } }
            .decodeList<PlannedExerciseDto>()
            .map { PlannedExerciseEntity(it.id, it.dayId, it.userId, it.exerciseId, it.orderIndex, it.updatedAt, isSynced = true, deletedAt = it.deletedAt) }

    suspend fun fetchSessions(userId: String): List<WorkoutSessionEntity> =
        supabase.postgrest["workout_sessions"]
            .select { filter { eq("user_id", userId) } }
            .decodeList<WorkoutSessionDto>()
            .map { WorkoutSessionEntity(it.id, it.userId, it.dayId, it.date, it.durationMinutes, it.notes, it.updatedAt, isSynced = true, deletedAt = it.deletedAt) }

    suspend fun fetchSets(userId: String): List<SessionSetEntity> =
        supabase.postgrest["session_sets"]
            .select { filter { eq("user_id", userId) } }
            .decodeList<SessionSetDto>()
            .map { SessionSetEntity(it.id, it.sessionId, it.userId, it.exerciseId, it.setNumber, it.weightKg, it.reps, it.completed, it.updatedAt, isSynced = true, deletedAt = it.deletedAt) }

    suspend fun markDeleted(table: String, entityId: String, userId: String, deletedAt: Long) {
        supabase.postgrest[table].update(DeletionPatch(deletedAt, deletedAt)) {
            filter {
                eq("id", entityId)
                eq("user_id", userId)
            }
        }
    }
}
