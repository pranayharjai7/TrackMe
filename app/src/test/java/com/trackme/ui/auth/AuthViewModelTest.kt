package com.trackme.ui.auth

import app.cash.turbine.test
import com.trackme.sync.SyncManager
import io.github.jan.supabase.SupabaseClient
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val supabase = mockk<SupabaseClient>(relaxed = true)
    private val syncManager = mockk<SyncManager>(relaxed = true)
    private lateinit var viewModel: AuthViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = AuthViewModel(supabase, syncManager)
    }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `initial state is idle`() = runTest {
        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isLoading)
            assertNull(state.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `empty email shows validation error`() = runTest {
        viewModel.uiState.test {
            awaitItem() // initial
            viewModel.signInWithEmail("", "password123")
            val state = awaitItem()
            assertNotNull(state.error)
            assertTrue(state.error!!.contains("email", ignoreCase = true))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `short password shows validation error`() = runTest {
        viewModel.uiState.test {
            awaitItem()
            viewModel.signInWithEmail("test@test.com", "12345")
            val state = awaitItem()
            assertNotNull(state.error)
            assertTrue(state.error!!.contains("password", ignoreCase = true))
            cancelAndIgnoreRemainingEvents()
        }
    }
}
