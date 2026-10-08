package com.lucas.mobilemanager.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucas.mobilemanager.R
import com.lucas.mobilemanager.ui.theme.LocalExtendedColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenPermissions: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()

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
        Column(
            Modifier
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
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
    }
}
