package com.trackme.domain.model

data class UserPrefs(
    val userId: String,
    val displayName: String,
    val useKg: Boolean,
    val fitnessGoal: FitnessGoal,
    val onboardingComplete: Boolean,
)

enum class FitnessGoal { LOSE_WEIGHT, BUILD_MUSCLE, MAINTAIN, IMPROVE_FITNESS }
