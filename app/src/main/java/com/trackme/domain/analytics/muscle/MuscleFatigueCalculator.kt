package com.trackme.domain.analytics.muscle

import com.trackme.domain.analytics.models.MuscleFatigue
import com.trackme.domain.analytics.models.WorkoutSessionAnalyticsData
import kotlin.math.max
import kotlin.math.pow

/**
 * Pure analytics module calculating current muscle fatigue.
 * Uses a decay algorithm where volume increases fatigue, and time decays it.
 */
class MuscleFatigueCalculator {

    companion object {
        const val MILLIS_PER_HOUR = 60 * 60 * 1000L
        
        // Dynamic recovery clear times per muscle group (in hours)
        private val MUSCLE_RECOVERY_HOURS = mapOf(
            "QUADS" to 84f,
            "GLUTES" to 84f,
            "CHEST" to 60f,
            "BACK" to 60f,
            "HAMSTRINGS" to 60f,
            "BICEPS" to 36f,
            "TRICEPS" to 36f,
            "SHOULDERS" to 36f,
            "CALVES" to 36f,
            "ABS" to 36f
        )
        
        private const val DEFAULT_RECOVERY_HOURS = 48f
    }

    fun calculate(
        recentSessions: List<WorkoutSessionAnalyticsData>,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): Map<String, MuscleFatigue> {
        val fatigueMap = mutableMapOf<String, Float>()

        // 1. Accumulate raw volume per muscle group across the recent time window.
        // We only care about sessions within the last 7 days max.
        val cutoffTime = currentTimeMillis - (7 * 24 * MILLIS_PER_HOUR)

        val relevantSessions = recentSessions.filter { it.dateMillis >= cutoffTime }.sortedBy { it.dateMillis }

        for (session in relevantSessions) {
            for (exercise in session.exercises) {
                // Safeguard against invalid reps/weight
                if (exercise.weightKg <= 0f || exercise.reps <= 0) continue

                // Approximate volume for this exercise: Weight * Reps
                val volume = exercise.weightKg * exercise.reps

                // Distribute volume evenly across target muscles
                if (exercise.targetMuscles.isNotEmpty()) {
                    val volumePerMuscle = volume / exercise.targetMuscles.size
                    for (muscle in exercise.targetMuscles) {
                        val uppercaseMuscle = muscle.uppercase()
                        val currentVolume = fatigueMap.getOrDefault(uppercaseMuscle, 0f)
                        fatigueMap[uppercaseMuscle] = currentVolume + volumePerMuscle
                    }
                }
            }
        }

        if (fatigueMap.isEmpty()) return emptyMap()

        // 2. We need a reference max volume to scale everything to 0-100%.
        val MAX_VOLUME_THRESHOLD = 5000f

        val result = mutableMapOf<String, MuscleFatigue>()

        for ((muscle, accumulatedVolume) in fatigueMap) {
            val clearHours = MUSCLE_RECOVERY_HOURS[muscle] ?: DEFAULT_RECOVERY_HOURS
            
            // Find the last time this muscle was hit to calculate decay
            val lastHitTime = findLastHitTime(muscle, relevantSessions)
            val hoursSinceHit = if (lastHitTime > 0) (currentTimeMillis - lastHitTime) / MILLIS_PER_HOUR.toFloat() else clearHours

            // Calculate base fatigue percentage
            val baseFatiguePercent = ((accumulatedVolume / MAX_VOLUME_THRESHOLD) * 100f).coerceAtMost(100f)

            // Apply exponential decay based on physiological half-life
            // halfLife = clearHours / 4. (So at clearHours elapsed, fatigue decays to (1/2)^4 = 6.25% of baseline)
            val halfLife = clearHours / 4f
            val decayFactor = if (halfLife > 0) {
                val power = hoursSinceHit / halfLife
                0.5f.pow(power)
            } else 0f
            
            val currentFatigue = (baseFatiguePercent * decayFactor).toInt().coerceIn(0, 100)

            val remainingHoursToClear = if (currentFatigue > 0) {
                (clearHours * (currentFatigue / 100f)).toInt().coerceAtLeast(1)
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
            if (session.exercises.any { it.targetMuscles.any { m -> m.uppercase() == muscle.uppercase() } }) {
                return session.dateMillis
            }
        }
        return 0L
    }
}
