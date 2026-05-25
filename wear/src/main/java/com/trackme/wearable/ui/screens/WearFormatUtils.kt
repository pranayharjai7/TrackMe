package com.trackme.wearable.ui.screens

internal fun readinessLabel(score: Int): String = when {
    score >= 85 -> "optimal"
    score >= 65 -> "good"
    score >= 40 -> "moderate"
    else -> "recover"
}

internal fun formatSeconds(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    return "${safe / 60}:${(safe % 60).toString().padStart(2, '0')}"
}
