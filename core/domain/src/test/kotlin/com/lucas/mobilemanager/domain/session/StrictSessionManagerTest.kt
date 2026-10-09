package com.lucas.mobilemanager.domain.session

import com.lucas.mobilemanager.domain.MONDAY
import com.lucas.mobilemanager.domain.PARIS
import com.lucas.mobilemanager.domain.TIKTOK
import com.lucas.mobilemanager.domain.YOUTUBE
import com.lucas.mobilemanager.domain.at
import com.lucas.mobilemanager.domain.model.EventLogEntry
import com.lucas.mobilemanager.domain.model.EventType
import com.lucas.mobilemanager.domain.model.StrictSession
import com.lucas.mobilemanager.domain.repository.EventLogRepository
import com.lucas.mobilemanager.domain.repository.StrictSessionRepository
import com.lucas.mobilemanager.domain.session.StrictSessionManager.StartResult
import com.lucas.mobilemanager.domain.time.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class StrictSessionManagerTest {

    private val clock = FakeTime(at(MONDAY, 9).toInstant())
    private val repository = FakeSessionRepository()
    private val events = mutableListOf<EventLogEntry>()
    private val manager = StrictSessionManager(repository, FakeEventLog(events), clock)

    private fun minutes(m: Long) = Duration.ofMinutes(m)
    private fun advance(d: Duration) { clock.now = clock.now + d }

    private suspend fun startDefault() =
        manager.start(clock.now + Duration.ofHours(1), setOf(TIKTOK, YOUTUBE), minutes(5))

    @Test
    fun `start stores the session and logs it`() = runTest {
        val result = startDefault()

        assertTrue(result is StartResult.Started)
        assertTrue(repository.current()!!.isRunning(clock.now))
        assertEquals(listOf(EventType.STRICT_SESSION_STARTED), events.map { it.type })
    }

    @Test
    fun `a second session cannot start while one is running`() = runTest {
        startDefault()
        assertEquals(StartResult.AlreadyRunning, startDefault())
    }

    @Test
    fun `invalid session is refused without side effects`() = runTest {
        val result = manager.start(clock.now + Duration.ofHours(13), setOf(TIKTOK), minutes(5))
        assertEquals(StartResult.Invalid(setOf(StrictSessionPolicy.ValidationError.TOO_LONG)), result)
        assertNull(repository.current())
        assertTrue(events.isEmpty())
    }

    @Test
    fun `override is impossible before the reflection delay`() = runTest {
        startDefault()
        manager.requestOverride()
        advance(minutes(4))

        assertFalse(manager.endByOverride())
        assertTrue(repository.current()!!.isRunning(clock.now))
    }

    @Test
    fun `override after the delay ends the session and logs it`() = runTest {
        startDefault()
        manager.requestOverride()
        advance(minutes(5))

        assertTrue(manager.endByOverride())
        assertNull(repository.current())
        assertEquals(StrictSession.EndReason.OVERRIDDEN, repository.sessions.single().endReason)
        assertEquals(EventType.STRICT_SESSION_OVERRIDDEN, events.last().type)
    }

    @Test
    fun `requesting again while waiting does not restart the delay`() = runTest {
        startDefault()
        manager.requestOverride()
        advance(minutes(3))
        manager.requestOverride()
        advance(minutes(2))

        assertTrue(manager.endByOverride())
    }

    @Test
    fun `expired request must be renewed with a full delay`() = runTest {
        startDefault()
        manager.requestOverride()
        advance(minutes(8)) // délai de 5 min + fenêtre de 2 min dépassés
        assertFalse(manager.endByOverride())

        manager.requestOverride()
        advance(minutes(4))
        assertFalse(manager.endByOverride())
        advance(minutes(1))
        assertTrue(manager.endByOverride())
    }

    @Test
    fun `cancel clears the request`() = runTest {
        startDefault()
        manager.requestOverride()
        manager.cancelOverride()
        advance(minutes(5))

        assertFalse(manager.endByOverride())
        assertNull(repository.current()!!.overrideRequestedAt)
    }

    @Test
    fun `session completes at its end time`() = runTest {
        startDefault()
        advance(Duration.ofMinutes(59))
        assertFalse(manager.completeIfDue())

        advance(minutes(2))
        assertTrue(manager.completeIfDue())
        assertNull(repository.current())
        assertEquals(StrictSession.EndReason.COMPLETED, repository.sessions.single().endReason)
        assertEquals(EventType.STRICT_SESSION_ENDED, events.last().type)
    }

    @Test
    fun `new session can start once the previous one is over`() = runTest {
        startDefault()
        advance(Duration.ofHours(2))
        assertTrue(startDefault() is StartResult.Started)
        assertEquals(2, repository.sessions.size)
    }

    private class FakeTime(var now: Instant) : TimeProvider {
        override fun now(): Instant = now
        override fun zone(): ZoneId = PARIS
    }

    private class FakeEventLog(private val events: MutableList<EventLogEntry>) : EventLogRepository {
        override suspend fun record(entry: EventLogEntry) { events += entry }
        override fun observeBetween(from: Instant, to: Instant): Flow<List<EventLogEntry>> = flowOf(events)
    }

    private class FakeSessionRepository : StrictSessionRepository {
        val sessions = mutableListOf<StrictSession>()
        private val currentFlow = MutableStateFlow<StrictSession?>(null)

        override fun observeCurrent(): Flow<StrictSession?> = currentFlow
        override suspend fun current(): StrictSession? = sessions.lastOrNull { it.endedAt == null }

        override suspend fun insert(session: StrictSession): Long {
            val id = sessions.size + 1L
            sessions += session.copy(id = id)
            publish()
            return id
        }

        override suspend fun setOverrideRequestedAt(sessionId: Long, at: Instant?) =
            update(sessionId) { it.copy(overrideRequestedAt = at) }

        override suspend fun end(sessionId: Long, endedAt: Instant, reason: StrictSession.EndReason) =
            update(sessionId) { it.copy(endedAt = endedAt, endReason = reason) }

        override suspend fun sessionsStartedBetween(from: Instant, to: Instant) =
            sessions.filter { !it.startedAt.isBefore(from) && it.startedAt.isBefore(to) }

        private suspend fun update(id: Long, change: (StrictSession) -> StrictSession) {
            val index = sessions.indexOfFirst { it.id == id }
            sessions[index] = change(sessions[index])
            publish()
        }

        private suspend fun publish() { currentFlow.value = current() }
    }
}
