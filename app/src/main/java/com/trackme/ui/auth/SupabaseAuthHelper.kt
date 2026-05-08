package com.trackme.ui.auth

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.gotrue.providers.Google

suspend fun signInWithEmailImpl(supabase: SupabaseClient, email: String, password: String) {
    supabase.auth.signInWith(Email) { this.email = email; this.password = password }
}

suspend fun signUpWithEmailImpl(supabase: SupabaseClient, email: String, password: String) {
    supabase.auth.signUpWith(Email) { this.email = email; this.password = password }
}

suspend fun signInWithGoogleImpl(supabase: SupabaseClient) {
    supabase.auth.signInWith(Google)
}
