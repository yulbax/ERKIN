package io.github.yulbax.erkin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.yulbax.erkin.data.AppDao
import io.github.yulbax.erkin.data.RoutedApps
import io.github.yulbax.erkin.data.SettingsRepository
import io.github.yulbax.erkin.util.Telemetry
import io.github.yulbax.erkin.util.VpnLauncher
import io.github.yulbax.erkin.vpn.ConnectionStats
import io.github.yulbax.erkin.vpn.VpnCommandBus
import io.github.yulbax.erkin.vpn.VpnState
import io.github.yulbax.erkin.vpn.VpnStateRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ErkinUiState(
    val homeHintSeen: Boolean = true,
    val hasRoutedApps: Boolean = false,
    val hasVpnApps: Boolean = false,
    val hasByedpiApps: Boolean = false,
    val bypassLan: Boolean = false
)

class ConnectionViewModel(
    private val vpnLauncher: VpnLauncher,
    vpnStateRepository: VpnStateRepository,
    appDao: AppDao,
    private val settingsRepository: SettingsRepository,
    private val commandBus: VpnCommandBus
) : ViewModel() {

    val uiState: StateFlow<ErkinUiState> = combine(
        settingsRepository.settings,
        appDao.getAllApps()
    ) { settings, apps ->
        val routed = RoutedApps.from(apps)
        ErkinUiState(settings.homeHintSeen, !routed.isEmpty, routed.hasVpn, routed.hasByeDpi, settings.bypassLan)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ErkinUiState())

    init {
        viewModelScope.launch {
            vpnStateRepository.state.first { it is VpnState.Connected }
            if (!settingsRepository.settings.first().homeHintSeen) dismissHomeHint()
        }
    }

    fun dismissHomeHint() {
        viewModelScope.launch {
            settingsRepository.update { it.copy(homeHintSeen = true) }
        }
    }

    val state: StateFlow<VpnState> = vpnStateRepository.state
    val stats: StateFlow<ConnectionStats> = vpnStateRepository.stats

    fun startVpn() {
        Telemetry.logVpnToggle(connect = true)
        vpnLauncher.start()
    }

    fun stopVpn() {
        Telemetry.logVpnToggle(connect = false)
        commandBus.stop()
    }
}
