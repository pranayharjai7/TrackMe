package com.trackme.domain.model

data class SessionSet(
    val id: String,
    val sessionId: String,
    val userId: String,
    val exerciseId: String,
    val setNumber: Int,
    val weightKg: Float,
    val reps: Int,
    val completed: Boolean,
    val updatedAt: Long,
    val durationSeconds: Int? = null,
    val distanceKm: Float? = null,
    val speedKmh: Float? = null,
    val inclinePercent: Float? = null,
)
