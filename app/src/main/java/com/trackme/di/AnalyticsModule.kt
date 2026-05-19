package com.trackme.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.trackme.data.local.dao.MuscleWeeklyAnalyticsDao
import com.trackme.data.local.dao.ExerciseProgressSnapshotDao
import com.trackme.data.local.dao.DailyHealthAnalyticsDao
import com.trackme.data.local.dao.BodyStateSnapshotDao
import com.trackme.domain.analytics.ProgressAnalyticsEngine
import com.trackme.domain.analytics.muscle.MuscleFatigueCalculator
import com.trackme.domain.analytics.performance.OneRMProjectionEngine
import com.trackme.domain.analytics.performance.PlateauDetector
import com.trackme.domain.analytics.recovery.ReadinessScoreCalculator
import com.trackme.domain.analytics.trends.ConsistencyMatrixGenerator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AnalyticsModule {

    @Provides
    @Singleton
    fun provideReadinessScoreCalculator() = ReadinessScoreCalculator()

    @Provides
    @Singleton
    fun provideOneRMProjectionEngine() = OneRMProjectionEngine()

    @Provides
    @Singleton
    fun provideMuscleFatigueCalculator() = MuscleFatigueCalculator()

    @Provides
    @Singleton
    fun providePlateauDetector() = PlateauDetector()

    @Provides
    @Singleton
    fun provideConsistencyMatrixGenerator() = ConsistencyMatrixGenerator()

    @Provides
    @Singleton
    fun provideProgressAnalyticsEngine(
        readinessCalculator: ReadinessScoreCalculator,
        oneRMEngine: OneRMProjectionEngine,
        fatigueCalculator: MuscleFatigueCalculator,
        plateauDetector: PlateauDetector,
        matrixGenerator: ConsistencyMatrixGenerator,
        exerciseRepository: com.trackme.domain.repository.ExerciseRepository,
        dataStore: DataStore<Preferences>,
        muscleWeeklyAnalyticsDao: MuscleWeeklyAnalyticsDao,
        exerciseProgressSnapshotDao: ExerciseProgressSnapshotDao,
        dailyHealthAnalyticsDao: DailyHealthAnalyticsDao,
        bodyStateSnapshotDao: BodyStateSnapshotDao
    ): ProgressAnalyticsEngine {
        return ProgressAnalyticsEngine(
            readinessCalculator,
            oneRMEngine,
            fatigueCalculator,
            plateauDetector,
            matrixGenerator,
            exerciseRepository,
            dataStore,
            muscleWeeklyAnalyticsDao,
            exerciseProgressSnapshotDao,
            dailyHealthAnalyticsDao,
            bodyStateSnapshotDao
        )
    }
}
