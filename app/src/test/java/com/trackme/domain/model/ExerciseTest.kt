package com.trackme.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseTest {
    @Test
    fun `exercise holds all fields correctly`() {
        val exercise = Exercise(
            id = "bench-press",
            name = "Bench Press",
            category = "Strength",
            primaryMuscles = listOf("chest"),
            secondaryMuscles = listOf("triceps", "shoulders"),
            equipment = "barbell",
            instructions = listOf("Lie on bench", "Lower bar to chest", "Press up"),
            gifUrl = "https://example.com/bench.gif",
            youtubeQuery = "bench press tutorial",
        )
        assertEquals("Bench Press", exercise.name)
        assertEquals(2, exercise.secondaryMuscles.size)
    }
}
