package com.trackme.wearable.haptics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WearHapticsTest {

    @Test
    fun `all waveform arrays have matching timings and amplitudes lengths`() {
        // A length mismatch causes IllegalArgumentException at runtime in VibrationEffect.createWaveform
        assertEquals("setLogged arrays length", WearHaptics.SET_LOGGED_TIMINGS.size, WearHaptics.SET_LOGGED_AMPLITUDES.size)
        assertEquals("restStart arrays length", WearHaptics.REST_START_TIMINGS.size, WearHaptics.REST_START_AMPLITUDES.size)
        assertEquals("restEnd arrays length", WearHaptics.REST_END_TIMINGS.size, WearHaptics.REST_END_AMPLITUDES.size)
        assertEquals("exerciseSummary arrays length", WearHaptics.EXERCISE_SUMMARY_TIMINGS.size, WearHaptics.EXERCISE_SUMMARY_AMPLITUDES.size)
        assertEquals("workoutComplete arrays length", WearHaptics.WORKOUT_COMPLETE_TIMINGS.size, WearHaptics.WORKOUT_COMPLETE_AMPLITUDES.size)
        assertEquals("bezelStep arrays length", WearHaptics.BEZEL_STEP_TIMINGS.size, WearHaptics.BEZEL_STEP_AMPLITUDES.size)
        assertEquals("fieldToggle arrays length", WearHaptics.FIELD_TOGGLE_TIMINGS.size, WearHaptics.FIELD_TOGGLE_AMPLITUDES.size)
        assertEquals("warning arrays length", WearHaptics.WARNING_TIMINGS.size, WearHaptics.WARNING_AMPLITUDES.size)
    }

    @Test
    fun `WearHaptics has all 8 required methods`() {
        val methods = WearHaptics::class.java.methods.map { it.name }.toSet()
        assertTrue("setLogged method must exist", "setLogged" in methods)
        assertTrue("restStart method must exist", "restStart" in methods)
        assertTrue("restEnd method must exist", "restEnd" in methods)
        assertTrue("exerciseSummary method must exist", "exerciseSummary" in methods)
        assertTrue("workoutComplete method must exist", "workoutComplete" in methods)
        assertTrue("bezelStep method must exist", "bezelStep" in methods)
        assertTrue("fieldToggle method must exist", "fieldToggle" in methods)
        assertTrue("warning method must exist", "warning" in methods)
    }

    @Test
    fun `bezelStep waveform total duration is minimal (at most 60ms)`() {
        val duration = WearHaptics.BEZEL_STEP_TIMINGS.sum()
        assertTrue("bezelStep must stay under 60ms total, was $duration", duration <= 60L)
    }

    @Test
    fun `workoutComplete waveform total duration is at most 500ms`() {
        val duration = WearHaptics.WORKOUT_COMPLETE_TIMINGS.sum()
        assertTrue("workoutComplete must stay under 500ms total, was $duration", duration <= 500L)
    }

    @Test
    fun `setLogged waveform has correct structure (2 vibration stages)`() {
        // timings: [0=wait, on, off, on] → 4 elements, amplitudes same length
        val timings = WearHaptics.SET_LOGGED_TIMINGS
        val amplitudes = WearHaptics.SET_LOGGED_AMPLITUDES
        assertTrue("setLogged needs at least 4 timing entries", timings.size >= 4)
        assertTrue("setLogged timings and amplitudes must be same length",
            timings.size == amplitudes.size)
        assertTrue("setLogged first amplitude must be 0 (silence gap)", amplitudes[0] == 0)
    }

    @Test
    fun `all waveform amplitude arrays contain only values in range 0 to 255`() {
        val allAmplitudes = listOf(
            WearHaptics.SET_LOGGED_AMPLITUDES,
            WearHaptics.REST_START_AMPLITUDES,
            WearHaptics.REST_END_AMPLITUDES,
            WearHaptics.EXERCISE_SUMMARY_AMPLITUDES,
            WearHaptics.WORKOUT_COMPLETE_AMPLITUDES,
            WearHaptics.BEZEL_STEP_AMPLITUDES,
            WearHaptics.FIELD_TOGGLE_AMPLITUDES,
            WearHaptics.WARNING_AMPLITUDES,
        )
        for (amplitudes in allAmplitudes) {
            for (amp in amplitudes) {
                assertTrue("Amplitude $amp is out of range [0,255]", amp in 0..255)
            }
        }
    }
}
