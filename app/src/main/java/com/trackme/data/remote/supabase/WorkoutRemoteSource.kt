package com.trackme.data.remote.supabase

import com.trackme.data.local.entity.*
import com.trackme.data.remote.dto.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkoutRemoteSource @Inject constructor(
    private val supabase: SupabaseClient,
) {
    suspend fun upsertPlan(entity: WorkoutPlanEntity) {
        supabase.postgrest["workout_plans"].upsert(
            WorkoutPlanDto(entity.id, entity.userId, entity.name, entity.isActive, entity.createdAt, entity.updatedAt)
        )
    }

    suspend fun upsertDay(entity: WorkoutDayEntity) {
        supabase.postgrest["workout_days"].upsert(
            WorkoutDayDto(entity.id, entity.planId, entity.userId, entity.dayOfWeek, entity.name, entity.updatedAt)
        )
    }

    suspend fun upsertPlannedExercise(entity: PlannedExerciseEntity) {
        supabase.postgrest["planned_exercises"].upsert(
            PlannedExerciseDto(entity.id, entity.dayId, entity.userId, entity.exerciseId, entity.orderIndex, entity.updatedAt)
        )
    }

    suspend fun upsertSession(entity: WorkoutSessionEntity) {
        supabase.postgrest["workout_sessions"].upsert(
            WorkoutSessionDto(entity.id, entity.userId, entity.dayId, entity.date, entity.durationMinutes, entity.notes, entity.updatedAt)
        )
    }

    suspend fun upsertSet(entity: SessionSetEntity) {
        supabase.postgrest["session_sets"].upsert(
            SessionSetDto(entity.id, entity.sessionId, entity.userId, entity.exerciseId, entity.setNumber, entity.weightKg, entity.reps, entity.completed, entity.updatedAt)
        )
    }
}
