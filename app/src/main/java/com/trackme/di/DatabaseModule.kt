package com.trackme.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.trackme.data.local.AppDatabase
import com.trackme.data.local.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "trackme_prefs")

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "trackme.db")
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
            )
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
    @Provides fun providePendingDeletionDao(db: AppDatabase): PendingDeletionDao = db.pendingDeletionDao()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> = context.dataStore
}
