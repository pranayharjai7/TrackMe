package com.trackme.data.remote.dto

import kotlinx.serialization.json.Json
import org.junit.Assert.assertNull
import org.junit.Test

class PlannedExerciseDtoTest {

    @Test
    fun decodesLegacyNullTargetSets() {
        val dto = Json.decodeFromString<PlannedExerciseDto>(
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
}
