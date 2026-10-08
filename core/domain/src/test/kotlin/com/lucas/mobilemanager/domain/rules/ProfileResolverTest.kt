package com.lucas.mobilemanager.domain.rules

import com.lucas.mobilemanager.domain.INSTAGRAM
import com.lucas.mobilemanager.domain.MONDAY
import com.lucas.mobilemanager.domain.TIKTOK
import com.lucas.mobilemanager.domain.at
import com.lucas.mobilemanager.domain.block
import com.lucas.mobilemanager.domain.model.ManualActivation
import com.lucas.mobilemanager.domain.model.StrictSession
import com.lucas.mobilemanager.domain.profile
import com.lucas.mobilemanager.domain.schedule
import com.lucas.mobilemanager.domain.time
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

class ProfileResolverTest {

    private val resolver = ProfileResolver()
    private val now = at(MONDAY, 10)

    private fun resolve(
        profiles: List<com.lucas.mobilemanager.domain.model.Profile> = emptyList(),
        globalRules: List<Rule> = emptyList(),
        manual: ManualActivation? = null,
        strict: StrictSession? = null,
    ) = resolver.resolve(profiles, globalRules, manual, strict, now).layers.map { it.source }

    private fun session(start: Int, end: Int) = StrictSession(
        startedAt = at(MONDAY, start).toInstant(),
        endsAt = at(MONDAY, end).toInstant(),
        blockedPackages = setOf(TIKTOK, INSTAGRAM),
        reflectionDelay = Duration.ofMinutes(5),
    )

    @Test
    fun `nothing active gives an empty policy`() = assertTrue(resolve().isEmpty())

    @Test
    fun `scheduled profiles are ordered by priority then id`() {
        val sources = resolve(
            profiles = listOf(
                profile(id = 3, priority = 5),
                profile(id = 2, priority = 10),
                profile(id = 1, priority = 5),
            ),
        )
        assertEquals(listOf(2L, 1L, 3L), sources.map { (it as LayerSource.ScheduledProfile).profileId })
    }

    @Test
    fun `profiles outside their schedule are ignored`() {
        val evening = profile(id = 1, schedules = listOf(schedule(time(18), time(23))))
        assertTrue(resolve(profiles = listOf(evening)).isEmpty())
    }

    @Test
    fun `disabled and manual-only profiles are not scheduled`() {
        val sources = resolve(
            profiles = listOf(
                profile(id = 1, enabled = false),
                profile(id = 2, autoActivation = false),
            ),
        )
        assertTrue(sources.isEmpty())
    }

    @Test
    fun `manual profile comes before scheduled ones and is not duplicated`() {
        val sources = resolve(
            profiles = listOf(profile(id = 1, priority = 100), profile(id = 2, priority = 1)),
            manual = ManualActivation(profileId = 2, until = null),
        )
        assertEquals(
            listOf(
                LayerSource.ManualProfile(2, "Profil 2"),
                LayerSource.ScheduledProfile(1, "Profil 1", 100),
            ),
            sources,
        )
    }

    @Test
    fun `manual-only profile can be activated manually`() {
        val sources = resolve(
            profiles = listOf(profile(id = 1, autoActivation = false)),
            manual = ManualActivation(profileId = 1, until = null),
        )
        assertEquals(listOf(LayerSource.ManualProfile(1, "Profil 1")), sources)
    }

    @Test
    fun `expired manual activation is ignored`() {
        val sources = resolve(
            profiles = listOf(profile(id = 1, autoActivation = false)),
            manual = ManualActivation(profileId = 1, until = at(MONDAY, 9).toInstant()),
        )
        assertTrue(sources.isEmpty())
    }

    @Test
    fun `manual activation of a disabled profile is ignored`() {
        val sources = resolve(
            profiles = listOf(profile(id = 1, enabled = false)),
            manual = ManualActivation(profileId = 1, until = null),
        )
        assertTrue(sources.isEmpty())
    }

    @Test
    fun `strict session comes first and blocks its packages until its end`() {
        val policy = resolver.resolve(
            profiles = listOf(profile(id = 1)),
            globalRules = emptyList(),
            manualActivation = ManualActivation(1, null),
            strictSession = session(9, 11),
            now = now,
        )
        val first = policy.layers.first()
        assertEquals(LayerSource.StrictSession, first.source)
        assertEquals(listOf(block(INSTAGRAM), block(TIKTOK)), first.rules)
        assertEquals(at(MONDAY, 11).toInstant(), first.activeUntil)
    }

    @Test
    fun `finished or future strict sessions are ignored`() {
        assertTrue(resolve(strict = session(8, 10)).isEmpty())
        assertTrue(resolve(strict = session(11, 12)).isEmpty())
    }

    @Test
    fun `global rules come last and are omitted when empty`() {
        val sources = resolve(profiles = listOf(profile(id = 1)), globalRules = listOf(block(TIKTOK)))
        assertEquals(LayerSource.Global, sources.last())
        assertEquals(1, resolve(profiles = listOf(profile(id = 1))).size)
    }

    @Test
    fun `scheduled layer ends with its current window`() {
        val sleep = profile(id = 1, schedules = listOf(schedule(time(9), time(12))))
        val layer = resolver.resolve(listOf(sleep), emptyList(), null, null, now).layers.single()
        assertEquals(at(MONDAY, 12).toInstant(), layer.activeUntil)
    }
}
