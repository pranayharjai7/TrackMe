package com.trackme.data.auth

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.trackme.domain.usecase.ClearLocalUserDataUseCase
import com.trackme.sync.SyncManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

val PREF_DEVICE_ID = stringPreferencesKey("device_id")

/**
 * Singleton manager coordinating single active session enforcement, background heartbeats,
 * graceful sync-before-logout, and immediate database write blocking.
 *
 * Architecture Layer: Session Management Coordinator
 *
 * Authentication Flow:
 * 1. User signs in/up. registerCurrentSession is triggered.
 * 2. SessionManager calls PostgreSQL register_session to set status to 'active' on current device
 *    and sets other active devices of the user to 'sync_requested' (if active within 45s) or 'terminated'.
 * 3. A periodic foreground heartbeat loop (every 15s) checks status.
 * 4. If heartbeat gets 'sync_requested', we lock UI, run a full Sync, then confirm_sync_complete.
 * 5. If heartbeat gets 'terminated' or sync is finished, we clear Room DB, signOut, and force return to Auth screen.
 */
@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val supabase: SupabaseClient,
    private val syncManager: SyncManager,
    private val dataStore: DataStore<Preferences>,
    private val clearLocalUserData: ClearLocalUserDataUseCase,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private var heartbeatJob: Job? = null

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.LoggedOut)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val isSyncingOrLoggingOut = AtomicBoolean(false)

    // Retrieve or generate a unique device ID to register sessions
    val deviceIdFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[PREF_DEVICE_ID] ?: run {
            val newId = UUID.randomUUID().toString()
            dataStore.edit { it[PREF_DEVICE_ID] = newId }
            newId
        }
    }

    private suspend fun getDeviceId(): String {
        return deviceIdFlow.first()
    }

    init {
        scope.launch {
            supabase.auth.sessionStatus.collect { status ->
                if (supabase.auth.currentSessionOrNull() != null) {
                    if (_sessionState.value == SessionState.LoggedOut) {
                        _sessionState.value = SessionState.Active
                        registerCurrentSession()
                    }
                } else {
                    _sessionState.value = SessionState.LoggedOut
                }
            }
        }
    }

    /**
     * Registers this device's session on the remote Supabase database.
     * Marks all other active sessions of the user for graceful sync-before-logout or termination.
     */
    fun registerCurrentSession() {
        scope.launch {
            val session = supabase.auth.currentSessionOrNull() ?: return@launch
            val devId = getDeviceId()
            Log.d("SessionManager", "Registering session ${session.user?.id} on device $devId")
            
            runCatching {
                supabase.postgrest.rpc(
                    function = "register_session",
                    parameters = mapOf("device_id_param" to devId)
                )
            }.onSuccess {
                Log.d("SessionManager", "Session registered successfully on server.")
                _sessionState.value = SessionState.Active
                startHeartbeat()
            }.onFailure { e ->
                Log.e("SessionManager", "Failed to register session on server", e)
            }
        }
    }

    /**
     * Starts the periodic foreground heartbeat loop.
     * Fires every 15 seconds to report device activity and pull session state updates.
     */
    fun startHeartbeat() {
        if (heartbeatJob?.isActive == true) return
        val currentSession = supabase.auth.currentSessionOrNull() ?: return
        Log.d("SessionManager", "Starting heartbeat loop...")

        heartbeatJob = scope.launch {
            val devId = getDeviceId()
            while (isActive) {
                if (supabase.auth.currentSessionOrNull() == null) {
                    _sessionState.value = SessionState.LoggedOut
                    break
                }

                // Skip heartbeat if we are already performing a logout / sync
                if (isSyncingOrLoggingOut.get()) {
                    delay(15_000)
                    continue
                }

                runCatching {
                    val response = supabase.postgrest.rpc(
                        function = "heartbeat_session",
                        parameters = mapOf("device_id_param" to devId)
                    )
                    // Robust clean deserialization for simple string output from Postgres RPC
                    response.data.replace("\"", "").trim()
                }.onSuccess { status ->
                    Log.d("SessionManager", "Heartbeat success. Server status: $status")
                    handleServerSessionStatus(status)
                }.onFailure { e ->
                    Log.e("SessionManager", "Heartbeat failed", e)
                }

                delay(15_000)
            }
        }
    }

    /**
     * Stops the heartbeat loop when the app goes into the background to save battery/network.
     */
    fun stopHeartbeat() {
        Log.d("SessionManager", "Stopping heartbeat loop...")
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun handleServerSessionStatus(status: String) {
        when (status) {
            "sync_requested" -> {
                if (isSyncingOrLoggingOut.compareAndSet(false, true)) {
                    triggerSyncAndLogout()
                }
            }
            "sync_completed", "terminated" -> {
                if (isSyncingOrLoggingOut.compareAndSet(false, true)) {
                    triggerImmediateForceLogout()
                }
            }
            "active" -> {
                _sessionState.value = SessionState.Active
            }
        }
    }

    private fun triggerSyncAndLogout() {
        scope.launch {
            _sessionState.value = SessionState.SyncingBeforeLogout
            Log.d("SessionManager", "Forced sync requested by server. Freezing UI and pushing local data...")
            
            val userId = supabase.auth.currentSessionOrNull()?.user?.id
            if (userId != null) {
                // Execute sync directly to push local unsynced writes
                val syncResult = runCatching {
                    withTimeout(30_000) {
                        syncManager.executeSyncDirectly(userId)
                    }
                }
                if (syncResult.isSuccess) {
                    Log.d("SessionManager", "Sync successfully completed before logout.")
                } else {
                    Log.e("SessionManager", "Sync failed or timed out before logout, forcing logout anyway", syncResult.exceptionOrNull())
                }
            }

            // 2. Notify the server that sync is finished for this session
            runCatching {
                supabase.postgrest.rpc(function = "confirm_sync_complete")
            }

            // 3. Complete logout
            triggerImmediateForceLogout()
        }
    }

    private fun triggerImmediateForceLogout() {
        scope.launch {
            _sessionState.value = SessionState.Terminated
            Log.d("SessionManager", "Forced logout triggered. Cleaning up local data...")
            
            // Stop heartbeats first
            stopHeartbeat()

            // Wipe Room database tables and cancel WorkManager syncs
            runCatching {
                clearLocalUserData()
            }.onFailure { e ->
                Log.e("SessionManager", "Error clearing local data during force logout", e)
            }

            // Sign out from Supabase Auth storage
            runCatching {
                supabase.auth.signOut()
            }

            _sessionState.value = SessionState.LoggedOut
            isSyncingOrLoggingOut.set(false)
            Log.d("SessionManager", "Forced logout successfully completed.")
        }
    }
}
