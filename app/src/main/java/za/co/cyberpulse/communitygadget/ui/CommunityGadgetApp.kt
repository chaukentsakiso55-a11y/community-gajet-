package za.co.cyberpulse.communitygadget.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import za.co.cyberpulse.communitygadget.data.TerminalConfig
import za.co.cyberpulse.communitygadget.domain.AlertLevel
import za.co.cyberpulse.communitygadget.domain.EmergencyAlert
import za.co.cyberpulse.communitygadget.network.ActiveAlertState
import za.co.cyberpulse.communitygadget.network.AlertProgress
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SecureGreen = Color(0xFF20C97A)
private val MonitorAmber = Color(0xFFFFB629)
private val EmergencyRed = Color(0xFFFF4357)
private val TestBlue = Color(0xFF41C7FF)

@Composable
fun CommunityGadgetApp(
    viewModel: AppViewModel,
    permissionsReady: Boolean,
    requestPermissions: () -> Unit
) {
    val config by viewModel.config.collectAsState()
    val active by viewModel.activeAlert.collectAsState()
    val silenced by viewModel.silencedAlertIds.collectAsState()
    val progress by viewModel.progress.collectAsState()

    LaunchedEffect(config) {
        if (config != null) viewModel.startMesh()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            config == null -> SetupScreen(
                onSetup = { name, code -> viewModel.completeSetup(name, code) },
                requestPermissions = requestPermissions
            )
            active != null && !requireNotNull(active).localOrigin && requireNotNull(active).alert.id !in silenced ->
                EmergencyScreen(requireNotNull(active), progress, viewModel)
            else -> Dashboard(
                config = requireNotNull(config),
                viewModel = viewModel,
                permissionsReady = permissionsReady,
                requestPermissions = requestPermissions
            )
        }
    }
}

