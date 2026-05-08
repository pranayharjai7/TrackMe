package com.trackme.domain.model

data class WorkoutPlan(
    val id: String,
    val userId: String,
    val name: String,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
