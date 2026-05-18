package com.trackme.domain.analytics.models

/**
 * Pure data model representing raw health metrics for analytics modules.
 */
data class HealthMetricsData(
    val dateMillis: Long,
    val hrvRmssd: Float?,
    val restingHeartRate: Int?,
    val sleepDurationMinutes: Int?,
    val deepSleepMinutes: Int?
)

/**
 * Pure data model representing a single exercise's execution within a session.
 */
data class ExerciseAnalyticsData(
    val setId: String,
    val exerciseId: String,
    val name: String,
    val dateMillis: Long,
    val targetMuscles: List<String>,
    val weightKg: Float,
    val reps: Int,
    val durationSeconds: Int?
)

/**
 * Pure data model representing a completed workout session.
 */
data class WorkoutSessionAnalyticsData(
    val sessionId: String,
    val dateMillis: Long,
    val durationMinutes: Int,
    val totalVolumeKg: Float,
    val exercises: List<ExerciseAnalyticsData>
)
