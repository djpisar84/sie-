package com.dronehacks.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dronehacks.app.model.*
import com.dronehacks.app.protocol.DroneProtocol
import com.dronehacks.app.protocol.SimulatedDroneProtocol
import com.dronehacks.app.protocol.UsbDroneProtocol
import com.dronehacks.app.protocol.WifiDroneProtocol
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DroneUiState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val connectionType: ConnectionType = ConnectionType.NONE,
    val droneInfo: DroneInfo? = null,
    val flightParams: FlightParams = FlightParams(),
    val nfzZones: List<NfzZone> = emptyList(),
    val lastResult: ModificationResult? = null,
    val isLoading: Boolean = false,
    val isDemoMode: Boolean = false
)

@HiltViewModel
class DroneViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(DroneUiState())
    val uiState: StateFlow<DroneUiState> = _uiState.asStateFlow()

    private var protocol: DroneProtocol? = null

    // -------------------------------------------------------------------------
    // Connection
    // -------------------------------------------------------------------------

    fun connectUsb() {
        connect(ConnectionType.USB, UsbDroneProtocol())
    }

    fun connectWifi() {
        connect(ConnectionType.WIFI, WifiDroneProtocol())
    }

    fun connectDemo() {
        connect(ConnectionType.NONE, SimulatedDroneProtocol(), demo = true)
    }

    private fun connect(type: ConnectionType, proto: DroneProtocol, demo: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(connectionState = ConnectionState.SCANNING, connectionType = type) }
            protocol = proto
            val ok = proto.connect()
            if (ok) {
                _uiState.update { it.copy(connectionState = ConnectionState.CONNECTING) }
                val info = proto.getDroneInfo()
                val params = proto.readFlightParams() ?: FlightParams()
                _uiState.update {
                    it.copy(
                        connectionState = ConnectionState.CONNECTED,
                        droneInfo = info,
                        flightParams = params,
                        nfzZones = buildSampleNfzZones(),
                        isDemoMode = demo
                    )
                }
            } else {
                _uiState.update { it.copy(connectionState = ConnectionState.ERROR, connectionType = ConnectionType.NONE) }
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            protocol?.disconnect()
            protocol = null
            _uiState.update {
                DroneUiState()
            }
        }
    }

    // -------------------------------------------------------------------------
    // Flight parameters
    // -------------------------------------------------------------------------

    fun updateFlightParams(params: FlightParams) {
        _uiState.update { it.copy(flightParams = params) }
    }

    fun applyFlightParams() {
        val params = _uiState.value.flightParams
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = protocol?.writeFlightParams(params)
                ?: ModificationResult(false, "No drone connected")
            _uiState.update { it.copy(isLoading = false, lastResult = result) }
        }
    }

    fun resetToDefaults() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = protocol?.resetToDefaults()
                ?: ModificationResult(false, "No drone connected")
            if (result.success) {
                val params = protocol?.readFlightParams() ?: FlightParams()
                _uiState.update { it.copy(isLoading = false, lastResult = result, flightParams = params) }
            } else {
                _uiState.update { it.copy(isLoading = false, lastResult = result) }
            }
        }
    }

    // -------------------------------------------------------------------------
    // NFZ
    // -------------------------------------------------------------------------

    fun unlockNfzZone(zoneId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = protocol?.unlockNfz(zoneId)
                ?: ModificationResult(false, "No drone connected")
            if (result.success) {
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        lastResult = result,
                        nfzZones = state.nfzZones.map { zone ->
                            if (zone.id == zoneId) zone.copy(isActive = false) else zone
                        }
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false, lastResult = result) }
            }
        }
    }

    fun clearLastResult() {
        _uiState.update { it.copy(lastResult = null) }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun buildSampleNfzZones(): List<NfzZone> = listOf(
        NfzZone("WAW-01", "Warsaw Chopin Airport", 52.1657, 20.9671, 5000.0, 0.0, NfzType.AIRPORT),
        NfzZone("WAW-02", "Warsaw City Center", 52.2297, 21.0122, 2000.0, 50.0, NfzType.REGULATORY),
        NfzZone("KRK-01", "Kraków John Paul II Airport", 50.0777, 19.7848, 5000.0, 0.0, NfzType.AIRPORT),
        NfzZone("GDN-01", "Gdańsk Lech Wałęsa Airport", 54.3776, 18.4662, 5000.0, 0.0, NfzType.AIRPORT),
        NfzZone("POZ-01", "Poznań Ławica Airport", 52.4210, 16.8263, 5000.0, 0.0, NfzType.AIRPORT),
        NfzZone("WRO-01", "Wrocław Copernicus Airport", 51.1027, 16.8858, 5000.0, 0.0, NfzType.AIRPORT),
        NfzZone("GOV-01", "Government Security Zone", 52.2419, 21.0176, 1000.0, 100.0, NfzType.RESTRICTED),
        NfzZone("MIL-01", "Military Zone Bemowo", 52.2469, 20.9228, 3000.0, 0.0, NfzType.RESTRICTED)
    )
}
