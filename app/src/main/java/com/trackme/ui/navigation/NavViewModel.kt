package com.trackme.ui.navigation

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackme.ui.onboarding.PREF_ONBOARDING_DONE
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class NavViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : ViewModel() {

    val startDestination: StateFlow<String?> = dataStore.data
        .map { prefs ->
            if (prefs[PREF_ONBOARDING_DONE] == true) Routes.Home.route else Routes.Auth.route
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
