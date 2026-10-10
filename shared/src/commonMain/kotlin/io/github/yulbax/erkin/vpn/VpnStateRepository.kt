package io.github.yulbax.erkin.vpn

import io.github.yulbax.erkin.vpn.core.ProxyDelay
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
    val byedpiLatencyMs: Int = 0,
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

    private val _proxyDelays = MutableStateFlow<Map<Long, ProxyDelay>>(emptyMap())
    val proxyDelays: StateFlow<Map<Long, ProxyDelay>> = _proxyDelays.asStateFlow()

    fun update(state: VpnState) {
        _state.value = state
    }

    fun updateStats(block: (ConnectionStats) -> ConnectionStats) {
        _stats.update(block)
    }

    fun byeDpiStopped() {
        _stats.update { it.copy(byeDpiPort = 0, byedpiChecking = false) }
    }

    fun updateProxyDelays(delays: Map<Long, ProxyDelay>) {
        _proxyDelays.value = delays
    }

    fun resetStats() {
        _stats.value = ConnectionStats()
        _proxyDelays.value = emptyMap()
    }
}
