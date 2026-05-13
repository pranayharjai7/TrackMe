package com.trackme.domain.model

import java.util.Locale

fun String.formatExerciseName(): String {
    if (this.isEmpty()) return ""
    
    // If it looks like a UUID, don't format it (it will be replaced by a real name later if fetched)
    if (this.contains("-") && this.length > 30) return this
    
    return this.lowercase()
        .replace('_', ' ')
        .split(' ')
        .filter { it.isNotEmpty() }
        .joinToString(" ") { it.replaceFirstChar { char -> 
            if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString() 
        } }
}
