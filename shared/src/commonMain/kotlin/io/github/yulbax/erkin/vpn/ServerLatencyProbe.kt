package io.github.yulbax.erkin.vpn

import io.github.yulbax.erkin.vpn.core.BYEDPI_PROXY_TAG
import io.github.yulbax.erkin.vpn.core.EngineProxy
import io.github.yulbax.erkin.vpn.core.NetworkOptions
import io.github.yulbax.erkin.vpn.core.ProxyDelay
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

fun interface ProxyLatencyTester {
    suspend fun measure(proxies: List<EngineProxy>, options: NetworkOptions, url: String, timeoutMs: Int, byeDpiPort: Int?): String
}

class ServerLatencyProbe(
    private val store: VpnSessionStore,
    private val byeDpiPort: () -> Int = { 0 },
    private val tester: ProxyLatencyTester
) {
    suspend fun measure(ids: Collection<Long>? = null): Map<Long, ProxyDelay> = measureEach(ids).toList().toMap()

    fun measureEach(queue: Collection<Long>? = null): Flow<Pair<Long, ProxyDelay>> = channelFlow {
        val inputs = store.load()
        val byId = inputs.profiles.associateBy { it.id }
        val queued = queue?.mapNotNull(byId::get) ?: inputs.profiles
        val plans = queued.mapNotNull { profile ->
            runCatching { EngineConfigComposer.proxiesFor(profile, inputs.profiles) }.getOrNull()?.let { profile.id to it }
        }
        val port = byeDpiPort().takeIf { it > 0 }
        val options = inputs.settings.networkOptions()
        val slots = Semaphore(CONCURRENCY)
        plans.forEach { (id, proxies) ->
            slots.acquire()
            launch {
                val running = async { delayOf(id, proxies, options, port) }
                try {
                    withTimeoutOrNull(SLOT_HOLD_MS) { running.join() }
                } finally {
                    slots.release()
                }
                send(id to running.await())
            }
        }
    }

    private suspend fun delayOf(id: Long, proxies: List<EngineProxy>, options: NetworkOptions, port: Int?): ProxyDelay {
        val throughByeDpi = proxies.any { it.dialerProxy == BYEDPI_PROXY_TAG }
        val tested = proxies.map { proxy -> if (port == null && proxy.dialerProxy == BYEDPI_PROXY_TAG) proxy.copy(dialerProxy = null) else proxy }
        val delay = parse(tester.measure(tested, options, ProbeUrls.VPN, TIMEOUT_MS, port))[id] ?: ProxyDelay.Failed
        return if (delay == ProxyDelay.Failed && port == null && throughByeDpi) ProxyDelay.NeedsConnection else delay
    }

    companion object {
        const val TIMEOUT_MS = 5_000
        private const val CONCURRENCY = 10
        private const val SLOT_HOLD_MS = 2_000L

        fun parse(results: String): Map<Long, ProxyDelay> =
            Json.parseToJsonElement(results).jsonObject.mapNotNull { (tag, value) ->
                val id = ProxyTag.profileId(tag) ?: return@mapNotNull null
                val ms = value.jsonPrimitive.intOrNull ?: return@mapNotNull null
                id to if (ms > 0) ProxyDelay.Measured(ms) else ProxyDelay.Failed
            }.toMap()
    }
}
