package com.trackme.domain.model

enum class LoggingType(val displayName: String) {
    WEIGHTED_REPS("Weighted · Sets · Reps"),
    BODYWEIGHT_REPS("Bodyweight · Sets · Reps"),
    TIMED("Timed · Sets · Duration"),
    CARDIO("Cardio · Duration · Speed"),
}

fun Exercise.loggingType(): LoggingType = when {
    category.equals("Cardio", ignoreCase = true)      -> LoggingType.CARDIO
    category.equals("Stretching", ignoreCase = true)  -> LoggingType.TIMED
    category.equals("Plyometrics", ignoreCase = true) -> LoggingType.BODYWEIGHT_REPS
    equipment.equals("body only", ignoreCase = true)  -> LoggingType.BODYWEIGHT_REPS
    else                                               -> LoggingType.WEIGHTED_REPS
}
