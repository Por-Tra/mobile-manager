package com.lucas.mobilemanager.ui.goals

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucas.mobilemanager.R
import com.lucas.mobilemanager.ui.format.Formats
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun GoalReportDialog(title: String, viewModel: GoalsViewModel, onDismiss: () -> Unit) {
    val report by viewModel.report.collectAsStateWithLifecycle()
    val loading by viewModel.reportLoading.collectAsStateWithLifecycle()
    val failed by viewModel.reportFailed.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val dates = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale) }
    fun bytes(value: Long) = Formatter.formatShortFileSize(context, value)
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.goal_report_title, title)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (loading) CircularProgressIndicator()
            if (failed) Text(stringResource(R.string.goal_report_unavailable))
            report?.let { r ->
                Text(stringResource(R.string.goal_report_period, r.from.format(dates), r.to.format(dates)))
                Text(stringResource(R.string.goal_usage_total, Formats.duration(r.usage), r.days))
                r.previousUsage?.let { previous ->
                    val change = r.usage - previous
                    Text(stringResource(if (change.isNegative) R.string.goal_usage_less else R.string.goal_usage_more, Formats.duration(change.abs())))
                } ?: Text(stringResource(R.string.goal_usage_incomplete, r.days, r.previousDays))
                r.network?.let { n ->
                    Text(stringResource(R.string.goal_network_total, bytes(n.wifi), bytes(n.mobile)))
                    r.previousNetwork?.let { previous ->
                        val change = n.wifi + n.mobile - previous.wifi - previous.mobile
                        Text(stringResource(if (change < 0) R.string.goal_network_less else R.string.goal_network_more, bytes(kotlin.math.abs(change))))
                    }
                } ?: Text(stringResource(R.string.goal_network_unavailable))
                if (r.sharedUid) Text(stringResource(R.string.goal_network_shared_uid))
                Text(stringResource(R.string.goal_report_scope), style = MaterialTheme.typography.bodySmall)
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } })
}
