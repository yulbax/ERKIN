package io.github.yulbax.erkin.vpn

import io.github.yulbax.erkin.util.AppLog
import io.github.yulbax.erkin.vpn.core.VpnEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.time.Duration

data class HealthParams(
    val engine: VpnEngine,
    val byeDpiPort: Int?,
    val vpnActive: Boolean,
    val activeTag: String,
    val activeServer: Long?,
    val boundServers: Set<Long>,
    val onVpnFailing: () -> Unit,
    val onVpnDown: suspend () -> Unit,
    val onLoginRejected: suspend () -> Unit,
    val onVpnUp: () -> Unit,
    val onByedpiChanged: suspend () -> Unit
)

enum class HealthChannel { VPN, BYEDPI }

data class HealthSnapshot(
    val vpnActive: Boolean,
    val vpnDelayMs: Int?,
    val vpnChecked: Boolean,
    val vpnCountry: ExitCountry,
    val byedpiActive: Boolean,
    val byedpiUp: Boolean,
    val byedpiChecked: Boolean
) {
    val vpnUp get() = vpnDelayMs != null
    val allActiveUp get() = (!vpnActive || vpnUp) && (!byedpiActive || byedpiUp)
    val vpnDown get() = vpnActive && vpnChecked && !vpnUp
    val byedpiDown get() = byedpiActive && byedpiChecked && !byedpiUp
    val state: VpnState get() = when {
        vpnDown -> VpnState.ChannelDown(HealthChannel.VPN)
        byedpiDown -> VpnState.ChannelDown(HealthChannel.BYEDPI)
        allActiveUp -> VpnState.Connected(vpnDelayMs ?: 0)
        else -> VpnState.Verifying
    }
}

interface ChannelProbe {
    suspend fun byeDpiReachable(port: Int): Boolean
    suspend fun country(engine: VpnEngine): String?
}

class HealthMonitor(
    private val stateRepository: VpnStateRepository,
    private val log: AppLog,
    private val checks: ServerChecks,
    private val channels: ChannelProbe,
    private val checkInterval: Duration = ServerProbePolicy.CHECK_INTERVAL,
    private val retryDelay: Duration = ServerProbePolicy.RETRY_DELAY
) {
    private var scope: CoroutineScope? = null

    fun start(scope: CoroutineScope, params: HealthParams) {
        stop()
        val childScope = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
        this.scope = childScope
        val snapshot = MutableStateFlow(
            HealthSnapshot(
                vpnActive = params.vpnActive,
                vpnDelayMs = null,
                vpnChecked = false,
                vpnCountry = if (params.vpnActive) ExitCountry.Pending else ExitCountry.Unknown,
                byedpiActive = params.byeDpiPort != null,
                byedpiUp = false,
                byedpiChecked = false
            )
        )
        snapshot.onEach(::publish).launchIn(childScope)
        if (params.vpnActive) {
            val country = CountryLookup(params.engine, channels, snapshot, childScope)
            country.start()
            val liveness = VpnLiveness(params, snapshot, stateRepository, checks, country, log, checkInterval, retryDelay)
            childScope.launch { liveness.run() }
            val bound = (params.boundServers - listOfNotNull(params.activeServer).toSet()).toList()
            if (bound.isNotEmpty()) {
                val boundServers = BoundServers(bound, stateRepository, checks, log, checkInterval, retryDelay)
                childScope.launch { boundServers.run() }
            }
        }
        params.byeDpiPort?.let { port ->
            val byeDpi = ByeDpiWatch(port, params, snapshot, channels, log, checkInterval)
            childScope.launch { byeDpi.run() }
        }
    }

    fun stop() {
        scope?.cancel()
        scope = null
    }

    fun close() {
        stop()
        checks.forgetBackground()
    }

    private fun publish(s: HealthSnapshot) {
        stateRepository.updateStats {
            it.copy(
                vpnUp = s.vpnUp,
                vpnLatencyMs = s.vpnDelayMs ?: 0,
                vpnCountry = s.vpnCountry,
                vpnActive = s.vpnActive,
                byedpiActive = s.byedpiActive,
                byedpiUp = s.byedpiUp
            )
        }
        stateRepository.update(s.state)
    }
}

internal const val HEALTH_TAG = "Health"
