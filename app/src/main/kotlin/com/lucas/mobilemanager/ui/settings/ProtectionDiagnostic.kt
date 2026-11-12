package com.lucas.mobilemanager.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucas.mobilemanager.R
import com.lucas.mobilemanager.domain.mode.ModePolicy
import com.lucas.mobilemanager.domain.onboarding.PermissionKind
import com.lucas.mobilemanager.domain.onboarding.PermissionStatus

@Composable
fun ProtectionDiagnostic(viewModel: SettingsViewModel, onOpenPermissions: () -> Unit) {
    var statuses by remember { mutableStateOf(viewModel.permissionChecker.currentStatuses()) }
    val connected by viewModel.monitorConnected.collectAsStateWithLifecycle()
    val mode by viewModel.modeSettings.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        statuses = viewModel.permissionChecker.currentStatuses()
        onPauseOrDispose { }
    }
    Card {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.protection_diagnostic_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(when {
                mode == null -> R.string.protection_loading
                mode?.let(ModePolicy::blocksEnabled) != true -> R.string.protection_free
                !connected -> R.string.protection_disconnected
                statuses[PermissionKind.USAGE_STATS] == PermissionStatus.MISSING -> R.string.protection_usage_missing
                statuses[PermissionKind.BATTERY_OPTIMIZATION] == PermissionStatus.MISSING -> R.string.protection_battery
                else -> R.string.protection_ready
            }))
            Text(stringResource(if (connected) R.string.protection_service_connected else R.string.protection_service_disconnected))
            PermissionKind.entries.forEach { kind ->
                val title = when (kind) {
                    PermissionKind.ACCESSIBILITY -> R.string.permission_accessibility_title
                    PermissionKind.USAGE_STATS -> R.string.permission_usage_stats_title
                    PermissionKind.BATTERY_OPTIMIZATION -> R.string.permission_battery_title
                    PermissionKind.OVERLAY -> R.string.permission_overlay_title
                    PermissionKind.NOTIFICATIONS -> R.string.permission_notifications_title
                    PermissionKind.DEVICE_ADMIN -> R.string.permission_device_admin_title
                }
                val status = when (statuses[kind]) {
                    PermissionStatus.GRANTED -> R.string.protection_granted
                    PermissionStatus.NOT_APPLICABLE -> R.string.protection_not_applicable
                    else -> R.string.protection_missing
                }
                Text(stringResource(R.string.protection_permission_status, stringResource(title), stringResource(status)), style = MaterialTheme.typography.bodySmall)
            }
            Text(stringResource(R.string.protection_diagnostic_scope), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onOpenPermissions) { Text(stringResource(R.string.protection_fix)) }
        }
    }
}
