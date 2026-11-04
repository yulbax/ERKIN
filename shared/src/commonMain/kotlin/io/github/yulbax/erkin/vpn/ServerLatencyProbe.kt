package io.github.yulbax.erkin.vpn

import io.github.yulbax.erkin.vpn.core.BYEDPI_PROXY_TAG
import io.github.yulbax.erkin.vpn.core.EngineProxy
import io.github.yulbax.erkin.vpn.core.NetworkOptions
import io.github.yulbax.erkin.vpn.core.ProxyDelay
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
    suspend fun measure(ids: Set<Long>? = null): Map<Long, ProxyDelay> {
        val inputs = store.load()
        val plans = inputs.profiles.filter { ids == null || it.id in ids }.mapNotNull { profile ->
            runCatching { EngineConfigComposer.proxiesFor(profile, inputs.profiles) }.getOrNull()?.let { profile.id to it }
        }
        if (plans.isEmpty()) return emptyMap()
        val port = byeDpiPort().takeIf { it > 0 }
        val throughByeDpi = plans.filter { (_, proxies) -> proxies.any { it.dialerProxy == BYEDPI_PROXY_TAG } }.mapTo(HashSet()) { it.first }
        val proxies = plans.flatMap { it.second }.distinctBy { it.tag }
            .map { proxy -> if (port == null && proxy.dialerProxy == BYEDPI_PROXY_TAG) proxy.copy(dialerProxy = null) else proxy }
        val results = parse(tester.measure(proxies, inputs.settings.networkOptions(), ProbeUrls.VPN, TIMEOUT_MS, port))
        return plans.associate { (id, _) ->
            val delay = results[id] ?: ProxyDelay.Failed
            id to if (delay == ProxyDelay.Failed && port == null && id in throughByeDpi) ProxyDelay.NeedsConnection else delay
        }
    }

    companion object {
        const val TIMEOUT_MS = 5_000

        fun parse(results: String): Map<Long, ProxyDelay> =
            Json.parseToJsonElement(results).jsonObject.mapNotNull { (tag, value) ->
                val id = ProxyTag.profileId(tag) ?: return@mapNotNull null
                val ms = value.jsonPrimitive.intOrNull ?: return@mapNotNull null
                id to if (ms > 0) ProxyDelay.Measured(ms) else ProxyDelay.Failed
            }.toMap()
    }
}
