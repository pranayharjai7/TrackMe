package com.trackme.domain.analytics.utils

import com.trackme.domain.analytics.models.ExerciseAnalyticsData
import com.trackme.domain.analytics.utils.ValidationUtils.isValid
import com.trackme.domain.analytics.utils.ValidationUtils.deduplicateSets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationUtilsTest {

    @Test
    fun `exercise validation filters extreme values`() {
        val validExercise = ExerciseAnalyticsData("set_1", "bench", "Bench Press", 0L, emptyList(), 100f, 10, null)
        val negativeWeight = ExerciseAnalyticsData("set_2", "bench", "Bench Press", 0L, emptyList(), -5f, 10, null)
        val zeroReps = ExerciseAnalyticsData("set_3", "bench", "Bench Press", 0L, emptyList(), 100f, 0, null)
        val excessiveWeight = ExerciseAnalyticsData("set_4", "bench", "Bench Press", 0L, emptyList(), 1000f, 10, null)
        
        assertTrue(validExercise.isValid())
        assertFalse(negativeWeight.isValid())
        assertFalse(zeroReps.isValid())
        assertFalse(excessiveWeight.isValid())
    }

    @Test
    fun `deduplicateSets removes sync duplicates but preserves legitimate identical sets`() {
        val exercise1 = ExerciseAnalyticsData("set_1", "bench", "Bench Press", 0L, emptyList(), 100f, 10, null)
        val exercise1Duplicate = ExerciseAnalyticsData("set_1", "bench", "Bench Press", 1000L, emptyList(), 100f, 10, null)
        val exercise2LegitSet = ExerciseAnalyticsData("set_2", "bench", "Bench Press", 0L, emptyList(), 100f, 10, null)

        val list = listOf(exercise1, exercise1Duplicate, exercise2LegitSet)
        val deduplicated = list.deduplicateSets()

        assertEquals(2, deduplicated.size)
        assertTrue(deduplicated.contains(exercise1))
        assertTrue(deduplicated.contains(exercise2LegitSet))
    }
}
