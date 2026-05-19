package com.trackme.domain.model

import java.util.Locale

fun String.formatExerciseName(): String {
    if (this.isEmpty()) return ""

    // A real UUID has exactly 4 hyphens and 36 characters — don't touch those.
    if (this.length == 36 && this.count { it == '-' } == 4) return this

    return this.lowercase()
        .replace('-', ' ')
        .replace('_', ' ')
        .split(' ')
        .filter { it.isNotEmpty() }
        .joinToString(" ") { word ->
            word.replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString()
            }
        }
}
