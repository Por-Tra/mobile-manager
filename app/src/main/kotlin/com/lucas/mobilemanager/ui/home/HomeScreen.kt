package com.lucas.mobilemanager.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import com.lucas.mobilemanager.domain.dashboard.DashboardSummary
import com.lucas.mobilemanager.domain.dashboard.Period
import com.lucas.mobilemanager.domain.model.Profile
import com.lucas.mobilemanager.domain.model.StrictSession
import com.lucas.mobilemanager.domain.onboarding.OnboardingState
import com.lucas.mobilemanager.ui.dashboard.comparisonText
import com.lucas.mobilemanager.ui.format.Formats
import com.lucas.mobilemanager.ui.lock.LockBanner
import com.lucas.mobilemanager.ui.lock.rememberCanModify
import com.lucas.mobilemanager.ui.theme.LocalExtendedColors
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenPermissions: () -> Unit,
    onOpenSettings: () -> Unit,
    onStartSession: () -> Unit,
    onRequestOverride: () -> Unit,
    onEditProfile: (Long) -> Unit,
    onNewProfile: () -> Unit,
    onEditDefaultRules: () -> Unit,
    onOpenDashboard: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val today by viewModel.today.collectAsStateWithLifecycle()
    var activating by remember { mutableStateOf<Profile?>(null) }
    var deleting by remember { mutableStateOf<Profile?>(null) }
    // Activer un profil reste libre ; le modifier demande le code du proche, s'il est défini.
    val canActivate = session == null
    val editable = canActivate && rememberCanModify()

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                actions = {
                    IconButton(onClick = onOpenDashboard) {
                        Icon(Icons.Outlined.BarChart, contentDescription = stringResource(R.string.dashboard_title))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.home_open_settings),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        ) {
            item { StatusCard(permissions, onOpenPermissions) }
            today?.let { summary -> item { TodayCard(summary, onOpenDashboard) } }
            item { SessionCard(session, onStartSession, onRequestOverride) }
            item {
                Text(
                    stringResource(R.string.home_profiles),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
                if (session != null) {
                    Text(
                        stringResource(R.string.locked_during_session),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LockBanner(Modifier.padding(top = 8.dp))
            }
            itemsIndexed(profiles, key = { _, item -> item.profile.id }) { index, item ->
                ProfileCard(
                    item = item,
                    editable = editable,
                    canActivate = canActivate,
                    canMoveUp = index > 0,
                    canMoveDown = index < profiles.lastIndex,
                    onOpen = { onEditProfile(item.profile.id) },
                    onToggle = { viewModel.setEnabled(item.profile.id, it) },
                    onActivate = { activating = item.profile },
                    onDeactivate = viewModel::deactivateManual,
                    onMoveUp = { viewModel.move(item.profile.id, -1) },
                    onMoveDown = { viewModel.move(item.profile.id, +1) },
                    onDelete = { deleting = item.profile },
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onNewProfile, enabled = editable) {
                        Text(stringResource(R.string.home_new_profile))
                    }
                    TextButton(onClick = onEditDefaultRules, enabled = canActivate) {
                        Text(stringResource(R.string.home_default_rules))
                    }
                }
                Text(
                    stringResource(R.string.home_priority_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                )
            }
        }
    }

    activating?.let { profile ->
        ManualActivationSheet(
            profile = profile,
            replacing = profiles.firstOrNull { it.manual && it.profile.id != profile.id }?.profile,
            onDismiss = { activating = null },
            onChoose = { duration ->
                viewModel.activateManually(profile, duration)
                activating = null
            },
        )
    }
    deleting?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.profile_delete_title, profile.name)) },
            text = { Text(stringResource(R.string.profile_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(profile.id)
                    deleting = null
                }) { Text(stringResource(R.string.profile_delete_confirm)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** « Aujourd'hui : 2 h 10 » + comparaison neutre avec hier ; ouvre le tableau de bord. */
@Composable
private fun TodayCard(summary: DashboardSummary, onOpen: () -> Unit) {
    Card(
        onClick = onOpen,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.home_today), style = MaterialTheme.typography.labelLarge)
            Text(Formats.duration(summary.total), style = MaterialTheme.typography.headlineMedium)
            Text(comparisonText(summary, Period.TODAY), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun StatusCard(permissions: OnboardingState, onOpenPermissions: () -> Unit) {
    val ready = permissions.canMonitor
    // Permissions manquantes : couleur neutre, pas d'alerte rouge.
    val container = if (ready) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        LocalExtendedColors.current.limitReachedContainer
    }
    val onContainer = if (ready) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        LocalExtendedColors.current.onLimitReachedContainer
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = container, contentColor = onContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(if (ready) R.string.home_status_ready else R.string.home_status_incomplete),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.padding(2.dp))
            Text(
                stringResource(
                    if (ready) R.string.home_status_ready_body else R.string.home_status_incomplete_body,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!ready) {
                Spacer(Modifier.padding(6.dp))
                OutlinedButton(onClick = onOpenPermissions) {
                    Text(stringResource(R.string.home_open_permissions))
                }
            }
        }
    }
}

@Composable
private fun SessionCard(session: StrictSession?, onStart: () -> Unit, onRequestOverride: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (session == null) {
                Text(stringResource(R.string.session_card_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.padding(2.dp))
                Text(stringResource(R.string.session_card_body), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.padding(6.dp))
                Button(onClick = onStart) { Text(stringResource(R.string.session_card_start)) }
            } else {
                val endTime = Formats.TIME.format(session.endsAt.atZone(ZoneId.systemDefault()))
                Text(stringResource(R.string.session_card_running), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.session_card_until, endTime),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    pluralStringResource(
                        R.plurals.session_card_apps,
                        session.blockedPackages.size,
                        session.blockedPackages.size,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = onRequestOverride, modifier = Modifier.padding(top = 4.dp)) {
                    Text(stringResource(R.string.session_card_override))
                }
            }
        }
    }
}
