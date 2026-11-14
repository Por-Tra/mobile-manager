package com.lucas.mobilemanager.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lucas.mobilemanager.R
import com.lucas.mobilemanager.domain.model.Schedule
import com.lucas.mobilemanager.domain.dashboard.DashboardSummary
import com.lucas.mobilemanager.domain.dashboard.Period
import com.lucas.mobilemanager.ui.apps.AppIcon
import com.lucas.mobilemanager.ui.dashboard.comparisonText
import com.lucas.mobilemanager.ui.format.Formats
import com.lucas.mobilemanager.ui.theme.GraphicColors as G
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun UsageRing(shares: List<Float>, modifier: Modifier = Modifier) {
    val progress = entranceProgress()
    val track = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f)
    val description = stringResource(R.string.graphic_usage_distribution)
    Box(modifier.semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val width = 12.dp.toPx()
            val diameter = minOf(size.width, size.height) - width
            val origin = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
            drawArc(track, 0f, 360f, false, origin, Size(diameter, diameter), style = Stroke(width))
            var start = -90f
            // Shares come from actual app usage; an empty day retains an empty track.
            val total = shares.sum().coerceAtLeast(1f)
            shares.forEachIndexed { index, share ->
                val sweep = 360 * share.coerceAtLeast(0f) / total * progress
                val gap = minOf(4f, sweep * .15f)
                if (sweep > 0) drawArc(G.chart[index % G.chart.size], start + gap / 2, sweep - gap, false, origin, Size(diameter, diameter), style = Stroke(width, cap = StrokeCap.Butt))
                start += sweep
            }
        }
        Icon(Icons.Outlined.Schedule, null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun ScheduleRibbon(schedules: List<Schedule>, enabled: Boolean, modifier: Modifier = Modifier) {
    if (schedules.isEmpty()) return
    val color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val track = MaterialTheme.colorScheme.surfaceContainer
    val description = stringResource(R.string.graphic_schedule_union)
    Column(modifier.semantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(18.dp)) {
            val y = size.height * .3f
            val height = size.height * .4f
            drawRoundRect(track, Offset(0f, y), Size(size.width, height), CornerRadius(height / 2))
            fun segment(start: Float, end: Float) {
                if (end <= start) return
                drawRoundRect(color.copy(alpha = .75f), Offset(start * size.width, y), Size((end - start) * size.width, height), CornerRadius(height / 2))
            }
            schedules.forEach { schedule ->
                val from = schedule.start.toSecondOfDay() / 86400f
                val to = schedule.end.toSecondOfDay() / 86400f
                when {
                    schedule.start == schedule.end -> segment(0f, 1f)
                    schedule.crossesMidnight -> { segment(from, 1f); segment(0f, to) }
                    else -> segment(from, to)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(R.string.graphic_axis_start, R.string.graphic_axis_middle, R.string.graphic_axis_end).forEach { Text(stringResource(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
fun UsageOverview(summary: DashboardSummary, period: Period, modifier: Modifier = Modifier) {
    Column(modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(if (period == Period.TODAY) R.string.dashboard_total_today else R.string.dashboard_total_week), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Formats.duration(summary.total), style = MaterialTheme.typography.headlineLarge)
            }
            UsageRing(summary.apps.map { it.share }, Modifier.size(88.dp))
        }
        if (summary.apps.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                summary.apps.take(3).forEach { app ->
                    val packageManager = LocalContext.current.packageManager
                    val appLabel by produceState(app.packageName, app.packageName, packageManager) {
                        value = withContext(Dispatchers.IO) {
                            runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(app.packageName, 0)).toString() }.getOrDefault(app.packageName)
                        }
                    }
                    val share = if (app.share > 0f && app.share < .01f) stringResource(R.string.graphic_app_share_small)
                        else stringResource(R.string.graphic_app_share, (app.share * 100).toInt())
                    val accessible = stringResource(R.string.graphic_app_distribution_label, appLabel, share)
                    Row(Modifier.clearAndSetSemantics { contentDescription = accessible }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        AppIcon(app.packageName, Modifier.size(20.dp))
                        Text(share, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        Text(comparisonText(summary, period), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
