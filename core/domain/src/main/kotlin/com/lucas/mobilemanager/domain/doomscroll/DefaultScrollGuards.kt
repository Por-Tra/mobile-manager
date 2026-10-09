package com.lucas.mobilemanager.domain.doomscroll

import java.time.Duration

/**
 * Réglages proposés à l'installation, actifs d'emblée (modifiables dans les Réglages).
 * TikTok, entièrement basé sur le défilement, a un régime strict. Les autres apps n'ont
 * qu'un écran de pause : leur messagerie doit rester utilisable (zones en phase 7).
 */
object DefaultScrollGuards {

    private val TIKTOK = listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")

    private val PAUSE_ONLY = listOf(
        "com.instagram.android",
        "com.google.android.youtube",
        "com.facebook.katana",
        "com.snapchat.android",
        "com.twitter.android",
        "com.reddit.frontpage",
    )

    val ALL: List<ScrollGuard> =
        TIKTOK.map {
            ScrollGuard(
                packageName = it,
                pauseEvery = Duration.ofMinutes(5),
                sessionLimit = Duration.ofMinutes(10),
                cooldown = Duration.ofMinutes(15),
                dailyLimit = Duration.ofMinutes(30),
            )
        } + PAUSE_ONLY.map { ScrollGuard(packageName = it, pauseEvery = Duration.ofMinutes(15)) }
}
