package com.lucas.mobilemanager.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lucas.mobilemanager.R
import com.lucas.mobilemanager.domain.dashboard.DashboardSummary
import com.lucas.mobilemanager.domain.dashboard.Period
import com.lucas.mobilemanager.domain.model.StrictSession
import com.lucas.mobilemanager.domain.onboarding.OnboardingState
import com.lucas.mobilemanager.ui.format.Formats
import com.lucas.mobilemanager.ui.theme.LocalExtendedColors
import java.time.ZoneId

/** « Aujourd'hui : 2 h 10 » + comparaison neutre avec hier ; ouvre le tableau de bord. */
@Composable
internal fun TodayCard(summary: DashboardSummary, onOpen: () -> Unit) {
    Card(
        onClick = onOpen,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        com.lucas.mobilemanager.ui.components.UsageOverview(summary, Period.TODAY)
    }
}

@Composable
internal fun StatusCard(permissions: OnboardingState, blocksEnabled: Boolean, onOpenPermissions: () -> Unit) {
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
            com.lucas.mobilemanager.ui.components.GraphicScene(
                if (blocksEnabled) com.lucas.mobilemanager.ui.components.SceneKind.SHIELD else com.lucas.mobilemanager.ui.components.SceneKind.BALANCE,
                Modifier.fillMaxWidth().height(84.dp),
            )
            Text(
                stringResource(if (!blocksEnabled) R.string.mode_free_status else if (ready) R.string.home_status_ready else R.string.home_status_incomplete),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.padding(2.dp))
            Text(
                stringResource(
                    if (!blocksEnabled) R.string.mode_free_status_body else if (ready) R.string.home_status_ready_body else R.string.home_status_incomplete_body,
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
internal fun SessionCard(session: StrictSession?, onStart: () -> Unit, onRequestOverride: () -> Unit, blocksEnabled: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (session == null) {
                Text(stringResource(R.string.session_card_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.padding(2.dp))
                Text(stringResource(R.string.session_card_body), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.padding(6.dp))
                if (!blocksEnabled) Text(stringResource(R.string.mode_session_disabled), style = MaterialTheme.typography.bodySmall)
                Button(onClick = onStart, enabled = blocksEnabled, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.session_card_start)) }
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
