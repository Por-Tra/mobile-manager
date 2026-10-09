package com.lucas.mobilemanager.domain.profiles

import com.lucas.mobilemanager.domain.profile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProfileOrderingTest {

    // Ordre affiché : Sommeil (100), Études (50), Temps libre (10).
    private val displayed = listOf(
        profile(id = 1, name = "Sommeil", priority = 100),
        profile(id = 2, name = "Études", priority = 50),
        profile(id = 3, name = "Temps libre", priority = 10),
    )

    private fun applied(changes: List<com.lucas.mobilemanager.domain.model.Profile>) =
        ProfileOrdering.sorted(displayed.map { p -> changes.firstOrNull { it.id == p.id } ?: p }).map { it.name }

    @Test
    fun `sorted follows priority then id`() {
        val tie = listOf(profile(id = 5, priority = 10), profile(id = 4, priority = 10), profile(id = 6, priority = 20))
        assertEquals(listOf(6L, 4L, 5L), ProfileOrdering.sorted(tie).map { it.id })
    }

    @Test
    fun `moving up puts the profile first`() =
        assertEquals(listOf("Temps libre", "Sommeil", "Études"), applied(ProfileOrdering.reorder(displayed, 2, 0)))

    @Test
    fun `moving down swaps with the next one`() =
        assertEquals(listOf("Études", "Sommeil", "Temps libre"), applied(ProfileOrdering.reorder(displayed, 0, 1)))

    @Test
    fun `priorities become strictly decreasing and spaced`() {
        val all = ProfileOrdering.reorder(displayed, 0, 2) + displayed
        val result = ProfileOrdering.sorted(displayed.map { p -> all.first { it.id == p.id } })
        assertEquals(listOf(30, 20, 10), result.map { it.priority })
    }

    @Test
    fun `only changed profiles are returned`() {
        val already = listOf(profile(id = 1, priority = 30), profile(id = 2, priority = 20), profile(id = 3, priority = 10))
        assertEquals(listOf(1L, 2L), ProfileOrdering.reorder(already, 0, 1).map { it.id }.sorted())
    }

    @Test
    fun `moving to the same place changes nothing on a normalized list`() {
        val already = listOf(profile(id = 1, priority = 20), profile(id = 2, priority = 10))
        assertTrue(ProfileOrdering.reorder(already, 1, 1).isEmpty())
    }

    @Test
    fun `new profile goes to the bottom`() {
        assertEquals(0, ProfileOrdering.nextPriority(displayed))
        assertEquals(100, ProfileOrdering.nextPriority(emptyList()))
    }
}
