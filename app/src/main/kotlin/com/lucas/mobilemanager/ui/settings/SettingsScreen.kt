package com.lucas.mobilemanager.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucas.mobilemanager.BuildConfig
import com.lucas.mobilemanager.R
import com.lucas.mobilemanager.ui.mode.ModeCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenZones: () -> Unit,
    onOpenLock: () -> Unit,
    onOpenDiagnostic: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val versionName = remember {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val sessionRunning by viewModel.sessionRunning.collectAsStateWithLifecycle()
            val lockStatus by viewModel.lockStatus.collectAsStateWithLifecycle()

            ModeCard()
            ProtectionDiagnostic(viewModel, onOpenPermissions)
            WellbeingPreferences(viewModel, enabled = !sessionRunning && lockStatus?.canModify == true)

            SettingsHeading(R.string.settings_protection)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(stringResource(R.string.settings_permissions)) },
                    supportingContent = { Text(stringResource(R.string.settings_permissions_body)) },
                    leadingContent = { Icon(Icons.Outlined.Security, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onOpenPermissions),
                )

                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(stringResource(R.string.lock_title)) },
                    supportingContent = {
                        Text(stringResource(if (lockStatus?.isSet == true) R.string.lock_entry_body_on else R.string.lock_entry_body_off))
                    },
                    leadingContent = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onOpenLock),
                )
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(stringResource(R.string.zones_title)) },
                    supportingContent = { Text(stringResource(R.string.zones_entry_body)) },
                    leadingContent = { Icon(Icons.Outlined.Layers, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onOpenZones),
                )
            }
            SettingsHeading(R.string.settings_application)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) { LanguagePreference() }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) { UpdatePreference(enabled = !sessionRunning && lockStatus?.canModify == true) }
            SettingsHeading(R.string.settings_advanced)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                UninstallPreference(enabled = !sessionRunning && lockStatus?.canModify == true, viewModel = viewModel)
            }
            ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text(stringResource(R.string.diagnostic_title)) },
                supportingContent = { Text(stringResource(R.string.diagnostic_entry_body)) },
                leadingContent = { Icon(Icons.Outlined.BugReport, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onOpenDiagnostic),
            )
            if (BuildConfig.DEBUG) {
                DebugTools(
                    lockedReason = when {
                        sessionRunning -> R.string.locked_during_session
                        lockStatus?.canModify != true -> R.string.lock_banner_locked
                        else -> null
                    },
                    onCreateTestProfile = viewModel::createTestProfile,
                    onDeleteTestProfile = viewModel::deleteTestProfile,
                )
            }
            Text(
                stringResource(R.string.settings_version, versionName),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
            )
        }
    }
}

@Composable
private fun DebugTools(@StringRes lockedReason: Int?, onCreateTestProfile: () -> Unit, onDeleteTestProfile: () -> Unit) {
    val enabled = lockedReason == null
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(stringResource(R.string.settings_debug_title), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(R.string.settings_debug_create_test_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (lockedReason != null) {
            Text(
                stringResource(lockedReason),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            OutlinedButton(onClick = onCreateTestProfile, enabled = enabled) {
                Text(stringResource(R.string.settings_debug_create_test))
            }
            TextButton(onClick = onDeleteTestProfile, enabled = enabled) {
                Text(stringResource(R.string.settings_debug_delete_test))
            }
        }
    }
}

@Composable
private fun SettingsHeading(@StringRes id: Int) {
    Text(stringResource(id), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
}
