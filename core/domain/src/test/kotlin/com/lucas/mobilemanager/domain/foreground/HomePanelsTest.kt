package com.lucas.mobilemanager.domain.foreground

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomePanelsTest {

    private val google = "com.google.android.googlequicksearchbox"

    @Test
    fun `pixel launcher google feed is a home panel`() = assertTrue(
        HomePanels.isHomePanel(google, "com.google.android.apps.search.googleapp.launcher.minusone.activity.MinusOneActivity"),
    )

    @Test
    fun `google search app itself is not a home panel`() = assertFalse(
        HomePanels.isHomePanel(google, "com.google.android.apps.search.googleapp.activity.GoogleAppActivity"),
    )

    @Test
    fun `same class fragment in another package is not a home panel`() =
        assertFalse(HomePanels.isHomePanel("com.example.app", "com.example.launcher.minusone.Feed"))

    @Test
    fun `missing class name is not a home panel`() = assertFalse(HomePanels.isHomePanel(google, null))
}
