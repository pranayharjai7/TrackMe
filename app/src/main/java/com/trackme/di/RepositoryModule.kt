package com.trackme.di

import com.trackme.data.repository.ExerciseRepositoryImpl
import com.trackme.data.repository.HealthRepositoryImpl
import com.trackme.data.repository.WorkoutRepositoryImpl
import com.trackme.domain.repository.ExerciseRepository
import com.trackme.domain.repository.HealthRepository
import com.trackme.domain.repository.WorkoutRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt bindings for repository interfaces.
 *
 * Architecture Layer: Dependency injection
 *
 * Responsibilities:
 * - Bind domain repository contracts to data-layer implementations.
 * - Preserve dependency inversion so ViewModels/use cases depend on interfaces.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindExerciseRepository(impl: ExerciseRepositoryImpl): ExerciseRepository

    @Binds
    @Singleton
    abstract fun bindWorkoutRepository(impl: WorkoutRepositoryImpl): WorkoutRepository

    @Binds
    @Singleton
    abstract fun bindHealthRepository(impl: HealthRepositoryImpl): HealthRepository
}
