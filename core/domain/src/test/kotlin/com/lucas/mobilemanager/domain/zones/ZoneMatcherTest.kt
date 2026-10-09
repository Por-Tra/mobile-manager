package com.lucas.mobilemanager.domain.zones

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

class ZoneMatcherTest {

    private val youtube = "com.google.android.youtube"
    private val instagram = "com.instagram.android"

    /** Le fichier réellement livré avec l'app (testé contre de vraies captures d'écran). */
    private val shipped: ZoneDetectorFile by lazy {
        val text = File("../../app/src/main/assets/zone_detectors.json").readText()
        (ZoneDetectorParser.parse(text) as ZoneDetectorParser.Result.Valid).file
    }

    private val json = Json { ignoreUnknownKeys = true }

    private fun fixture(name: String): List<UiNode> {
        val text = javaClass.getResource("/zones/$name.json")!!.readText()
        return json.decodeFromString(ListSerializer(UiNode.serializer()), text)
    }

    @Nested
    inner class YouTubeRealCaptures {
        @Test
        fun `shorts tab is detected`() = assertEquals("shorts", ZoneMatcher.detect(shipped, youtube, fixture("youtube_shorts"))?.zone?.id)

        @Test
        fun `short opened from a link is detected`() =
            assertEquals("shorts", ZoneMatcher.detect(shipped, youtube, fixture("youtube_short_link"))?.zone?.id)

        @Test
        fun `home is not a zone`() = assertNull(ZoneMatcher.detect(shipped, youtube, fixture("youtube_home")))

        @Test
        fun `regular video is not a zone, even with a reel_time_bar`() {
            val video = fixture("youtube_video")
            assertTrue(video.any { it.viewId?.endsWith("reel_time_bar") == true })
            assertNull(ZoneMatcher.detect(shipped, youtube, video))
        }

        @Test
        fun `exit clicks the home tab`() {
            val nodes = fixture("youtube_shorts")
            val step = ZoneExitPlanner.plan(shipped.zonesFor(youtube).single(), nodes)
            assertEquals("Home", nodes[(step as ExitStep.Click).nodeIndex].desc)
        }

        @Test
        fun `shorts tab is the entry point to hide`() {
            val nodes = fixture("youtube_home")
            val hidden = ZoneMatcher.entryPoints(shipped.zonesFor(youtube).single(), nodes)
            assertEquals(listOf("Shorts"), hidden.map { nodes[it].desc })
        }
    }

    /** Captures anonymisées d'Instagram 451 sur le téléphone (identifiants et onglets seulement). */
    @Nested
    inner class InstagramRealCaptures {
        private val reels get() = shipped.zonesFor(instagram).single()

        @Test
        fun `reels tab is detected`() = assertEquals("reels", ZoneMatcher.detect(shipped, instagram, fixture("instagram_reels"))?.zone?.id)

        @Test
        fun `home feed is not a zone`() = assertNull(ZoneMatcher.detect(shipped, instagram, fixture("instagram_home")))

        @Test
        fun `direct messages are not a zone`() = assertNull(ZoneMatcher.detect(shipped, instagram, fixture("instagram_direct")))

        @Test
        fun `reels kept in memory off screen are ignored`() {
            // Instagram garde l'onglet Reels visité dans l'arbre, invisible, derrière la messagerie.
            val offscreen = fixture("instagram_reels").map { it.copy(visible = false) }
            assertNull(ZoneMatcher.detect(shipped, instagram, fixture("instagram_direct") + offscreen))
        }

        @Test
        fun `reels button is the entry point to hide`() {
            for (screen in listOf("instagram_home", "instagram_direct", "instagram_reels")) {
                val nodes = fixture(screen)
                val hidden = ZoneMatcher.entryPoints(reels, nodes)
                assertEquals(listOf("$instagram:id/clips_tab"), hidden.map { nodes[it].viewId }, screen)
            }
        }

        @Test
        fun `exit goes back first, then to the home tab`() {
            val nodes = fixture("instagram_reels")
            assertEquals(ExitStep.Back, ZoneExitPlanner.plan(reels, nodes, attempt = 0))
            val second = ZoneExitPlanner.plan(reels, nodes, attempt = 1)
            assertEquals("$instagram:id/feed_tab", nodes[(second as ExitStep.Click).nodeIndex].viewId)
            assertEquals(second, ZoneExitPlanner.plan(reels, nodes, attempt = 5))
        }
    }

