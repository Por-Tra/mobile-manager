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

    /** « 45 min », « 1 h », « 1 h 30 ». */
    fun duration(duration: Duration): String {
        val hours = duration.toHours()
        val minutes = duration.toMinutes() % 60 // toMinutesPart() exige l'API 31
        return when {
            hours == 0L -> "$minutes min"
            minutes == 0L -> "$hours h"
            else -> "$hours h %02d".format(minutes)
        }
    }
}
