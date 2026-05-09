package com.trackme.domain.usecase

import com.trackme.domain.model.PlannedExercise
import com.trackme.domain.repository.WorkoutRepository
import java.util.UUID
import javax.inject.Inject

class AddExerciseToDayUseCase @Inject constructor(private val repo: WorkoutRepository) {
    suspend operator fun invoke(
        dayId: String, userId: String, exerciseId: String, orderIndex: Int,
        targetSets: Int = 3,
        targetReps: Int? = null,
        targetWeightKg: Float? = null,
        targetDurationSeconds: Int? = null,
        targetDistanceKm: Float? = null,
        targetSpeedKmh: Float? = null,
        targetIncline: Float? = null,
    ) {
        repo.addPlannedExercise(
            PlannedExercise(
                id = UUID.randomUUID().toString(),
                dayId = dayId,
                userId = userId,
                exerciseId = exerciseId,
                orderIndex = orderIndex,
                updatedAt = System.currentTimeMillis(),
                targetSets = targetSets,
                targetReps = targetReps,
                targetWeightKg = targetWeightKg,
                targetDurationSeconds = targetDurationSeconds,
                targetDistanceKm = targetDistanceKm,
                targetSpeedKmh = targetSpeedKmh,
                targetIncline = targetIncline,
            )
        )
    }
}
