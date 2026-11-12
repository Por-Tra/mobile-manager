package com.lucas.mobilemanager.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucas.mobilemanager.R
import com.lucas.mobilemanager.ui.apps.AppSearchField
import com.lucas.mobilemanager.ui.apps.appPickerItems
import com.lucas.mobilemanager.ui.components.LimitPicker
import com.lucas.mobilemanager.ui.lock.LockBanner

@Composable
fun WellbeingPreferences(viewModel: SettingsViewModel, enabled: Boolean) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val prefs = preferences ?: return
    var editing by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var query by rememberSaveable { mutableStateOf("") }
    LockBanner()
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.wellbeing_title), style = MaterialTheme.typography.titleMedium)
            ListItem(
                headlineContent = { Text(stringResource(R.string.shared_budget_title)) },
                supportingContent = { Text(stringResource(R.string.shared_budget_body)) },
                trailingContent = { Switch(prefs.sharedBudgetEnabled, { viewModel.saveBudget(it, prefs.sharedBudgetMinutes, prefs.sharedBudgetPackages) }, enabled = enabled) },
            )
            LimitPicker(prefs.sharedBudgetMinutes, enabled, { viewModel.saveBudget(prefs.sharedBudgetEnabled, it, prefs.sharedBudgetPackages) })
            TextButton(onClick = { selected = prefs.sharedBudgetPackages; query = ""; editing = "budget" }, enabled = enabled) {
                Text(stringResource(R.string.wellbeing_select_apps, prefs.sharedBudgetPackages.size))
            }
            if (prefs.sharedBudgetEnabled && prefs.sharedBudgetPackages.isEmpty()) Text(stringResource(R.string.wellbeing_no_apps))
            ListItem(
                headlineContent = { Text(stringResource(R.string.opening_pause_title)) },
                supportingContent = { Text(stringResource(R.string.opening_pause_setting_body)) },
                trailingContent = { Switch(prefs.openingPauseEnabled, { viewModel.savePause(it, prefs.openingPauseSeconds, prefs.openingPausePackages) }, enabled = enabled) },
            )
            var durationMenu by remember { mutableStateOf(false) }
            androidx.compose.foundation.layout.Box {
                TextButton(onClick = { durationMenu = true }, enabled = enabled) {
                    Text(stringResource(R.string.opening_pause_duration, prefs.openingPauseSeconds))
                }
                androidx.compose.material3.DropdownMenu(expanded = durationMenu, onDismissRequest = { durationMenu = false }) {
                    listOf(3, 5, 10, 15, 30).forEach { seconds ->
                        androidx.compose.material3.DropdownMenuItem(text = { Text(stringResource(R.string.opening_pause_seconds, seconds)) }, onClick = {
                            viewModel.savePause(prefs.openingPauseEnabled, seconds, prefs.openingPausePackages)
                            durationMenu = false
                        })
                    }
                }
            }
            TextButton(onClick = { selected = prefs.openingPausePackages; query = ""; editing = "pause" }, enabled = enabled) {
                Text(stringResource(R.string.wellbeing_select_apps, prefs.openingPausePackages.size))
            }
            if (prefs.openingPauseEnabled && prefs.openingPausePackages.isEmpty()) Text(stringResource(R.string.wellbeing_no_apps))
            ListItem(
                headlineContent = { Text(stringResource(R.string.limit_warning_title)) },
                supportingContent = { Text(stringResource(R.string.limit_warning_body)) },
                trailingContent = { Switch(prefs.limitWarningsEnabled, viewModel::setWarnings, enabled = enabled) },
            )
            if (!enabled) Text(stringResource(R.string.wellbeing_locked))
        }
    }
    if (editing != null) {
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(if (editing == "budget") R.string.shared_budget_title else R.string.opening_pause_title)) },
            text = {
                Column {
                    AppSearchField(query, { query = it })
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        appPickerItems(apps, selected, query) { pkg -> selected = if (pkg in selected) selected - pkg else selected + pkg }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editing == "budget") viewModel.saveBudget(prefs.sharedBudgetEnabled, prefs.sharedBudgetMinutes, selected)
                    else viewModel.savePause(prefs.openingPauseEnabled, prefs.openingPauseSeconds, selected)
                    editing = null
                }, enabled = enabled) { Text(stringResource(R.string.wellbeing_save)) }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
