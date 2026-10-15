package io.github.yulbax.erkin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.yulbax.erkin.data.AppDao
import io.github.yulbax.erkin.data.RoutedApps
import io.github.yulbax.erkin.data.SettingsRepository
import io.github.yulbax.erkin.util.Telemetry
import io.github.yulbax.erkin.util.VpnLauncher
import io.github.yulbax.erkin.vpn.ByeDpiSiteProbe
import io.github.yulbax.erkin.vpn.ByeDpiSites
import io.github.yulbax.erkin.vpn.ConnectionStats
import io.github.yulbax.erkin.vpn.SiteResult
import io.github.yulbax.erkin.vpn.VpnCommandBus
import io.github.yulbax.erkin.vpn.VpnState
import io.github.yulbax.erkin.vpn.VpnStateRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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
    private val siteProbe: ByeDpiSiteProbe,
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

    fun dismissHomeHint() {
        viewModelScope.launch {
            settingsRepository.update { it.copy(homeHintSeen = true) }
        }
    }

    val state: StateFlow<VpnState> = vpnStateRepository.state
    val stats: StateFlow<ConnectionStats> = vpnStateRepository.stats

    data class ByeDpiTestState(
        val running: Boolean = false,
        val full: Boolean = false,
        val total: Int = 0,
        val results: List<SiteResult> = emptyList()
    )

    private var testJob: Job? = null
    private val _byeDpiTest = MutableStateFlow(ByeDpiTestState())
    val byeDpiTest: StateFlow<ByeDpiTestState> = _byeDpiTest.asStateFlow()

    fun startVpn() {
        Telemetry.logVpnToggle(connect = true)
        vpnLauncher.start()
    }

    fun stopVpn() {
        Telemetry.logVpnToggle(connect = false)
        commandBus.stop()
    }

    fun runByeDpiTest(full: Boolean) {
        val port = stats.value.byeDpiPort
        if (port <= 0) return
        testJob?.cancel()
        val groups = if (full) ByeDpiSites.GROUPS else listOf(ByeDpiSites.QUICK)
        val total = groups.sumOf { it.sites.size }
        _byeDpiTest.value = ByeDpiTestState(running = true, full = full, total = total)
        testJob = viewModelScope.launch {
            siteProbe.probe(port, groups).collect { result ->
                _byeDpiTest.update { it.copy(results = it.results + result) }
            }
            _byeDpiTest.update { it.copy(running = false) }
        }
    }

    fun stopByeDpiTest() {
        testJob?.cancel()
        _byeDpiTest.update { it.copy(running = false) }
    }
}
