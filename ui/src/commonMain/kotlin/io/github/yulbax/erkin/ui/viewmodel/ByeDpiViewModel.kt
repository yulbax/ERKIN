package io.github.yulbax.erkin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.yulbax.erkin.data.SettingsEntity
import io.github.yulbax.erkin.data.SettingsRepository
import io.github.yulbax.erkin.data.byedpi.ByeDpiArgsEntity
import io.github.yulbax.erkin.data.byedpi.ByeDpiHistory
import io.github.yulbax.erkin.data.byedpi.ByeDpiSiteResultEntity
import io.github.yulbax.erkin.ui.res.Res
import io.github.yulbax.erkin.vpn.ByeDpiSites
import io.github.yulbax.erkin.vpn.ByeDpiStrategyRunner
import io.github.yulbax.erkin.vpn.StrategyRunKind
import io.github.yulbax.erkin.vpn.core.ByeDpiArgs
import io.github.yulbax.erkin.vpn.core.ByeDpiReachability
import io.github.yulbax.erkin.vpn.core.TestNetwork
import io.github.yulbax.erkin.vpn.core.TestNetworkSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ByeDpiViewModel(
    private val settingsRepository: SettingsRepository,
    private val history: ByeDpiHistory,
    private val runner: ByeDpiStrategyRunner,
    private val networkSource: TestNetworkSource
) : ViewModel() {

    data class GroupScore(val group: String, val reachability: ByeDpiReachability)

    data class NetworkResult(
        val network: TestNetwork,
        val testedAt: Long,
        val score: ByeDpiReachability,
        val groups: List<GroupScore>,
        val sites: List<ByeDpiSiteResultEntity>
    )

    data class StrategyCard(
        val id: Long,
        val args: String,
        val name: String,
        val pinned: Boolean,
        val active: Boolean,
        val current: NetworkResult?,
        val results: List<NetworkResult>
    )

    data class SearchRow(val args: String, val score: ByeDpiReachability, val saved: Boolean)

    data class UiState(
        val network: TestNetwork = TestNetwork.Unknown,
        val cards: List<StrategyCard> = emptyList(),
        val run: ByeDpiStrategyRunner.Run? = null,
        val search: List<SearchRow> = emptyList()
    )

    private val ranked = MutableStateFlow(false)

    val uiState: StateFlow<UiState> = combine(
        settingsRepository.settings,
        history.entries,
        history.results,
        combine(runner.run, ranked, networkSource.network) { run, ranked, network ->
            Triple(run, ranked && run?.kind != StrategyRunKind.Saved, network)
        },
        runner.searchResults
    ) { settings, entries, results, (run, rank, network), search ->
        val active = ByeDpiArgs.effective(settings.byeDpiArgs)
        val resultsByArgs = results.groupBy { it.argsId }
        val listed = entries.map { it.toCard(active, network, resultsByArgs[it.id].orEmpty()) }
        val cards = if (rank) {
            listed.sortedWith(compareByDescending<StrategyCard> { it.pinned }.thenByDescending { it.current?.score?.percent ?: -1 })
        } else {
            listed
        }
        val saved = entries.mapTo(HashSet()) { it.args }
        UiState(
            network = network,
            cards = cards,
            run = run,
            search = search
                .map { SearchRow(it.args, it.score, it.args in saved) }
                .sortedByDescending { it.score.percent }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState())

    init {
        viewModelScope.launch { history.remember(settingsRepository.settings.first().byeDpiArgs) }
    }

    fun apply(args: String) {
        viewModelScope.launch { history.apply(args) }
    }

    fun save(args: String) {
        if (args.isBlank()) return
        viewModelScope.launch { history.remember(args) }
    }

    fun edit(card: StrategyCard, args: String) {
        if (args.isBlank() || args.trim() == card.args) return
        viewModelScope.launch {
            if (card.active) history.apply(args) else history.remember(args)
            history.delete(card.id)
        }
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

    fun delete(cards: List<StrategyCard>) {
        viewModelScope.launch { cards.filterNot { it.active }.forEach { history.delete(it.id) } }
    }

    fun test(args: String, full: Boolean) = run(StrategyRunKind.Single, listOf(args), full, save = true)

    fun testAll() {
        ranked.value = true
        run(StrategyRunKind.Saved, uiState.value.cards.map { it.args }, full = false, save = true)
    }

    fun search(sni: String, custom: String?) {
        viewModelScope.launch {
            val source = custom ?: Res.readBytes(BUILT_IN_STRATEGIES).decodeToString()
            val quoted = "\"${sni.trim().ifBlank { DEFAULT_SNI }}\""
            val strategies = source.lines().map { it.trim().replace(SNI_PLACEHOLDER, quoted) }.filter { it.isNotEmpty() }
            run(StrategyRunKind.Search, strategies, full = false, save = false)
        }
    }

    fun addFromSearch(args: String) {
        val outcome = runner.searchResults.value.firstOrNull { it.args == args } ?: return
        viewModelScope.launch {
            history.remember(args)
            history.saveResults(args, networkSource.network.first(), outcome.results)
        }
    }

    fun addBestFromSearch() {
        uiState.value.search.filter { !it.saved && it.score.reachable > 0 }.take(BEST_TO_ADD).forEach { addFromSearch(it.args) }
    }

    fun stop() = runner.stop()

    private fun run(kind: StrategyRunKind, strategies: List<String>, full: Boolean, save: Boolean) {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val network = networkSource.network.first()
            runner.start(kind, strategies, settings.byeDpiDns(), settings.ipv6(), full) { outcome ->
                if (save) history.saveResults(outcome.args, network, outcome.results)
            }
        }
    }

    private fun SettingsEntity.byeDpiDns() = networkOptions().byeDpiDns

    private fun SettingsEntity.ipv6() = networkOptions().ipv6Mode.ipv6Enabled

    private fun ByeDpiArgsEntity.toCard(active: String, network: TestNetwork, results: List<ByeDpiSiteResultEntity>): StrategyCard {
        val byNetwork = results.groupBy { it.network }
            .map { (wire, rows) -> rows.toNetworkResult(TestNetwork.fromWire(wire)) }
            .sortedByDescending { it.testedAt }
        return StrategyCard(
            id = id,
            args = args,
            name = name,
            pinned = pinned,
            active = args == active,
            current = byNetwork.firstOrNull { it.network == network },
            results = byNetwork
        )
    }

    private fun List<ByeDpiSiteResultEntity>.toNetworkResult(network: TestNetwork) = NetworkResult(
        network = network,
        testedAt = maxOf { it.testedAt },
        score = filter { it.host in ByeDpiSites.QUICK_HOSTS }.reachability(),
        groups = groupBy { it.siteGroup }
            .map { (group, rows) -> GroupScore(group, rows.reachability()) }
            .sortedBy { score -> GROUP_ORDER.indexOf(score.group).let { if (it < 0) Int.MAX_VALUE else it } },
        sites = this
    )

    private fun List<ByeDpiSiteResultEntity>.reachability() = ByeDpiReachability(count { it.reachable }, size)

    private companion object {
        const val BUILT_IN_STRATEGIES = "files/byedpi_strategies.list"
        const val SNI_PLACEHOLDER = "{sni}"
        const val DEFAULT_SNI = "google.com"
        const val BEST_TO_ADD = 5
        val GROUP_ORDER = ByeDpiSites.GROUPS.map { it.name }
    }
}
