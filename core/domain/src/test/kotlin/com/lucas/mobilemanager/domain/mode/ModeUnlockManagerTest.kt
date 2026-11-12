package com.lucas.mobilemanager.domain.mode

import com.lucas.mobilemanager.domain.lock.LockConfig
import com.lucas.mobilemanager.domain.model.StrictSession
import com.lucas.mobilemanager.domain.repository.LockRepository
import com.lucas.mobilemanager.domain.repository.ModeRepository
import com.lucas.mobilemanager.domain.repository.StrictSessionRepository
import com.lucas.mobilemanager.domain.time.TimeProvider
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ModeUnlockManagerTest {
    private val modes = object : ModeRepository {
        override val settings = MutableStateFlow(ModeSettings(AppMode.SEMI_FREE, minimumWaitMinutes = 60))
        override suspend fun update(transform: (ModeSettings) -> ModeSettings) { settings.value = transform(settings.value) }
    }
    private val locks = object : LockRepository {
        override val config = MutableStateFlow<LockConfig?>(null)
        override suspend fun save(config: LockConfig?) { this.config.value = config }
    }
    private val clock = object : TimeProvider {
        var instant: Instant = Instant.parse("2026-11-12T10:00:00Z")
        override fun now() = instant
        override fun zone(): ZoneId = ZoneId.of("Europe/Paris")
    }
    private var session: StrictSession? = null
    private val sessions = object : StrictSessionRepository {
        override fun observeCurrent() = MutableStateFlow(session)
        override suspend fun current() = session
        override suspend fun insert(session: StrictSession): Long = error("unused")
        override suspend fun setOverrideRequestedAt(sessionId: Long, at: Instant?) = Unit
        override suspend fun end(sessionId: Long, endedAt: Instant, reason: StrictSession.EndReason) = Unit
        override suspend fun sessionsStartedBetween(from: Instant, to: Instant) = emptyList<StrictSession>()
    }
    private fun manager() = ModeUnlockManager(modes, locks, sessions, clock)
    private fun advance(minutes: Long) { clock.instant += Duration.ofMinutes(minutes) }

    @Test
    fun `request survives manager recreation and repeated requests preserve the original start`() = runTest {
        val start = clock.now()
        manager().request()
        advance(59)
        manager().request()
        assertEquals(start, modes.settings.value.unlockRequestedAt)
        assertFalse(manager().consume())
        advance(1)
        assertTrue(manager().consume())
        assertNull(modes.settings.value.unlockRequestedAt)
        assertFalse(manager().consume())
        assertEquals(AppMode.SEMI_FREE, modes.settings.value.selectedMode)
    }

    @Test
    fun `cancelling requires a complete new wait`() = runTest {
        manager().request()
        advance(59)
        manager().cancel()
        assertFalse(manager().consume())
        manager().request()
        advance(1)
        assertFalse(manager().consume())
        advance(59)
        assertTrue(manager().consume())
    }

    @Test
    fun `pin added during waiting prevents timer unlocking`() = runTest {
        manager().request()
        locks.save(LockConfig("hash", "salt"))
        advance(120)
        assertFalse(manager().consume())
        manager().cancel()
        manager().request()
        assertNull(modes.settings.value.unlockRequestedAt)
    }

    @Test
    fun `strict session prevents both requesting and claiming a waiting unlock`() = runTest {
        manager().request()
        session = StrictSession(startedAt = clock.now(), endsAt = clock.now() + Duration.ofHours(3), blockedPackages = setOf("com.instagram.android"), reflectionDelay = Duration.ofMinutes(5))
        advance(60)
        assertFalse(manager().consume())
        manager().cancel()
        manager().request()
        assertNull(modes.settings.value.unlockRequestedAt)
    }

    @Test
    fun `switching away from semi free invalidates waiting access`() = runTest {
        manager().request()
        advance(60)
        modes.update { it.copy(selectedMode = AppMode.RESTRICTED) }
        assertFalse(manager().consume())
        modes.update { ModeSettings(AppMode.FREE) }
        manager().request()
        assertNull(modes.settings.value.unlockRequestedAt)
    }
}
