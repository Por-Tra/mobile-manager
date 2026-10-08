package com.lucas.mobilemanager.domain.profiles

import com.lucas.mobilemanager.domain.model.Profile
import com.lucas.mobilemanager.domain.model.Schedule
import com.lucas.mobilemanager.domain.rules.Rule
import com.lucas.mobilemanager.domain.rules.RuleAction
import com.lucas.mobilemanager.domain.rules.Target
import java.time.Duration
import java.time.LocalTime

/** Profils proposés au premier lancement. Tous désactivés : rien ne bloque sans action explicite. */
object DefaultProfiles {

    /** Applications distrayantes courantes. Modifiables dans l'UI des profils (phase 5). */
    val DISTRACTING_APPS = listOf(
        "com.instagram.android",
        "com.zhiliaoapp.musically", // TikTok
        "com.ss.android.ugc.trill", // TikTok (variante régionale)
        "com.google.android.youtube",
        "com.facebook.katana",
        "com.snapchat.android",
        "com.twitter.android",
        "com.reddit.frontpage",
    )

    val SLEEP = Profile(
        name = "Sommeil",
        priority = 100,
        schedules = listOf(Schedule(Schedule.EVERY_DAY, LocalTime.MIDNIGHT, LocalTime.of(6, 0))),
        rules = listOf(Rule(Target.AllApps, RuleAction.Block)),
        enabled = false,
    )

    val STUDY = Profile(
        name = "Études",
        priority = 50,
        schedules = listOf(Schedule(Schedule.WEEKDAYS, LocalTime.of(9, 0), LocalTime.of(18, 0))),
        rules = DISTRACTING_APPS.map { Rule(Target.App(it), RuleAction.Block) },
        enabled = false,
    )

    val FREE_TIME = Profile(
        name = "Temps libre",
        priority = 10,
        schedules = listOf(Schedule(Schedule.EVERY_DAY, LocalTime.of(18, 0), LocalTime.MIDNIGHT)),
        rules = DISTRACTING_APPS.map { Rule(Target.App(it), RuleAction.Limit(Duration.ofMinutes(30))) },
        enabled = false,
    )

    val ALL = listOf(SLEEP, STUDY, FREE_TIME)
}
