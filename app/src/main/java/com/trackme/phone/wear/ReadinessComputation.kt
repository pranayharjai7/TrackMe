package com.trackme.phone.wear

/**
 * Maps hours since the last completed workout to a watch-facing readiness score (35–95).
 * More recovery time yields a higher score; immediately post-workout is lowest.
 */
internal fun computeReadinessScore(lastCompletedWorkoutAt: Long?, nowMs: Long = System.currentTimeMillis()): Int {
    if (lastCompletedWorkoutAt == null) return 95
    val hoursSince = (nowMs - lastCompletedWorkoutAt).coerceAtLeast(0L) / 3_600_000.0
    val score = 35.0 + (hoursSince / 48.0) * 60.0
    return score.toInt().coerceIn(35, 95)
}
