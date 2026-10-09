package com.lucas.mobilemanager.ui.doomscroll

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucas.mobilemanager.BuildConfig
import com.lucas.mobilemanager.R
import com.lucas.mobilemanager.apps.InstalledApp
import com.lucas.mobilemanager.domain.doomscroll.ScrollGuard
import com.lucas.mobilemanager.ui.apps.AppIcon
import com.lucas.mobilemanager.ui.apps.AppSearchField
import com.lucas.mobilemanager.ui.apps.appPickerItems
import com.lucas.mobilemanager.ui.format.Formats
import java.time.Duration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScrollGuardScreen(
    onBack: () -> Unit,
    viewModel: ScrollGuardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<GuardItem?>(null) }
    var picking by remember { mutableStateOf(false) }
    val enabled = !state.locked

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scroll_guard_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.navigate_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Caption(stringResource(R.string.scroll_guard_intro)) }
            if (state.locked) item { Caption(stringResource(R.string.locked_during_session)) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.scroll_guard_enabled), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = state.settings.enabled, onCheckedChange = viewModel::setEnabled, enabled = enabled)
                }
            }
            item {
                DurationSetting(
                    label = stringResource(R.string.scroll_guard_gap),
                    value = state.settings.sessionGap,
                    options = GAP_OPTIONS,
                    format = ::formatSeconds,
                    enabled = enabled,
                    onSelect = viewModel::setSessionGap,
                )
            }
            item {
                DurationSetting(
                    label = stringResource(R.string.scroll_guard_pause_duration),
                    value = state.settings.pauseDuration,
                    options = PAUSE_DURATION_OPTIONS,
                    format = ::formatSeconds,
                    enabled = enabled,
                    onSelect = viewModel::setPauseDuration,
                )
            }
            item { GoalField(state.settings.goal, enabled, viewModel::setGoal) }
            item {
                Text(
                    stringResource(R.string.scroll_guard_apps),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            items(state.guards, key = { it.guard.packageName }) { item ->
                GuardRow(item, enabled, onClick = { editing = item }, onRemove = { viewModel.removeGuard(item.guard.packageName) })
            }
            item {
                OutlinedButton(
                    onClick = {
                        viewModel.loadInstalledApps()
                        picking = true
                    },
                    enabled = enabled,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.profile_edit_add_apps))
                }
            }
        }
    }

    editing?.let { item ->
        // Toujours la version la plus récente du réglage.
        val current = state.guards.firstOrNull { it.guard.packageName == item.guard.packageName } ?: return@let
        GuardSheet(current, enabled, onDismiss = { editing = null }, onChange = viewModel::updateGuard)
    }
    if (picking) {
        val installed by viewModel.installed.collectAsStateWithLifecycle()
        GuardAppPicker(
            installed = installed,
            selected = state.guards.map { it.guard.packageName }.toSet(),
            onToggle = { pkg -> installed.firstOrNull { it.packageName == pkg }?.let(viewModel::toggleApp) },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun GuardRow(item: GuardItem, enabled: Boolean, onClick: () -> Unit, onRemove: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(vertical = 6.dp),
    ) {
        AppIcon(item.guard.packageName, Modifier.size(36.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.label, style = MaterialTheme.typography.bodyLarge)
            Caption(guardSummary(item.guard) + if (item.installed) "" else " · " + stringResource(R.string.profile_edit_not_installed))
        }
        IconButton(onClick = onRemove, enabled = enabled) {
            Icon(Icons.Filled.Close, stringResource(R.string.profile_edit_remove_app))
        }
    }
}

/** « Pause / 15 min · Blocage à 10 min (repos 15 min) · 30 min/jour » */
@Composable
private fun guardSummary(guard: ScrollGuard): String {
    val parts = buildList {
        guard.pauseEvery?.let { add(stringResource(R.string.guard_summary_pause, Formats.duration(it))) }
        guard.sessionLimit?.let {
            add(stringResource(R.string.guard_summary_block, Formats.duration(it), Formats.duration(guard.cooldown)))
        }
        guard.dailyLimit?.let { add(stringResource(R.string.guard_summary_daily, Formats.duration(it))) }
    }
    return if (parts.isEmpty()) stringResource(R.string.guard_summary_none) else parts.joinToString(" · ")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GuardSheet(item: GuardItem, enabled: Boolean, onDismiss: () -> Unit, onChange: (ScrollGuard) -> Unit) {
    val guard = item.guard
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text(item.label, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(8.dp))
            OptionalDurationSetting(
                label = stringResource(R.string.guard_pause_every),
                value = guard.pauseEvery,
                options = PAUSE_EVERY_OPTIONS,
                enabled = enabled,
                onSelect = { onChange(guard.copy(pauseEvery = it)) },
            )
            OptionalDurationSetting(
                label = stringResource(R.string.guard_session_limit),
                value = guard.sessionLimit,
                options = SESSION_LIMIT_OPTIONS,
                enabled = enabled,
                onSelect = { onChange(guard.copy(sessionLimit = it)) },
            )
            if (guard.sessionLimit != null) {
                DurationSetting(
                    label = stringResource(R.string.guard_cooldown),
                    value = guard.cooldown,
                    options = COOLDOWN_OPTIONS,
                    format = Formats::duration,
                    enabled = enabled,
                    onSelect = { onChange(guard.copy(cooldown = it)) },
                )
            }
            OptionalDurationSetting(
                label = stringResource(R.string.guard_daily_limit),
                value = guard.dailyLimit,
                options = DAILY_LIMIT_OPTIONS,
                enabled = enabled,
                onSelect = { onChange(guard.copy(dailyLimit = it)) },
            )
            Spacer(Modifier.width(16.dp))
        }
    }
}

