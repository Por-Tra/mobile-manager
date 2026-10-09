package com.lucas.mobilemanager.ui.diagnostic

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucas.mobilemanager.R
import com.lucas.mobilemanager.domain.zones.UiNode
import com.lucas.mobilemanager.ui.format.Formats
import java.time.ZoneId

/**
 * Mode diagnostic : voir ce que voit le service dans une app (bandeau en direct + capture) et
 * réparer un détecteur de zone sans toucher au code.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticScreen(onBack: () -> Unit, viewModel: DiagnosticViewModel = hiltViewModel()) {
    val overlayEnabled by viewModel.overlayEnabled.collectAsStateWithLifecycle()
    val capture by viewModel.capture.collectAsStateWithLifecycle()
    val editorText by viewModel.editorText.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val isCustom by viewModel.isCustom.collectAsStateWithLifecycle()
    val locked by viewModel.locked.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current

    fun copy(text: String) {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("signal", text))
        Toast.makeText(context, resources.getString(R.string.diagnostic_copied, text), Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.diagnostic_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.navigate_back)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item { Caption(stringResource(R.string.diagnostic_intro)) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.diagnostic_overlay), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = overlayEnabled, onCheckedChange = viewModel::setOverlayEnabled)
                }
                Caption(stringResource(R.string.diagnostic_overlay_help))
            }

            // --- Capture ---
            item { Title(stringResource(R.string.diagnostic_capture_title)) }
            val current = capture
            if (current == null) {
                item { Caption(stringResource(R.string.diagnostic_no_capture)) }
            } else {
                item {
                    val time = Formats.TIME.format(current.capturedAt.atZone(ZoneId.systemDefault()))
                    Text(
                        current.match?.let {
                            stringResource(R.string.diagnostic_capture_zone, current.packageName, time, it.zone.label, it.signalIndex + 1)
                        } ?: stringResource(R.string.diagnostic_capture_no_zone, current.packageName, time),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Caption(stringResource(R.string.diagnostic_capture_help))
                    TextButton(onClick = viewModel::clearCapture) { Text(stringResource(R.string.diagnostic_clear)) }
                }
                val highlighted = current.match?.nodeIndices.orEmpty().toSet()
                itemsIndexed(current.nodes) { index, node ->
                    if (node.viewId != null || node.text != null || node.desc != null) {
                        NodeRow(node, highlighted = index in highlighted, onLongClick = { copy(DiagnosticViewModel.signalFor(node)) })
                    }
                }
            }

            // --- Éditeur ---
            item {
                Title(stringResource(R.string.diagnostic_editor_title))
                Caption(stringResource(if (isCustom) R.string.diagnostic_editor_custom else R.string.diagnostic_editor_shipped))
                if (locked) Caption(stringResource(R.string.locked_during_session))
            }
            item {
                OutlinedTextField(
                    value = editorText,
                    onValueChange = viewModel::setEditorText,
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    // Du JSON : pas de correction automatique (« anyOf » devenait « any of »).
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Uri, // Gboard ignore autoCorrectEnabled avec Ascii
                    ),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp),
                )
            }
            item {
                message?.let {
                    Text(
                        when (it) {
                            EditorMessage.Valid -> stringResource(R.string.diagnostic_editor_valid)
                            EditorMessage.Saved -> stringResource(R.string.diagnostic_editor_saved)
                            EditorMessage.Reset -> stringResource(R.string.diagnostic_editor_reset_done)
                            is EditorMessage.Invalid -> it.message
                        },
                        color = if (it is EditorMessage.Invalid) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = viewModel::check) { Text(stringResource(R.string.diagnostic_editor_check)) }
                    Button(onClick = viewModel::save, enabled = !locked) { Text(stringResource(R.string.diagnostic_editor_save)) }
                    TextButton(onClick = viewModel::reset, enabled = !locked && isCustom) {
                        Text(stringResource(R.string.diagnostic_editor_reset))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NodeRow(node: UiNode, highlighted: Boolean, onLongClick: () -> Unit) {
    val parts = listOfNotNull(
        node.viewId?.substringAfter(":id/")?.let { "#$it" },
        node.text?.let { "« ${it.take(40)} »" },
        node.desc?.let { "desc=« ${it.take(40)} »" },
        "sélectionné".takeIf { node.selected },
        "cliquable".takeIf { node.clickable },
        "hors écran (ignoré)".takeIf { !node.visible },
    )
    Text(
        parts.joinToString("  "),
        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
        color = when {
            highlighted -> MaterialTheme.colorScheme.onPrimaryContainer
            !node.visible -> MaterialTheme.colorScheme.outline
            else -> MaterialTheme.colorScheme.onSurface
        },
        modifier = Modifier
            .fillMaxWidth()
            .background(if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background)
            .combinedClickable(onClick = {}, onLongClick = onLongClick)
            .padding(start = (node.depth.coerceAtMost(20) * 6).dp, top = 2.dp, bottom = 2.dp),
    )
}

@Composable
private fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun Caption(text: String) {
    Column { Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}
