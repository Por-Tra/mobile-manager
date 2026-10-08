package com.lucas.mobilemanager.data.db

import com.lucas.mobilemanager.domain.model.Profile
import com.lucas.mobilemanager.domain.model.Schedule
import com.lucas.mobilemanager.domain.rules.Rule
import com.lucas.mobilemanager.domain.rules.RuleAction
import com.lucas.mobilemanager.domain.rules.Target
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime

fun ProfileWithDetails.toDomain() = Profile(
    id = profile.id,
    name = profile.name,
    priority = profile.priority,
    schedules = schedules.sortedBy { it.id }.map { it.toDomain() },
    rules = rules.sortedBy { it.id }.map { it.toDomain() },
    autoActivation = profile.autoActivation,
    enabled = profile.enabled,
)

fun Profile.toEntity() = ProfileEntity(
    id = id,
    name = name,
    priority = priority,
    autoActivation = autoActivation,
    enabled = enabled,
)

fun ProfileScheduleEntity.toDomain() = Schedule(
    days = DayOfWeek.entries.filter { daysMask and (1 shl it.ordinal) != 0 }.toSet(),
    start = LocalTime.ofSecondOfDay(startMinute * 60L),
    end = LocalTime.ofSecondOfDay(endMinute * 60L),
)

fun Schedule.toEntity(profileId: Long) = ProfileScheduleEntity(
    profileId = profileId,
    daysMask = days.fold(0) { mask, day -> mask or (1 shl day.ordinal) },
    startMinute = start.toSecondOfDay() / 60,
    endMinute = end.toSecondOfDay() / 60,
)

fun RuleEntity.toDomain(): Rule {
    val target = when (targetType) {
        TARGET_ALL_APPS -> Target.AllApps
        TARGET_APP -> Target.App(requireNotNull(packageName))
        TARGET_ZONE -> Target.Zone(requireNotNull(packageName), requireNotNull(zone))
        else -> error("Unknown rule target type: $targetType")
    }
    val action = when (action) {
        ACTION_ALLOW -> RuleAction.Allow
        ACTION_LIMIT -> RuleAction.Limit(Duration.ofMinutes(requireNotNull(dailyLimitMinutes)))
        ACTION_BLOCK -> RuleAction.Block
        else -> error("Unknown rule action: $action")
    }
    return Rule(target, action)
}

fun Rule.toEntity(profileId: Long?): RuleEntity {
    val (type, packageName, zone) = when (val t = target) {
        Target.AllApps -> Triple(TARGET_ALL_APPS, null, null)
        is Target.App -> Triple(TARGET_APP, t.packageName, null)
        is Target.Zone -> Triple(TARGET_ZONE, t.packageName, t.zone)
    }
    val (actionName, limitMinutes) = when (val a = action) {
        RuleAction.Allow -> ACTION_ALLOW to null
        is RuleAction.Limit -> ACTION_LIMIT to a.dailyLimit.toMinutes()
        RuleAction.Block -> ACTION_BLOCK to null
    }
    return RuleEntity(
        profileId = profileId,
        targetType = type,
        packageName = packageName,
        zone = zone,
        action = actionName,
        dailyLimitMinutes = limitMinutes,
    )
}

private const val TARGET_ALL_APPS = "ALL_APPS"
private const val TARGET_APP = "APP"
private const val TARGET_ZONE = "ZONE"
private const val ACTION_ALLOW = "ALLOW"
private const val ACTION_LIMIT = "LIMIT"
private const val ACTION_BLOCK = "BLOCK"