@Composable
private fun OptionalDurationSetting(
    label: String,
    value: Duration?,
    options: List<Duration>,
    enabled: Boolean,
    onSelect: (Duration?) -> Unit,
) {
    val off = stringResource(R.string.guard_off)
    SettingDropdown(
        label = label,
        valueText = value?.let(Formats::duration) ?: off,
        options = listOf<Duration?>(null) + options,
        optionText = { it?.let(Formats::duration) ?: off },
        enabled = enabled,
        onSelect = onSelect,
    )
}

@Composable
private fun DurationSetting(
    label: String,
    value: Duration,
    options: List<Duration>,
    format: (Duration) -> String,
    enabled: Boolean,
    onSelect: (Duration) -> Unit,
) {
    SettingDropdown(label, format(value), options, format, enabled, onSelect)
}

@Composable
private fun <T> SettingDropdown(
    label: String,
    valueText: String,
    options: List<T>,
    optionText: (T) -> String,
    enabled: Boolean,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Box {
            TextButton(onClick = { open = true }, enabled = enabled) { Text(valueText) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { option ->
                    DropdownMenuItem(text = { Text(optionText(option)) }, onClick = {
                        onSelect(option)
                        open = false
                    })
                }
            }
        }
    }
}

@Composable
private fun GoalField(goal: String, enabled: Boolean, onSave: (String) -> Unit) {
    // Valeur enregistrée tant qu'on n'a rien tapé ; ensuite la saisie locale fait foi
    // (resynchroniser à chaque frappe mélangeait les caractères, la sauvegarde ayant du retard).
    var edited by remember { mutableStateOf<String?>(null) }
    val text = edited ?: goal
    OutlinedTextField(
        value = text,
        onValueChange = {
            edited = it
            onSave(it)
        },
        label = { Text(stringResource(R.string.scroll_guard_goal)) },
        placeholder = { Text(stringResource(R.string.scroll_guard_goal_hint)) },
        singleLine = true,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GuardAppPicker(
    installed: List<InstalledApp>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.profile_edit_add_apps)) },
                    actions = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
                )
            },
        ) { padding ->
            LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
                item { AppSearchField(query, { query = it }, Modifier.padding(bottom = 8.dp)) }
                if (installed.isEmpty()) {
                    item { CircularProgressIndicator(Modifier.padding(16.dp)) }
                } else {
                    appPickerItems(installed, selected, query, onToggle)
                }
            }
        }
    }
}

@Composable
private fun Caption(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun formatSeconds(duration: Duration): String =
    if (duration.seconds < 60) "${duration.seconds} s" else Formats.duration(duration)

private fun minutes(vararg values: Long) = values.map(Duration::ofMinutes)

/** En build debug, des valeurs très courtes permettent de tester sans attendre. */
private val DEBUG_SHORT = if (BuildConfig.DEBUG) minutes(1, 2) else emptyList()

private val GAP_OPTIONS = listOf(30L, 60L, 120L, 300L).map(Duration::ofSeconds)
private val PAUSE_DURATION_OPTIONS = listOf(5L, 10L, 20L).map(Duration::ofSeconds)
private val PAUSE_EVERY_OPTIONS = DEBUG_SHORT + minutes(5, 10, 15, 20, 30)
private val SESSION_LIMIT_OPTIONS = DEBUG_SHORT + minutes(10, 15, 20, 25, 30, 45, 60)
private val COOLDOWN_OPTIONS = DEBUG_SHORT.take(1) + minutes(5, 10, 15, 30, 60)
private val DAILY_LIMIT_OPTIONS = minutes(15, 30, 45, 60, 90, 120)
