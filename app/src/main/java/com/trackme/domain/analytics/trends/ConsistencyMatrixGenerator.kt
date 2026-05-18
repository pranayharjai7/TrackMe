package com.trackme.domain.analytics.trends

import com.trackme.domain.analytics.models.ConsistencyMatrixPosition
import com.trackme.domain.analytics.models.MatrixQuadrant
import com.trackme.domain.analytics.models.WorkoutSessionAnalyticsData

/**
 * Pure analytic module placing the user into a Consistency vs. Intensity Matrix quadrant.
 */
class ConsistencyMatrixGenerator {

    companion object {
        const val WINDOW_DAYS = 30L
        const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000L
        
        // Thresholds defining the matrix axes
        const val CONSISTENCY_THRESHOLD_WORKOUTS_PER_WEEK = 3f // >3 times/week is High Consistency
        const val INTENSITY_THRESHOLD_KG_PER_MINUTE = 150f // >150kg/min is High Intensity
    }

    fun generate(
        history: List<WorkoutSessionAnalyticsData>,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): ConsistencyMatrixPosition? {
        val cutoffTime = currentTimeMillis - (WINDOW_DAYS * MILLIS_PER_DAY)
        val recentSessions = history.filter { it.dateMillis >= cutoffTime && it.durationMinutes > 0 }

        if (recentSessions.isEmpty()) return null

        // 1. Calculate Consistency (Workouts per week over the 30 day window)
        val weeksInWindow = WINDOW_DAYS / 7f
        val consistencyScore = recentSessions.size / weeksInWindow

        // 2. Calculate Intensity (Average Tonnage / Minute)
        val totalVolume = recentSessions.sumOf { it.totalVolumeKg.toDouble() }
        val totalActiveMinutes = recentSessions.sumOf { it.durationMinutes }
        
        val intensityScore = if (totalActiveMinutes > 0) {
            (totalVolume / totalActiveMinutes).toFloat()
        } else 0f

        // 3. Determine Quadrant
        val isHighCons = consistencyScore >= CONSISTENCY_THRESHOLD_WORKOUTS_PER_WEEK
        val isHighInt = intensityScore >= INTENSITY_THRESHOLD_KG_PER_MINUTE

        val quadrant = when {
            isHighCons && isHighInt -> MatrixQuadrant.JUGGERNAUT
            isHighCons && !isHighInt -> MatrixQuadrant.BUILDER
            !isHighCons && isHighInt -> MatrixQuadrant.WEEKEND_WARRIOR
            else -> MatrixQuadrant.RECHARGING
        }

        return ConsistencyMatrixPosition(
            consistencyScore = consistencyScore,
            intensityScore = intensityScore,
            quadrant = quadrant
        )
    }
}
