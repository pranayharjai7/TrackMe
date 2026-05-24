package com.trackme.ui.workout.session.notification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationDisplayPlanTest {

    @Test
    fun `resting shows rest block not stepper`() {
        val plan = NotificationDisplayPlan.from(WorkoutNotificationState.RESTING)
        assertTrue(plan.expandedShowRestBlock)
        assertFalse(plan.expandedShowQuickStepper)
        assertTrue(plan.collapsedInlineRestTimer)
    }

    @Test
    fun `active set shows stepper not rest block`() {
        val plan = NotificationDisplayPlan.from(WorkoutNotificationState.ACTIVE_SET)
        assertFalse(plan.expandedShowRestBlock)
        assertTrue(plan.expandedShowQuickStepper)
        assertFalse(plan.collapsedInlineRestTimer)
    }

    @Test
    fun `paused hides progress`() {
        val plan = NotificationDisplayPlan.from(WorkoutNotificationState.WORKOUT_PAUSED)
        assertFalse(plan.collapsedShowProgress)
        assertFalse(plan.expandedShowQuickStepper)
    }
}
