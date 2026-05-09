package com.trackme.domain.usecase

import com.trackme.domain.model.SessionSet
import com.trackme.domain.repository.WorkoutRepository
import java.util.UUID
import javax.inject.Inject

class LogSetUseCase @Inject constructor(private val repo: WorkoutRepository) {
    suspend operator fun invoke(
        sessionId: String,
        userId: String,
        exerciseId: String,
        setNumber: Int,
        weightKg: Float,
        reps: Int,
    ): SessionSet {
        val set = SessionSet(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            userId = userId,
            exerciseId = exerciseId,
            setNumber = setNumber,
            weightKg = weightKg,
            reps = reps,
            completed = true,
            updatedAt = System.currentTimeMillis(),
        )
        repo.logSet(set)
        repo.updatePersonalRecord(userId, exerciseId, weightKg, reps, set.updatedAt)
        return set
    }
}
