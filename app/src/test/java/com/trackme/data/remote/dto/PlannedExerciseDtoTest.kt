package com.trackme.data.remote.dto

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlannedExerciseDtoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun decodesLegacyNullTargetSets() {
        val dto = json.decodeFromString<PlannedExerciseDto>(
            """
            {
              "id": "planned-1",
              "day_id": "day-1",
              "user_id": "user-1",
              "exercise_id": "exercise-1",
              "order_index": 0,
              "updated_at": 123,
              "deleted_at": 123,
              "target_sets": null,
              "target_reps": null
            }
            """.trimIndent()
        )

        assertNull(dto.targetSets)
    }

    @Test
    fun `decodes legacy rows missing all optional target fields`() {
        val dto = json.decodeFromString<PlannedExerciseDto>(
            """
            {
              "id": "planned-1",
              "day_id": "day-1",
              "user_id": "user-1",
              "exercise_id": "exercise-1",
              "order_index": 2,
              "updated_at": 123
            }
            """.trimIndent()
        )

        assertEquals("day-1", dto.dayId)
        assertNull(dto.deletedAt)
        assertNull(dto.targetSets)
        assertNull(dto.targetReps)
        assertNull(dto.targetWeightKg)
        assertNull(dto.targetDurationSeconds)
        assertNull(dto.targetDistanceKm)
        assertNull(dto.targetSpeedKmh)
        assertNull(dto.targetIncline)
    }

    @Test
    fun `serializes Supabase column names for all target variants`() {
        val encoded = json.encodeToString(
            PlannedExerciseDto.serializer(),
            PlannedExerciseDto(
                id = "planned-1",
                dayId = "day-1",
                userId = "user-1",
                exerciseId = "exercise-1",
                orderIndex = 1,
                updatedAt = 456L,
                targetSets = 3,
                targetReps = 8,
                targetWeightKg = 72.5f,
                targetDurationSeconds = 120,
                targetDistanceKm = 1.5f,
                targetSpeedKmh = 11.2f,
                targetIncline = 2.5f,
            )
        )

        assertTrue(encoded.contains(""""day_id":"day-1""""))
        assertTrue(encoded.contains(""""user_id":"user-1""""))
        assertTrue(encoded.contains(""""exercise_id":"exercise-1""""))
        assertTrue(encoded.contains(""""order_index":1"""))
        assertTrue(encoded.contains(""""target_sets":3"""))
        assertTrue(encoded.contains(""""target_reps":8"""))
        assertTrue(encoded.contains(""""target_weight_kg":72.5"""))
        assertTrue(encoded.contains(""""target_duration_seconds":120"""))
        assertTrue(encoded.contains(""""target_distance_km":1.5"""))
        assertTrue(encoded.contains(""""target_speed_kmh":11.2"""))
        assertTrue(encoded.contains(""""target_incline":2.5"""))
        assertFalse(encoded.contains("dayId"))
        assertFalse(encoded.contains("targetWeightKg"))
    }
}
