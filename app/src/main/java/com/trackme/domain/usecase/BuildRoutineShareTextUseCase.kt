package com.trackme.domain.usecase

import com.trackme.domain.model.WorkoutDay
import com.trackme.domain.model.WorkoutPlan
import com.trackme.domain.repository.ExerciseRepository
import com.trackme.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Use case that formats a workout plan for Android share intents.
 *
 * Architecture Layer: Domain use case
 *
 * Responsibilities:
 * - Read planned exercises for each workout day.
 * - Resolve exercise names in one batch per day.
 * - Preserve the existing share text contract used by WeeklyPlannerScreen.
 */
class BuildRoutineShareTextUseCase @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
) {
    suspend operator fun invoke(plan: WorkoutPlan, days: List<WorkoutDay>): String {
        val sortedDays = days.sortedBy { it.dayOfWeek.ordinal }
        return buildString {
            append("Weekly Routine: ${plan.name}\n")
            append("Generated via TrackMe\n\n")

            for (day in sortedDays) {
                append("${day.dayOfWeek.name}: ${day.name}\n")
                val planned = workoutRepository.getPlannedExercisesForDay(day.id).first()
                if (planned.isEmpty()) {
                    append("- Rest Day\n")
                } else {
                    val exerciseById = exerciseRepository.getByIds(planned.map { it.exerciseId })
                    planned.sortedBy { it.orderIndex }.forEach { plannedExercise ->
                        val exerciseName = exerciseById[plannedExercise.exerciseId]?.name ?: "Unknown Exercise"
                        append("- $exerciseName: ${plannedExercise.targetSets} sets")
                        plannedExercise.targetReps?.let { append(" x $it reps") }
                        plannedExercise.targetWeightKg?.let { append(" @ ${it}kg") }
                        plannedExercise.targetDurationSeconds?.let { append(", ${it}s") }
                        plannedExercise.targetDistanceKm?.let { append(", ${it}km") }
                        append("\n")
                    }
                }
                append("\n")
            }
        }
    }
}
