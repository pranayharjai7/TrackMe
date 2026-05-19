package com.trackme.domain.analytics

import com.trackme.domain.analytics.models.HealthMetricsData
import com.trackme.domain.analytics.models.WorkoutSessionAnalyticsData
import com.trackme.domain.analytics.muscle.MuscleFatigueCalculator
import com.trackme.domain.analytics.performance.OneRMProjectionEngine
import com.trackme.domain.analytics.performance.PlateauDetector
import com.trackme.domain.analytics.recovery.ReadinessScoreCalculator
import com.trackme.domain.analytics.trends.ConsistencyMatrixGenerator
import io.mockk.mockk
import com.trackme.domain.repository.ExerciseRepository
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.trackme.data.local.dao.MuscleWeeklyAnalyticsDao
import com.trackme.data.local.dao.ExerciseProgressSnapshotDao
import com.trackme.data.local.dao.DailyHealthAnalyticsDao
import com.trackme.data.local.dao.BodyStateSnapshotDao
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
    private val exerciseRepository = mockk<ExerciseRepository>(relaxed = true)
    private val dataStore = mockk<DataStore<Preferences>>(relaxed = true)
    private val muscleWeeklyAnalyticsDao = mockk<MuscleWeeklyAnalyticsDao>(relaxed = true)
    private val exerciseProgressSnapshotDao = mockk<ExerciseProgressSnapshotDao>(relaxed = true)
    private val dailyHealthAnalyticsDao = mockk<DailyHealthAnalyticsDao>(relaxed = true)
    private val bodyStateSnapshotDao = mockk<BodyStateSnapshotDao>(relaxed = true)

    private val engine = ProgressAnalyticsEngine(
        readiness, oneRM, fatigue, plateau, matrix,
        exerciseRepository, dataStore,
        muscleWeeklyAnalyticsDao, exerciseProgressSnapshotDao, dailyHealthAnalyticsDao, bodyStateSnapshotDao
    )

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
