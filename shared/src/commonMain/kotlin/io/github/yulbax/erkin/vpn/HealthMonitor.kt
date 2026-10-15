package io.github.yulbax.erkin.vpn

import io.github.yulbax.erkin.util.AppLog
import io.github.yulbax.erkin.vpn.core.VpnEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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
    val isFingerprintError: () -> Boolean
)

data class HealthParams(
    val probe: ProbeParams,
    val onRefreshSubscription: suspend () -> Unit,
    val onRecoveryReload: suspend () -> Unit,
    val onByedpiChanged: suspend () -> Unit
)

data class HealthSnapshot(
    val vpnActive: Boolean,
    val vpnDelayMs: Int?,
    val vpnCountry: String,
    val byedpiActive: Boolean,
    val byedpiUp: Boolean,
    val fpError: Boolean
) {
    val vpnUp get() = vpnDelayMs != null
    val anyUp get() = vpnUp || byedpiUp
    val allActiveUp get() = (!vpnActive || vpnUp) && (!byedpiActive || byedpiUp)
    val vpnHealthy get() = (!vpnActive || vpnUp) && !fpError
}

interface HealthProbe {
    fun observe(params: ProbeParams): Flow<HealthSnapshot>
}

class HealthMonitor(
    private val stateRepository: VpnStateRepository,
    private val log: AppLog,
    private val probe: HealthProbe
) {
    private var scope: CoroutineScope? = null

    fun start(scope: CoroutineScope, params: HealthParams) {
        stop()
        val childScope = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
        this.scope = childScope

        val health = probe.observe(params.probe)
            .shareIn(childScope, SharingStarted.Eagerly, replay = 1)

        health.onEach(::publishStats).launchIn(childScope)

        health.filter { it.vpnActive }
            .distinctUntilChangedBy { it.vpnUp }
            .onEach { logVpnTransition(it) }
            .launchIn(childScope)

        health.filter { it.byedpiActive }
            .distinctUntilChangedBy { it.byedpiUp }
            .onEach { onByedpiTransition(it, params) }
            .launchIn(childScope)

        childScope.launch {
            var failures = 0
            health.collect { snapshot ->
                failures = if (snapshot.vpnHealthy) 0 else failures + 1
                if (!snapshot.vpnHealthy) recover(failures, snapshot, params)
            }
        }
    }

    fun stop() {
        scope?.cancel()
        scope = null
    }

    private fun publishStats(s: HealthSnapshot) {
        stateRepository.updateStats {
            it.copy(
                vpnUp = s.vpnUp,
                vpnLatencyMs = s.vpnDelayMs ?: 0,
                vpnCountry = s.vpnCountry,
                vpnCycling = s.vpnActive && !s.vpnUp && s.fpError,
                byedpiActive = s.byedpiActive,
                byedpiUp = s.byedpiUp
            )
        }
        stateRepository.update(
            if (s.anyUp) VpnState.Connected(s.vpnDelayMs ?: 0)
            else VpnState.Verifying
        )
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

    private suspend fun recover(failures: Int, s: HealthSnapshot, params: HealthParams) {
        if (s.fpError) {
            params.onRecoveryReload()
            return
        }
        if (failures % REFRESH_SUBSCRIPTION_AFTER_FAILURES == 0) params.onRefreshSubscription()
        if (failures % RELOAD_EVERY_N_FAILURES == 0) params.onRecoveryReload()
    }

    companion object {
        private const val TAG = "Health"
        private const val RELOAD_EVERY_N_FAILURES = 4
        private const val REFRESH_SUBSCRIPTION_AFTER_FAILURES = 3
    }
}
