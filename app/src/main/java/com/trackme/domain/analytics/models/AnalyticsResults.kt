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
    val matrixPosition: ConsistencyMatrixPosition?,
    val fullAnalytics: FullProgressAnalytics? = null
)

// --- Deterministic Body Progression Analytics Models ---

data class MuscleDevelopment(
    val muscleGroup: String,
    val growthIndex: Float,
    val percentageGrowth: Float,
    val stimulusThisWeek: Float
)

data class MuscleStimulusHeatmap(
    val muscleGroup: String,
    val stimulus: Float,
    val minRecommended: Float,
    val maxRecommended: Float,
    val status: String, // "Undertrained", "Optimal", "Overtrained"
    val ratio: Float
)

data class StrengthProjectionPoint(
    val exerciseId: String,
    val exerciseName: String,
    val current1RM: Float,
    val projected30Days: String,  // String to allow "Not enough data for prediction"
    val projected90Days: String,
    val projected365Days: String,
    val historyPoints: List<Pair<Long, Float>> // Date to 1RM
)

data class EnergyBreakdown(
    val dateMillis: Long,
    val bmr: Float,
    val steps: Long,
    val stepsCalories: Float,
    val activeCalories: Float,
    val caloriesLifting: Float,
    val totalTDEE: Float
)

data class PhysiqueProjectionPoint(
    val muscleGroup: String,
    val currentMuscleIndex: Float,
    val projectedIndex4Weeks: Float,
    val projectedIndex12Weeks: Float,
    val projectedIndex52Weeks: Float
)

data class MuscleBalanceInfo(
    val pushPullRatio: Float,
    val pushPullStatus: String,
    val quadHamRatio: Float,
    val quadHamStatus: String,
    val upperLowerRatio: Float,
    val upperLowerStatus: String
)

data class PlateauInfo(
    val exerciseId: String,
    val exerciseName: String,
    val improvementPercentage: Float,
    val isPlateaued: Boolean
)

data class MuscleRankingInfo(
    val mostTrained: List<String>,
    val leastTrained: List<String>,
    val fastestGrowing: List<String>
)

data class FullProgressAnalytics(
    val muscleDevelopment: List<MuscleDevelopment>,
    val stimulusHeatmap: List<MuscleStimulusHeatmap>,
    val strengthProjections: List<StrengthProjectionPoint>,
    val energyExpenditure: EnergyBreakdown,
    val physiquePrediction: List<PhysiqueProjectionPoint>,
    val muscleBalance: MuscleBalanceInfo,
    val plateaus: List<PlateauInfo>,
    val muscleRankings: MuscleRankingInfo,
    val consistencyScore: Float, // 0.0 to 1.0
    val weeklyVolumeHistory: List<Pair<String, Float>> // Week Label -> Volume
)

