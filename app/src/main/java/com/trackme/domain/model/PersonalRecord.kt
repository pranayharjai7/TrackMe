package com.trackme.domain.model

data class PersonalRecord(
    val id: String,
    val userId: String,
    val exerciseId: String,
    val maxWeightKg: Float,
    val maxReps: Int,
    val achievedAt: Long,
)
