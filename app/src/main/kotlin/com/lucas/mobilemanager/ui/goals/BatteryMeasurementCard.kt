package com.lucas.mobilemanager.ui.goals

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lucas.mobilemanager.R
import com.lucas.mobilemanager.domain.goals.ResponsibleMetrics
import java.text.NumberFormat

/** Mesure volontaire, uniquement tant que cette vue reste visible et le téléphone débranché. */
@Composable
fun BatteryMeasurementCard() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val battery = remember(context) { context.getSystemService(BatteryManager::class.java) }
    var start by remember { mutableStateOf<Long?>(null) }
    var startMs by remember { mutableLongStateOf(0L) }
    var seconds by remember { mutableLongStateOf(0L) }
    var energy by remember { mutableStateOf<Double?>(null) }
    var invalid by remember { mutableStateOf(false) }
    var intensity by remember { mutableStateOf("") }
    var efficiency by remember { mutableStateOf("85") }
    val locale = LocalConfiguration.current.locales[0]
    val number = remember(locale) { NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 4 } }
    fun unplugged(): Boolean = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        ?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) == 0
    fun counter(): Long = battery.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER)
    DisposableEffect(context, lifecycle) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) != 0 && start != null) {
                    start = null; invalid = true; energy = null
                }
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && start != null) { start = null; energy = null; invalid = true }
        }
        lifecycle.addObserver(observer)
        onDispose { context.unregisterReceiver(receiver); lifecycle.removeObserver(observer) }
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.energy_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.energy_scope))
            TextButton(onClick = {
                if (start == null) {
                    energy = null
                    val value = counter()
                    invalid = !unplugged() || value <= 0
                    if (!invalid) { start = value; startMs = SystemClock.elapsedRealtime() }
                } else {
                    val before = requireNotNull(start)
                    seconds = (SystemClock.elapsedRealtime() - startMs) / 1000
                    energy = if (unplugged()) ResponsibleMetrics.dischargedWh(before, counter()) else null
                    invalid = energy == null || seconds < 1
                    if (invalid) energy = null
                    start = null
                }
            }) { Text(stringResource(if (start == null) R.string.energy_start else R.string.energy_stop)) }
            if (start != null) Text(stringResource(R.string.energy_measuring))
            if (invalid) Text(stringResource(R.string.energy_unavailable))
            energy?.let { wh ->
                Text(stringResource(R.string.energy_result, number.format(wh), seconds))
                Text(stringResource(R.string.energy_carbon_help))
                OutlinedTextField(intensity, { intensity = it.take(12) }, label = { Text(stringResource(R.string.energy_intensity)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                OutlinedTextField(efficiency, { efficiency = it.take(6) }, label = { Text(stringResource(R.string.energy_efficiency)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                val carbon = intensity.replace(',', '.').toDoubleOrNull()
                val eta = efficiency.replace(',', '.').toDoubleOrNull()?.div(100)
                if (carbon != null && carbon.isFinite() && carbon >= 0 && eta != null && eta.isFinite() && eta > 0 && eta <= 1) {
                    Text(stringResource(R.string.energy_carbon_result, number.format(wh / eta), number.format(ResponsibleMetrics.rechargeGrams(wh, eta, carbon))))
                }
            }
        }
    }
}
