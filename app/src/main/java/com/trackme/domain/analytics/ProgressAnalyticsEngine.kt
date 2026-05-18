package com.trackme.domain.analytics

import com.trackme.domain.analytics.models.*
import com.trackme.domain.analytics.muscle.MuscleFatigueCalculator
import com.trackme.domain.analytics.performance.OneRMProjectionEngine
import com.trackme.domain.analytics.performance.PlateauDetector
import com.trackme.domain.analytics.recovery.ReadinessScoreCalculator
import com.trackme.domain.analytics.trends.ConsistencyMatrixGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central orchestrator for all complex health and performance computations.
 * Designed to prevent the UI or ViewModels from running heavy math.
 * 
 * Features:
 * - Thread isolation (Dispatchers.Default)
 * - In-memory caching
 */
@Singleton
class ProgressAnalyticsEngine @Inject constructor(
    private val readinessCalculator: ReadinessScoreCalculator,
    private val oneRMEngine: OneRMProjectionEngine,
    private val fatigueCalculator: MuscleFatigueCalculator,
    private val plateauDetector: PlateauDetector,
    private val matrixGenerator: ConsistencyMatrixGenerator
) {
    
    // In-memory cache variables.
    // In a fully persistent implementation, these would be backed by Room AnalyticsSnapshotEntity
    private var lastComputedSessionTimestamp: Long = -1L
    private var cachedResult: EngineAnalyticsResult? = null

    /**
     * Recomputes analytics ONLY if the newest session timestamp has changed.
     * Runs strictly on Dispatchers.Default for CPU performance.
     */
    suspend fun computeAnalytics(
        userId: String,
        workoutHistory: List<WorkoutSessionAnalyticsData>,
        healthHistory: List<HealthMetricsData>,
        forceRecompute: Boolean = false
    ): EngineAnalyticsResult = withContext(Dispatchers.Default) {
        
        val latestSessionTime = workoutHistory.maxOfOrNull { it.dateMillis } ?: 0L

        // Return cached result if nothing has changed
        if (!forceRecompute && cachedResult != null && latestSessionTime == lastComputedSessionTimestamp) {
            return@withContext cachedResult!!
        }

        // 1. Calculate Readiness
        val todayStart = com.trackme.utils.startOfLocalDayMillis(System.currentTimeMillis())
        val historicalHealth = healthHistory.filter { it.dateMillis < todayStart }
        val todayHealth = healthHistory.firstOrNull { it.dateMillis == todayStart }
        val readiness = readinessCalculator.calculate(historicalHealth, todayHealth)

        // 2. Flatten exercise history for performance modules
        val allExercises = workoutHistory.flatMap { it.exercises }

        // 3. Calculate 1RM Projections for major lifts
        // Let's assume we want to project major compounds. Here we extract unique names to process top ones.
        val topExercises = allExercises
            .groupBy { it.exerciseId }
            .mapValues { it.value.first().name }
            .toList()
            .take(5) // Limit to 5 for performance. Real app would let user select these.

        val projections = topExercises.mapNotNull { (id, name) ->
            oneRMEngine.calculateProjection(allExercises, id, name)
        }

        // 4. Calculate Muscle Fatigue Heatmap
        val fatigueMap = fatigueCalculator.calculate(workoutHistory)

        // 5. Detect Plateaus
        val plateaus = plateauDetector.detectPlateaus(allExercises)

        // 6. Generate Consistency Matrix
        val matrixPosition = matrixGenerator.generate(workoutHistory)

        val result = EngineAnalyticsResult(
            readinessScore = readiness,
            oneRmProjections = projections,
            muscleFatigueMap = fatigueMap,
            plateauAlerts = plateaus,
            matrixPosition = matrixPosition
        )

        // Update Cache
        cachedResult = result
        lastComputedSessionTimestamp = latestSessionTime

        result
    }
}
