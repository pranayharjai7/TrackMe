package com.trackme.wearable.designsystem

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

object WearTypography {
    val Display = TextStyle(
        fontSize = 28.sp,
        fontWeight = FontWeight.ExtraBold,
        color = WearTokens.TextPrimary,
        fontFamily = FontFamily.SansSerif,
        textAlign = TextAlign.Center,
    )

    val DisplayRest = Display.copy(
        fontSize = 26.sp,
        color = WearTokens.Rest,
    )

    val DisplayHr = Display.copy(color = WearTokens.Signal)

    val Body = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = WearTokens.TextPrimary,
        textAlign = TextAlign.Center,
    )

    val BodyAccent = Body.copy(color = WearTokens.Active)

    val Label = TextStyle(
        fontSize = 8.sp,
        fontWeight = FontWeight.Bold,
        color = WearTokens.TextMuted,
        letterSpacing = 1.2.sp,
        textAlign = TextAlign.Center,
    )

    val LabelAction = Label.copy(
        color = WearTokens.Active,
        fontSize = 8.sp,
    )

    val LabelHint = Label.copy(fontSize = 7.sp)

    val ExerciseName = TextStyle(
        fontSize = 8.sp,
        fontWeight = FontWeight.Medium,
        color = WearTokens.TextMuted,
        letterSpacing = 0.8.sp,
        textAlign = TextAlign.Center,
    )

    val Unit = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        color = WearTokens.TextSecondary,
    )

    val Ambient = TextStyle(
        fontSize = 7.sp,
        fontWeight = FontWeight.Medium,
        color = WearTokens.TextMuted,
    )
}
