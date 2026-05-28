package com.trackme.data.local

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationInstrumentedTest {

    private lateinit var context: Context
    private val dbName = "trackme-migration-test.db"

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
    }

    @After
    fun teardown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun migration9To10_recreatesDerivedAnalyticsTablesWithRoomCompatibleSchema() {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(9) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            createVersion9AnalyticsTables(db)
                        }

                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                    }
                )
                .build()
        )
        val db = helper.writableDatabase
        db.execSQL(
            "INSERT INTO muscle_weekly_analytics " +
                "(id, userId, muscleGroup, weekOffset, weeklyStimulus, growthIndex, fatigue, updatedAt, isSynced, deletedAt) " +
                "VALUES ('old-row', 'user-1', 'CHEST', 0, 10.0, 100.0, 20.0, 1, 0, NULL)"
        )

        AppDatabase.MIGRATION_9_10.migrate(db)

        assertEquals(
            listOf(
                "id",
                "userId",
                "muscleGroup",
                "weekOffset",
                "weeklyStimulus",
                "growthIndex",
                "fatigue",
                "updatedAt",
                "isSynced",
                "deletedAt",
            ),
            db.columnNames("muscle_weekly_analytics"),
        )
        assertNull(db.columnDefault("muscle_weekly_analytics", "isSynced"))
        assertTrue(db.indexNames("muscle_weekly_analytics").contains("index_muscle_weekly_analytics_userId"))
        assertTrue(db.indexNames("muscle_weekly_analytics").contains("index_muscle_weekly_analytics_isSynced"))
        assertEquals(0, db.scalarLong("SELECT COUNT(*) FROM muscle_weekly_analytics"))

        db.assertInsertableWithoutDefaults("muscle_weekly_analytics")
        db.assertInsertableWithoutDefaults("exercise_progress_snapshots")
        db.assertInsertableWithoutDefaults("daily_health_analytics")
        db.assertInsertableWithoutDefaults("body_state_snapshots")

        helper.close()
    }

    private fun createVersion9AnalyticsTables(db: SupportSQLiteDatabase) {
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

    private fun SupportSQLiteDatabase.assertInsertableWithoutDefaults(tableName: String) {
        val columns = columnNames(tableName)
        val values = columns.joinToString(", ") { column ->
            when (column) {
                "id" -> "'$tableName-id'"
                "userId" -> "'user-1'"
                "muscleGroup" -> "'CHEST'"
                "exerciseId" -> "'bench'"
                "exerciseName" -> "'Bench Press'"
                "deletedAt" -> "NULL"
                "isPlateaued", "isSynced", "weekOffset", "readinessScore", "steps" -> "0"
                else -> "1.0"
            }
        }
        execSQL("INSERT INTO $tableName (${columns.joinToString(", ")}) VALUES ($values)")
        assertEquals(1, scalarLong("SELECT COUNT(*) FROM $tableName WHERE id = '$tableName-id'"))
    }

    private fun SupportSQLiteDatabase.columnNames(tableName: String): List<String> =
        query("PRAGMA table_info($tableName)").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                }
            }
        }

    private fun SupportSQLiteDatabase.columnDefault(tableName: String, columnName: String): String? =
        query("PRAGMA table_info($tableName)").use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(cursor.getColumnIndexOrThrow("name")) == columnName) {
                    val index = cursor.getColumnIndexOrThrow("dflt_value")
                    return if (cursor.isNull(index)) null else cursor.getString(index)
                }
            }
            null
        }

    private fun SupportSQLiteDatabase.indexNames(tableName: String): List<String> =
        query("PRAGMA index_list($tableName)").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                }
            }
        }

    private fun SupportSQLiteDatabase.scalarLong(sql: String): Long =
        query(sql).use { cursor ->
            cursor.moveToFirst()
            cursor.getLong(0)
        }
}
