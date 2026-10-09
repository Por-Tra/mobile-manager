package com.lucas.mobilemanager.domain.profiles

import com.lucas.mobilemanager.domain.YOUTUBE
import com.lucas.mobilemanager.domain.allow
import com.lucas.mobilemanager.domain.block
import com.lucas.mobilemanager.domain.limit
import com.lucas.mobilemanager.domain.model.Schedule
import com.lucas.mobilemanager.domain.profile
import com.lucas.mobilemanager.domain.profiles.ProfileValidator.Error
import com.lucas.mobilemanager.domain.schedule
import com.lucas.mobilemanager.domain.time
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ProfileValidatorTest {

    private val valid = profile(id = 1, name = "Soirée", schedules = listOf(schedule(time(18), time(23))), rules = listOf(block(YOUTUBE)))

    private fun errors(p: com.lucas.mobilemanager.domain.model.Profile, others: List<String> = listOf("Sommeil")) =
        ProfileValidator.validate(p, others)

    @Test
    fun `valid automatic profile`() = assertEquals(emptySet<Error>(), errors(valid))

    @Test
    fun `manual-only profile needs no schedule`() =
        assertEquals(emptySet<Error>(), errors(valid.copy(autoActivation = false, schedules = emptyList())))

    @Test
    fun `blank name is refused`() = assertEquals(setOf(Error.NAME_EMPTY), errors(valid.copy(name = "  ")))

    @Test
    fun `name already used is refused, ignoring case and spaces`() =
        assertEquals(setOf(Error.NAME_TAKEN), errors(valid.copy(name = " sommeil ")))

    @Test
    fun `automatic profile without schedule is refused`() =
        assertEquals(setOf(Error.NO_SCHEDULE), errors(valid.copy(schedules = emptyList())))

    @Test
    fun `schedule without days is refused`() =
        assertEquals(
            setOf(Error.SCHEDULE_WITHOUT_DAYS),
            errors(valid.copy(schedules = listOf(Schedule(emptySet(), time(9), time(10))))),
        )

    @Test
    fun `profile without rules is refused`() = assertEquals(setOf(Error.NO_RULES), errors(valid.copy(rules = emptyList())))

    @Test
    fun `limits must stay between 1 and 600 minutes`() {
        assertEquals(setOf(Error.LIMIT_OUT_OF_RANGE), errors(valid.copy(rules = listOf(limit(YOUTUBE, 0)))))
        assertEquals(setOf(Error.LIMIT_OUT_OF_RANGE), errors(valid.copy(rules = listOf(limit(YOUTUBE, 601)))))
        assertEquals(emptySet<Error>(), errors(valid.copy(rules = listOf(limit(YOUTUBE, 600)))))
    }

    @Test
    fun `same app set twice is refused`() =
        assertEquals(setOf(Error.DUPLICATE_TARGET), errors(valid.copy(rules = listOf(block(YOUTUBE), allow(YOUTUBE)))))

    @Test
    fun `global rules may be empty`() = assertEquals(emptySet<Error>(), ProfileValidator.validateRules(emptyList()))
}
