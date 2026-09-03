package za.co.cyberpulse.communitygadget.control

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import za.co.cyberpulse.communitygadget.control.network.ControlRuntime
import za.co.cyberpulse.communitygadget.control.network.ControlService
import za.co.cyberpulse.communitygadget.control.network.DashboardAlert
import za.co.cyberpulse.communitygadget.control.network.TerminalPresence
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var preferences: ControlPreferences
    private val configState = mutableStateOf<ControlConfig?>(null)
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (configState.value != null) ControlService.start(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = ControlPreferences(this)
        configState.value = preferences.load()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF51D7FF),
                    secondary = Color(0xFF7CF5B5),
                    background = Color(0xFF07131A),
                    surface = Color(0xFF10242E),
                    error = Color(0xFFFF5F6D)
                )
            ) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val config = configState.value
                    if (config == null) {
                        SetupScreen { name, code ->
                            runCatching { preferences.save(name, code) }
                                .onSuccess {
                                    configState.value = it
                                    permissionLauncher.launch(requiredPermissions())
                                    ControlService.start(this)
                                }
                        }
                    } else {
                        Dashboard(
                            config = config,
                            requestPermissions = { permissionLauncher.launch(requiredPermissions()) },
                            sendTest = { ControlService.sendTest(this) },
                            reset = {
                                ControlService.stop(this)
                                preferences.clear()
                                ControlRuntime.clear()
                                configState.value = null
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (configState.value != null) ControlService.start(this)
    }

    private fun requiredPermissions(): Array<String> = buildList {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }.toTypedArray()
}

@Composable
private fun SetupScreen(onSetup: (String, String) -> Unit) {
    var name by remember { mutableStateOf("Community Control") }
    var code by remember { mutableStateOf("") }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item { Spacer(Modifier.height(30.dp)) }
        item {
            Text("COMMUNITY\nCONTROL CENTER", fontSize = 38.sp, lineHeight = 39.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            Text("Trusted dashboard for Community Gadget", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it.take(80) }, label = { Text("Control center name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.take(64) },
                        label = { Text("Private community code") },
                        supportingText = { Text("Use the exact same 8+ character code as the field devices") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions.Default,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(onClick = { onSetup(name, code) }, enabled = name.trim().length >= 3 && code.length >= 8, modifier = Modifier.fillMaxWidth()) {
                        Text("Activate Control Center")
                    }
                }
            }
        }
        item {
            Text(
                "The raw community code is not stored. This dashboard verifies signed heartbeats and alerts using the same derived community key as the field app.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Dashboard(
    config: ControlConfig,
    requestPermissions: () -> Unit,
    sendTest: () -> Unit,
    reset: () -> Unit
) {
    val status by ControlRuntime.status.collectAsState()
    val devices by ControlRuntime.devices.collectAsState()
    val alerts by ControlRuntime.alerts.collectAsState()
    val peers by ControlRuntime.connectedPeers.collectAsState()
    val nearby by ControlRuntime.nearbyReady.collectAsState()
    val lan by ControlRuntime.lanReady.collectAsState()
    val internet by ControlRuntime.internetAvailable.collectAsState()
    val cellular by ControlRuntime.cellularAvailable.collectAsState()
    val lastTransport by ControlRuntime.lastTransport.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(5_000L)
        }
    }

    val active = devices.count { now - it.lastSeenEpochMs <= 60_000L }
    val activeEmergency = alerts.firstOrNull { !it.ended && !it.isTest && it.level.name == "EMERGENCY" }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(Modifier.height(16.dp)) }
        item {
            Text("COMMUNITY CONTROL", fontSize = 28.sp, fontWeight = FontWeight.Black)
            Text(config.centerName, color = MaterialTheme.colorScheme.primary)
            Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
        if (activeEmergency != null) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.20f)), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("ACTIVE EMERGENCY", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Black, fontSize = 20.sp)
                        Text(activeEmergency.originName, fontWeight = FontWeight.Bold)
                        Text("${activeEmergency.receivedBy.size} received • ${activeEmergency.acknowledgedBy.size} acknowledged • ${activeEmergency.responders.size} responding")
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Known trusted", devices.size.toString(), Modifier.weight(1f))
                StatCard("Active now", active.toString(), Modifier.weight(1f))
                StatCard("Nearby peers", peers.toString(), Modifier.weight(1f))
            }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("NETWORK HEALTH", fontWeight = FontWeight.Black)
                    HealthRow("Nearby / Bluetooth", nearby, if (nearby) "Ready" else "Permission or radio limited")
                    HealthRow("Wi-Fi LAN", lan, if (lan) "Listening" else "Unavailable")
                    HealthRow("Internet", internet, if (internet) "Available" else "Offline")
                    HealthRow("SIM / mobile data", cellular, if (cellular) "Cellular network detected" else "Not currently detected")
                    HealthRow("Cloud relay", false, "Not configured — mobile data cannot relay between distant phones yet")
                    Text("Last trusted transport: $lastTransport", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
        }
        item {
            Button(onClick = sendTest, modifier = Modifier.fillMaxWidth()) { Text("Send Community Test Alert") }
        }
        item {
            OutlinedButton(onClick = requestPermissions, modifier = Modifier.fillMaxWidth()) { Text("Check / grant radio permissions") }
        }
        item { Text("TRUSTED DEVICES", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (devices.isEmpty()) {
            item { Text("No signed field-device heartbeat received yet. Install the updated field APK on another phone using the same community code.") }
        } else {
            items(devices, key = { it.terminalId }) { DeviceCard(it, now) }
        }
        item { Text("RECENT ALERTS", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (alerts.isEmpty()) item { Text("No trusted alerts recorded in this Control Center session.") }
        else items(alerts, key = { it.alertId }) { AlertCard(it) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF132832)), shape = RoundedCornerShape(18.dp)) {
                Text(
                    "Global registered-user and active-user totals require a real shared backend such as Firebase. Until that is configured, Known trusted and Active now count devices this Control Center has actually verified on Nearby/Bluetooth or the same LAN.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
        }
        item { OutlinedButton(onClick = reset, modifier = Modifier.fillMaxWidth()) { Text("Change community / reset Control Center") } }
        item { Spacer(Modifier.height(28.dp)) }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp)) {
            Text(value, fontSize = 27.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HealthRow(name: String, ok: Boolean, detail: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(name, fontWeight = FontWeight.Bold)
        Text(if (ok) "● $detail" else "○ $detail", color = if (ok) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
}

@Composable
private fun DeviceCard(device: TerminalPresence, now: Long) {
    val age = (now - device.lastSeenEpochMs).coerceAtLeast(0L)
    val active = age <= 60_000L
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(device.terminalName, fontWeight = FontWeight.Bold)
                Text(if (active) "ACTIVE" else "OFFLINE", color = if (active) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Text("Last seen ${ageText(age)} • ${device.transport}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Text(device.terminalId.take(12), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
    }
}

@Composable
private fun AlertCard(alert: DashboardAlert) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val formatter = remember { DateTimeFormatter.ofPattern("dd MMM HH:mm").withZone(ZoneId.systemDefault()) }
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (alert.isTest) "TEST ${alert.level}" else alert.level.name, fontWeight = FontWeight.Black, color = if (alert.level.name == "EMERGENCY" && !alert.isTest) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                Text(formatter.format(Instant.ofEpochMilli(alert.createdAtEpochMs)), fontSize = 12.sp)
            }
            Text(alert.originName, fontWeight = FontWeight.Bold)
            Text("${alert.receivedBy.size} received • ${alert.acknowledgedBy.size} acknowledged • ${alert.responders.size} responding", fontSize = 13.sp)
            if (alert.ended) Text("Ended", color = MaterialTheme.colorScheme.secondary)
            if (alert.latitude != null && alert.longitude != null) {
                Text(String.format(Locale.US, "%.6f, %.6f", alert.latitude, alert.longitude), fontSize = 12.sp)
                OutlinedButton(onClick = {
                    val uri = Uri.parse("geo:${alert.latitude},${alert.longitude}?q=${alert.latitude},${alert.longitude}")
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                }) { Text("Open emergency location") }
            }
        }
    }
}

private fun ageText(ms: Long): String = when {
    ms < 10_000L -> "just now"
    ms < 60_000L -> "${ms / 1000}s ago"
    ms < 3_600_000L -> "${ms / 60_000}m ago"
    else -> "${ms / 3_600_000}h ago"
}
