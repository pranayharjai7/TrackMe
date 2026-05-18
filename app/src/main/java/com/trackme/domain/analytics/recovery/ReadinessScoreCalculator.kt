package com.trackme.domain.analytics.recovery

import com.trackme.domain.analytics.models.HealthMetricsData
import com.trackme.domain.analytics.models.ReadinessScore
import com.trackme.domain.analytics.models.ReadinessScoreDebug
import kotlin.math.max

/**
 * Pure analytic module for computing daily readiness.
 *
 * Requirements:
 * - Handle < 7 days of data gracefully (Insufficient Data)
 * - Safely handle missing HRV or Sleep data on any given day.
 * - Outlier mitigation via baseline averages.
 */
class ReadinessScoreCalculator {

    fun calculate(
        historicalMetrics: List<HealthMetricsData>,
        todayMetric: HealthMetricsData?
    ): ReadinessScore {
        // Edge Case: No data for today
        if (todayMetric == null) {
            return ReadinessScore(
                score = 0,
                status = "Insufficient Data",
                debug = ReadinessScoreDebug(0f, 0f, 0f, listOf("todayMetric is null"))
            )
        }

        // Edge Case: Users with < 7 days history
        if (historicalMetrics.size < 7) {
            return ReadinessScore(
                score = 0,
                status = "Establishing Baseline (${historicalMetrics.size}/7 days)",
                debug = ReadinessScoreDebug(0f, 0f, 0f, listOf("historicalMetrics < 7 days"))
            )
        }

        val missingFlags = mutableListOf<String>()

        // 1. Calculate Baselines (Averages over history)
        val validHrvs = historicalMetrics.mapNotNull { it.hrvRmssd }.filter { it > 0 }
        val validRhrs = historicalMetrics.mapNotNull { it.restingHeartRate }.filter { it > 0 }

        val baselineHrv = if (validHrvs.isNotEmpty()) validHrvs.average().toFloat() else null
        val baselineRhr = if (validRhrs.isNotEmpty()) validRhrs.average().toFloat() else null

        // 2. Compute Deviations
        var hrvDeviation = 0f
        var hasHrv = false
        if (baselineHrv != null && todayMetric.hrvRmssd != null && todayMetric.hrvRmssd > 0) {
            hrvDeviation = (todayMetric.hrvRmssd - baselineHrv) / baselineHrv
            hasHrv = true
        } else {
            missingFlags.add("Missing HRV data")
        }

        var rhrDeviation = 0f
        var hasRhr = false
        if (baselineRhr != null && todayMetric.restingHeartRate != null && todayMetric.restingHeartRate > 0) {
            rhrDeviation = (baselineRhr - todayMetric.restingHeartRate) / baselineRhr
            hasRhr = true
        } else {
            missingFlags.add("Missing RHR data")
        }

        var sleepPenalty = 0f
        var hasSleep = false
        if (todayMetric.sleepDurationMinutes != null && todayMetric.sleepDurationMinutes > 0) {
            val sleepHours = todayMetric.sleepDurationMinutes / 60f
            if (sleepHours < 6.0f) {
                sleepPenalty = ((6.0f - sleepHours) / 6.0f) * 0.3f
            } else if (sleepHours >= 7.5f) {
                sleepPenalty = -0.1f 
            }
            hasSleep = true
        } else {
            missingFlags.add("Missing Sleep data")
        }

        // 3. Dynamic Weight Redistribution
        val baselineWeights = mutableMapOf<String, Float>()
        if (hasHrv) baselineWeights["HRV"] = 0.40f
        if (hasRhr) baselineWeights["RHR"] = 0.30f
        if (hasSleep) baselineWeights["Sleep"] = 0.30f

        val totalAvailableWeight = baselineWeights.values.sum()
        if (totalAvailableWeight == 0f) {
            return ReadinessScore(
                score = 0,
                status = "Insufficient Data",
                debug = ReadinessScoreDebug(0f, 0f, 0f, missingFlags)
            )
        }

        // Normalize weights to sum to 1.0
        val normalizedWeights = baselineWeights.mapValues { it.value / totalAvailableWeight }

        // Compute normalized impacts scaled to a standard 0-100 range
        val rawHrvImpact = (hrvDeviation * 100f).coerceIn(-20f, 20f)
        val rawRhrImpact = (rhrDeviation * 100f).coerceIn(-15f, 15f)
        val rawSleepImpact = -(sleepPenalty * 100f)

        // Apply redistributed weights
        val hrvImpact = if (hasHrv) rawHrvImpact * (normalizedWeights["HRV"] ?: 0f) / 0.40f else 0f
        val rhrImpact = if (hasRhr) rawRhrImpact * (normalizedWeights["RHR"] ?: 0f) / 0.30f else 0f
        val sleepImpact = if (hasSleep) rawSleepImpact * (normalizedWeights["Sleep"] ?: 0f) / 0.30f else 0f

        val baseScore = 80f
        val finalScore = (baseScore + hrvImpact + rhrImpact + sleepImpact).toInt().coerceIn(0, 100)

        val status = when {
            finalScore >= 85 -> "Optimal"
            finalScore >= 65 -> "Good"
            finalScore >= 40 -> "Moderate"
            else -> "Needs Recovery"
        }

        return ReadinessScore(
            score = finalScore,
            status = status,
            debug = ReadinessScoreDebug(
                hrvDeviationPercentage = hrvDeviation,
                rhrDeviationPercentage = rhrDeviation,
                sleepScorePenalty = sleepPenalty,
                missingDataFlags = missingFlags
            )
        )
    }
}
