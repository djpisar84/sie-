package com.dronehacks.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Primary – vivid orange (drone brand feel)
val DroneOrange = Color(0xFFFF6B00)
val DroneOrangeDark = Color(0xFFBF5000)
val DroneOrangeLight = Color(0xFFFF9A47)

// Accent – electric blue
val DroneBlue = Color(0xFF00B4FF)

// Background – near-black
val BackgroundDark = Color(0xFF0D0F14)
val SurfaceDark = Color(0xFF161A22)
val SurfaceVariantDark = Color(0xFF1E2330)
val OnSurfaceDark = Color(0xFFE0E4EF)

val DroneDarkColorScheme = darkColorScheme(
    primary = DroneOrange,
    onPrimary = Color.White,
    primaryContainer = DroneOrangeDark,
    onPrimaryContainer = DroneOrangeLight,
    secondary = DroneBlue,
    onSecondary = Color.Black,
    background = BackgroundDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = Color(0xFFADB5C9),
    error = Color(0xFFFF5252),
    onError = Color.White,
    outline = Color(0xFF3A3F52)
)

@Composable
fun DroneHacksTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DroneDarkColorScheme,
        typography = Typography(),
        content = content
    )
}
