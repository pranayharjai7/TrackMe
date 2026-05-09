package com.trackme.domain.usecase

import com.trackme.domain.model.DayOfWeek
import com.trackme.domain.model.WorkoutDay
import com.trackme.domain.repository.WorkoutRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.util.Calendar
import javax.inject.Inject

class GetTodayWorkoutUseCase @Inject constructor(private val repo: WorkoutRepository) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(userId: String): Flow<WorkoutDay?> =
        repo.getActivePlan(userId).flatMapLatest { plan ->
            if (plan == null) flowOf(null)
            else repo.getDaysForPlan(plan.id).map { days ->
                val today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
                val todayEnum = when (today) {
                    Calendar.MONDAY -> DayOfWeek.MON
                    Calendar.TUESDAY -> DayOfWeek.TUE
                    Calendar.WEDNESDAY -> DayOfWeek.WED
                    Calendar.THURSDAY -> DayOfWeek.THU
                    Calendar.FRIDAY -> DayOfWeek.FRI
                    Calendar.SATURDAY -> DayOfWeek.SAT
                    else -> DayOfWeek.SUN
                }
                days.firstOrNull { it.dayOfWeek == todayEnum }
            }
        }
}
