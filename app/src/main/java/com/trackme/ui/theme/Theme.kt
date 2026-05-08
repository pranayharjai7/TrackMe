package com.trackme.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    background       = Background,
    surface          = Surface,
    surfaceVariant   = SurfaceVariant,
    primary          = Violet,
    secondary        = Blue,
    tertiary         = Teal,
    onBackground     = OnBackground,
    onSurface        = OnSurface,
    onPrimary        = Background,
    onSecondary      = Background,
    onTertiary       = Background,
)

@Composable
fun TrackMeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography  = TrackMeTypography,
        content     = content,
    )
}
