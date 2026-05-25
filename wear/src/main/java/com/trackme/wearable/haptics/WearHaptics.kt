package com.trackme.wearable.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object WearHaptics {

    // ── Waveform constants (internal for testability) ──────────────────────

    internal val SET_LOGGED_TIMINGS      = longArrayOf(0, 30, 40, 80)
    internal val SET_LOGGED_AMPLITUDES   = intArrayOf(0, 180, 0, 255)

    internal val REST_START_TIMINGS      = longArrayOf(0, 25, 35, 25)
    internal val REST_START_AMPLITUDES   = intArrayOf(0, 120, 0, 120)

    internal val REST_END_TIMINGS        = longArrayOf(0, 60)
    internal val REST_END_AMPLITUDES     = intArrayOf(0, 200)

    internal val EXERCISE_SUMMARY_TIMINGS    = longArrayOf(0, 30, 30, 40, 30, 60)
    internal val EXERCISE_SUMMARY_AMPLITUDES = intArrayOf(0, 100, 0, 150, 0, 220)

    internal val WORKOUT_COMPLETE_TIMINGS    = longArrayOf(0, 60, 30, 80, 30, 100, 40, 120)
    internal val WORKOUT_COMPLETE_AMPLITUDES = intArrayOf(0, 120, 0, 160, 0, 200, 0, 255)

    internal val BEZEL_STEP_TIMINGS      = longArrayOf(0, 20)
    internal val BEZEL_STEP_AMPLITUDES   = intArrayOf(0, 60)

    internal val FIELD_TOGGLE_TIMINGS    = longArrayOf(0, 25, 25, 25)
    internal val FIELD_TOGGLE_AMPLITUDES = intArrayOf(0, 150, 0, 150)

    internal val WARNING_TIMINGS         = longArrayOf(0, 80, 30, 80)
    internal val WARNING_AMPLITUDES      = intArrayOf(0, 255, 0, 255)

    // ── Public API ─────────────────────────────────────────────────────────

    /** Short crisp tap — set confirmed and logged. */
    fun setLogged(context: Context) =
        vibrate(context, SET_LOGGED_TIMINGS, SET_LOGGED_AMPLITUDES)

    /** Two light taps — rest phase begins. */
    fun restStart(context: Context) =
        vibrate(context, REST_START_TIMINGS, REST_START_AMPLITUDES)

    /** Single medium pulse — go again. */
    fun restEnd(context: Context) =
        vibrate(context, REST_END_TIMINGS, REST_END_AMPLITUDES)

    /** Ascending triple tap — exercise complete. */
    fun exerciseSummary(context: Context) =
        vibrate(context, EXERCISE_SUMMARY_TIMINGS, EXERCISE_SUMMARY_AMPLITUDES)

    /** Long triumph rumble — workout done. */
    fun workoutComplete(context: Context) =
        vibrate(context, WORKOUT_COMPLETE_TIMINGS, WORKOUT_COMPLETE_AMPLITUDES)

    /** Minimal tick — single bezel step. */
    fun bezelStep(context: Context) =
        vibrate(context, BEZEL_STEP_TIMINGS, BEZEL_STEP_AMPLITUDES)

    /** Double-click feel — field switched. */
    fun fieldToggle(context: Context) =
        vibrate(context, FIELD_TOGGLE_TIMINGS, FIELD_TOGGLE_AMPLITUDES)

    /** Urgent buzz — something needs attention. */
    fun warning(context: Context) =
        vibrate(context, WARNING_TIMINGS, WARNING_AMPLITUDES)

    // ── Internal dispatcher ────────────────────────────────────────────────

    private fun vibrate(context: Context, timings: LongArray, amplitudes: IntArray) {
        val effect = VibrationEffect.createWaveform(timings, amplitudes, /* repeat= */ -1)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator?.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            @Suppress("DEPRECATION")
            vibrator?.vibrate(effect)
        }
    }
}
