package com.trackme.domain.analytics.performance

import com.trackme.domain.analytics.models.ExerciseAnalyticsData
import com.trackme.domain.analytics.models.OneRMProjection
import kotlin.math.max

/**
 * Pure analytic module for computing 1RM (One Rep Max) and projecting future performance.
 */
class OneRMProjectionEngine {

    companion object {
        const val PROJECTION_DAYS_AHEAD = 14L
        const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000L
    }

    /**
     * @param history Raw exercise data points across history
     * @param exerciseId The specific exercise to project
     * @param exerciseName Human readable name
     * @param limitDays Lookback window (e.g. 90 days)
     */
    fun calculateProjection(
        history: List<ExerciseAnalyticsData>,
        exerciseId: String,
        exerciseName: String,
        limitDays: Int = 90
    ): OneRMProjection? {
        val targetHistory = history.filter { it.exerciseId == exerciseId }
        if (targetHistory.isEmpty()) return null

        val now = System.currentTimeMillis()
        val limitMillis = now - (limitDays * MILLIS_PER_DAY)

        // 1. Group by session day and calculate daily estimated 1RM using Epley formula
        // Epley: 1RM = Weight * (1 + Reps/30)
        val dailyMaxes = targetHistory
            .filter { it.dateMillis >= limitMillis && it.weightKg > 0 && it.reps > 0 }
            .groupBy { it.dateMillis / MILLIS_PER_DAY }
            .mapNotNull { (dayIdx, sets) ->
                // For each day, find the highest 1RM achieved
                val maxEpley = sets.maxOfOrNull { set ->
                    set.weightKg * (1f + (set.reps / 30f))
                }
                if (maxEpley != null) Pair(dayIdx * MILLIS_PER_DAY, maxEpley) else null
            }
            .sortedBy { it.first }

        if (dailyMaxes.isEmpty()) return null

        // 2. Outlier rejection (IQR filter) to ignore bad/extreme logs
        val filteredMaxes = removeOutliers(dailyMaxes)

        if (filteredMaxes.isEmpty()) return null

        // 3. Current 1RM (highest in recent history, or last known)
        val current1RM = filteredMaxes.last().second

        // 4. Linear Regression for Projection
        val projected1RM = if (filteredMaxes.size >= 3) {
            calculateLinearRegressionProjection(filteredMaxes, now + (PROJECTION_DAYS_AHEAD * MILLIS_PER_DAY))
        } else {
            // Not enough data to project a trend, assume maintenance
            current1RM
        }

        return OneRMProjection(
            exerciseId = exerciseId,
            exerciseName = exerciseName,
            current1RM = current1RM,
            projected1RM14Days = max(current1RM, projected1RM), // Never project getting drastically weaker as a "goal"
            historyPoints = filteredMaxes
        )
    }

    private fun removeOutliers(data: List<Pair<Long, Float>>): List<Pair<Long, Float>> {
        if (data.size < 4) return data // Too small for IQR
        
        val sortedValues = data.map { it.second }.sorted()
        val q1 = sortedValues[sortedValues.size / 4]
        val q3 = sortedValues[(sortedValues.size * 3) / 4]
        val iqr = q3 - q1
        
        // standard 1.5 multiplier for IQR
        val lowerBound = q1 - (1.5f * iqr)
        val upperBound = q3 + (1.5f * iqr)

        return data.filter { it.second in lowerBound..upperBound }
    }

    private fun calculateLinearRegressionProjection(data: List<Pair<Long, Float>>, targetTimeMillis: Long): Float {
        // Simple Least Squares Linear Regression
        // y = mx + b
        // m = (N*Σ(xy) - Σx*Σy) / (N*Σ(x^2) - (Σx)^2)
        // b = (Σy - m*Σx) / N

        val n = data.size
        val xValues = data.map { it.first / MILLIS_PER_DAY.toDouble() } // Use days as X to prevent massive Long overflow
        val yValues = data.map { it.second.toDouble() }

        val sumX = xValues.sum()
        val sumY = yValues.sum()
        val sumXY = xValues.zip(yValues) { x, y -> x * y }.sum()
        val sumX2 = xValues.sumOf { it * it }

        val denominator = (n * sumX2) - (sumX * sumX)
        if (denominator == 0.0) return yValues.last().toFloat() // Avoid div by zero

        val m = ((n * sumXY) - (sumX * sumY)) / denominator
        val b = (sumY - (m * sumX)) / n

        val targetX = targetTimeMillis / MILLIS_PER_DAY.toDouble()
        return ((m * targetX) + b).toFloat()
    }
}
