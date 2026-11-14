package com.lucas.mobilemanager.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucas.mobilemanager.R
import com.lucas.mobilemanager.domain.model.Profile
import com.lucas.mobilemanager.ui.lock.rememberCanModify

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(
    onEditProfile: (Long) -> Unit,
    onNewProfile: () -> Unit,
    onEditDefaultRules: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    var activating by remember { mutableStateOf<Profile?>(null) }
    var deleting by remember { mutableStateOf<Profile?>(null) }
    // Les commandes qui peuvent remplacer ou lever une restriction demandent le déverrouillage.
    val canActivate = session == null
    val editable = canActivate && rememberCanModify()

    LifecycleResumeEffect(Unit) {
        viewModel.refresh(loadToday = false)
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            com.lucas.mobilemanager.ui.components.PageTopBar(stringResource(R.string.nav_profiles))
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        ) {
            item {
                com.lucas.mobilemanager.ui.components.PageIntro(stringResource(R.string.profiles_heading), stringResource(R.string.profiles_intro))
                com.lucas.mobilemanager.ui.lock.LockBanner()
                if (session != null) Text(stringResource(R.string.locked_during_session), style = MaterialTheme.typography.bodySmall)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                    Button(onClick = onNewProfile, enabled = editable, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.home_new_profile)) }
                    OutlinedButton(onClick = onEditDefaultRules, enabled = canActivate, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.home_default_rules)) }
                }
                if (profiles.isEmpty()) Text(stringResource(R.string.profiles_empty), modifier = Modifier.padding(vertical = 16.dp))
            }
            itemsIndexed(profiles, key = { _, item -> item.profile.id }) { index, item ->
                ProfileCard(
                    item = item,
                    editable = editable,
                    canActivate = editable,
                    canOpen = canActivate,
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

