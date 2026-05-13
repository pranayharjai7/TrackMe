package com.trackme.ui.auth

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.Google
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.gotrue.providers.builtin.IDToken

/**
 * Thin Supabase Auth wrappers used by AuthViewModel.
 *
 * Architecture Layer: UI auth adapter
 *
 * Responsibilities:
 * - Isolate SDK-specific provider builders from ViewModel state management.
 * - Keep unit tests focused on ViewModel validation and UI state transitions.
 */
suspend fun signInWithEmailImpl(supabase: SupabaseClient, email: String, password: String) {
    supabase.auth.signInWith(Email) {
        this.email = email
        this.password = password
    }
}

suspend fun signUpWithEmailImpl(supabase: SupabaseClient, email: String, password: String) {
    supabase.auth.signUpWith(Email) {
        this.email = email
        this.password = password
    }
}

suspend fun signInWithGoogleIdTokenImpl(supabase: SupabaseClient, idToken: String) {
    supabase.auth.signInWith(IDToken) {
        this.idToken = idToken
        provider = Google
    }
}
