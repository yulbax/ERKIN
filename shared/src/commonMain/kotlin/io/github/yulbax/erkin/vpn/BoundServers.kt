package io.github.yulbax.erkin.vpn

import io.github.yulbax.erkin.util.AppLog
import io.github.yulbax.erkin.vpn.core.ProbeResult
import kotlinx.coroutines.delay
import kotlin.time.Duration

internal class BoundMisses {
    private val misses = HashMap<Long, Int>()

    var unreachable = emptySet<Long>()
        private set

    fun record(round: Map<Long, ProbeResult>) {
        round.forEach { (id, result) -> misses[id] = if (result is ProbeResult.Answered) 0 else (misses[id] ?: 0) + 1 }
        unreachable = misses.filterValues { it >= ServerProbePolicy.MISSES_TO_UNREACHABLE }.keys.toSet()
    }

    fun shouldHurry(round: Map<Long, ProbeResult>): Boolean = round.any { (id, result) ->
        result !is ProbeResult.Answered && (misses[id] ?: 0) < ServerProbePolicy.MISSES_TO_UNREACHABLE
    }
}

internal class BoundServers(
    private val bound: List<Long>,
    private val stateRepository: VpnStateRepository,
    private val checks: ServerChecks,
    private val log: AppLog,
    private val checkInterval: Duration,
    private val retryDelay: Duration
) {
    private val misses = BoundMisses()

    suspend fun run() {
        while (true) {
            val round = checks.check(bound, runningOnly = true) ?: bound.associateWith { ProbeResult.NoAnswer }
            val before = misses.unreachable
            misses.record(round)
            val down = misses.unreachable
            if (down != before) log.w(HEALTH_TAG, "servers not responding: ${down.sorted()}")
            stateRepository.updateStats { it.copy(unreachableServers = down) }
            delay(if (misses.shouldHurry(round)) retryDelay else checkInterval)
        }
    }
}
