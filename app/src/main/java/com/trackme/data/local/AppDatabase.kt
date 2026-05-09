package com.trackme.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
        PendingDeletionEntity::class,
    ],
    version = 3,
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
    abstract fun pendingDeletionDao(): PendingDeletionDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workout_plans ADD COLUMN deletedAt INTEGER")
                db.execSQL("ALTER TABLE workout_days ADD COLUMN deletedAt INTEGER")
                db.execSQL("ALTER TABLE planned_exercises ADD COLUMN deletedAt INTEGER")
                db.execSQL("ALTER TABLE workout_sessions ADD COLUMN deletedAt INTEGER")
                db.execSQL("ALTER TABLE session_sets ADD COLUMN deletedAt INTEGER")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS pending_deletions (" +
                    "entityId TEXT NOT NULL PRIMARY KEY, " +
                    "userId TEXT NOT NULL, " +
                    "tableName TEXT NOT NULL, " +
                    "deletedAt INTEGER NOT NULL)"
                )
            }
        }
    }
}
