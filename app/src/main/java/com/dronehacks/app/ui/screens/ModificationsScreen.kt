package com.dronehacks.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dronehacks.app.model.FlightParams
import com.dronehacks.app.model.VideoResolution
import com.dronehacks.app.ui.components.*
import com.dronehacks.app.ui.theme.DroneOrange
import com.dronehacks.app.viewmodel.DroneViewModel

@Composable
fun ModificationsScreen(viewModel: DroneViewModel) {
    val state by viewModel.uiState.collectAsState()
    val params = state.flightParams
    var showResetDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            "Flight Modifications",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            "Customize flight parameters",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))

        // Result banner
        AnimatedVisibility(visible = state.lastResult != null) {
            state.lastResult?.let { result ->
                ResultBanner(result = result, onDismiss = { viewModel.clearLastResult() })
                Spacer(Modifier.height(12.dp))
            }
        }

        // ---- Altitude & Distance ----
        SectionCard(title = "Altitude & Distance", icon = Icons.Default.Height) {
            SliderSetting(
                label = "Max Altitude",
                value = params.maxAltitude.toFloat(),
                valueRange = 10f..500f,
                unit = "m",
                onValueChange = { viewModel.updateFlightParams(params.copy(maxAltitude = it.toInt())) }
            )
            Spacer(Modifier.height(8.dp))
            SliderSetting(
                label = "Max Distance",
                value = params.maxDistance.toFloat(),
                valueRange = 50f..8000f,
                unit = "m",
                onValueChange = { viewModel.updateFlightParams(params.copy(maxDistance = it.toInt())) }
            )
        }

        Spacer(Modifier.height(12.dp))

        // ---- Speed ----
        SectionCard(title = "Speed", icon = Icons.Default.Speed) {
            SliderSetting(
                label = "Max Speed",
                value = params.maxSpeed,
                valueRange = 1f..30f,
                unit = "m/s",
                onValueChange = { viewModel.updateFlightParams(params.copy(maxSpeed = it)) }
            )
        }

        Spacer(Modifier.height(12.dp))

        // ---- Safety ----
        SectionCard(title = "Safety", icon = Icons.Default.Shield) {
            ToggleSetting(
                label = "Return to Home",
                description = "Auto RTH on signal loss",
                checked = params.enableReturnHome,
                onCheckedChange = { viewModel.updateFlightParams(params.copy(enableReturnHome = it)) }
            )
            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))
            ToggleSetting(
                label = "Low Battery RTL",
                description = "Return home when battery is low",
                checked = params.enableLowBatteryRtl,
                onCheckedChange = { viewModel.updateFlightParams(params.copy(enableLowBatteryRtl = it)) }
            )
            AnimatedVisibility(params.enableLowBatteryRtl) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    SliderSetting(
                        label = "Low Battery Threshold",
                        value = params.lowBatteryThreshold.toFloat(),
                        valueRange = 5f..40f,
                        unit = "%",
                        onValueChange = { viewModel.updateFlightParams(params.copy(lowBatteryThreshold = it.toInt())) }
                    )
                }
            }
            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))
            ToggleSetting(
                label = "Obstacle Avoidance",
                description = "Use sensors to avoid obstacles",
                checked = params.enableObstacleAvoidance,
                onCheckedChange = { viewModel.updateFlightParams(params.copy(enableObstacleAvoidance = it)) }
            )
            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))
            ToggleSetting(
                label = "Beginner Mode",
                description = "Limits speed and altitude for new pilots",
                checked = params.enableBeginnerMode,
                onCheckedChange = { viewModel.updateFlightParams(params.copy(enableBeginnerMode = it)) }
            )
        }

        Spacer(Modifier.height(12.dp))

        // ---- NFZ Bypass ----
        SectionCard(title = "No-Fly Zone", icon = Icons.Default.LocationOff) {
            ToggleSetting(
                label = "NFZ Bypass",
                description = "Disable geofencing restrictions. USE AT YOUR OWN RISK. Only with proper authorization.",
                checked = params.enableNfzBypass,
                onCheckedChange = { viewModel.updateFlightParams(params.copy(enableNfzBypass = it)) }
            )
            if (params.enableNfzBypass) {
                Spacer(Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2B1A00)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(modifier = Modifier.padding(10.dp)) {
                        Icon(Icons.Default.Warning, null, tint = Color(0xFFFFC107), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "NFZ bypass is enabled. Ensure you have the necessary permits and authorizations.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFFECB3)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ---- Video ----
        SectionCard(title = "Video Settings", icon = Icons.Default.Videocam) {
            Text("Resolution", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            VideoResolution.entries.forEach { res ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = params.videoResolution == res,
                        onClick = { viewModel.updateFlightParams(params.copy(videoResolution = res)) },
                        colors = RadioButtonDefaults.colors(selectedColor = DroneOrange)
                    )
                    Text(res.label, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 6.dp))
            SliderSetting(
                label = "Frame Rate",
                value = params.videoFrameRate.toFloat(),
                valueRange = 24f..120f,
                unit = "fps",
                steps = 3,
                onValueChange = { viewModel.updateFlightParams(params.copy(videoFrameRate = it.toInt())) }
            )
            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))
            ToggleSetting(
                label = "HDR Video",
                checked = params.enableHdr,
                onCheckedChange = { viewModel.updateFlightParams(params.copy(enableHdr = it)) }
            )
        }

        Spacer(Modifier.height(24.dp))

        // Action buttons
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { showResetDialog = true },
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.RestartAlt, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Reset")
            }
            DroneHacksButton(
                text = "Apply",
                onClick = { viewModel.applyFlightParams() },
                modifier = Modifier.weight(2f),
                isLoading = state.isLoading,
                icon = Icons.Default.Send
            )
        }

        Spacer(Modifier.height(16.dp))
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            icon = { Icon(Icons.Default.RestartAlt, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Reset to Defaults") },
            text = { Text("This will restore all flight parameters to factory defaults. Are you sure?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetToDefaults()
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
            }
        )
    }
}
