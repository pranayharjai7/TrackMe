package com.trackme.domain.analytics

import com.trackme.domain.analytics.models.*
import com.trackme.domain.analytics.muscle.MuscleFatigueCalculator
import com.trackme.domain.analytics.performance.OneRMProjectionEngine
import com.trackme.domain.analytics.performance.PlateauDetector
import com.trackme.domain.analytics.recovery.ReadinessScoreCalculator
import com.trackme.domain.analytics.trends.ConsistencyMatrixGenerator
import com.trackme.domain.analytics.utils.ValidationUtils.isValid
import com.trackme.domain.analytics.utils.ValidationUtils.deduplicateSets
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
    
    // User-scoped composite cache state
    data class CacheKey(
        val userId: String,
        val latestWorkoutTimestamp: Long,
        val latestHealthTimestamp: Long,
        val workoutSize: Int,
        val healthSize: Int
    )

    private var lastCacheKey: CacheKey? = null
    private var cachedResult: EngineAnalyticsResult? = null

    /**
     * Recomputes analytics ONLY if the user-scoped CacheKey has changed.
     * Runs strictly on Dispatchers.Default for CPU performance.
     */
    suspend fun computeAnalytics(
        userId: String,
        workoutHistory: List<WorkoutSessionAnalyticsData>,
        healthHistory: List<HealthMetricsData>,
        forceRecompute: Boolean = false
    ): EngineAnalyticsResult = withContext(Dispatchers.Default) {
        
        // 1. Sanitize Workouts & Health points using our validation and deduplication utilities
        val validatedWorkouts = workoutHistory
            .filter { it.isValid() }
            .map { session ->
                session.copy(
                    exercises = session.exercises
                        .filter { it.isValid() }
                        .deduplicateSets()
                )
            }
            .filter { it.exercises.isNotEmpty() }

        val validatedHealth = healthHistory.filter { it.dateMillis > 0 }

        val latestWorkoutTime = validatedWorkouts.maxOfOrNull { it.dateMillis } ?: 0L
        val latestHealthTime = validatedHealth.maxOfOrNull { it.dateMillis } ?: 0L

        val currentKey = CacheKey(
            userId = userId,
            latestWorkoutTimestamp = latestWorkoutTime,
            latestHealthTimestamp = latestHealthTime,
            workoutSize = validatedWorkouts.size,
            healthSize = validatedHealth.size
        )

        // Return user-scoped cached result if nothing has changed
        if (!forceRecompute && cachedResult != null && currentKey == lastCacheKey) {
            return@withContext cachedResult!!
        }

        // 2. Calculate Readiness
        val todayStart = com.trackme.utils.startOfLocalDayMillis(System.currentTimeMillis())
        val historicalHealth = validatedHealth.filter { it.dateMillis < todayStart }
        val todayHealth = validatedHealth.firstOrNull { it.dateMillis == todayStart }
        val readiness = readinessCalculator.calculate(historicalHealth, todayHealth)

        // 3. Flatten exercise history for performance modules
        val allExercises = validatedWorkouts.flatMap { it.exercises }

        // 4. Calculate 1RM Projections for major lifts
        val topExercises = allExercises
            .groupBy { it.exerciseId }
            .mapValues { it.value.first().name }
            .toList()
            .take(5) // Limit to top 5 for dashboard responsiveness

        val projections = topExercises.mapNotNull { (id, name) ->
            // Enforce sufficiency guard: 1RM calculation requires at least 3 distinct training days
            val exerciseHistory = allExercises.filter { it.exerciseId == id }
            val distinctDays = exerciseHistory.map { it.dateMillis / (24 * 60 * 60 * 1000L) }.distinct().size
            if (distinctDays >= 3) {
                oneRMEngine.calculateProjection(allExercises, id, name)
            } else {
                null
            }
        }

        // 5. Calculate Muscle Fatigue Heatmap
        val fatigueMap = fatigueCalculator.calculate(validatedWorkouts)

        // 6. Detect Plateaus
        val plateaus = plateauDetector.detectPlateaus(allExercises)

        // 7. Generate Consistency Matrix
        val matrixPosition = matrixGenerator.generate(validatedWorkouts)

        val result = EngineAnalyticsResult(
            readinessScore = readiness,
            oneRmProjections = projections,
            muscleFatigueMap = fatigueMap,
            plateauAlerts = plateaus,
            matrixPosition = matrixPosition
        )

        // Update Cache
        cachedResult = result
        lastCacheKey = currentKey

        result
    }
}
