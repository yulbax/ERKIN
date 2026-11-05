package io.github.yulbax.erkin.vpn

import io.github.yulbax.erkin.util.AppLog
import io.github.yulbax.erkin.vpn.core.VpnEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn

data class ProbeParams(
    val engine: VpnEngine,
    val byeDpiPort: Int?,
    val vpnActive: Boolean,
    val serverTags: Map<Long, String> = emptyMap()
)

data class HealthParams(
    val probe: ProbeParams,
    val onVpnDown: suspend () -> Unit,
    val onVpnUp: () -> Unit,
    val onByedpiChanged: suspend () -> Unit
)

enum class HealthChannel { VPN, BYEDPI }

data class HealthSnapshot(
    val vpnActive: Boolean,
    val vpnDelayMs: Int?,
    val vpnChecked: Boolean,
    val vpnCountry: String,
    val byedpiActive: Boolean,
    val byedpiUp: Boolean,
    val measured: HealthChannel
) {
    val vpnUp get() = vpnDelayMs != null
    val anyUp get() = vpnUp || byedpiUp
    val allActiveUp get() = (!vpnActive || vpnUp) && (!byedpiActive || byedpiUp)
    val vpnHealthy get() = !vpnActive || vpnUp
    val vpnDown get() = vpnActive && vpnChecked && !vpnUp
    val state: VpnState get() = when {
        vpnDown -> VpnState.VpnDown
        anyUp -> VpnState.Connected(vpnDelayMs ?: 0)
        else -> VpnState.Verifying
    }
}

interface HealthProbe {
    fun observe(params: ProbeParams): Flow<HealthSnapshot>
}

class HealthMonitor(
    private val stateRepository: VpnStateRepository,
    private val log: AppLog,
    private val probe: HealthProbe,
    private val serverCheckInterval: Duration = SERVER_CHECK_INTERVAL,
    private val serverRecheckDelay: Duration = SERVER_RECHECK_DELAY
) {
    private var scope: CoroutineScope? = null

    fun start(scope: CoroutineScope, params: HealthParams) {
        stop()
        val childScope = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
        this.scope = childScope

        val health = probe.observe(params.probe)
            .shareIn(childScope, SharingStarted.Eagerly, replay = 1)

        health.onEach(::publishStats).launchIn(childScope)

        val vpnResults = health.filter { it.vpnActive && it.measured == HealthChannel.VPN }

        vpnResults
            .distinctUntilChangedBy { it.vpnUp }
            .onEach { logVpnTransition(it) }
            .launchIn(childScope)

        health.filter { it.byedpiActive && it.measured == HealthChannel.BYEDPI }
            .distinctUntilChangedBy { it.byedpiUp }
            .onEach { onByedpiTransition(it, params) }
            .launchIn(childScope)

        if (params.probe.serverTags.isNotEmpty()) childScope.watchServers(params.probe)

        childScope.launch {
            var failures = 0
            vpnResults.collect { snapshot ->
                if (snapshot.vpnHealthy) {
                    failures = 0
                    params.onVpnUp()
                } else {
                    failures++
                    if (failures % RECOVER_EVERY_N_FAILURES == 0) params.onVpnDown()
                }
            }
        }
    }

    fun stop() {
        scope?.cancel()
        scope = null
    }

    private fun CoroutineScope.watchServers(probe: ProbeParams) = launch {
        val misses = HashMap<Long, Int>()
        var previous = emptySet<Long>()
        while (true) {
            val failed = coroutineScope {
                probe.serverTags.map { (id, tag) -> async { id.takeIf { probe.engine.measureDelay(tag) == null } } }
                    .awaitAll()
                    .filterNotNullTo(HashSet())
            }
            probe.serverTags.keys.forEach { id -> misses[id] = if (id in failed) (misses[id] ?: 0) + 1 else 0 }
            val down = misses.filterValues { it >= SERVER_MISSES_TO_DOWN }.keys
            if (down != previous) log.w(TAG, "servers not responding: ${down.sorted()}")
            previous = down
            stateRepository.updateStats { it.copy(unreachableServers = down) }
            val suspect = failed.any { id -> id !in down }
            delay(if (suspect) serverRecheckDelay else serverCheckInterval)
        }
    }

    private fun publishStats(s: HealthSnapshot) {
        stateRepository.updateStats {
            it.copy(
                vpnUp = s.vpnUp,
                vpnLatencyMs = s.vpnDelayMs ?: 0,
                vpnCountry = s.vpnCountry,
                byedpiActive = s.byedpiActive,
                byedpiUp = s.byedpiUp
            )
        }
        stateRepository.update(s.state)
    }

    private fun logVpnTransition(s: HealthSnapshot) {
        if (s.vpnUp) {
            val country = if (s.vpnCountry.isNotEmpty()) " ${s.vpnCountry}" else ""
            log.i(TAG, "vpn channel up (${s.vpnDelayMs}ms$country)")
        } else {
            log.w(TAG, "vpn channel down")
        }
    }

    private suspend fun onByedpiTransition(s: HealthSnapshot, params: HealthParams) {
        if (s.byedpiUp) {
            log.i(TAG, "byedpi channel up")
        } else {
            log.w(TAG, "byedpi channel down")
        }
        params.onByedpiChanged()
    }

    companion object {
        private const val TAG = "Health"
        private const val RECOVER_EVERY_N_FAILURES = 4
        private const val SERVER_MISSES_TO_DOWN = 2
        private val SERVER_CHECK_INTERVAL = 60.seconds
        private val SERVER_RECHECK_DELAY = 10.seconds
    }
}
