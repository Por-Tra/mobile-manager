package com.lucas.mobilemanager.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucas.mobilemanager.R
import com.lucas.mobilemanager.domain.dashboard.BlockKind
import com.lucas.mobilemanager.domain.dashboard.DashboardSummary
import com.lucas.mobilemanager.domain.dashboard.Period
import com.lucas.mobilemanager.ui.apps.AppIcon
import com.lucas.mobilemanager.ui.format.Formats
import java.time.Duration

/**
 * Tableau de bord : mesure si l'outil aide, sans juger. Formulations neutres ou encourageantes,
 * aucune couleur d'alerte.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(onBack: () -> Unit, viewModel: DashboardViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pickingGoal by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dashboard_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.navigate_back)) }
                },
            )
        },
    ) { padding ->
        val summary = state.summary
        if (summary == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val goal = state.goalMinutes?.let { Duration.ofMinutes(it.toLong()) }
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Period.entries.forEachIndexed { index, period ->
                        SegmentedButton(
                            selected = state.period == period,
                            onClick = { viewModel.setPeriod(period) },
                            shape = SegmentedButtonDefaults.itemShape(index, Period.entries.size),
                            label = {
                                Text(stringResource(if (period == Period.TODAY) R.string.dashboard_today else R.string.dashboard_week))
                            },
                        )
                    }
                }
            }
            item { TotalCard(summary, state.period) }
            item {
                Section(stringResource(R.string.dashboard_week_chart)) {
                    WeekChart(summary.week, goal, stringResource(R.string.dashboard_week_chart))
                    goal?.let {
                        Caption(stringResource(R.string.dashboard_goal_line, Formats.duration(it)))
                    }
                }
            }
            if (!summary.hasData) {
                item { Caption(stringResource(R.string.dashboard_no_data)) }
            } else {
                item {
                    Section(stringResource(R.string.dashboard_apps)) {
                        val apps = if (state.showAllApps) summary.apps else summary.apps.take(TOP_APPS)
                        apps.forEach { app ->
                            AppRow(
                                packageName = app.packageName,
                                label = state.appLabels[app.packageName] ?: app.packageName,
                                duration = app.duration,
                                share = app.share,
                            )
                        }
                        if (summary.apps.size > TOP_APPS) {
                            TextButton(onClick = viewModel::toggleShowAllApps) {
                                Text(stringResource(if (state.showAllApps) R.string.dashboard_show_less else R.string.dashboard_show_all))
                            }
                        }
                    }
                }
            }
            item { ZonesSection(summary, state, state.period) }
            item { InterruptionsSection(summary) }
            item { GoalsSection(summary, goal, state.period, onPickGoal = { pickingGoal = true }) }
        }
    }

    if (pickingGoal) {
        GoalDialog(
            current = state.goalMinutes,
            onDismiss = { pickingGoal = false },
            onSelect = {
                viewModel.setGoal(it)
                pickingGoal = false
            },
        )
    }
}

@Composable
private fun TotalCard(summary: DashboardSummary, period: Period) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(if (period == Period.TODAY) R.string.dashboard_total_today else R.string.dashboard_total_week),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(Formats.duration(summary.total), style = MaterialTheme.typography.displaySmall)
            Text(comparisonText(summary, period), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun AppRow(packageName: String, label: String, duration: Duration, share: Float) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        AppIcon(packageName, Modifier.size(32.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row {
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(Formats.duration(duration), style = MaterialTheme.typography.bodyMedium)
            }
            LinearProgressIndicator(
                progress = { share },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                drawStopIndicator = {},
            )
        }
    }
}

@Composable
private fun ZonesSection(summary: DashboardSummary, state: DashboardUiState, period: Period) {
    val zones = summary.zones
    Section(stringResource(R.string.dashboard_zones)) {
        if (zones.timeByZone.isEmpty() && zones.exitsByZone.isEmpty()) {
            Caption(stringResource(R.string.dashboard_zones_none))
        }
        zones.timeByZone.forEach { (key, duration) ->
            val zoneLabel = state.zoneLabels[key] ?: key.zone
            val appLabel = state.appLabels[key.packageName] ?: key.packageName
            val quota = zones.quotaByZone[key]
            Line(
                if (quota != null && period == Period.TODAY) {
                    stringResource(R.string.dashboard_zone_time_quota, zoneLabel, appLabel, Formats.duration(duration), Formats.duration(quota))
                } else {
                    stringResource(R.string.dashboard_zone_time, zoneLabel, appLabel, Formats.duration(duration))
                },
            )
        }
        zones.exitsByZone.forEach { (key, count) ->
            Line(
                pluralStringResource(R.plurals.dashboard_zone_exits, count, state.zoneLabels[key] ?: key.zone, count),
            )
        }
    }
}

@Composable
private fun InterruptionsSection(summary: DashboardSummary) {
    val interruptions = summary.interruptions
    Section(stringResource(R.string.dashboard_interruptions)) {
        if (interruptions.total == 0) {
            Caption(stringResource(R.string.dashboard_interruptions_none))
            return@Section
        }
        interruptions.blocks.forEach { (kind, count) ->
            val label = when (kind) {
                BlockKind.RULE -> R.plurals.dashboard_blocks_rule
                BlockKind.STRICT_SESSION -> R.plurals.dashboard_blocks_strict
                BlockKind.DAILY_LIMIT -> R.plurals.dashboard_blocks_daily
            }
            Line(pluralStringResource(label, count, count))
        }
    }
}

@Composable
private fun GoalsSection(summary: DashboardSummary, goal: Duration?, period: Period, onPickGoal: () -> Unit) {
    val goals = summary.goals
    Section(stringResource(R.string.dashboard_goals)) {
        if (goal == null) {
            Line(stringResource(R.string.dashboard_goal_none))
        } else if (period == Period.TODAY) {
            Line(
                stringResource(
                    if (summary.total <= goal) R.string.dashboard_goal_today_under else R.string.dashboard_goal_today_over,
                    Formats.duration(goal),
                ),
            )
        } else {
            Line(stringResource(R.string.dashboard_goal_week, goals.daysUnderGoal ?: 0, goals.daysWithData, Formats.duration(goal)))
        }
        TextButton(onClick = onPickGoal, contentPadding = PaddingValues(0.dp)) {
            Text(stringResource(if (goal == null) R.string.dashboard_goal_set else R.string.dashboard_goal_change))
        }
        Line(
            if (goals.daysLimitReached == 0) {
                stringResource(R.string.dashboard_limits_none)
            } else {
                pluralStringResource(R.plurals.dashboard_limits_reached, goals.daysLimitReached, goals.daysLimitReached)
            },
        )
        val sessions = goals.sessionsCompleted + goals.sessionsEndedEarly
        Line(
            if (sessions == 0) {
                stringResource(R.string.dashboard_sessions_none)
            } else {
                stringResource(R.string.dashboard_sessions, goals.sessionsCompleted, goals.sessionsEndedEarly)
            },
        )
    }
}

@Composable
private fun GoalDialog(current: Int?, onDismiss: () -> Unit, onSelect: (Int?) -> Unit) {
    val none = stringResource(R.string.dashboard_goal_option_none)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dashboard_goal_dialog)) },
        text = {
            Column {
                (listOf<Int?>(null) + GOAL_OPTIONS).forEach { minutes ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().selectable(selected = current == minutes, onClick = { onSelect(minutes) }),
                    ) {
                        RadioButton(selected = current == minutes, onClick = { onSelect(minutes) })
                        Text(minutes?.let { Formats.duration(Duration.ofMinutes(it.toLong())) } ?: none)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun Line(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
}

@Composable
private fun Caption(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private const val TOP_APPS = 8
private val GOAL_OPTIONS = listOf(30, 60, 90, 120, 180, 240, 300, 360, 480)
