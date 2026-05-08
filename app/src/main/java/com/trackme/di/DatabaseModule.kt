package com.trackme.di

import android.content.Context
import androidx.room.Room
import com.trackme.data.local.AppDatabase
import com.trackme.data.local.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "trackme.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideExerciseDao(db: AppDatabase): ExerciseDao = db.exerciseDao()
    @Provides fun provideWorkoutPlanDao(db: AppDatabase): WorkoutPlanDao = db.workoutPlanDao()
    @Provides fun provideWorkoutDayDao(db: AppDatabase): WorkoutDayDao = db.workoutDayDao()
    @Provides fun providePlannedExerciseDao(db: AppDatabase): PlannedExerciseDao = db.plannedExerciseDao()
    @Provides fun provideWorkoutSessionDao(db: AppDatabase): WorkoutSessionDao = db.workoutSessionDao()
    @Provides fun provideSessionSetDao(db: AppDatabase): SessionSetDao = db.sessionSetDao()
    @Provides fun providePersonalRecordDao(db: AppDatabase): PersonalRecordDao = db.personalRecordDao()
    @Provides fun provideHealthSnapshotDao(db: AppDatabase): HealthSnapshotDao = db.healthSnapshotDao()
}
