package com.dronehacks.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dronehacks.app.model.NfzType
import com.dronehacks.app.model.NfzZone
import com.dronehacks.app.ui.theme.DroneOrange
import com.dronehacks.app.ui.theme.SurfaceVariantDark
import com.dronehacks.app.viewmodel.DroneViewModel

@Composable
fun NfzScreen(viewModel: DroneViewModel) {
    val state by viewModel.uiState.collectAsState()
    var selectedType by remember { mutableStateOf<NfzType?>(null) }
    var pendingUnlockZoneId by remember { mutableStateOf<String?>(null) }

    val filtered = if (selectedType == null) state.nfzZones
                   else state.nfzZones.filter { it.type == selectedType }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("NFZ Manager", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
            Text(
                "${state.nfzZones.count { it.isActive }} active zones · ${state.nfzZones.count { !it.isActive }} unlocked",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Type filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedType == null,
                onClick = { selectedType = null },
                label = { Text("All") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = DroneOrange,
                    selectedLabelColor = Color.White
                )
            )
            NfzType.entries.take(3).forEach { type ->
                FilterChip(
                    selected = selectedType == type,
                    onClick = { selectedType = if (selectedType == type) null else type },
                    label = { Text(type.label.split(" ").first()) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(type.color),
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filtered, key = { it.id }) { zone ->
                NfzZoneCard(
                    zone = zone,
                    isLoading = state.isLoading,
                    onUnlockClick = { pendingUnlockZoneId = zone.id }
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    // Confirm dialog
    pendingUnlockZoneId?.let { zoneId ->
        val zone = state.nfzZones.find { it.id == zoneId }
        AlertDialog(
            onDismissRequest = { pendingUnlockZoneId = null },
            icon = { Icon(Icons.Default.LockOpen, null, tint = DroneOrange) },
            title = { Text("Unlock Zone") },
            text = {
                Text("Unlock \"${zone?.name}\"? Only proceed if you have valid authorization to fly in this area.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.unlockNfzZone(zoneId)
                        pendingUnlockZoneId = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = DroneOrange)
                ) { Text("Unlock") }
            },
            dismissButton = {
                TextButton(onClick = { pendingUnlockZoneId = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun NfzZoneCard(zone: NfzZone, isLoading: Boolean, onUnlockClick: () -> Unit) {
    val typeColor = Color(zone.type.color)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Type indicator dot
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(if (zone.isActive) typeColor else Color(0xFF607D8B))
            )
            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    zone.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ZoneStat(Icons.Default.RadioButtonChecked, "${(zone.radiusMeters / 1000.0).let { if (it < 1) "${zone.radiusMeters.toInt()}m" else "${"%.1f".format(it)}km" }}")
                    ZoneStat(Icons.Default.Height, "${zone.maxAltitudeMeters.toInt()}m max")
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(typeColor.copy(alpha = 0.15f))
                        .border(0.5.dp, typeColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        zone.type.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = typeColor
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            if (zone.isActive) {
                OutlinedButton(
                    onClick = onUnlockClick,
                    enabled = !isLoading,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DroneOrange),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(DroneOrange.copy(alpha = 0.5f))
                    )
                ) {
                    Icon(Icons.Default.LockOpen, null, Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Unlock", style = MaterialTheme.typography.labelMedium)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Unlocked", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50))
                }
            }
        }
    }
}

@Composable
private fun ZoneStat(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(3.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
