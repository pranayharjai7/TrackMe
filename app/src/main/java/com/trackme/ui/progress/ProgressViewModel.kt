package com.trackme.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackme.domain.model.HealthSnapshot
import com.trackme.domain.model.PersonalRecord
import com.trackme.domain.usecase.GetHealthSnapshotsUseCase
import com.trackme.domain.usecase.GetPersonalRecordsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class ProgressUiState(
    val personalRecords: List<PersonalRecord> = emptyList(),
    val weightHistory: List<HealthSnapshot> = emptyList(),
    val latestSnapshot: HealthSnapshot? = null,
    val isLoading: Boolean = true,
)

@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val getPersonalRecords: GetPersonalRecordsUseCase,
    private val getHealthSnapshots: GetHealthSnapshotsUseCase,
    private val supabase: SupabaseClient,
) : ViewModel() {

    private val userId get() = supabase.auth.currentSessionOrNull()?.user?.id ?: ""

    val uiState: StateFlow<ProgressUiState> = combine(
        getPersonalRecords(userId),
        getHealthSnapshots(userId, 30),
    ) { prs, snapshots ->
        ProgressUiState(
            personalRecords = prs,
            weightHistory = snapshots,
            latestSnapshot = snapshots.maxByOrNull { it.date },
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProgressUiState())
}
