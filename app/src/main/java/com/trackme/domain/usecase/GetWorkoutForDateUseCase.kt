package com.trackme.domain.usecase

import com.trackme.domain.model.DayOfWeek
import com.trackme.domain.model.WorkoutDay
import com.trackme.domain.repository.WorkoutRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import javax.inject.Inject

class GetWorkoutForDateUseCase @Inject constructor(private val repo: WorkoutRepository) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(userId: String, date: LocalDate): Flow<WorkoutDay?> =
        repo.getActivePlan(userId).flatMapLatest { plan ->
            if (plan == null) flowOf(null)
            else repo.getDaysForPlan(plan.id).map { days ->
                val dayOfWeekEnum = when (date.dayOfWeek) {
                    java.time.DayOfWeek.MONDAY -> DayOfWeek.MON
                    java.time.DayOfWeek.TUESDAY -> DayOfWeek.TUE
                    java.time.DayOfWeek.WEDNESDAY -> DayOfWeek.WED
                    java.time.DayOfWeek.THURSDAY -> DayOfWeek.THU
                    java.time.DayOfWeek.FRIDAY -> DayOfWeek.FRI
                    java.time.DayOfWeek.SATURDAY -> DayOfWeek.SAT
                    java.time.DayOfWeek.SUNDAY -> DayOfWeek.SUN
                }
                days.firstOrNull { it.dayOfWeek == dayOfWeekEnum }
            }
        }
}
