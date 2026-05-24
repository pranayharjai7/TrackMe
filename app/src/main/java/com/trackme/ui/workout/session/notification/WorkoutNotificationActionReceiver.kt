package com.trackme.ui.workout.session.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Handles notification action broadcasts on a background thread.
 */
@AndroidEntryPoint
class WorkoutNotificationActionReceiver : BroadcastReceiver() {

    @Inject lateinit var actionHandler: WorkoutNotificationActionHandler

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val pendingResult = goAsync()
        try {
            actionHandler.handle(action)
        } catch (e: Exception) {
            WorkoutNotificationLogger.e("Action failed: $action", e)
        } finally {
            pendingResult.finish()
        }
    }
}
