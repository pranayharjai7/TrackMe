package com.trackme.domain.usecase

import com.trackme.domain.model.Exercise
import com.trackme.domain.model.PlannedExercise
import com.trackme.domain.repository.ExerciseRepository
import com.trackme.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

typealias PlannedExerciseWithDetails = Pair<PlannedExercise, Exercise?>

/**
 * Use case that enriches a workout day's planned exercises with exercise metadata.
 *
 * Architecture Layer: Domain use case
 *
 * Responsibilities:
 * - Observe planned exercises for a day from the workout repository.
 * - Batch-load exercise definitions instead of issuing one lookup per row.
 * - Preserve planned exercise ordering supplied by the repository/DAO.
 */
class ObservePlannedExercisesWithDetailsUseCase @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
) {
    operator fun invoke(dayId: String): Flow<List<PlannedExerciseWithDetails>> =
        workoutRepository.getPlannedExercisesForDay(dayId).map { plannedExercises ->
            val exercisesById = exerciseRepository.getByIds(plannedExercises.map { it.exerciseId })
            plannedExercises.map { plannedExercise ->
                plannedExercise to exercisesById[plannedExercise.exerciseId]
            }
        }
}
