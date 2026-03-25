package com.dronehacks.app.ui.screens

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dronehacks.app.BuildConfig
import com.dronehacks.app.ui.theme.DroneOrange
import com.dronehacks.app.ui.theme.SurfaceVariantDark

@Composable
fun SettingsScreen() {
    var logEnabled by remember { mutableStateOf(true) }
    var autoConnect by remember { mutableStateOf(false) }
    var showTelemetry by remember { mutableStateOf(true) }
    var language by remember { mutableStateOf("English") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(20.dp))

        SettingsGroup(title = "Connection") {
            SettingsToggleItem(
                icon = Icons.Default.Bolt,
                title = "Auto-connect on USB attach",
                subtitle = "Automatically connect when drone is plugged in",
                checked = autoConnect,
                onCheckedChange = { autoConnect = it }
            )
        }

        Spacer(Modifier.height(12.dp))

        SettingsGroup(title = "Display") {
            SettingsToggleItem(
                icon = Icons.Default.Analytics,
                title = "Show telemetry overlay",
                subtitle = "Display real-time flight data",
                checked = showTelemetry,
                onCheckedChange = { showTelemetry = it }
            )
        }

        Spacer(Modifier.height(12.dp))

        SettingsGroup(title = "Developer") {
            SettingsToggleItem(
                icon = Icons.Default.BugReport,
                title = "Debug logging",
                subtitle = "Write verbose logs to file",
                checked = logEnabled,
                onCheckedChange = { logEnabled = it }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            SettingsNavigationItem(
                icon = Icons.Default.FolderOpen,
                title = "Export logs",
                subtitle = "Save log file to Downloads"
            )
        }

        Spacer(Modifier.height(12.dp))

        SettingsGroup(title = "About") {
            SettingsInfoItem(Icons.Default.Info, "Version", BuildConfig.VERSION_NAME)
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            SettingsInfoItem(Icons.Default.Code, "Build", BuildConfig.BUILD_TYPE)
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            SettingsNavigationItem(
                icon = Icons.Default.Gavel,
                title = "Licenses",
                subtitle = "Open source attributions"
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            SettingsNavigationItem(
                icon = Icons.Default.Policy,
                title = "Terms & Disclaimer",
                subtitle = "Usage terms and legal notice"
            )
        }

        Spacer(Modifier.height(24.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1200)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Gavel, null, tint = Color(0xFFFFC107), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Legal Disclaimer", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFFFFC107))
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "DroneHacks is intended solely for authorized users. Modifying drone firmware without authorization may violate local laws and regulations. Always obtain required permits. The authors accept no liability for misuse.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFFECB3)
                )
            }
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = androidx.compose.ui.unit.TextUnit(2f, androidx.compose.ui.unit.TextUnitType.Sp)),
        color = DroneOrange,
        modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark)
    ) {
        Column(modifier = Modifier.padding(4.dp)) {
            content()
        }
    }
}

@Composable
private fun SettingsToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = DroneOrange)
        )
    }
}

@Composable
private fun SettingsNavigationItem(icon: ImageVector, title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SettingsInfoItem(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
