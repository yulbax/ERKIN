package io.github.yulbax.erkin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.yulbax.erkin.data.SettingsRepository
import io.github.yulbax.erkin.data.byedpi.ByeDpiArgsEntity
import io.github.yulbax.erkin.data.byedpi.ByeDpiHistory
import io.github.yulbax.erkin.data.byedpi.ByeDpiSiteResultEntity
import io.github.yulbax.erkin.vpn.ByeDpiSiteProbe
import io.github.yulbax.erkin.vpn.ByeDpiSites
import io.github.yulbax.erkin.vpn.ConnectionStats
import io.github.yulbax.erkin.vpn.SiteResult
import io.github.yulbax.erkin.vpn.VpnStateRepository
import io.github.yulbax.erkin.vpn.core.ByeDpiArgs
import io.github.yulbax.erkin.vpn.core.TestNetwork
import io.github.yulbax.erkin.vpn.core.TestNetworkSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

class ByeDpiViewModel(
    private val settingsRepository: SettingsRepository,
    private val history: ByeDpiHistory,
    private val siteProbe: ByeDpiSiteProbe,
    private val networkSource: TestNetworkSource,
    vpnStateRepository: VpnStateRepository
) : ViewModel() {

    data class GroupScore(val group: String, val reachable: Int, val total: Int)

    data class NetworkResult(
        val network: TestNetwork,
        val testedAt: Long,
        val reachable: Int,
        val total: Int,
        val groups: List<GroupScore>,
        val sites: List<ByeDpiSiteResultEntity>
    )

    data class ArgsCard(
        val id: Long,
        val args: String,
        val name: String,
        val pinned: Boolean,
        val active: Boolean,
        val results: List<NetworkResult>
    )

    data class TestProgress(val args: String, val done: Int, val total: Int)

    data class UiState(
        val args: String = "",
        val running: Boolean = false,
        val cards: List<ArgsCard> = emptyList(),
        val testing: TestProgress? = null
    )

    val argsDefault: String = ByeDpiArgs.DEFAULT.joinToString(" ")

    private val stats: StateFlow<ConnectionStats> = vpnStateRepository.stats
    private val testing = MutableStateFlow<TestProgress?>(null)
    private var testJob: Job? = null

    val uiState: StateFlow<UiState> = combine(
        settingsRepository.settings,
        history.entries,
        history.results,
        stats,
        testing
    ) { settings, entries, results, stats, test ->
        val active = ByeDpiArgs.effective(settings.byeDpiArgs)
        val resultsByArgs = results.groupBy { it.argsId }
        UiState(
            args = settings.byeDpiArgs,
            running = stats.byeDpiPort > 0,
            cards = entries.map { it.toCard(active, resultsByArgs[it.id].orEmpty()) },
            testing = test
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState())

    init {
        viewModelScope.launch { history.remember(settingsRepository.settings.first().byeDpiArgs) }
    }

    fun apply(line: String) {
        viewModelScope.launch { history.apply(line) }
    }

    fun rename(id: Long, name: String) {
        viewModelScope.launch { history.rename(id, name) }
    }

    fun setPinned(id: Long, pinned: Boolean) {
        viewModelScope.launch { history.setPinned(id, pinned) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { history.delete(id) }
    }

    fun test(args: String, full: Boolean) {
        if (testJob?.isActive == true) return
        testJob = viewModelScope.launch {
            if (stats.value.byeDpiArgs != args) {
                history.apply(args)
                withTimeoutOrNull(RESTART_TIMEOUT) { stats.first { it.byeDpiArgs == args && it.byeDpiPort > 0 } }
                    ?: return@launch
            }
            val port = stats.value.byeDpiPort
            if (port <= 0) return@launch
            val groups = if (full) ByeDpiSites.GROUPS else listOf(ByeDpiSites.QUICK)
            val total = groups.sumOf { it.sites.size }
            val collected = mutableListOf<SiteResult>()
            testing.value = TestProgress(args, 0, total)
            try {
                siteProbe.probe(port, groups).collect { result ->
                    collected += result
                    testing.value = TestProgress(args, collected.size, total)
                }
                history.saveResults(args, networkSource.current(), collected)
            } finally {
                testing.value = null
            }
        }
    }

    fun stopTest() {
        testJob?.cancel()
    }

    private fun ByeDpiArgsEntity.toCard(active: String, results: List<ByeDpiSiteResultEntity>) = ArgsCard(
        id = id,
        args = args,
        name = name,
        pinned = pinned,
        active = args == active,
        results = results.groupBy { it.network }
            .map { (network, rows) -> rows.toNetworkResult(TestNetwork.fromWire(network)) }
            .sortedByDescending { it.testedAt }
    )

    private fun List<ByeDpiSiteResultEntity>.toNetworkResult(network: TestNetwork) = NetworkResult(
        network = network,
        testedAt = maxOf { it.testedAt },
        reachable = count { it.reachable },
        total = size,
        groups = groupBy { it.siteGroup }
            .map { (group, rows) -> GroupScore(group, rows.count { it.reachable }, rows.size) }
            .sortedBy { score -> GROUP_ORDER.indexOf(score.group).let { if (it < 0) Int.MAX_VALUE else it } },
        sites = this
    )

    private companion object {
        val RESTART_TIMEOUT = 10.seconds
        val GROUP_ORDER = ByeDpiSites.GROUPS.map { it.name }
    }
}
