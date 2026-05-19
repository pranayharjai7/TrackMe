package com.trackme.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.trackme.data.local.dao.*
import com.trackme.data.local.entity.*

/**
 * Room database for all local TrackMe persistence.
 *
 * Architecture Layer: Data/local database
 *
 * Responsibilities:
 * - Declare the Room schema and DAO access points.
 * - Preserve migration history for existing installs.
 * - Keep local table structure aligned with sync entities and Supabase DTOs.
 */
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
        MuscleWeeklyAnalyticsEntity::class,
        ExerciseProgressSnapshotEntity::class,
        DailyHealthAnalyticsEntity::class,
        BodyStateSnapshotEntity::class,
    ],
    version = 10,
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
    abstract fun muscleWeeklyAnalyticsDao(): MuscleWeeklyAnalyticsDao
    abstract fun exerciseProgressSnapshotDao(): ExerciseProgressSnapshotDao
    abstract fun dailyHealthAnalyticsDao(): DailyHealthAnalyticsDao
    abstract fun bodyStateSnapshotDao(): BodyStateSnapshotDao

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

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE health_snapshots ADD COLUMN hrvRmssd REAL")
                db.execSQL("ALTER TABLE health_snapshots ADD COLUMN restingHeartRate INTEGER")
                db.execSQL("ALTER TABLE health_snapshots ADD COLUMN sleepDurationMinutes INTEGER")
                db.execSQL("ALTER TABLE health_snapshots ADD COLUMN deepSleepMinutes INTEGER")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS muscle_weekly_analytics (" +
                    "id TEXT NOT NULL PRIMARY KEY, " +
                    "userId TEXT NOT NULL, " +
                    "muscleGroup TEXT NOT NULL, " +
                    "weekOffset INTEGER NOT NULL, " +
                    "weeklyStimulus REAL NOT NULL, " +
                    "growthIndex REAL NOT NULL, " +
                    "fatigue REAL NOT NULL, " +
                    "updatedAt INTEGER NOT NULL, " +
                    "isSynced INTEGER NOT NULL DEFAULT 0, " +
                    "deletedAt INTEGER)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_muscle_weekly_analytics_user ON muscle_weekly_analytics(userId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_muscle_weekly_analytics_sync ON muscle_weekly_analytics(isSynced)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS exercise_progress_snapshots (" +
                    "id TEXT NOT NULL PRIMARY KEY, " +
                    "userId TEXT NOT NULL, " +
                    "exerciseId TEXT NOT NULL, " +
                    "exerciseName TEXT NOT NULL, " +
                    "current1RM REAL NOT NULL, " +
                    "projected1RM30Days REAL NOT NULL, " +
                    "projected1RM90Days REAL NOT NULL, " +
                    "projected1RM365Days REAL NOT NULL, " +
                    "isPlateaued INTEGER NOT NULL, " +
                    "improvementPercentage REAL NOT NULL, " +
                    "updatedAt INTEGER NOT NULL, " +
                    "isSynced INTEGER NOT NULL DEFAULT 0, " +
                    "deletedAt INTEGER)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_exercise_progress_snapshots_user ON exercise_progress_snapshots(userId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_exercise_progress_snapshots_sync ON exercise_progress_snapshots(isSynced)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS daily_health_analytics (" +
                    "id TEXT NOT NULL PRIMARY KEY, " +
                    "userId TEXT NOT NULL, " +
                    "dateMillis INTEGER NOT NULL, " +
                    "bmr REAL NOT NULL, " +
                    "caloriesSteps REAL NOT NULL, " +
                    "caloriesActive REAL NOT NULL, " +
                    "caloriesLifting REAL NOT NULL, " +
                    "caloriesTDEE REAL NOT NULL, " +
                    "steps INTEGER NOT NULL, " +
                    "weightKg REAL NOT NULL, " +
                    "updatedAt INTEGER NOT NULL, " +
                    "isSynced INTEGER NOT NULL DEFAULT 0, " +
                    "deletedAt INTEGER)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_daily_health_analytics_user ON daily_health_analytics(userId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_daily_health_analytics_sync ON daily_health_analytics(isSynced)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS body_state_snapshots (" +
                    "id TEXT NOT NULL PRIMARY KEY, " +
                    "userId TEXT NOT NULL, " +
                    "consistencyScore REAL NOT NULL, " +
                    "readinessScore INTEGER NOT NULL, " +
                    "muscleBalancePushPull REAL NOT NULL, " +
                    "muscleBalanceQuadHam REAL NOT NULL, " +
                    "muscleBalanceUpperLower REAL NOT NULL, " +
                    "updatedAt INTEGER NOT NULL, " +
                    "isSynced INTEGER NOT NULL DEFAULT 0, " +
                    "deletedAt INTEGER)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_body_state_snapshots_user ON body_state_snapshots(userId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_body_state_snapshots_sync ON body_state_snapshots(isSynced)")
            }
        }

        /**
         * MIGRATION_9_10
         *
         * Fixes schema mismatches on older devices that ran MIGRATION_8_9.
         * All four tables created in that migration share two bugs:
         *   1. Custom index names (idx_...) instead of Room's auto-generated
         *      format (index_<tableName>_<column>)
         *   2. isSynced declared with DEFAULT 0 in SQL — Room expects no SQL
         *      default (it manages Kotlin defaults in code)
         *
         * All four tables are analytics/cache tables derived from workout
         * history, so dropping and recreating them causes no data loss.
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {

                // ── muscle_weekly_analytics ──────────────────────────────
                db.execSQL("DROP TABLE IF EXISTS muscle_weekly_analytics")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS muscle_weekly_analytics (" +
                    "id TEXT NOT NULL PRIMARY KEY, " +
                    "userId TEXT NOT NULL, " +
                    "muscleGroup TEXT NOT NULL, " +
                    "weekOffset INTEGER NOT NULL, " +
                    "weeklyStimulus REAL NOT NULL, " +
                    "growthIndex REAL NOT NULL, " +
                    "fatigue REAL NOT NULL, " +
                    "updatedAt INTEGER NOT NULL, " +
                    "isSynced INTEGER NOT NULL, " +
                    "deletedAt INTEGER)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_muscle_weekly_analytics_userId` ON muscle_weekly_analytics(userId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_muscle_weekly_analytics_isSynced` ON muscle_weekly_analytics(isSynced)")

                // ── exercise_progress_snapshots ──────────────────────────
                db.execSQL("DROP TABLE IF EXISTS exercise_progress_snapshots")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS exercise_progress_snapshots (" +
                    "id TEXT NOT NULL PRIMARY KEY, " +
                    "userId TEXT NOT NULL, " +
                    "exerciseId TEXT NOT NULL, " +
                    "exerciseName TEXT NOT NULL, " +
                    "current1RM REAL NOT NULL, " +
                    "projected1RM30Days REAL NOT NULL, " +
                    "projected1RM90Days REAL NOT NULL, " +
                    "projected1RM365Days REAL NOT NULL, " +
                    "isPlateaued INTEGER NOT NULL, " +
                    "improvementPercentage REAL NOT NULL, " +
                    "updatedAt INTEGER NOT NULL, " +
                    "isSynced INTEGER NOT NULL, " +
                    "deletedAt INTEGER)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_exercise_progress_snapshots_userId` ON exercise_progress_snapshots(userId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_exercise_progress_snapshots_isSynced` ON exercise_progress_snapshots(isSynced)")

                // ── daily_health_analytics ───────────────────────────────
                db.execSQL("DROP TABLE IF EXISTS daily_health_analytics")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS daily_health_analytics (" +
                    "id TEXT NOT NULL PRIMARY KEY, " +
                    "userId TEXT NOT NULL, " +
                    "dateMillis INTEGER NOT NULL, " +
                    "bmr REAL NOT NULL, " +
                    "caloriesSteps REAL NOT NULL, " +
                    "caloriesActive REAL NOT NULL, " +
                    "caloriesLifting REAL NOT NULL, " +
                    "caloriesTDEE REAL NOT NULL, " +
                    "steps INTEGER NOT NULL, " +
                    "weightKg REAL NOT NULL, " +
                    "updatedAt INTEGER NOT NULL, " +
                    "isSynced INTEGER NOT NULL, " +
                    "deletedAt INTEGER)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_health_analytics_userId` ON daily_health_analytics(userId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_health_analytics_isSynced` ON daily_health_analytics(isSynced)")

                // ── body_state_snapshots ─────────────────────────────────
                db.execSQL("DROP TABLE IF EXISTS body_state_snapshots")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS body_state_snapshots (" +
                    "id TEXT NOT NULL PRIMARY KEY, " +
                    "userId TEXT NOT NULL, " +
                    "consistencyScore REAL NOT NULL, " +
                    "readinessScore INTEGER NOT NULL, " +
                    "muscleBalancePushPull REAL NOT NULL, " +
                    "muscleBalanceQuadHam REAL NOT NULL, " +
                    "muscleBalanceUpperLower REAL NOT NULL, " +
                    "updatedAt INTEGER NOT NULL, " +
                    "isSynced INTEGER NOT NULL, " +
                    "deletedAt INTEGER)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_body_state_snapshots_userId` ON body_state_snapshots(userId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_body_state_snapshots_isSynced` ON body_state_snapshots(isSynced)")
            }
        }
    }
}
