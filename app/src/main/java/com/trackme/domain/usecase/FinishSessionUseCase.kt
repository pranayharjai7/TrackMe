package com.trackme.domain.usecase

import com.trackme.domain.repository.WorkoutRepository
import javax.inject.Inject

class FinishSessionUseCase @Inject constructor(private val repo: WorkoutRepository) {
    suspend operator fun invoke(sessionId: String, startTimeMillis: Long) {
        val durationMinutes = maxOf(1, ((System.currentTimeMillis() - startTimeMillis) / 60_000).toInt())
        repo.finishSession(sessionId, durationMinutes)
    }
}
