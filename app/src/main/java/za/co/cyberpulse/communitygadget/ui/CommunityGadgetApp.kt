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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SecureGreen = Color(0xFF20C97A)
private val MonitorAmber = Color(0xFFFFB629)
private val EmergencyRed = Color(0xFFFF4357)

@Composable
fun CommunityGadgetApp(
    viewModel: AppViewModel,
    permissionsReady: Boolean,
    requestPermissions: () -> Unit
) {
    val config by viewModel.config.collectAsState()

    LaunchedEffect(config, permissionsReady) {
        if (config != null && permissionsReady) viewModel.startMesh()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (config == null) {
            SetupScreen(
                onSetup = { name, code -> viewModel.completeSetup(name, code) },
                requestPermissions = requestPermissions
            )
        } else {
            Dashboard(
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
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Spacer(Modifier.height(20.dp))
            Text(
                text = "COMMUNITY\nGADGET",
                fontSize = 42.sp,
                lineHeight = 42.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Offline emergency relay",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 17.sp
            )
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
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
                        supportingText = { Text("Use the same 8+ character code on trusted devices") },
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
                    ) {
                        Text("Activate terminal", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item {
            PrivacyCard()
        }
    }
}

@Composable
private fun PrivacyCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF102734)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Location privacy", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
            Text(
                "GPS is requested so a red emergency can include the caller's position. " +
                    "Green and amber alerts never contain location, and the app does not continuously track anyone.",
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
    var showResetDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("COMMUNITY GADGET", fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Text(config.terminalName, color = MaterialTheme.colorScheme.primary)
                }
                TextButton(onClick = { showResetDialog = true }) { Text("Setup") }
            }
        }

        item {
            NetworkCard(status = status, peers = peers)
        }

        if (!permissionsReady) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF3B2B13)),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Permissions required", fontWeight = FontWeight.Bold, color = MonitorAmber)
                        Text("Nearby radios, notifications, and GPS must be allowed for reliable alerts.")
                        Button(onClick = requestPermissions) { Text("Grant permissions") }
                    }
                }
            }
        }

        item {
            Text("ZONE STATUS", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
        }
        item {
            AlertButton(
                title = "GREEN — SECURE",
                subtitle = "All clear. Zone is normal.",
                color = SecureGreen,
                enabled = !isSending,
                onClick = { viewModel.sendAlert(AlertLevel.SECURE) }
            )
        }
        item {
            AlertButton(
                title = "AMBER — MONITOR",
                subtitle = "Suspicious activity. Stay alert.",
                color = MonitorAmber,
                enabled = !isSending,
                onClick = { viewModel.sendAlert(AlertLevel.MONITOR) }
            )
        }
        item {
            AlertButton(
                title = if (isSending) "GETTING GPS…" else "RED — EMERGENCY",
                subtitle = "Immediate alert with emergency-only location.",
                color = EmergencyRed,
                enabled = !isSending,
                onClick = { viewModel.sendAlert(AlertLevel.EMERGENCY) }
            )
        }

        item {
            OutlinedButton(
                onClick = viewModel::acknowledgeAlert,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(14.dp)
            ) {
                Text("Acknowledge and silence local alarm")
            }
        }

        item {
            Text(
                "Phone relay range grows through nearby devices. Reliable multi-kilometre offline coverage requires dedicated radio relay stations.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }

        if (alerts.isNotEmpty()) {
            item {
                Text("RECENT TRUSTED ALERTS", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            }
            items(alerts, key = { it.id }) { alert ->
                AlertCard(alert)
            }
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
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun NetworkCard(status: String, peers: Int) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .size(12.dp)
                        .background(if (status.contains("listening", true)) SecureGreen else MonitorAmber, CircleShape)
                )
                Column {
                    Text(status, fontWeight = FontWeight.Bold)
                    Text("No airtime or server required", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(peers.toString(), fontSize = 26.sp, fontWeight = FontWeight.Black)
                Text("nearby", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AlertButton(
    title: String,
    subtitle: String,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp),
        enabled = enabled,
        shape = RoundedCornerShape(22.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = Color(0xFF061013),
            disabledContainerColor = color.copy(alpha = 0.45f)
        ),
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
    val formatter = remember {
        DateTimeFormatter.ofPattern("dd MMM, HH:mm").withZone(ZoneId.systemDefault())
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.13f)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(alert.level.name, color = color, fontWeight = FontWeight.Black)
                Text(formatter.format(Instant.ofEpochMilli(alert.createdAtEpochMs)), fontSize = 13.sp)
            }
            Text(alert.originName, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            if (alert.level == AlertLevel.EMERGENCY && alert.hasLocation()) {
                Text(
                    String.format(Locale.US, "%.6f, %.6f", alert.latitude, alert.longitude),
                    fontWeight = FontWeight.Medium
                )
                Text("GPS accuracy ±${(alert.accuracyMeters ?: 0f).toInt()} metres")
                OutlinedButton(onClick = {
                    val uri = Uri.parse("geo:${alert.latitude},${alert.longitude}?q=${alert.latitude},${alert.longitude}")
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                }) {
                    Text("Open emergency location")
                }
            } else if (alert.level == AlertLevel.EMERGENCY) {
                Text("Emergency location unavailable", color = color)
            } else {
                Text("Location not collected or shared", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
