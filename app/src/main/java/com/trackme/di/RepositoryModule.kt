package com.trackme.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    // ExerciseRepository binding added in Task 9
    // WorkoutRepository binding added in Plan 2 Task 2
    // HealthRepository binding added in Plan 3 Task 2
}
