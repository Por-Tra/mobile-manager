package com.lucas.mobilemanager.ui.apps

import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.lucas.mobilemanager.R
import com.lucas.mobilemanager.apps.InstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Champ de recherche du sélecteur d'apps. */
@Composable
fun AppSearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        placeholder = { Text(stringResource(R.string.app_picker_search)) },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * Lignes du sélecteur d'apps, à insérer dans une LazyColumn. [apps] est affiché dans l'ordre
 * reçu (l'appelant place les apps présélectionnées en tête) ; la recherche filtre sur le nom.
 * L'ordre ne change pas quand on coche, pour que la liste ne saute pas sous le doigt.
 */
fun LazyListScope.appPickerItems(
    apps: List<InstalledApp>,
    selected: Set<String>,
    query: String,
    onToggle: (String) -> Unit,
) {
    val filtered = apps.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
    items(filtered, key = { it.packageName }) { app ->
        AppRow(app, checked = app.packageName in selected, onToggle = { onToggle(app.packageName) })
    }
}

@Composable
private fun AppRow(app: InstalledApp, checked: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 6.dp),
    ) {
        AppIcon(app.packageName, Modifier.size(36.dp))
        Spacer(Modifier.width(12.dp))
        Text(app.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
    }
}

/** Icône d'une app, chargée hors du thread principal. */
@Composable
fun AppIcon(packageName: String, modifier: Modifier = Modifier) {
    val packageManager = LocalContext.current.packageManager
    var icon by remember(packageName) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(packageName) {
        icon = withContext(Dispatchers.IO) {
            try {
                packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap()
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
        }
    }
    Box(modifier) {
        icon?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.size(36.dp)) }
    }
}
