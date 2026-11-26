package io.github.yulbax.erkin.vpn

import io.github.yulbax.erkin.util.AppLog
import io.github.yulbax.erkin.vpn.core.ProbeResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlin.time.Duration

internal enum class LivenessAction { NONE, VPN_UP, VPN_FAILING, VPN_DOWN, LOGIN_REJECTED }

internal data class LivenessStep(val counters: LivenessCounters, val action: LivenessAction)

internal data class LivenessCounters(val failures: Int = 0) {
    fun after(result: ProbeResult): LivenessStep = when {
        result is ProbeResult.Answered -> LivenessStep(LivenessCounters(), LivenessAction.VPN_UP)
        result == ProbeResult.LoginRejected -> LivenessStep(this, LivenessAction.LOGIN_REJECTED)
        else -> {
            val next = LivenessCounters(failures = failures + 1)
            val action = when {
                next.failures % ServerProbePolicy.FAILURES_TO_RECOVER == 0 -> LivenessAction.VPN_DOWN
                next.failures == 1 -> LivenessAction.VPN_FAILING
                else -> LivenessAction.NONE
            }
            LivenessStep(next, action)
        }
    }
}

internal class VpnLiveness(
    private val params: HealthParams,
    private val snapshot: MutableStateFlow<HealthSnapshot>,
    private val stateRepository: VpnStateRepository,
    private val checks: ServerChecks,
    private val country: CountryLookup,
    private val log: AppLog,
    private val checkInterval: Duration,
    private val retryDelay: Duration
) {
    private var counters = LivenessCounters()

    suspend fun run() {
        val active = params.activeServer
        while (true) {
            val result = params.activeTag.takeIf { it.isNotEmpty() }?.let { checks.checkRunning(it) } ?: ProbeResult.NoAnswer
            active?.let { id -> stateRepository.updateStats { it.afterProbes(mapOf(id to result)) } }
            if (apply(result) == LivenessAction.LOGIN_REJECTED) return
            delay(if (result is ProbeResult.Answered) checkInterval else retryDelay)
        }
    }

    private suspend fun apply(result: ProbeResult): LivenessAction {
        val delayMs = (result as? ProbeResult.Answered)?.ms
        val before = snapshot.getAndUpdate { it.copy(vpnDelayMs = delayMs, vpnChecked = true) }
        if (!before.vpnChecked || before.vpnUp != (delayMs != null)) {
            if (delayMs != null) log.i(HEALTH_TAG, "vpn channel up (${delayMs}ms)") else log.w(HEALTH_TAG, "vpn channel down")
        }
        val step = counters.after(result)
        counters = step.counters
        when (step.action) {
            LivenessAction.VPN_UP -> {
                country.onVpnUp()
                params.onVpnUp()
            }
            LivenessAction.LOGIN_REJECTED -> params.onLoginRejected()
            LivenessAction.VPN_FAILING -> params.onVpnFailing()
            LivenessAction.VPN_DOWN -> params.onVpnDown()
            LivenessAction.NONE -> Unit
        }
        return step.action
    }
}
