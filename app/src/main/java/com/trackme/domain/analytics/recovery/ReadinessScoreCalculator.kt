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
        if (baselineHrv != null && todayMetric.hrvRmssd != null && todayMetric.hrvRmssd > 0) {
            // Higher HRV is generally better. 
            // Deviation = (Today - Baseline) / Baseline
            hrvDeviation = (todayMetric.hrvRmssd - baselineHrv) / baselineHrv
        } else {
            missingFlags.add("Missing HRV data")
        }

        var rhrDeviation = 0f
        if (baselineRhr != null && todayMetric.restingHeartRate != null && todayMetric.restingHeartRate > 0) {
            // Lower RHR is better. Negative deviation is good.
            // Deviation = (Baseline - Today) / Baseline
            rhrDeviation = (baselineRhr - todayMetric.restingHeartRate) / baselineRhr
        } else {
            missingFlags.add("Missing RHR data")
        }

        // 3. Compute Sleep Penalty
        var sleepPenalty = 0f
        if (todayMetric.sleepDurationMinutes != null) {
            val sleepHours = todayMetric.sleepDurationMinutes / 60f
            if (sleepHours < 6.0f) {
                // Penalty linear from 6 hrs down to 0 hrs. Max 30% penalty.
                sleepPenalty = ((6.0f - sleepHours) / 6.0f) * 0.3f
            } else if (sleepHours >= 7.5f) {
                // Bonus for great sleep
                sleepPenalty = -0.1f 
            }
        } else {
            missingFlags.add("Missing Sleep data")
            // If missing sleep, we don't penalize, we just adjust weights later or assume ok.
        }

        // 4. Weighting
        // Base score is 80 (assumes generally ready unless metrics drag it down or push it up)
        var baseScore = 80f

        // HRV deviation usually ranges from -0.3 (bad) to +0.3 (good)
        // Let's cap the influence to +/- 20 points
        val hrvImpact = (hrvDeviation * 100f).coerceIn(-20f, 20f)
        
        // RHR deviation usually ranges from -0.15 (bad) to +0.15 (good)
        // Let's cap influence to +/- 15 points
        val rhrImpact = (rhrDeviation * 100f).coerceIn(-15f, 15f)

        // Sleep penalty is 0 to 0.30 -> up to -30 points
        val sleepImpact = -(sleepPenalty * 100f)

        // Adjust base score based on available data
        if (missingFlags.contains("Missing HRV data") && missingFlags.contains("Missing RHR data")) {
            // Totally blind on heart metrics, just base on sleep
            baseScore = 70f + sleepImpact
        } else {
            // We have some heart metrics
            val hrvWeight = if (missingFlags.contains("Missing HRV data")) 0f else 1f
            val rhrWeight = if (missingFlags.contains("Missing RHR data")) 0f else 1f
            
            // Normalize weights if one is missing
            val totalWeight = hrvWeight + rhrWeight
            val hrvFinalImpact = if (totalWeight > 0) hrvImpact * (2f / totalWeight) * hrvWeight else 0f
            val rhrFinalImpact = if (totalWeight > 0) rhrImpact * (2f / totalWeight) * rhrWeight else 0f

            baseScore = baseScore + hrvFinalImpact + rhrFinalImpact + sleepImpact
        }

        val finalScore = baseScore.toInt().coerceIn(0, 100)

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
