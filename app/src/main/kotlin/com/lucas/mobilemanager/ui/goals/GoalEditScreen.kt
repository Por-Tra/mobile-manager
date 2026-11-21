package com.lucas.mobilemanager.ui.goals

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucas.mobilemanager.R
import com.lucas.mobilemanager.domain.goals.*
import com.lucas.mobilemanager.ui.apps.AppSearchField
import com.lucas.mobilemanager.ui.apps.appPickerItems
import com.lucas.mobilemanager.ui.components.PageTopBar
import com.lucas.mobilemanager.ui.components.TimePickerDialog
import com.lucas.mobilemanager.ui.lock.LockBanner
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.UUID
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalEditScreen(goalId: String, onBack: () -> Unit, viewModel: GoalsViewModel = hiltViewModel()) {
    val goals by viewModel.goals.collectAsStateWithLifecycle()
    if (goals == null) { CircularProgressIndicator(); return }
    val goal = goals.orEmpty().firstOrNull { it.id == goalId }
    if (goalId.isNotBlank() && goal == null) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.cancel)) }; return
    }
    GoalEditor(goal, onBack, viewModel)
}

private val SlotsSaver = listSaver<List<PreparationSlot>, String>(
    save = { slots -> slots.map { "${it.days.sorted().joinToString(",") { d -> d.name }}|${it.start}|${it.minutes}" } },
    restore = { rows -> rows.map { val p = it.split('|'); PreparationSlot(p[0].split(',').map(DayOfWeek::valueOf).toSet(), LocalTime.parse(p[1]), p[2].toInt()) } },
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun GoalEditor(goal: PersonalGoal?, onBack: () -> Unit, viewModel: GoalsViewModel) {
    val editable by viewModel.editable.collectAsStateWithLifecycle()
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val today by viewModel.today.collectAsStateWithLifecycle()
    val id by rememberSaveable { mutableStateOf(goal?.id ?: UUID.randomUUID().toString()) }
    var title by rememberSaveable { mutableStateOf(goal?.title.orEmpty()) }
    var due by rememberSaveable { mutableLongStateOf((goal?.dueDate ?: today.plusWeeks(3)).toEpochDay()) }
    var packages by rememberSaveable { mutableStateOf(goal?.packages?.toList() ?: emptyList<String>()) }
    var budget by rememberSaveable { mutableStateOf((goal?.dailyBudgetMinutes ?: 30).toString()) }
    var reminders by rememberSaveable { mutableStateOf(goal?.remindersEnabled ?: false) }
    var slots by rememberSaveable(stateSaver = SlotsSaver) { mutableStateOf(goal?.slots ?: emptyList()) }
    var dateDialog by rememberSaveable { mutableStateOf(false) }
    var appsDialog by rememberSaveable { mutableStateOf(false) }
    var slotDialog by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(if (goal == null) R.string.goal_create else R.string.goal_edit)) },
        navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.cancel)) } }) }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            LockBanner()
            OutlinedTextField(title, { title = it.take(120) }, enabled = editable, label = { Text(stringResource(R.string.goal_name)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            TextButton(onClick = { dateDialog = true }, enabled = editable) { Text(stringResource(R.string.goal_due, LocalDate.ofEpochDay(due).format(dateFormat))) }
            if (LocalDate.ofEpochDay(due).isBefore(today)) Text(stringResource(R.string.goal_date_invalid))
            Text(stringResource(R.string.goal_end_help))
            Text(stringResource(R.string.shared_budget_title), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(budget, { budget = it.filter(Char::isDigit).take(4) }, enabled = editable,
                label = { Text(stringResource(R.string.goal_minutes)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = budget.toIntOrNull() !in 1..1440)
            TextButton(onClick = { appsDialog = true }, enabled = editable) { Text(stringResource(R.string.wellbeing_select_apps, packages.size)) }
            Text(packages.joinToString(", ") { pkg -> apps.firstOrNull { it.packageName == pkg }?.label ?: pkg })
            Text(stringResource(R.string.goal_slots), style = MaterialTheme.typography.titleMedium)
            slots.forEachIndexed { index, slot ->
                Text(stringResource(R.string.goal_slot_summary, slot.days.sorted().joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }, slot.start.toString(), slot.minutes))
                TextButton(onClick = { slots = slots.filterIndexed { i, _ -> i != index } }, enabled = editable) { Text(stringResource(R.string.goal_slot_remove)) }
            }
            TextButton(onClick = { slotDialog = true }, enabled = editable) { Text(stringResource(R.string.goal_slot_add)) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.goal_reminders), Modifier.weight(1f))
                Switch(reminders, { reminders = it }, enabled = editable)
            }
            Text(stringResource(R.string.goal_reminders_help))
            if (failed) Text(stringResource(R.string.goal_action_refused))
            Button(enabled = editable && !saving && title.isNotBlank() && packages.isNotEmpty() && budget.toIntOrNull() in 1..1440 && !LocalDate.ofEpochDay(due).isBefore(today), onClick = {
                saving = true
                scope.launch {
                    try {
                        val saved = viewModel.save(PersonalGoal(id, title.trim(), LocalDate.ofEpochDay(due), packages.toSet(), budget.toInt(), slots, reminders, completed = false))
                        if (saved) {
                            if (reminders && Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            onBack()
                        } else failed = true
                    } finally { saving = false }
                }
            }) { Text(stringResource(R.string.wellbeing_save)) }
        }
    }
    if (dateDialog) {
        val state = rememberDatePickerState(initialSelectedDateMillis = LocalDate.ofEpochDay(due).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { dateDialog = false }, confirmButton = { TextButton(onClick = {
            state.selectedDateMillis?.let { due = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay() }; dateDialog = false
        }, enabled = editable && state.selectedDateMillis != null) { Text(stringResource(R.string.ok)) } },
            dismissButton = { TextButton(onClick = { dateDialog = false }) { Text(stringResource(R.string.cancel)) } }) { DatePicker(state) }
    }
    if (appsDialog) AlertDialog(onDismissRequest = { appsDialog = false }, title = { Text(stringResource(R.string.wellbeing_select_apps, packages.size)) },
        text = { Column { AppSearchField(query, { query = it }); LazyColumn(Modifier.heightIn(max = 360.dp)) {
            appPickerItems(apps, packages.toSet(), query) { pkg -> packages = if (pkg in packages) packages - pkg else packages + pkg }
        } } }, confirmButton = { TextButton(onClick = { appsDialog = false }) { Text(stringResource(R.string.ok)) } })
    if (slotDialog) SlotDialog(onDismiss = { slotDialog = false }, onSave = { slots = slots + it; slotDialog = false })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SlotDialog(onDismiss: () -> Unit, onSave: (PreparationSlot) -> Unit) {
    var days by rememberSaveable { mutableStateOf(listOf<String>()) }
    var hour by rememberSaveable { mutableIntStateOf(19 * 60) }
    var minutes by rememberSaveable { mutableStateOf("20") }
    var timePicker by rememberSaveable { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.goal_slot_add)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DayOfWeek.entries.forEach { day -> FilterChip(day.name in days, onClick = { days = if (day.name in days) days - day.name else days + day.name },
                    label = { Text(day.getDisplayName(TextStyle.SHORT, locale)) }) }
            }
            TextButton(onClick = { timePicker = true }) { Text(LocalTime.of(hour / 60, hour % 60).toString()) }
            OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit).take(4) }, label = { Text(stringResource(R.string.goal_minutes)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
        }
    }, confirmButton = { TextButton(enabled = days.isNotEmpty() && minutes.toIntOrNull() in 1..1440, onClick = {
        onSave(PreparationSlot(days.map(DayOfWeek::valueOf).toSet(), LocalTime.of(hour / 60, hour % 60), minutes.toInt()))
    }) { Text(stringResource(R.string.ok)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
    if (timePicker) TimePickerDialog(stringResource(R.string.goal_slot_time), LocalTime.of(hour / 60, hour % 60), { timePicker = false }) {
        hour = it.hour * 60 + it.minute; timePicker = false
    }
}
