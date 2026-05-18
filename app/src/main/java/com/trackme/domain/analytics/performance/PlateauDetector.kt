package com.trackme.domain.analytics.performance

import com.trackme.domain.analytics.models.ExerciseAnalyticsData
import com.trackme.domain.analytics.models.PlateauAlert
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Pure analytic module that detects if an exercise has stalled.
 * Stalling is defined as having extremely low variance in either max weight or total volume over 4+ weeks.
 */
class PlateauDetector {

    companion object {
        const val MILLIS_PER_WEEK = 7 * 24 * 60 * 60 * 1000L
        const val PLATEAU_WINDOW_WEEKS = 4
        // If the standard deviation divided by the mean is less than this percentage, it's a plateau.
        const val VARIANCE_THRESHOLD_PERCENT = 0.02f // 2% variation
    }

    fun detectPlateaus(
        history: List<ExerciseAnalyticsData>,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): List<PlateauAlert> {
        val cutoffTime = currentTimeMillis - (PLATEAU_WINDOW_WEEKS * MILLIS_PER_WEEK)
        
        // Group by exercise ID
        val exercisesById = history.groupBy { it.exerciseId }
        val alerts = mutableListOf<PlateauAlert>()

        for ((exerciseId, sets) in exercisesById) {
            val name = sets.firstOrNull()?.name ?: "Unknown Exercise"

            // Only look at the last 4 weeks
            val recentSets = sets.filter { it.dateMillis >= cutoffTime }
            
            // Need at least 3 distinct days in the last 4 weeks to determine a plateau reliably
            val distinctDays = recentSets.map { it.dateMillis / (24 * 60 * 60 * 1000L) }.distinct().size
            if (distinctDays < 3) continue

            // Group recent sets into weekly buckets to calculate weekly max weight and weekly total volume
            val weeklyData = recentSets
                .groupBy { (currentTimeMillis - it.dateMillis) / MILLIS_PER_WEEK }
                .mapValues { (_, weekSets) ->
                    val maxWeight = weekSets.maxOfOrNull { it.weightKg } ?: 0f
                    val totalVolume = weekSets.sumOf { (it.weightKg * it.reps).toDouble() }.toFloat()
                    Pair(maxWeight, totalVolume)
                }

            if (weeklyData.size < 3) continue // Not enough weekly data points

            val weights = weeklyData.values.map { it.first }
            val volumes = weeklyData.values.map { it.second }

            val weightStalled = isStalled(weights)
            val volumeStalled = isStalled(volumes)

            if (weightStalled && volumeStalled) {
                // Find the date the plateau likely started (earliest date in our 4 week window)
                val stalledSince = recentSets.minOfOrNull { it.dateMillis } ?: cutoffTime
                alerts.add(
                    PlateauAlert(
                        exerciseId = exerciseId,
                        exerciseName = name,
                        stalledSinceMillis = stalledSince,
                        recommendation = "Weight and volume have stalled. Consider a deload week or switching to a different rep range (e.g., 5x5 to 3x10)."
                    )
                )
            } else if (weightStalled) {
                 val stalledSince = recentSets.minOfOrNull { it.dateMillis } ?: cutoffTime
                 alerts.add(
                    PlateauAlert(
                        exerciseId = exerciseId,
                        exerciseName = name,
                        stalledSinceMillis = stalledSince,
                        recommendation = "Max weight is plateaued. Try micro-loading (adding 1.25kg) or increasing sets."
                    )
                )
            }
        }

        return alerts
    }

    /**
     * Checks if a list of values has very low variance (Standard Deviation / Mean < Threshold)
     */
    private fun isStalled(values: List<Float>): Boolean {
        if (values.isEmpty()) return false
        val mean = values.average().toFloat()
        if (mean == 0f) return true

        val variance = values.map { (it - mean).pow(2) }.average().toFloat()
        val stdDev = sqrt(variance)

        val coefficientOfVariation = stdDev / mean
        return coefficientOfVariation < VARIANCE_THRESHOLD_PERCENT
    }
}
