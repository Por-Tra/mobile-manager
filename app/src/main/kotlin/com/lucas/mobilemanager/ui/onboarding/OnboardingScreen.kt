package com.lucas.mobilemanager.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucas.mobilemanager.R
import com.lucas.mobilemanager.domain.onboarding.OnboardingState
import com.lucas.mobilemanager.domain.onboarding.PermissionItem
import com.lucas.mobilemanager.domain.onboarding.PermissionKind
import com.lucas.mobilemanager.domain.onboarding.PermissionRequirement
import com.lucas.mobilemanager.domain.onboarding.PermissionStatus
import com.lucas.mobilemanager.permissions.PermissionIntents

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        // Refus définitif : Android n'affiche plus la demande, on ouvre les réglages de notification.
        if (!granted) PermissionIntents.launch(context, PermissionIntents.appNotificationSettings(context))
        viewModel.refresh()
    }

    OnboardingContent(
        state = state,
        isSamsung = Build.MANUFACTURER.equals("samsung", ignoreCase = true),
        onGrant = { kind ->
            val intent = PermissionIntents.forPermission(context, kind)
            when {
                intent != null -> PermissionIntents.launch(context, intent)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        onOpenAppDetails = {
            PermissionIntents.launch(context, PermissionIntents.appDetails(context))
        },
        onOpenBatterySettings = {
            PermissionIntents.launch(context, PermissionIntents.batterySettings())
        },
        onFinish = { viewModel.finish(onFinished) },
    )
}

@Composable
private fun OnboardingContent(
    state: OnboardingState,
    isSamsung: Boolean,
    onGrant: (PermissionKind) -> Unit,
    onOpenAppDetails: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onFinish: () -> Unit,
) {
    val accessibilityMissing = state.items
        .first { it.kind == PermissionKind.ACCESSIBILITY }
        .status == PermissionStatus.MISSING

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 20.dp),
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(Modifier.padding(top = 24.dp, bottom = 8.dp)) {
                    Text(
                        stringResource(R.string.onboarding_title),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Spacer(Modifier.padding(4.dp))
                    Text(
                        stringResource(R.string.onboarding_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.padding(8.dp))
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.padding(4.dp))
                    Text(
                        stringResource(
                            R.string.onboarding_progress,
                            state.grantedCount,
                            state.applicableCount,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(state.items.filter { it.status != PermissionStatus.NOT_APPLICABLE }, key = { it.kind }) { item ->
                PermissionCard(item = item, onGrant = { onGrant(item.kind) })

                if (item.kind == PermissionKind.ACCESSIBILITY &&
                    accessibilityMissing &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ) {
                    Spacer(Modifier.padding(6.dp))
                    HintCard(
                        title = R.string.restricted_settings_title,
                        body = R.string.restricted_settings_body,
                        action = R.string.restricted_settings_action,
                        onAction = onOpenAppDetails,
                    )
                }
                if (item.kind == PermissionKind.BATTERY_OPTIMIZATION && isSamsung) {
                    Spacer(Modifier.padding(6.dp))
                    HintCard(
                        title = R.string.samsung_battery_title,
                        body = R.string.samsung_battery_body,
                        action = R.string.samsung_battery_action,
                        onAction = onOpenBatterySettings,
                    )
                }
            }

            if (state.canMonitor) {
                item {
                    Text(
                        stringResource(R.string.onboarding_ready),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
        }

        Column(Modifier.padding(vertical = 16.dp)) {
            Button(
                onClick = onFinish,
                enabled = state.canMonitor,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.onboarding_continue))
            }
            if (!state.canMonitor) {
                TextButton(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.onboarding_later))
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(item: PermissionItem, onGrant: () -> Unit) {
    val texts = item.kind.texts()
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            RequirementLabel(item.kind.requirement)
            Spacer(Modifier.padding(4.dp))
            Text(stringResource(texts.title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.padding(2.dp))
            Text(
                stringResource(texts.body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.padding(6.dp))
            if (item.status == PermissionStatus.GRANTED) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.permission_granted),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                Button(onClick = onGrant) { Text(stringResource(R.string.permission_grant)) }
            }
        }
    }
}

@Composable
private fun RequirementLabel(requirement: PermissionRequirement) {
    val label = when (requirement) {
        PermissionRequirement.REQUIRED -> R.string.permission_required
        PermissionRequirement.RECOMMENDED -> R.string.permission_recommended
        PermissionRequirement.STRICT_MODE -> R.string.permission_strict_mode
    }
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            stringResource(label),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun HintCard(
    @StringRes title: Int,
    @StringRes body: Int,
    @StringRes action: Int,
    onAction: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.padding(2.dp))
            Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.padding(6.dp))
            OutlinedButton(onClick = onAction) { Text(stringResource(action)) }
        }
    }
}

private data class PermissionTexts(@StringRes val title: Int, @StringRes val body: Int)

private fun PermissionKind.texts() = when (this) {
    PermissionKind.ACCESSIBILITY ->
        PermissionTexts(R.string.permission_accessibility_title, R.string.permission_accessibility_body)
    PermissionKind.USAGE_STATS ->
        PermissionTexts(R.string.permission_usage_stats_title, R.string.permission_usage_stats_body)
    PermissionKind.BATTERY_OPTIMIZATION ->
        PermissionTexts(R.string.permission_battery_title, R.string.permission_battery_body)
    PermissionKind.OVERLAY ->
        PermissionTexts(R.string.permission_overlay_title, R.string.permission_overlay_body)
    PermissionKind.NOTIFICATIONS ->
        PermissionTexts(R.string.permission_notifications_title, R.string.permission_notifications_body)
    PermissionKind.DEVICE_ADMIN ->
        PermissionTexts(R.string.permission_device_admin_title, R.string.permission_device_admin_body)
}
