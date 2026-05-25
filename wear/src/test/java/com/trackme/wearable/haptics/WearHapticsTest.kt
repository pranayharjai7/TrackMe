package com.trackme.wearable.haptics

import org.junit.Assert.assertTrue
import org.junit.Test

class WearHapticsTest {

    @Test
    fun `WearHaptics is a singleton object`() {
        // Verify both references are the same instance
        val a = WearHaptics
        val b = WearHaptics
        assertTrue("WearHaptics must be a singleton", a === b)
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
