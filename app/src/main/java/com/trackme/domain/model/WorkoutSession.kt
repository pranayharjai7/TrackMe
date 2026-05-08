package com.trackme.domain.model

data class WorkoutSession(
    val id: String,
    val userId: String,
    val dayId: String,
    val date: Long,
    val durationMinutes: Int,
    val notes: String,
    val updatedAt: Long,
)
