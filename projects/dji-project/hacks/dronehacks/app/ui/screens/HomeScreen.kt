package com.dronehacks.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronehacks.app.model.ConnectionState
import com.dronehacks.app.model.DroneInfo
import com.dronehacks.app.ui.theme.DroneBlue
import com.dronehacks.app.ui.theme.DroneOrange
import com.dronehacks.app.ui.theme.SurfaceVariantDark
import com.dronehacks.app.viewmodel.DroneViewModel

@Composable
fun HomeScreen(
    viewModel: DroneViewModel,
    onNavigateToConnect: () -> Unit,
    onNavigateToMods: () -> Unit,
    onNavigateToNfz: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.FlightTakeoff,
                contentDescription = null,
                tint = DroneOrange,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = "DroneHacks",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
                Text(
                    text = "Advanced Drone Modification Tool",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // Connection status card
        ConnectionStatusCard(
            connectionState = state.connectionState,
            droneInfo = state.droneInfo,
            isDemoMode = state.isDemoMode,
            onConnectClick = onNavigateToConnect,
            onDisconnectClick = { viewModel.disconnect() }
        )

        Spacer(Modifier.height(16.dp))

        // Quick-action grid
        if (state.connectionState == ConnectionState.CONNECTED) {
            Text(
                text = "QUICK ACTIONS",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 2.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionCard(
                    icon = Icons.Default.Tune,
                    label = "Modify",
                    description = "Flight params",
                    color = DroneOrange,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToMods
                )
                QuickActionCard(
                    icon = Icons.Default.LocationOff,
                    label = "NFZ",
                    description = "Zone manager",
                    color = DroneBlue,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToNfz
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        // Drone info
        state.droneInfo?.let { info ->
            Text(
                text = "DRONE INFO",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 2.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Spacer(Modifier.height(8.dp))
            DroneInfoCard(info)
            Spacer(Modifier.height(16.dp))
        }

        // Disclaimer
        DisclaimerCard()
    }
}

@Composable
private fun ConnectionStatusCard(
    connectionState: ConnectionState,
    droneInfo: DroneInfo?,
    isDemoMode: Boolean,
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit
) {
    val (bgBrush, dotColor, statusText) = when (connectionState) {
        ConnectionState.CONNECTED -> Triple(
            Brush.horizontalGradient(listOf(Color(0xFF1B2F1B), Color(0xFF1E2B1A))),
            Color(0xFF4CAF50),
            if (isDemoMode) "Connected (Demo Mode)" else "Connected"
        )
        ConnectionState.SCANNING, ConnectionState.CONNECTING -> Triple(
            Brush.horizontalGradient(listOf(Color(0xFF2B2510), Color(0xFF231E0E))),
            Color(0xFFFFC107),
            if (connectionState == ConnectionState.SCANNING) "Scanning…" else "Connecting…"
        )
        ConnectionState.ERROR -> Triple(
            Brush.horizontalGradient(listOf(Color(0xFF2B1010), Color(0xFF230E0E))),
            Color(0xFFF44336),
            "Connection Error"
        )
        else -> Triple(
            Brush.horizontalGradient(listOf(SurfaceVariantDark, SurfaceVariantDark)),
            Color(0xFF607D8B),
            "Not Connected"
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bgBrush)
            .padding(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            droneInfo?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = it.model,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    InfoChip(Icons.Default.Battery5Bar, "${it.batteryLevel}%")
                    InfoChip(Icons.Default.Wifi, "${it.signalStrength}%")
                    InfoChip(Icons.Default.Memory, "FW ${it.firmwareVersion}")
                }
            }

            Spacer(Modifier.height(12.dp))

            if (connectionState == ConnectionState.CONNECTED) {
                OutlinedButton(
                    onClick = onDisconnectClick,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF44336)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.LinkOff, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Disconnect")
                }
            } else if (connectionState == ConnectionState.DISCONNECTED || connectionState == ConnectionState.ERROR) {
                Button(
                    onClick = onConnectClick,
                    colors = ButtonDefaults.buttonColors(containerColor = DroneOrange),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Link, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Connect Drone", fontWeight = FontWeight.SemiBold)
                }
            } else {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = DroneOrange
                )
            }
        }
    }
}

@Composable
private fun InfoChip(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun QuickActionCard(
    icon: ImageVector,
    label: String,
    description: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(label, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DroneInfoCard(info: DroneInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoRow("Model", info.model)
            InfoRow("Firmware", info.firmwareVersion)
            InfoRow("Serial Number", info.serialNumber)
            InfoRow("Flight Controller", info.flightController)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
    }
}

@Composable
private fun DisclaimerCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1200))
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.Warning, null, tint = Color(0xFFFFC107), modifier = Modifier.size(18.dp).padding(top = 2.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = "For authorized use only. Always comply with local aviation regulations and obtain necessary permits before modifying or flying your drone.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFFFF8E1)
            )
        }
    }
}
