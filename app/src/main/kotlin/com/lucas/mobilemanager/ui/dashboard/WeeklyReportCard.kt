package com.lucas.mobilemanager.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lucas.mobilemanager.R
import com.lucas.mobilemanager.domain.dashboard.DashboardSummary
import com.lucas.mobilemanager.ui.format.Formats
import java.time.format.DateTimeFormatter
import androidx.compose.ui.platform.LocalConfiguration

@Composable
fun WeeklyReportCard(report: DashboardSummary, labels: Map<String, String>) {
    val locale = LocalConfiguration.current.locales[0]
    val date = DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(locale)
    val count = report.week.count { it.hasData }
    var expanded by rememberSaveable { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.weekly_report_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.weekly_report_dates, date.format(report.range.start), date.format(report.range.endInclusive)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.weekly_report_coverage, count), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            if (count > 0) {
                Text(stringResource(R.string.weekly_report_total, Formats.duration(report.total), Formats.duration(report.total.dividedBy(count.toLong()))), style = MaterialTheme.typography.bodyMedium)
                if (count == 7 && report.previousDaysWithData == 7) Text(comparisonText(report, com.lucas.mobilemanager.domain.dashboard.Period.WEEK), style = MaterialTheme.typography.bodyMedium)
                else Text(stringResource(R.string.weekly_report_partial), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { expanded = !expanded }) { Text(stringResource(if (expanded) R.string.weekly_report_less else R.string.weekly_report_details)) }
                if (expanded) {
                    report.apps.firstOrNull()?.let { app ->
                        Text(stringResource(R.string.weekly_report_top_app, labels[app.packageName] ?: app.packageName, Formats.duration(app.duration)), style = MaterialTheme.typography.bodyMedium)
                    }
                    report.week.filter { it.hasData }.maxByOrNull { it.total }?.let { day ->
                        Text(stringResource(R.string.weekly_report_peak, date.format(day.date), Formats.duration(day.total)), style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(stringResource(R.string.weekly_report_sessions, report.goals.sessionsCompleted), style = MaterialTheme.typography.bodyMedium)
                }
            } else Text(stringResource(R.string.dashboard_no_data))
            Text(stringResource(R.string.weekly_report_local), style = MaterialTheme.typography.bodySmall)
        }
    }
}
