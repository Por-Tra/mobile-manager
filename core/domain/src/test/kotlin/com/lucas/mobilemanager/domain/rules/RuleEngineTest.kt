package com.lucas.mobilemanager.domain.rules

import com.lucas.mobilemanager.domain.CLOCK
import com.lucas.mobilemanager.domain.DIALER
import com.lucas.mobilemanager.domain.INSTAGRAM
import com.lucas.mobilemanager.domain.MONDAY
import com.lucas.mobilemanager.domain.TIKTOK
import com.lucas.mobilemanager.domain.YOUTUBE
import com.lucas.mobilemanager.domain.allow
import com.lucas.mobilemanager.domain.at
import com.lucas.mobilemanager.domain.block
import com.lucas.mobilemanager.domain.limit
import com.lucas.mobilemanager.domain.model.ManualActivation
import com.lucas.mobilemanager.domain.model.Profile
import com.lucas.mobilemanager.domain.model.StrictSession
import com.lucas.mobilemanager.domain.profile
import com.lucas.mobilemanager.domain.schedule
import com.lucas.mobilemanager.domain.time
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.ZonedDateTime

class RuleEngineTest {

    private val resolver = ProfileResolver()
    private val engine = RuleEngine()
    private val whitelist = SystemWhitelist(setOf(DIALER, CLOCK))

    private fun decide(
        request: AppRequest,
        profiles: List<Profile> = emptyList(),
        globalRules: List<Rule> = emptyList(),
        manual: ManualActivation? = null,
        strict: StrictSession? = null,
        now: ZonedDateTime = at(MONDAY, 10),
    ): Decision {
        val policy = resolver.resolve(profiles, globalRules, manual, strict, now)
        return engine.evaluate(policy, request, whitelist, now)
    }

    private fun minutes(m: Long) = Duration.ofMinutes(m)
    private val allApps = Rule(Target.AllApps, RuleAction.Block)

    private fun strictSession(vararg packages: String) = StrictSession(
        startedAt = at(MONDAY, 9).toInstant(),
        endsAt = at(MONDAY, 12).toInstant(),
        blockedPackages = packages.toSet(),
        reflectionDelay = minutes(5),
    )

    @Nested
    inner class SpecificationExamples {

        @Test
        fun `sleep blocks everything but an explicit exception lets the dialer through`() {
            val sleep = profile(
                id = 1,
                name = "Sommeil",
                schedules = listOf(schedule(time(0), time(6))),
                rules = listOf(allApps, allow("com.samsung.android.dialer")),
            )
            val night = at(MONDAY, 2)

            assertEquals(
                Decision.Allowed(null),
                decide(AppRequest("com.samsung.android.dialer"), listOf(sleep), now = night),
            )
            assertEquals(
                Decision.Blocked(BlockReason.Profile(1, "Sommeil"), at(MONDAY, 6).toInstant()),
                decide(AppRequest(YOUTUBE), listOf(sleep), now = night),
            )
        }

        @Test
        fun `strongest restriction wins between overlapping profiles`() {
            val study = profile(id = 1, name = "Études", priority = 10, rules = listOf(block(INSTAGRAM)))
            val freeTime = profile(id = 2, name = "Temps libre", priority = 5, rules = listOf(limit(INSTAGRAM, 30)))

            val decision = decide(AppRequest(INSTAGRAM), listOf(study, freeTime))

            assertEquals(BlockReason.Profile(1, "Études"), (decision as Decision.Blocked).reason)
        }

        @Test
        fun `strongest restriction wins even when it comes from a lower priority`() {
            val freeTime = profile(id = 1, priority = 10, rules = listOf(limit(INSTAGRAM, 30)))
            val study = profile(id = 2, name = "Études", priority = 5, rules = listOf(block(INSTAGRAM)))

            val decision = decide(AppRequest(INSTAGRAM), listOf(freeTime, study))

            assertEquals(BlockReason.Profile(2, "Études"), (decision as Decision.Blocked).reason)
        }

        @Test
        fun `explicit allow from a higher level overrides lower restrictions`() {
            val manual = profile(id = 1, autoActivation = false, rules = listOf(allow(YOUTUBE)))
            val scheduled = profile(id = 2, rules = listOf(block(YOUTUBE)))

            assertEquals(
                Decision.Allowed(null),
                decide(AppRequest(YOUTUBE), listOf(manual, scheduled), manual = ManualActivation(1, null)),
            )
        }

        @Test
        fun `a limit from a higher level does not override a lower block`() {
            val manual = profile(id = 1, autoActivation = false, rules = listOf(limit(YOUTUBE, 60)))
            val scheduled = profile(id = 2, name = "Études", rules = listOf(block(YOUTUBE)))

            val decision = decide(AppRequest(YOUTUBE), listOf(manual, scheduled), manual = ManualActivation(1, null))

            assertEquals(BlockReason.Profile(2, "Études"), (decision as Decision.Blocked).reason)
        }

        @Test
        fun `strict session beats a manual allow`() {
            val manual = profile(id = 1, autoActivation = false, rules = listOf(allow(TIKTOK)))

            assertEquals(
                Decision.Blocked(BlockReason.StrictSession, at(MONDAY, 12).toInstant()),
                decide(
                    AppRequest(TIKTOK),
                    listOf(manual),
                    manual = ManualActivation(1, null),
                    strict = strictSession(TIKTOK),
                ),
            )
        }

        @Test
        fun `messaging zone stays allowed after the app limit is reached`() {
            val instagram = profile(
                id = 1,
                rules = listOf(
                    limit(INSTAGRAM, 30),
                    Rule(Target.Zone(INSTAGRAM, "messages"), RuleAction.Allow),
                ),
            )

            assertEquals(
                Decision.Allowed(null),
                decide(AppRequest(INSTAGRAM, zone = "messages", usageToday = minutes(45)), listOf(instagram)),
            )
            assertEquals(
                BlockReason.DailyLimitReached(minutes(30)),
                (decide(AppRequest(INSTAGRAM, zone = "feed", usageToday = minutes(45)), listOf(instagram))
                    as Decision.Blocked).reason,
            )
        }

        @Test
        fun `whitelisted clock stays allowed during a strict session on all apps`() {
            val lockdown = profile(id = 1, rules = listOf(allApps))
            assertEquals(
                Decision.Allowed(null),
                decide(AppRequest(CLOCK), listOf(lockdown), strict = strictSession(CLOCK)),
            )
        }
    }

