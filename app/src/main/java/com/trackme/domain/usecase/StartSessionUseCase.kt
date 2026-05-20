package com.trackme.domain.usecase

import com.trackme.domain.model.WorkoutSession
import com.trackme.domain.repository.WorkoutRepository
import java.util.UUID
import javax.inject.Inject

class StartSessionUseCase @Inject constructor(private val repo: WorkoutRepository) {
    suspend operator fun invoke(userId: String, dayId: String, dateMillis: Long = System.currentTimeMillis()): WorkoutSession {
        val session = WorkoutSession(
            id = UUID.randomUUID().toString(),
            userId = userId,
            dayId = dayId,
            date = dateMillis,
            durationMinutes = 0,
            notes = "",
            updatedAt = System.currentTimeMillis(),
        )
        repo.startSession(session)
        return session
    }
}
