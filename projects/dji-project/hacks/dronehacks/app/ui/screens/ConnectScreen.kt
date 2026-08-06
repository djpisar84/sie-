package com.dronehacks.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dronehacks.app.model.ConnectionState
import com.dronehacks.app.ui.theme.DroneBlue
import com.dronehacks.app.ui.theme.DroneOrange
import com.dronehacks.app.ui.theme.SurfaceVariantDark
import com.dronehacks.app.viewmodel.DroneViewModel

@Composable
fun ConnectScreen(viewModel: DroneViewModel, onConnected: () -> Unit) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.connectionState) {
        if (state.connectionState == ConnectionState.CONNECTED) {
            onConnected()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))

        Icon(
            imageVector = Icons.Default.FlightTakeoff,
            contentDescription = null,
            tint = DroneOrange,
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Connect Your Drone",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            "Select connection method",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(32.dp))

        if (state.connectionState == ConnectionState.SCANNING ||
            state.connectionState == ConnectionState.CONNECTING
        ) {
            ConnectingIndicator(state.connectionState)
        } else {
            ConnectionMethodCard(
                icon = Icons.Default.Usb,
                title = "USB / OTG",
                description = "Connect via USB-C OTG cable. Fast and reliable. Supports all DJI Mini, Air and Mavic series.",
                color = DroneOrange,
                onClick = { viewModel.connectUsb() }
            )

            Spacer(Modifier.height(12.dp))

            ConnectionMethodCard(
                icon = Icons.Default.Wifi,
                title = "WiFi",
                description = "Connect over WiFi. Make sure you are connected to the drone's hotspot before selecting this option.",
                color = DroneBlue,
                onClick = { viewModel.connectWifi() }
            )

            Spacer(Modifier.height(12.dp))

            ConnectionMethodCard(
                icon = Icons.Default.PlayCircle,
                title = "Demo Mode",
                description = "Explore all features without a physical drone. Perfect for learning the interface.",
                color = Color(0xFF7B61FF),
                onClick = { viewModel.connectDemo() }
            )
        }

        if (state.connectionState == ConnectionState.ERROR) {
            Spacer(Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2B1010)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Error, null, tint = Color(0xFFF44336), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Connection failed. Please check your cable/WiFi and try again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFFCDD2)
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionMethodCard(
    icon: ImageVector,
    title: String,
    description: String,
    color: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceVariantDark)
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(2.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Default.ChevronRight, null, tint = color, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun ConnectingIndicator(state: ConnectionState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = DroneOrange, modifier = Modifier.size(56.dp), strokeWidth = 4.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            text = if (state == ConnectionState.SCANNING) "Scanning for drone…" else "Connecting…",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "Please wait",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