    @Nested
    inner class Specificity {

        @Test
        fun `zone rule beats app rule in the same layer`() {
            val p = profile(id = 1, rules = listOf(allow(INSTAGRAM), Rule(Target.Zone(INSTAGRAM, "reels"), RuleAction.Block)))
            assertEquals(
                Decision.Blocked(BlockReason.Profile(1, "Profil 1"), null),
                decide(AppRequest(INSTAGRAM, zone = "reels"), listOf(p)).withoutUntil(),
            )
            assertEquals(Decision.Allowed(null), decide(AppRequest(INSTAGRAM, zone = "messages"), listOf(p)))
        }

        @Test
        fun `default blocked zone can be allowed by a higher profile`() {
            val shorts = Rule(Target.Zone(YOUTUBE, "shorts"), RuleAction.Block)
            val request = AppRequest(YOUTUBE, zone = "shorts")
            assertEquals(
                BlockReason.GlobalRule,
                (decide(request, globalRules = listOf(shorts)) as Decision.Blocked).reason,
            )
            val evening = profile(id = 1, rules = listOf(Rule(Target.Zone(YOUTUBE, "shorts"), RuleAction.Allow)))
            assertEquals(Decision.Allowed(null), decide(request, listOf(evening), listOf(shorts)))
            assertEquals(Decision.Allowed(null), decide(AppRequest(YOUTUBE, zone = null), globalRules = listOf(shorts)))
        }

        @Test
        fun `zone rules are ignored when the zone is unknown`() {
            val p = profile(id = 1, rules = listOf(Rule(Target.Zone(INSTAGRAM, "reels"), RuleAction.Block)))
            assertEquals(Decision.Allowed(null), decide(AppRequest(INSTAGRAM, zone = null), listOf(p)))
        }

        @Test
        fun `app rule beats all-apps rule in the same layer`() {
            val p = profile(id = 1, rules = listOf(allApps, limit(YOUTUBE, 20)))
            assertEquals(Decision.Allowed(minutes(20)), decide(AppRequest(YOUTUBE), listOf(p)))
        }

        @Test
        fun `same specificity keeps the strictest rule`() {
            val p = profile(id = 1, rules = listOf(allow(YOUTUBE), limit(YOUTUBE, 50), limit(YOUTUBE, 15)))
            assertEquals(Decision.Allowed(minutes(15)), decide(AppRequest(YOUTUBE), listOf(p)))
        }

        @Test
        fun `layer without matching rule is skipped`() {
            val higher = profile(id = 1, priority = 10, rules = listOf(block(TIKTOK)))
            val lower = profile(id = 2, priority = 1, name = "Bas", rules = listOf(block(YOUTUBE)))
            assertEquals(
                BlockReason.Profile(2, "Bas"),
                (decide(AppRequest(YOUTUBE), listOf(higher, lower)) as Decision.Blocked).reason,
            )
        }
    }

