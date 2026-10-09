package com.lucas.mobilemanager.domain.profiles

import app.cash.turbine.test
import com.lucas.mobilemanager.domain.model.Profile
import com.lucas.mobilemanager.domain.model.UserPreferences
import com.lucas.mobilemanager.domain.repository.ProfileRepository
import com.lucas.mobilemanager.domain.repository.UserPreferencesRepository
import com.lucas.mobilemanager.domain.rules.Rule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SeedDefaultProfilesTest {

    private val profiles = FakeProfileRepository()
    private val preferences = FakePreferencesRepository()
    private val seed = SeedDefaultProfiles(profiles, preferences)

    @Test
    fun `seeds the default profiles, all disabled`() = runTest {
        profiles.observeProfiles().test {
            assertEquals(emptyList<Profile>(), awaitItem())

            seed()

            val seeded = expectMostRecentItem()
            assertEquals(listOf("Sommeil", "Études", "Temps libre"), seeded.map { it.name })
            assertTrue(seeded.none { it.enabled })
        }
    }

    @Test
    fun `seeds only once, even after the user deleted the profiles`() = runTest {
        seed()
        profiles.observeProfiles().test {
            awaitItem().forEach { profiles.delete(it.id) }
            assertEquals(emptyList<Profile>(), expectMostRecentItem())

            seed()

            expectNoEvents()
        }
    }

    private class FakeProfileRepository : ProfileRepository {
        private val state = MutableStateFlow<List<Profile>>(emptyList())
        private var nextId = 1L

        override fun observeProfiles(): Flow<List<Profile>> = state
        override fun observeGlobalRules(): Flow<List<Rule>> = state.map { emptyList() }

        override suspend fun upsert(profile: Profile): Long {
            val id = if (profile.id == 0L) nextId++ else profile.id
            state.update { list -> list.filterNot { it.id == id } + profile.copy(id = id) }
            return id
        }

        override suspend fun delete(profileId: Long) = state.update { list -> list.filterNot { it.id == profileId } }
        override suspend fun setEnabled(profileId: Long, enabled: Boolean) =
            state.update { list -> list.map { if (it.id == profileId) it.copy(enabled = enabled) else it } }

        override suspend fun replaceGlobalRules(rules: List<Rule>) = Unit
    }

    private class FakePreferencesRepository : UserPreferencesRepository {
        private val state = MutableStateFlow(UserPreferences())
        override val preferences: Flow<UserPreferences> = state
        override suspend fun setOnboardingCompleted(completed: Boolean) =
            state.update { it.copy(onboardingCompleted = completed) }
        override suspend fun setDefaultProfilesSeeded(seeded: Boolean) =
            state.update { it.copy(defaultProfilesSeeded = seeded) }
        override suspend fun setDefaultScrollGuardsSeeded(seeded: Boolean) =
            state.update { it.copy(defaultScrollGuardsSeeded = seeded) }
        override suspend fun setDefaultZoneRulesSeeded(seeded: Boolean) =
            state.update { it.copy(defaultZoneRulesSeeded = seeded) }
    }
}
