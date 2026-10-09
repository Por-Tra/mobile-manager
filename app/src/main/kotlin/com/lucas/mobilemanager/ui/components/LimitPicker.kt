package com.lucas.mobilemanager.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.lucas.mobilemanager.R
import com.lucas.mobilemanager.ui.format.Formats
import java.time.Duration

/** Durées proposées pour une limite quotidienne ou un quota (5 min → 3 h). */
val LIMIT_OPTIONS = listOf(5, 10, 15, 20, 30, 45, 60, 90, 120, 180)

/** Bouton « Limite : 30 min » qui ouvre la liste des durées. */
@Composable
fun LimitPicker(minutes: Int, enabled: Boolean, onLimit: (Int) -> Unit, options: List<Int> = LIMIT_OPTIONS) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }, enabled = enabled) {
            Text(stringResource(R.string.profile_edit_limit_value, Formats.duration(Duration.ofMinutes(minutes.toLong()))))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(Formats.duration(Duration.ofMinutes(option.toLong()))) },
                    onClick = {
                        onLimit(option)
                        open = false
                    },
                )
            }
        }
    }
}
