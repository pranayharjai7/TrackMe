package com.trackme.wearable.ui.motion

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import com.trackme.wearable.workout.WorkoutScreenPhase

object TransitionCoordinator {
    val pageSpring = spring<Float>(stiffness = 300f, dampingRatio = 0.82f)

    fun horizontalPageTransition(forward: Boolean): ContentTransform {
        val offset = { full: Int -> if (forward) full / 4 else -full / 4 }
        return slideInHorizontally(animationSpec = tween(220), initialOffsetX = offset) +
            fadeIn(tween(180)) togetherWith
            slideOutHorizontally(animationSpec = tween(220), targetOffsetX = { if (forward) -it / 6 else it / 6 }) +
            fadeOut(tween(160))
    }

    fun overlayDismiss(): ContentTransform =
        fadeIn(tween(150)) togetherWith fadeOut(tween(150))
}

fun AnimatedContentTransitionScope<WorkoutScreenPhase>.screenPhaseTransition(
    from: WorkoutScreenPhase,
    to: WorkoutScreenPhase,
): ContentTransform =
    when (to) {
        WorkoutScreenPhase.LogConfirm ->
            scaleIn(tween(150), initialScale = 0.95f) + fadeIn(tween(150)) togetherWith
                fadeOut(tween(120))
        WorkoutScreenPhase.RestTimer ->
            fadeIn(tween(300)) togetherWith fadeOut(tween(300))
        WorkoutScreenPhase.ExerciseSummary ->
            scaleIn(animationSpec = spring(stiffness = Spring.StiffnessMedium), initialScale = 0.9f) +
                fadeIn(tween(220)) togetherWith fadeOut(tween(180))
        else ->
            fadeIn(tween(220)) togetherWith fadeOut(tween(180))
    }
