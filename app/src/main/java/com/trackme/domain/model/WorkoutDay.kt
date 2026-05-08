package com.trackme.domain.model

data class WorkoutDay(
    val id: String,
    val planId: String,
    val userId: String,
    val dayOfWeek: DayOfWeek,
    val name: String,
    val updatedAt: Long,
)

enum class DayOfWeek { MON, TUE, WED, THU, FRI, SAT, SUN }
