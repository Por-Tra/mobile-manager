package com.lucas.mobilemanager.ui.diagnostic

import android.content.ActivityNotFoundException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucas.mobilemanager.R
import com.lucas.mobilemanager.domain.mode.ModePolicy
import com.lucas.mobilemanager.domain.onboarding.PermissionKind
import com.lucas.mobilemanager.domain.onboarding.PermissionStatus
import com.lucas.mobilemanager.domain.rules.Decision
import com.lucas.mobilemanager.domain.zones.ProtectionProbe
import com.lucas.mobilemanager.ui.lock.LockBanner
import com.lucas.mobilemanager.ui.lock.rememberCanModify
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtectionTestScreen(
    onBack: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenAdvanced: () -> Unit,
    viewModel: ProtectionTestViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val targets by viewModel.targets.collectAsStateWithLifecycle()
    val selection by viewModel.selected.collectAsStateWithLifecycle()
    val run by viewModel.run.collectAsStateWithLifecycle()
    val connected by viewModel.connected.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val locked by viewModel.locked.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val decision by viewModel.zoneDecision.collectAsStateWithLifecycle()
    val appBlocked by viewModel.appBlocked.collectAsStateWithLifecycle()
    var statuses by remember { mutableStateOf(viewModel.permissionChecker.currentStatuses()) }
    var detailsExpanded by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val target = targets.firstOrNull { it.packageName == selection.first && it.zoneId == selection.second }
    val active = run?.active == true
    val canModify = rememberCanModify() && !locked
    val resultPosition = remember { BringIntoViewRequester() }
    LaunchedEffect(run?.id, active) {
        if (run != null && !active) {
            delay(150)
            resultPosition.bringIntoView()
        }
    }
    LifecycleResumeEffect(target, connected) {
        statuses = viewModel.permissionChecker.currentStatuses()
        viewModel.refreshDecision(target)
        onPauseOrDispose { }
    }
    LaunchedEffect(targets) {
        if (target == null && targets.size == 1 && !active) viewModel.select(targets.single())
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.probe_title)) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.navigate_back)) }
        })
    }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.probe_intro))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.probe_preflight), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(if (connected) R.string.protection_service_connected else R.string.probe_disconnected))
                    if (mode?.let(ModePolicy::blocksEnabled) != true) Text(stringResource(R.string.protection_free))
                    if (statuses[PermissionKind.ACCESSIBILITY] == PermissionStatus.MISSING) Text(stringResource(R.string.probe_accessibility_missing))
                    if (statuses[PermissionKind.USAGE_STATS] == PermissionStatus.MISSING) Text(stringResource(R.string.probe_usage_missing))
                    if (statuses[PermissionKind.BATTERY_OPTIMIZATION] == PermissionStatus.MISSING) Text(stringResource(R.string.probe_battery))
                    TextButton(onClick = onOpenPermissions) { Text(stringResource(R.string.protection_fix)) }
                }
            }
            Text(stringResource(R.string.probe_target), style = MaterialTheme.typography.titleMedium)
            if (targets.isEmpty()) Text(stringResource(R.string.probe_no_apps))
            targets.forEach { option ->
                Row(Modifier.fillMaxWidth().selectable(selected = target == option, enabled = !active && canModify,
                    role = androidx.compose.ui.semantics.Role.RadioButton, onClick = { viewModel.select(option) }),
                    verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = target == option, onClick = null, enabled = !active && canModify)
                    Text(option.label)
                }
            }
            if (appBlocked) Text(stringResource(R.string.probe_app_blocked))
            if (decision != null) Text(stringResource(if (decision is Decision.Blocked) R.string.probe_policy_blocked else R.string.probe_policy_allowed))
            LockBanner()
            if (locked) Text(stringResource(R.string.probe_locked))
            message?.let { Text(stringResource(it)) }
            Text(stringResource(R.string.probe_instructions))
            TextButton(onClick = { detailsExpanded = !detailsExpanded }) { Text(stringResource(R.string.probe_details)) }
            if (detailsExpanded) {
                Text(stringResource(R.string.probe_privacy), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.probe_system_limits), style = MaterialTheme.typography.bodySmall)
            }
            if (active) {
                Text(stringResource(R.string.probe_running))
                OutlinedButton(onClick = { viewModel.store.finish() }) { Text(stringResource(R.string.probe_stop)) }
            } else {
                Button(enabled = target != null && connected && canModify, onClick = {
                    target?.let { selected -> viewModel.start(selected) {
                        try {
                            val intent = context.packageManager.getLaunchIntentForPackage(selected.packageName)
                            if (intent == null) viewModel.launchFailed() else context.startActivity(intent)
                        } catch (_: ActivityNotFoundException) {
                            viewModel.launchFailed()
                        } catch (_: SecurityException) {
                            viewModel.launchFailed()
                        }
                    } }
                }) { Text(stringResource(R.string.probe_start)) }
            }
            run?.let { current ->
                Card(Modifier.fillMaxWidth().bringIntoViewRequester(resultPosition)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.probe_results), style = MaterialTheme.typography.titleMedium)
                        Text("${current.packageName} · ${current.zoneId}", style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(current.probe.finding(connected).messageResource()))
                        Text(stringResource(R.string.probe_counts, current.probe.events, current.probe.snapshots,
                            current.probe.maxVisibleNodes, current.probe.maxViewIds))
                        if (current.probe.maxVisibleNodes > 0 && current.probe.maxViewIds == 0) Text(stringResource(R.string.probe_no_ids))
                        if (current.probe.fallbackUsed) Text(stringResource(R.string.probe_fallback))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = current.probe.overlayConfirmed, enabled = current.probe.overlayAttached,
                                onCheckedChange = viewModel.store::confirmOverlay)
                            Text(stringResource(R.string.probe_confirm_overlay), Modifier.weight(1f))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = current.probe.protectionConfirmed, enabled = current.probe.actionAccepted,
                                onCheckedChange = viewModel.store::confirmProtection)
                            Text(stringResource(R.string.probe_confirm_protection), Modifier.weight(1f))
                        }
                        Text(stringResource(R.string.probe_result_scope), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = {
                            val appVersion = runCatching { context.packageManager.getPackageInfo(current.packageName, 0).versionName }.getOrNull()
                            val report = buildString {
                                appendLine("Mobile Manager ${com.lucas.mobilemanager.BuildConfig.VERSION_NAME} (${com.lucas.mobilemanager.BuildConfig.VERSION_CODE})")
                                appendLine("Android ${android.os.Build.VERSION.RELEASE} / API ${android.os.Build.VERSION.SDK_INT}")
                                appendLine("${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                                appendLine("${current.packageName} $appVersion / ${current.zoneId}")
                                appendLine("connected=$connected; mode=${mode?.selectedMode}; active=${current.active}")
                                appendLine(viewModel.permissionChecker.currentStatuses().toString())
                                appendLine(current.probe.toString())
                                append(resources.getString(current.probe.finding(connected).messageResource()))
                            }
                            context.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(
                                android.content.ClipData.newPlainText(resources.getString(R.string.probe_results), report))
                            android.widget.Toast.makeText(context, R.string.probe_copied, android.widget.Toast.LENGTH_SHORT).show()
                        }) { Text(stringResource(R.string.probe_copy)) }
                        if (!active) TextButton(onClick = viewModel.store::acknowledgeReview) { Text(stringResource(R.string.probe_reviewed)) }
                    }
                }
            }
            TextButton(onClick = onOpenAdvanced) { Text(stringResource(R.string.probe_advanced)) }
        }
    }
}

