package com.dronehacks.app.protocol

import com.dronehacks.app.model.DroneInfo
import com.dronehacks.app.model.FlightParams
import com.dronehacks.app.model.ModificationResult
import kotlinx.coroutines.delay

/**
 * Abstract drone communication protocol.
 * Concrete implementations handle USB/WiFi transport layers.
 */
interface DroneProtocol {
    suspend fun connect(): Boolean
    suspend fun disconnect()
    suspend fun getDroneInfo(): DroneInfo?
    suspend fun readFlightParams(): FlightParams?
    suspend fun writeFlightParams(params: FlightParams): ModificationResult
    suspend fun unlockNfz(zoneId: String): ModificationResult
    suspend fun resetToDefaults(): ModificationResult
}

/**
 * Simulated drone for testing / demo mode when no physical drone is connected.
 */
class SimulatedDroneProtocol : DroneProtocol {

    private var connected = false

    override suspend fun connect(): Boolean {
        delay(1500)
        connected = true
        return true
    }

    override suspend fun disconnect() {
        delay(200)
        connected = false
    }

    override suspend fun getDroneInfo(): DroneInfo? {
        if (!connected) return null
        delay(800)
        return DroneInfo(
            model = "DJI Mini 3 Pro (Demo)",
            firmwareVersion = "01.00.0500",
            serialNumber = "1ZNBJ1A0D200XX",
            flightController = "FC350",
            batteryLevel = 87,
            signalStrength = 92
        )
    }

    override suspend fun readFlightParams(): FlightParams? {
        if (!connected) return null
        delay(600)
        return FlightParams()
    }

    override suspend fun writeFlightParams(params: FlightParams): ModificationResult {
        if (!connected) return ModificationResult(false, "Not connected")
        delay(2000)
        return ModificationResult(true, "Parameters written successfully")
    }

    override suspend fun unlockNfz(zoneId: String): ModificationResult {
        if (!connected) return ModificationResult(false, "Not connected")
        delay(1500)
        return ModificationResult(true, "NFZ zone $zoneId unlocked")
    }

    override suspend fun resetToDefaults(): ModificationResult {
        if (!connected) return ModificationResult(false, "Not connected")
        delay(2500)
        return ModificationResult(true, "Drone reset to factory defaults")
    }
}

/**
 * USB OTG protocol stub.
 * In a full implementation this would use Android UsbManager to
 * communicate with the drone's flight controller over serial/HID.
 */
class UsbDroneProtocol : DroneProtocol by SimulatedDroneProtocol()

/**
 * WiFi protocol stub.
 * In a full implementation this would open a TCP/UDP socket to the
 * drone's built-in HTTP / RTSP server (e.g. 192.168.1.1).
 */
class WifiDroneProtocol : DroneProtocol by SimulatedDroneProtocol()
