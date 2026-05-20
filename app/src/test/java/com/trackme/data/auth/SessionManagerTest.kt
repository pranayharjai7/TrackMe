@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.trackme.data.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import app.cash.turbine.test
import com.trackme.domain.usecase.ClearLocalUserDataUseCase
import com.trackme.sync.SyncManager
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.SessionStatus
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.user.UserSession
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.mockk.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionManagerTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val context = mockk<Context>(relaxed = true)
    private val supabase = mockk<SupabaseClient>(relaxed = true)
    private val syncManager = mockk<SyncManager>(relaxed = true)
    private val dataStore = mockk<DataStore<Preferences>>(relaxed = true)
    private val clearLocalUserData = mockk<ClearLocalUserDataUseCase>(relaxed = true)

    private val auth = mockk<Auth>(relaxed = true)
    private lateinit var realPostgrest: Postgrest

    private val sessionStatusFlow = MutableStateFlow<SessionStatus>(SessionStatus.NotAuthenticated(isSignOut = false))
    private val preferences = mockk<Preferences>(relaxed = true)

    private lateinit var sessionManager: SessionManager
    private var heartbeatResponse = "active"

    private val mockEngine = MockEngine { request ->
        val path = request.url.encodedPath
        when {
            path.endsWith("/rpc/register_session") -> {
                respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
            path.endsWith("/rpc/heartbeat_session") -> {
                respond("\"$heartbeatResponse\"", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
            path.endsWith("/rpc/confirm_sync_complete") -> {
                respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
            else -> {
                respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        // Mock Static Extensions for Supabase Client
        mockkStatic("io.github.jan.supabase.gotrue.AuthKt")
        mockkStatic("io.github.jan.supabase.postgrest.PostgrestKt")

        val realSupabase = createSupabaseClient("https://example.supabase.co", "dummy") {
            install(Postgrest)
            httpEngine = mockEngine
        }
        realPostgrest = realSupabase.postgrest

        every { supabase.auth } returns auth
        every { supabase.postgrest } returns realPostgrest

        // Setup Auth mocks
        every { auth.sessionStatus } returns sessionStatusFlow
        every { auth.currentSessionOrNull() } returns null

        // Setup DataStore mocks
        every { preferences[PREF_DEVICE_ID] } returns "test-device-id"
        every { dataStore.data } returns flowOf(preferences)
    }

    @After
    fun tearDown() {
        if (::sessionManager.isInitialized) {
            try {
                val field = SessionManager::class.java.getDeclaredField("scope")
                field.isAccessible = true
                val scope = field.get(sessionManager) as kotlinx.coroutines.CoroutineScope
                scope.cancel()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `initial state is LoggedOut when user has no active supabase session`() = runTest {
        sessionManager = SessionManager(
            context = context,
            supabase = supabase,
            syncManager = syncManager,
            dataStore = dataStore,
            clearLocalUserData = clearLocalUserData,
            ioDispatcher = testDispatcher
        )

        assertEquals(SessionState.LoggedOut, sessionManager.sessionState.value)
    }

    @Test
    fun `state transitions to Active and registers session when user logs in`() = runTest {
        val userSession = mockk<UserSession>(relaxed = true)
        every { userSession.user?.id } returns "test-user-id"
        every { auth.currentSessionOrNull() } returns userSession

        // Construct SessionManager
        sessionManager = SessionManager(
            context = context,
            supabase = supabase,
            syncManager = syncManager,
            dataStore = dataStore,
            clearLocalUserData = clearLocalUserData,
            ioDispatcher = testDispatcher
        )

        // Emit that user authenticated
        sessionStatusFlow.value = SessionStatus.NotAuthenticated(isSignOut = false) // triggers flow collection

        sessionManager.sessionState.test {
            val state = awaitItem()
            assertEquals(SessionState.Active, state)
            cancelAndIgnoreRemainingEvents()
        }

        // Verify request was sent via Ktor MockEngine
        val registered = mockEngine.requestHistory.any { it.url.encodedPath.endsWith("/rpc/register_session") }
        assertTrue(registered)
    }

    @Test
    fun `heartbeat with sync_requested response triggers sync before forced logout`() = runTest {
        val userSession = mockk<UserSession>(relaxed = true)
        every { userSession.user?.id } returns "test-user-id"
        every { auth.currentSessionOrNull() } returns userSession

        // Set response for heartbeat
        heartbeatResponse = "sync_requested"

        sessionManager = SessionManager(
            context = context,
            supabase = supabase,
            syncManager = syncManager,
            dataStore = dataStore,
            clearLocalUserData = clearLocalUserData,
            ioDispatcher = testDispatcher
        )

        // Force register session to start heartbeat
        sessionManager.registerCurrentSession()
        sessionManager.startHeartbeat()

        sessionManager.sessionState.test {
            // First item should be active after registration
            var state = awaitItem()
            while (state != SessionState.SyncingBeforeLogout && state != SessionState.LoggedOut) {
                state = awaitItem()
            }
            
            // Check that it does direct sync, wipes data, and logs out
            coVerify(exactly = 1) { syncManager.executeSyncDirectly("test-user-id") }
            coVerify(exactly = 1) { clearLocalUserData() }
            coVerify(exactly = 1) { auth.signOut() }

            // Verify rpc calls were actually sent to the mock server
            val syncCompletedSent = mockEngine.requestHistory.any { it.url.encodedPath.endsWith("/rpc/confirm_sync_complete") }
            assertTrue(syncCompletedSent)
            
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `heartbeat with terminated response triggers immediate force logout without syncing`() = runTest {
        val userSession = mockk<UserSession>(relaxed = true)
        every { userSession.user?.id } returns "test-user-id"
        every { auth.currentSessionOrNull() } returns userSession

        // Set response for heartbeat
        heartbeatResponse = "terminated"

        sessionManager = SessionManager(
            context = context,
            supabase = supabase,
            syncManager = syncManager,
            dataStore = dataStore,
            clearLocalUserData = clearLocalUserData,
            ioDispatcher = testDispatcher
        )

        sessionManager.registerCurrentSession()
        sessionManager.startHeartbeat()

        sessionManager.sessionState.test {
            var state = awaitItem()
            while (state != SessionState.Terminated && state != SessionState.LoggedOut) {
                state = awaitItem()
            }
            
            // Wipes data and logs out directly without syncing or confirming
            coVerify(exactly = 0) { syncManager.executeSyncDirectly(any()) }
            coVerify(exactly = 1) { clearLocalUserData() }
            coVerify(exactly = 1) { auth.signOut() }

            // Verify no confirm_sync_complete request was sent
            val syncCompletedSent = mockEngine.requestHistory.any { it.url.encodedPath.endsWith("/rpc/confirm_sync_complete") }
            assertEquals(false, syncCompletedSent)
            
            cancelAndIgnoreRemainingEvents()
        }
    }
}
