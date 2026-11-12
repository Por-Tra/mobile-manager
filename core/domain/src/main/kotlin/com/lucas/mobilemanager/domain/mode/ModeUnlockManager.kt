package com.lucas.mobilemanager.domain.mode

import com.lucas.mobilemanager.domain.repository.LockRepository
import com.lucas.mobilemanager.domain.repository.ModeRepository
import com.lucas.mobilemanager.domain.repository.StrictSessionRepository
import com.lucas.mobilemanager.domain.time.TimeProvider
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class ModeUnlockManager @Inject constructor(
    private val modes: ModeRepository,
    private val locks: LockRepository,
    private val sessions: StrictSessionRepository,
    private val time: TimeProvider,
) {
    suspend fun request() {
        if (locks.config.first() != null || sessions.current()?.isRunning(time.now()) == true) return
        modes.update { mode ->
            if (ModePolicy.requiresWait(mode, hasPin = false)) mode.copy(unlockRequestedAt = mode.unlockRequestedAt ?: time.now()) else mode
        }
    }

    suspend fun cancel() { modes.update { it.copy(unlockRequestedAt = null) } }

    suspend fun consume(): Boolean {
        val hasPin = locks.config.first() != null
        val running = sessions.current()?.isRunning(time.now()) == true
        var accepted = false
        modes.update { mode ->
            if (ModePolicy.canUnlockAfterWait(mode, hasPin, running, time.now())) {
                accepted = true
                mode.copy(unlockRequestedAt = null)
            } else mode
        }
        return accepted
    }
}
