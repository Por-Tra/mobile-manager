package com.lucas.mobilemanager.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucas.mobilemanager.domain.model.StrictSession
import com.lucas.mobilemanager.domain.repository.StrictSessionRepository
import com.lucas.mobilemanager.domain.session.StrictSessionManager
import com.lucas.mobilemanager.domain.session.StrictSessionPolicy
import com.lucas.mobilemanager.domain.session.StrictSessionPolicy.OverrideState
import com.lucas.mobilemanager.domain.time.TimeProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class OverrideUiState(
    val session: StrictSession?,
    val override: OverrideState,
    val now: Instant,
)

@HiltViewModel
class OverrideViewModel @Inject constructor(
    repository: StrictSessionRepository,
    private val manager: StrictSessionManager,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    /** Battement d'une seconde pour les comptes à rebours. */
    private val ticker = flow {
        while (true) {
            emit(timeProvider.now())
            delay(1_000)
        }
    }

    val state: StateFlow<OverrideUiState?> = combine(repository.observeCurrent(), ticker) { session, now ->
        val running = session?.takeIf { it.isRunning(now) }
        OverrideUiState(
            session = running,
            override = running?.let { StrictSessionPolicy.overrideState(it, now) } ?: OverrideState.NotRequested,
            now = now,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun request() {
        viewModelScope.launch { manager.requestOverride() }
    }

    fun cancel() {
        viewModelScope.launch { manager.cancelOverride() }
    }

    fun end() {
        viewModelScope.launch { manager.endByOverride() }
    }
}
