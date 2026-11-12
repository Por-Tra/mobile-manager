package com.lucas.mobilemanager.domain.zones

/** Reconnaît le fil recommandé et le fil des comptes suivis sans lire les publications. */
object InstagramFeedPolicy {
    const val PACKAGE = "com.instagram.android"

    enum class Surface { OTHER, RECOMMENDED, FOLLOWING }

    val followingLabels = setOf("Suivi(e)", "Abonnements", "Following")

    val viewIds: Set<String> = setOf(
        "sticky_header_list", "main_feed_action_bar", "tab_bar", "feed_tab", "direct_tab",
        "action_bar_title", "layout_listview_parent_container", "direct_inbox_action_bar",
        "reel_viewer_root", "clips_viewer_view_pager", "root_clips_layout",
    ).mapTo(mutableSetOf()) { "$PACKAGE:id/$it" } + "android:id/list"

    fun classify(nodes: List<UiNode>): Surface {
        fun has(id: String) = nodes.any { it.visible && it.viewId.isViewId(id) }
        if (listOf("direct_inbox_action_bar", "reel_viewer_root", "clips_viewer_view_pager", "root_clips_layout").any(::has)) {
            return Surface.OTHER
        }
        // Un libellé dans une publication ne prouve pas l'abonnement à son auteur.
        val followingTitle = nodes.any {
            it.visible && it.viewId.isViewId("action_bar_title") &&
                followingLabels.any { label -> label.equals(it.text?.trim(), ignoreCase = true) }
        }
        if (followingTitle && has("layout_listview_parent_container") && has("android:id/list") && !has("sticky_header_list")) {
            return Surface.FOLLOWING
        }
        return if (has("sticky_header_list") && has("feed_tab") && has("tab_bar")) Surface.RECOMMENDED else Surface.OTHER
    }
}
