package com.trackme.domain.analytics

import com.trackme.domain.analytics.models.HealthMetricsData
import com.trackme.domain.analytics.models.WorkoutSessionAnalyticsData
import com.trackme.domain.analytics.muscle.MuscleFatigueCalculator
import com.trackme.domain.analytics.performance.OneRMProjectionEngine
import com.trackme.domain.analytics.performance.PlateauDetector
import com.trackme.domain.analytics.recovery.ReadinessScoreCalculator
import com.trackme.domain.analytics.trends.ConsistencyMatrixGenerator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class ProgressAnalyticsEngineTest {

    private val readiness = ReadinessScoreCalculator()
    private val oneRM = OneRMProjectionEngine()
    private val fatigue = MuscleFatigueCalculator()
    private val plateau = PlateauDetector()
    private val matrix = ConsistencyMatrixGenerator()

    private val engine = ProgressAnalyticsEngine(readiness, oneRM, fatigue, plateau, matrix)

    @Test
    fun `caching scopes results strictly per user`() = runBlocking {
        val userA = "user_A"
        val userB = "user_B"

        val workouts = emptyList<WorkoutSessionAnalyticsData>()
        val health = List(10) { HealthMetricsData(100000L * (it + 1), 60f, 60, 480, 100) }

        // Compute for User A
        val resultA1 = engine.computeAnalytics(userA, workouts, health)
        val resultA2 = engine.computeAnalytics(userA, workouts, health)

        // Assert cached hit (exact same instance reference)
        assertSame(resultA1, resultA2)

        // Compute for User B
        val resultB = engine.computeAnalytics(userB, workouts, health)

        // Assert cache miss / strictly isolated sandbox
        assertNotSame(resultA1, resultB)
    }
}
