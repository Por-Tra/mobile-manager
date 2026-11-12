package com.lucas.mobilemanager.domain.zones

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InstagramFeedPolicyTest {
    private val pkg = InstagramFeedPolicy.PACKAGE
    private val json = Json { ignoreUnknownKeys = true }
    private fun id(name: String, text: String? = null, visible: Boolean = true) = UiNode(
        viewId = if (":id/" in name) name else "$pkg:id/$name", text = text, visible = visible,
    )
    private fun capture(name: String): List<UiNode> = json.decodeFromString(
        ListSerializer(UiNode.serializer()), javaClass.getResource("/zones/$name.json")!!.readText(),
    )

    @Test
    fun `recommended home is covered with photos as well as reels`() {
        for (fixture in listOf("instagram_home", "instagram_home_reel")) {
            assertEquals(InstagramFeedPolicy.Surface.RECOMMENDED, InstagramFeedPolicy.classify(capture(fixture)))
        }
    }

    @Test
    fun `following feed remains accessible after scrolling`() {
        for (fixture in listOf("instagram_following", "instagram_following_reel")) {
            val nodes = capture(fixture)
            assertEquals(InstagramFeedPolicy.Surface.FOLLOWING, InstagramFeedPolicy.classify(nodes))
            assertEquals(InstagramFeedPolicy.Surface.FOLLOWING, InstagramFeedPolicy.classify(nodes + id("video_container")))
        }
    }

    @Test
    fun `messages stories and fullscreen reels are separate surfaces`() {
        for (fixture in listOf("instagram_direct", "instagram_story", "instagram_reels")) {
            assertEquals(InstagramFeedPolicy.Surface.OTHER, InstagramFeedPolicy.classify(capture(fixture)))
        }
    }

    @Test
    fun `a follow button or text in a post never grants the following exception`() {
        val home = capture("instagram_home_reel")
        for (text in listOf("Suivi(e)", "Following", "Abonnements")) {
            assertEquals(InstagramFeedPolicy.Surface.RECOMMENDED, InstagramFeedPolicy.classify(home + UiNode(text = text)))
            assertEquals(InstagramFeedPolicy.Surface.RECOMMENDED, InstagramFeedPolicy.classify(home + id("action_bar_title", text)))
        }
    }

    @Test
    fun `following requires its title and its own visible list`() {
        for (text in InstagramFeedPolicy.followingLabels) {
            val nodes = listOf(id("action_bar_title", text), id("layout_listview_parent_container"), id("android:id/list"))
            assertEquals(InstagramFeedPolicy.Surface.FOLLOWING, InstagramFeedPolicy.classify(nodes))
            for (i in nodes.indices) {
                assertEquals(InstagramFeedPolicy.Surface.OTHER, InstagramFeedPolicy.classify(nodes.mapIndexed { index, node ->
                    if (i == index) node.copy(visible = false) else node
                }))
            }
        }
    }

    @Test
    fun `cached home behind messages is ignored`() {
        val offscreen = capture("instagram_home").map { it.copy(visible = false) }
        assertEquals(InstagramFeedPolicy.Surface.OTHER, InstagramFeedPolicy.classify(capture("instagram_direct") + offscreen))
    }

    @Test
    fun `targeted ids preserve the classification`() {
        for (fixture in listOf("instagram_home", "instagram_home_reel", "instagram_following", "instagram_following_reel", "instagram_direct", "instagram_story", "instagram_reels")) {
            val nodes = capture(fixture)
            assertEquals(InstagramFeedPolicy.classify(nodes), InstagramFeedPolicy.classify(nodes.filter { it.viewId in InstagramFeedPolicy.viewIds }), fixture)
        }
        assertTrue(InstagramFeedPolicy.viewIds.contains("android:id/list"))
    }
}
