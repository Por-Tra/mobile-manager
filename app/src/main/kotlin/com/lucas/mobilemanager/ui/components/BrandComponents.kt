package com.lucas.mobilemanager.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import com.lucas.mobilemanager.ui.theme.GraphicColors as G
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
fun PageIntro(title: String, body: String, scene: SceneKind = SceneKind.PROFILES) {
    val mint = scene == SceneKind.SCROLL || scene == SceneKind.BALANCE
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(if (mint) colors.secondaryContainer else colors.primaryContainer, colors.surfaceContainerLow)))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            GraphicScene(scene, Modifier.size(92.dp))
        }
        Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
    }
}

@Composable
fun FeatureTile(title: String, body: String, icon: ImageVector, modifier: Modifier = Modifier, mint: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .96f else 1f, label = "feature press")
    Card(
        onClick = onClick,
        interactionSource = interaction,
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        colors = CardDefaults.cardColors(containerColor = if (mint) colors.secondaryContainer else colors.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp, pressedElevation = 0.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GraphicScene(if (mint) SceneKind.SCROLL else SceneKind.PROFILES, Modifier.fillMaxWidth().height(112.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(18.dp), tint = if (mint) colors.secondary else colors.primary)
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(start = 6.dp))
            }
            Text(body, style = MaterialTheme.typography.bodySmall)
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(20.dp))
        }
    }
}

@Composable
fun FocusHero(onClick: () -> Unit, enabled: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = G.deep, contentColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(Modifier.background(Brush.linearGradient(listOf(G.iris, G.deep)))) {
            androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
                drawCircle(Color.White.copy(alpha = .07f), size.width * .6f, androidx.compose.ui.geometry.Offset(size.width, 0f))
                drawCircle(Color.White.copy(alpha = .05f), size.width * .7f, androidx.compose.ui.geometry.Offset(size.width * .9f, size.height))
            }
            Column(Modifier.padding(22.dp)) {
                Text(stringResource(R.string.hero_eyebrow), style = MaterialTheme.typography.labelMedium, color = G.mintLight)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                    Text(stringResource(R.string.home_welcome), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                    GraphicScene(SceneKind.FOCUS, Modifier.size(126.dp))
                }
                Text(stringResource(R.string.home_subtitle), Modifier.padding(top = 8.dp, bottom = 16.dp), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = .9f))
                Button(
                    onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = G.deep, disabledContainerColor = Color.White.copy(alpha = .18f), disabledContentColor = Color.White.copy(alpha = .75f)),
                ) { Text(stringResource(R.string.session_card_start)) }
                if (!enabled) Text(stringResource(R.string.mode_session_disabled), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .85f), modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}
