package com.trackme.utils

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlin.coroutines.CoroutineContext

private const val TAG = "GlobalExceptionHandler"

/**
 * A global [CoroutineExceptionHandler] for use in root coroutine scopes across the app.
 *
 * Architecture Layer: Utilities / App Stability
 *
 * Responsibilities:
 * - Catch and log all unhandled coroutine exceptions to Firebase Crashlytics.
 * - Prevent cascading silent failures in background coroutines.
 * - Provide structured error context via a human-readable tag.
 *
 * Usage:
 *   val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + globalExceptionHandler("MyComponent"))
 *
 * @param tag A descriptive label identifying the scope where the exception originated.
 */
fun globalExceptionHandler(tag: String = "TrackMe"): CoroutineExceptionHandler =
    CoroutineExceptionHandler { context: CoroutineContext, throwable: Throwable ->
        Log.e(TAG, "[$tag] Unhandled coroutine exception in context: $context", throwable)
        runCatching {
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.setCustomKey("error_tag", tag)
            crashlytics.setCustomKey("coroutine_context", context.toString())
            crashlytics.recordException(throwable)
        }.onFailure { crashlyticsError ->
            // If Crashlytics itself fails (e.g. not initialized in tests), log locally
            Log.e(TAG, "[$tag] Failed to record exception to Crashlytics", crashlyticsError)
        }
    }

fun recordNonFatal(tag: String, throwable: Throwable) {
    Log.e(TAG, "[$tag] Non-fatal failure", throwable)
    runCatching {
        val crashlytics = FirebaseCrashlytics.getInstance()
        crashlytics.setCustomKey("error_tag", tag)
        crashlytics.recordException(throwable)
    }.onFailure { crashlyticsError ->
        Log.e(TAG, "[$tag] Failed to record non-fatal exception to Crashlytics", crashlyticsError)
    }
}
