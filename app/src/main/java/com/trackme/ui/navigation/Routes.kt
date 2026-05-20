package com.trackme.ui.navigation

sealed class Routes(val route: String) {
    object Splash : Routes("splash")
    object Auth : Routes("auth")
    object Onboarding : Routes("onboarding")
    object Home : Routes("home")
    object WeeklyPlanner : Routes("weekly_planner")
    object DayEditor : Routes("day_editor/{dayId}") {
        fun createRoute(dayId: String) = "day_editor/$dayId"
    }
    object ExerciseSearch : Routes("exercise_search?dayId={dayId}") {
        fun createRoute(dayId: String) = "exercise_search?dayId=$dayId"
    }
    object ExerciseDetail : Routes("exercise_detail/{exerciseId}") {
        fun createRoute(exerciseId: String) = "exercise_detail/$exerciseId"
    }
    object ActiveSession : Routes("active_session/{dayId}?dateMillis={dateMillis}") {
        fun createRoute(dayId: String, dateMillis: Long? = null) = if (dateMillis != null) "active_session/$dayId?dateMillis=$dateMillis" else "active_session/$dayId"
    }
    object Progress : Routes("progress")
    object Profile : Routes("profile")
    object HealthMetrics : Routes("health_metrics")
}
