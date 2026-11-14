package com.lucas.mobilemanager.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucas.mobilemanager.R
import com.lucas.mobilemanager.domain.mode.ModePolicy
import com.lucas.mobilemanager.ui.components.*
import com.lucas.mobilemanager.ui.mode.ModeCard

@Composable
fun HomeScreen(
    onOpenPermissions: () -> Unit,
    onStartSession: () -> Unit,
    onRequestOverride: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenAntiScroll: () -> Unit,
    onEditProfile: (Long) -> Unit,
    onOpenDashboard: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val globalRules by viewModel.globalRules.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val today by viewModel.today.collectAsStateWithLifecycle()
    val modeSettings by viewModel.modeSettings.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val blocksEnabled = modeSettings?.let(ModePolicy::blocksEnabled) == true
    LifecycleResumeEffect(Unit) { viewModel.refresh(); onPauseOrDispose { } }

    Scaffold(topBar = { PageTopBar(stringResource(R.string.app_name)) }) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                if (session == null) FocusHero(onStartSession, blocksEnabled)
                else SessionCard(session, onStartSession, onRequestOverride, blocksEnabled)
            }
            today?.let { summary -> item { TodayCard(summary, onOpenDashboard) } }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FeatureTile(stringResource(R.string.nav_profiles), stringResource(R.string.profiles_shortcut), Icons.Outlined.Layers, modifier = Modifier.weight(1f), onClick = onOpenProfiles)
                    FeatureTile(stringResource(R.string.nav_antiscroll), stringResource(R.string.antiscroll_shortcut), Icons.Outlined.TouchApp, mint = true, modifier = Modifier.weight(1f), onClick = onOpenAntiScroll)
                }
            }
            item { Text(stringResource(R.string.home_active_heading), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp)) }
            item { ModeCard() }
            item { StatusCard(permissions, blocksEnabled, onOpenPermissions) }
            // A scheduled profile is only enforcing when the selected mode enables blocks.
            if (blocksEnabled) {
                items(profiles.filter { it.activeNow }, key = { it.profile.id }) { item ->
                    Card(onClick = { onEditProfile(item.profile.id) }, enabled = session == null, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        ListItem(
                            headlineContent = { Text(item.profile.name) },
                            supportingContent = { Text(stringResource(R.string.profile_active_now)) },
                            leadingContent = { Icon(Icons.Outlined.Layers, null) },
                            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                        )
                    }
                }
            }
            if (blocksEnabled) item {
                val restrictionCount = globalRules.count { it.action != com.lucas.mobilemanager.domain.rules.RuleAction.Allow }
                Card(onClick = onOpenProfiles, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.home_default_rules)) },
                        supportingContent = { Text(pluralStringResource(R.plurals.home_global_summary, restrictionCount, restrictionCount)) },
                        leadingContent = { Icon(Icons.Outlined.Tune, null) },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                    )
                }
            }
            preferences?.let { prefs ->
                if (blocksEnabled && (prefs.sharedBudgetEnabled || prefs.openingPauseEnabled || prefs.limitWarningsEnabled)) item {
                    Card(onClick = onOpenAntiScroll, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.wellbeing_title), style = MaterialTheme.typography.titleMedium)
                            if (prefs.sharedBudgetEnabled) Text(stringResource(R.string.shared_budget_title))
                            if (prefs.openingPauseEnabled) Text(stringResource(R.string.opening_pause_title))
                            if (prefs.limitWarningsEnabled) Text(stringResource(R.string.limit_warning_title))
                        }
                    }
                }
            }
        }
    }
}
