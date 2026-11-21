package com.lucas.mobilemanager.ui.goals

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucas.mobilemanager.R
import com.lucas.mobilemanager.domain.goals.PersonalGoal
import com.lucas.mobilemanager.ui.apps.AppSearchField
import com.lucas.mobilemanager.ui.apps.appPickerItems
import com.lucas.mobilemanager.ui.components.PageIntro
import com.lucas.mobilemanager.ui.components.SceneKind
import com.lucas.mobilemanager.ui.format.Formats
import com.lucas.mobilemanager.ui.lock.LockBanner
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoalsScreen(onEdit: (String) -> Unit, viewModel: GoalsViewModel = hiltViewModel()) {
    val goals by viewModel.goals.collectAsStateWithLifecycle()
    val editable by viewModel.editable.collectAsStateWithLifecycle()
    val today by viewModel.today.collectAsStateWithLifecycle()
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val access by viewModel.temporary.access.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    var reporting by remember { mutableStateOf<PersonalGoal?>(null) }
    var deleting by remember { mutableStateOf<PersonalGoal?>(null) }
    var choosingApp by rememberSaveable { mutableStateOf(false) }
    var chosen by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var tick by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { while (true) { delay(1000); tick++ } }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { PageIntro(stringResource(R.string.goals_title), stringResource(R.string.goals_intro), SceneKind.BALANCE) }
        item { LockBanner() }
        item {
            Button(onClick = { onEdit("") }, enabled = editable) { Text(stringResource(R.string.goal_create)) }
            if (!editable) Text(stringResource(R.string.wellbeing_locked))
        }
        if (goals == null) item { CircularProgressIndicator() }
        else if (goals.orEmpty().isEmpty()) item { Text(stringResource(R.string.goals_empty)) }
        items(goals.orEmpty().sortedWith(compareBy<PersonalGoal> { !it.isActive(today) }.thenBy { it.dueDate }), key = { it.id }) { goal ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(goal.title, style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.goal_due, goal.dueDate.format(dateFormat)))
                    Text(stringResource(when {
                        goal.completed -> R.string.goal_completed
                        !goal.isActive(today) -> R.string.goal_expired
                        else -> R.string.goal_active
                    }))
                    Text(stringResource(R.string.goal_budget_summary, goal.dailyBudgetMinutes, goal.packages.size))
                    goal.slots.forEach { slot ->
                        Text(stringResource(R.string.goal_slot_summary,
                            slot.days.sorted().joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }, Formats.time(slot.start), slot.minutes))
                    }
                    Text(goal.packages.joinToString(", ") { pkg -> apps.firstOrNull { it.packageName == pkg }?.label ?: pkg }, style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { onEdit(goal.id) }, enabled = editable) {
                            Text(stringResource(if (goal.isActive(today)) R.string.goal_edit else R.string.goal_extend))
                        }
                        if (goal.isActive(today)) TextButton(onClick = { viewModel.complete(goal) }, enabled = editable) { Text(stringResource(R.string.goal_finish)) }
                        TextButton(onClick = { reporting = goal; viewModel.loadReport(goal) }) { Text(stringResource(R.string.goal_report)) }
                        TextButton(onClick = { deleting = goal }, enabled = editable) { Text(stringResource(R.string.goal_delete)) }
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.temporary_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.temporary_summary))
                    TextButton(onClick = { choosingApp = true }, enabled = editable) { Text(stringResource(R.string.temporary_choose)) }
                    if (chosen.isNotBlank()) {
                        Text(apps.firstOrNull { it.packageName == chosen }?.label ?: chosen)
                        TemporaryAccessAction(chosen, viewModel.temporary, editable, onGranted = { chosen = "" })
                    }
                    access.orEmpty().forEach { a ->
                        val remaining = remember(a, tick) { viewModel.temporary.remainingNow(a.packageName) }
                        if (remaining != null) {
                            Text(stringResource(R.string.temporary_remaining,
                                apps.firstOrNull { it.packageName == a.packageName }?.label ?: a.packageName, Formats.duration(remaining)))
                            TextButton(onClick = { scope.launch { viewModel.temporary.revoke(a.packageName) } }) { Text(stringResource(R.string.temporary_revoke)) }
                        }
                    }
                }
            }
        }
        item { BatteryMeasurementCard() }
    }
    if (choosingApp) AlertDialog(onDismissRequest = { choosingApp = false }, title = { Text(stringResource(R.string.temporary_choose)) }, text = {
        Column { AppSearchField(query, { query = it }); LazyColumn(Modifier.heightIn(max = 320.dp)) {
            appPickerItems(apps, setOf(chosen), query) { chosen = it; choosingApp = false }
        } }
    }, confirmButton = { TextButton(onClick = { choosingApp = false }) { Text(stringResource(R.string.cancel)) } })
    deleting?.let { goal -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text(stringResource(R.string.goal_delete)) },
        text = { Text(stringResource(R.string.goal_delete_confirm, goal.title)) },
        confirmButton = { TextButton(onClick = { viewModel.delete(goal); deleting = null }, enabled = editable) { Text(stringResource(R.string.goal_delete)) } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } }) }
    reporting?.let { goal -> GoalReportDialog(goal.title, viewModel, onDismiss = { reporting = null }) }
}