@Composable
private fun SetupScreen(
    onSetup: (String, String) -> Result<Unit>,
    requestPermissions: () -> Unit
) {
    var terminalName by remember { mutableStateOf("") }
    var communityCode by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Spacer(Modifier.height(20.dp))
            Text("COMMUNITY\nGADGET V2", fontSize = 40.sp, lineHeight = 40.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            Text("Trusted community emergency network", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 17.sp)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Configure this terminal", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = terminalName,
                        onValueChange = { terminalName = it.take(80) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Home, street, or terminal name") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = communityCode,
                        onValueChange = { communityCode = it.take(64) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Private community code") },
                        supportingText = { Text("Use the exact same 8+ character code on trusted devices") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(
                        onClick = {
                            onSetup(terminalName, communityCode)
                                .onSuccess { requestPermissions() }
                                .onFailure { error = it.message }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = terminalName.isNotBlank() && communityCode.length >= 8,
                        contentPadding = PaddingValues(16.dp)
                    ) { Text("Activate terminal", fontWeight = FontWeight.Bold) }
                }
            }
        }
        item { PrivacyCard() }
    }
}

@Composable
private fun PrivacyCard() {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF102734)), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Emergency-only location", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
            Text(
                "Location is attached only to a real red emergency. While that emergency remains active, V2 refreshes the position so responders can follow changes. Ending the emergency stops those updates.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Dashboard(
    config: TerminalConfig,
    viewModel: AppViewModel,
    permissionsReady: Boolean,
    requestPermissions: () -> Unit
) {
    val status by viewModel.meshStatus.collectAsState()
    val peers by viewModel.connectedPeers.collectAsState()
    val alerts by viewModel.alerts.collectAsState()
    val isSending by viewModel.isSending.collectAsState()
    val active by viewModel.activeAlert.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val nearbyReady by viewModel.nearbyReady.collectAsState()
    val lanReady by viewModel.lanReady.collectAsState()
    val internet by viewModel.internetAvailable.collectAsState()
    val lastTransport by viewModel.lastTransport.collectAsState()
    var showResetDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("COMMUNITY GADGET V2", fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Text(config.terminalName, color = MaterialTheme.colorScheme.primary)
                }
                TextButton(onClick = { showResetDialog = true }) { Text("Setup") }
            }
        }

        item {
            NetworkHealthCard(status, peers, nearbyReady, lanReady, internet, lastTransport)
        }

        if (!permissionsReady) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF3B2B13)), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Some permissions are off", fontWeight = FontWeight.Bold, color = MonitorAmber)
                        Text("Wi-Fi LAN can still work. Grant Nearby, notifications and location for the complete emergency experience.")
                        Button(onClick = requestPermissions) { Text("Review permissions") }
                    }
                }
            }
        }

        if (active?.localOrigin == true) {
            item { ActiveOriginCard(requireNotNull(active), progress, viewModel) }
        }

        item { Text("ZONE STATUS", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold) }
        item { AlertButton("GREEN — SECURE", "All clear. Zone is normal.", SecureGreen, !isSending) { viewModel.sendAlert(AlertLevel.SECURE) } }
        item { AlertButton("AMBER — MONITOR", "Suspicious activity. Stay alert.", MonitorAmber, !isSending) { viewModel.sendAlert(AlertLevel.MONITOR) } }
        item {
            AlertButton(
                if (isSending) "GETTING LOCATION…" else "RED — EMERGENCY",
                "Immediate alarm + emergency-only live location.",
                EmergencyRed,
                !isSending
            ) { viewModel.sendAlert(AlertLevel.EMERGENCY) }
        }
        item {
            OutlinedButton(onClick = viewModel::sendTestAlert, modifier = Modifier.fillMaxWidth(), enabled = !isSending, contentPadding = PaddingValues(14.dp)) {
                Text("TEST COMMUNITY ALERT — NO EMERGENCY", fontWeight = FontWeight.Bold, color = TestBlue)
            }
        }

        item {
            Text(
                "V2 sends through Nearby and same-Wi-Fi LAN together. Internet availability is shown below, but global cloud relay is not enabled until a Firebase/backend project is connected.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }

        if (alerts.isNotEmpty()) {
            item { Text("RECENT TRUSTED ALERTS", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold) }
            items(alerts, key = { it.id }) { alert -> AlertCard(alert) }
        }
        item { Spacer(Modifier.height(28.dp)) }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset this terminal?") },
            text = { Text("This removes the local community code. It does not affect other terminals.") },
            confirmButton = {
                TextButton(onClick = {
                    showResetDialog = false
                    viewModel.resetTerminal()
                }) { Text("Reset", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun NetworkHealthCard(
    status: String,
    peers: Int,
    nearbyReady: Boolean,
    lanReady: Boolean,
    internet: Boolean,
    lastTransport: String
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("NETWORK HEALTH", fontWeight = FontWeight.Black)
                    Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(peers.toString(), fontSize = 26.sp, fontWeight = FontWeight.Black)
                    Text("nearby peers", fontSize = 12.sp)
                }
            }
            HealthLine("Nearby mesh", nearbyReady, if (nearbyReady) "READY" else "UNAVAILABLE")
            HealthLine("Wi-Fi LAN", lanReady, if (lanReady) "LISTENING" else "NOT ACTIVE")
            HealthLine("Internet", internet, if (internet) "AVAILABLE" else "OFFLINE")
            HealthLine("Cloud relay", false, "NOT CONFIGURED")
            Text("Last received path: $lastTransport", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HealthLine(name: String, ready: Boolean, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(9.dp).background(if (ready) SecureGreen else MonitorAmber, CircleShape))
            Text(name)
        }
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (ready) SecureGreen else MonitorAmber)
    }
}

@Composable
private fun ActiveOriginCard(active: ActiveAlertState, progress: AlertProgress, viewModel: AppViewModel) {
    val color = if (active.isTest) TestBlue else EmergencyRed
    Card(colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.14f)), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (active.isTest) "TEST ALERT ACTIVE" else "YOUR EMERGENCY IS ACTIVE", color = color, fontWeight = FontWeight.Black, fontSize = 19.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("Received", progress.receivedBy.size)
                Metric("Acknowledged", progress.acknowledgedBy.size)
                Metric("Responding", progress.responders.size)
            }
            if (!active.isTest) {
                Text(
                    if (active.alert.hasLocation()) "Live emergency location is updating while this alert remains active." else "Waiting for an emergency GPS fix…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
            Button(
                onClick = viewModel::endEmergency,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color(0xFF071013))
            ) { Text(if (active.isTest) "END TEST ALERT" else "END EMERGENCY", fontWeight = FontWeight.Black) }
        }
    }
}

