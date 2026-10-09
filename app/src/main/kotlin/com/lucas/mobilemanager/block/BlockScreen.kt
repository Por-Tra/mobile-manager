package com.lucas.mobilemanager.block

import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.lucas.mobilemanager.R
import com.lucas.mobilemanager.domain.rules.BlockEnd
import com.lucas.mobilemanager.domain.rules.BlockReason
import com.lucas.mobilemanager.domain.rules.DayRelation
import com.lucas.mobilemanager.ui.format.Formats
import com.lucas.mobilemanager.ui.theme.LocalExtendedColors
import java.time.Instant
import java.time.ZoneId

@Composable
fun BlockScreen(
    args: BlockScreenArgs,
    now: Instant,
    zone: ZoneId,
    onGoHome: () -> Unit,
) {
    val context = LocalContext.current
    val app = remember(args.packageName) { loadAppInfo(context.packageManager, args.packageName) }
    val colors = LocalExtendedColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.limitReachedContainer)
            .safeDrawingPadding()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        app.icon?.let {
            Image(bitmap = it, contentDescription = null, modifier = Modifier.size(72.dp))
            Spacer(Modifier.height(16.dp))
        }
        Text(
            app.label,
            style = MaterialTheme.typography.headlineSmall,
            color = colors.onLimitReachedContainer,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            reasonText(args.reason),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onLimitReachedContainer,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            untilText(args.until, now, zone),
            style = MaterialTheme.typography.titleMedium,
            color = colors.onLimitReachedContainer,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(40.dp))
        Button(
            onClick = onGoHome,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.onLimitReachedContainer,
                contentColor = colors.limitReachedContainer,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.block_go_home))
        }
    }
}

@Composable
private fun reasonText(reason: BlockReason): String = when (reason) {
    BlockReason.StrictSession -> stringResource(R.string.block_reason_strict)
    BlockReason.GlobalRule -> stringResource(R.string.block_reason_global)
    is BlockReason.Profile -> stringResource(R.string.block_reason_profile, reason.name)
    is BlockReason.DailyLimitReached ->
        stringResource(R.string.block_reason_daily_limit, Formats.duration(reason.limit))
    is BlockReason.SessionLimitReached ->
        stringResource(R.string.block_reason_session_limit, Formats.duration(reason.limit))
}

@Composable
private fun untilText(until: Instant?, now: Instant, zone: ZoneId): String {
    if (until == null) return stringResource(R.string.block_until_unknown)
    val local = until.atZone(zone)
    return when (BlockEnd.relation(until, now, zone)) {
        DayRelation.TODAY -> stringResource(R.string.block_until_today, Formats.TIME.format(local))
        DayRelation.TOMORROW -> stringResource(R.string.block_until_tomorrow, Formats.TIME.format(local))
        DayRelation.LATER -> stringResource(R.string.block_until_later, Formats.DAY_AND_TIME.format(local))
    }
}

private data class AppInfo(val label: String, val icon: ImageBitmap?)

private fun loadAppInfo(packageManager: PackageManager, packageName: String): AppInfo =
    try {
        val info = packageManager.getApplicationInfo(packageName, 0)
        AppInfo(
            label = packageManager.getApplicationLabel(info).toString(),
            icon = packageManager.getApplicationIcon(info).toBitmap().asImageBitmap(),
        )
    } catch (_: PackageManager.NameNotFoundException) {
        AppInfo(label = packageName, icon = null)
    }
