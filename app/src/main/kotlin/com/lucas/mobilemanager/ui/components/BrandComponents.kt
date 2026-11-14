package com.lucas.mobilemanager.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lucas.mobilemanager.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageTopBar(title: String) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        actions = {
            Image(painterResource(R.drawable.ic_launcher_foreground), null,
                Modifier.padding(end = 20.dp).size(42.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primary))
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
fun PageIntro(title: String, body: String) {
    Column(Modifier.padding(top = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun FeatureTile(title: String, body: String, icon: ImageVector, modifier: Modifier = Modifier, mint: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(onClick = onClick, modifier = modifier, colors = CardDefaults.cardColors(containerColor = if (mint) colors.secondaryContainer else colors.primaryContainer)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(16.dp), color = colors.surfaceContainerLow) {
                Icon(icon, null, Modifier.padding(12.dp).size(26.dp), tint = if (mint) colors.secondary else colors.primary)
            }
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodySmall)
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(20.dp))
        }
    }
}

@Composable
fun FocusHero(onClick: () -> Unit, enabled: Boolean) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFECE3FF)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.background(Brush.linearGradient(listOf(Color(0xFFF0E9FF), Color(0xFFDDD0FF)))).padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.hero_eyebrow), style = MaterialTheme.typography.labelLarge, color = Color(0xFF5935AA))
                    Text(stringResource(R.string.home_welcome), style = MaterialTheme.typography.headlineLarge, color = Color(0xFF30205D))
                }
                Image(painterResource(R.drawable.ic_focus_illustration), null, Modifier.size(112.dp))
            }
            Text(stringResource(R.string.home_subtitle), Modifier.padding(top = 10.dp, bottom = 16.dp), style = MaterialTheme.typography.bodyMedium, color = Color(0xFF514368))
            Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.session_card_start))
            }
            if (!enabled) Text(stringResource(R.string.mode_session_disabled), style = MaterialTheme.typography.bodySmall, color = Color(0xFF514368), modifier = Modifier.padding(top = 8.dp))
        }
    }
}
