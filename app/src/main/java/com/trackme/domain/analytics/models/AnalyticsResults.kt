package com.trackme.domain.analytics.models

// 1. Readiness Score
data class ReadinessScoreDebug(
    val hrvDeviationPercentage: Float,
    val rhrDeviationPercentage: Float,
    val sleepScorePenalty: Float,
    val missingDataFlags: List<String>
)

data class ReadinessScore(
    val score: Int, // 0-100
    val status: String, // "Optimal", "Good", "Needs Recovery", "Insufficient Data"
    val debug: ReadinessScoreDebug? = null
)

// 2. 1RM Projection
data class OneRMProjection(
    val exerciseId: String,
    val exerciseName: String,
    val current1RM: Float,
    val projected1RM14Days: Float,
    val historyPoints: List<Pair<Long, Float>> // Date to 1RM
)

// 3. Muscle Fatigue
data class MuscleFatigue(
    val muscleGroup: String,
    val fatiguePercentage: Int, // 0 = Fully Recovered, 100 = Highly Fatigued
    val recoveryTimeRemainingHours: Int
)

// 4. Plateau Detection
data class PlateauAlert(
    val exerciseId: String,
    val exerciseName: String,
    val stalledSinceMillis: Long,
    val recommendation: String
)

// 5. Consistency Matrix
enum class MatrixQuadrant {
    JUGGERNAUT, // High Consistency, High Intensity
    BUILDER,    // High Consistency, Low Intensity
    WEEKEND_WARRIOR, // Low Consistency, High Intensity
    RECHARGING // Low Consistency, Low Intensity
}

data class ConsistencyMatrixPosition(
    val consistencyScore: Float, // workouts per week
    val intensityScore: Float,   // volume per minute
    val quadrant: MatrixQuadrant
)

// Aggregated Result emitted by the Engine
data class EngineAnalyticsResult(
    val readinessScore: ReadinessScore,
    val oneRmProjections: List<OneRMProjection>,
    val muscleFatigueMap: Map<String, MuscleFatigue>,
    val plateauAlerts: List<PlateauAlert>,
    val matrixPosition: ConsistencyMatrixPosition?
)
