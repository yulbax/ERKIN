package io.github.yulbax.erkin.vpn

import io.github.yulbax.erkin.util.AppLog
import io.github.yulbax.erkin.vpn.core.ProbeResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ServerChecks(
    private val probe: ServerLatencyProbe,
    private val results: ProbeResults,
    private val log: AppLog
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _testing = MutableStateFlow<Set<Long>>(emptySet())
    val testing: StateFlow<Set<Long>> = _testing.asStateFlow()
    private var running: Deferred<Map<Long, ProbeResult>?>? = null
    private val background = MutableStateFlow<Set<Long>>(emptySet())

    fun run(queue: List<Long>): Deferred<Map<Long, ProbeResult>?> {
        val ids = queue.toSet()
        if (running?.isActive == true || ids.isEmpty()) return CompletableDeferred(null)
        results.update { it - ids }
        results.compare(ids)
        background.update { it - ids }
        _testing.value = ids
        return scope.async<Map<Long, ProbeResult>?> {
            try {
                measure(queue, runningOnly = false) { id, _ -> _testing.update { it - id } }
            } finally {
                _testing.value = emptySet()
            }
        }.also { running = it }
    }

    suspend fun check(
        queue: Collection<Long>,
        runningOnly: Boolean = false,
        onResult: suspend (Long, ProbeResult) -> Unit = { _, _ -> }
    ): Map<Long, ProbeResult>? {
        background.update { it + queue }
        return measure(queue, runningOnly, onResult)
    }

    suspend fun checkRunning(tag: String): ProbeResult {
        val id = ProxyTag.profileId(tag)
        id?.let { server -> background.update { it + server } }
        val result = try {
            probe.measureRunning(tag)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            log.w(TAG, "latency test failed", t)
            ProbeResult.NoAnswer
        }
        id?.let { server -> results.update { it + (server to result) } }
        return result
    }

    suspend fun firstAnswering(queue: Collection<Long>): Long? {
        background.update { it + queue }
        val first = CompletableDeferred<Long?>()
        scope.launch {
            try {
                probe.measureEach(queue).collect { (id, result) ->
                    results.update { it + (id to result) }
                    if (result is ProbeResult.Answered) first.complete(id)
                }
            } catch (t: Throwable) {
                if (t !is CancellationException) log.w(TAG, "latency test failed", t)
            } finally {
                first.complete(null)
            }
        }
        return first.await()
    }

    private suspend fun measure(
        queue: Collection<Long>,
        runningOnly: Boolean,
        onResult: suspend (Long, ProbeResult) -> Unit
    ): Map<Long, ProbeResult>? {
        val measured = LinkedHashMap<Long, ProbeResult>()
        suspend fun record(id: Long, result: ProbeResult) {
            measured[id] = result
            results.update { it + (id to result) }
            onResult(id, result)
        }
        try {
            probe.measureEach(queue, runningOnly).collect { (id, result) -> record(id, result) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            log.w(TAG, "latency test failed", t)
            return null
        }
        queue.filter { it !in measured }.forEach { record(it, ProbeResult.NoAnswer) }
        return measured
    }

    fun forgetBackground() {
        val ids = background.getAndUpdate { emptySet() }
        results.update { it - ids }
    }

    private companion object {
        const val TAG = "Servers"
    }
}
