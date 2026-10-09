package com.lucas.mobilemanager.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucas.mobilemanager.R
import com.lucas.mobilemanager.domain.session.StrictSessionPolicy
import com.lucas.mobilemanager.domain.session.StrictSessionPolicy.OverrideState
import com.lucas.mobilemanager.ui.format.Formats
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Parcours de dérogation : délai de réflexion, puis courte fenêtre pour confirmer.
 * Ton neutre : on ne juge pas, on laisse le temps de reconsidérer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverrideScreen(
    onBack: () -> Unit,
    viewModel: OverrideViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val current = state ?: return

    // Plus de session (terminée ou dérogée) : retour à l'accueil.
    LaunchedEffect(current.session == null) { if (current.session == null) onBack() }
    val session = current.session ?: return
    val endTime = Formats.TIME.format(session.endsAt.atZone(ZoneId.systemDefault()))
    val delayMinutes = session.reflectionDelay.toMinutes()
    val windowMinutes = StrictSessionPolicy.OVERRIDE_WINDOW.toMinutes()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.override_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.navigate_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (val override = current.override) {
                OverrideState.NotRequested -> {
                    Body(stringResource(R.string.override_intro, endTime, delayMinutes, windowMinutes))
                    Spacer(Modifier.height(32.dp))
                    OutlinedButton(onClick = viewModel::request, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.override_start_delay))
                    }
                    TextButton(onClick = onBack) { Text(stringResource(R.string.override_keep_going)) }
                }
                is OverrideState.Waiting -> {
                    Countdown(override.availableAt, current.now)
                    Spacer(Modifier.height(16.dp))
                    Body(stringResource(R.string.override_waiting))
                    Spacer(Modifier.height(32.dp))
                    Button(onClick = viewModel::cancel, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.override_cancel))
                    }
                }
                is OverrideState.Available -> {
                    Body(stringResource(R.string.override_available, endTime))
                    Spacer(Modifier.height(8.dp))
                    Countdown(override.expiresAt, current.now)
                    Spacer(Modifier.height(32.dp))
                    Button(onClick = viewModel::cancel, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.override_cancel))
                    }
                    OutlinedButton(onClick = viewModel::end, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.override_end))
                    }
                }
                OverrideState.Expired -> {
                    Body(stringResource(R.string.override_expired, delayMinutes))
                    Spacer(Modifier.height(32.dp))
                    OutlinedButton(onClick = viewModel::request, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.override_restart_delay))
                    }
                    TextButton(onClick = onBack) { Text(stringResource(R.string.override_keep_going)) }
                }
            }
        }
    }
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
}

/** « 04:12 » jusqu'à [target]. */
@Composable
private fun Countdown(target: Instant, now: Instant) {
    val remaining = Duration.between(now, target).coerceAtLeast(Duration.ZERO)
    val seconds = remaining.seconds
    Text(
        "%02d:%02d".format(seconds / 60, seconds % 60),
        style = MaterialTheme.typography.displayMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}
