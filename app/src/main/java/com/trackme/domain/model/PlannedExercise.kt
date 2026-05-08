package com.trackme.domain.model

data class PlannedExercise(
    val id: String,
    val dayId: String,
    val userId: String,
    val exerciseId: String,
    val orderIndex: Int,
    val updatedAt: Long,
)