private fun ProtectionProbe.Finding.messageResource(): Int = when (this) {
    ProtectionProbe.Finding.SERVICE_DISCONNECTED -> R.string.probe_disconnected
    ProtectionProbe.Finding.CONFIGURATION -> R.string.probe_configuration
    ProtectionProbe.Finding.NO_EVENTS -> R.string.probe_no_events
    ProtectionProbe.Finding.NO_WINDOW -> R.string.probe_no_window
    ProtectionProbe.Finding.NO_VISIBLE_NODES -> R.string.probe_no_nodes
    ProtectionProbe.Finding.ENTRY_MASKED -> R.string.probe_entry_masked
    ProtectionProbe.Finding.ZONE_NOT_SEEN -> R.string.probe_no_zone
    ProtectionProbe.Finding.ALLOWED -> R.string.probe_allowed
    ProtectionProbe.Finding.ACTION_NOT_SEEN -> R.string.probe_no_action
    ProtectionProbe.Finding.CONFIRM_OVERLAY -> R.string.probe_overlay_unconfirmed
    ProtectionProbe.Finding.CONFIRM_ACTION -> R.string.probe_action_unconfirmed
    ProtectionProbe.Finding.OBSERVED -> R.string.probe_observed
}

@Composable
fun ProtectionReviewCard(onOpenTest: () -> Unit, viewModel: ProtectionTestViewModel = hiltViewModel()) {
    val needed by viewModel.reviewNeeded.collectAsStateWithLifecycle()
    if (!needed) return
    Card(Modifier.fillMaxWidth().padding(12.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.probe_review_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.probe_review_body))
            TextButton(onClick = onOpenTest) { Text(stringResource(R.string.probe_title)) }
            TextButton(onClick = viewModel.store::acknowledgeReview) { Text(stringResource(R.string.probe_later)) }
        }
    }
}
