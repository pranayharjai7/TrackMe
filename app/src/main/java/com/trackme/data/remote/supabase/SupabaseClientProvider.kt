package com.trackme.data.remote.supabase

import com.trackme.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpRequestRetry

/**
 * Singleton factory for the Supabase SDK client.
 *
 * Architecture Layer: Data/network configuration
 *
 * Responsibilities:
 * - Install the Supabase Auth and Postgrest modules used by repositories.
 * - Keep redirect URL and public anon key usage centralized.
 * - Configure automatic network retries with exponential backoff.
 */
object SupabaseClientProvider {
    @OptIn(SupabaseInternal::class)
    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
        ) {
            install(Auth) {
                defaultRedirectUrl = "com.trackme://auth-callback"
            }
            install(Postgrest)
            httpEngine = Android.create {
                connectTimeout = 15_000
                socketTimeout = 15_000
            }
            httpConfig {
                install(HttpRequestRetry) {
                    retryOnExceptionOrServerErrors(maxRetries = 3)
                    exponentialDelay()
                }
            }
        }
    }
}
