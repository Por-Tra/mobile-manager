package com.lucas.mobilemanager.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lucas.mobilemanager.domain.dashboard.DayTotal
import java.time.Duration
import java.time.format.TextStyle
import java.util.Locale

/**
 * Barres des 7 derniers jours, aujourd'hui mis en avant. Si un objectif est défini : ligne
 * pointillée, et les jours au-delà en couleur neutre (jamais de rouge).
 */
@Composable
fun WeekChart(week: List<DayTotal>, goal: Duration?, description: String, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val past = MaterialTheme.colorScheme.primaryContainer
    val overGoal = MaterialTheme.colorScheme.tertiary
    val goalLine = MaterialTheme.colorScheme.onSurfaceVariant
    val maxMinutes = (week.maxOfOrNull { it.total.toMinutes() } ?: 0L)
        .coerceAtLeast(goal?.toMinutes() ?: 0L)
        .coerceAtLeast(30L) * 1.1f

    Column(modifier.semantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val slot = size.width / week.size
            val barWidth = slot * 0.55f
            week.forEachIndexed { index, day ->
                val height = size.height * (day.total.toMinutes() / maxMinutes)
                val color = when {
                    goal != null && day.total > goal -> overGoal
                    index == week.lastIndex -> primary
                    else -> past
                }
                drawRoundRect(
                    color = color,
                    topLeft = Offset(slot * index + (slot - barWidth) / 2, size.height - height),
                    size = Size(barWidth, height),
                    cornerRadius = CornerRadius(6.dp.toPx()),
                )
            }
            goal?.let {
                val y = size.height - size.height * (it.toMinutes() / maxMinutes)
                drawLine(
                    color = goalLine,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            week.forEachIndexed { index, day ->
                Text(
                    day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.FRENCH).take(3),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (index == week.lastIndex) primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
