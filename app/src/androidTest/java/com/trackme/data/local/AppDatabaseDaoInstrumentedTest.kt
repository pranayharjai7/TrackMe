package com.trackme.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.trackme.data.local.entity.ExerciseEntity
import com.trackme.data.local.entity.HealthMetricEntity
import com.trackme.data.local.entity.PlannedExerciseEntity
import com.trackme.data.local.entity.SessionSetEntity
import com.trackme.data.local.entity.WorkoutDayEntity
import com.trackme.data.local.entity.WorkoutPlanEntity
import com.trackme.data.local.entity.WorkoutSessionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseDaoInstrumentedTest {

    private lateinit var db: AppDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun exerciseDao_searchesByNameAndMuscleAndPreservesJsonLists() = runTest {
        db.exerciseDao().insertAll(
            listOf(
                ExerciseEntity(
                    id = "bench",
                    name = "Bench Press",
                    category = "Strength",
                    primaryMuscles = """["Chest"]""",
                    secondaryMuscles = """["Triceps","Front Delts"]""",
                    equipment = "Barbell",
                    instructions = """["Brace","Press"]""",
                    gifUrl = "https://example.com/bench.gif",
                    youtubeQuery = "bench press",
                ),
                ExerciseEntity(
                    id = "row",
                    name = "Barbell Row",
                    category = "Strength",
                    primaryMuscles = """["Back"]""",
                    secondaryMuscles = """["Biceps"]""",
                    equipment = "Barbell",
                    instructions = """["Hinge","Pull"]""",
                    gifUrl = "",
                    youtubeQuery = "barbell row",
                ),
            )
        )

        val chestResults = db.exerciseDao().search("Chest", limit = 10).first()
        val row = db.exerciseDao().getById("row")!!.toDomain()

        assertEquals(listOf("bench"), chestResults.map { it.id })
        assertEquals(listOf("Back"), row.primaryMuscles)
        assertEquals(listOf("Hinge", "Pull"), row.instructions)
        assertEquals(2, db.exerciseDao().count())
    }

    @Test
    fun workoutDaos_hideSoftDeletedRowsButKeepTombstonesForSync() = runTest {
        val plan = WorkoutPlanEntity(
            id = "plan-1",
            userId = "user-1",
            name = "Push Pull",
            isActive = true,
            createdAt = 1L,
            updatedAt = 1L,
            isSynced = true,
        )
        val day = WorkoutDayEntity(
            id = "day-1",
            planId = plan.id,
            userId = plan.userId,
            dayOfWeek = "MONDAY",
            name = "Push",
            updatedAt = 2L,
            isSynced = true,
        )
        val planned = PlannedExerciseEntity(
            id = "planned-1",
            dayId = day.id,
            userId = plan.userId,
            exerciseId = "bench",
            orderIndex = 0,
            updatedAt = 3L,
            isSynced = true,
            targetSets = 4,
            targetReps = 8,
            targetWeightKg = 80f,
        )

        db.workoutPlanDao().insert(plan)
        db.workoutDayDao().insert(day)
        db.plannedExerciseDao().insert(planned)

        db.workoutDayDao().softDelete(day.id, ts = 99L)
        db.plannedExerciseDao().softDelete(planned.id, ts = 100L)

        assertTrue(db.workoutDayDao().getDaysForPlan(plan.id).first().isEmpty())
        assertTrue(db.plannedExerciseDao().getForDay(day.id).first().isEmpty())

        val dayTombstone = db.workoutDayDao().getAllForSync(plan.userId).single()
        val plannedTombstone = db.plannedExerciseDao().getAllForSync(plan.userId).single()
        assertEquals(99L, dayTombstone.deletedAt)
        assertEquals(100L, plannedTombstone.deletedAt)
        assertEquals(false, dayTombstone.isSynced)
        assertEquals(false, plannedTombstone.isSynced)
    }

    @Test
    fun sessionDaos_returnInProgressAndFilterDeletedOrIncompleteSets() = runTest {
        val todayStart = 10_000L
        val session = WorkoutSessionEntity(
            id = "session-1",
            userId = "user-1",
            dayId = "day-1",
            date = todayStart + 123L,
            durationMinutes = 0,
            notes = "",
            updatedAt = 20L,
        )
        val olderCompletedSession = session.copy(
            id = "session-older",
            date = todayStart - 1L,
            durationMinutes = 45,
        )
        db.workoutSessionDao().insertAll(listOf(session, olderCompletedSession))
        db.sessionSetDao().insertAll(
            listOf(
                SessionSetEntity(
                    id = "set-1",
                    sessionId = session.id,
                    userId = session.userId,
                    exerciseId = "bench",
                    setNumber = 1,
                    weightKg = 80f,
                    reps = 8,
                    completed = true,
                    updatedAt = 30L,
                ),
                SessionSetEntity(
                    id = "set-2",
                    sessionId = session.id,
                    userId = session.userId,
                    exerciseId = "bench",
                    setNumber = 2,
                    weightKg = 82.5f,
                    reps = 8,
                    completed = false,
                    updatedAt = 31L,
                ),
            )
        )
        db.sessionSetDao().softDelete("set-2", ts = 40L)

        assertEquals(session.id, db.workoutSessionDao().getInProgressSession("user-1", todayStart)?.id)
        assertNull(db.workoutSessionDao().getInProgressSession("user-2", todayStart))
        assertEquals(listOf("set-1"), db.sessionSetDao().getSetsSince("user-1", fromDate = 1L).first().map { it.id })
        assertEquals(80f, db.sessionSetDao().getMaxWeightForExercise("user-1", "bench") ?: 0f, 0.001f)
    }

    @Test
    fun healthMetricDao_replaceForUserIsAtomicPerUserAndOrdersNewestFirst() = runTest {
        val userOneOld = healthMetric(id = "old", userId = "user-1", startTime = 1L)
        val userTwoMetric = healthMetric(id = "other", userId = "user-2", startTime = 5L)
        db.healthMetricDao().insertAll(listOf(userOneOld, userTwoMetric))

        db.healthMetricDao().replaceForUser(
            userId = "user-1",
            metrics = listOf(
                healthMetric(id = "newer", userId = "user-1", startTime = 10L),
                healthMetric(id = "new", userId = "user-1", startTime = 8L),
            ),
        )

        assertEquals(listOf("newer", "new"), db.healthMetricDao().getAllForUser("user-1").first().map { it.id })
        assertEquals(listOf("other"), db.healthMetricDao().getAllForUser("user-2").first().map { it.id })
    }

    private fun healthMetric(id: String, userId: String, startTime: Long) = HealthMetricEntity(
        id = id,
        userId = userId,
        category = "Vitals",
        recordType = "HeartRateRecord",
        displayName = "Heart rate",
        startTime = startTime,
        endTime = null,
        primaryValue = "60",
        primaryUnit = "bpm",
        details = "Average: 60 bpm",
        sourceApp = "com.example",
        rawData = "{}",
        updatedAt = startTime,
    )
}
