package com.trackme.ui.navigation

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackme.ui.onboarding.PREF_ONBOARDING_DONE
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.SessionStatus
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class NavViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val supabase: SupabaseClient,
) : ViewModel() {

    val startDestination: StateFlow<String?> = combine(
        dataStore.data.map { prefs -> prefs[PREF_ONBOARDING_DONE] == true },
        supabase.auth.sessionStatus.onStart {
            supabase.auth.awaitInitialization()
            emit(supabase.auth.sessionStatus.value)
        },
    ) { onboardingDone, sessionStatus ->
        when {
            !onboardingDone -> Routes.Auth.route
            sessionStatus is SessionStatus.LoadingFromStorage -> null
            sessionStatus is SessionStatus.Authenticated -> Routes.Home.route
            else -> Routes.Auth.route
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
