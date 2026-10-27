package io.github.yulbax.erkin.vpn

import io.github.yulbax.erkin.util.AppLog
import io.github.yulbax.erkin.vpn.core.ByeDpiArgs
import io.github.yulbax.erkin.vpn.core.ByeDpiDns
import io.github.yulbax.erkin.vpn.core.ByeDpiProcess
import io.github.yulbax.erkin.vpn.core.ByeDpiReachability
import io.github.yulbax.erkin.vpn.core.freeLoopbackPort
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

enum class StrategyRunKind { Single, Saved, Search }

class ByeDpiStrategyRunner(
    private val newProcess: () -> ByeDpiProcess,
    private val siteCheck: ByeDpiSiteCheck,
    private val scope: CoroutineScope,
    private val log: AppLog
) {
    data class Outcome(val args: String, val results: List<SiteResult>) {
        val score: ByeDpiReachability get() = results.reachability()
    }

    data class Run(val kind: StrategyRunKind, val total: Int, val done: Int, val active: Set<String>)

    private val _run = MutableStateFlow<Run?>(null)
    val run: StateFlow<Run?> = _run.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Outcome>>(emptyList())
    val searchResults: StateFlow<List<Outcome>> = _searchResults.asStateFlow()

    private var job: Job? = null

    private val leasedPorts = mutableSetOf<Int>()

    fun start(
        kind: StrategyRunKind,
        strategies: List<String>,
        dns: ByeDpiDns,
        ipv6: Boolean,
        full: Boolean = false,
        onOutcome: suspend (Outcome) -> Unit = {}
    ) {
        val unique = strategies.distinct()
        if (job?.isCompleted == false || unique.isEmpty()) return
        val groups = if (full) ByeDpiSites.GROUPS else ByeDpiSites.QUICK
        if (kind == StrategyRunKind.Search) _searchResults.value = emptyList()
        _run.value = Run(kind, unique.size, 0, emptySet())
        job = scope.launch {
            val slots = Semaphore(PARALLEL_PROCESSES)
            unique.map { args ->
                launch {
                    slots.withPermit {
                        _run.update { it?.copy(active = it.active + args) }
                        val outcome = Outcome(args, test(args, groups, dns, ipv6))
                        if (kind == StrategyRunKind.Search) _searchResults.update { it + outcome }
                        onOutcome(outcome)
                        _run.update { it?.copy(done = it.done + 1, active = it.active - args) }
                    }
                }
            }.forEach { it.join() }
        }.also { it.invokeOnCompletion { _run.value = null } }
    }

    fun stop() {
        job?.cancel()
    }

    private suspend fun test(args: String, groups: List<SiteGroup>, dns: ByeDpiDns, ipv6: Boolean): List<SiteResult> = try {
        measure(args, groups, dns, ipv6)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (t: Throwable) {
        log.w(TAG, "strategy test failed for $args", t)
        ByeDpiSiteCheck.unreachable(groups)
    }

    private suspend fun measure(args: String, groups: List<SiteGroup>, dns: ByeDpiDns, ipv6: Boolean): List<SiteResult> =
        newProcess().use { process ->
            PortLease().use { lease ->
                coroutineScope {
                    val stopOnCancel = launch { awaitCancellation() }
                    stopOnCancel.invokeOnCompletion { process.stop() }
                    process.start(lease.port, ByeDpiArgs.parse(args)) { }
                    val results = if (awaitPort(lease.port)) {
                        siteCheck.results(lease.port, groups, dns, ipv6)
                    } else {
                        ByeDpiSiteCheck.unreachable(groups)
                    }
                    stopOnCancel.cancel()
                    results
                }
            }
        }

    private inner class PortLease : AutoCloseable {
        val port: Int = synchronized(leasedPorts) {
            generateSequence { freeLoopbackPort() }.first { it !in leasedPorts }.also { leasedPorts += it }
        }

        override fun close() {
            synchronized(leasedPorts) { leasedPorts -= port }
        }
    }

    private suspend fun awaitPort(port: Int): Boolean = withContext(Dispatchers.IO) {
        repeat(PORT_ATTEMPTS) {
            val open = runCatching { Socket().use { it.connect(InetSocketAddress(LOOPBACK, port), PORT_CONNECT_MS) } }.isSuccess
            if (open) return@withContext true
            delay(PORT_RETRY_MS)
        }
        false
    }

    private companion object {
        const val TAG = "StrategyTest"
        const val PARALLEL_PROCESSES = 5
        const val LOOPBACK = "127.0.0.1"
        const val PORT_ATTEMPTS = 40
        const val PORT_CONNECT_MS = 200
        const val PORT_RETRY_MS = 50L
    }
}
