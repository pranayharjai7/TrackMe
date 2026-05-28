package com.trackme.data.remote.supabase

import com.trackme.data.local.entity.*
import com.trackme.data.remote.dto.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
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

/**
 * Remote data source for Supabase workout tables.
 *
 * Architecture Layer: Data remote source
 *
 * Hardening & Security:
 * - Validates user session status before making remote requests.
 * - Wraps all database queries with authentication guards.
 * - Centralizes exception mapping and network retry compatibility.
 */
@Singleton
class WorkoutRemoteSource @Inject constructor(
    private val supabase: SupabaseClient,
) {

    private suspend inline fun <T> runAuthenticated(crossinline block: suspend () -> T): T {
        val session = supabase.auth.currentSessionOrNull()
        if (session == null || session.user == null) {
            throw SecurityException("Unauthenticated query execution blocked: No active Supabase session found.")
        }
        return try {
            block()
        } catch (e: Exception) {
            // Transform or wrap postgrest/ktor client network errors if needed
            throw e
        }
    }

    suspend fun upsertPlan(entity: WorkoutPlanEntity) = runAuthenticated {
        supabase.postgrest[TABLE_WORKOUT_PLANS].upsert(
            WorkoutPlanDto(entity.id, entity.userId, entity.name, entity.isActive, entity.createdAt, entity.updatedAt, entity.deletedAt)
        )
    }

    suspend fun upsertPlans(entities: List<WorkoutPlanEntity>) = runAuthenticated {
        if (entities.isEmpty()) return@runAuthenticated
        supabase.postgrest[TABLE_WORKOUT_PLANS].upsert(
            entities.map { WorkoutPlanDto(it.id, it.userId, it.name, it.isActive, it.createdAt, it.updatedAt, it.deletedAt) }
        )
    }

    suspend fun upsertDay(entity: WorkoutDayEntity) = runAuthenticated {
        supabase.postgrest[TABLE_WORKOUT_DAYS].upsert(
            WorkoutDayDto(entity.id, entity.planId, entity.userId, entity.dayOfWeek, entity.name, entity.updatedAt, entity.deletedAt)
        )
    }

    suspend fun upsertDays(entities: List<WorkoutDayEntity>) = runAuthenticated {
        if (entities.isEmpty()) return@runAuthenticated
        supabase.postgrest[TABLE_WORKOUT_DAYS].upsert(
            entities.map { WorkoutDayDto(it.id, it.planId, it.userId, it.dayOfWeek, it.name, it.updatedAt, it.deletedAt) }
        )
    }

    suspend fun upsertPlannedExercise(entity: PlannedExerciseEntity) = runAuthenticated {
        supabase.postgrest[TABLE_PLANNED_EXERCISES].upsert(
            PlannedExerciseDto(
                entity.id, entity.dayId, entity.userId, entity.exerciseId,
                entity.orderIndex, entity.updatedAt, entity.deletedAt,
                entity.targetSets, entity.targetReps, entity.targetWeightKg,
                entity.targetDurationSeconds, entity.targetDistanceKm,
                entity.targetSpeedKmh, entity.targetIncline,
            )
        )
    }

    suspend fun upsertPlannedExercises(entities: List<PlannedExerciseEntity>) = runAuthenticated {
        if (entities.isEmpty()) return@runAuthenticated
        supabase.postgrest[TABLE_PLANNED_EXERCISES].upsert(
            entities.map { entity ->
                PlannedExerciseDto(
                    entity.id, entity.dayId, entity.userId, entity.exerciseId,
                    entity.orderIndex, entity.updatedAt, entity.deletedAt,
                    entity.targetSets, entity.targetReps, entity.targetWeightKg,
                    entity.targetDurationSeconds, entity.targetDistanceKm,
                    entity.targetSpeedKmh, entity.targetIncline,
                )
            }
        )
    }

    suspend fun upsertSession(entity: WorkoutSessionEntity) = runAuthenticated {
        supabase.postgrest[TABLE_WORKOUT_SESSIONS].upsert(
            WorkoutSessionDto(entity.id, entity.userId, entity.dayId, entity.date, entity.durationMinutes, entity.notes, entity.updatedAt, entity.deletedAt)
        )
    }

    suspend fun upsertSessions(entities: List<WorkoutSessionEntity>) = runAuthenticated {
        if (entities.isEmpty()) return@runAuthenticated
        supabase.postgrest[TABLE_WORKOUT_SESSIONS].upsert(
            entities.map { WorkoutSessionDto(it.id, it.userId, it.dayId, it.date, it.durationMinutes, it.notes, it.updatedAt, it.deletedAt) }
        )
    }

    suspend fun upsertSet(entity: SessionSetEntity) = runAuthenticated {
        supabase.postgrest[TABLE_SESSION_SETS].upsert(
            SessionSetDto(entity.id, entity.sessionId, entity.userId, entity.exerciseId, entity.setNumber, entity.weightKg, entity.reps, entity.completed, entity.updatedAt, entity.deletedAt, entity.durationSeconds, entity.distanceKm, entity.speedKmh, entity.inclinePercent)
        )
    }

    suspend fun upsertSets(entities: List<SessionSetEntity>) = runAuthenticated {
        if (entities.isEmpty()) return@runAuthenticated
        supabase.postgrest[TABLE_SESSION_SETS].upsert(
            entities.map { entity ->
                SessionSetDto(entity.id, entity.sessionId, entity.userId, entity.exerciseId, entity.setNumber, entity.weightKg, entity.reps, entity.completed, entity.updatedAt, entity.deletedAt, entity.durationSeconds, entity.distanceKm, entity.speedKmh, entity.inclinePercent)
            }
        )
    }

    suspend fun upsertMuscleWeeklyAnalytics(entity: MuscleWeeklyAnalyticsEntity) = runAuthenticated {
        supabase.postgrest[TABLE_MUSCLE_WEEKLY_ANALYTICS].upsert(entity.toDto())
    }

    suspend fun upsertMuscleWeeklyAnalytics(entities: List<MuscleWeeklyAnalyticsEntity>) = runAuthenticated {
        if (entities.isEmpty()) return@runAuthenticated
        supabase.postgrest[TABLE_MUSCLE_WEEKLY_ANALYTICS].upsert(entities.map { it.toDto() })
    }

    suspend fun upsertExerciseProgressSnapshot(entity: ExerciseProgressSnapshotEntity) = runAuthenticated {
        supabase.postgrest[TABLE_EXERCISE_PROGRESS_SNAPSHOTS].upsert(entity.toDto())
    }

    suspend fun upsertExerciseProgressSnapshots(entities: List<ExerciseProgressSnapshotEntity>) = runAuthenticated {
        if (entities.isEmpty()) return@runAuthenticated
        supabase.postgrest[TABLE_EXERCISE_PROGRESS_SNAPSHOTS].upsert(entities.map { it.toDto() })
    }

    suspend fun upsertDailyHealthAnalytics(entity: DailyHealthAnalyticsEntity) = runAuthenticated {
        supabase.postgrest[TABLE_DAILY_HEALTH_ANALYTICS].upsert(entity.toDto())
    }

    suspend fun upsertDailyHealthAnalytics(entities: List<DailyHealthAnalyticsEntity>) = runAuthenticated {
        if (entities.isEmpty()) return@runAuthenticated
        supabase.postgrest[TABLE_DAILY_HEALTH_ANALYTICS].upsert(entities.map { it.toDto() })
    }

    suspend fun upsertBodyStateSnapshot(entity: BodyStateSnapshotEntity) = runAuthenticated {
        supabase.postgrest[TABLE_BODY_STATE_SNAPSHOTS].upsert(entity.toDto())
    }

    suspend fun upsertBodyStateSnapshots(entities: List<BodyStateSnapshotEntity>) = runAuthenticated {
        if (entities.isEmpty()) return@runAuthenticated
        supabase.postgrest[TABLE_BODY_STATE_SNAPSHOTS].upsert(entities.map { it.toDto() })
    }

    suspend fun fetchPlans(userId: String): List<WorkoutPlanEntity> = runAuthenticated {
        supabase.postgrest[TABLE_WORKOUT_PLANS]
            .select { filter { eq("user_id", userId) } }
            .decodeList<WorkoutPlanDto>()
            .map { WorkoutPlanEntity(it.id, it.userId, it.name, it.isActive, it.createdAt, it.updatedAt, isSynced = true, deletedAt = it.deletedAt) }
    }

    suspend fun fetchDays(userId: String): List<WorkoutDayEntity> = runAuthenticated {
        supabase.postgrest[TABLE_WORKOUT_DAYS]
            .select { filter { eq("user_id", userId) } }
            .decodeList<WorkoutDayDto>()
            .map { WorkoutDayEntity(it.id, it.planId, it.userId, it.dayOfWeek, it.name, it.updatedAt, isSynced = true, deletedAt = it.deletedAt) }
    }

    suspend fun fetchPlannedExercises(userId: String): List<PlannedExerciseEntity> = runAuthenticated {
        supabase.postgrest[TABLE_PLANNED_EXERCISES]
            .select { filter { eq("user_id", userId) } }
            .decodeList<PlannedExerciseDto>()
            .map { PlannedExerciseEntity(it.id, it.dayId, it.userId, it.exerciseId, it.orderIndex, it.updatedAt, isSynced = true, deletedAt = it.deletedAt, targetSets = it.targetSets ?: 3, targetReps = it.targetReps, targetWeightKg = it.targetWeightKg, targetDurationSeconds = it.targetDurationSeconds, targetDistanceKm = it.targetDistanceKm, targetSpeedKmh = it.targetSpeedKmh, targetIncline = it.targetIncline) }
    }

    suspend fun fetchSessions(userId: String): List<WorkoutSessionEntity> = runAuthenticated {
        supabase.postgrest[TABLE_WORKOUT_SESSIONS]
            .select { filter { eq("user_id", userId) } }
            .decodeList<WorkoutSessionDto>()
            .map { WorkoutSessionEntity(it.id, it.userId, it.dayId, it.date, it.durationMinutes, it.notes, it.updatedAt, isSynced = true, deletedAt = it.deletedAt) }
    }

    suspend fun fetchSets(userId: String): List<SessionSetEntity> = runAuthenticated {
        supabase.postgrest[TABLE_SESSION_SETS]
            .select { filter { eq("user_id", userId) } }
            .decodeList<SessionSetDto>()
            .map { SessionSetEntity(it.id, it.sessionId, it.userId, it.exerciseId, it.setNumber, it.weightKg, it.reps, it.completed, it.updatedAt, isSynced = true, deletedAt = it.deletedAt, durationSeconds = it.durationSeconds, distanceKm = it.distanceKm, speedKmh = it.speedKmh, inclinePercent = it.inclinePercent) }
    }

    suspend fun fetchMuscleWeeklyAnalytics(userId: String): List<MuscleWeeklyAnalyticsEntity> = runAuthenticated {
        supabase.postgrest[TABLE_MUSCLE_WEEKLY_ANALYTICS]
            .select { filter { eq("user_id", userId) } }
            .decodeList<MuscleWeeklyAnalyticsDto>()
            .map { it.toEntity() }
    }

    suspend fun fetchExerciseProgressSnapshots(userId: String): List<ExerciseProgressSnapshotEntity> = runAuthenticated {
        supabase.postgrest[TABLE_EXERCISE_PROGRESS_SNAPSHOTS]
            .select { filter { eq("user_id", userId) } }
            .decodeList<ExerciseProgressSnapshotDto>()
            .map { it.toEntity() }
    }

    suspend fun fetchDailyHealthAnalytics(userId: String): List<DailyHealthAnalyticsEntity> = runAuthenticated {
        supabase.postgrest[TABLE_DAILY_HEALTH_ANALYTICS]
            .select { filter { eq("user_id", userId) } }
            .decodeList<DailyHealthAnalyticsDto>()
            .map { it.toEntity() }
    }

    suspend fun fetchBodyStateSnapshots(userId: String): List<BodyStateSnapshotEntity> = runAuthenticated {
        supabase.postgrest[TABLE_BODY_STATE_SNAPSHOTS]
            .select { filter { eq("user_id", userId) } }
            .decodeList<BodyStateSnapshotDto>()
            .map { it.toEntity() }
    }

    suspend fun markDeleted(table: String, entityId: String, userId: String, deletedAt: Long) = runAuthenticated {
        supabase.postgrest[table].update(DeletionPatch(deletedAt, deletedAt)) {
            filter {
                eq("id", entityId)
                eq("user_id", userId)
            }
        }
    }

    private companion object {
        const val TABLE_WORKOUT_PLANS = "workout_plans"
        const val TABLE_WORKOUT_DAYS = "workout_days"
        const val TABLE_PLANNED_EXERCISES = "planned_exercises"
        const val TABLE_WORKOUT_SESSIONS = "workout_sessions"
        const val TABLE_SESSION_SETS = "session_sets"
        const val TABLE_MUSCLE_WEEKLY_ANALYTICS = "muscle_weekly_analytics"
        const val TABLE_EXERCISE_PROGRESS_SNAPSHOTS = "exercise_progress_snapshots"
        const val TABLE_DAILY_HEALTH_ANALYTICS = "daily_health_analytics"
        const val TABLE_BODY_STATE_SNAPSHOTS = "body_state_snapshots"
    }
}
