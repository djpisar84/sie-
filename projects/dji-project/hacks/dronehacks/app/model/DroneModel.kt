package com.dronehacks.app.model

data class DroneInfo(
    val model: String = "Unknown",
    val firmwareVersion: String = "Unknown",
    val serialNumber: String = "Unknown",
    val flightController: String = "Unknown",
    val batteryLevel: Int = 0,
    val signalStrength: Int = 0
)

data class FlightParams(
    val maxAltitude: Int = 120,          // meters
    val maxDistance: Int = 500,          // meters
    val maxSpeed: Float = 15.0f,         // m/s
    val enableNfzBypass: Boolean = false,
    val enableReturnHome: Boolean = true,
    val enableLowBatteryRtl: Boolean = true,
    val lowBatteryThreshold: Int = 20,   // percent
    val enableObstacleAvoidance: Boolean = true,
    val enableBeginnerMode: Boolean = false,
    val videoResolution: VideoResolution = VideoResolution.RES_4K,
    val videoFrameRate: Int = 30,
    val enableHdr: Boolean = false
)

enum class VideoResolution(val label: String) {
    RES_4K("4K (3840×2160)"),
    RES_2_7K("2.7K (2688×1512)"),
    RES_1080P("1080p (1920×1080)"),
    RES_720P("720p (1280×720)")
}

enum class ConnectionType {
    USB,
    WIFI,
    NONE
}

enum class ConnectionState {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED,
    ERROR
}

data class NfzZone(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double,
    val maxAltitudeMeters: Double,
    val type: NfzType,
    val isActive: Boolean = true
)

enum class NfzType(val label: String, val color: Long) {
    AIRPORT("Airport", 0xFFE53935),
    RESTRICTED("Restricted", 0xFFD81B60),
    REGULATORY("Regulatory", 0xFFFF6F00),
    WARNING("Warning", 0xFFFDD835),
    ENHANCED_WARNING("Enhanced Warning", 0xFFFB8C00),
    AUTHORIZATION("Authorization Required", 0xFF1E88E5)
}

data class ModificationResult(
    val success: Boolean,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)
