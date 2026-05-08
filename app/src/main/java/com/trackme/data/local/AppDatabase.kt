package com.trackme.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.trackme.data.local.dao.*
import com.trackme.data.local.entity.*

@Database(
    entities = [
        ExerciseEntity::class,
        WorkoutPlanEntity::class,
        WorkoutDayEntity::class,
        PlannedExerciseEntity::class,
        WorkoutSessionEntity::class,
        SessionSetEntity::class,
        PersonalRecordEntity::class,
        HealthSnapshotEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutPlanDao(): WorkoutPlanDao
    abstract fun workoutDayDao(): WorkoutDayDao
    abstract fun plannedExerciseDao(): PlannedExerciseDao
    abstract fun workoutSessionDao(): WorkoutSessionDao
    abstract fun sessionSetDao(): SessionSetDao
    abstract fun personalRecordDao(): PersonalRecordDao
    abstract fun healthSnapshotDao(): HealthSnapshotDao
}
