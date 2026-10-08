package com.lucas.mobilemanager.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.lucas.mobilemanager.R
import com.lucas.mobilemanager.domain.model.Profile
import com.lucas.mobilemanager.domain.model.Schedule
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** « Tous les jours · 00:00–06:00 · 1 règle » */
@Composable
fun profileSummary(profile: Profile): String {
    val schedules = if (profile.autoActivation) {
        profile.schedules.map { scheduleText(it) }
    } else {
        listOf(stringResource(R.string.profile_manual_only))
    }
    val rules = pluralStringResource(R.plurals.profile_rules_count, profile.rules.size, profile.rules.size)
    return (schedules + rules).joinToString(" · ")
}

@Composable
fun scheduleText(schedule: Schedule): String {
    val hours = if (schedule.start == schedule.end) {
        stringResource(R.string.schedule_all_day)
    } else {
        "${Formats.time(schedule.start)}–${Formats.time(schedule.end)}"
    }
    return "${daysText(schedule.days)} $hours"
}

@Composable
private fun daysText(days: Set<DayOfWeek>): String = when (days) {
    Schedule.EVERY_DAY -> stringResource(R.string.days_every_day)
    Schedule.WEEKDAYS -> stringResource(R.string.days_weekdays)
    setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) -> stringResource(R.string.days_weekend)
    else -> days.sorted()
        .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.FRENCH) }
        .replaceFirstChar { it.titlecase(Locale.FRENCH) }
}