    @Nested
    inner class Limits {

        @Test
        fun `shortest limit across layers wins`() {
            val a = profile(id = 1, priority = 10, rules = listOf(limit(YOUTUBE, 60)))
            val b = profile(id = 2, priority = 5, rules = listOf(limit(YOUTUBE, 20)))
            assertEquals(
                Decision.Allowed(minutes(15)),
                decide(AppRequest(YOUTUBE, usageToday = minutes(5)), listOf(a, b)),
            )
        }

        @Test
        fun `limit reached exactly blocks until next midnight`() {
            val p = profile(id = 1, rules = listOf(limit(YOUTUBE, 30)))
            assertEquals(
                Decision.Blocked(BlockReason.DailyLimitReached(minutes(30)), at(MONDAY.plusDays(1), 0).toInstant()),
                decide(AppRequest(YOUTUBE, usageToday = minutes(30)), listOf(p)),
            )
        }

        @Test
        fun `one minute before the limit is still allowed`() {
            val p = profile(id = 1, rules = listOf(limit(YOUTUBE, 30)))
            assertEquals(
                Decision.Allowed(minutes(1)),
                decide(AppRequest(YOUTUBE, usageToday = minutes(29)), listOf(p)),
            )
        }

        @Test
        fun `global limit applies when no profile is active`() {
            assertEquals(
                Decision.Allowed(minutes(40)),
                decide(AppRequest(TIKTOK, usageToday = minutes(5)), globalRules = listOf(limit(TIKTOK, 45))),
            )
        }

        @Test
        fun `profile allow shields from a global block`() {
            val p = profile(id = 1, rules = listOf(allow(TIKTOK)))
            assertEquals(Decision.Allowed(null), decide(AppRequest(TIKTOK), listOf(p), listOf(block(TIKTOK))))
        }
    }

    @Nested
    inner class BlockEnd {

        @Test
        fun `global block has no known end`() {
            assertEquals(
                Decision.Blocked(BlockReason.GlobalRule, null),
                decide(AppRequest(TIKTOK), globalRules = listOf(block(TIKTOK))),
            )
        }

        @Test
        fun `block lasts until the latest blocking layer ends`() {
            val study = profile(id = 1, rules = listOf(block(TIKTOK)), schedules = listOf(schedule(time(9), time(18))))
            val decision = decide(AppRequest(TIKTOK), listOf(study), strict = strictSession(TIKTOK))

            assertEquals(Decision.Blocked(BlockReason.StrictSession, at(MONDAY, 18).toInstant()), decision)
        }

        @Test
        fun `manual block without end has no known end`() {
            val p = profile(id = 1, autoActivation = false, rules = listOf(block(TIKTOK)))
            assertEquals(
                Decision.Blocked(BlockReason.Profile(1, "Profil 1"), null),
                decide(AppRequest(TIKTOK), listOf(p), manual = ManualActivation(1, null)),
            )
        }

        @Test
        fun `expired manual activation falls back to scheduled profiles`() {
            val manual = profile(id = 1, autoActivation = false, rules = listOf(allow(YOUTUBE)))
            val study = profile(id = 2, name = "Études", rules = listOf(block(YOUTUBE)))
            val activation = ManualActivation(1, until = at(MONDAY, 11).toInstant())

            assertEquals(
                Decision.Allowed(null),
                decide(AppRequest(YOUTUBE), listOf(manual, study), manual = activation, now = at(MONDAY, 10)),
            )
            assertEquals(
                BlockReason.Profile(2, "Études"),
                (decide(AppRequest(YOUTUBE), listOf(manual, study), manual = activation, now = at(MONDAY, 11))
                    as Decision.Blocked).reason,
            )
        }

        @Test
        fun `manual block with end uses it`() {
            val p = profile(id = 1, autoActivation = false, rules = listOf(block(TIKTOK)))
            val until = at(MONDAY, 11).toInstant()
            assertEquals(
                Decision.Blocked(BlockReason.Profile(1, "Profil 1"), until),
                decide(AppRequest(TIKTOK), listOf(p), manual = ManualActivation(1, until)),
            )
        }
    }

    private fun Decision.withoutUntil() = if (this is Decision.Blocked) copy(until = null) else this
}
