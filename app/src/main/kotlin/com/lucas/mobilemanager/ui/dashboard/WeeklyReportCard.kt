package com.lucas.mobilemanager.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.weekly_report_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.weekly_report_dates, date.format(report.range.start), date.format(report.range.endInclusive)))
            Text(stringResource(R.string.weekly_report_coverage, count))
            if (count > 0) {
                Text(stringResource(R.string.weekly_report_total, Formats.duration(report.total), Formats.duration(report.total.dividedBy(count.toLong()))))
                if (count == 7 && report.previousDaysWithData == 7) Text(comparisonText(report, com.lucas.mobilemanager.domain.dashboard.Period.WEEK))
                else Text(stringResource(R.string.weekly_report_partial))
                report.apps.firstOrNull()?.let { app ->
                    Text(stringResource(R.string.weekly_report_top_app, labels[app.packageName] ?: app.packageName, Formats.duration(app.duration)))
                }
                report.week.filter { it.hasData }.maxByOrNull { it.total }?.let { day ->
                    Text(stringResource(R.string.weekly_report_peak, date.format(day.date), Formats.duration(day.total)))
                }
                Text(stringResource(R.string.weekly_report_sessions, report.goals.sessionsCompleted))
            } else Text(stringResource(R.string.dashboard_no_data))
            Text(stringResource(R.string.weekly_report_local), style = MaterialTheme.typography.bodySmall)
        }
    }
}
