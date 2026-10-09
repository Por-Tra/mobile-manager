package com.lucas.mobilemanager.ui.format

import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Formats d'affichage partagés par les écrans (heures et durées, en français). */
object Formats {
    val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.FRENCH)
    val DAY_AND_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM 'à' HH:mm", Locale.FRENCH)

    fun time(time: LocalTime): String = TIME.format(time)

    /** « < 1 min », « 45 min », « 1 h », « 1 h 30 » (arrondi à la minute la plus proche). */
    fun duration(duration: Duration): String {
        if (!duration.isNegative && !duration.isZero && duration < Duration.ofMinutes(1)) return "< 1 min"
        val totalMinutes = (duration.seconds.coerceAtLeast(0) + 30) / 60
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60 // toMinutesPart() exige l'API 31
        return when {
            hours == 0L -> "$minutes min"
            minutes == 0L -> "$hours h"
            else -> "$hours h %02d".format(minutes)
        }
    }
}
