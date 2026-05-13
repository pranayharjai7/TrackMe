package com.trackme.utils

import java.util.Calendar

const val MILLIS_PER_DAY: Long = 86_400_000L

data class LocalDayBounds(
    val startMillis: Long,
    val endExclusiveMillis: Long,
)

/**
 * Utilities for local-day calculations used by dashboard and session state.
 *
 * Architecture Layer: Shared utility
 *
 * Responsibilities:
 * - Normalize timestamps to the user's current local midnight.
 * - Keep repeated "days ago" and "start of today" calculations consistent.
 *
 * These functions intentionally use Calendar because the existing code already
 * used Calendar-based local time. That preserves current behavior across the app.
 */
fun startOfLocalDayMillis(epochMs: Long): Long {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = epochMs
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
}

fun startOfTodayMillis(): Long = startOfLocalDayMillis(System.currentTimeMillis())

fun todayBoundsMillis(): LocalDayBounds {
    val start = startOfTodayMillis()
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = start
    calendar.add(Calendar.DATE, 1)
    return LocalDayBounds(startMillis = start, endExclusiveMillis = calendar.timeInMillis)
}

fun millisDaysAgo(days: Int, nowMs: Long = System.currentTimeMillis()): Long =
    nowMs - days.toLong() * MILLIS_PER_DAY
