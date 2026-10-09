package com.lucas.mobilemanager.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucas.mobilemanager.R
import com.lucas.mobilemanager.domain.model.StrictSession
import com.lucas.mobilemanager.domain.onboarding.OnboardingState
import com.lucas.mobilemanager.ui.format.Formats
import com.lucas.mobilemanager.ui.format.profileSummary
import com.lucas.mobilemanager.ui.theme.LocalExtendedColors
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenPermissions: () -> Unit,
    onOpenSettings: () -> Unit,
    onStartSession: () -> Unit,
    onRequestOverride: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                actions = {
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
            }
            items(profiles, key = { it.profile.id }) { item ->
                ProfileCard(
                    item,
                    editable = session == null,
                    onToggle = { viewModel.setEnabled(item.profile.id, it) },
                )
            }
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
private fun ProfileCard(item: ProfileItem, editable: Boolean, onToggle: (Boolean) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(item.profile.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    profileSummary(item.profile),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (item.activeNow) {
                    Text(
                        stringResource(R.string.profile_active_now),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = item.profile.enabled, onCheckedChange = onToggle, enabled = editable)
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
