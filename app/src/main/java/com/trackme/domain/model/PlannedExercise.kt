package com.trackme.domain.model

data class PlannedExercise(
    val id: String,
    val dayId: String,
    val userId: String,
    val exerciseId: String,
    val orderIndex: Int,
    val updatedAt: Long,
    val targetSets: Int = 3,
    val targetReps: Int? = null,
    val targetWeightKg: Float? = null,
    val targetDurationSeconds: Int? = null,
    val targetDistanceKm: Float? = null,
    val targetSpeedKmh: Float? = null,
    val targetIncline: Float? = null,
)
