package com.trackme.ui.workout.session.notification

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.trackme.ui.workout.session.ActiveSessionUiState
import com.trackme.ui.workout.session.WorkoutSessionService
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutNotificationIntegrationTest {

    @Test
    fun renderer_buildsOngoingNotification() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val renderer = WorkoutNotificationRenderer(context)
        val model = WorkoutNotificationStateMapper.map(
            ActiveSessionUiState(
                sessionId = "session-test",
                activeExerciseId = "exercise-test",
            ),
        )

        val notification = renderer.buildNotification(
            model = model,
            channelId = WorkoutSessionService.ONGOING_CHANNEL_ID,
        )

        assertNotNull(notification)
    }

    @Test
    fun restFinishedAlert_buildsSuccessfully() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val renderer = WorkoutNotificationRenderer(context)
        val alert = renderer.buildRestFinishedAlert(sessionId = "session-test")
        assertNotNull(alert)
    }
}
