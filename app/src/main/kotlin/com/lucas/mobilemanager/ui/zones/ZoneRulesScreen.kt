package com.lucas.mobilemanager.ui.zones

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucas.mobilemanager.R
import com.lucas.mobilemanager.ui.apps.AppIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZoneRulesScreen(onBack: () -> Unit, viewModel: ZoneRulesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.zones_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.navigate_back)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Caption(stringResource(R.string.zones_intro)) }
            if (state.locked) item { Caption(stringResource(R.string.locked_during_session)) }
            items(state.zones, key = { "${it.packageName}/${it.zoneId}" }) { zone ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(zone.packageName, Modifier.size(32.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            stringResource(R.string.zones_item, zone.appLabel, zone.zoneLabel),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        listOf(true, false).forEachIndexed { index, blocked ->
                            SegmentedButton(
                                selected = zone.blocked == blocked,
                                onClick = { viewModel.setBlocked(zone, blocked) },
                                enabled = !state.locked,
                                shape = SegmentedButtonDefaults.itemShape(index, 2),
                                label = { Text(stringResource(if (blocked) R.string.rule_block else R.string.rule_allow)) },
                            )
                        }
                    }
                }
            }
            item { Caption(stringResource(R.string.zones_fragile)) }
        }
    }
}

@Composable
private fun Caption(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
