package com.trackme.domain.analytics.utils

import com.trackme.domain.analytics.models.ExerciseAnalyticsData
import com.trackme.domain.analytics.models.WorkoutSessionAnalyticsData

/**
 * Validation rules and deduplication filters for ProgressAnalyticsEngine.
 */
object ValidationUtils {

    fun ExerciseAnalyticsData.isValid(): Boolean {
        return weightKg in 0.1f..800f && 
               reps in 1..100 && 
               (durationSeconds == null || durationSeconds >= 0)
    }

    fun WorkoutSessionAnalyticsData.isValid(): Boolean {
        return durationMinutes > 0 && exercises.isNotEmpty()
    }

    /**
     * Strictly deduplicates exercises by their unique database SessionSet ID (setId).
     * This preserves multiple identical sets of the same exercise (e.g. 5x10 at 100kg)
     * while safely purging double-write sync anomalies.
     */
    fun List<ExerciseAnalyticsData>.deduplicateSets(): List<ExerciseAnalyticsData> {
        return this.distinctBy { it.setId }
    }
}
