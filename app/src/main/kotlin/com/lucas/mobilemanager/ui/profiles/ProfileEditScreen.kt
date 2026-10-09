package com.lucas.mobilemanager.ui.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucas.mobilemanager.R
import com.lucas.mobilemanager.domain.model.Schedule
import com.lucas.mobilemanager.domain.profiles.ProfileValidator
import com.lucas.mobilemanager.ui.apps.AppIcon
import com.lucas.mobilemanager.ui.apps.AppSearchField
import com.lucas.mobilemanager.ui.apps.appPickerItems
import com.lucas.mobilemanager.ui.components.TimePickerDialog
import com.lucas.mobilemanager.ui.format.Formats
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditScreen(
    onBack: () -> Unit,
    viewModel: ProfileEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onBack() }

    val title = when {
        state.isDefaultRules -> R.string.profile_edit_default_rules
        state.isNew -> R.string.profile_edit_new
        else -> R.string.profile_edit_title
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.navigate_back))
                    }
                },
            )
        },
        bottomBar = {
            Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                if (state.locked) {
                    Text(stringResource(R.string.locked_during_session), color = MaterialTheme.colorScheme.tertiary)
                }
                state.errors.forEach { Text(errorText(it), color = MaterialTheme.colorScheme.tertiary) }
                Button(
                    onClick = viewModel::save,
                    enabled = !state.locked && !state.loading,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.profile_edit_save)) }
            }
        },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val draft = state.draft
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.isDefaultRules) {
                item { Caption(stringResource(R.string.profile_edit_default_rules_help)) }
            } else {
                item {
                    OutlinedTextField(
                        value = draft.name,
                        onValueChange = viewModel::setName,
                        label = { Text(stringResource(R.string.profile_edit_name)) },
                        singleLine = true,
                        enabled = !state.locked,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    SwitchRow(
                        title = stringResource(R.string.profile_edit_auto),
                        caption = if (draft.autoActivation) null else stringResource(R.string.profile_edit_manual_only),
                        checked = draft.autoActivation,
                        enabled = !state.locked,
                        onChange = viewModel::setAutoActivation,
                    )
                }
                if (draft.autoActivation) {
                    item { SectionTitle(stringResource(R.string.profile_edit_schedules)) }
                    itemsIndexed(draft.schedules) { index, schedule ->
                        ScheduleCard(
                            schedule = schedule,
                            enabled = !state.locked,
                            onToggleDay = { viewModel.toggleDay(index, it) },
                            onSetDays = { viewModel.setDays(index, it) },
                            onStart = { viewModel.setStart(index, it) },
                            onEnd = { viewModel.setEnd(index, it) },
                            onRemove = { viewModel.removeSchedule(index) },
                        )
                    }
                    item {
                        TextButton(onClick = viewModel::addSchedule, enabled = !state.locked) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.profile_edit_add_schedule))
                        }
                    }
                }
                item {
                    SwitchRow(
                        title = stringResource(R.string.profile_edit_block_all),
                        caption = stringResource(R.string.profile_edit_block_all_help),
                        checked = draft.blockAll,
                        enabled = !state.locked,
                        onChange = viewModel::setBlockAll,
                    )
                }
            }

            item {
                SectionTitle(
                    stringResource(
                        if (draft.blockAll && !state.isDefaultRules) R.string.profile_edit_exceptions else R.string.profile_edit_app_rules,
                    ),
                )
            }
            itemsIndexed(draft.appRules, key = { _, rule -> rule.packageName }) { _, rule ->
                AppRuleRow(
                    rule = rule,
                    enabled = !state.locked,
                    onChoice = { viewModel.setChoice(rule.packageName, it) },
                    onLimit = { viewModel.setLimit(rule.packageName, it) },
                    onRemove = { viewModel.removeApp(rule.packageName) },
                )
            }
            item {
                OutlinedButton(onClick = viewModel::openPicker, enabled = !state.locked) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.profile_edit_add_apps))
                }
            }
        }
    }

    if (state.pickerOpen) {
        AppPickerDialog(state, viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppPickerDialog(state: ProfileEditState, viewModel: ProfileEditViewModel) {
    Dialog(onDismissRequest = viewModel::closePicker, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.profile_edit_add_apps)) },
                    navigationIcon = {
                        IconButton(onClick = viewModel::closePicker) {
                            Icon(Icons.Filled.Close, stringResource(R.string.ok))
                        }
                    },
                    actions = { TextButton(onClick = viewModel::closePicker) { Text(stringResource(R.string.ok)) } },
                )
            },
        ) { padding ->
            val selected = state.draft.appRules.map { it.packageName }.toSet()
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            ) {
                item { AppSearchField(state.query, viewModel::setQuery, Modifier.padding(bottom = 8.dp)) }
                if (state.installedApps.isEmpty()) {
                    item { CircularProgressIndicator(Modifier.padding(16.dp)) }
                } else {
                    appPickerItems(state.installedApps, selected, state.query) { pkg ->
                        state.installedApps.firstOrNull { it.packageName == pkg }?.let(viewModel::togglePickerApp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleCard(
    schedule: Schedule,
    enabled: Boolean,
    onToggleDay: (DayOfWeek) -> Unit,
    onSetDays: (Set<DayOfWeek>) -> Unit,
    onStart: (LocalTime) -> Unit,
    onEnd: (LocalTime) -> Unit,
    onRemove: () -> Unit,
) {
    var editing by remember { mutableStateOf<Boolean?>(null) } // true = début, false = fin
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DayOfWeek.entries.forEach { day ->
                    FilterChip(
                        selected = day in schedule.days,
                        onClick = { onToggleDay(day) },
                        enabled = enabled,
                        label = { Text(day.getDisplayName(TextStyle.NARROW, Locale.FRENCH)) },
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { onSetDays(Schedule.EVERY_DAY) },
                    enabled = enabled,
                    label = { Text(stringResource(R.string.days_every_day)) },
                )
                AssistChip(
                    onClick = { onSetDays(Schedule.WEEKDAYS) },
                    enabled = enabled,
                    label = { Text(stringResource(R.string.days_weekdays)) },
                )
                AssistChip(
                    onClick = { onSetDays(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) },
                    enabled = enabled,
                    label = { Text(stringResource(R.string.days_weekend)) },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { editing = true }, enabled = enabled) {
                    Text(stringResource(R.string.schedule_from, Formats.time(schedule.start)))
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { editing = false }, enabled = enabled) {
                    Text(stringResource(R.string.schedule_to, Formats.time(schedule.end)))
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onRemove, enabled = enabled) {
                    Icon(Icons.Outlined.Delete, stringResource(R.string.profile_edit_remove_schedule))
                }
            }
            val hint = when {
                schedule.start == schedule.end -> stringResource(R.string.schedule_all_day)
                schedule.crossesMidnight -> stringResource(R.string.schedule_crosses_midnight, Formats.time(schedule.end))
                else -> null
            }
            hint?.let { Caption(it) }
        }
    }
    editing?.let { isStart ->
        TimePickerDialog(
            title = stringResource(if (isStart) R.string.schedule_pick_start else R.string.schedule_pick_end),
            initial = if (isStart) schedule.start else schedule.end,
            onDismiss = { editing = null },
            onConfirm = {
                if (isStart) onStart(it) else onEnd(it)
                editing = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRuleRow(
    rule: AppRuleDraft,
    enabled: Boolean,
    onChoice: (RuleChoice) -> Unit,
    onLimit: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(rule.packageName, Modifier.size(32.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(rule.label, style = MaterialTheme.typography.bodyLarge)
                if (!rule.installed) Caption(stringResource(R.string.profile_edit_not_installed))
            }
            IconButton(onClick = onRemove, enabled = enabled) {
                Icon(Icons.Filled.Close, stringResource(R.string.profile_edit_remove_app))
            }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val choices = RuleChoice.entries
            choices.forEachIndexed { index, choice ->
                SegmentedButton(
                    selected = rule.choice == choice,
                    onClick = { onChoice(choice) },
                    enabled = enabled,
                    shape = SegmentedButtonDefaults.itemShape(index, choices.size),
                    label = { Text(stringResource(choice.label())) },
                )
            }
        }
        if (rule.choice == RuleChoice.LIMIT) {
            LimitPicker(rule.limitMinutes, enabled, onLimit)
        }
    }
}

@Composable
private fun LimitPicker(minutes: Int, enabled: Boolean, onLimit: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }, enabled = enabled) {
            Text(stringResource(R.string.profile_edit_limit_value, Formats.duration(Duration.ofMinutes(minutes.toLong()))))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            LIMIT_OPTIONS.forEach { option ->
                DropdownMenuItem(
                    text = { Text(Formats.duration(Duration.ofMinutes(option.toLong()))) },
                    onClick = {
                        onLimit(option)
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, caption: String?, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            caption?.let { Caption(it) }
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun Caption(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun RuleChoice.label() = when (this) {
    RuleChoice.BLOCK -> R.string.rule_block
    RuleChoice.LIMIT -> R.string.rule_limit
    RuleChoice.ALLOW -> R.string.rule_allow
}

@Composable
private fun errorText(error: ProfileValidator.Error): String = stringResource(
    when (error) {
        ProfileValidator.Error.NAME_EMPTY -> R.string.profile_error_name_empty
        ProfileValidator.Error.NAME_TAKEN -> R.string.profile_error_name_taken
        ProfileValidator.Error.NO_SCHEDULE -> R.string.profile_error_no_schedule
        ProfileValidator.Error.SCHEDULE_WITHOUT_DAYS -> R.string.profile_error_no_days
        ProfileValidator.Error.NO_RULES -> R.string.profile_error_no_rules
        ProfileValidator.Error.LIMIT_OUT_OF_RANGE -> R.string.profile_error_limit
        ProfileValidator.Error.DUPLICATE_TARGET -> R.string.profile_error_duplicate
    },
)

private val LIMIT_OPTIONS = listOf(5, 10, 15, 20, 30, 45, 60, 90, 120, 180)
