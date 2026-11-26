package io.github.yulbax.erkin.vpn

import io.github.yulbax.erkin.vpn.core.BYEDPI_PROXY_TAG
import io.github.yulbax.erkin.vpn.core.EngineProxy
import io.github.yulbax.erkin.vpn.core.NetworkOptions
import io.github.yulbax.erkin.vpn.core.ProbeResult
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

fun interface ProxyLatencyTester {
    suspend fun measure(proxies: List<EngineProxy>, options: NetworkOptions, url: String, timeout: Duration, byeDpiPort: Int?, runningOnly: Boolean): String
}

class ServerLatencyProbe(
    private val store: VpnSessionStore,
    private val byeDpiPort: () -> Int = { 0 },
    private val tester: ProxyLatencyTester
) {
    suspend fun measure(ids: Collection<Long>? = null): Map<Long, ProbeResult> = measureEach(ids).toList().toMap()

    fun measureEach(queue: Collection<Long>? = null, runningOnly: Boolean = false): Flow<Pair<Long, ProbeResult>> = channelFlow {
        val (plans, options) = if (runningOnly) runningPlans(queue.orEmpty()) else describedPlans(queue)
        val port = byeDpiPort().takeIf { it > 0 && !runningOnly }
        val slots = Semaphore(CONCURRENCY)
        plans.forEach { (id, proxies) ->
            slots.acquire()
            launch {
                val running = async { resultOf(id, proxies, options, port, runningOnly) }
                try {
                    withTimeoutOrNull(SLOT_HOLD) { running.join() }
                } finally {
                    slots.release()
                }
                send(id to running.await())
            }
        }
    }

    suspend fun measureRunning(tag: String): ProbeResult = measured(tag, listOf(loaded(tag)), NetworkOptions(), null, runningOnly = true)

    private fun runningPlans(ids: Collection<Long>) =
        Plans(ids.map { id -> id to listOf(loaded(ProxyTag.of(id))) }, NetworkOptions())

    private suspend fun describedPlans(queue: Collection<Long>?): Plans {
        val inputs = store.load()
        val byId = inputs.profiles.associateBy { it.id }
        val queued = queue?.mapNotNull(byId::get) ?: inputs.profiles
        val plans = queued.mapNotNull { profile ->
            runCatching { EngineConfigComposer.proxiesFor(profile, inputs.profiles) }.getOrNull()?.let { profile.id to it }
        }
        return Plans(plans, inputs.settings.networkOptions())
    }

    private data class Plans(val proxies: List<Pair<Long, List<EngineProxy>>>, val options: NetworkOptions)

    private suspend fun resultOf(id: Long, proxies: List<EngineProxy>, options: NetworkOptions, port: Int?, runningOnly: Boolean): ProbeResult {
        val throughByeDpi = proxies.any { it.dialerProxy == BYEDPI_PROXY_TAG }
        val tested = proxies.map { proxy -> if (port == null && proxy.dialerProxy == BYEDPI_PROXY_TAG) proxy.copy(dialerProxy = null) else proxy }
        val result = measured(ProxyTag.of(id), tested, options, port, runningOnly)
        return if (result == ProbeResult.NoAnswer && port == null && throughByeDpi) ProbeResult.NeedsConnection else result
    }

    private suspend fun measured(tag: String, proxies: List<EngineProxy>, options: NetworkOptions, port: Int?, runningOnly: Boolean): ProbeResult =
        byTag(tester.measure(proxies, options, ProbeUrls.VPN, ServerProbePolicy.TIMEOUT, port, runningOnly))[tag] ?: ProbeResult.NoAnswer

    companion object {
        private const val CONCURRENCY = 10
        private val SLOT_HOLD = 2_000.milliseconds
        private const val LOADED_PROXY = """{"type":"direct"}"""

        fun parse(results: String): Map<Long, ProbeResult> =
            byTag(results).mapNotNull { (tag, result) -> ProxyTag.profileId(tag)?.let { it to result } }.toMap()

        private fun byTag(results: String): Map<String, ProbeResult> =
            Json.parseToJsonElement(results).jsonObject.mapNotNull { (tag, value) ->
                value.jsonPrimitive.intOrNull?.let { tag to ProbeResult.fromWire(it) }
            }.toMap()

        private fun loaded(tag: String) = EngineProxy(tag, LOADED_PROXY)
    }
}
