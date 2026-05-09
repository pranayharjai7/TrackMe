package com.trackme.domain.usecase

import com.trackme.domain.model.DayOfWeek
import com.trackme.domain.model.WorkoutDay
import com.trackme.domain.repository.WorkoutRepository
import java.util.UUID
import javax.inject.Inject

class SaveWorkoutDayUseCase @Inject constructor(private val repo: WorkoutRepository) {
    suspend operator fun invoke(planId: String, userId: String, dayOfWeek: DayOfWeek, name: String): WorkoutDay {
        val day = WorkoutDay(
            id = UUID.randomUUID().toString(),
            planId = planId,
            userId = userId,
            dayOfWeek = dayOfWeek,
            name = name,
            updatedAt = System.currentTimeMillis(),
        )
        repo.saveDay(day)
        return day
    }
}