    @Nested
    inner class InstagramSynthetic {
        private fun ig(vararg nodes: UiNode) = nodes.toList()
        private fun id(name: String, selected: Boolean = false) = UiNode(viewId = "$instagram:id/$name", selected = selected)

        private val tabs = arrayOf(
            id("feed_tab"), id("search_tab"), id("clips_tab"), id("profile_tab"),
        )

        @Test
        fun `reels viewer is detected`() =
            assertEquals("reels", ZoneMatcher.detect(shipped, instagram, ig(id("clips_viewer_view_pager"), *tabs))?.zone?.id)

        @Test
        fun `reels tab selected is detected`() = assertNotNull(
            ZoneMatcher.detect(shipped, instagram, ig(id("feed_tab"), id("clips_tab", selected = true))),
        )

        @Test
        fun `invisible reels viewer is ignored`() = assertNull(
            ZoneMatcher.detect(shipped, instagram, ig(UiNode(viewId = "$instagram:id/clips_viewer_view_pager", visible = false), *tabs)),
        )

        @Test
        fun `stories are not reels`() = assertNull(
            ZoneMatcher.detect(shipped, instagram, ig(id("reel_viewer_root"), id("clips_viewer_view_pager"), *tabs)),
        )

        @Test
        fun `home feed with an unselected reels tab is not a zone`() =
            assertNull(ZoneMatcher.detect(shipped, instagram, ig(id("feed_tab", selected = true), *tabs)))

        @Test
        fun `direct messages are not a zone`() =
            assertNull(ZoneMatcher.detect(shipped, instagram, ig(id("direct_inbox_container"), UiNode(text = "Messages"))))
    }

    @Nested
    inner class Signals {
        private val zone = ZoneDefinition(
            id = "z",
            label = "Z",
            anyOf = listOf(NodeSignal(desc = listOf("Accueil", "Home"), selected = true)),
            exit = listOf(ExitAction(clickText = listOf("Retour")), ExitAction(back = true)),
        )
        private val file = ZoneDetectorFile(1, listOf(AppZones("pkg", listOf(zone))))

        @Test
        fun `all conditions must hold on the same node`() {
            assertNull(ZoneMatcher.detect(file, "pkg", listOf(UiNode(desc = "Home"), UiNode(selected = true))))
            assertNotNull(ZoneMatcher.detect(file, "pkg", listOf(UiNode(desc = "Home", selected = true))))
        }

        @Test
        fun `text comparison ignores case and accepts language variants`() =
            assertNotNull(ZoneMatcher.detect(file, "pkg", listOf(UiNode(desc = "ACCUEIL", selected = true))))

        @Test
        fun `other packages are ignored`() =
            assertNull(ZoneMatcher.detect(file, "other", listOf(UiNode(desc = "Home", selected = true))))

        @Test
        fun `match reports the signal and nodes`() {
            val match = ZoneMatcher.detect(file, "pkg", listOf(UiNode(text = "x"), UiNode(desc = "Home", selected = true)))
            assertEquals(ZoneMatch(zone, 0, listOf(1)), match)
        }

        @Test
        fun `exit falls back to back when nothing is clickable`() =
            assertEquals(ExitStep.Back, ZoneExitPlanner.plan(zone, listOf(UiNode(text = "Autre"))))

        @Test
        fun `exit never clicks an invisible node`() =
            assertEquals(ExitStep.Back, ZoneExitPlanner.plan(zone, listOf(UiNode(text = "Retour", visible = false))))

        @Test
        fun `invisible nodes never match`() =
            assertNull(ZoneMatcher.detect(file, "pkg", listOf(UiNode(desc = "Home", selected = true, visible = false))))

        @Test
        fun `short view id matches the full resource name`() =
            assertTrue("com.app:id/reel_recycler".isViewId("reel_recycler") && !"com.app:id/reel_recycler2".isViewId("reel_recycler"))
    }
}