@Composable
private fun Metric(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), fontSize = 26.sp, fontWeight = FontWeight.Black)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmergencyScreen(active: ActiveAlertState, progress: AlertProgress, viewModel: AppViewModel) {
    val context = LocalContext.current
    val alert = active.alert
    val color = if (active.isTest) TestBlue else EmergencyRed
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF090D11)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Spacer(Modifier.height(20.dp))
                Text(
                    if (active.isTest) "TEST ALERT" else "EMERGENCY",
                    color = color,
                    fontSize = 44.sp,
                    lineHeight = 46.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
                Text(
                    if (active.isTest) "NO EMERGENCY — CONNECTION TEST" else "A trusted community member needs attention",
                    color = if (active.isTest) TestBlue else Color.White,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.14f)), shape = RoundedCornerShape(28.dp)) {
                    Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(alert.originName, fontSize = 28.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                        if (!active.isTest && alert.hasLocation()) {
                            Text(String.format(Locale.US, "%.6f, %.6f", alert.latitude, alert.longitude), fontWeight = FontWeight.Bold)
                            Text("Accuracy ±${(alert.accuracyMeters ?: 0f).toInt()} m", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedButton(onClick = {
                                val uri = Uri.parse("geo:${alert.latitude},${alert.longitude}?q=${alert.latitude},${alert.longitude}")
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                            }, modifier = Modifier.fillMaxWidth()) {
                                Text("OPEN EMERGENCY MAP")
                            }
                        } else if (!active.isTest) {
                            Text("Location is not available yet. Live updates will appear when the sender gets a GPS fix.", textAlign = TextAlign.Center)
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Metric("Received", progress.receivedBy.size)
                    Metric("Acknowledged", progress.acknowledgedBy.size)
                    Metric("Responding", progress.responders.size)
                }
            }
            item {
                if (active.isTest) {
                    Button(onClick = viewModel::acknowledgeAlert, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = TestBlue, contentColor = Color(0xFF071013))) {
                        Text("CLOSE TEST ALERT", fontWeight = FontWeight.Black)
                    }
                } else {
                    Button(onClick = viewModel::respondToAlert, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = SecureGreen, contentColor = Color(0xFF071013)), contentPadding = PaddingValues(18.dp)) {
                        Text("I'M RESPONDING", fontWeight = FontWeight.Black, fontSize = 18.sp)
                    }
                    OutlinedButton(onClick = viewModel::acknowledgeAlert, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                        Text("ACKNOWLEDGE & SILENCE")
                    }
                }
            }
            item {
                Text(
                    if (active.isTest) "This alert uses the same trusted network path as a real emergency but does not share location." else "Location is shared only for this active emergency. Acknowledging silences your phone; it does not end the sender's emergency.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun AlertButton(title: String, subtitle: String, color: Color, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(92.dp),
        enabled = enabled,
        shape = RoundedCornerShape(22.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color(0xFF061013), disabledContainerColor = color.copy(alpha = 0.45f)),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text(subtitle, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun AlertCard(alert: EmergencyAlert) {
    val context = LocalContext.current
    val color = when (alert.level) {
        AlertLevel.SECURE -> SecureGreen
        AlertLevel.MONITOR -> MonitorAmber
        AlertLevel.EMERGENCY -> EmergencyRed
    }
    val formatter = remember { DateTimeFormatter.ofPattern("dd MMM, HH:mm").withZone(ZoneId.systemDefault()) }
    Card(colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.13f)), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(alert.level.name, color = color, fontWeight = FontWeight.Black)
                Text(formatter.format(Instant.ofEpochMilli(alert.createdAtEpochMs)), fontSize = 13.sp)
            }
            Text(alert.originName, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            if (alert.level == AlertLevel.EMERGENCY && alert.hasLocation()) {
                Text(String.format(Locale.US, "%.6f, %.6f", alert.latitude, alert.longitude), fontWeight = FontWeight.Medium)
                OutlinedButton(onClick = {
                    val uri = Uri.parse("geo:${alert.latitude},${alert.longitude}?q=${alert.latitude},${alert.longitude}")
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                }) { Text("Open emergency map") }
            } else if (alert.level != AlertLevel.EMERGENCY) {
                Text("Location not collected or shared", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
