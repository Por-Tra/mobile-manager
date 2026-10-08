package com.lucas.mobilemanager.domain

import com.lucas.mobilemanager.domain.model.Profile
import com.lucas.mobilemanager.domain.model.Schedule
import com.lucas.mobilemanager.domain.rules.Rule
import com.lucas.mobilemanager.domain.rules.RuleAction
import com.lucas.mobilemanager.domain.rules.Target
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

val PARIS: ZoneId = ZoneId.of("Europe/Paris")

/** Lundi 12 octobre 2026 (heure d'été). */
val MONDAY: LocalDate = LocalDate.of(2026, 10, 12)

/** Passage à l'heure d'été : 02:00 → 03:00. */
val SPRING_FORWARD: LocalDate = LocalDate.of(2026, 3, 29)

/** Passage à l'heure d'hiver : 03:00 → 02:00. */
val FALL_BACK: LocalDate = LocalDate.of(2026, 10, 25)

const val INSTAGRAM = "com.instagram.android"
const val TIKTOK = "com.zhiliaoapp.musically"
const val YOUTUBE = "com.google.android.youtube"
const val DIALER = "com.android.dialer"
const val CLOCK = "com.android.deskclock"

fun at(date: LocalDate, hour: Int, minute: Int = 0): ZonedDateTime =
    ZonedDateTime.of(LocalDateTime.of(date, LocalTime.of(hour, minute)), PARIS)

fun time(hour: Int, minute: Int = 0): LocalTime = LocalTime.of(hour, minute)

fun schedule(start: LocalTime, end: LocalTime, vararg days: DayOfWeek): Schedule =
    Schedule(if (days.isEmpty()) Schedule.EVERY_DAY else days.toSet(), start, end)

fun block(packageName: String) = Rule(Target.App(packageName), RuleAction.Block)
fun allow(packageName: String) = Rule(Target.App(packageName), RuleAction.Allow)
fun limit(packageName: String, minutes: Long) =
    Rule(Target.App(packageName), RuleAction.Limit(Duration.ofMinutes(minutes)))

fun profile(
    id: Long,
    name: String = "Profil $id",
    priority: Int = 0,
    schedules: List<Schedule> = listOf(schedule(time(0), time(0))),
    rules: List<Rule> = emptyList(),
    autoActivation: Boolean = true,
    enabled: Boolean = true,
) = Profile(id, name, priority, schedules, rules, autoActivation, enabled)
