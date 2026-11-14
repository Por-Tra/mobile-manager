package com.lucas.mobilemanager.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.lucas.mobilemanager.ui.components.entranceProgress
import com.lucas.mobilemanager.ui.theme.GraphicColors as G
import com.lucas.mobilemanager.ui.format.Formats
import com.lucas.mobilemanager.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lucas.mobilemanager.domain.dashboard.DayTotal
import java.time.Duration
import java.time.format.TextStyle

/**
 * Barres des 7 derniers jours, aujourd'hui mis en avant. Si un objectif est défini : ligne
 * pointillée, et les jours au-delà en couleur neutre (jamais de rouge).
 */
@Composable
fun WeekChart(week: List<DayTotal>, goal: Duration?, description: String, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val past = MaterialTheme.colorScheme.primaryContainer
    val grid = MaterialTheme.colorScheme.outlineVariant
    val progress = entranceProgress()
    val missing = stringResource(R.string.graphic_no_data)
    val locale = LocalConfiguration.current.locales[0]
    val accessible = description + ": " + week.joinToString("; ") {
        it.date.dayOfWeek.getDisplayName(TextStyle.FULL, locale) + ": " + if (it.hasData) Formats.duration(it.total) else missing
    }
    val overGoal = MaterialTheme.colorScheme.tertiary
    val goalLine = MaterialTheme.colorScheme.onSurfaceVariant
    val maxMinutes = (week.maxOfOrNull { it.total.toMinutes() } ?: 0L)
        .coerceAtLeast(goal?.toMinutes() ?: 0L)
        .coerceAtLeast(30L) * 1.1f

    Column(modifier.semantics { contentDescription = accessible }) {
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            if (week.isEmpty()) return@Canvas
            val slot = size.width / week.size
            val barWidth = slot * .48f
            for (line in 1..3) {
                val y = size.height * line / 3
                drawLine(grid.copy(alpha = .5f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
            }
            week.forEachIndexed { index, day ->
                val x = slot * index + (slot - barWidth) / 2
                if (index == week.lastIndex) drawRoundRect(past.copy(alpha = .45f), Offset(slot * index + 2.dp.toPx(), 0f), Size(slot - 4.dp.toPx(), size.height), CornerRadius(12.dp.toPx()))
                if (!day.hasData) {
                    drawCircle(grid, 3.dp.toPx(), Offset(slot * (index + .5f), size.height - 3.dp.toPx()), style = Stroke(1.5.dp.toPx()))
                    return@forEachIndexed
                }
                val height = (size.height - 10.dp.toPx()) * (day.total.toMinutes() / maxMinutes) * progress
                val color = when {
                    goal != null && day.total > goal -> overGoal
                    index == week.lastIndex -> primary
                    else -> G.chart[index % G.chart.size]
                }
                if (height > 0) {
                    drawRoundRect(Brush.verticalGradient(listOf(color.copy(alpha = .5f), color)), Offset(x, size.height - height), Size(barWidth, height), CornerRadius(8.dp.toPx()))
                    drawCircle(Color.White, 2.5.dp.toPx(), Offset(slot * (index + .5f), size.height - height + 7.dp.toPx()))
                } else {
                    drawCircle(color, 3.dp.toPx(), Offset(slot * (index + .5f), size.height - 3.dp.toPx()))
                }
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
                    day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, LocalConfiguration.current.locales[0]).take(3),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (index == week.lastIndex) primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
