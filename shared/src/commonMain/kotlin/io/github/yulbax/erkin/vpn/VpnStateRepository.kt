package io.github.yulbax.erkin.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ConnectionStats(
    val configName: String = "",
    val vpnUp: Boolean = false,
    val vpnLatencyMs: Int = 0,
    val vpnCountry: String = "",
    val vpnCycling: Boolean = false,
    val byedpiActive: Boolean = false,
    val byedpiUp: Boolean = false,
    val byedpiChecking: Boolean = false,
    val byedpiReachable: Int = 0,
    val byedpiTotal: Int = 0,
    val byeDpiPort: Int = 0,
    val uplink: Long = 0,
    val downlink: Long = 0
)

class VpnStateRepository {
    private val _state = MutableStateFlow<VpnState>(VpnState.Disconnected)
    val state: StateFlow<VpnState> = _state.asStateFlow()

    private val _stats = MutableStateFlow(ConnectionStats())
    val stats: StateFlow<ConnectionStats> = _stats.asStateFlow()

    fun update(state: VpnState) {
        _state.value = state
    }

    fun updateStats(block: (ConnectionStats) -> ConnectionStats) {
        _stats.update(block)
    }

    fun channelsReconnecting() {
        _stats.update {
            it.copy(
                vpnUp = false,
                vpnLatencyMs = 0,
                vpnCountry = "",
                vpnCycling = false,
                byedpiUp = false,
                byedpiChecking = false,
                byedpiReachable = 0,
                byedpiTotal = 0
            )
        }
    }

    fun byeDpiStopped() {
        _stats.update { it.copy(byeDpiPort = 0, byedpiChecking = false) }
    }

    fun resetStats() {
        _stats.value = ConnectionStats()
    }
}
