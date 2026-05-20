package com.trackme.data.auth

/**
 * State representing the status of the current device's authentication session.
 */
sealed interface SessionState {
    /**
     * Normal active session. App operates normally.
     */
    object Active : SessionState

    /**
     * Another device has logged in. The app blocks UI, pushes all local unsynced
     * data to the remote database, and prepares to log out.
     */
    object SyncingBeforeLogout : SessionState

    /**
     * The session has been terminated (either by manual action, sync completion,
     * or stale state after being offline when another device registered).
     * Wipes local database and forces navigation to login screen.
     */
    object Terminated : SessionState

    /**
     * The user is logged out of the app.
     */
    object LoggedOut : SessionState
}
