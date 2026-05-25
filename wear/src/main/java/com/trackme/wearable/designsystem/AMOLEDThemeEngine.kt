package com.trackme.wearable.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.trackme.wearable.workout.WorkoutScreenPhase

@Immutable
data class AmoledTheme(
    val background: Color = WearTokens.Background,
    val accent: Color = WearTokens.Active,
    val stateTint: Color = Color.Transparent,
)

@Stable
object WearTokens {
    val Background = Color(0xFF000000)
    val Void = Color(0xFF0A0A0E)
    val Surface = Color(0xFF111118)
    val Elevated = Color(0xFF1A1A24)

    val Active = Color(0xFF34D399)
    val Rest = Color(0xFF60A5FA)
    val Summary = Color(0xFFA78BFA)
    val Warning = Color(0xFFFBBF24)
    val Signal = Color(0xFFFB7185)

    val TextPrimary = Color(0xFFF5F5F7)
    val TextSecondary = Color(0xFFAAAABC)
    val TextMuted = Color(0xFF6E6E82)

    val ConfirmTint = Color(0xFF071310)
    val RestTint = Color(0xFF00050F)
}

object AMOLEDThemeEngine {
    fun themeFor(phase: WorkoutScreenPhase?): AmoledTheme =
        when (phase) {
            WorkoutScreenPhase.LogConfirm -> AmoledTheme(
                background = WearTokens.Background,
                accent = WearTokens.Active,
                stateTint = WearTokens.ConfirmTint,
            )
            WorkoutScreenPhase.RestTimer -> AmoledTheme(
                background = WearTokens.Background,
                accent = WearTokens.Rest,
                stateTint = WearTokens.RestTint,
            )
            WorkoutScreenPhase.ExerciseSummary -> AmoledTheme(
                background = WearTokens.Background,
                accent = WearTokens.Summary,
            )
            WorkoutScreenPhase.WorkoutSummary -> AmoledTheme(
                background = WearTokens.Background,
                accent = WearTokens.Warning,
            )
            else -> AmoledTheme()
        }
}

@Composable
fun AmoledScreenBackground(
    phase: WorkoutScreenPhase?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val theme = AMOLEDThemeEngine.themeFor(phase)
    val tint by animateColorAsState(theme.stateTint, tween(300), label = "stateTint")
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .background(WearTokens.Background)
            .background(tint),
    ) {
        content()
    }
}
