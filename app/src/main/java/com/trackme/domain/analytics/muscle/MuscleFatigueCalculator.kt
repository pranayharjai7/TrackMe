package com.trackme.domain.analytics.muscle

import com.trackme.domain.analytics.models.MuscleFatigue
import com.trackme.domain.analytics.models.WorkoutSessionAnalyticsData
import kotlin.math.max

/**
 * Pure analytics module calculating current muscle fatigue.
 * Uses a decay algorithm where volume increases fatigue, and time decays it.
 */
class MuscleFatigueCalculator {

    companion object {
        const val MILLIS_PER_HOUR = 60 * 60 * 1000L
        // Assuming 100% fatigue takes ~72 hours to fully clear on average.
        // Fatigue drops by a certain percentage every hour.
        const val FATIGUE_CLEAR_HOURS = 72f 
    }

    fun calculate(
        recentSessions: List<WorkoutSessionAnalyticsData>,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): Map<String, MuscleFatigue> {
        val fatigueMap = mutableMapOf<String, Float>()

        // 1. Accumulate raw volume per muscle group across the recent time window.
        // We only care about sessions within the last 7 days max, since fatigue clears in ~3 days anyway.
        val cutoffTime = currentTimeMillis - (7 * 24 * MILLIS_PER_HOUR)

        val relevantSessions = recentSessions.filter { it.dateMillis >= cutoffTime }.sortedBy { it.dateMillis }

        for (session in relevantSessions) {
            for (exercise in session.exercises) {
                // Approximate volume for this exercise: Weight * Reps
                val volume = exercise.weightKg * exercise.reps

                // Distribute volume evenly across target muscles
                if (exercise.targetMuscles.isNotEmpty()) {
                    val volumePerMuscle = volume / exercise.targetMuscles.size
                    for (muscle in exercise.targetMuscles) {
                        val currentVolume = fatigueMap.getOrDefault(muscle, 0f)
                        fatigueMap[muscle] = currentVolume + volumePerMuscle
                    }
                }
            }
        }

        if (fatigueMap.isEmpty()) return emptyMap()

        // 2. We need a reference max volume to scale everything to 0-100%.
        // For simplicity, let's assume 5000kg of volume on a single muscle group equals 100% fatigue.
        // (In a real system, this max threshold would be personalized to the user's historical capacity).
        val MAX_VOLUME_THRESHOLD = 5000f

        val result = mutableMapOf<String, MuscleFatigue>()

        for ((muscle, accumulatedVolume) in fatigueMap) {
            // Find the last time this muscle was hit to calculate decay
            val lastHitTime = findLastHitTime(muscle, relevantSessions)
            val hoursSinceHit = if (lastHitTime > 0) (currentTimeMillis - lastHitTime) / MILLIS_PER_HOUR.toFloat() else FATIGUE_CLEAR_HOURS

            // Calculate base fatigue percentage
            var baseFatiguePercent = (accumulatedVolume / MAX_VOLUME_THRESHOLD) * 100f
            baseFatiguePercent = baseFatiguePercent.coerceAtMost(100f)

            // Apply linear decay based on hours elapsed
            // If hoursSinceHit >= 72, fatigue is 0.
            val decayFactor = max(0f, (FATIGUE_CLEAR_HOURS - hoursSinceHit) / FATIGUE_CLEAR_HOURS)
            val currentFatigue = (baseFatiguePercent * decayFactor).toInt().coerceIn(0, 100)

            val remainingHoursToClear = if (currentFatigue > 0) {
                (FATIGUE_CLEAR_HOURS * (currentFatigue / 100f)).toInt()
            } else 0

            result[muscle] = MuscleFatigue(
                muscleGroup = muscle,
                fatiguePercentage = currentFatigue,
                recoveryTimeRemainingHours = remainingHoursToClear
            )
        }

        return result
    }

    private fun findLastHitTime(muscle: String, sessions: List<WorkoutSessionAnalyticsData>): Long {
        for (session in sessions.reversed()) {
            if (session.exercises.any { it.targetMuscles.contains(muscle) }) {
                return session.dateMillis
            }
        }
        return 0L
    }
}
