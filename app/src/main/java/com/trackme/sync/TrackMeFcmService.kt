package com.trackme.sync

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.trackme.data.auth.SessionManager
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Service that handles incoming Firebase Cloud Messaging (FCM) registrations and payloads.
 *
 * Architecture Layer: Sync/Remote Boundary
 *
 * Responsibilities:
 * - Capture newly generated FCM registration tokens.
 * - Save active FCM tokens to the Supabase database mapped to the current authenticated user.
 * - Handle incoming data payloads (e.g. remote instructions to trigger background syncs).
 */
@AndroidEntryPoint
class TrackMeFcmService : FirebaseMessagingService() {

    @Inject lateinit var supabase: SupabaseClient
    @Inject lateinit var sessionManager: SessionManager
    @Inject lateinit var syncManager: SyncManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("TrackMeFcmService", "Refreshed FCM token received: $token")
        uploadFcmToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d("TrackMeFcmService", "Received push notification from: ${message.from}")

        // 1. Process sync command if requested by remote backend
        if (message.data["action"] == "sync" || message.data["type"] == "sync") {
            val userId = supabase.auth.currentSessionOrNull()?.user?.id
            if (userId != null) {
                Log.d("TrackMeFcmService", "FCM triggered background synchronization for user: $userId")
                serviceScope.launch {
                    runCatching {
                        syncManager.executeSyncDirectly(userId)
                    }.onFailure { e ->
                        Log.e("TrackMeFcmService", "Failed executing FCM sync request", e)
                    }
                }
            }
        }
    }

    private fun uploadFcmToken(token: String) {
        serviceScope.launch {
            val session = supabase.auth.currentSessionOrNull()
            if (session == null || session.user == null) {
                Log.d("TrackMeFcmService", "Skipping FCM token registration: No active user session.")
                return@launch
            }

            val userId = session.user!!.id
            val deviceId = runCatching { sessionManager.deviceIdFlow.first() }.getOrElse { "" }
            if (deviceId.isEmpty()) return@launch

            Log.d("TrackMeFcmService", "Uploading token to Supabase for user: $userId, device: $deviceId")
            runCatching {
                supabase.postgrest["user_fcm_tokens"].upsert(
                    mapOf(
                        "fcm_token" to token,
                        "user_id" to userId,
                        "device_id" to deviceId,
                        "platform" to "android",
                        "updated_at" to java.time.Instant.now().toString()
                    )
                )
            }.onSuccess {
                Log.d("TrackMeFcmService", "FCM Token successfully uploaded to Supabase.")
            }.onFailure { e ->
                Log.e("TrackMeFcmService", "Failed to upload FCM Token to Supabase", e)
            }
        }
    }
}
