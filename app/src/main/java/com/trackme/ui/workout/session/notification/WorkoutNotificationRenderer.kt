package com.trackme.ui.workout.session.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.trackme.MainActivity
import com.trackme.R
import com.trackme.ui.workout.session.WorkoutSessionService

/**
 * Builds compact, rounded notification RemoteViews with state-aware visibility.
 */
class WorkoutNotificationRenderer(
    private val context: Context,
) {
    private var lastModel: WorkoutNotificationModel? = null
    private var collapsedViews: RemoteViews? = null
    private var expandedViews: RemoteViews? = null

    fun buildNotification(
        model: WorkoutNotificationModel,
        channelId: String,
        ongoing: Boolean = true,
    ): android.app.Notification {
        ensureViews()
        applyModel(model)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_workout)
            .setColor(ContextCompat.getColor(context, R.color.notification_accent))
            .setCustomContentView(collapsedViews)
            .setCustomBigContentView(expandedViews)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(openAppPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)

        attachSystemActions(builder, model)
        return builder.build()
    }

    fun buildRestFinishedAlert(@Suppress("UNUSED_PARAMETER") sessionId: String): android.app.Notification {
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            context,
            10,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(context, WorkoutSessionService.ALERTS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_rest_urgent)
            .setColor(ContextCompat.getColor(context, R.color.notification_accent))
            .setContentTitle(context.getString(R.string.notification_rest_finished_title))
            .setContentText(context.getString(R.string.notification_rest_finished_body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingOpen)
            .addAction(
                R.drawable.ic_notification_active,
                context.getString(R.string.notification_action_start_set),
                actionPendingIntent(WorkoutNotificationActions.ACTION_START_SET),
            )
            .build()
    }

    fun reset() {
        lastModel = null
        collapsedViews = null
        expandedViews = null
    }

    private fun attachSystemActions(
        builder: NotificationCompat.Builder,
        model: WorkoutNotificationModel,
    ) {
        val plan = model.displayPlan
        if (plan.systemActionRestPlus15) {
            builder.addAction(
                0,
                context.getString(R.string.notification_action_plus_15),
                actionPendingIntent(WorkoutNotificationActions.ACTION_REST_ADD_15),
            )
        }
        if (plan.systemActionPause) {
            val isResume = model.pauseButtonLabel == "Resume"
            builder.addAction(
                0,
                model.pauseButtonLabel,
                actionPendingIntent(
                    if (isResume) {
                        WorkoutNotificationActions.ACTION_RESUME_WORKOUT
                    } else {
                        WorkoutNotificationActions.ACTION_PAUSE_WORKOUT
                    },
                ),
            )
        }
        if (plan.systemActionEnd) {
            builder.addAction(
                0,
                context.getString(R.string.notification_action_end),
                actionPendingIntent(WorkoutNotificationActions.ACTION_FINISH_WORKOUT),
            )
        }
    }

    private fun ensureViews() {
        if (collapsedViews == null) {
            collapsedViews = RemoteViews(context.packageName, R.layout.notification_workout_collapsed)
        }
        if (expandedViews == null) {
            expandedViews = RemoteViews(context.packageName, R.layout.notification_workout_expanded)
        }
    }

    private fun applyModel(model: WorkoutNotificationModel) {
        val collapsed = collapsedViews ?: return
        val expanded = expandedViews ?: return
        val previous = lastModel
        val plan = model.displayPlan
        val isResting = model.notificationState == WorkoutNotificationState.RESTING

        applyDisplayPlan(collapsed, expanded, model, previous)

        if (previous?.currentExerciseName != model.currentExerciseName) {
            collapsed.setTextViewText(R.id.tv_exercise_title, model.currentExerciseName)
            expanded.setTextViewText(R.id.tv_exercise_hero, model.currentExerciseName)
        }

        val collapsedMeta = if (plan.collapsedInlineRestTimer) {
            "Rest · ${model.currentExerciseName}"
        } else {
            model.statusLine
        }
        if (previous?.statusLine != model.statusLine || previous.displayPlan != plan) {
            collapsed.setTextViewText(R.id.tv_set_meta, collapsedMeta)
            expanded.setTextViewText(R.id.tv_set_badge, model.statusLine)
        }

        val timerPillText = if (plan.collapsedInlineRestTimer) {
            WorkoutNotificationStateMapper.formatRestTime(model.restSecondsRemaining)
        } else {
            model.elapsedFormatted
        }
        if (previous?.elapsedFormatted != model.elapsedFormatted ||
            previous?.restSecondsRemaining != model.restSecondsRemaining ||
            previous?.displayPlan != plan
        ) {
            collapsed.setTextViewText(R.id.tv_elapsed_time, timerPillText)
            expanded.setTextViewText(R.id.tv_elapsed_duration, timerPillText)

            val timerColor = when {
                plan.collapsedInlineRestTimer && model.isRestUrgent ->
                    R.color.notification_rest_urgent
                plan.collapsedInlineRestTimer -> R.color.notification_blue
                else -> R.color.notification_violet
            }
            val color = ContextCompat.getColor(context, timerColor)
            collapsed.setTextColor(R.id.tv_elapsed_time, color)
            expanded.setTextColor(R.id.tv_elapsed_duration, color)
        }

        if (previous?.statusIconRes != model.statusIconRes) {
            collapsed.setImageViewResource(R.id.iv_status_icon, model.statusIconRes)
            expanded.setImageViewResource(R.id.iv_expanded_status_icon, model.statusIconRes)
        }

        if (previous?.workoutProgressPercent != model.workoutProgressPercent ||
            previous?.restProgressPercent != model.restProgressPercent
        ) {
            val collapsedProgress = if (isResting) model.restProgressPercent else model.workoutProgressPercent
            collapsed.setProgressBar(R.id.pb_session_progress, 100, collapsedProgress, false)
            expanded.setProgressBar(R.id.pb_workout_progress, 100, model.workoutProgressPercent, false)
            expanded.setProgressBar(R.id.pb_rest_progress, 100, model.restProgressPercent, false)
        }

        if (isResting && (
                previous?.restSecondsRemaining != model.restSecondsRemaining ||
                    previous?.isRestUrgent != model.isRestUrgent
                )
        ) {
            val restTime = WorkoutNotificationStateMapper.formatRestTime(model.restSecondsRemaining)
            expanded.setTextViewText(R.id.tv_rest_timer_display, restTime)
            expanded.setTextColor(
                R.id.tv_rest_timer_display,
                ContextCompat.getColor(
                    context,
                    if (model.isRestUrgent) R.color.notification_rest_urgent else R.color.notification_blue,
                ),
            )
        }

        if (previous?.contextLine != model.contextLine) {
            expanded.setTextViewText(R.id.tv_context_line, model.contextLine)
        }

        if (previous?.quickWeight != model.quickWeight || previous?.quickReps != model.quickReps) {
            expanded.setTextViewText(
                R.id.tv_weight_val,
                WorkoutNotificationStateMapper.formatWeight(model.quickWeight),
            )
            expanded.setTextViewText(R.id.tv_reps_val, model.quickReps.toString())
        }

        if (previous?.primaryActionLabel != model.primaryActionLabel ||
            previous?.primaryAction != model.primaryAction
        ) {
            expanded.setTextViewText(R.id.btn_expanded_primary, model.primaryActionLabel)
            expanded.setOnClickPendingIntent(
                R.id.btn_expanded_primary,
                actionPendingIntent(model.primaryAction),
            )
        }

        applyCollapsedAction(collapsed, model, previous)
        bindHiddenActionIntents(expanded, model)
        lastModel = model
    }

    private fun applyDisplayPlan(
        collapsed: RemoteViews,
        expanded: RemoteViews,
        model: WorkoutNotificationModel,
        previous: WorkoutNotificationModel?,
    ) {
        if (previous?.displayPlan == model.displayPlan) return

        val plan = model.displayPlan
        collapsed.setViewVisibility(
            R.id.pb_session_progress,
            if (plan.collapsedShowProgress) View.VISIBLE else View.GONE,
        )
        collapsed.setViewVisibility(
            R.id.tv_collapsed_action,
            if (plan.collapsedShowAction) View.VISIBLE else View.GONE,
        )

        expanded.setViewVisibility(
            R.id.tv_context_line,
            if (plan.expandedShowContextLine) View.VISIBLE else View.GONE,
        )
        expanded.setViewVisibility(
            R.id.pb_workout_progress,
            if (plan.expandedShowProgress) View.VISIBLE else View.GONE,
        )
        expanded.setViewVisibility(
            R.id.ll_rest_block,
            if (plan.expandedShowRestBlock) View.VISIBLE else View.GONE,
        )
        expanded.setViewVisibility(
            R.id.ll_quick_stepper,
            if (plan.expandedShowQuickStepper) View.VISIBLE else View.GONE,
        )
        expanded.setViewVisibility(
            R.id.btn_expanded_primary,
            if (plan.expandedShowPrimaryCta) View.VISIBLE else View.GONE,
        )
    }

    private fun applyCollapsedAction(
        collapsed: RemoteViews,
        model: WorkoutNotificationModel,
        previous: WorkoutNotificationModel?,
    ) {
        if (!model.displayPlan.collapsedShowAction) return

        val (label, action) = collapsedAction(model)
        if (
            previous?.showCollapsedStart != model.showCollapsedStart ||
            previous.showCollapsedComplete != model.showCollapsedComplete ||
            previous.showCollapsedSkipRest != model.showCollapsedSkipRest
        ) {
            collapsed.setTextViewText(R.id.tv_collapsed_action, label)
            collapsed.setOnClickPendingIntent(R.id.tv_collapsed_action, actionPendingIntent(action))
            val bg = when {
                model.showCollapsedSkipRest && model.isRestUrgent -> R.drawable.notification_chip_danger
                else -> R.drawable.notification_chip_primary
            }
            collapsed.setInt(R.id.tv_collapsed_action, "setBackgroundResource", bg)
        }
    }

    private fun collapsedAction(model: WorkoutNotificationModel): Pair<String, String> = when {
        model.showCollapsedSkipRest -> "Skip rest" to WorkoutNotificationActions.ACTION_SKIP_REST
        model.showCollapsedComplete -> "Complete set" to WorkoutNotificationActions.ACTION_COMPLETE_SET
        model.showCollapsedStart -> "Start set" to WorkoutNotificationActions.ACTION_START_SET
        else -> model.primaryActionLabel to model.primaryAction
    }

    /** Keeps pending intents wired for system actions and hidden stubs. */
    private fun bindHiddenActionIntents(expanded: RemoteViews, model: WorkoutNotificationModel) {
        expanded.setOnClickPendingIntent(
            R.id.btn_rest_plus_15,
            actionPendingIntent(WorkoutNotificationActions.ACTION_REST_ADD_15),
        )
        expanded.setOnClickPendingIntent(
            R.id.btn_rest_skip,
            actionPendingIntent(WorkoutNotificationActions.ACTION_SKIP_REST),
        )
        expanded.setOnClickPendingIntent(
            R.id.btn_pause_resume,
            actionPendingIntent(
                if (model.pauseButtonLabel == "Resume") {
                    WorkoutNotificationActions.ACTION_RESUME_WORKOUT
                } else {
                    WorkoutNotificationActions.ACTION_PAUSE_WORKOUT
                },
            ),
        )
        expanded.setOnClickPendingIntent(
            R.id.btn_end_workout,
            actionPendingIntent(WorkoutNotificationActions.ACTION_FINISH_WORKOUT),
        )
        expanded.setOnClickPendingIntent(
            R.id.btn_prev_exercise,
            actionPendingIntent(WorkoutNotificationActions.ACTION_PREV_EXERCISE),
        )
        expanded.setOnClickPendingIntent(
            R.id.btn_skip_exercise,
            actionPendingIntent(WorkoutNotificationActions.ACTION_NEXT_EXERCISE),
        )
        expanded.setOnClickPendingIntent(
            R.id.btn_weight_minus,
            actionPendingIntent(WorkoutNotificationActions.ACTION_WEIGHT_DEC),
        )
        expanded.setOnClickPendingIntent(
            R.id.btn_weight_plus,
            actionPendingIntent(WorkoutNotificationActions.ACTION_WEIGHT_INC),
        )
        expanded.setOnClickPendingIntent(
            R.id.btn_reps_minus,
            actionPendingIntent(WorkoutNotificationActions.ACTION_REPS_DEC),
        )
        expanded.setOnClickPendingIntent(
            R.id.btn_reps_plus,
            actionPendingIntent(WorkoutNotificationActions.ACTION_REPS_INC),
        )
    }

    fun actionPendingIntent(action: String): PendingIntent {
        val intent = Intent(context, WorkoutNotificationActionReceiver::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
