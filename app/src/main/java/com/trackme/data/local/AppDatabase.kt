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
        HealthMetricEntity::class,
        PendingDeletionEntity::class,
    ],
    version = 7,
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
    abstract fun healthMetricDao(): HealthMetricDao
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
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE planned_exercises ADD COLUMN targetSets INTEGER NOT NULL DEFAULT 3")
                db.execSQL("ALTER TABLE planned_exercises ADD COLUMN targetReps INTEGER")
                db.execSQL("ALTER TABLE planned_exercises ADD COLUMN targetWeightKg REAL")
                db.execSQL("ALTER TABLE planned_exercises ADD COLUMN targetDurationSeconds INTEGER")
                db.execSQL("ALTER TABLE planned_exercises ADD COLUMN targetDistanceKm REAL")
                db.execSQL("ALTER TABLE planned_exercises ADD COLUMN targetSpeedKmh REAL")
                db.execSQL("ALTER TABLE planned_exercises ADD COLUMN targetIncline REAL")
                db.execSQL("ALTER TABLE session_sets ADD COLUMN durationSeconds INTEGER")
                db.execSQL("ALTER TABLE session_sets ADD COLUMN distanceKm REAL")
                db.execSQL("ALTER TABLE session_sets ADD COLUMN speedKmh REAL")
                db.execSQL("ALTER TABLE session_sets ADD COLUMN inclinePercent REAL")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_exercises_name ON exercises(name)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_exercises_category ON exercises(category)")

                db.execSQL("CREATE INDEX IF NOT EXISTS idx_workout_plans_user ON workout_plans(userId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_workout_plans_active ON workout_plans(userId, isActive, deletedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_workout_plans_sync ON workout_plans(isSynced)")

                db.execSQL("CREATE INDEX IF NOT EXISTS idx_workout_days_user ON workout_days(userId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_workout_days_plan ON workout_days(planId, deletedAt, dayOfWeek)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_workout_days_sync ON workout_days(isSynced)")

                db.execSQL("CREATE INDEX IF NOT EXISTS idx_planned_exercises_user ON planned_exercises(userId, deletedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_planned_exercises_day ON planned_exercises(dayId, deletedAt, orderIndex)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_planned_exercises_exercise ON planned_exercises(exerciseId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_planned_exercises_sync ON planned_exercises(isSynced)")

                db.execSQL("CREATE INDEX IF NOT EXISTS idx_workout_sessions_user ON workout_sessions(userId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_workout_sessions_user_date ON workout_sessions(userId, date, deletedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_workout_sessions_in_progress ON workout_sessions(userId, dayId, date, durationMinutes, deletedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_workout_sessions_sync ON workout_sessions(isSynced)")

                db.execSQL("CREATE INDEX IF NOT EXISTS idx_session_sets_user ON session_sets(userId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_session_sets_session ON session_sets(sessionId, deletedAt, exerciseId, setNumber)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_session_sets_history ON session_sets(userId, exerciseId, deletedAt, updatedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_session_sets_since ON session_sets(userId, updatedAt, completed, deletedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_session_sets_sync ON session_sets(isSynced)")

                db.execSQL("CREATE INDEX IF NOT EXISTS idx_personal_records_user_date ON personal_records(userId, achievedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_personal_records_exercise ON personal_records(userId, exerciseId)")

                db.execSQL("CREATE INDEX IF NOT EXISTS idx_health_snapshots_user_date ON health_snapshots(userId, date)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_health_snapshots_sync ON health_snapshots(isSynced)")

                db.execSQL("CREATE INDEX IF NOT EXISTS idx_pending_deletions_user ON pending_deletions(userId)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS health_metrics (" +
                    "id TEXT NOT NULL PRIMARY KEY, " +
                    "userId TEXT NOT NULL, " +
                    "category TEXT NOT NULL, " +
                    "recordType TEXT NOT NULL, " +
                    "displayName TEXT NOT NULL, " +
                    "startTime INTEGER NOT NULL, " +
                    "endTime INTEGER, " +
                    "primaryValue TEXT NOT NULL, " +
                    "primaryUnit TEXT, " +
                    "details TEXT NOT NULL, " +
                    "sourceApp TEXT, " +
                    "rawData TEXT, " +
                    "updatedAt INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_health_metrics_user_time ON health_metrics(userId, startTime)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_health_metrics_user_category ON health_metrics(userId, category)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_health_metrics_record_type ON health_metrics(recordType)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE health_snapshots ADD COLUMN heartRateAvg INTEGER")
            }
        }
    }
}
